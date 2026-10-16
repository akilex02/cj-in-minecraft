import math
import unittest
from pathlib import Path

import cjproto as p

GOLDEN = Path(__file__).parent / "golden"


def fixed_state():
    return p.State(seq=7, ts_ms=1234, pos=(10.5, 64.0, -3.25), vel=(0.1, 0.0, -0.2),
                   yaw=90.0, pitch=-15.0, flags=p.FLAG_ON_GROUND | p.FLAG_SPRINT,
                   intents=p.INTENT_AIM | p.INTENT_FIRE, weapon=22, aim=(1.5, 1.25, -20.0))


def fixed_echo():
    return p.Echo(ack_seq=7, gta_time_ms=4321, pos=(2488.5, -1666.25, 13.5), weapon_held=22)


def fixed_shot():
    return p.Shot(ack_seq=7, gta_time_ms=4321, shot_seq=12, weapon=22, rounds=3)


def fixed_pose():
    return p.Pose(ack_seq=7, gta_time_ms=4321, flags=0, heading=1.5, bones=(
        p.Bone(1, (0.0, 0.0, 0.1), (0.0, 0.0, 0.0, 1.0)),
        p.Bone(5, (0.0, 0.0, 0.7), (0.0, 0.0, 0.0, 1.0)),
        p.Bone(53, (0.1, 0.2, -0.9), (0.5, 0.5, 0.5, 0.5)),
    ))


def fixed_vehicle_command():
    return p.VehicleCommand(seq=7, ts_ms=1234, command=p.VEHICLE_CMD_ENTER, model=492, yaw=90.0)


def fixed_vehicle_input():
    return p.VehicleInput(seq=8, ts_ms=1300, throttle=200, brake=30, steer=-64, handbrake=255,
                          corr_flags=p.VEHICLE_CORR_PRESENT, corr_x=0.5, corr_y=-0.25)


def fixed_vehicle_state():
    return p.VehicleState(ack_seq=8, gta_time_ms=4321, flags=p.VEHICLE_FLAG_DRIVER_IN, model=492,
                          pos=(1514.5, -2585.5, 13.25), heading=1.5, pitch=0.0625, roll=-0.03125, speed=12.5, steer=-0.25,
                          seat=(-0.5, 0.125, -0.0625), seat_heading=0.5)


class EatingTests(unittest.TestCase):
    def test_eating_is_a_flag_with_the_kind_of_food_next_to_it(self):
        self.assertEqual(p.FLAG_EATING, 1 << 19)
        self.assertEqual((p.EAT_BURGER, p.EAT_CHICKEN, p.EAT_PIZZA), (0, 1, 2))
        for kind in (p.EAT_BURGER, p.EAT_CHICKEN, p.EAT_PIZZA):
            flags = p.eating_flags(kind)
            self.assertTrue(flags & p.FLAG_EATING)
            self.assertEqual(p.eat_kind(flags), kind)
        self.assertEqual(p.eating_flags(p.EAT_PIZZA), (1 << 19) | (2 << 20))

    def test_not_eating_says_nothing_and_the_other_flags_are_untouched(self):
        self.assertIsNone(p.eat_kind(p.FLAG_ON_GROUND | p.FLAG_SPRINT))
        self.assertIsNone(p.eat_kind(2 << 20))  # a kind without the flag
        for used in (p.FLAG_SWIM, p.FLAG_WATER_MOVING, p.HURT_MASK, p.FLAG_JETPACK_THRUST):
            self.assertEqual(used & p.eating_flags(p.EAT_PIZZA), 0, "the bits do not overlap")

    def test_a_broken_kind_is_refused(self):
        for bad in (-1, 3, 99):
            with self.assertRaises(ValueError):
                p.eating_flags(bad)


class RadioTests(unittest.TestCase):
    def test_commands_are_after_enter_and_exit(self):
        self.assertEqual((p.VEHICLE_CMD_RADIO_NEXT, p.VEHICLE_CMD_RADIO_PREVIOUS), (3, 4))

    def test_the_station_lives_in_the_upper_byte_of_the_flags_as_id_plus_one(self):
        self.assertIsNone(p.vehicle_station(p.VEHICLE_FLAG_DRIVER_IN))  # nothing there: unknown
        for station in (0, 1, 11, 13):
            flags = p.with_vehicle_station(p.VEHICLE_FLAG_DRIVER_IN, station)
            self.assertEqual(p.vehicle_station(flags), station)
            self.assertTrue(flags & p.VEHICLE_FLAG_DRIVER_IN)  # the low bits are kept
        self.assertEqual(p.with_vehicle_station(1, 3), 1 | (4 << 8))

    def test_an_unknown_station_clears_it_and_a_broken_one_is_refused(self):
        flags = p.with_vehicle_station(1, 5)
        self.assertIsNone(p.vehicle_station(p.with_vehicle_station(flags, None)))
        self.assertEqual(p.with_vehicle_station(flags, None) & 0xFF, 1)
        for bad in (-1, 255, 300):
            with self.assertRaises(ValueError):
                p.with_vehicle_station(1, bad)


class CodecTests(unittest.TestCase):
    def test_sizes(self):
        self.assertEqual(len(p.pack_state(fixed_state())), 72)
        self.assertEqual(len(p.pack_echo(fixed_echo())), 32)
        self.assertEqual(len(p.pack_shot(fixed_shot())), 24)

    def test_state_roundtrip(self):
        s = p.unpack_state(p.pack_state(fixed_state()))
        self.assertEqual((s.seq, s.ts_ms, s.flags, s.intents, s.weapon), (7, 1234, 3, 3, 22))
        self.assertEqual(s.pos, (10.5, 64.0, -3.25))
        self.assertAlmostEqual(s.vel[0], 0.1, places=6)
        self.assertEqual((s.yaw, s.pitch), (90.0, -15.0))
        self.assertEqual(s.aim, (1.5, 1.25, -20.0))

    def test_shot_roundtrip(self):
        self.assertEqual(p.unpack_shot(p.pack_shot(fixed_shot())), fixed_shot())

    def test_shot_rejects_malformed(self):
        data = p.pack_shot(fixed_shot())
        for bad in (data[:-1], data + b"\0", p.pack_state(fixed_state())):  # a State is not a Shot
            with self.assertRaises(ValueError):
                p.unpack_shot(bad)

    def test_the_echo_flags_sit_in_the_last_two_bytes_and_default_to_none(self):
        # bit 0: CJ holds two guns (the game's twin pistols)
        dual = p.Echo(ack_seq=7, gta_time_ms=4321, pos=(2488.5, -1666.25, 13.5), weapon_held=22, flags=p.ECHO_DUAL_WIELD)
        data = p.pack_echo(dual)
        self.assertEqual(data[30:32], b"\x01\x00")
        self.assertEqual(p.unpack_echo(data), dual)
        self.assertEqual(p.unpack_echo(p.pack_echo(fixed_echo())).flags, 0)
        self.assertEqual(p.ECHO_DUAL_WIELD, 1)

    def test_echo_roundtrip(self):
        self.assertEqual(p.unpack_echo(p.pack_echo(fixed_echo())), fixed_echo())

    def test_rejects_wrong_length(self):
        data = p.pack_state(fixed_state())
        with self.assertRaises(ValueError):
            p.unpack_state(data[:-1])
        with self.assertRaises(ValueError):
            p.unpack_state(data + b"\0")

    def test_rejects_bad_magic_version_type(self):
        data = bytearray(p.pack_state(fixed_state()))
        bad_magic = bytes([data[0] ^ 0xFF]) + bytes(data[1:])
        bad_version = bytes(data[:4]) + b"\x03\x00" + bytes(data[6:])
        wrong_type = bytes(data[:6]) + b"\x02\x00" + bytes(data[8:])
        for bad in (bad_magic, bad_version, wrong_type):
            with self.assertRaises(ValueError):
                p.unpack_state(bad)

    def test_golden_files_match(self):
        self.assertEqual((GOLDEN / "state_v2.bin").read_bytes(), p.pack_state(fixed_state()))
        self.assertEqual((GOLDEN / "echo_v2.bin").read_bytes(), p.pack_echo(fixed_echo()))
        self.assertEqual((GOLDEN / "shot_v2.bin").read_bytes(), p.pack_shot(fixed_shot()))


class PoseTests(unittest.TestCase):
    def test_sizes(self):
        self.assertEqual(len(p.pack_pose(fixed_pose())), 24 + 32 * 3)
        full = p.Pose(1, 2, tuple(p.Bone(i, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0, 1.0)) for i in range(48)))
        self.assertEqual(len(p.pack_pose(full)), 24 + 32 * 48)

    def test_roundtrip(self):
        got = p.unpack_pose(p.pack_pose(fixed_pose()))
        self.assertEqual((got.ack_seq, got.gta_time_ms, got.flags), (7, 4321, 0))
        self.assertAlmostEqual(got.heading, 1.5, delta=2 * math.pi / 65536)
        self.assertEqual([b.node_id for b in got.bones], [1, 5, 53])
        self.assertAlmostEqual(got.bones[2].pos[0], 0.1, places=6)
        self.assertEqual(got.bones[2].quat, (0.5, 0.5, 0.5, 0.5))

    def test_heading_is_a_quantised_angle_that_wraps(self):
        def back(h):
            return p.unpack_pose(p.pack_pose(p.Pose(1, 2, (), heading=h))).heading
        step = 2 * math.pi / 65536
        self.assertEqual(back(0.0), 0.0)
        self.assertAlmostEqual(back(math.pi), math.pi, delta=step)
        self.assertAlmostEqual(back(-math.pi / 2), 3 * math.pi / 2, delta=step)  # negative angles wrap into [0, 2*pi)
        self.assertAlmostEqual(back(2 * math.pi + 1.0), 1.0, delta=step)
        self.assertLess(back(2 * math.pi - 1e-9), 2 * math.pi)  # the top of the range does not overflow to 2*pi
        self.assertEqual(p.pack_pose(p.Pose(1, 2, (), heading=1.5))[22:24], (15646).to_bytes(2, "little"))

    def test_rejects_malformed(self):
        data = p.pack_pose(fixed_pose())
        for bad in (data[:10], data[:-1], data + b"\0"):
            with self.assertRaises(ValueError):
                p.unpack_pose(bad)
        too_many = bytearray(data)
        too_many[20] = 49  # boneCount field (u16 at offset 20)
        with self.assertRaises(ValueError):
            p.unpack_pose(bytes(too_many))
        with self.assertRaises(ValueError):
            p.unpack_pose(p.pack_state(fixed_state()))  # a State is not a Pose
        with self.assertRaises(ValueError):
            p.pack_pose(p.Pose(1, 2, tuple(p.Bone(i, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0, 1.0)) for i in range(49))))

    def test_golden_file_matches(self):
        self.assertEqual((GOLDEN / "pose_v2.bin").read_bytes(), p.pack_pose(fixed_pose()))


class CoordTests(unittest.TestCase):
    def test_delta_mapping(self):
        self.assertEqual(p.mc_delta_to_gta((1.0, 2.0, 3.0)), (1.0, -3.0, 2.0))

    def test_heading(self):
        # MC yaw 0 faces south (+Z) -> GTA faces -Y -> heading pi
        self.assertAlmostEqual(p.mc_yaw_to_gta_heading(0.0), math.pi)
        # MC yaw 90 faces west (-X) -> GTA heading pi/2
        self.assertAlmostEqual(p.mc_yaw_to_gta_heading(90.0), math.pi / 2)
        # MC yaw -90 faces east (+X) -> heading 3pi/2
        self.assertAlmostEqual(p.mc_yaw_to_gta_heading(-90.0), 3 * math.pi / 2)
        # MC yaw 180 faces north (-Z) -> GTA faces +Y -> heading 0
        self.assertAlmostEqual(p.mc_yaw_to_gta_heading(180.0), 0.0)


class JumpIntentTests(unittest.TestCase):
    def test_the_jump_intent_is_its_own_bit_and_travels_in_a_state(self):
        others = p.INTENT_AIM | p.INTENT_FIRE | p.INTENT_MELEE
        self.assertEqual(p.INTENT_JUMP, 1 << 3)
        self.assertEqual(p.INTENT_JUMP & others, 0)
        s = p.State(1, 2, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.0, 0.0, intents=p.INTENT_JUMP | p.INTENT_MELEE)
        back = p.unpack_state(p.pack_state(s))
        self.assertEqual(back.intents, p.INTENT_JUMP | p.INTENT_MELEE)


class BodyFlagTests(unittest.TestCase):
    def test_water_flags_are_free_and_round_trip(self):
        used = [p.FLAG_ON_GROUND, p.FLAG_SPRINT, p.FLAG_SNEAK, p.FLAG_SWIM, p.FLAG_CLIMBING, p.FLAG_FALLING, p.FLAG_KNOCKED, p.FLAG_DEAD,
                p.HURT_MASK, p.FLAG_BLASTED, p.FLAG_WALL_CLIMB, p.FLAG_JETPACK, p.FLAG_JETPACK_THRUST]
        water = [p.FLAG_IN_WATER, p.FLAG_UNDERWATER, p.FLAG_WATER_MOVING]
        self.assertEqual(water, [1 << 16, 1 << 17, 1 << 18])
        for w in water:
            for u in used:
                self.assertEqual(w & u, 0)
        self.assertEqual(p.FLAG_IN_WATER & p.FLAG_UNDERWATER, 0)
        self.assertEqual(p.FLAG_IN_WATER & p.FLAG_WATER_MOVING, 0)
        self.assertEqual(p.FLAG_UNDERWATER & p.FLAG_WATER_MOVING, 0)
        flags = p.FLAG_ON_GROUND | p.FLAG_IN_WATER | p.FLAG_UNDERWATER | p.FLAG_WATER_MOVING
        state = p.State(seq=1, ts_ms=2, pos=(0.0, 0.0, 0.0), vel=(0.0, 0.0, 0.0), yaw=0.0, pitch=0.0, flags=flags, intents=0, weapon=0)
        self.assertEqual(p.unpack_state(p.pack_state(state)).flags, flags)

    def test_jetpack_flags_are_free_and_round_trip(self):
        used = [p.FLAG_ON_GROUND, p.FLAG_SPRINT, p.FLAG_SNEAK, p.FLAG_SWIM, p.FLAG_CLIMBING, p.FLAG_FALLING, p.FLAG_KNOCKED,
                p.FLAG_DEAD, p.HURT_MASK, p.FLAG_BLASTED, p.FLAG_WALL_CLIMB]
        self.assertEqual(p.FLAG_JETPACK, 1 << 14)
        self.assertEqual(p.FLAG_JETPACK_THRUST, 1 << 15)
        for u in used:
            self.assertEqual(p.FLAG_JETPACK & u, 0)
            self.assertEqual(p.FLAG_JETPACK_THRUST & u, 0)
        self.assertEqual(p.FLAG_JETPACK & p.FLAG_JETPACK_THRUST, 0)
        flags = p.FLAG_ON_GROUND | p.FLAG_JETPACK | p.FLAG_JETPACK_THRUST
        state = p.State(seq=1, ts_ms=2, pos=(0.0, 0.0, 0.0), vel=(0.0, 0.0, 0.0), yaw=0.0, pitch=0.0, flags=flags, intents=0, weapon=0)
        self.assertEqual(p.unpack_state(p.pack_state(state)).flags, flags)

    def test_wall_climb_flag_is_free_and_round_trips(self):
        used = [p.FLAG_ON_GROUND, p.FLAG_SPRINT, p.FLAG_SNEAK, p.FLAG_SWIM, p.FLAG_CLIMBING, p.FLAG_FALLING, p.FLAG_KNOCKED,
                p.FLAG_DEAD, p.HURT_MASK, p.FLAG_BLASTED]
        self.assertEqual(p.FLAG_WALL_CLIMB, 1 << 13)
        for u in used:
            self.assertEqual(p.FLAG_WALL_CLIMB & u, 0)
        state = p.State(seq=1, ts_ms=2, pos=(0.0, 0.0, 0.0), vel=(0.0, 0.0, 0.0), yaw=0.0, pitch=0.0,
                        flags=p.FLAG_ON_GROUND | p.FLAG_WALL_CLIMB, intents=0, weapon=0)
        self.assertEqual(p.unpack_state(p.pack_state(state)).flags, p.FLAG_ON_GROUND | p.FLAG_WALL_CLIMB)

    def test_body_flags_do_not_overlap_and_round_trip(self):
        old = p.FLAG_ON_GROUND | p.FLAG_SPRINT | p.FLAG_SNEAK | p.FLAG_SWIM | p.FLAG_CLIMBING
        fields = [p.FLAG_FALLING, p.FLAG_KNOCKED, p.FLAG_DEAD, p.HURT_MASK, p.FLAG_BLASTED]
        self.assertEqual(p.FLAG_FALLING, 1 << 5)
        self.assertEqual(p.FLAG_KNOCKED, 1 << 6)
        self.assertEqual(p.FLAG_DEAD, 1 << 7)
        self.assertEqual(p.HURT_SHIFT, 8)
        self.assertEqual(p.HURT_MASK, 0xF00)
        self.assertEqual(p.FLAG_BLASTED, 1 << 12)
        for i, a in enumerate(fields):
            self.assertEqual(a & old, 0)
            for b in fields[i + 1:]:
                self.assertEqual(a & b, 0)
        self.assertEqual(p.body_flags(True, False, True, 5), p.FLAG_FALLING | p.FLAG_DEAD | (5 << 8))
        self.assertEqual(p.hurt_of(p.body_flags(True, False, True, 5)), 5)
        self.assertEqual(p.body_flags(False, True, False, 0), p.FLAG_KNOCKED)
        self.assertEqual(p.body_flags(False, False, False, 16), 0)  # the counter wraps modulo 16
        self.assertEqual(p.body_flags(False, True, False, 0, True), p.FLAG_KNOCKED | p.FLAG_BLASTED)  # knocked down by an explosion
        self.assertEqual(p.body_flags(False, True, False, 0), p.FLAG_KNOCKED)
        self.assertEqual(p.hurt_of(p.body_flags(False, False, False, 31)), 15)

    def test_a_state_carries_the_body_flags(self):
        flags = p.FLAG_ON_GROUND | p.body_flags(True, True, False, 9)
        s = p.State(1, 2, (0.0, 0.0, 0.0), (0.0, 0.0, 0.0), 0.0, 0.0, flags=flags)
        back = p.unpack_state(p.pack_state(s))
        self.assertEqual(back.flags, flags)
        self.assertEqual(p.hurt_of(back.flags), 9)


if __name__ == "__main__":
    unittest.main()


class VehicleTests(unittest.TestCase):
    def test_sizes_and_types(self):
        self.assertEqual((p.TYPE_VEHICLE_COMMAND, p.TYPE_VEHICLE_INPUT, p.TYPE_VEHICLE_STATE), (5, 6, 7))
        self.assertEqual(len(p.pack_vehicle_command(fixed_vehicle_command())), 24)
        self.assertEqual(len(p.pack_vehicle_input(fixed_vehicle_input())), 36)
        self.assertEqual(len(p.pack_vehicle_state(fixed_vehicle_state())), 68)

    def test_roundtrips(self):
        self.assertEqual(p.unpack_vehicle_command(p.pack_vehicle_command(fixed_vehicle_command())), fixed_vehicle_command())
        self.assertEqual(p.unpack_vehicle_input(p.pack_vehicle_input(fixed_vehicle_input())), fixed_vehicle_input())
        self.assertEqual(p.unpack_vehicle_state(p.pack_vehicle_state(fixed_vehicle_state())), fixed_vehicle_state())

    def test_every_field_has_its_own_place(self):
        cmd = p.unpack_vehicle_command(p.pack_vehicle_command(p.VehicleCommand(1, 2, 3, 4, 5.0)))
        self.assertEqual((cmd.seq, cmd.ts_ms, cmd.command, cmd.model, cmd.yaw), (1, 2, 3, 4, 5.0))
        inp = p.unpack_vehicle_input(p.pack_vehicle_input(p.VehicleInput(1, 2, 3, 4, -5, 6, 7, 8.0, 9.0)))
        self.assertEqual((inp.seq, inp.ts_ms, inp.throttle, inp.brake, inp.steer, inp.handbrake, inp.corr_flags, inp.corr_x, inp.corr_y),
                         (1, 2, 3, 4, -5, 6, 7, 8.0, 9.0))

    def test_rejects_malformed(self):
        for pack, unpack, fixed in ((p.pack_vehicle_command, p.unpack_vehicle_command, fixed_vehicle_command()),
                                    (p.pack_vehicle_input, p.unpack_vehicle_input, fixed_vehicle_input()),
                                    (p.pack_vehicle_state, p.unpack_vehicle_state, fixed_vehicle_state())):
            data = pack(fixed)
            bad_magic = bytes([data[0] ^ 0xFF]) + data[1:]
            bad_version = data[:4] + b"\x03\x00" + data[6:]
            wrong_type = data[:6] + b"\x01\x00" + data[8:]
            for bad in (data[:-1], data + b"\0", bad_magic, bad_version, wrong_type, p.pack_state(fixed_state())):
                with self.assertRaises(ValueError):
                    unpack(bad)

    def test_other_packets_are_not_vehicle_packets_and_back(self):
        with self.assertRaises(ValueError):
            p.unpack_state(p.pack_vehicle_state(fixed_vehicle_state()))
        with self.assertRaises(ValueError):
            p.unpack_vehicle_input(p.pack_vehicle_command(fixed_vehicle_command()))  # 24 vs 36 bytes
        with self.assertRaises(ValueError):
            p.unpack_vehicle_state(p.pack_vehicle_input(fixed_vehicle_input()))

    def test_packet_type_reads_only_the_header(self):
        self.assertEqual(p.packet_type(p.pack_state(fixed_state())), p.TYPE_STATE)
        self.assertEqual(p.packet_type(p.pack_vehicle_input(fixed_vehicle_input())), p.TYPE_VEHICLE_INPUT)
        self.assertEqual(p.packet_type(p.pack_vehicle_state(fixed_vehicle_state())), p.TYPE_VEHICLE_STATE)
        self.assertEqual(p.packet_type(p.pack_pose(fixed_pose())), p.TYPE_POSE)
        for bad in (b"", b"\0" * 7, b"\0" * 8, bytes([1]) + p.pack_state(fixed_state())[1:]):
            self.assertIsNone(p.packet_type(bad))
        data = p.pack_state(fixed_state())
        self.assertIsNone(p.packet_type(data[:4] + b"\x03\x00" + data[6:]))  # wrong version

    def test_golden_files_match(self):
        self.assertEqual((GOLDEN / "vehicle_command_v2.bin").read_bytes(), p.pack_vehicle_command(fixed_vehicle_command()))
        self.assertEqual((GOLDEN / "vehicle_input_v2.bin").read_bytes(), p.pack_vehicle_input(fixed_vehicle_input()))
        self.assertEqual((GOLDEN / "vehicle_state_v2.bin").read_bytes(), p.pack_vehicle_state(fixed_vehicle_state()))
