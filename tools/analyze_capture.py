#!/usr/bin/env python3
"""Analyze the Phase 1 periodic colour/pulse capture (requires NumPy and FFmpeg).

Source: 1080p HorizontalTop monochrome grid. Receiver: default test HUD with
solid square covering pixel (100,100). Assumes this capture geometry.
Output is diagnostic; capture-device latency and physical output are uncalibrated.
"""
import subprocess,json,numpy as np
from pathlib import Path
import argparse
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source', type=Path, required=True)
parser.add_argument('--client', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
def run(args):return subprocess.check_output(args)
def analyze(name,source):
 p=str(name)
 frames=json.loads(run(['ffprobe','-v','error','-select_streams','v:0','-show_frames','-show_entries','frame=best_effort_timestamp_time','-of','json',p]))['frames']
 ts=np.array([float(f['best_effort_timestamp_time']) for f in frames])
 crop='format=rgb24,crop=1:64:8:0' if source else 'format=rgb24,crop=1:1:100:100'
 raw=run(['ffmpeg','-v','error','-i',p,'-vf',crop,'-fps_mode','passthrough','-pix_fmt','rgb24','-f','rawvideo','-'])
 a=np.frombuffer(raw,np.uint8).reshape((-1,64 if source else 1,3))
 rgb=a[:,[24,40,56],1] if source else a[:,0,:]
 # Dominant channel states; mixed has no channel > 180.
 state=np.where(rgb.max(axis=1)>180,rgb.argmax(axis=1),3)
 change=np.flatnonzero(state[1:]!=state[:-1])+1
 vt=ts[change]; states=state[change]
 audio=np.frombuffer(run(['ffmpeg','-v','error','-i',p,'-vn','-ac','1','-ar','48000','-f','f32le','-']),np.float32)
 st=json.loads(run(['ffprobe','-v','error','-select_streams','a:0','-show_entries','stream=start_time','-of','json',p]))['streams'][0]
 if not len(audio) or max(abs(audio)) == 0: raise ValueError('No audio pulses')
 active=np.abs(audio)>max(abs(audio))*.35
 ix=np.flatnonzero(active)
 on=ix[np.r_[True,np.diff(ix)>4800]]/48000+float(st.get('start_time',0))
 if len(on) < 3: raise ValueError('At least three audio pulses required')
 offsets=[float(t-on[np.argmin(abs(on-t))]) for t in vt if on[0]+.5<t<on[-1]-.5]
 if not offsets: raise ValueError('No interior lighting transitions')
 return {'video_transitions_seconds':vt.tolist(),'states':states.tolist(),'audio_onsets_seconds':on.tolist(),'light_minus_audio_seconds':offsets,'median_seconds':float(np.median(offsets)),'audio_peak_float':float(max(abs(audio)))}
r = {'source': analyze(args.source, True), 'client': analyze(args.client, False)}
if r['source']['states'] != r['client']['states']:
    raise SystemExit('Colour transition sequences differ; align recordings before comparing.')
r['added_offset_seconds'] = r['client']['median_seconds'] - r['source']['median_seconds']
r['limitations'] = 'Uncalibrated capture latency; periodic sources; no physical or audience timing claim.'
args.output.write_text(json.dumps(r, indent=2) + '\n')
print(json.dumps(r, indent=2))
