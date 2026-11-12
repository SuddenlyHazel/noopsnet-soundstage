import unittest
from showcase import channels, grid

class ShowcaseTests(unittest.TestCase):
    def test_every_fixture_and_blackout(self):
        self.assertEqual(channels(1)[:4], [180, 255, 0, 0])
        self.assertEqual(channels(3)[:4], [180, 0, 255, 0])
        self.assertEqual(channels(6)[:4], [180, 0, 0, 255])
        self.assertEqual(channels(9)[:4], [180, 255, 255, 255])
        self.assertGreater(channels(12)[4], 0)
        self.assertNotEqual(channels(12)[8:10], channels(17)[8:10])
        self.assertGreater(channels(21)[10], 0)
        self.assertEqual(channels(24)[10], 0)
        self.assertGreater(channels(30)[12], 0)
        self.assertEqual(channels(31)[12], 0)
        for i in range(8):
            values = channels(40 + i / 2 + .1)
            self.assertEqual([j for j in range(8) if values[16 + j * 4]], [i])
        for address in [1, 5, 11, 13, *range(17, 46, 4)]:
            self.assertGreater(channels(52)[address - 1], 0)
            self.assertEqual(channels(59)[address - 1], 0)
        self.assertEqual(channels(0), channels(60))

    def test_non_emissive_effects_have_fill_light(self):
        for t in (20, 22, 25, 28, 30, 32, 38):
            values = channels(t)
            for address in [1, *range(17, 46, 4)]:
                self.assertEqual(values[address - 1:address + 3], [64, 255, 255, 255])
        self.assertFalse(any(channels(59)[16:48]))

    def test_grid_and_bounds(self):
        for frame in range(1800):
            values = channels(frame / 30)
            self.assertEqual(len(values), 512)
            self.assertTrue(all(0 <= v <= 255 for v in values))
            image = grid(frame / 30)
            for c in range(512):
                self.assertEqual(image[(c % 13) * 40 + c // 13], values[c])
            self.assertFalse(any(values[48:]))

if __name__ == '__main__':
    unittest.main()
