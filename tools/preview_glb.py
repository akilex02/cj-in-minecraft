#!/usr/bin/env python3
"""Software preview of cj.glb: three orthographic views (front, side, back) so the conversion can be checked by eye."""
import io
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert.glb import read_accessor, read_glb  # noqa: E402

BACKGROUND = (60, 64, 72)
# Model space is X forward, Y left, Z up. For each view: where the camera is, the screen's right axis and the depth axis.
VIEWS = (
    ("front", np.array([0.0, 1.0, 0.0]), np.array([1.0, 0.0, 0.0])),   # camera ahead of CJ: his left is on the screen's right
    ("side", np.array([-1.0, 0.0, 0.0]), np.array([0.0, 1.0, 0.0])),   # camera on his left: he faces the screen's left
    ("back", np.array([0.0, -1.0, 0.0]), np.array([-1.0, 0.0, 0.0])),  # camera behind him
)


def _load(data):
    gltf, binary = read_glb(data)
    textures = []
    for image in gltf["images"]:
        view = gltf["bufferViews"][image["bufferView"]]
        png = binary[view["byteOffset"]:view["byteOffset"] + view["byteLength"]]
        textures.append(np.array(Image.open(io.BytesIO(png)).convert("RGBA")))
    parts = []
    for prim in gltf["meshes"][0]["primitives"]:
        attrs = prim["attributes"]
        pos = np.array(read_accessor(gltf, binary, attrs["POSITION"]), dtype=np.float64)
        uv = np.array(read_accessor(gltf, binary, attrs["TEXCOORD_0"]), dtype=np.float64)
        idx = np.array(read_accessor(gltf, binary, prim["indices"]), dtype=np.int64).reshape(-1, 3)
        texture = textures[gltf["textures"][gltf["materials"][prim["material"]]["pbrMetallicRoughness"]["baseColorTexture"]["index"]]["source"]]
        parts.append((pos, uv, idx, texture))
    return parts


def _render(parts, right, toward_camera, scale, size, centre):
    up = np.array([0.0, 0.0, 1.0])
    height = size
    image = np.zeros((height, size, 3), dtype=np.uint8)
    image[:] = BACKGROUND
    depth = np.full((height, size), -np.inf)
    for pos, uv, idx, texture in parts:
        sx = (pos - centre) @ right * scale + size / 2
        sy = height / 2 - (pos - centre) @ up * scale
        sz = pos @ toward_camera
        th, tw = texture.shape[:2]
        for tri in idx:
            xs, ys, zs = sx[tri], sy[tri], sz[tri]
            x0, x1 = max(int(np.floor(xs.min())), 0), min(int(np.ceil(xs.max())) + 1, size)
            y0, y1 = max(int(np.floor(ys.min())), 0), min(int(np.ceil(ys.max())) + 1, height)
            if x0 >= x1 or y0 >= y1:
                continue
            det = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(det) < 1e-9:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / det
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / det
            w2 = 1.0 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            if not inside.any():
                continue
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            region = depth[y0:y1, x0:x1]
            visible = inside & (z > region)
            if not visible.any():
                continue
            u = w0 * uv[tri[0], 0] + w1 * uv[tri[1], 0] + w2 * uv[tri[2], 0]
            v = w0 * uv[tri[0], 1] + w1 * uv[tri[1], 1] + w2 * uv[tri[2], 1]
            tx = np.clip((np.mod(u, 1.0) * tw).astype(int), 0, tw - 1)  # textures repeat: the feet use v in [-1, 0]
            ty = np.clip((np.mod(v, 1.0) * th).astype(int), 0, th - 1)
            colour = texture[ty, tx, :3]
            region[visible] = z[visible]
            image[y0:y1, x0:x1][visible] = colour[visible]
    return image


def render_preview(glb_bytes, scale=330):
    parts = _load(glb_bytes)
    everything = np.concatenate([p[0] for p in parts])
    centre = (everything.min(axis=0) + everything.max(axis=0)) / 2
    extent = float(np.abs(everything - centre).max())
    size = int(2 * extent * scale + 40)
    tiles = [_render(parts, right, toward, scale, size, centre) for _, right, toward in VIEWS]
    return Image.fromarray(np.concatenate(tiles, axis=1))


if __name__ == "__main__":
    src = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else src.with_name("cj_preview.png")
    render_preview(src.read_bytes()).save(out)
    print("wrote", out)
