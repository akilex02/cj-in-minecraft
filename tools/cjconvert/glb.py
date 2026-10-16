"""Writer (and a small reader, for tests and the preview) of glTF binary files."""
import json
import struct

from .txd import encode_png

GLB_MAGIC = 0x46546C67
CHUNK_JSON = 0x4E4F534A
CHUNK_BIN = 0x004E4942
FLOAT, UNSIGNED_BYTE, UNSIGNED_SHORT = 5126, 5121, 5123
ARRAY_BUFFER, ELEMENT_ARRAY_BUFFER = 34962, 34963
COMPONENT_SIZE = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
COMPONENT_FORMAT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
TYPE_WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
MAX_VERTICES = 65535
SPACE = "GTA clump space: X forward, Y left, Z up"


def rw_matrix_to_column_major(m):
    """The 16 floats of an RwMatrix (right, up, at, pos, each followed by a pad float) as a column-major 4x4."""
    return [m[0], m[1], m[2], 0.0, m[4], m[5], m[6], 0.0, m[8], m[9], m[10], 0.0, m[12], m[13], m[14], 1.0]


def mat4_inverse(m):
    """Inverse of a column-major 4x4 by Gauss-Jordan elimination; ValueError if it is singular."""
    a = [[m[c * 4 + r] for c in range(4)] + [1.0 if r == k else 0.0 for k in range(4)] for r in range(4)]
    for col in range(4):
        pivot = max(range(col, 4), key=lambda r: abs(a[r][col]))
        if abs(a[pivot][col]) < 1e-12:
            raise ValueError("the matrix is singular")
        a[col], a[pivot] = a[pivot], a[col]
        scale = a[col][col]
        a[col] = [x / scale for x in a[col]]
        for r in range(4):
            if r != col:
                factor = a[r][col]
                a[r] = [x - factor * y for x, y in zip(a[r], a[col])]
    return [a[r][4 + c] for c in range(4) for r in range(4)]


class _Buffer:
    def __init__(self):
        self.data = bytearray()
        self.views = []
        self.accessors = []

    def view(self, payload, target=None):
        while len(self.data) % 4:
            self.data.append(0)
        entry = {"buffer": 0, "byteOffset": len(self.data), "byteLength": len(payload)}
        if target:
            entry["target"] = target
        self.data += payload
        self.views.append(entry)
        return len(self.views) - 1

    def accessor(self, payload, component, count, kind, target=None, bounds=None):
        entry = {"bufferView": self.view(payload, target), "componentType": component, "count": count, "type": kind}
        if bounds:
            entry["min"], entry["max"] = bounds
        self.accessors.append(entry)
        return len(self.accessors) - 1


def _pack(fmt, values):
    return b"".join(struct.pack(fmt, *v) if isinstance(v, (tuple, list)) else struct.pack(fmt, v) for v in values)


def to_glb(model):
    buf = _Buffer()
    joint_count = len(model.skeleton.node_ids)
    nodes = [{"name": "CJ", "mesh": 0, "skin": 0}, {"name": "skeleton"}]  # node 1: the common root of every joint
    inverse_bind = []
    for node_id, rw in zip(model.skeleton.node_ids, model.skeleton.inverse_bind):
        column_major = rw_matrix_to_column_major(rw)
        inverse_bind.append(column_major)
        nodes.append({"name": f"bone_{node_id}", "matrix": mat4_inverse(column_major), "extras": {"nodeId": node_id}})
    joints = list(range(2, joint_count + 2))
    nodes[1]["children"] = joints
    ibm = buf.accessor(_pack("<16f", inverse_bind), FLOAT, joint_count, "MAT4")

    texture_keys = list(model.textures)
    images, materials, textures = [], [], []
    for key in texture_keys:
        t = model.textures[key]
        images.append({"bufferView": buf.view(encode_png(t.width, t.height, t.rgba)), "mimeType": "image/png", "name": key})
        textures.append({"sampler": 0, "source": len(images) - 1})
        materials.append({"name": key, "pbrMetallicRoughness": {
            "baseColorTexture": {"index": len(textures) - 1}, "baseColorFactor": [1, 1, 1, 1],
            "metallicFactor": 0.0, "roughnessFactor": 1.0}})

    primitives = []
    for part in model.parts:
        n = len(part.positions)
        if n > MAX_VERTICES:
            raise ValueError(f"{part.name} has {n} vertices; glTF indices are 16-bit")
        bounds = ([min(p[i] for p in part.positions) for i in range(3)], [max(p[i] for p in part.positions) for i in range(3)])
        attributes = {
            "POSITION": buf.accessor(_pack("<3f", part.positions), FLOAT, n, "VEC3", ARRAY_BUFFER, bounds),
            "NORMAL": buf.accessor(_pack("<3f", part.normals), FLOAT, n, "VEC3", ARRAY_BUFFER),
            "TEXCOORD_0": buf.accessor(_pack("<2f", part.uvs), FLOAT, n, "VEC2", ARRAY_BUFFER),
            "JOINTS_0": buf.accessor(_pack("<4B", part.joints), UNSIGNED_BYTE, n, "VEC4", ARRAY_BUFFER),
            "WEIGHTS_0": buf.accessor(_pack("<4f", part.weights), FLOAT, n, "VEC4", ARRAY_BUFFER),
        }
        flat = [i for tri in part.triangles for i in tri]
        indices = buf.accessor(_pack("<H", flat), UNSIGNED_SHORT, len(flat), "SCALAR", ELEMENT_ARRAY_BUFFER)
        primitives.append({"attributes": attributes, "indices": indices, "material": texture_keys.index(part.texture), "mode": 4})

    gltf = {
        "asset": {"version": "2.0", "generator": "cjconvert", "extras": {"space": SPACE, "parts": [p.name for p in model.parts], **model.extras}},
        "scene": 0,
        "scenes": [{"nodes": [0, 1]}],
        "nodes": nodes,
        "meshes": [{"name": "CJ", "primitives": primitives}],
        "skins": [{"joints": joints, "skeleton": 1, "inverseBindMatrices": ibm}],
        "materials": materials,
        "textures": textures,
        "images": images,
        "samplers": [{"magFilter": 9728, "minFilter": 9728, "wrapS": 10497, "wrapT": 10497}],
        "accessors": buf.accessors,
        "bufferViews": buf.views,
        "buffers": [{"byteLength": len(buf.data) + (-len(buf.data)) % 4}],
    }
    json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_bytes += b" " * (-len(json_bytes) % 4)
    bin_bytes = bytes(buf.data) + b"\0" * (-len(buf.data) % 4)
    total = 12 + 8 + len(json_bytes) + 8 + len(bin_bytes)
    return (struct.pack("<III", GLB_MAGIC, 2, total) + struct.pack("<II", len(json_bytes), CHUNK_JSON) + json_bytes
            + struct.pack("<II", len(bin_bytes), CHUNK_BIN) + bin_bytes)


def read_glb(data):
    if len(data) < 20 or struct.unpack_from("<I", data, 0)[0] != GLB_MAGIC:
        raise ValueError("not a GLB file")
    version, total = struct.unpack_from("<II", data, 4)
    if version != 2 or total != len(data):
        raise ValueError("unsupported or truncated GLB")
    json_len, json_type = struct.unpack_from("<II", data, 12)
    if json_type != CHUNK_JSON or 20 + json_len + 8 > len(data):
        raise ValueError("the GLB has no JSON chunk")
    gltf = json.loads(data[20:20 + json_len].decode("utf-8"))
    bin_len, bin_type = struct.unpack_from("<II", data, 20 + json_len)
    if bin_type != CHUNK_BIN or 20 + json_len + 8 + bin_len > len(data):
        raise ValueError("the GLB has no BIN chunk")
    return gltf, data[28 + json_len:28 + json_len + bin_len]


def read_accessor(gltf, binary, index):
    acc = gltf["accessors"][index]
    view = gltf["bufferViews"][acc["bufferView"]]
    width = TYPE_WIDTH[acc["type"]]
    fmt = "<" + COMPONENT_FORMAT[acc["componentType"]] * width
    size = struct.calcsize(fmt)
    start = view["byteOffset"] + acc.get("byteOffset", 0)
    out = [struct.unpack_from(fmt, binary, start + i * size) for i in range(acc["count"])]
    return [v[0] for v in out] if width == 1 else out
