import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
sys.path.insert(0, str(Path(__file__).resolve().parent.parent.parent / "protocol"))

import fake_mc  # noqa: E402
import cjproto  # noqa: E402


class AimTests(unittest.TestCase):
    def assertVec(self, got, want):
        for g, w in zip(got, want):
            self.assertAlmostEqual(g, w, places=5)

    def test_look_vector_follows_the_minecraft_convention(self):
        self.assertVec(fake_mc.look_vector(0, 0), (0, 0, 1))      # yaw 0 faces south (+Z)
        self.assertVec(fake_mc.look_vector(-90, 0), (1, 0, 0))    # yaw -90 faces east (+X)
        self.assertVec(fake_mc.look_vector(90, 0), (-1, 0, 0))    # yaw 90 faces west
        self.assertVec(fake_mc.look_vector(180, 0), (0, 0, -1))   # yaw 180 faces north
        self.assertVec(fake_mc.look_vector(0, -90), (0, 1, 0))    # pitch -90 looks straight up
        self.assertVec(fake_mc.look_vector(0, 90), (0, -1, 0))    # pitch 90 looks straight down

    def test_aim_vector_starts_at_the_eyes_and_reaches_the_distance(self):
        self.assertVec(fake_mc.aim_vector(0, 0), (0, 1.62, 20))
        self.assertVec(fake_mc.aim_vector(-90, -30, eye_height=1.5, distance=10),
                       (10 * math.cos(math.radians(30)), 1.5 + 10 * math.sin(math.radians(30)), 0))


class ScenarioTests(unittest.TestCase):
    def test_idle_is_unarmed_and_does_not_aim(self):
        s = fake_mc.Scenario("idle", weapon=22).state_at(1.0, 5)
        self.assertEqual((s.weapon, s.intents, s.aim), (0, 0, (0.0, 0.0, 0.0)))

    def test_aim_holds_the_weapon_and_aims(self):
        s = fake_mc.Scenario("aim", weapon=22).state_at(1.0, 5)
        self.assertEqual((s.weapon, s.intents), (22, cjproto.INTENT_AIM))
        self.assertNotEqual(s.aim, (0.0, 0.0, 0.0))

    def test_fire_aims_and_fires(self):
        s = fake_mc.Scenario("fire", weapon=22).state_at(1.0, 5)
        self.assertEqual(s.intents, cjproto.INTENT_AIM | cjproto.INTENT_FIRE)

    def test_sweep_moves_the_aim_over_time(self):
        sc = fake_mc.Scenario("sweep", weapon=22)
        self.assertNotEqual(sc.state_at(0.0, 1).aim, sc.state_at(1.0, 2).aim)

    def test_sweepfire_sweeps_while_firing(self):
        sc = fake_mc.Scenario("sweepfire", weapon=22)
        a, b = sc.state_at(0.0, 1), sc.state_at(1.0, 2)
        self.assertEqual(a.intents, cjproto.INTENT_AIM | cjproto.INTENT_FIRE)
        self.assertNotEqual(a.aim, b.aim)

    def test_distance_and_pitch_amplitude_are_configurable(self):
        near = fake_mc.Scenario("aim", weapon=22, distance=3.0).state_at(1.0, 5)
        self.assertAlmostEqual(math.sqrt(near.aim[0] ** 2 + (near.aim[1] - 1.62) ** 2 + near.aim[2] ** 2), 3.0, places=5)
        sc = fake_mc.Scenario("sweep", weapon=22, pitch_amp=80.0)
        pitches = [sc.state_at(t / 10, 1).pitch for t in range(0, 100)]
        self.assertGreater(max(abs(p) for p in pitches), 60.0)
        self.assertLessEqual(max(abs(p) for p in pitches), 80.0)

    def test_spin_turns_continuously_at_the_given_rate_and_wraps(self):
        sc = fake_mc.Scenario("spin", weapon=22, spin_rate=90.0)
        a, b = sc.state_at(0.0, 1), sc.state_at(1.0, 2)
        self.assertEqual(a.intents, cjproto.INTENT_AIM)
        self.assertAlmostEqual(b.yaw - a.yaw, 90.0 if b.yaw > a.yaw else 90.0 - 360.0, places=3)
        yaws = [sc.state_at(t / 10, 1).yaw for t in range(0, 100)]
        self.assertTrue(all(-180.0 <= y < 180.0 for y in yaws))  # wrapped like Minecraft's yaw
        self.assertNotEqual(sc.state_at(0.0, 1).aim, sc.state_at(1.0, 2).aim)

    def test_pitch_mode_sweeps_only_the_pitch(self):
        sc = fake_mc.Scenario("pitch", weapon=30, pitch_amp=60.0)
        states = [sc.state_at(t / 10, 1) for t in range(0, 120)]
        self.assertTrue(all(s.yaw == 0.0 for s in states))
        self.assertGreater(max(s.pitch for s in states), 50.0)
        self.assertLess(min(s.pitch for s in states), -50.0)
        self.assertEqual(states[5].intents, cjproto.INTENT_AIM)

    def test_yaw_of_a_quaternion(self):
        s = math.sqrt(0.5)
        self.assertAlmostEqual(fake_mc.yaw_of_quat((0, 0, 0, 1)), 0.0)
        self.assertAlmostEqual(fake_mc.yaw_of_quat((0, 0, s, s)), 90.0, places=4)    # a quarter turn about Z
        self.assertAlmostEqual(fake_mc.yaw_of_quat((0, 0, -s, s)), -90.0, places=4)
        self.assertAlmostEqual(fake_mc.yaw_of_quat((s, 0, 0, s)), 0.0, places=4)     # a turn about X is a roll, not a yaw

    def test_walking_and_sprinting_are_in_the_state(self):
        walk = fake_mc.Scenario("held", weapon=30, speed=4.3).state_at(1.0, 5)
        self.assertEqual(walk.vel, (0.0, 0.0, 4.3))
        self.assertFalse(walk.flags & cjproto.FLAG_SPRINT)
        run = fake_mc.Scenario("held", weapon=30, speed=5.6, sprint=True).state_at(1.0, 5)
        self.assertTrue(run.flags & cjproto.FLAG_SPRINT)
        self.assertTrue(run.flags & cjproto.FLAG_ON_GROUND)
        still = fake_mc.Scenario("held", weapon=30).state_at(1.0, 5)
        self.assertEqual(still.vel, (0.0, 0.0, 0.0))

    def test_held_only_carries_the_weapon_without_aiming(self):
        s = fake_mc.Scenario("held", weapon=25).state_at(1.0, 5)
        self.assertEqual((s.weapon, s.intents, s.aim), (25, 0, (0.0, 0.0, 0.0)))

    def test_standing_state_is_well_formed(self):
        s = fake_mc.Scenario("aim", weapon=22).state_at(1.0, 5)
        self.assertEqual(s.seq, 5)
        self.assertTrue(s.flags & cjproto.FLAG_ON_GROUND)
        self.assertEqual(s.vel, (0.0, 0.0, 0.0))
        cjproto.unpack_state(cjproto.pack_state(s))  # it encodes


if __name__ == "__main__":
    unittest.main()
