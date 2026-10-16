import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

try:
    import numpy  # noqa: F401
    from PIL import Image  # noqa: F401
    HAVE_PREVIEW_DEPS = True
except ImportError:
    HAVE_PREVIEW_DEPS = False

from cjconvert import glb  # noqa: E402
from cjconvert.assemble import MeshPart, Model  # noqa: E402
from cjconvert.dff import Skeleton  # noqa: E402
from cjconvert.txd import Texture  # noqa: E402

RW_IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


@unittest.skipUnless(HAVE_PREVIEW_DEPS, "numpy and Pillow are needed for the preview")
class PreviewTests(unittest.TestCase):
    def test_renders_three_views_with_the_model_in_each(self):
        import preview_glb
        # a quad in the XZ plane facing +X (forward), 1 m wide along Y and 2 m tall
        part = MeshPart("quad", [(0.0, -0.5, -1.0), (0.0, 0.5, -1.0), (0.0, -0.5, 1.0), (0.0, 0.5, 1.0)],
                        [(1.0, 0.0, 0.0)] * 4, [(0.0, 1.0), (1.0, 1.0), (0.0, 0.0), (1.0, 0.0)],
                        [(0, 2, 1), (1, 2, 3)], [(0, 0, 0, 0)] * 4, [(1.0, 0.0, 0.0, 0.0)] * 4, "t")
        tex = Texture("t", 2, 2, bytes([255, 0, 0, 255] * 4))
        data = glb.to_glb(Model(Skeleton((0,), (RW_IDENT,)), [part], {"t": tex}))
        image = preview_glb.render_preview(data, scale=100)
        self.assertEqual(image.height, 240)  # 2 * extent (1 m) * scale (100) + 40
        self.assertEqual(image.width, 3 * 240)
        pixels = numpy.array(image.convert("RGB"))
        background = pixels[0, 0]
        for view in (0, 2):  # the side view sees the quad edge-on, so it is checked below
            tile = pixels[:, view * 240:(view + 1) * 240]
            self.assertTrue((tile != background).any(axis=2).any(), f"view {view} is empty")
        # the quad faces the camera in the front view (wide) and is edge-on in the side view (nearly empty)
        painted = [(pixels[:, v * 240:(v + 1) * 240] != background).any(axis=2).sum() for v in range(3)]
        self.assertGreater(painted[0], 10 * max(painted[1], 1))
        self.assertGreater(painted[2], 10 * max(painted[1], 1))


if __name__ == "__main__":
    unittest.main()
