import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import rwchunk as rw  # noqa: E402


class ChunkTests(unittest.TestCase):
    def test_walks_nested_chunks(self):
        data = b.chunk(rw.CLUMP, b.struct_chunk(b"abcd") + b.extension(b.chunk(0x11E, b"xy")))
        clump = rw.first(data, rw.root(data), rw.CLUMP)
        self.assertEqual([c.type for c in rw.iter_chunks(data, clump.start, clump.end)], [rw.STRUCT, rw.EXTENSION])
        ext = rw.first(data, clump, rw.EXTENSION)
        self.assertEqual(rw.first(data, ext, rw.HANIM).size, 2)
        self.assertEqual(len(rw.all_of(data, clump, rw.STRUCT)), 1)

    def test_zero_padding_after_the_last_chunk_is_harmless(self):
        data = b.chunk(rw.CLUMP, b"") + b"\0" * 2048
        self.assertEqual(rw.first(data, rw.root(data), rw.CLUMP).size, 0)

    def test_missing_chunk_is_a_format_error(self):
        data = b.chunk(rw.CLUMP, b"")
        with self.assertRaises(rw.FormatError):
            rw.first(data, rw.root(data), rw.TEXTURE_DICTIONARY)

    def test_a_chunk_that_runs_past_its_parent_is_rejected(self):
        # Review Focus #1: a size that does not fit must not be followed or looped over
        good = b.chunk(rw.CLUMP, b.chunk(rw.STRUCT, b"abcd"))
        corrupt = bytearray(good)
        struct.pack_into("<I", corrupt, 12 + 4, 9999)  # the inner chunk claims 9999 bytes
        with self.assertRaises(rw.FormatError):
            clump = rw.first(bytes(corrupt), rw.root(bytes(corrupt)), rw.CLUMP)
            list(rw.iter_chunks(bytes(corrupt), clump.start, clump.end))

    def test_a_truncated_file_is_rejected(self):
        data = b.chunk(rw.CLUMP, b"0123456789")
        with self.assertRaises(rw.FormatError):
            rw.first(data[:-4], rw.root(data[:-4]), rw.CLUMP)

    def test_library_version_decodes_the_gta_stamp(self):
        self.assertEqual(rw.library_version(0x1803FFFF), 0x36003)
        self.assertGreaterEqual(rw.library_version(0x1803FFFF), 0x34000)
        self.assertEqual(rw.library_version(0x0310), 0x31000)  # old-style stamps (upper 16 bits zero) hold the version directly


if __name__ == "__main__":
    unittest.main()
