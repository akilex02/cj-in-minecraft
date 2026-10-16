import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import make_skate_rig  # noqa: E402


class MakeSkateRigTest(unittest.TestCase):
    def test_a_joint_at_rest_in_blender_axes_comes_out_in_the_native_ones(self):
        # one joint, its GLB rotation the identity: natively its axes are Blender's basis undone (x stays, y -> z, z -> -y)
        js = {"nodes": [{"name": "HIPS", "matrix": [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0.9, 0, 1]}],
              "scenes": [{"nodes": [0]}], "skins": [{"joints": [0]}]}
        joint = make_skate_rig.rig(js)["joints"][0]
        self.assertEqual(joint["name"], "HIPS")
        self.assertEqual(joint["position"], [0, 0.9, 0])
        self.assertEqual(joint["rotation"], [1, 0, 0, 0, 0, 1, 0, -1, 0])

    def test_native_undoes_the_basis(self):
        basis = [[1, 0, 0], [0, 0, 1], [0, -1, 0]]  # columns x, z, -y
        self.assertEqual(make_skate_rig.native(basis), [[1, 0, 0], [0, 1, 0], [0, 0, 1]])


    def test_the_board_takes_only_the_skateboard_parts_with_native_inverse_binds_and_textures(self):
        import struct
        chunks, views, accessors = [], [], []

        def add(fmt, values, kind, component, count):
            data = struct.pack(f"<{len(values)}{fmt}", *values)
            views.append({"buffer": 0, "byteOffset": sum(len(c) for c in chunks), "byteLength": len(data)})
            chunks.append(data)
            accessors.append({"bufferView": len(views) - 1, "componentType": component, "count": count, "type": kind})
            return len(accessors) - 1

        ibm = add("f", [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, -0.1, 0, 1], "MAT4", 5126, 1)
        attributes = {"POSITION": add("f", [0, 0, 0, 1, 0, 0, 0, 0, 1], "VEC3", 5126, 3),
                      "NORMAL": add("f", [0, 1, 0] * 3, "VEC3", 5126, 3),
                      "TEXCOORD_0": add("f", [0, 0, 1, 0, 0, 1], "VEC2", 5126, 3),
                      "JOINTS_0": add("B", [0, 0, 0, 0] * 3, "VEC4", 5121, 3),
                      "WEIGHTS_0": add("f", [1, 0, 0, 0] * 3, "VEC4", 5126, 3)}
        indices = add("H", [0, 1, 2], "SCALAR", 5123, 3)
        png = b"\x89PNG fake"
        views.append({"buffer": 0, "byteOffset": sum(len(c) for c in chunks), "byteLength": len(png)})
        chunks.append(png)
        js = {"accessors": accessors, "bufferViews": views, "skins": [{"joints": [0], "inverseBindMatrices": ibm}],
              "images": [{"bufferView": len(views) - 1, "mimeType": "image/png"}], "textures": [{"source": 0}],
              "materials": [{"name": "Retail_Pants"}, {"name": "Retail_SkateBoard", "pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}}],
              "meshes": [{"primitives": [{"material": 0, "attributes": attributes, "indices": indices},
                                         {"material": 1, "attributes": attributes, "indices": indices}]}]}
        result, images = make_skate_rig.board(js, b"".join(chunks))
        self.assertEqual([p["name"] for p in result["parts"]], ["Retail_SkateBoard"])  # the skater's pants are not taken
        part = result["parts"][0]
        self.assertEqual(part["indices"], [0, 1, 2])
        self.assertEqual(part["texture"], "skate_board_0.png")
        self.assertEqual(images, {"skate_board_0.png": png})
        # the GLB's inverse bind (a move of -0.1 in y) turned to the native axes: Blender's basis times it moves -0.1 in -z (its y row goes to z)
        self.assertEqual(result["inverseBinds"]["0"], [1, 0, 0, 0, 0, 0, -1, 0, 0, 1, 0, 0, 0, 0, 0.1, 1])


if __name__ == "__main__":
    unittest.main()
