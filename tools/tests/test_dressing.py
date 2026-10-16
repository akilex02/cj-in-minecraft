import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
from cjconvert import assemble, clothes, dressing  # noqa: E402
from cjconvert.assemble import ConversionError  # noqa: E402
from cjconvert.txd import Texture  # noqa: E402

IDENT = (1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0)
SKELETON_IDS = [0, 5, 1]


def px(*pixels):
    """BGRA bytes of a 2 x 1 texture from two (r, g, b, a) pixels."""
    return b"".join(bytes((p[2], p[1], p[0], p[3])) for p in pixels)


def tex(name, a, b_):
    return Texture(name, 2, 1, bytes(a) + bytes(b_))


def key(name):
    return clothes.uppercase_key(name)


MODEL_FILES = ("torso", "head", "hands", "legs", "feet", "hoodya", "shoe", "glasses01", "capback")
TEXTURES = {
    "player_torso.txd": [("torso_ripped", px((200, 0, 0, 255), (200, 0, 0, 255))), ("torso_fat", px((0, 200, 0, 255), (0, 200, 0, 255))),
                         ("torso", px((0, 0, 200, 255), (0, 0, 200, 255)))],
    "player_face.txd": [("face", px((10, 10, 10, 255), (10, 10, 10, 255)))],
    "beard.txd": [("afrobeard", px((20, 20, 20, 255), (20, 20, 20, 255)))],
    "player_legs.txd": [("legs", px((30, 30, 30, 255), (30, 30, 30, 255)))],
    "player_feet.txd": [("feet", px((40, 40, 40, 255), (40, 40, 40, 255)))],
    "hoodyablack.txd": [("hoodyablack", px((1, 2, 3, 255), (1, 2, 3, 255)))],
    "vest.txd": [("vest", px((0, 0, 0, 0), (250, 250, 250, 255)))],           # half transparent: the left pixel is the skin's
    "tattoo.txd": [("tattoo", px((90, 90, 90, 255), (0, 0, 0, 0)))],
    "denimsred.txd": [("denimsred", px((50, 0, 0, 255), (50, 0, 0, 255)))],
    "shoedressblk.txd": [("shoedressblk", px((60, 60, 60, 255), (60, 60, 60, 255)))],
    "glasses01dark.txd": [("glasses01dark", px((0, 0, 0, 120), (0, 0, 0, 120)))],
    "capblkback.txd": [("capblk", px((70, 70, 70, 255), (70, 70, 70, 255)))],
    "wide.txd": [("wide", Texture("wide", 4, 1, bytes(16)).rgba)],
}


def make_install(root):
    models = Path(root) / "models"
    models.mkdir(parents=True)
    stub = b.geometry([(0, 0, 0)] * 3, [(0, 0, 1)] * 3, [(0, 0)] * 3, [(0, 1, 2)], skin=b.skin(3, [0], [(0, 0, 0, 0)] * 3, [(1, 0, 0, 0)] * 3, [IDENT] * 3))
    (models / "gta3.img").write_bytes(b.img({"player.dff": b.dff(SKELETON_IDS, stub)}))
    sk = b.skin(4, [0, 1], [(0, 0, 0, 0), (1, 2, 0, 0), (2, 0, 0, 0)], [(1.0, 0.0, 0.0, 0.0), (0.5, 0.5, 0.0, 0.0), (1.0, 0.0, 0.0, 0.0)], [IDENT] * 4)

    def clump(shift):
        return b.dff([0, 1, 5, 303], b.geometry([(shift, 0, 0), (shift + 1, 0, 0), (shift, 1, 0)], [(0, 0, 1)] * 3, [(0, 0), (1, 0), (0, 1)], [(0, 1, 2)], skin=sk))

    files = {name + ".dff": b"".join(clump(10.0 * k) for k in range(3)) for name in MODEL_FILES}  # ripped 0, fat 10, normal 20
    for name, textures in TEXTURES.items():
        natives = []
        for texture_name, data in textures:
            natives.append(b.texture_native(texture_name, 4 if name == "wide.txd" else 2, 1, data, alpha=True))
        files[name] = b.txd(*natives)
    (models / "player.img").write_bytes(b.img(files))
    return Path(root)


def outfit(models=None, textures=None, muscle=0.0, fat=0.0):
    m, t = [0] * 10, [0] * 18
    for slot, name in (models or {}).items():
        m[slot] = key(name)
    for slot, name in (textures or {}).items():
        t[slot] = key(name)
    return clothes.Outfit(muscle, fat, tuple(m), tuple(t))


def dressed(install, o):
    return assemble.load_model(install, dressing=o, stats=(o.muscle, o.fat))


def pixels(model, texture):
    return model.textures[texture].rgba


class PlaceTests(unittest.TestCase):
    def test_a_pixel_with_alpha_replaces_the_one_below_as_a_whole_and_the_transparent_ones_leave_it(self):
        base = tex("skin", (1, 1, 1, 255), (2, 2, 2, 255))
        over = tex("vest", (0, 0, 0, 0), (9, 8, 7, 120))  # half transparent: the game does not blend it, it copies it
        self.assertEqual(dressing.place(base, over).rgba, bytes((1, 1, 1, 255, 9, 8, 7, 120)))

    def test_textures_of_another_size_cannot_be_placed(self):
        with self.assertRaises(ConversionError):
            dressing.place(tex("a", (0, 0, 0, 255), (0, 0, 0, 255)), Texture("b", 4, 1, bytes(16)))


class SkinTests(unittest.TestCase):
    SKINS = (tex("ripped", (200, 0, 0, 255), (200, 0, 0, 255)), tex("fat", (0, 200, 0, 255), (0, 200, 0, 255)), tex("normal", (0, 0, 200, 255), (0, 0, 200, 255)))

    def test_the_skin_is_the_blend_of_the_three_by_the_ratios_and_keeps_its_alpha(self):
        self.assertEqual(dressing.blend_skin(self.SKINS, (0.5, 0.0, 0.5)).rgba[:4], bytes((100, 0, 100, 255)))
        self.assertEqual(dressing.blend_skin(self.SKINS, (1.0, 0.0, 0.0)).rgba[:4], bytes((200, 0, 0, 255)))
        self.assertEqual(dressing.blend_skin(self.SKINS, (0.0, 0.0, 1.0)).rgba[:4], bytes((0, 0, 200, 255)))


class DressedModelTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.install = make_install(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_an_empty_outfit_is_the_five_default_parts_of_the_game(self):
        model = dressed(self.install, outfit())
        self.assertEqual([p.name for p in model.parts], ["torso", "head", "hands", "legs", "feet"])

    def test_each_key_of_a_slot_gives_the_part_of_that_model_named_after_the_slot(self):
        model = dressed(self.install, outfit({0: "hoodya", 4: "shoe", 7: "glasses01", 8: "capback"}, {3: "shoedressblk", 15: "glasses01dark", 16: "capblkback"}))
        self.assertEqual([p.name for p in model.parts], ["torso", "head", "hands", "legs", "feet", "glasses", "hats"])
        self.assertEqual(model.parts[0].positions[0][0], 20.0)  # the hoodie's normal body (shifted 20 in the synthetic file)

    def test_the_bodies_of_every_part_are_blended_by_the_stats(self):
        model = dressed(self.install, outfit({0: "hoodya", 8: "capback"}, {16: "capblkback"}, muscle=500.0))
        self.assertAlmostEqual(model.parts[0].positions[0][0], 10.0)  # half ripped (0), half normal (20)
        self.assertAlmostEqual(model.parts[-1].positions[0][0], 10.0)

    def test_a_key_no_file_has_the_name_of_is_a_clear_error(self):
        o = clothes.Outfit(0.0, 0.0, (123456,) + (0,) * 9, (0,) * 18)
        with self.assertRaises(ConversionError) as caught:
            dressed(self.install, o)
        self.assertIn("torso", str(caught.exception))

    def test_the_torso_and_the_hands_share_one_texture_made_of_the_blended_skin_and_the_layers(self):
        model = dressed(self.install, outfit({0: "hoodya"}, {0: "hoodyablack"}, muscle=1000.0))
        torso, hands = model.parts[0], model.parts[2]
        self.assertEqual(torso.texture, hands.texture)
        self.assertEqual(pixels(model, torso.texture)[:4], bytes((1, 2, 3, 255)))  # the hoodie is opaque: it replaces the skin

    def test_the_layers_go_over_the_skin_in_the_order_of_the_slots_and_what_is_transparent_shows_the_skin(self):
        model = dressed(self.install, outfit({0: "torso"}, {0: "vest", 4: "tattoo"}, muscle=1000.0))
        rgba = pixels(model, model.parts[0].texture)
        # the left pixel: skin (ripped, red) with the vest transparent there, and the tattoo (slot 4) over it; the right one: the vest, no tattoo
        self.assertEqual(rgba[:4], bytes((90, 90, 90, 255)))
        self.assertEqual(rgba[4:8], bytes((250, 250, 250, 255)))

    def test_the_bare_skin_of_the_torso_is_the_blend_when_nothing_is_worn_on_it(self):
        model = dressed(self.install, outfit(muscle=1000.0))
        self.assertEqual(pixels(model, model.parts[0].texture)[:4], bytes((200, 0, 0, 255)))

    def test_the_head_is_the_face_unless_the_slot_names_another_texture(self):
        self.assertEqual(pixels(dressed(self.install, outfit()), dressed(self.install, outfit()).parts[1].texture)[:4], bytes((10, 10, 10, 255)))
        model = dressed(self.install, outfit(textures={1: "beard"}))
        self.assertEqual(pixels(model, model.parts[1].texture)[:4], bytes((20, 20, 20, 255)))

    def test_the_legs_the_shoes_and_the_accessories_take_the_texture_of_their_slot(self):
        model = dressed(self.install, outfit({4: "shoe", 7: "glasses01", 8: "capback"}, {2: "denimsred", 3: "shoedressblk", 15: "glasses01dark", 16: "capblkback"}))
        by_name = {p.name: p for p in model.parts}
        self.assertEqual(pixels(model, by_name["legs"].texture)[:4], bytes((50, 0, 0, 255)))
        self.assertEqual(pixels(model, by_name["feet"].texture)[:4], bytes((60, 60, 60, 255)))
        self.assertEqual(pixels(model, by_name["glasses"].texture)[:4], bytes((0, 0, 0, 120)))  # the lens keeps its translucency
        self.assertEqual(pixels(model, by_name["hats"].texture)[:4], bytes((70, 70, 70, 255)))

    def test_the_bare_legs_and_feet_are_the_skins_of_the_game_when_nothing_is_worn_on_them(self):
        model = dressed(self.install, outfit())
        by_name = {p.name: p for p in model.parts}
        self.assertEqual(pixels(model, by_name["legs"].texture)[:4], bytes((30, 30, 30, 255)))
        self.assertEqual(pixels(model, by_name["feet"].texture)[:4], bytes((40, 40, 40, 255)))

    def test_a_layer_of_another_size_than_the_torso_is_a_clear_error(self):
        with self.assertRaises(ConversionError):
            dressed(self.install, outfit(textures={4: "wide"}))

    def test_an_accessory_model_without_a_texture_is_a_clear_error(self):
        with self.assertRaises(ConversionError) as caught:
            dressed(self.install, outfit({7: "glasses01"}))
        self.assertIn("glasses", str(caught.exception))


if __name__ == "__main__":
    unittest.main()
