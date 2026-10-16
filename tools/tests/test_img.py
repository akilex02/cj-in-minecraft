import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert.img import ImgArchive  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402


def write(tmp, name, data):
    path = Path(tmp) / name
    path.write_bytes(data)
    return path


class ImgTests(unittest.TestCase):
    def test_reads_files_by_case_insensitive_name(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(tmp, "a.img", b.img({"Player.DFF": b"hello", "head.txd": b"x" * 5000}))
            img = ImgArchive(path)
            self.assertEqual(img.names(), ["head.txd", "player.dff"])
            self.assertIn("PLAYER.dff", img)
            self.assertTrue(img.read("player.dff").startswith(b"hello"))
            self.assertEqual(len(img.read("head.txd")), 3 * 2048)  # whole sectors, as the game stores them

    def test_missing_file_names_the_archive(self):
        with tempfile.TemporaryDirectory() as tmp:
            img = ImgArchive(write(tmp, "a.img", b.img({"a.dff": b"1"})))
            with self.assertRaises(KeyError) as ctx:
                img.read("nope.dff")
            self.assertIn("nope.dff", str(ctx.exception))
            self.assertIn("a.img", str(ctx.exception))

    def test_rejects_other_formats_and_corruption(self):
        # Review Focus #1
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "v1.img", b"VER1" + b"\0" * 100))
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "short.img", b"VER2"))
            huge = b"VER2" + struct.pack("<I", 0x7FFFFFFF)
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "huge.img", huge))
            truncated = b.img({"a.dff": b"1", "b.dff": b"2"})[:8 + 32 + 10]  # the directory is cut short
            with self.assertRaises(FormatError):
                ImgArchive(write(tmp, "cut.img", truncated))

    def test_a_file_whose_data_is_missing_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            data = b.img({"a.dff": b"x" * 100})
            img = ImgArchive(write(tmp, "t.img", data[:2048]))  # the sector with the data was cut off
            with self.assertRaises(FormatError):
                img.read("a.dff")


if __name__ == "__main__":
    unittest.main()
