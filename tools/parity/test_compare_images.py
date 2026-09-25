import tempfile
import unittest
from pathlib import Path
from PIL import Image, ImageDraw
from compare_images import compare


class ComparisonTest(unittest.TestCase):
    def test_identical_passes_but_moved_panel_fails(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            reference = Image.new('RGB', (64, 64), '#07101a')
            ImageDraw.Draw(reference).rectangle((5, 5, 25, 40), fill='#8b5cf6')
            reference.save(root / 'reference.png')
            reference.save(root / 'actual.png')
            self.assertEqual(0, compare(root / 'reference.png', root / 'actual.png', root / 'diff.png'))
            shifted = Image.new('RGB', (64, 64), '#07101a')
            ImageDraw.Draw(shifted).rectangle((10, 5, 30, 40), fill='#8b5cf6')
            shifted.save(root / 'actual.png')
            self.assertGreater(compare(root / 'reference.png', root / 'actual.png', root / 'diff.png'), 0)

    def test_flat_colour_change_is_not_antialiasing(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            Image.new('RGB', (8, 8), '#101d2b').save(root / 'reference.png')
            Image.new('RGB', (8, 8), '#111d2b').save(root / 'actual.png')
            self.assertEqual(0, compare(root / 'reference.png', root / 'actual.png', root / 'diff.png'))
            Image.new('RGB', (8, 8), '#121d2b').save(root / 'actual.png')
            self.assertEqual(64, compare(root / 'reference.png', root / 'actual.png', root / 'diff.png'))


if __name__ == '__main__':
    unittest.main()
