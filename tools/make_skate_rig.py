#!/usr/bin/env python3
"""Reads the skater's rig out of the user's own converted Skate 3 files (assets/private/skater.glb) and writes the rest pose the mod needs to pose CJ with the
skater's bones: for each joint, its name and the rotation of its rest pose in the world of the rig (3x3, column by column) -> skate_rig.json; and the
skateboard (deck, trucks and wheels: their meshes, the skin to the board's bones and their textures) -> skate_board.json and skate_board_<n>.png.
Nothing of the skater's own body is written.
The GLB carries Blender's bone axes (y along the bone); the engine poses the bones in their native axes, so the rest rotation is turned back to those
(as the GTA San AnSkateas plugin's board_export.rs does): without it the hips of the pose come out a quarter turn off.
Usage: make_skate_rig.py <skater.glb> <out folder (the mod's config/cj_fabric)>"""
import json
import struct
import sys
from pathlib import Path


def read_glb(path):
    """(the JSON of a .glb, its binary chunk)"""
    data = open(path, "rb").read()
    length = struct.unpack_from("<I", data, 12)[0]
    js = json.loads(data[20:20 + length])
    rest = data[20 + length:]
    binary = rest[8:8 + struct.unpack_from("<I", rest, 0)[0]] if len(rest) >= 8 else b""
    return js, binary


COMPONENT = {5120: "b", 5121: "B", 5122: "h", 5123: "H", 5125: "I", 5126: "f"}
WIDTH = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}


def accessor(js, binary, index):
    """The values of an accessor as a flat list (tightly packed buffer views only, as the converter writes them)."""
    a = js["accessors"][index]
    view = js["bufferViews"][a["bufferView"]]
    fmt = COMPONENT[a["componentType"]]
    count = a["count"] * WIDTH[a["type"]]
    offset = view.get("byteOffset", 0) + a.get("byteOffset", 0)
    return list(struct.unpack_from(f"<{count}{fmt}", binary, offset))


# Blender's bone basis (columns x, -z, y) as a 4x4 by rows: the native inverse bind is this times the GLB's
BLENDER_BASIS = [[1, 0, 0, 0], [0, 0, 1, 0], [0, -1, 0, 0], [0, 0, 0, 1]]


def board(js, binary):
    """The skateboard's parts (the primitives whose material is the board's, its trucks' or its wheels'), the native inverse bind of every joint (16 floats,
    column-major, by joint index) and the PNGs of their textures: (board.json as a dict, {file name: bytes})."""
    ibm = accessor(js, binary, js["skins"][0]["inverseBindMatrices"])
    inverse_binds = {}
    for j in range(len(ibm) // 16):
        m = [[ibm[j * 16 + c * 4 + r] for c in range(4)] for r in range(4)]
        native_inverse = matmul(BLENDER_BASIS, m)
        inverse_binds[str(j)] = [round(native_inverse[r][c], 6) for c in range(4) for r in range(4)]
    parts, images = [], {}
    for primitive in js["meshes"][0]["primitives"]:
        material = js["materials"][primitive["material"]]
        if not material.get("name", "").startswith("Retail_Skate"):
            continue
        attributes = primitive["attributes"]
        texture = material.get("pbrMetallicRoughness", {}).get("baseColorTexture", {}).get("index")
        image_file = None
        if texture is not None:
            image = js["images"][js["textures"][texture]["source"]]
            view = js["bufferViews"][image["bufferView"]]
            image_file = f"skate_board_{len(images)}.png"
            images[image_file] = binary[view.get("byteOffset", 0):view.get("byteOffset", 0) + view["byteLength"]]
        parts.append({"name": material["name"], "texture": image_file,
                      "positions": [round(v, 5) for v in accessor(js, binary, attributes["POSITION"])],
                      "normals": [round(v, 4) for v in accessor(js, binary, attributes["NORMAL"])],
                      "uvs": [round(v, 5) for v in accessor(js, binary, attributes["TEXCOORD_0"])],
                      "joints": accessor(js, binary, attributes["JOINTS_0"]),
                      "weights": [round(v, 4) for v in accessor(js, binary, attributes["WEIGHTS_0"])],
                      "indices": accessor(js, binary, primitive["indices"])})
    return {"parts": parts, "inverseBinds": inverse_binds}, images


def matmul(a, b):
    return [[sum(a[r][k] * b[k][c] for k in range(4)) for c in range(4)] for r in range(4)]


def local_matrix(node):
    if "matrix" in node:
        m = node["matrix"]  # column-major
        return [[m[c * 4 + r] for c in range(4)] for r in range(4)]
    x, y, z, w = node.get("rotation", [0, 0, 0, 1])
    t = node.get("translation", [0, 0, 0])
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w), t[0]],
            [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w), t[1]],
            [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y), t[2]],
            [0, 0, 0, 1]]


# Blender's bone basis in the native one: the native rest rotation is the GLB's times the inverse of this (columns x, z, -y)
BLENDER_BASIS_INVERSE = [[1, 0, 0], [0, 0, -1], [0, 1, 0]]


def native(rotation):
    """The 3x3 rotation (rows) of a GLB joint turned to the engine's own bone axes."""
    return [[sum(rotation[r][k] * BLENDER_BASIS_INVERSE[k][c] for k in range(3)) for c in range(3)] for r in range(3)]


def rig(js):
    nodes = js["nodes"]
    world = {}

    def walk(i, parent):
        world[i] = matmul(parent, local_matrix(nodes[i]))
        for c in nodes[i].get("children", []):
            walk(c, world[i])

    identity = [[1 if r == c else 0 for c in range(4)] for r in range(4)]
    for root in js["scenes"][0]["nodes"]:
        walk(root, identity)
    out = []
    for joint in js["skins"][0]["joints"]:
        m = world[joint]
        rotation = native([row[:3] for row in m[:3]])
        out.append({"name": nodes[joint].get("name", ""),
                    "rotation": [round(rotation[r][c], 6) for c in range(3) for r in range(3)],  # column-major
                    "position": [round(m[r][3], 6) for r in range(3)]})
    return {"joints": out}


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    js, binary = read_glb(argv[1])
    out = Path(argv[2])
    out.mkdir(parents=True, exist_ok=True)
    result = rig(js)
    (out / "skate_rig.json").write_text(json.dumps(result))
    board_json, images = board(js, binary)
    (out / "skate_board.json").write_text(json.dumps(board_json))
    for name, data in images.items():
        (out / name).write_bytes(data)
    print(f"{len(result['joints'])} joints -> {out / 'skate_rig.json'}; the board: {len(board_json['parts'])} parts, {len(images)} textures")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
