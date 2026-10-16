"""Reader for GTA San Andreas IMG archives (version 2)."""
import struct
from pathlib import Path

from .rwchunk import FormatError

SECTOR = 2048
MAX_ENTRIES = 100_000


class ImgArchive:
    def __init__(self, path):
        self.path = Path(path)
        with open(self.path, "rb") as f:
            head = f.read(8)
            if len(head) < 8 or head[:4] != b"VER2":
                raise FormatError(f"{self.path.name} is not a VER2 IMG archive")
            count = struct.unpack_from("<I", head, 4)[0]
            if count > MAX_ENTRIES:
                raise FormatError(f"{self.path.name} claims {count} entries")
            raw = f.read(count * 32)
        if len(raw) != count * 32:
            raise FormatError(f"{self.path.name} has a truncated directory")
        self._entries = {}
        for i in range(count):
            offset, streaming, archive, name = struct.unpack_from("<IHH24s", raw, i * 32)
            key = name.split(b"\0")[0].decode("ascii", "replace").lower()
            self._entries[key] = (offset * SECTOR, (streaming or archive) * SECTOR)

    def names(self):
        return sorted(self._entries)

    def __contains__(self, name):
        return name.lower() in self._entries

    def read(self, name):
        try:
            offset, size = self._entries[name.lower()]
        except KeyError:
            raise KeyError(f"{name} is not in {self.path.name}") from None
        with open(self.path, "rb") as f:
            f.seek(offset)
            data = f.read(size)
        if len(data) != size:
            raise FormatError(f"{name} is cut off inside {self.path.name}")
        return data
