import struct
import sys
import unittest
import zlib
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import txd  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402

# a 2x1 image: one red pixel and one green pixel, stored as BGRA like Direct3D does
BGRA = bytes([0, 0, 255, 77, 0, 255, 0, 88])


def decode_png(png):
    """Tiny PNG decoder for the filter-0 images encode_png writes."""
    assert png[:8] == b"\x89PNG\r\n\x1a\n"
    pos, idat, size = 8, b"", None
    while pos < len(png):
        length, kind = struct.unpack_from(">I4s", png, pos)
        body = png[pos + 8:pos + 8 + length]
        assert struct.unpack_from(">I", png, pos + 8 + length)[0] == zlib.crc32(kind + body) & 0xFFFFFFFF
        if kind == b"IHDR":
            size = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        pos += 12 + length
    w, h = size[0], size[1]
    raw = zlib.decompress(idat)
    rows = [raw[y * (w * 4 + 1) + 1:(y + 1) * (w * 4 + 1)] for y in range(h)]
    assert all(raw[y * (w * 4 + 1)] == 0 for y in range(h))
    return w, h, b"".join(rows)


class TxdTests(unittest.TestCase):
    def test_converts_bgra_to_rgba(self):
        data = b.txd(b.texture_native("skin", 2, 1, BGRA, alpha=True))
        t = txd.parse_txd(data)["skin"]
        self.assertEqual((t.name, t.width, t.height), ("skin", 2, 1))
        self.assertEqual(t.rgba, bytes([255, 0, 0, 77, 0, 255, 0, 88]))

    def test_without_the_alpha_flag_the_alpha_is_opaque(self):
        data = b.txd(b.texture_native("skin", 2, 1, BGRA, alpha=False))
        self.assertEqual(txd.parse_txd(data)["skin"].rgba, bytes([255, 0, 0, 255, 0, 255, 0, 255]))

    def test_reads_every_texture_of_the_dictionary(self):
        data = b.txd(b.texture_native("a", 2, 1, BGRA), b.texture_native("b", 1, 1, BGRA[:4]))
        self.assertEqual(sorted(txd.parse_txd(data)), ["a", "b"])

    def test_unsupported_formats_fail_clearly(self):
        # Review Focus #3: compressed, paletted and 16-bit textures are not guessed at
        cases = {
            "compressed with an unknown format": b.texture_native("t", 4, 4, bytes(16), flags=0x08, d3d=0x12345678, depth=16),
            "paletted": b.texture_native("t", 2, 1, BGRA, raster=0x2600),
            "16 bit": b.texture_native("t", 2, 1, BGRA, depth=16),
            "wrong d3d format": b.texture_native("t", 2, 1, BGRA, d3d=827611204),  # 'DXT1'
            "other platform": b.texture_native("t", 2, 1, BGRA, platform=6),
        }
        for label, native in cases.items():
            with self.subTest(label):
                with self.assertRaises(txd.UnsupportedTexture) as ctx:
                    txd.parse_txd(b.txd(native))
                self.assertIn("t", str(ctx.exception))

    def test_corrupt_sizes_are_rejected(self):
        # Review Focus #1
        with self.assertRaises(FormatError):
            txd.parse_txd(b.txd(b.texture_native("t", 2, 1, BGRA[:6])))  # the pixel data is shorter than 2x1x4
        with self.assertRaises(FormatError):
            txd.parse_txd(b.txd(b.texture_native("t", 0, 1, b"")))
        with self.assertRaises(FormatError):
            txd.parse_txd(b"\0" * 64)  # no dictionary at all

    def test_png_roundtrip(self):
        rgba = bytes(range(16))  # a 2x2 image
        png = txd.encode_png(2, 2, rgba)
        self.assertEqual(decode_png(png), (2, 2, rgba))
        self.assertEqual(txd.encode_png(2, 2, rgba), png)  # deterministic


def block(c0, c1, bits):
    return struct.pack("<HHI", c0, c1, bits)


DXT1, DXT3, DXT5 = 0x31545844, 0x33545844, 0x35545844


def dxt_texture(fourcc, data, alpha=True):
    return b.txd(b.texture_native("t", 4, 4, data, flags=0x08 | (1 if alpha else 0), d3d=fourcc, depth=16, raster=0x300))


class DxtTests(unittest.TestCase):
    def pixels(self, t):
        return [tuple(t.rgba[i * 4:i * 4 + 4]) for i in range(t.width * t.height)]

    def test_dxt1_four_colour_block(self):
        # red and blue endpoints; the first row uses indices 0, 1, 2, 3
        t = txd.parse_txd(dxt_texture(DXT1, block(0xF800, 0x001F, 0xE4)))["t"]
        px = self.pixels(t)
        self.assertEqual(px[0:4], [(255, 0, 0, 255), (0, 0, 255, 255), (170, 0, 85, 255), (85, 0, 170, 255)])
        self.assertTrue(all(p == (255, 0, 0, 255) for p in px[4:]))  # every other pixel is index 0

    def test_dxt1_three_colour_block_has_a_transparent_index(self):
        # c0 <= c1: index 2 is the midpoint and index 3 is transparent black
        t = txd.parse_txd(dxt_texture(DXT1, block(0x001F, 0xF800, 0xE4)))["t"]
        px = self.pixels(t)
        self.assertEqual(px[0:4], [(0, 0, 255, 255), (255, 0, 0, 255), (127, 0, 127, 255), (0, 0, 0, 0)])

    def test_dxt3_explicit_alpha(self):
        alpha_rows = struct.pack("<4H", 0xFA50, 0xFFFF, 0xFFFF, 0xFFFF)  # row 0 nibbles: 0, 5, 10, 15
        t = txd.parse_txd(dxt_texture(DXT3, alpha_rows + block(0xF800, 0x0000, 0)))["t"]
        px = self.pixels(t)
        self.assertEqual([p[3] for p in px[0:4]], [0, 85, 170, 255])
        self.assertEqual(px[0][:3], (255, 0, 0))
        self.assertTrue(all(p[3] == 255 for p in px[4:]))

    def test_dxt5_interpolated_alpha(self):
        bits = (0 | 1 << 3 | 2 << 6 | 7 << 9).to_bytes(6, "little")  # indices 0, 1, 2, 7 in row 0
        t = txd.parse_txd(dxt_texture(DXT5, bytes([255, 0]) + bits + block(0xF800, 0x0000, 0)))["t"]
        self.assertEqual([p[3] for p in self.pixels(t)[0:4]], [255, 0, 218, 36])

    def test_dxt5_with_the_second_alpha_endpoint_larger_uses_six_steps(self):
        bits = (2 | 3 << 3 | 6 << 6 | 7 << 9).to_bytes(6, "little")  # indices 2, 3, 6, 7
        t = txd.parse_txd(dxt_texture(DXT5, bytes([0, 250]) + bits + block(0xF800, 0x0000, 0)))["t"]
        self.assertEqual([p[3] for p in self.pixels(t)[0:4]], [50, 100, 0, 255])

    def test_a_texture_not_a_multiple_of_four_is_cropped(self):
        data = b.txd(b.texture_native("t", 2, 2, block(0xF800, 0x001F, 0xE4), flags=0x08, d3d=DXT1, depth=16, raster=0x200))
        t = txd.parse_txd(data)["t"]
        self.assertEqual((t.width, t.height, len(t.rgba)), (2, 2, 16))

    def test_truncated_compressed_data_fails_clearly(self):
        with self.assertRaises(FormatError):
            txd.parse_txd(dxt_texture(DXT3, bytes(8)))  # a DXT3 block needs 16 bytes


if __name__ == "__main__":
    unittest.main()
