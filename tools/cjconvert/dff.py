"""DFF models: the skeleton of player.dff and the body parts (head, torso, hands, legs, feet) of player.img."""
import struct
from dataclasses import dataclass

from .rwchunk import (ATOMIC, CLUMP, EXTENSION, FRAME_LIST, GEOMETRY, GEOMETRY_LIST, HANIM, MATERIAL, MATERIAL_LIST, SKIN, STRING,
                      STRUCT, TEXTURE, FormatError, all_of, first, iter_chunks, library_version, root)

MAX_VERTICES = 65535


class UnsupportedGeometry(FormatError):
    """A geometry uses a layout the converter does not read (tri-strips, old RenderWare versions)."""


@dataclass(frozen=True)
class Skeleton:
    node_ids: tuple       # nodeID of every skeleton node, in node-index order
    inverse_bind: tuple   # per node, the 16 floats of its RwMatrix (right, up, at, pos with a pad float after each row)


@dataclass(frozen=True)
class Part:
    positions: list
    normals: list
    uvs: list
    triangles: list       # (v1, v2, v3), front faces counter-clockwise
    bone_indices: list    # 4 per vertex, indices into the part's own hierarchy
    weights: list         # 4 per vertex
    hierarchy_ids: tuple  # nodeID of each of the part's hierarchy nodes


def _hierarchy_ids(data, clump):
    frame_list = first(data, clump, FRAME_LIST)
    for ext in all_of(data, frame_list, EXTENSION):
        for c in iter_chunks(data, ext.start, ext.end):
            if c.type == HANIM and c.size >= 12:
                _version, _node_id, count = struct.unpack_from("<III", data, c.start)
                if count > 0:
                    if c.size < 20 + 12 * count:
                        raise FormatError("a skeleton hierarchy is cut off")
                    return tuple(struct.unpack_from("<i", data, c.start + 20 + 12 * i)[0] for i in range(count))
    raise FormatError("the model has no skeleton hierarchy")


def _only_geometry(data, clump):
    geometries = all_of(data, first(data, clump, GEOMETRY_LIST), GEOMETRY)
    if len(geometries) != 1:
        raise FormatError(f"expected one geometry, found {len(geometries)}")
    return geometries[0]


def _read_geometry(data, geometry, allow_tristrip=False, with_materials=False):
    st = first(data, geometry, STRUCT)
    if library_version(geometry.version) < 0x34000:
        raise UnsupportedGeometry("geometry from a RenderWare older than 3.4 is not supported")
    if st.size < 16:
        raise FormatError("a geometry is too short to hold its header")
    fmt, tri_count, vert_count, morph_count = struct.unpack_from("<IIII", data, st.start)
    flags = fmt & 0xFFFF
    uv_sets = (fmt >> 16) & 0xFF
    if flags & 0x01 and not allow_tristrip:
        raise UnsupportedGeometry("tri-strip geometry is not supported")
    if morph_count != 1:
        raise UnsupportedGeometry(f"{morph_count} morph targets; only one is supported")
    if not 0 < vert_count <= MAX_VERTICES:
        raise FormatError(f"a geometry has {vert_count} vertices")
    if uv_sets == 0:
        uv_sets = 1 if flags & 0x04 else (2 if flags & 0x80 else 0)
    need = 16 + (vert_count * 4 if flags & 0x08 else 0) + vert_count * 8 * uv_sets + tri_count * 8 + 24 + vert_count * 24
    if st.size < need:
        raise FormatError("a geometry is shorter than its header says")
    o = st.start + 16
    if flags & 0x08:
        o += vert_count * 4  # prelit vertex colours
    uvs = [struct.unpack_from("<2f", data, o + i * 8) for i in range(vert_count)] if uv_sets else []
    o += vert_count * 8 * uv_sets
    raw_tris = [struct.unpack_from("<4H", data, o + i * 8) for i in range(tri_count)]
    o += tri_count * 8
    o += 16  # bounding sphere
    has_vertices, has_normals = struct.unpack_from("<II", data, o)
    o += 8
    if not has_vertices:
        raise FormatError("a geometry has no vertices")
    positions = [struct.unpack_from("<3f", data, o + i * 12) for i in range(vert_count)]
    o += vert_count * 12
    normals = [struct.unpack_from("<3f", data, o + i * 12) for i in range(vert_count)] if has_normals else []
    # The file stores each triangle as vertex2, vertex1, material, vertex3.
    triangles = []
    materials = []
    for v2, v1, material, v3 in raw_tris:
        if max(v1, v2, v3) >= vert_count:
            raise FormatError("a triangle points past the last vertex")
        triangles.append((v1, v2, v3))
        materials.append(material)
    if with_materials:
        return positions, normals, uvs, triangles, vert_count, materials
    return positions, normals, uvs, triangles, vert_count


def _read_skin(data, geometry, vert_count):
    ext = first(data, geometry, EXTENSION)
    skin = first(data, ext, SKIN)
    if skin.size < 4:
        raise FormatError("a skin is too short")
    bones, used, _max_weights, _pad = struct.unpack_from("<BBBB", data, skin.start)
    o = skin.start + 4 + used
    if o + vert_count * 20 + bones * 64 > skin.end:
        raise FormatError("a skin does not fit in its chunk")
    indices = [tuple(data[o + i * 4:o + i * 4 + 4]) for i in range(vert_count)]
    o += vert_count * 4
    weights = [struct.unpack_from("<4f", data, o + i * 16) for i in range(vert_count)]
    o += vert_count * 16
    matrices = tuple(struct.unpack_from("<16f", data, o + 64 * i) for i in range(bones))
    return bones, indices, weights, matrices


def parse_skeleton(data):
    clump = first(data, root(data), CLUMP)
    ids = _hierarchy_ids(data, clump)
    geometry = _only_geometry(data, clump)
    *_, vert_count = _read_geometry(data, geometry)
    bones, _indices, _weights, matrices = _read_skin(data, geometry, vert_count)
    if bones != len(ids):
        raise FormatError(f"the skeleton has {len(ids)} nodes but its skin has {bones} matrices")
    return Skeleton(ids, matrices)


def clump_count(data):
    """How many clumps a file holds: the body parts of player.img have three, one per build of CJ (normal, fat and thin)."""
    count, offset = 0, 0
    while offset + 12 <= len(data):
        kind, size = struct.unpack_from("<II", data, offset)
        if kind == CLUMP:
            count += 1
        offset += 12 + size  # the zero bytes the archive pads a file with are empty chunks: they are stepped over, not a sign of the end
    return count


def nth_clump(data, index):
    """The bytes of the clump number `index` (from 0) of a file, as a file of its own."""
    seen, offset = 0, 0
    while offset + 12 <= len(data):
        kind, size = struct.unpack_from("<II", data, offset)
        if kind == CLUMP:
            if seen == index:
                return data[offset:offset + 12 + size]
            seen += 1
        offset += 12 + size
    raise FormatError(f"the file has {seen} clumps, not {index + 1}")


def parse_part(data, clump_index=0):
    if clump_index:
        data = nth_clump(data, clump_index)
    clump = first(data, root(data), CLUMP)
    ids = _hierarchy_ids(data, clump)
    geometry = _only_geometry(data, clump)
    positions, normals, uvs, triangles, vert_count = _read_geometry(data, geometry)
    _bones, indices, weights, _matrices = _read_skin(data, geometry, vert_count)
    return Part(positions, normals, uvs, triangles, indices, weights, ids)


@dataclass(frozen=True)
class StaticPart:
    positions: list
    normals: list
    uvs: list
    triangles: list
    texture: str  # the texture of the first group
    groups: tuple = ()  # ((texture name, triangles), ...): the triangles that use each texture, in order of first use


def _frames(data, clump):
    st = first(data, first(data, clump, FRAME_LIST), STRUCT)
    count = struct.unpack_from("<I", data, st.start)[0]
    frames = []
    for i in range(count):
        values = struct.unpack_from("<12f", data, st.start + 4 + i * 56)
        parent = struct.unpack_from("<i", data, st.start + 4 + i * 56 + 48)[0]
        frames.append((parent, values))
    return frames


def _apply(right, up, at, pos, p):
    return tuple(p[0] * right[k] + p[1] * up[k] + p[2] * at[k] + pos[k] for k in range(3))


def _frame_world(frames, index):
    """The matrix of a child frame, as (right, up, at, position): a point p goes to p.x*right + p.y*up + p.z*at + position.
    Only frames that hang straight from an identity root are supported (the weapons of GTA are built like that)."""
    parent, values = frames[index]
    if parent < 0 or not 0 <= parent < len(frames) or frames[parent][0] != -1 \
            or any(abs(v - e) > 1e-4 for v, e in zip(frames[parent][1], (1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0))):
        raise FormatError("a frame of the model does not hang straight from an identity root frame")
    return values[0:3], values[3:6], values[6:9], values[9:12]


def _material_textures(data, geometry):
    """The texture name of each material of the geometry (None for a material with no texture)."""
    names = []
    for material in all_of(data, first(data, geometry, MATERIAL_LIST), MATERIAL):
        textures = all_of(data, material, TEXTURE)
        if textures:
            raw = first(data, textures[0], STRING)
            names.append(data[raw.start:raw.end].split(b"\0")[0].decode("ascii", "replace"))
        else:
            names.append(None)
    return names


def _static_geometry(data, geometry):
    """(positions, normals, uvs, triangles, texture name of each triangle) of one geometry."""
    positions, normals, uvs, triangles, _count, materials = _read_geometry(data, geometry, allow_tristrip=True, with_materials=True)
    textures = _material_textures(data, geometry)
    names = []
    for m in materials:
        if m >= len(textures):
            raise FormatError(f"a triangle uses the material {m} but the geometry has {len(textures)}")
        if textures[m] is None:
            raise FormatError(f"the material {m} of the geometry has no texture")
        names.append(textures[m])
    if not names:
        raise FormatError("the geometry has no textured material")
    return positions, normals, uvs, triangles, names


def _groups(triangles, names):
    order = []
    for name in names:
        if name not in order:
            order.append(name)
    return tuple((name, tuple(t for t, n in zip(triangles, names) if n == name)) for name in order)


def parse_static_part(data, children=False):
    """The solid geometry of a model with no skeleton (a weapon): the atomic that hangs from the root frame.
    The muzzle flash is another atomic on a child frame and is left out. With `children`, the other atomics are added too
    (moved by their frames), except any that use a texture whose name contains "muzzle": that is how a minigun keeps its barrels."""
    clump = first(data, root(data), CLUMP)
    frames = _frames(data, clump)
    geometries = all_of(data, first(data, clump, GEOMETRY_LIST), GEOMETRY)
    atomics = []
    for atomic in all_of(data, clump, ATOMIC):
        st = first(data, atomic, STRUCT)
        frame, geometry = struct.unpack_from("<II", data, st.start)
        if frame < len(frames) and geometry < len(geometries):
            atomics.append((frame, geometries[geometry]))
    body = next(((f, g) for f, g in atomics if frames[f][0] == -1), None)
    if body is None:
        raise FormatError("no geometry hangs from the root frame")
    identity = (1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0)
    if any(abs(v - e) > 1e-4 for v, e in zip(frames[body[0]][1], identity)):
        raise FormatError("the root frame of the model is not the identity; the model would need a transform")
    positions, normals, uvs, triangles, names = _static_geometry(data, body[1])
    if children:
        count = len(positions)
        normals = list(normals) or [(0.0, 0.0, 1.0)] * count
        uvs = list(uvs) or [(0.0, 0.0)] * count
        positions, triangles, names = list(positions), list(triangles), list(names)
        for frame, geometry in atomics:
            if (frame, geometry) == body or any("muzzle" in n for n in _material_textures(data, geometry) if n):
                continue
            p, n, u, t, tn = _static_geometry(data, geometry)
            right, up, at, pos = _frame_world(frames, frame)
            base = len(positions)
            positions += [_apply(right, up, at, pos, v) for v in p]
            normals += [_apply(right, up, at, (0, 0, 0), v) for v in n] if n else [(0.0, 0.0, 1.0)] * len(p)
            uvs += list(u) if u else [(0.0, 0.0)] * len(p)
            triangles += [(a + base, b + base, c + base) for a, b, c in t]
            names += tn
    groups = _groups(triangles, names)
    return StaticPart(positions, normals, uvs, triangles, groups[0][0], groups)


FRAME_NAME = 0x253F2FE  # the extension chunk that holds the name of a frame


@dataclass(frozen=True)
class Frame:
    name: str          # "" when the file names no frames (the weapons)
    parent: int        # index of the parent frame, -1 for the root
    local: tuple       # the 12 floats of its own matrix: right, up, at, position
    right: tuple       # its axes and position in the model, after the matrices of its parents
    up: tuple
    at: tuple
    position: tuple


def _compose(parent, local):
    """The matrix of a frame in the model: a point p of the frame goes to p.x*right + p.y*up + p.z*at + position."""
    p_right, p_up, p_at, p_pos = parent
    right, up, at, pos = local
    return (_apply(p_right, p_up, p_at, (0, 0, 0), right), _apply(p_right, p_up, p_at, (0, 0, 0), up),
            _apply(p_right, p_up, p_at, (0, 0, 0), at), _apply(p_right, p_up, p_at, p_pos, pos))


def parse_frames(data):
    """Every frame of a model with its name, its parent and its place in the model (the matrices of its parents applied)."""
    clump = first(data, root(data), CLUMP)
    raw = _frames(data, clump)
    names = []
    for ext in [c for c in iter_chunks(data, first(data, clump, FRAME_LIST).start, first(data, clump, FRAME_LIST).end) if c.type == EXTENSION]:
        name = ""
        for sub in iter_chunks(data, ext.start, ext.end):
            if sub.type == FRAME_NAME:
                name = data[sub.start:sub.end].split(b"\0")[0].decode("ascii", "replace")
        names.append(name)
    names += [""] * (len(raw) - len(names))
    frames = []
    worlds = []
    for index, (parent, values) in enumerate(raw):
        if not -1 <= parent < index:
            raise FormatError(f"frame {index} has the parent {parent}: frames must come after their parents")
        local = (tuple(values[0:3]), tuple(values[3:6]), tuple(values[6:9]), tuple(values[9:12]))
        world = local if parent < 0 else _compose(worlds[parent], local)
        worlds.append(world)
        frames.append(Frame(names[index], parent, tuple(values), world[0], world[1], world[2], world[3]))
    return frames


@dataclass(frozen=True)
class Material:
    texture: str  # the name of its texture, None when it is only a colour
    color: tuple  # (r, g, b, a)


def parse_materials(data, geometry):
    """The materials of a geometry: the texture name (None for none) and the colour of each."""
    out = []
    for material, texture in zip(all_of(data, first(data, geometry, MATERIAL_LIST), MATERIAL), _material_textures(data, geometry)):
        st = first(data, material, STRUCT)
        out.append(Material(texture, tuple(data[st.start + 4:st.start + 8])))
    return out


def parse_vehicle_geometry(data):
    """(frames, atomics) of a model: each atomic is (frame index, positions, normals, uvs, triangles, per-triangle material index, materials)."""
    clump = first(data, root(data), CLUMP)
    geometries = all_of(data, first(data, clump, GEOMETRY_LIST), GEOMETRY)
    atomics = []
    for atomic in all_of(data, clump, ATOMIC):
        st = first(data, atomic, STRUCT)
        frame, geometry = struct.unpack_from("<II", data, st.start)
        if geometry >= len(geometries):
            raise FormatError(f"an atomic uses the geometry {geometry} but the model has {len(geometries)}")
        positions, normals, uvs, triangles, _count, per_triangle = _read_geometry(data, geometries[geometry], allow_tristrip=True, with_materials=True)
        materials = parse_materials(data, geometries[geometry])
        for m in per_triangle:
            if m >= len(materials):
                raise FormatError(f"a triangle uses the material {m} but the geometry has {len(materials)}")
        atomics.append((frame, positions, normals, uvs, triangles, per_triangle, materials))
    return parse_frames(data), atomics
