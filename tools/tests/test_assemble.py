import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import assemble, txd  # noqa: E402
from cjconvert.dff import Part, Skeleton  # noqa: E402

IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
SKELETON_IDS = [0, 5, 1]  # deliberately not in the order of the part's hierarchy
BGRA = bytes([0, 0, 255, 255, 0, 255, 0, 255])


def make_part(indices, weights, hierarchy=(0, 1, 5, 303)):
    n = len(indices)
    return Part([(0.0, 0.0, 0.0)] * n, [(0.0, 0.0, 1.0)] * n, [(0.0, 0.0)] * n, [(0, 1, 2)] * (1 if n >= 3 else 0),
                indices, weights, tuple(hierarchy))


SKELETON = Skeleton(tuple(SKELETON_IDS), (IDENT, IDENT, IDENT))


class RemapTests(unittest.TestCase):
    def test_remaps_part_bones_to_skeleton_joints_by_node_id(self):
        # part bone 2 is node 5, which is joint 1 of the skeleton; part bone 1 is node 1, joint 2
        part = make_part([(2, 0, 0, 0), (1, 2, 0, 0), (0, 0, 0, 0)],
                         [(1.0, 0.0, 0.0, 0.0), (0.25, 0.75, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)])
        joints, weights = assemble.remap_part("t", part, SKELETON)
        self.assertEqual(joints[0], (1, 0, 0, 0))
        self.assertEqual(joints[1][:2], (2, 1))
        self.assertEqual(joints[2], (0, 0, 0, 0))
        self.assertAlmostEqual(sum(weights[1]), 1.0)
        self.assertEqual(weights[1][:2], (0.25, 0.75))

    def test_zero_weight_slots_point_at_joint_zero_whatever_their_index(self):
        part = make_part([(1, 3, 3, 3)], [(1.0, 0.0, 0.0, 0.0)])  # bone 3 is node 303, which the skeleton lacks, but unused
        joints, _ = assemble.remap_part("t", part, SKELETON)
        self.assertEqual(joints[0], (2, 0, 0, 0))

    def test_a_weight_on_a_node_the_skeleton_lacks_is_an_error(self):
        # Review Focus #4: never guess a bone
        part = make_part([(3, 0, 0, 0)], [(1.0, 0.0, 0.0, 0.0)])
        with self.assertRaises(assemble.ConversionError) as ctx:
            assemble.remap_part("legs", part, SKELETON)
        self.assertIn("303", str(ctx.exception))
        self.assertIn("legs", str(ctx.exception))

    def test_a_bone_index_past_the_part_hierarchy_is_an_error(self):
        part = make_part([(9, 0, 0, 0)], [(1.0, 0.0, 0.0, 0.0)])
        with self.assertRaises(assemble.ConversionError):
            assemble.remap_part("t", part, SKELETON)

    def test_weights_that_do_not_sum_to_one_are_an_error(self):
        part = make_part([(0, 1, 0, 0)], [(0.5, 0.2, 0.0, 0.0)])
        with self.assertRaises(assemble.ConversionError):
            assemble.remap_part("t", part, SKELETON)

    def test_weights_that_are_slightly_off_are_normalised(self):
        part = make_part([(0, 1, 0, 0)], [(0.504, 0.5, 0.0, 0.0)])
        _, weights = assemble.remap_part("t", part, SKELETON)
        self.assertAlmostEqual(sum(weights[0]), 1.0, places=6)


def make_install(root, outfit_files=None, overlay=False, shapes=1):
    """models/gta3.img with the skeleton and models/player.img with parts and textures, all synthetic."""
    models = Path(root) / "models"
    models.mkdir(parents=True)
    stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)],
                      skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 3))
    (models / "gta3.img").write_bytes(b.img({"player.dff": b.dff(SKELETON_IDS, stub)}))
    files = {}
    for name in ("torso", "hands"):
        sk = b.skin(4, [0, 1], [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0)],
                    [(1.0, 0.0, 0.0, 0.0), (0.5, 0.5, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)], [IDENT] * 4)
        def clump(shift):
            return b.dff([0, 1, 5, 303], b.geometry(
                [(shift, 0, 0), (shift + 1, 0, 0), (shift, 1, 0)], [(0, 0, 1)] * 3, [(0, 0), (1, 0), (0, 1)], [(0, 1, 2)], skin=sk))
        files[name + ".dff"] = b"".join(clump(10.0 * k) for k in range(shapes))
    files["player_torso.txd"] = b.txd(b.texture_native("torso", 2, 1, BGRA), b.texture_native("torso_fat", 2, 1, BGRA))
    if overlay:  # the pixels of a 2 x 1 texture: BGRA bytes
        files["player_torso.txd"] = b.txd(b.texture_native("torso", 2, 1, bytes((30, 20, 10, 255, 30, 20, 10, 255))))
        files["vest.txd"] = b.txd(b.texture_native("vest", 2, 1, bytes((0, 0, 0, 0, 220, 210, 200, 255)), alpha=True))
    (models / "player.img").write_bytes(b.img(files))
    return Path(root)


OUTFIT = (assemble.PartSpec("torso", "torso.dff", "player_torso.txd", "torso"),
          assemble.PartSpec("hands", "hands.dff", "player_torso.txd", "torso"))


class LoadModelTests(unittest.TestCase):
    def test_assembles_the_parts_on_the_shared_skeleton(self):
        with tempfile.TemporaryDirectory() as tmp:
            model = assemble.load_model(make_install(tmp), OUTFIT)
        self.assertEqual(model.skeleton.node_ids, tuple(SKELETON_IDS))
        self.assertEqual([p.name for p in model.parts], ["torso", "hands"])
        self.assertEqual(model.parts[0].joints[1][:2], (2, 1))  # part bones 1,2 are nodes 1,5 -> joints 2,1
        self.assertEqual(model.parts[0].triangles, [(0, 1, 2)])
        self.assertEqual(list(model.textures), ["player_torso:torso"])  # torso and hands share one texture
        self.assertEqual({p.texture for p in model.parts}, {"player_torso:torso"})

    def test_the_default_outfit_is_cjs_everyday_clothes(self):
        # the thin CJ of the start of the game: a white vest over his own skin, blue jeans and black chucks
        self.assertEqual([(s.name, s.dff, s.txd, s.texture, s.overlay) for s in assemble.OUTFIT], [
            ("torso", "torso.dff", "player_torso.txd", "torso", ("vest.txd", "vest")),
            ("head", "head.dff", "player_face.txd", "face", None),
            ("hands", "hands.dff", "player_torso.txd", "torso", ("vest.txd", "vest")),
            ("legs", "jeans.dff", "jeansdenim.txd", "legs", None),
            ("feet", "chucks.dff", "chucksblack.txd", "chucksblack", None),
        ])

    def test_the_body_shape_is_the_clump_asked_for_of_the_three_a_part_file_holds(self):
        # the body files hold three clumps, one per build (the parts of the thin CJ are the third)
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp, shapes=3)
            outfit = (assemble.PartSpec("torso", "torso.dff", "player_torso.txd", "torso"),)
            thin = assemble.load_model(install, outfit, body=2).parts[0].positions
            first = assemble.load_model(install, outfit, body=0).parts[0].positions
            self.assertEqual(thin[0][0] - first[0][0], 20.0)  # the third one is shifted 20 along x in the synthetic file
            self.assertEqual(assemble.load_model(install, outfit).parts[0].positions, thin)  # the default is the thin one
            # a file with fewer clumps than asked gives its last one
            self.assertEqual(assemble.load_model(install, outfit, body=9).parts[0].positions, thin)

    def test_the_stats_of_the_save_blend_the_three_bodies_like_the_game_does(self):
        # the clumps of the synthetic file are shifted 0 (ripped), 10 (fat) and 20 (normal) along x
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp, shapes=3)
            outfit = (assemble.PartSpec("torso", "torso.dff", "player_torso.txd", "torso"),)
            x = lambda stats: assemble.load_model(install, outfit, stats=stats).parts[0].positions[0][0]
            self.assertEqual(x((0, 0)), 20.0)      # the start of the game: the normal body, as with no stats
            self.assertEqual(x((1000, 0)), 0.0)    # all the muscle: ripped
            self.assertEqual(x((0, 1000)), 10.0)   # all the fat: fat
            self.assertAlmostEqual(x((500, 0)), 10.0)  # half ripped, half normal
            self.assertEqual(assemble.load_model(install, outfit).parts[0].positions[0][0], 20.0)  # no stats: as before

    def test_a_part_with_a_single_clump_is_read_whatever_the_body_asked(self):
        with tempfile.TemporaryDirectory() as tmp:
            model = assemble.load_model(make_install(tmp), OUTFIT, body=2)
        self.assertEqual(len(model.parts), 2)

    def test_an_overlay_is_composited_on_the_skin_and_shared_by_the_parts_that_name_it(self):
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp, overlay=True)
            outfit = (assemble.PartSpec("torso", "torso.dff", "player_torso.txd", "torso", ("vest.txd", "vest")),
                      assemble.PartSpec("hands", "hands.dff", "player_torso.txd", "torso", ("vest.txd", "vest")))
            model = assemble.load_model(install, outfit)
        self.assertEqual(list(model.textures), ["player_torso:torso+vest:vest"])
        self.assertEqual({p.texture for p in model.parts}, {"player_torso:torso+vest:vest"})
        tex = model.textures["player_torso:torso+vest:vest"]
        self.assertEqual((tex.width, tex.height), (2, 1))
        self.assertEqual(tex.rgba[0:4], bytes((10, 20, 30, 255)))   # the first pixel: the vest has nothing there, the skin shows
        self.assertEqual(tex.rgba[4:8], bytes((200, 210, 220, 255)))  # the second: the opaque vest covers it

    def test_the_overlay_goes_over_the_skin_by_its_alpha(self):
        base = txd.Texture("b", 2, 1, bytes((100, 100, 100, 255, 100, 100, 100, 255)))
        over = txd.Texture("o", 2, 1, bytes((200, 0, 0, 128, 0, 0, 0, 0)))
        out = assemble.composite(base, over)
        self.assertEqual(out.rgba[4:8], bytes((100, 100, 100, 255)))      # transparent: the skin
        r, g, b_, a = out.rgba[0:4]
        self.assertTrue(abs(r - 150) <= 1 and abs(g - 50) <= 1 and abs(b_ - 50) <= 1 and a == 255)  # half red over grey

    def test_an_overlay_of_another_size_is_refused(self):
        base = txd.Texture("b", 2, 1, bytes(8))
        over = txd.Texture("o", 4, 2, bytes(32))
        with self.assertRaises(assemble.ConversionError):
            assemble.composite(base, over)

    def test_a_missing_game_folder_says_where_to_look(self):
        # Review Focus #2
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(Path(tmp) / "nowhere", OUTFIT)
        self.assertIn("gta3.img", str(ctx.exception))

    def test_a_missing_part_names_the_file_and_the_archive(self):
        # Review Focus #2
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp)
            outfit = OUTFIT + (assemble.PartSpec("head", "head.dff", "player_torso.txd", "torso"),)
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(install, outfit)
        self.assertIn("head.dff", str(ctx.exception))
        self.assertIn("player.img", str(ctx.exception))

    def test_a_missing_texture_lists_what_the_dictionary_has(self):
        # Review Focus #2
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp)
            outfit = (assemble.PartSpec("torso", "torso.dff", "player_torso.txd", "nope"),)
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(install, outfit)
        self.assertIn("nope", str(ctx.exception))
        self.assertIn("torso_fat", str(ctx.exception))

    def test_a_broken_file_is_reported_as_a_conversion_error_naming_it(self):
        # Review Focus #1
        with tempfile.TemporaryDirectory() as tmp:
            install = make_install(tmp)
            (install / "models" / "player.img").write_bytes(b.img({"torso.dff": b"\0" * 64, "hands.dff": b"\0" * 64,
                                                                   "player_torso.txd": b.txd(b.texture_native("torso", 2, 1, BGRA))}))
            with self.assertRaises(assemble.ConversionError) as ctx:
                assemble.load_model(install, OUTFIT)
        self.assertIn("torso.dff", str(ctx.exception))


if __name__ == "__main__":
    unittest.main()
