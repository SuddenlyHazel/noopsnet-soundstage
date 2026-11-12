#!/usr/bin/env python3
"""Decode the actual H.264 demo and validate its VRSL channel samples."""
import json
import subprocess
import sys
from showcase import channels, DURATION

video = sys.argv[1]
raw = subprocess.check_output(['ffmpeg', '-v', 'error', '-i', video, '-vf',
    "select='eq(mod(n,30),3)',crop=640:208:0:0,scale=40:13:flags=neighbor,format=rgb24",
    '-fps_mode', 'passthrough', '-f', 'rawvideo', '-'])
stride = 40 * 13 * 3
assert len(raw) == DURATION * stride, len(raw)
max_error = 0
for second in range(DURATION):
    expected = channels(second + .1)
    for channel in range(512):
        offset = second * stride + ((channel % 13) * 40 + channel // 13) * 3 + 1
        error = abs(raw[offset] - expected[channel])
        max_error = max(error, max_error)
        assert error <= 5, (second, channel + 1, raw[offset], expected[channel])
print(json.dumps({'decoded_frames': DURATION, 'channels_per_frame': 512,
                  'max_channel_error': max_error, 'passed': True}, indent=2))
