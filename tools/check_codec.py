#!/usr/bin/env python3
"""Measure synthetic monochrome-grid codec error using the production Java decoder."""
import argparse
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java', default='java')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    # Independent fixture placement from HNode serializer + TextureWriter row flip.
    pixels = bytearray(1920 * 1080 * 3)
    expected = [(i * 73 + 19) % 256 for i in range(512)]
    for channel, value in enumerate(expected):
        x, y = channel // 13 * 16, channel % 13 * 16
        for row in range(y, y + 16):
            start = (row * 1920 + x) * 3
            pixels[start:start + 48] = bytes([value]) * 48
    reports = []
    with tempfile.TemporaryDirectory(prefix='soundstage-codec-') as temp:
        p = Path(temp)
        (p / 'grid.ppm').write_bytes(b'P6\n1920 1080\n255\n' + pixels)
        for crf, scale in [(18, None), (23, None), (28, None), (23, 720), (23, 360)]:
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-loop', '1', '-framerate', '30', '-i', str(p / 'grid.ppm'),
                *(['-vf', f'scale=-2:{scale},scale=1920:1080'] if scale else []),
                '-t', '1', '-c:v', 'libx264', '-crf', str(crf), '-pix_fmt', 'yuv420p', str(p / 'grid.mp4')], check=True, timeout=30)
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', str(p / 'grid.mp4'), '-frames:v', '1',
                '-pix_fmt', 'rgba', '-f', 'rawvideo', str(p / 'frame.rgba')], check=True, timeout=15)
            cp = str(ROOT / 'build/classes/java/main') + ':' + str(ROOT / 'build/classes/java/test')
            decoded = json.loads(subprocess.check_output([args.java, '-cp', cp, 'net.noopsnet.soundstage.DecodeFrame', str(p / 'frame.rgba')], text=True))
            errors = [abs(a - b) for a, b in zip(decoded, expected, strict=True)]
            reports.append({'crf': crf, 'resize_height_before_restore': scale, 'channels': len(errors), 'max_absolute_error': max(errors),
                'mean_absolute_error': sum(errors) / len(errors), 'within_two_codes': sum(e <= 2 for e in errors)})
    result = {'scope': 'Synthetic static 512-channel grid through H264/yuv420p and production Java decoder; not HNode capture or timing',
        'ffmpeg': subprocess.check_output(['ffmpeg', '-version'], text=True).splitlines()[0], 'results': reports}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
    if any(r['max_absolute_error'] > 2 for r in reports if r['resize_height_before_restore'] is None):
        raise SystemExit('Codec error exceeded provisional two-code tolerance')


if __name__ == '__main__':
    main()
