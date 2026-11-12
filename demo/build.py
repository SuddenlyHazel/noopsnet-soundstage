#!/usr/bin/env python3
"""Build the credited music + deterministic, mixed-fixture VRSL demo assets."""
from pathlib import Path
import argparse
import hashlib
import subprocess
from showcase import DURATION, FPS, grid


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', nargs='?', default='/tmp/soundstage-demo')
    parser.add_argument('--lighting-only', action='store_true', help='Reuse the deployed music; generate only the lighting video.')
    args = parser.parse_args()
    out = Path(args.output); out.mkdir(parents=True, exist_ok=True)
    if not args.lighting_only:
        track = out / 'dream-culture.mp3'
        if not track.exists():
            subprocess.run(['curl', '-fL', 'https://incompetech.com/music/royalty-free/mp3-royaltyfree/Dream%20Culture.mp3', '-o', str(track)], check=True)
        if hashlib.sha256(track.read_bytes()).hexdigest() != 'f12cbb52bd4b92bd6cf8a343645567c119627e9cbf98108a3eb6ac2cc275ee46':
            raise SystemExit('Unexpected track checksum')
    command = ['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pixel_format', 'gray', '-video_size', '40x13', '-framerate', str(FPS), '-i', '-',
               '-vf', 'scale=640:208:flags=neighbor,pad=1920:1080:0:0:black,format=yuv420p', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '12',
               '-g', str(FPS), '-keyint_min', str(FPS), '-sc_threshold', '0', '-an', str(out / 'lighting.mp4')]
    with subprocess.Popen(command, stdin=subprocess.PIPE) as process:
        try:
            for frame in range(DURATION * FPS):
                process.stdin.write(grid(frame / FPS))
        finally:
            process.stdin.close()
        if process.wait() != 0:
            raise SystemExit('Demo encode failed')
    print(out)


if __name__ == '__main__':
    main()
