import struct
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

from cjconvert import glb  # noqa: E402
from cjconvert.assemble import MeshPart, Model  # noqa: E402
from cjconvert.dff import Skeleton  # noqa: E402
from cjconvert.txd import Texture  # noqa: E402

# a rigid RwMatrix: right=(0,1,0) up=(-1,0,0) at=(0,0,1), position (1,2,3); a pad float follows each row
RW = (0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 2.0, 3.0, 1.0)
RW_IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)


def make_model():
    skeleton = Skeleton((0, 5, 1), (RW, RW_IDENT, RW_IDENT))
    part = MeshPart(
        "torso",
        [(0.0, 0.0, 0.0), (1.0, 0.0, 0.5), (0.0, 2.0, -1.0), (1.0, 2.0, 0.0)],
        [(0.0, 0.0, 1.0)] * 4,
        [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0), (1.0, 1.0)],
        [(0, 1, 2), (2, 1, 3)],
        [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0), (1, 0, 0, 0)],
        [(1.0, 0.0, 0.0, 0.0), (0.25, 0.75, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)],
        "tex:a")
    hands = MeshPart("hands", part.positions, part.normals, part.uvs, part.triangles, part.joints, part.weights, "tex:a")
    tex = Texture("a", 2, 1, bytes([255, 0, 0, 255, 0, 255, 0, 255]))
    return Model(skeleton, [part, hands], {"tex:a": tex})


class MatrixTests(unittest.TestCase):
    def test_rw_matrix_becomes_a_column_major_matrix(self):
        self.assertEqual(glb.rw_matrix_to_column_major(RW),
                         [0, 1, 0, 0, -1, 0, 0, 0, 0, 0, 1, 0, 1, 2, 3, 1])

    def test_inverse_times_matrix_is_the_identity(self):
        m = glb.rw_matrix_to_column_major(RW)
        inv = glb.mat4_inverse(m)
        for c in range(4):
            for r in range(4):
                total = sum(m[k * 4 + r] * inv[c * 4 + k] for k in range(4))  # (m * inv)[r][c]
                self.assertAlmostEqual(total, 1.0 if r == c else 0.0, places=5)

    def test_a_singular_matrix_is_rejected(self):
        with self.assertRaises(ValueError):
            glb.mat4_inverse([0.0] * 16)


class GlbTests(unittest.TestCase):
    def setUp(self):
        self.model = make_model()
        self.data = glb.to_glb(self.model)
        self.gltf, self.bin = glb.read_glb(self.data)

    def test_container_is_a_valid_glb(self):
        magic, version, total = struct.unpack_from("<III", self.data, 0)
        self.assertEqual((magic, version, total), (0x46546C67, 2, len(self.data)))
        json_len, json_type = struct.unpack_from("<II", self.data, 12)
        self.assertEqual(json_type, 0x4E4F534A)
        self.assertEqual(json_len % 4, 0)
        bin_len, bin_type = struct.unpack_from("<II", self.data, 20 + json_len)
        self.assertEqual(bin_type, 0x004E4942)
        self.assertEqual(bin_len % 4, 0)
        self.assertEqual(20 + json_len + 8 + bin_len, len(self.data))

    def test_output_is_deterministic(self):
        self.assertEqual(glb.to_glb(make_model()), self.data)

    def test_joints_follow_the_skeleton_order_and_carry_their_node_id(self):
        g = self.gltf
        joints = g["skins"][0]["joints"]
        self.assertEqual([g["nodes"][j]["extras"]["nodeId"] for j in joints], [0, 5, 1])
        self.assertEqual([g["nodes"][j]["name"] for j in joints], ["bone_0", "bone_5", "bone_1"])
        self.assertEqual(g["nodes"][0]["skin"], 0)

    def test_joints_hang_from_one_skeleton_root(self):
        # Review Focus #5: a strict glTF reader (the Khronos validator) requires the joints of a skin to share a root
        g = self.gltf
        skin = g["skins"][0]
        root = skin["skeleton"]
        self.assertEqual(g["nodes"][root]["children"], skin["joints"])
        self.assertNotIn("matrix", g["nodes"][root])  # an identity root: the joint matrices stay in model space
        self.assertEqual(g["scenes"][0]["nodes"], [0, root])
        self.assertTrue(all(j not in g["scenes"][0]["nodes"] for j in skin["joints"]))

    def test_joint_nodes_are_at_their_bind_pose(self):
        g = self.gltf
        inverse_bind = glb.read_accessor(g, self.bin, g["skins"][0]["inverseBindMatrices"])
        for k, joint in enumerate(g["skins"][0]["joints"]):
            node = g["nodes"][joint]["matrix"]
            ib = inverse_bind[k]
            for c in range(4):
                for r in range(4):
                    total = sum(node[kk * 4 + r] * ib[c * 4 + kk] for kk in range(4))
                    self.assertAlmostEqual(total, 1.0 if r == c else 0.0, places=5)

    def test_every_accessor_is_well_formed(self):
        # Review Focus #5
        g = self.gltf
        for view in g["bufferViews"]:
            self.assertEqual(view["byteOffset"] % 4, 0)
            self.assertLessEqual(view["byteOffset"] + view["byteLength"], len(self.bin))
        self.assertEqual(g["buffers"][0]["byteLength"], len(self.bin))
        sizes = {5120: 1, 5121: 1, 5122: 2, 5123: 2, 5125: 4, 5126: 4}
        widths = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4, "MAT4": 16}
        for acc in g["accessors"]:
            view = g["bufferViews"][acc["bufferView"]]
            self.assertLessEqual(acc.get("byteOffset", 0) + acc["count"] * sizes[acc["componentType"]] * widths[acc["type"]],
                                 view["byteLength"])

    def test_primitives(self):
        g = self.gltf
        self.assertEqual(len(g["meshes"][0]["primitives"]), 2)
        prim = g["meshes"][0]["primitives"][0]
        attrs = prim["attributes"]
        pos = glb.read_accessor(g, self.bin, attrs["POSITION"])
        self.assertEqual(pos, [tuple(p) for p in self.model.parts[0].positions])
        self.assertEqual(glb.read_accessor(g, self.bin, attrs["TEXCOORD_0"]), [tuple(u) for u in self.model.parts[0].uvs])
        idx = glb.read_accessor(g, self.bin, prim["indices"])
        self.assertEqual(idx, [0, 1, 2, 2, 1, 3])
        self.assertTrue(all(i < len(pos) for i in idx))
        joints = glb.read_accessor(g, self.bin, attrs["JOINTS_0"])
        weights = glb.read_accessor(g, self.bin, attrs["WEIGHTS_0"])
        self.assertEqual(joints, [tuple(j) for j in self.model.parts[0].joints])  # the joints survive the round trip
        self.assertTrue(all(max(j) < 3 for j in joints))
        self.assertTrue(all(abs(sum(w) - 1.0) < 1e-5 for w in weights))

    def test_position_min_and_max_are_present_and_right(self):
        acc = self.gltf["accessors"][self.gltf["meshes"][0]["primitives"][0]["attributes"]["POSITION"]]
        self.assertEqual(acc["min"], [0.0, 0.0, -1.0])
        self.assertEqual(acc["max"], [1.0, 2.0, 0.5])

    def test_the_texture_is_embedded_as_png_and_shared(self):
        g = self.gltf
        self.assertEqual(len(g["images"]), 1)
        view = g["bufferViews"][g["images"][0]["bufferView"]]
        png = self.bin[view["byteOffset"]:view["byteOffset"] + view["byteLength"]]
        self.assertEqual(g["images"][0]["mimeType"], "image/png")
        self.assertEqual(png[:8], b"\x89PNG\r\n\x1a\n")
        self.assertEqual(len(g["materials"]), 1)
        self.assertEqual([p["material"] for p in g["meshes"][0]["primitives"]], [0, 0])
        self.assertEqual(g["asset"]["extras"]["space"], "GTA clump space: X forward, Y left, Z up")

    def test_a_part_with_too_many_vertices_is_rejected(self):
        # Review Focus #5: indices are 16-bit
        big = MeshPart("big", [(0.0, 0.0, 0.0)] * 70000, [(0.0, 0.0, 1.0)] * 70000, [(0.0, 0.0)] * 70000, [(0, 1, 2)],
                       [(0, 0, 0, 0)] * 70000, [(1.0, 0.0, 0.0, 0.0)] * 70000, "tex:a")
        with self.assertRaises(ValueError):
            glb.to_glb(Model(self.model.skeleton, [big], self.model.textures))

    def test_read_glb_rejects_garbage(self):
        for bad in (b"", b"glTF", b"\0" * 64, self.data[:30]):
            with self.assertRaises(ValueError):
                glb.read_glb(bad)


if __name__ == "__main__":
    unittest.main()
