#!/usr/bin/env python3
"""Reject overlapping, coplanar fixture faces (the striped lens regression)."""
import itertools
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FACES = {'west': (0, 'from'), 'east': (0, 'to'), 'down': (1, 'from'),
         'up': (1, 'to'), 'north': (2, 'from'), 'south': (2, 'to')}


def overlaps(model):
    bad = []
    for (i, a), (j, b) in itertools.combinations(enumerate(model['elements']), 2):
        for face in a['faces'].keys() & b['faces'].keys():
            axis, end = FACES[face]
            if a[end][axis] != b[end][axis]:
                continue
            if all(min(a['to'][d], b['to'][d]) > max(a['from'][d], b['from'][d])
                   for d in range(3) if d != axis):
                bad.append((i, j, face))
    return bad


if __name__ == '__main__':
    for name in ('stage_light', 'laser', 'fog_machine', 'particle_machine'):
        path = ROOT / f'src/main/resources/assets/noopsnet_soundstage/models/block/{name}.json'
        assert not (bad := overlaps(json.loads(path.read_text()))), (name, bad)
    print('PASS: all four fixture models have no overlapping coplanar faces.')
