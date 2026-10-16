import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from cjconvert import clothes  # noqa: E402

MODELS = list(range(100, 110))
TEXTURES = list(range(2000, 2018))


def dump(**changes):
    data = {"muscle": 1000.0, "fat": 250.5, "models": MODELS, "textures": TEXTURES}
    data.update(changes)
    return json.dumps(data)


class KeyTests(unittest.TestCase):
    def test_the_key_is_the_crc32_of_the_uppercase_name_without_the_final_inversion(self):
        # the standard check of CRC-32 ("123456789" gives 0xCBF43926) is the complement of what the game keeps
        self.assertEqual(clothes.uppercase_key("123456789"), 0xCBF43926 ^ 0xFFFFFFFF)

    def test_the_case_of_the_name_does_not_matter(self):
        self.assertEqual(clothes.uppercase_key("Vest"), clothes.uppercase_key("VEST"))
        self.assertEqual(clothes.uppercase_key("vest"), clothes.uppercase_key("vEsT"))
        self.assertNotEqual(clothes.uppercase_key("vest"), clothes.uppercase_key("jeans"))

    def test_the_empty_name_is_the_initial_remainder(self):
        self.assertEqual(clothes.uppercase_key(""), 0xFFFFFFFF)


class LoadTests(unittest.TestCase):
    def test_a_dump_gives_the_stats_and_the_keys(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "cj_outfit.json"
            path.write_text(dump())
            outfit = clothes.load_outfit(path)
        self.assertEqual((outfit.muscle, outfit.fat), (1000.0, 250.5))
        self.assertEqual(outfit.models, tuple(MODELS))
        self.assertEqual(outfit.textures, tuple(TEXTURES))

    def test_a_dump_that_is_not_the_shape_of_the_game_is_refused(self):
        for bad in (dump(models=MODELS[:9]), dump(textures=TEXTURES + [1]), dump(muscle="much"), dump(fat=-1), dump(muscle=2000), "not json", "[1]"):
            with tempfile.TemporaryDirectory() as d:
                path = Path(d) / "cj_outfit.json"
                path.write_text(bad)
                with self.assertRaises(ValueError, msg=bad):
                    clothes.load_outfit(path)

    def test_a_missing_dump_is_a_clear_error(self):
        with self.assertRaises(ValueError):
            clothes.load_outfit(Path("nowhere") / "cj_outfit.json")


class StatsTests(unittest.TestCase):
    def test_without_anything_asked_there_are_no_stats(self):
        self.assertIsNone(clothes.resolve_stats(None, None, None))

    def test_the_dump_gives_the_stats(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "cj_outfit.json"
            path.write_text(dump())
            self.assertEqual(clothes.resolve_stats(path, None, None), (1000.0, 250.5))

    def test_what_is_asked_by_hand_wins_over_the_dump_and_the_rest_is_zero(self):
        with tempfile.TemporaryDirectory() as d:
            path = Path(d) / "cj_outfit.json"
            path.write_text(dump())
            self.assertEqual(clothes.resolve_stats(path, 300.0, None), (300.0, 250.5))
            self.assertEqual(clothes.resolve_stats(path, None, 0.0), (1000.0, 0.0))
        self.assertEqual(clothes.resolve_stats(None, 500.0, None), (500.0, 0.0))
        self.assertEqual(clothes.resolve_stats(None, None, 900.0), (0.0, 900.0))

    def test_stats_out_of_range_by_hand_are_refused(self):
        for muscle, fat in ((1001, None), (-1, None), (None, 5000), (float("nan"), None)):
            with self.assertRaises(ValueError):
                clothes.resolve_stats(None, muscle, fat)


class TableTests(unittest.TestCase):
    NAMES = ["torso.dff", "jeans.dff", "capblkback.txd", "hoodyablack.txd", "readme.txt"]
    TEXTURES = {"capblkback.txd": ["capblk"], "hoodyablack.txd": ["hoodyablack"]}

    def test_the_models_are_keyed_by_the_name_of_their_dff(self):
        models, _ = clothes.key_tables(self.NAMES, lambda n: self.TEXTURES[n])
        self.assertEqual(models, {clothes.uppercase_key("torso"): "torso", clothes.uppercase_key("jeans"): "jeans"})

    def test_the_textures_are_keyed_by_the_name_of_their_txd_not_the_texture_inside(self):
        # the game keeps the key of capblkback.txd for a cap whose only texture is called capblk
        _, textures = clothes.key_tables(self.NAMES, lambda n: self.TEXTURES[n])
        self.assertEqual(textures[clothes.uppercase_key("capblkback")], "capblkback:capblk")
        self.assertEqual(textures[clothes.uppercase_key("hoodyablack")], "hoodyablack:hoodyablack")
        self.assertNotIn(clothes.uppercase_key("capblk"), textures)

    def test_a_txd_that_cannot_be_read_is_left_out(self):
        def textures(name):
            raise ValueError("broken")
        _, table = clothes.key_tables(self.NAMES, textures)
        self.assertEqual(table, {})


class NameTests(unittest.TestCase):
    def test_keys_become_names_and_the_unknown_ones_are_shown_as_such(self):
        names = {clothes.uppercase_key("torso"): "torso", clothes.uppercase_key("jeans"): "jeans"}
        keys = (clothes.uppercase_key("torso"), 0, clothes.uppercase_key("jeans"), 12345)
        self.assertEqual(clothes.name_keys(keys, names), ["torso", None, "jeans", "?12345"])  # 0 is an empty slot

    def test_the_slots_have_the_names_of_the_game(self):
        self.assertEqual(clothes.MODEL_SLOTS[:5], ("torso", "head", "hands", "legs", "shoes"))
        self.assertEqual(len(clothes.MODEL_SLOTS), 10)
        self.assertEqual(len(clothes.TEXTURE_SLOTS), 18)


if __name__ == "__main__":
    unittest.main()
