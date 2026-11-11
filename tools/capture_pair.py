#!/usr/bin/env python3
"""Capture two isolated X11 Minecraft windows and their separate audio monitors.

The input devices share the host wall clock; -isync preserves their start offsets.
The optional delay is for scheduled drift measurements, not simulated elapsed time.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--windows', nargs=2, type=int, required=True)
parser.add_argument('--sinks', nargs=2, required=True)
parser.add_argument('--display', default=':0')
parser.add_argument('--after', type=float, default=0)
parser.add_argument('--seconds', type=int, default=24)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--watch-pids', nargs='*', type=int, default=[])
args = parser.parse_args()
if not 0 <= args.after <= 7200 or not 1 <= args.seconds <= 120:
    parser.error('Invalid capture timing')
args.output.parent.mkdir(parents=True, exist_ok=True)
metadata_path = args.output.with_suffix('.capture.json')
metadata = {'status': 'waiting', 'created_unix': time.time(), 'deadline_monotonic': time.monotonic()+args.after,
            'windows': args.windows, 'sinks': args.sinks, 'capture_seconds': args.seconds, 'pid': os.getpid()}
identities = {pid: Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()[19] for pid in args.watch_pids}
def save():
    temp = metadata_path.with_suffix('.tmp')
    temp.write_text(json.dumps(metadata, indent=2)+'\n')
    temp.replace(metadata_path)
try:
    while time.monotonic() < metadata['deadline_monotonic']:
        for pid, identity in identities.items():
            current = Path(f'/proc/{pid}/stat').read_text().rsplit(')', 1)[1].split()
            if current[0] == 'Z' or current[19] != identity:
                raise RuntimeError(f'Observed test process {pid} ended or changed identity')
        metadata['heartbeat_unix'] = time.time()
        save()
        time.sleep(min(10, max(0, metadata['deadline_monotonic']-time.monotonic())))
    command = ['ffmpeg', '-hide_banner', '-nostdin', '-y']
    for i, window in enumerate(args.windows):
        command += ['-thread_queue_size', '1024']
        if i:
            command += ['-isync', '0']
        command += ['-f', 'x11grab', '-framerate', '60', '-window_id', str(window), '-i', args.display]
    for sink in args.sinks:
        command += ['-thread_queue_size', '1024', '-isync', '0', '-f', 'pulse', '-i', sink+'.monitor']
    command += ['-filter_complex', '[0:v][1:v]hstack=inputs=2[v]', '-map', '[v]', '-map', '2:a', '-map', '3:a',
                '-t', str(args.seconds), '-c:v', 'libx264', '-preset', 'ultrafast', '-crf', '18',
                '-c:a', 'pcm_s16le', str(args.output)]
    metadata.update(status='capturing', capture_started_unix=time.time(), command=command)
    save()
    with args.output.with_suffix('.capture.log').open('w') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
    metadata.update(status='complete', finished_unix=time.time())
    save()
except Exception as error:
    metadata.update(status='failed', error=str(error), finished_unix=time.time())
    save()
    raise
