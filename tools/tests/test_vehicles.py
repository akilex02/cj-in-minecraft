import io
import struct
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent))

import rwbuild as b  # noqa: E402
import convert_assets  # noqa: E402
from cjconvert import assemble, dff, glb, vehicles  # noqa: E402
from cjconvert.rwchunk import FormatError  # noqa: E402

ID3 = b.IDENTITY3
TRI = [(0.0, 0.0, 0.0), (1.0, 0.0, 0.0), (0.0, 1.0, 0.0)]
UV3 = [(0.0, 0.0), (1.0, 0.0), (0.0, 1.0)]
UP3 = [(0.0, 0.0, 1.0)] * 3
PAINT1, PAINT2 = (60, 255, 0, 255), (255, 0, 175, 255)
CARCOLS = "col\n0,0,0 # 0 black\n10,20,30 # 1\n40,50,60 # 2\nend\ncar\ngreenwoo, 2,1, 0,0\nend\n"

# frame index -> what it is
FRAMES = [
    ("root", -1, ID3, (0.0, 0.0, 0.0)),                 # 0
    ("chassis_dummy", 0, ID3, (0.0, 0.0, 0.0)),         # 1
    ("chassis", 1, ID3, (0.0, 1.0, 0.0)),               # 2
    ("wheel_lf_dummy", 0, ID3, (-1.0, 1.5, -0.3)),      # 3
    ("wheel_rf_dummy", 0, ID3, (1.0, 1.5, -0.3)),       # 4
    ("wheel_lb_dummy", 0, ID3, (-1.0, -1.5, -0.3)),     # 5
    ("wheel_rb_dummy", 0, ID3, (1.0, -1.5, -0.3)),      # 6
    ("ped_frontseat", 0, ID3, (0.5, 0.1, -0.1)),        # 7
    ("door_lf_ok", 2, ID3, (-1.0, 0.5, 0.2)),           # 8: under the chassis, so it carries the chassis' offset too
    ("door_lf_dam", 2, ID3, (-1.0, 0.5, 0.2)),          # 9
    ("wheel", 4, ID3, (0.0, 0.0, 0.0)),                 # 10
    ("chassis_vlo", 1, ID3, (0.0, 0.0, 0.0)),           # 11
    ("ug_bonnet_ok", 1, ID3, (0.0, 0.0, 0.0)),          # 12: an underside piece, even named like an ok one
    ("wheel", 6, ID3, (0.0, 0.0, 0.0)),                 # 13: another frame called wheel (used only by the test of two wheels)
]


def square(shift=(0.0, 0.0, 0.0), count=3):
    return [(x + shift[0], y + shift[1], z + shift[2]) for x, y, z in TRI[:count]]


def vehicle_files(frames=FRAMES, chassis_materials=("body", None, None), extra_textures=(), wheel_texture="tyre", without_chassis=False, body_color=(255, 255, 255, 255),
                  without_wheel=False, two_wheels=False, hub=False, lamp_y=0.0):
    """A synthetic Greenwood: the chassis has a textured and two coloured triangles, a door in two states, a wheel, and models that must be left out."""
    first = [(x, y + lamp_y, z) for x, y, z in TRI]  # the first triangle, which a test can move to the front or the back of the car
    chassis = b.geometry(first + square((5, 0, 0)) + square((9, 0, 0)), UP3 * 3, UV3 * 3, [(0, 1, 2), (3, 4, 5), (6, 7, 8)], flags=0x37,
                         texture_names=list(chassis_materials), materials=[0, 1, 2],
                         colors=[body_color, PAINT1, PAINT2])
    door_ok = b.geometry(square(), UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=[None], colors=[(255, 175, 0, 255)])
    door_dam = b.geometry(square((0, 0, 50)), UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=[None], colors=[PAINT1])
    if hub:  # the tyre and a hub cap: two materials
        wheel = b.geometry([(-0.2, 0.0, 0.35), (0.2, 0.35, 0.0), (0.2, -0.35, 0.0), (0.0, 0.0, 0.1), (0.1, 0.0, 0.0), (0.0, 0.1, 0.0)], UP3 * 2, UV3 * 2, [(0, 1, 2), (3, 4, 5)],
                           flags=0x37, texture_names=[wheel_texture, "hubcap"], materials=[0, 1])
    else:
        wheel = b.geometry([(-0.2, 0.0, 0.35), (0.2, 0.35, 0.0), (0.2, -0.35, 0.0)], UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=[wheel_texture])
    other_wheel = b.geometry([(-9.0, 0.0, 9.0), (9.0, 9.0, 0.0), (9.0, -9.0, 0.0)], UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=[wheel_texture])
    lod = b.geometry(square((0, 0, 60)), UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=[None], colors=[PAINT1])
    ug = b.geometry(square((0, 0, 70)), UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=[None], colors=[PAINT1])
    geometries = [chassis, door_ok, door_dam, wheel, lod, ug, other_wheel]
    atomics = [(10, 3), (11, 4), (2, 0), (8, 1), (9, 2), (12, 5)]
    if without_chassis:
        atomics = [a for a in atomics if a[0] != 2]
    if without_wheel:
        atomics = [a for a in atomics if a[0] != 10]
    if two_wheels:
        atomics.append((13, 6))  # a second atomic on a frame also called "wheel"
    return b.vehicle_dff(frames, geometries, atomics)


def make_install(root, with_shared=True, own_textures=("body",), carcols=CARCOLS, shared_names=("tyre",), shared_extra=(), with_img=True, **kwargs):
    models = Path(root) / "models"
    (models / "generic").mkdir(parents=True)
    (Path(root) / "data").mkdir()
    dxt1 = struct.pack("<HHI", 0xF800, 0x001F, 0xE4)
    own = [b.texture_native(name, 4, 4, dxt1, flags=0x08, d3d=0x31545844, depth=16, raster=0x200) for name in own_textures]
    if with_img:
        (models / "gta3.img").write_bytes(b.img({"greenwoo.dff": vehicle_files(**kwargs), "greenwoo.txd": b.txd(*own)}))
    if with_shared:
        other = struct.pack("<HHI", 0x07E0, 0x07E0, 0)  # another colour, to tell which of two same-named textures was used
        shared = [b.texture_native(name, 4, 4, dxt1, flags=0x08, d3d=0x31545844, depth=16, raster=0x200) for name in shared_names]
        shared += [b.texture_native(name, 4, 4, other, flags=0x08, d3d=0x31545844, depth=16, raster=0x200) for name in shared_extra]
        (models / "generic" / "vehicle.txd").write_bytes(b.txd(*shared))
    if carcols is not None:
        (Path(root) / "data" / "carcols.dat").write_text(carcols)
    return Path(root)


def texel(model, key):
    return tuple(model.textures[key].rgba[:4])


class FrameTests(unittest.TestCase):
    def assertVec(self, got, expected):
        for g, e in zip(got, expected):
            self.assertAlmostEqual(g, e, places=5)
        self.assertEqual(len(got), len(expected))

    def parse(self, frames=FRAMES):
        return dff.parse_frames(vehicle_files(frames))

    def test_frames_have_their_names_parents_and_world_places(self):
        frames = self.parse()
        self.assertEqual([f.name for f in frames][:4], ["root", "chassis_dummy", "chassis", "wheel_lf_dummy"])
        self.assertEqual(frames[2].parent, 1)
        self.assertEqual(frames[0].parent, -1)
        self.assertVec(frames[3].position, (-1.0, 1.5, -0.3))
        # the door hangs from the chassis, which is one metre forward: the world place is the sum
        self.assertVec(frames[8].position, (-1.0, 1.5, 0.2))

    def test_a_rotated_parent_turns_the_places_of_its_children(self):
        quarter = (0, 1, 0, -1, 0, 0, 0, 0, 1)  # right = +y, up = -x: a quarter turn counter-clockwise
        frames = list(FRAMES)
        frames[2] = ("chassis", 1, quarter, (0.0, 1.0, 0.0))
        parsed = self.parse(frames)
        # the door's local (-1, 0.5, 0.2): -1 * right + 0.5 * up + 0.2 * at = (0, -1, 0) + (-0.5, 0, 0) + (0, 0, 0.2), then the chassis' own place
        self.assertAlmostEqual(parsed[8].position[0], -0.5)
        self.assertAlmostEqual(parsed[8].position[1], 0.0)
        self.assertAlmostEqual(parsed[8].position[2], 0.2)

    def test_a_frame_that_is_its_own_parent_is_rejected(self):
        frames = list(FRAMES)
        frames[2] = ("chassis", 2, ID3, (0.0, 1.0, 0.0))
        with self.assertRaises(FormatError):
            self.parse(frames)

    def test_frames_without_a_name_chunk_are_padded_with_empty_names(self):
        data = b.vehicle_dff(FRAMES, [b.geometry(TRI, UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=["x"])], [(2, 0)], named=2)
        names = [f.name for f in dff.parse_frames(data)]
        self.assertEqual(names[:3], ["root", "chassis_dummy", ""])
        self.assertEqual(len(names), len(FRAMES))

    def test_an_atomic_on_a_missing_geometry_or_a_triangle_on_a_missing_material_is_rejected(self):
        geometry = b.geometry(TRI, UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=["x"])
        with self.assertRaises(FormatError):
            dff.parse_vehicle_geometry(b.vehicle_dff(FRAMES, [geometry], [(2, 5)]))
        bad_material = b.geometry(TRI, UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=["x"], materials=[3])
        with self.assertRaises(FormatError):
            dff.parse_vehicle_geometry(b.vehicle_dff(FRAMES, [bad_material], [(2, 0)]))

    def test_a_model_with_a_broken_parent_is_rejected(self):
        frames = list(FRAMES)
        frames[2] = ("chassis", 99, ID3, (0.0, 1.0, 0.0))
        with self.assertRaises(FormatError):
            self.parse(frames)

    def test_a_frame_without_a_name_has_an_empty_one(self):
        # dummy files (the weapons) have no names at all
        data = b.weapon_dff(b.geometry(TRI, UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=["x"]), b.geometry(TRI, UP3, UV3, [(0, 1, 2)], flags=0x37, texture_names=["y"]))
        self.assertEqual([f.name for f in dff.parse_frames(data)], ["", ""])


class VehicleModelTests(unittest.TestCase):
    def load(self, **kwargs):
        with tempfile.TemporaryDirectory() as root:
            return vehicles.load_vehicle(make_install(root, **kwargs), "greenwoo")

    def test_the_body_is_the_chassis_and_the_ok_pieces_and_nothing_else(self):
        model = self.load()
        body = [p for p in model.parts if p.name != "wheel"]
        xs = sorted(round(v[2], 3) for p in body for v in p.positions)
        # chassis (9 vertices at z 0), the ok door (3 vertices at z 0.2): not the damaged door (z 50), the low-resolution chassis (60) or the underside (70)
        self.assertEqual(len(xs), 12)
        self.assertEqual(max(xs), 0.2)
        self.assertEqual(sum(len(p.triangles) for p in body), 4)

    def test_every_piece_is_moved_by_its_frames(self):
        model = self.load()
        body = [v for p in model.parts if p.name != "wheel" for v in p.positions]
        self.assertIn((0.0, 1.0, 0.0), [tuple(round(c, 5) for c in v) for v in body])    # the chassis frame is one metre forward
        self.assertIn((-1.0, 1.5, 0.2), [tuple(round(c, 5) for c in v) for v in body])   # the door: its own place plus the chassis'

    def test_the_wheel_is_one_mesh_centred_on_its_own_frame(self):
        model = self.load()
        wheels = [p for p in model.parts if p.name == "wheel"]
        self.assertEqual(len(wheels), 1)
        self.assertEqual(len(wheels[0].positions), 3)
        first = sorted(wheels[0].positions)[0]
        for got, expected in zip(first, (-0.2, 0.0, 0.35)):  # as the file has it: not moved to a corner of the car
            self.assertAlmostEqual(got, expected, places=5)

    def test_a_wheel_with_two_materials_is_two_meshes_and_the_first_wheel_atomic_is_the_one_used(self):
        with tempfile.TemporaryDirectory() as root:
            model = vehicles.load_vehicle(make_install(root, hub=True, two_wheels=True, shared_extra=("hubcap",)), "greenwoo")
        names = [p.name for p in model.parts if p.name.startswith("wheel")]
        self.assertEqual(names, ["wheel_0", "wheel_1"])
        self.assertTrue(all(abs(v[0]) < 1 for p in model.parts if p.name.startswith("wheel") for v in p.positions))  # not the second wheel at 9

    def test_the_model_is_on_one_joint_with_whole_weights_and_its_triangles_index_its_own_vertices(self):
        with tempfile.TemporaryDirectory() as root:
            model = vehicles.load_vehicle(make_install(root), "greenwoo")
        self.assertEqual(model.skeleton.node_ids, (0,))
        for part in model.parts:
            self.assertTrue(all(w == (1.0, 0.0, 0.0, 0.0) for w in part.weights))
            self.assertTrue(all(j == (0, 0, 0, 0) for j in part.joints))
            self.assertTrue(all(0 <= i < len(part.positions) for tri in part.triangles for i in tri))
            self.assertEqual(len(part.positions), len(part.normals))
            self.assertEqual(len(part.positions), len(part.uvs))
        chassis_parts = [p for p in model.parts if p.name.startswith("body")]
        self.assertTrue(any(p.triangles == [(0, 1, 2)] for p in chassis_parts))

    def test_the_four_wheel_places_the_seat_and_the_radius_travel_in_the_file(self):
        with tempfile.TemporaryDirectory() as root:
            model = vehicles.load_vehicle(make_install(root), "greenwoo")
        gltf, _binary = glb.read_glb(glb.to_glb(model))
        extras = gltf["asset"]["extras"]
        self.assertEqual(extras["wheels"], {"lf": [-1.0, 1.5, -0.3], "rf": [1.0, 1.5, -0.3], "lb": [-1.0, -1.5, -0.3], "rb": [1.0, -1.5, -0.3]})
        self.assertEqual(extras["driverSeat"], [-0.5, 0.1, -0.1])  # the front seat of the file, mirrored to the left: GTA seats the driver there
        self.assertAlmostEqual(extras["wheelRadius"], 0.35)
        self.assertIn("X right", extras["space"])
        self.assertEqual(extras["parts"][-1], "wheel")
        self.assertEqual(extras["paint"], [[40, 50, 60], [10, 20, 30]])

    def lamps(self, lamp_y, body_color=(255, 255, 255, 255)):
        with tempfile.TemporaryDirectory() as root:
            model = vehicles.load_vehicle(make_install(root, chassis_materials=("vehiclelights128", None, None), shared_names=("tyre", "vehiclelights128"),
                                                       lamp_y=lamp_y, body_color=body_color), "greenwoo")
        keys = list(model.textures)
        gltf, _binary = glb.read_glb(glb.to_glb(model))
        return model, keys, gltf["asset"]["extras"]["lightSwaps"]

    def test_the_lens_at_the_front_gets_a_lit_twin_that_is_a_bright_warm_white(self):
        model, keys, swaps = self.lamps(lamp_y=3.0)  # the world y of the triangle is 4: a front lamp
        self.assertEqual(len(swaps), 1)
        dim, lit = swaps[0]
        self.assertTrue(keys[dim].endswith("@front"), keys[dim])
        self.assertTrue(keys[lit].endswith("@front@lit"), keys[lit])
        l = model.textures[keys[lit]].rgba
        d = model.textures[keys[dim]].rgba
        self.assertTrue(l[0] > 240 and l[1] > 180 and l[2] > 150, tuple(l[:4]))  # bright and warm whatever the texture under it
        self.assertTrue(sum(l[0:3]) > sum(d[0:3]) + 100, "much brighter than when it is off")
        self.assertTrue(any(p.texture == keys[dim] for p in model.parts), "the lamp triangles are a mesh of their own")

    def test_the_lens_at_the_back_gets_a_lit_twin_that_is_a_bright_red(self):
        model, keys, swaps = self.lamps(lamp_y=-5.0)  # world y -4: a tail lamp
        self.assertEqual(len(swaps), 1)
        dim, lit = swaps[0]
        self.assertTrue(keys[dim].endswith("@rear"), keys[dim])
        l = model.textures[keys[lit]].rgba
        d = model.textures[keys[dim]].rgba
        self.assertTrue(l[0] > 200 and l[1] < 120 and l[2] < 120, tuple(l[:4]))
        self.assertTrue(d[0] > d[1] and d[0] > d[2] and d[0] < l[0], "dark red when it is off")

    def test_the_lamps_that_are_neither_at_the_front_nor_at_the_back_are_left_alone(self):
        _model, keys, swaps = self.lamps(lamp_y=0.0)  # the world y is 1: a mirror
        self.assertEqual(swaps, [])
        self.assertFalse(any(k.endswith("@front") or k.endswith("@rear") for k in keys))

    def test_a_dim_light_colour_of_the_file_that_is_at_the_back_is_a_tail_lamp_and_an_indicator_is_not(self):
        _model, keys, swaps = self.lamps(lamp_y=-5.0, body_color=(185, 255, 0, 255))  # (185, 255, 0) is dimmed grey: a lamp of the file
        self.assertEqual(len(swaps), 1)
        self.assertTrue(keys[swaps[0][0]].endswith("@rear"))
        _model, keys, swaps = self.lamps(lamp_y=3.0, body_color=(255, 175, 0, 255))  # an amber indicator at the front stays as it is
        self.assertEqual(swaps, [])

    def test_a_car_without_those_lights_has_no_swaps(self):
        with tempfile.TemporaryDirectory() as root:
            model = vehicles.load_vehicle(make_install(root), "greenwoo")
        gltf, _binary = glb.read_glb(glb.to_glb(model))
        self.assertEqual(gltf["asset"]["extras"]["lightSwaps"], [])

    def test_the_magic_paint_colours_become_the_cars_own_and_the_lights_are_dimmed(self):
        model = self.load()
        colours = {tuple(t.rgba[:4]) for t in model.textures.values() if t.width == 1}
        self.assertIn((40, 50, 60, 255), colours)   # the primary of the file (60, 255, 0)
        self.assertIn((10, 20, 30, 255), colours)   # the secondary (255, 0, 175)
        self.assertIn((150, 100, 0, 255), colours)  # the indicator (255, 175, 0): not a neon orange
        self.assertNotIn((60, 255, 0, 255), colours)
        self.assertNotIn((255, 175, 0, 255), colours)

    def test_other_colours_stay_and_the_alpha_is_kept(self):
        self.assertEqual(vehicles.paint_colour((12, 34, 56, 78), (1, 2, 3), (4, 5, 6)), (12, 34, 56, 78))
        self.assertEqual(vehicles.paint_colour((0, 255, 255, 200), (1, 2, 3), (4, 5, 6)), (1, 2, 3, 200))     # the third paint is the primary
        self.assertEqual(vehicles.paint_colour((255, 255, 0, 255), (1, 2, 3), (4, 5, 6)), (4, 5, 6, 255))     # the fourth is the secondary
        self.assertEqual(vehicles.paint_colour((185, 255, 0, 255), (1, 2, 3), (4, 5, 6)), (150, 150, 140, 255))
        self.assertEqual(vehicles.paint_colour((255, 60, 0, 255), (1, 2, 3), (4, 5, 6)), (100, 10, 10, 255))
        self.assertEqual(vehicles.paint_colour((0, 255, 200, 255), (1, 2, 3), (4, 5, 6)), (150, 175, 170, 255))
        self.assertEqual(vehicles.paint_colour((60, 255, 0, 99), (1, 2, 3), (4, 5, 6)), (1, 2, 3, 99))
        self.assertEqual(vehicles.paint_colour((255, 175, 0, 77), (1, 2, 3), (4, 5, 6)), (150, 100, 0, 77))  # a light keeps its alpha

    def test_a_translucent_colour_tints_the_alpha_too(self):
        with tempfile.TemporaryDirectory() as root:
            painted = vehicles.load_vehicle(make_install(root, body_color=(60, 255, 0, 128)), "greenwoo")
        tinted = painted.textures["greenwoo:body@40,50,60,128"]
        self.assertTrue(all(a in (0, 128) for a in tinted.rgba[3::4]))  # the texture is opaque: alpha 255 * 128 / 255
        self.assertIn(128, tinted.rgba[3::4])

    def test_the_cars_own_texture_wins_over_a_shared_one_of_the_same_name(self):
        with tempfile.TemporaryDirectory() as root:
            model = vehicles.load_vehicle(make_install(root, shared_extra=("body",)), "greenwoo")
        self.assertIn("greenwoo:body", model.textures)
        self.assertNotIn("vehicle:body", model.textures)

    def test_the_colour_of_a_textured_material_tints_its_texture(self):
        with tempfile.TemporaryDirectory() as root:
            plain = vehicles.load_vehicle(make_install(Path(root) / "a"), "greenwoo")
            painted = vehicles.load_vehicle(make_install(Path(root) / "b", body_color=PAINT1), "greenwoo")
        self.assertIn("greenwoo:body", plain.textures)
        self.assertNotIn("greenwoo:body", painted.textures)  # it is only in its tinted form
        tinted = painted.textures["greenwoo:body@40,50,60,255"]
        base = plain.textures["greenwoo:body"]
        self.assertEqual((tinted.width, tinted.height), (base.width, base.height))
        for i in range(0, len(base.rgba), 4):
            self.assertEqual(tuple(tinted.rgba[i:i + 4]), tuple((v * c + 127) // 255 for v, c in zip(base.rgba[i:i + 4], (40, 50, 60, 255))))

    def test_textures_come_from_the_cars_own_txd_and_then_from_the_shared_one(self):
        model = self.load()
        self.assertIn("greenwoo:body", model.textures)   # the chassis' own texture
        self.assertIn("vehicle:tyre", model.textures)    # the wheel's, shared by every car
        self.assertEqual(len(model.textures["vehicle:tyre"].rgba), 4 * 4 * 4)

    def test_a_texture_in_neither_txd_is_named_in_the_error(self):
        with tempfile.TemporaryDirectory() as root:
            install = make_install(root, own_textures=())
            with self.assertRaises(assemble.ConversionError) as caught:
                vehicles.load_vehicle(install, "greenwoo")
        self.assertIn("body", str(caught.exception))
        self.assertIn("greenwoo", str(caught.exception))

    def test_without_the_shared_txd_the_error_says_which_file(self):
        with tempfile.TemporaryDirectory() as root:
            install = make_install(root, with_shared=False)
            with self.assertRaises(assemble.ConversionError) as caught:
                vehicles.load_vehicle(install, "greenwoo")
        self.assertIn("vehicle.txd", str(caught.exception))

    def test_a_model_without_a_chassis_is_an_error_that_names_the_file(self):
        with tempfile.TemporaryDirectory() as root:
            install = make_install(root, without_chassis=True)
            with self.assertRaises(assemble.ConversionError) as caught:
                vehicles.load_vehicle(install, "greenwoo")
        self.assertIn("greenwoo.dff", str(caught.exception))
        self.assertIn("chassis", str(caught.exception))

    def test_a_missing_model_or_install_is_an_error(self):
        with tempfile.TemporaryDirectory() as root:
            with self.assertRaises(assemble.ConversionError):
                vehicles.load_vehicle(root, "greenwoo")  # nothing at all
            with self.assertRaises(assemble.ConversionError) as caught:
                vehicles.load_vehicle(make_install(Path(root) / "noimg", with_img=False), "greenwoo")  # the shared txd but no gta3.img
            self.assertIn("gta3.img", str(caught.exception))
            install = make_install(Path(root) / "ok")
            with self.assertRaises(assemble.ConversionError) as caught:
                vehicles.load_vehicle(install, "nosuchcar")
            self.assertIn("nosuchcar", str(caught.exception))

    def test_a_car_with_the_wheel_frame_but_no_wheel_geometry_is_an_error(self):
        with tempfile.TemporaryDirectory() as root:
            install = make_install(root, without_wheel=True)
            with self.assertRaises(assemble.ConversionError) as caught:
                vehicles.load_vehicle(install, "greenwoo")
        self.assertIn("wheel", str(caught.exception))
        self.assertIn("greenwoo.dff", str(caught.exception))

    def test_a_car_missing_a_wheel_or_a_seat_is_an_error(self):
        for missing in ("wheel_rb_dummy", "ped_frontseat", "wheel"):
            with self.subTest(missing=missing), tempfile.TemporaryDirectory() as root:
                frames = [(("gone" if name == missing else name), parent, rot, pos) for name, parent, rot, pos in FRAMES]
                install = make_install(root, frames=frames)
                with self.assertRaises(assemble.ConversionError) as caught:
                    vehicles.load_vehicle(install, "greenwoo")
                self.assertIn(missing, str(caught.exception))

    def test_the_file_is_a_valid_glb_with_every_mesh_and_texture(self):
        model = self.load()
        data = glb.to_glb(model)
        gltf, binary = glb.read_glb(data)
        self.assertEqual(len(gltf["meshes"][0]["primitives"]), len(model.parts))
        self.assertEqual(len(gltf["images"]), len(model.textures))
        self.assertEqual(len(gltf["skins"][0]["joints"]), 1)
        for primitive in gltf["meshes"][0]["primitives"]:
            self.assertGreater(len(glb.read_accessor(gltf, binary, primitive["attributes"]["POSITION"])), 0)

    def test_the_colour_of_a_car_comes_from_carcols_and_is_asked_for_by_name(self):
        with tempfile.TemporaryDirectory() as root:
            install = make_install(root)
            self.assertEqual(vehicles.default_paint(install, "greenwoo"), ((40, 50, 60), (10, 20, 30)))
            with self.assertRaises(assemble.ConversionError):
                vehicles.default_paint(install, "nosuchcar")
        with tempfile.TemporaryDirectory() as root:
            install = make_install(root, carcols=None)
            with self.assertRaises(assemble.ConversionError):
                vehicles.default_paint(install, "greenwoo")
        with tempfile.TemporaryDirectory() as root:
            install = make_install(root, carcols="col\n1,2,3\nend\ncar\ngreenwoo, 5,0\nend\n")
            with self.assertRaises(assemble.ConversionError) as caught:
                vehicles.default_paint(install, "greenwoo")
            self.assertIn("5", str(caught.exception))
        with tempfile.TemporaryDirectory() as root:
            for carcols, fragment in (("col\n1,2,3\nend\ncar\ngreenwoo, 1\nend\n", "fewer than two"), ("col\n1,2,3\n4.5,6\nend\ncar\ngreenwoo, 0,1\nend\n", "1"),
                                      ("col\n1,2,3\n4,5,6\nend\ncar\ngreenwoo, 0,1\nother, 1 2\nend\n", None),
                                      ("col\n1,2,3\n4,5,6\nend\ncar\ngreenwoo, 0,x\nend\n", "not understood")):
                install = make_install(Path(root) / f"c{abs(hash(carcols))}", carcols=carcols)
                if fragment is None:
                    self.assertEqual(vehicles.default_paint(install, "greenwoo"), ((1, 2, 3), (4, 5, 6)))  # a mistyped line of another car does not matter
                else:
                    with self.assertRaises(assemble.ConversionError) as caught:
                        vehicles.default_paint(install, "greenwoo")
                    self.assertIn(fragment, str(caught.exception))
        with tempfile.TemporaryDirectory() as root:
            four = "col\n1,2,3\n4,5,6\n7,8,9\nend\ncar4\ngreenwoo, 0,1, 2,1\nend\ncar\ngreenwoo, 2,2\nend\n"
            install = make_install(root, carcols=four)
            self.assertEqual(vehicles.default_paint(install, "greenwoo"), ((1, 2, 3), (4, 5, 6)))  # the table of four colours is the one asked first


class CommandLineTests(unittest.TestCase):
    def test_vehicles_only_writes_the_vehicle_file_and_nothing_else(self):
        with tempfile.TemporaryDirectory() as root, tempfile.TemporaryDirectory() as out:
            install = make_install(Path(root) / "gta")
            buffer = io.StringIO()
            with redirect_stdout(buffer):
                code = convert_assets.main(["--gta", str(install), "--out", out, "--vehicles-only"])
            self.assertEqual(code, 0, buffer.getvalue())
            self.assertTrue((Path(out) / "vehicles" / "greenwood.glb").is_file())
            self.assertFalse((Path(out) / "cj.glb").exists())
            self.assertFalse((Path(out) / "weapons").exists())
            glb.read_glb((Path(out) / "vehicles" / "greenwood.glb").read_bytes())

    def test_a_broken_car_makes_the_command_fail_with_a_message(self):
        with tempfile.TemporaryDirectory() as root, tempfile.TemporaryDirectory() as out:
            install = make_install(Path(root) / "gta", without_chassis=True)
            buffer = io.StringIO()
            with redirect_stdout(buffer):
                code = convert_assets.main(["--gta", str(install), "--out", out, "--vehicles-only"])
            self.assertEqual(code, 1)
            self.assertIn("chassis", buffer.getvalue())
            self.assertFalse((Path(out) / "vehicles").exists())


GTA = Path(__file__).resolve().parent.parent.parent / "GTA San Andreas"


@unittest.skipUnless((GTA / "models" / "gta3.img").is_file() and (GTA / "models" / "generic" / "vehicle.txd").is_file(), "no GTA San Andreas install next to the project")
class RealGreenwoodTests(unittest.TestCase):
    def test_the_greenwood_converts_with_a_body_a_wheel_and_the_measured_numbers(self):
        model = vehicles.load_vehicle(GTA, "greenwoo")
        body = [p for p in model.parts if not p.name.startswith("wheel")]
        self.assertGreater(sum(len(p.positions) for p in body), 2500)
        wheels = [p for p in model.parts if p.name.startswith("wheel")]
        self.assertGreaterEqual(len(wheels), 1)
        gltf, _binary = glb.read_glb(glb.to_glb(model))
        extras = gltf["asset"]["extras"]
        self.assertAlmostEqual(extras["wheelRadius"], 0.35, delta=0.01)
        for corner, (x, y) in {"lf": (-0.925, 1.71), "rf": (0.925, 1.71), "lb": (-0.925, -1.7), "rb": (0.925, -1.7)}.items():
            self.assertAlmostEqual(extras["wheels"][corner][0], x, delta=0.01)
            self.assertAlmostEqual(extras["wheels"][corner][1], y, delta=0.01)
            self.assertAlmostEqual(extras["wheels"][corner][2], -0.35, delta=0.01)
        self.assertAlmostEqual(extras["driverSeat"][0], -0.463, delta=0.01)  # measured in the game with CJ at the wheel
        self.assertAlmostEqual(extras["driverSeat"][1], 0.14, delta=0.01)
        self.assertAlmostEqual(extras["driverSeat"][2], -0.095, delta=0.01)
        self.assertEqual(extras["paint"], [[66, 31, 33], [165, 169, 167]])
        self.assertGreaterEqual(len(extras["lightSwaps"]), 2)  # the headlights and the tail lights
        zs = [v[2] for p in body for v in p.positions]
        ys = [v[1] for p in body for v in p.positions]
        xs = [v[0] for p in body for v in p.positions]
        self.assertTrue(4.5 < max(ys) - min(ys) < 6.0 and 1.6 < max(xs) - min(xs) < 2.4 and 1.0 < max(zs) - min(zs) < 2.0, (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)))
        glb.read_glb(glb.to_glb(model))


if __name__ == "__main__":
    unittest.main()
