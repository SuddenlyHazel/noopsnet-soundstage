#!/usr/bin/env python3
"""External test fixture: feed matching PCM clicks and Art-Net flashes to OBS/HNode.

Audio samples and DMX states share a 48 kHz sample cursor. Device/capture latency
must still be measured; sending commands together does not imply presentation sync.
Requires pacat and a dedicated test sink. Does not use any microphone.
"""
import argparse
import array
import json
import math
from pathlib import Path
import socket
import signal
import subprocess
import time
from artnet_pattern import packet

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--pacat', default='pacat')
parser.add_argument('--sink', required=True)
parser.add_argument('--seconds', type=int, default=180)
parser.add_argument('--report', type=Path, required=True)
args = parser.parse_args()
if not 1 <= args.seconds <= 7200:
    parser.error('seconds must be 1..7200')
rate, samples_per_block = 48000, 1600
silence = bytes(samples_per_block*4)
tone = array.array('h')
for n in range(4800):
    sample = round(8192*math.sin(2*math.pi*1000*n/rate))
    tone.extend([sample, sample])
import sys
if sys.byteorder != 'little':
    tone.byteswap()
blocks = [tone.tobytes()[i*6400:(i+1)*6400] for i in range(3)]
command = [args.pacat, '--playback', '--raw', '--format=s16le', '--rate=48000',
           '--channels=2', '--latency-msec=20', '--device='+args.sink,
           '--client-name=SoundstageCalibration']
def stop_requested(signum, frame):
    raise KeyboardInterrupt

signal.signal(signal.SIGTERM, stop_requested)
process = subprocess.Popen(command, stdin=subprocess.PIPE, bufsize=0)
start = time.monotonic()
max_late = 0.0
sent = 0
try:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        try:
            # Half a second of silence lets the audio device establish its queue.
            for block in range(-15, args.seconds*30):
                phase = block % 60
                active = block >= 0 and phase < 3
                pcm = blocks[phase] if active else silence
                remaining = memoryview(pcm)
                while remaining:
                    written = process.stdin.write(remaining)
                    if not written:
                        raise RuntimeError('Audio pipe stopped accepting samples')
                    remaining = remaining[written:]
                values = [255 if active else 0, 255, 255, 255] + [i % 256 for i in range(4, 512)]
                sock.sendto(packet((block+15)%255+1, 0, values), ('127.0.0.1', 6454))
                sent += 1
                deadline = start + (block+16)/30
                max_late = max(max_late, time.monotonic()-deadline)
                time.sleep(max(0, deadline-time.monotonic()))
        finally:
            sock.sendto(packet(1, 0, [0]*512), ('127.0.0.1', 6454))
except KeyboardInterrupt:
    pass
finally:
    process.stdin.close()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.terminate()
        process.wait(timeout=5)
    args.report.write_text(json.dumps({'sample_rate': rate, 'artnet_hz': 30,
        'flash_click_duration_ms': 100, 'period_seconds': 2, 'sent_packets': sent,
        'elapsed_seconds': time.monotonic()-start, 'maximum_sender_lateness_ms': max_late*1000,
        'audio_command': command, 'note': 'Input timeline only; measure output capture offset separately.'}, indent=2)+'\n')
