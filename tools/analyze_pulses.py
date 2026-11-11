#!/usr/bin/env python3
"""Measure flash/click offset in a common-clock capture; requires FFmpeg/NumPy.

Use --isync 0 on capture inputs. One client: HUD pixel (100,100), audio 0.
Two clients: horizontally stacked 1280px windows and separate audio streams.
Results measure captured software outputs, not physical speaker/display latency.
"""
import argparse
import json
import subprocess
from pathlib import Path
import numpy as np


def read(command):
    return subprocess.check_output(command)


def probe(path, selector, entries):
    return json.loads(read(['ffprobe', '-v', 'error', '-select_streams', selector,
                            '-show_entries', entries, '-of', 'json', str(path)]))


def summary(values):
    if not len(values):
        raise ValueError('No matched observations')
    return {'count': len(values), 'median_ms': float(np.median(values)*1000),
            'min_ms': float(min(values)*1000), 'max_ms': float(max(values)*1000)}


def analyze(path, client, source_grid=False, pixel=(100, 100)):
    frames = probe(path, 'v:0', 'frame=best_effort_timestamp_time')['frames']
    times = np.array([float(f['best_effort_timestamp_time']) for f in frames])
    pixels = read(['ffmpeg', '-v', 'error', '-i', str(path), '-vf',
                   ('format=rgb24,crop=1:1:8:8' if source_grid else f'format=rgb24,crop=1:1:{pixel[0]+1280*client}:{pixel[1]}'), '-fps_mode', 'passthrough',
                   '-pix_fmt', 'rgb24', '-f', 'rawvideo', '-'])
    rgb = np.frombuffer(pixels, np.uint8).reshape(-1, 3)
    if len(rgb) != len(times):
        raise ValueError('Decoded video timestamps/pixel count differ')
    active = rgb.max(axis=1) > 100
    edges = np.flatnonzero(active & ~np.r_[False, active[:-1]])
    flashes = times[edges]
    middle = [min(int(np.searchsorted(times, times[i] + .04)), len(times)-1) for i in edges]
    colours = [int(np.argmax(rgb[i])) if min(rgb[i]) < 100 else 3 for i in middle]
    audio = np.frombuffer(read(['ffmpeg', '-v', 'error', '-i', str(path), '-map', f'0:a:{client}',
                               '-ac', '1', '-ar', '48000', '-f', 'f32le', '-']), np.float32)
    peak = float(np.max(np.abs(audio)))
    if peak == 0:
        raise ValueError('Silent audio stream')
    indices = np.flatnonzero(np.abs(audio) > peak * .3)
    first = indices[np.r_[True, np.diff(indices) > 4800]]
    start = float(probe(path, f'a:{client}', 'stream=start_time')['streams'][0]['start_time'])
    clicks = first / 48000 + start
    offsets = [float(t-clicks[np.argmin(abs(clicks-t))]) for t in flashes
               if clicks[0]+.2 < t < clicks[-1]-.2]
    return {'flash_seconds': flashes.tolist(), 'flash_colours': colours,
            'click_seconds': clicks.tolist(), 'light_minus_audio_seconds': offsets,
            'offset': summary(offsets), 'audio_peak': peak}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('capture', type=Path)
    parser.add_argument('--clients', type=int, choices=[1, 2], default=2)
    parser.add_argument('--pixel', nargs=2, type=int, default=[100, 100])
    parser.add_argument('--source-grid', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    result = {'clients': [analyze(args.capture, i, args.source_grid, args.pixel) for i in range(args.clients)],
              'limitations': 'Software screen/sink monitor capture; no physical output latency claim.'}
    if args.clients == 2:
        a, b = result['clients']
        pairs = []
        audio_pairs = []
        for t, colour in zip(a['flash_seconds'][1:-1], a['flash_colours'][1:-1]):
            candidates = [u for u, c in zip(b['flash_seconds'], b['flash_colours']) if c == colour and abs(u-t) < 4]
            if candidates:
                u = min(candidates, key=lambda u: abs(u-t))
                pairs.append(u-t)
                click_a = min(a['click_seconds'], key=lambda x: abs(x-t))
                click_b = min(b['click_seconds'], key=lambda x: abs(x-u))
                audio_pairs.append(click_b-click_a)
        result['client_b_minus_a_video_seconds'] = pairs
        result['interclient_video_skew'] = summary(pairs)
        result['client_b_minus_a_audio_seconds'] = audio_pairs
        result['interclient_audio_skew'] = summary(audio_pairs)
        result['matching_limit'] = 'Nearest same-colour pulse within 4s; eight-second repeating pattern.'
    args.output.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
