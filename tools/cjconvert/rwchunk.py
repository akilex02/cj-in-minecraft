"""Minimal reader for RenderWare chunk streams (the container of DFF and TXD files)."""
import struct
from dataclasses import dataclass

STRUCT = 0x01
STRING = 0x02
EXTENSION = 0x03
TEXTURE_NATIVE = 0x15
TEXTURE_DICTIONARY = 0x16
FRAME_LIST = 0x0E
GEOMETRY = 0x0F
CLUMP = 0x10
GEOMETRY_LIST = 0x1A
SKIN = 0x116
HANIM = 0x11E
ATOMIC = 0x14
MATERIAL_LIST = 0x08
MATERIAL = 0x07
TEXTURE = 0x06


class FormatError(ValueError):
    """A file does not have the layout the converter expects."""


@dataclass(frozen=True)
class Chunk:
    type: int
    size: int
    version: int  # the raw library stamp; see library_version()
    start: int  # offset of the payload (the 12-byte header sits before it)

    @property
    def end(self):
        return self.start + self.size


def root(buf):
    """A pseudo-chunk that spans the whole buffer, to search the top level with first()/all_of()."""
    return Chunk(0, len(buf), 0, 0)


def iter_chunks(buf, start, end):
    pos = start
    while pos + 12 <= end:
        type_id, size, version = struct.unpack_from("<III", buf, pos)
        if pos + 12 + size > end:
            raise FormatError(f"chunk {type_id:#x} at offset {pos} claims {size} bytes but only {end - pos - 12} are left")
        yield Chunk(type_id, size, version, pos + 12)
        pos += 12 + size


def all_of(buf, parent, type_id):
    return [c for c in iter_chunks(buf, parent.start, parent.end) if c.type == type_id]


def first(buf, parent, type_id):
    for c in iter_chunks(buf, parent.start, parent.end):
        if c.type == type_id:
            return c
    raise FormatError(f"no chunk {type_id:#x} inside the chunk at offset {max(parent.start - 12, 0)}")


def library_version(raw):
    """Decode a chunk's library stamp (0x1803FFFF is RenderWare 3.6.0.3, which is 0x36003)."""
    if raw & 0xFFFF0000:
        return ((raw >> 14) & 0x3FF00) + 0x30000 | ((raw >> 16) & 0x3F)
    return raw << 8
