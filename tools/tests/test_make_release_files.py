import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import make_release_files  # noqa: E402


class SumsTest(unittest.TestCase):
    def test_each_file_gets_its_sha256_and_its_name(self):
        with tempfile.TemporaryDirectory() as d:
            a = Path(d) / "cj-mod-0.2.0.zip"
            a.write_bytes(b"mod")
            b = Path(d) / "cj-skate-0.2.0-windows.zip"
            b.write_bytes(b"windows pack, as built on Windows")
            text = make_release_files.sha256sums([a, b])
            self.assertEqual(text, f"{hashlib.sha256(b'mod').hexdigest()}  cj-mod-0.2.0.zip\n"
                                   f"{hashlib.sha256(b'windows pack, as built on Windows').hexdigest()}  cj-skate-0.2.0-windows.zip\n")


if __name__ == "__main__":
    unittest.main()
