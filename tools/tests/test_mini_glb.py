import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import make_mini_glb  # noqa: E402
from cjconvert import glb  # noqa: E402


class MiniGlbTests(unittest.TestCase):
    def test_the_committed_fixture_is_what_the_generator_writes(self):
        # the Java tests assert on this file's contents; it must not drift from the generator
        self.assertTrue(make_mini_glb.OUT.is_file(), "run tools/make_mini_glb.py")
        self.assertEqual(make_mini_glb.OUT.read_bytes(), glb.to_glb(make_mini_glb.mini_model()))

    def test_the_fixture_is_synthetic_and_tiny(self):
        data = make_mini_glb.OUT.read_bytes()
        self.assertLess(len(data), 4096)
        gltf, _ = glb.read_glb(data)
        self.assertEqual(gltf["asset"]["extras"]["parts"], ["torso", "hands"])
        self.assertEqual(len(gltf["images"]), 1)


if __name__ == "__main__":
    unittest.main()
