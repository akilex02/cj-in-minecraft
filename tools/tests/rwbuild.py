"""Builders for tiny synthetic RenderWare/IMG files, so the converter is tested without any game data."""
import struct

VERSION = 0x1803FFFF  # the library stamp GTA San Andreas files carry
IDENTITY3 = (1, 0, 0, 0, 1, 0, 0, 0, 1)


def chunk(type_id, payload=b""):
    return struct.pack("<III", type_id, len(payload), VERSION) + payload


def struct_chunk(payload):
    return chunk(0x01, payload)


def extension(*children):
    return chunk(0x03, b"".join(children))


def string_chunk(text):
    raw = text.encode() + b"\0"
    return chunk(0x02, raw + b"\0" * (-len(raw) % 4))


def material(texture_name, color=(255, 255, 255, 255)):
    rgba = color[0] | color[1] << 8 | color[2] << 16 | color[3] << 24
    st = struct_chunk(struct.pack("<IIIIfff", 0, rgba, 0, 1, 1.0, 1.0, 1.0))
    tex = chunk(0x06, struct_chunk(struct.pack("<HH", 0x1106, 0)) + string_chunk(texture_name) + string_chunk("") + extension()) \
        if texture_name is not None else b""
    return chunk(0x07, st + tex + extension())


def material_list(texture_names, colors=None):
    n = len(texture_names)
    colors = colors or [(255, 255, 255, 255)] * n
    st = struct_chunk(struct.pack("<I", n) + struct.pack("<i", -1) * n)
    return chunk(0x08, st + b"".join(material(name, color) for name, color in zip(texture_names, colors)))


def hanim(node_id, nodes=()):
    payload = struct.pack("<III", 0x100, node_id, len(nodes))
    if nodes:
        payload += struct.pack("<II", 0, 0)
        payload += b"".join(struct.pack("<iII", nid, idx, 0) for idx, nid in enumerate(nodes))
    return chunk(0x11E, payload)


def frame_list(node_ids):
    """Frame 0 is the clump root; frame 1 carries the skeleton hierarchy; there is one frame per node."""
    count = 1 + len(node_ids)
    body = struct.pack("<I", count)
    for i in range(count):
        body += struct.pack("<12f", *IDENTITY3, 0, 0, 0) + struct.pack("<iI", -1 if i == 0 else 0, 0x20003)
    exts = [extension()]
    for i in range(len(node_ids)):
        exts.append(extension(hanim(node_ids[0], node_ids) if i == 0 else hanim(node_ids[i])))
    return chunk(0x0E, struct_chunk(body) + b"".join(exts))


def geometry(positions, normals, uvs, triangles, flags=0x36, skin=None, prelit=False, texture_names=None, materials=None, colors=None):
    """`triangles` are (v1, v2, v3); the file stores them as vertex2, vertex1, material, vertex3."""
    n = len(positions)
    if prelit:
        flags |= 0x08
    st = struct.pack("<IIII", flags | (1 << 16), len(triangles), n, 1)
    if prelit:
        st += b"\xff" * (n * 4)
    st += b"".join(struct.pack("<2f", *uv) for uv in uvs)
    mats = list(materials) if materials is not None else [0] * len(triangles)
    st += b"".join(struct.pack("<4H", v2, v1, m, v3) for (v1, v2, v3), m in zip(triangles, mats))
    st += struct.pack("<4f", 0, 0, 0, 1) + struct.pack("<II", 1, 1)
    st += b"".join(struct.pack("<3f", *p) for p in positions)
    st += b"".join(struct.pack("<3f", *p) for p in normals)
    ext = extension(skin) if skin else extension()
    materials = material_list(texture_names, colors) if texture_names else b""
    return chunk(0x0F, struct_chunk(st) + materials + ext)


def skin(bone_count, used, bone_indices, weights, matrices):
    payload = struct.pack("<BBBB", bone_count, len(used), 3, 0) + bytes(used)
    payload += b"".join(bytes(i) for i in bone_indices)
    payload += b"".join(struct.pack("<4f", *w) for w in weights)
    payload += b"".join(struct.pack("<16f", *m) for m in matrices)
    payload += b"\0" * 12
    return chunk(0x116, payload)


def dff(node_ids, geometry_chunk):
    clump_struct = struct_chunk(struct.pack("<III", 1, 0, 0))
    geometry_list = chunk(0x1A, struct_chunk(struct.pack("<I", 1)) + geometry_chunk)
    return chunk(0x10, clump_struct + frame_list(node_ids) + geometry_list + extension()) + b"\0" * 24  # real files end in zero padding


def texture_native(name, width, height, bgra, alpha=False, raster=0x600, d3d=22, depth=32, platform=9, flags=None):
    if flags is None:
        flags = 1 if alpha else 0
    st = struct.pack("<II", platform, 0x1101) + name.encode().ljust(32, b"\0") + b"".ljust(32, b"\0")
    st += struct.pack("<II", raster, d3d) + struct.pack("<HH", width, height)
    st += struct.pack("<BBBB", depth, 1, 4, flags) + struct.pack("<I", len(bgra)) + bgra
    return chunk(0x15, struct_chunk(st) + extension())


def txd(*natives):
    return chunk(0x16, struct_chunk(struct.pack("<HH", len(natives), 9)) + b"".join(natives) + extension())


def img(files):
    """A VER2 archive: a 32-byte directory entry per file and the data in 2048-byte sectors."""
    names = list(files)
    header = 8 + 32 * len(names)
    first_sector = -(-header // 2048)
    entries = b""
    blobs = b""
    sector = first_sector
    for n in names:
        data = files[n]
        sectors = -(-len(data) // 2048)
        entries += struct.pack("<IHH24s", sector, sectors, 0, n.encode())
        blobs += data.ljust(sectors * 2048, b"\0")
        sector += sectors
    return b"VER2" + struct.pack("<I", len(names)) + entries + b"\0" * (first_sector * 2048 - header) + blobs


def weapon_dff(gun_geometry, flash_geometry, root_pos=(0.0, 0.0, 0.0)):
    """A weapon as GTA ships it: frame 0 is the root (the gun), frame 1 the muzzle flash, a child of the root.
    The flash atomic comes first, like in the real files."""
    clump_struct = struct_chunk(struct.pack("<III", 2, 0, 0))
    frames = struct.pack("<I", 2)
    frames += struct.pack("<12f", *IDENTITY3, *root_pos) + struct.pack("<iI", -1, 0)
    frames += struct.pack("<12f", *IDENTITY3, 0.4, 0.0, 0.1) + struct.pack("<iI", 0, 0)
    frame_list = chunk(0x0E, struct_chunk(frames) + extension() + extension())
    geometry_list = chunk(0x1A, struct_chunk(struct.pack("<I", 2)) + flash_geometry + gun_geometry)  # geometry 0 flash, 1 gun
    atomic_flash = chunk(0x14, struct_chunk(struct.pack("<IIII", 1, 0, 5, 0)) + extension())
    atomic_gun = chunk(0x14, struct_chunk(struct.pack("<IIII", 0, 1, 5, 0)) + extension())
    return chunk(0x10, clump_struct + frame_list + geometry_list + atomic_flash + atomic_gun + extension()) + b"\0" * 24


def assembled_dff(body_geometry, part_geometry, flash_geometry, part_pos=(0.0, 0.0, 0.0), part_rot=IDENTITY3, part_parent=0):
    """A weapon with a body on the root frame, a flash on a child frame and one more part (the barrels of a minigun) on another
    child frame, moved and rotated by `part_pos` and `part_rot` (nine floats, the RenderWare right, up and at vectors).
    Geometry 0 is the flash, 1 the part, 2 the body; the atomics come in that order, like in the real file."""
    clump_struct = struct_chunk(struct.pack("<III", 3, 0, 0))
    frames = struct.pack("<I", 3)
    frames += struct.pack("<12f", *IDENTITY3, 0.0, 0.0, 0.0) + struct.pack("<iI", -1, 0)
    frames += struct.pack("<12f", *IDENTITY3, 0.4, 0.0, 0.1) + struct.pack("<iI", 0, 0)
    frames += struct.pack("<12f", *part_rot, *part_pos) + struct.pack("<iI", part_parent, 0)
    frame_list = chunk(0x0E, struct_chunk(frames) + extension() + extension() + extension())
    geometry_list = chunk(0x1A, struct_chunk(struct.pack("<I", 3)) + flash_geometry + part_geometry + body_geometry)
    atomic_flash = chunk(0x14, struct_chunk(struct.pack("<IIII", 1, 0, 5, 0)) + extension())
    atomic_part = chunk(0x14, struct_chunk(struct.pack("<IIII", 2, 1, 5, 0)) + extension())
    atomic_body = chunk(0x14, struct_chunk(struct.pack("<IIII", 0, 2, 5, 0)) + extension())
    return chunk(0x10, clump_struct + frame_list + geometry_list + atomic_flash + atomic_part + atomic_body + extension()) + b"\0" * 24


def vehicle_dff(frames, geometries, atomics, named=None):
    """A vehicle: `frames` are (name, parent, rotation (nine floats), position); every frame carries its name in a frame-name extension.
    `atomics` are (frame index, geometry index) pairs."""
    clump_struct = struct_chunk(struct.pack("<III", len(atomics), 0, 0))
    body = struct.pack("<I", len(frames))
    for _name, parent, rotation, position in frames:
        body += struct.pack("<12f", *rotation, *position) + struct.pack("<iI", parent, 0)
    names = [extension(chunk(0x253F2FE, name.encode())) for name, _parent, _rotation, _position in frames][:named]
    frame_list = chunk(0x0E, struct_chunk(body) + b"".join(names))
    geometry_list = chunk(0x1A, struct_chunk(struct.pack("<I", len(geometries))) + b"".join(geometries))
    atomic_chunks = b"".join(chunk(0x14, struct_chunk(struct.pack("<IIII", f, g, 5, 0)) + extension()) for f, g in atomics)
    return chunk(0x10, clump_struct + frame_list + geometry_list + atomic_chunks + extension()) + b"\0" * 24
