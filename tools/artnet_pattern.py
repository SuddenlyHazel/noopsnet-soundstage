#!/usr/bin/env python3
"""Send a deterministic ArtDmx calibration pattern to local HNode (no dependencies)."""
import argparse
import socket
import struct
import time


def packet(sequence, universe, values):
    if not 0 <= universe <= 32767 or not 1 <= sequence <= 255:
        raise ValueError('Invalid ArtDmx universe or sequence')
    if len(values) != 512:
        raise ValueError('Expected 512 channel bytes')
    return (b'Art-Net\0' + struct.pack('<H', 0x5000) + struct.pack('>H', 14)
            + bytes([sequence, 0]) + struct.pack('<H', universe)
            + struct.pack('>H', len(values)) + bytes(values))


def channels(frame):
    # 30 Hz: two seconds per state. Ch1 intensity; ch2-4 distinct RGB states.
    colours = [(255, 0, 0), (0, 255, 0), (0, 0, 255), (128, 64, 32)]
    colour = colours[(frame // 60) % len(colours)]
    # Remaining cells expose coordinate errors, universe boundaries, and transfer.
    values = [i % 256 for i in range(512)]
    values[:4] = [255, *colour]
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seconds', type=int, default=60)
    parser.add_argument('--universe', type=int, default=0)
    args = parser.parse_args()
    if not 1 <= args.seconds <= 7200 or not 0 <= args.universe <= 32767:
        parser.error('seconds must be 1..7200; universe 0..32767')
    start = time.monotonic()
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
        try:
            for frame in range(args.seconds * 30):
                sock.sendto(packet(frame % 255 + 1, args.universe, channels(frame)), ('127.0.0.1', 6454))
                time.sleep(max(0, start + (frame + 1) / 30 - time.monotonic()))
        finally:
            sock.sendto(packet(1, args.universe, [0] * 512), ('127.0.0.1', 6454))


if __name__ == '__main__':
    main()
