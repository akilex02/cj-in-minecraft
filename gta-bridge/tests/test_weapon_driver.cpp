#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstdlib>

#include "weapon_driver.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

using namespace cj;

static StatePacket State(uint16_t weapon, uint32_t intents, float ax = 0.0f, float ay = 1.6f, float az = -20.0f) {
    StatePacket s{};
    s.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::State)};
    s.weapon = weapon;
    s.intents = intents;
    s.aim[0] = ax;
    s.aim[1] = ay;
    s.aim[2] = az;
    return s;
}

static bool Near(Vec3 a, Vec3 b) {
    return std::fabs(a.x - b.x) < 1e-3f && std::fabs(a.y - b.y) < 1e-3f && std::fabs(a.z - b.z) < 1e-3f;
}

int main() {
    const Vec3 at{100, 200, 30};

    { // unarmed: nothing to do
        WeaponDriver d;
        d.OnState(State(0, 0));
        GunPlan p = d.Plan(at, 0);
        CHECK(!p.giveWeapon && !p.takeWeapons && !p.wantTask && !p.stopTask);
        CHECK(!d.Given());
    }

    { // only the fists (nothing in hand) and the bat make blows: anything else GTA holds is a gun
        CHECK(IsMeleeWeapon(0) && IsMeleeWeapon(5));
        CHECK(!IsMeleeWeapon(22) && !IsMeleeWeapon(35) && !IsMeleeWeapon(38) && !IsMeleeWeapon(9999));
    }

    { // the strike detector: one blow per animation, counted when the animation reaches GTA's hit time
        StrikeDetector d;
        const float hit = 0.367f;
        uint16_t total = 0;
        for (float t = 0.0f; t < 0.34f; t += 0.0105f) {
            total += d.Update(true, 0, 100, t, hit);
        }
        CHECK(total == 0); // the blow is under way, not yet at its hit time
        CHECK(d.Update(true, 0, 100, 0.37f, hit) == 1); // the frame it reaches the hit time
        CHECK(d.Update(true, 0, 100, 0.38f, hit) == 0); // and only that frame
        CHECK(d.Update(true, 0, 100, 0.43f, hit) == 0);
        CHECK(d.Update(false, 0, 0, 0.0f, hit) == 0); // the blow ended already counted: nothing more
    }

    { // a combo is three blows (moves 0, 1 and 2), each with its own animation
        StrikeDetector d;
        const float hits[3] = {0.367f, 0.467f, 0.433f};
        uint16_t total = 0;
        for (int round = 0; round < 2; round++) {
            for (int move = 0; move < 3; move++) {
                for (float t = 0.0f; t <= 0.6f; t += 0.0105f) {
                    total += d.Update(true, static_cast<int8_t>(move), 200 + move, t, hits[move]);
                }
            }
        }
        total += d.Update(false, 0, 0, 0.0f, 0.367f);
        CHECK(total == 6);
    }

    { // a blow cut by the next one before its hit time still counts, once, when it is cut
        StrikeDetector d;
        uint16_t total = 0;
        for (float t = 0.0f; t < 0.33f; t += 0.0105f) {
            total += d.Update(true, 0, 100, t, 0.367f);
        }
        CHECK(total == 0);
        CHECK(d.Update(true, 1, 101, 0.0f, 0.467f) == 1); // the first blow is closed by the second one starting
        uint16_t second = 0;
        for (float t = 0.0105f; t <= 0.6f; t += 0.0105f) {
            second += d.Update(true, 1, 101, t, 0.467f);
        }
        CHECK(second == 1);
    }

    { // the task going away in the middle of a blow counts it once; frames with no blow count nothing and open no blow
        StrikeDetector d;
        CHECK(d.Update(true, 2, 300, 0.1f, 0.433f) == 0);
        CHECK(d.Update(false, 2, 300, 0.2f, 0.433f) == 1);
        CHECK(d.Update(false, 2, 300, 0.3f, 0.433f) == 0);
        CHECK(d.Update(true, 3, 300, 0.1f, 0.433f) == 0); // 3 is a block, not a blow
        CHECK(d.Update(true, 4, 300, 0.1f, 0.433f) == 0); // 4 is the idle stance
        CHECK(d.Update(true, -1, 300, 0.1f, 0.433f) == 0);
        CHECK(d.Update(true, 0, 0, 0.1f, 0.367f) == 0);   // no animation
        CHECK(d.Update(false, 0, 0, 0.0f, 0.367f) == 0);  // none of those opened a blow that could be counted later
    }

    { // the same move and animation again after the time went back is a new blow; a bad time is no blow at all
        StrikeDetector d;
        CHECK(d.Update(true, 0, 100, 0.40f, 0.367f) == 1);
        CHECK(d.Update(true, 0, 100, 0.0f, 0.367f) == 0);  // time went back: the previous blow is closed (already counted) and a new one opens
        CHECK(d.Update(true, 0, 100, 0.40f, 0.367f) == 1); // and it counts too
        StrikeDetector n;
        CHECK(n.Update(true, 0, 100, NAN, 0.367f) == 0);
        CHECK(n.Update(false, 0, 0, 0.0f, 0.367f) == 0);   // a NaN time never opened a blow
        StrikeDetector h;
        CHECK(h.Update(true, 0, 100, 0.5f, NAN) == 0);     // no hit time: only the closing counts it
        CHECK(h.Update(false, 0, 0, 0.0f, 0.0f) == 1);
    }

    { // the game reuses one animation object for different moves, and a combo can repeat a move: either change opens a new blow
        StrikeDetector d;
        CHECK(d.Update(true, 0, 100, 0.40f, 0.367f) == 1);
        CHECK(d.Update(true, 1, 100, 0.10f, 0.467f) == 0); // same animation, next move: a new blow, not yet at its hit time
        CHECK(d.Update(true, 1, 100, 0.50f, 0.467f) == 1);
        StrikeDetector f; // the move alone tells it apart even when the time did not go back
        CHECK(f.Update(true, 0, 100, 0.40f, 0.367f) == 1);
        CHECK(f.Update(true, 1, 100, 0.45f, 0.467f) == 0);
        CHECK(f.Update(true, 1, 100, 0.47f, 0.467f) == 1);
        StrikeDetector e;
        CHECK(e.Update(true, 0, 100, 0.40f, 0.367f) == 1);
        CHECK(e.Update(true, 0, 101, 0.42f, 0.367f) == 1); // same move, another animation object: another blow (its time is already past the hit time)
    }

    { // Reset forgets the blow under way without counting it
        StrikeDetector d;
        d.Update(true, 0, 100, 0.1f, 0.367f);
        d.Reset();
        CHECK(d.Update(false, 0, 0, 0.0f, 0.367f) == 0);
    }

    { // the counter on the wire is shared by firearms and melee and never goes back
        ShotCounter c;
        CHECK(c.Credit(3) == 3 && c.Total() == 3);
        CHECK(c.Credit(0) == 0 && c.Total() == 3);
        c.Rebase();
        CHECK(c.Total() == 3);
        CHECK(c.Update(25, 10) == 0);
        CHECK(c.Update(25, 8) == 2 && c.Total() == 5);
        CHECK(c.Credit(1) == 1 && c.Total() == 6);
    }

    { // melee turns CJ towards where the player looks and never asks for the gun task
        const float kPi = 3.14159265358979f;
        auto heading = [&](uint16_t weapon, uint16_t held, float yaw, bool* faces) {
            WeaponDriver d;
            StatePacket st = State(weapon, kIntentMelee);
            st.yaw = yaw;
            d.OnState(st);
            GunPlan p = d.Plan(at, held);
            CHECK(!p.wantTask && !p.stopTask);
            *faces = p.faceHeading;
            return p.heading;
        };
        bool faces = false;
        float h = heading(0, 0, 90.0f, &faces); // fists, nothing held
        CHECK(faces && std::fabs(h - (kPi - kPi / 2)) < 1e-4f);
        h = heading(5, 5, 90.0f, &faces); // bat already in hand
        CHECK(faces && std::fabs(h - (kPi - kPi / 2)) < 1e-4f);
        h = heading(5, 5, -90.0f, &faces); // a heading is always in [0, 2 pi)
        CHECK(faces && h >= 0.0f && h < 2 * kPi && std::fabs(h - (kPi + kPi / 2)) < 1e-4f);
        WeaponDriver giving;
        giving.OnState(State(5, kIntentMelee));
        GunPlan p = giving.Plan(at, 0); // the bat is given first: no facing yet
        CHECK(p.giveWeapon && p.weapon == 5 && !p.faceHeading && !p.wantTask);
        WeaponDriver idle;
        idle.OnState(State(5, 0));
        idle.Plan(at, 0);
        p = idle.Plan(at, 5);
        CHECK(!p.faceHeading); // no blow: GTA keeps its own heading
        WeaponDriver nan;
        StatePacket st = State(0, kIntentMelee);
        st.yaw = NAN;
        nan.OnState(st);
        CHECK(!nan.Plan(at, 0).faceHeading);
    }

    { // the weapon of the held item is given first; the task waits until CJ really holds it
        WeaponDriver d;
        d.OnState(State(22, kIntentAim));
        GunPlan p = d.Plan(at, 0);
        CHECK(p.giveWeapon && p.weapon == 22 && !p.wantTask);
        CHECK(d.Given());
        p = d.Plan(at, 22); // now he holds it
        CHECK(!p.giveWeapon && p.wantTask);
    }

    { // aiming: the task aims at feet + aim vector, converted to GTA axes; the fire intent upgrades the command
        WeaponDriver d;
        d.OnState(State(22, kIntentAim, 0.0f, 1.6f, -20.0f)); // 20 blocks north (-Z in Minecraft), 1.6 up
        GunPlan p = d.Plan(at, 22);
        CHECK(p.wantTask && p.command == GunCommand::Aim);
        CHECK(Near(p.aimTarget, {100.0f, 220.0f, 31.6f})); // GTA: north is +Y
        d.OnState(State(22, kIntentAim | kIntentFire, 0.0f, 1.6f, -20.0f));
        CHECK(d.Plan(at, 22).command == GunCommand::Fire);
        d.OnState(State(22, kIntentFire, 5.0f, 1.0f, 0.0f)); // firing from the hip is allowed too
        p = d.Plan(at, 22);
        CHECK(p.wantTask && p.command == GunCommand::Fire);
        CHECK(Near(p.aimTarget, {105.0f, 200.0f, 31.0f}));
    }

    { // letting go of the aim stops the task once, not every frame
        WeaponDriver d;
        d.OnState(State(22, kIntentAim));
        CHECK(d.Plan(at, 22).wantTask);
        d.OnState(State(22, 0));
        GunPlan p = d.Plan(at, 22);
        CHECK(!p.wantTask && p.stopTask);
        p = d.Plan(at, 22);
        CHECK(!p.wantTask && !p.stopTask);
    }

    { // Review Focus #3: a missing, non-finite or absurd aim vector never creates a task
        WeaponDriver d;
        d.OnState(State(22, kIntentAim, 0, 0, 0));
        CHECK(!d.Plan(at, 22).wantTask);
        d.OnState(State(22, kIntentAim, NAN, 1, 1));
        CHECK(!d.Plan(at, 22).wantTask);
        d.OnState(State(22, kIntentAim, INFINITY, 1, 1));
        CHECK(!d.Plan(at, 22).wantTask);
        d.OnState(State(22, kIntentAim, 0, 5000.0f, 0));
        CHECK(!d.Plan(at, 22).wantTask);
        d.OnState(State(22, kIntentFire, 0, 0, 0)); // fire without any aim vector
        CHECK(!d.Plan(at, 22).wantTask);
    }

    { // Review Focus #2: dropping the weapon takes it away once, and stops a task in progress
        WeaponDriver d;
        d.OnState(State(22, kIntentAim));
        d.Plan(at, 0);
        d.Plan(at, 22);
        d.OnState(State(0, 0));
        GunPlan p = d.Plan(at, 22);
        CHECK(p.takeWeapons && p.stopTask && !p.wantTask);
        CHECK(!d.Given());
        p = d.Plan(at, 0);
        CHECK(!p.takeWeapons && !p.stopTask);
    }

    { // Review Focus #2: switching weapons gives the new one and stops the old task
        WeaponDriver d;
        d.OnState(State(22, kIntentAim));
        d.Plan(at, 0);
        CHECK(d.Plan(at, 22).wantTask);
        d.OnState(State(25, kIntentAim));
        GunPlan p = d.Plan(at, 22);
        CHECK(p.giveWeapon && p.weapon == 25 && p.stopTask && !p.wantTask);
        CHECK(d.Plan(at, 25).wantTask);
    }

    { // Reset forgets everything (the puppet expired): nothing is taken away by a driver that gave nothing
        WeaponDriver d;
        d.OnState(State(22, kIntentAim));
        d.Plan(at, 0);
        d.Reset();
        CHECK(!d.Given());
        GunPlan p = d.Plan(at, 22);
        CHECK(!p.giveWeapon && !p.takeWeapons && !p.wantTask);
    }

    { // rounds fired = how far the clip went down, for the same weapon
        ShotCounter c;
        CHECK(c.Update(22, 17) == 0);
        CHECK(c.Update(22, 16) == 1);
        CHECK(c.Update(22, 16) == 0);
        CHECK(c.Update(22, 14) == 2);   // two rounds in one frame
        CHECK(c.Update(22, 17) == 0);   // reload refills the clip: not a shot
        CHECK(c.Update(25, 1) == 0);    // another weapon: no comparison with the old clip
        CHECK(c.Update(25, 0) == 1);
        CHECK(c.Total() == 4);
        c.Reset();
        CHECK(c.Total() == 0 && c.Update(25, 3) == 0);
    }

    { // changing weapon or ending a session forgets the clip but never the total: the counter on the wire must not go back
        ShotCounter c;
        c.Update(22, 17);
        CHECK(c.Update(22, 15) == 2);
        CHECK(c.Total() == 2);
        c.Rebase();
        CHECK(c.Total() == 2);              // kept
        CHECK(c.Update(30, 30) == 0);       // the first clip seen after a rebase is only a baseline
        CHECK(c.Update(30, 29) == 1);
        CHECK(c.Total() == 3);              // continues from 2, not from 0
    }

    { // the model of each weapon: it must be streamed in before the game is asked to give the weapon
        CHECK(WeaponModelId(22) == 346);  // Colt 45
        CHECK(WeaponModelId(24) == 348);  // Desert Eagle
        CHECK(WeaponModelId(25) == 349);  // shotgun
        CHECK(WeaponModelId(28) == 352);  // Micro SMG
        CHECK(WeaponModelId(30) == 355);  // AK-47
        CHECK(WeaponModelId(31) == 356);  // M4
        CHECK(WeaponModelId(38) == 362);  // minigun
        CHECK(WeaponModelId(35) == 359);  // rocket launcher
        CHECK(WeaponModelId(5) == 336);   // baseball bat
        CHECK(WeaponModelId(0) == 0);     // unarmed has no model
        CHECK(WeaponModelId(16) == 0);    // a grenade: not supported in M2, so no model, so it is never given
        CHECK(WeaponModelId(9999) == 0);
    }

    { // the rocket launchers fire a projectile the mod makes itself: GTA's own is removed
        CHECK(FiresProjectile(35) && FiresProjectile(36));
        CHECK(!FiresProjectile(22) && !FiresProjectile(38) && !FiresProjectile(30) && !FiresProjectile(0) && !FiresProjectile(9999));
    }

    { // the proof shield: invulnerable, explosion proof and fire proof while it is on, the ped's own bits of those flags back when it goes off
        const uint32_t kBoth = kPhysicalInvulnerable | kPhysicalExplosionProof | kPhysicalFireProof;
        CHECK(kPhysicalInvulnerable == (1u << 22) && kPhysicalExplosionProof == (1u << 23) && kPhysicalFireProof == (1u << 19)); // CPhysical::physicalFlags (gta-reversed)
        ProofShield shield;
        uint32_t flags = 0x00000042u; // other bits (gravity...) must survive
        CHECK(!shield.Active());
        CHECK(shield.Update(flags, false) == flags); // off and never on: nothing changes
        flags = shield.Update(flags, true);
        CHECK(shield.Active() && (flags & kBoth) == kBoth && (flags & ~kBoth) == 0x42u);
        CHECK(shield.Update(flags, true) == flags); // still on: no change
        flags = shield.Update(flags, false);
        CHECK(!shield.Active() && flags == 0x42u);
        // a ped that was already invulnerable or fire proof stays so after the shield
        uint32_t own = 0x42u | kPhysicalInvulnerable | kPhysicalFireProof;
        own = shield.Update(own, true);
        own = shield.Update(own, false);
        CHECK(own == (0x42u | kPhysicalInvulnerable | kPhysicalFireProof));
        // Reset forgets without writing anything (another ped, a new game)
        shield.Update(0x42u, true);
        shield.Reset();
        CHECK(!shield.Active());
        CHECK(shield.Update(0x42u, false) == 0x42u);
    }

    { // a weapon that can be given must have a slot, or the arsenal never notes it and taking it away leaves CJ holding it (the minigun did)
        for (uint16_t weapon = 0; weapon < 100; weapon++) {
            if (WeaponModelId(weapon) != 0) {
                CHECK(WeaponSlot(weapon) >= 0);
            }
        }
    }

    { // while aiming or firing, CJ in GTA must face where the Minecraft player looks (GTA heading = pi - yaw)
        const float kPi = 3.14159265358979f;
        auto headingFor = [&](float yawDeg, uint32_t intents) {
            WeaponDriver d;
            StatePacket st = State(22, intents);
            st.yaw = yawDeg;
            d.OnState(st);
            d.Plan(at, 0);              // gives the weapon
            return d.Plan(at, 22);      // holds it: the plan to aim
        };
        GunPlan p = headingFor(0.0f, kIntentAim);     // looking south
        CHECK(p.faceHeading && std::fabs(p.heading - kPi) < 1e-4f);
        p = headingFor(-90.0f, kIntentAim);           // east: three quarters of a turn counter-clockwise from north
        CHECK(p.faceHeading && std::fabs(p.heading - 1.5f * kPi) < 1e-4f);
        p = headingFor(90.0f, kIntentFire | kIntentAim); // west
        CHECK(p.faceHeading && std::fabs(p.heading - 0.5f * kPi) < 1e-4f);
        p = headingFor(180.0f, kIntentAim);           // north: 0, never negative or 2*pi
        CHECK(p.faceHeading && p.heading >= 0.0f && p.heading < 1e-4f);
        p = headingFor(-540.0f, kIntentAim);          // a yaw that is not wrapped still gives a heading in [0, 2*pi)
        CHECK(p.faceHeading && p.heading >= 0.0f && p.heading < 2 * kPi);
        p = headingFor(0.0f, 0);                      // not aiming: GTA keeps its own heading
        CHECK(!p.faceHeading);
        p = headingFor(NAN, kIntentAim);              // a broken yaw never turns CJ
        CHECK(!p.faceHeading);
    }

    { // the camera's horizontal orbit angle for a direction: the GTA heading of the direction minus a quarter turn (modulo a full turn)
        const float kPi = 3.14159265358979f;
        auto same = [&](float a, float b) {
            float d = std::fmod(std::fabs(a - b), 2 * kPi);
            return std::min(d, 2 * kPi - d) < 1e-4f;
        };
        CHECK(same(CameraHorizontalAngle({0, 1, 0}), -0.5f * kPi));    // north: heading 0
        CHECK(same(CameraHorizontalAngle({-1, 0, 0}), 0.0f));          // west: heading pi/2
        CHECK(same(CameraHorizontalAngle({0, -1, 5}), 0.5f * kPi));    // south: heading pi; the height is ignored
        CHECK(same(CameraHorizontalAngle({1, 0, 0}), kPi));            // east: heading -pi/2
        CHECK(same(CameraHorizontalAngle({3, 3, 0}), -0.25f * kPi - 0.5f * kPi)); // north-east: heading -pi/4
        CHECK(std::isfinite(CameraHorizontalAngle({0, 0, 1})));        // straight up: no heading, but a number
    }

    { // the weapon slot of each firearm (the game keeps one weapon per slot)
        CHECK(WeaponSlot(22) == 2 && WeaponSlot(23) == 2 && WeaponSlot(24) == 2);   // pistols
        CHECK(WeaponSlot(25) == 3 && WeaponSlot(27) == 3);                          // shotguns
        CHECK(WeaponSlot(28) == 4 && WeaponSlot(29) == 4 && WeaponSlot(32) == 4);   // sub-machine guns
        CHECK(WeaponSlot(30) == 5 && WeaponSlot(31) == 5);                          // assault rifles
        CHECK(WeaponSlot(33) == 6 && WeaponSlot(34) == 6);                          // rifles
        CHECK(WeaponSlot(38) == 7);                                                 // heavy weapons: the minigun
        CHECK(WeaponSlot(35) == 7);                                                 // heavy weapons: the rocket launcher
        CHECK(WeaponSlot(5) == 1);                                                  // melee: the baseball bat
        CHECK(WeaponSlot(0) == -1 && WeaponSlot(9999) == -1 && WeaponSlot(6) == -1); // not a firearm we handle
    }

    { // I3: taking weapons away only removes what the bridge gave and puts back what it replaced, never the whole arsenal
        Arsenal a;
        CHECK(a.TakeAll().empty());                       // nothing given, nothing to take
        a.OnGive(2, 22, 0, 0);                            // an empty slot
        auto steps = a.TakeAll();
        CHECK(steps.size() == 1 && steps[0].clear == 22 && steps[0].restoreType == 0);
        CHECK(a.TakeAll().empty());                       // done once

        a.OnGive(2, 22, 24, 150);                         // the player's own Desert Eagle is in the slot: it is saved
        steps = a.TakeAll();
        CHECK(steps.size() == 1 && steps[0].clear == 22 && steps[0].restoreType == 24 && steps[0].restoreAmmo == 150);

        a.OnGive(2, 22, 23, 50);                          // the bridge's Colt over the player's silenced pistol...
        a.OnGive(2, 24, 22, 99999);                       // ...then its Deagle over its own Colt: the player's pistol is still the one saved
        steps = a.TakeAll();
        CHECK(steps.size() == 1 && steps[0].clear == 24 && steps[0].restoreType == 23 && steps[0].restoreAmmo == 50);

        a.OnGive(2, 22, 22, 30);                          // the player already had a Colt: it is theirs, not taken
        CHECK(a.TakeAll().empty());

        a.OnGive(2, 22, 0, 0);
        a.OnGive(5, 30, 31, 90);                          // two slots at once
        steps = a.TakeAll();
        CHECK(steps.size() == 2);
        CHECK(((steps[0].clear == 22 && steps[1].clear == 30) || (steps[0].clear == 30 && steps[1].clear == 22)));

        a.OnGive(-1, 22, 0, 0);                           // a slot that does not exist is ignored
        a.OnGive(13, 22, 0, 0);
        CHECK(a.TakeAll().empty());

        a.OnGive(2, 22, 0, 0);
        a.Reset();                                        // forgetting (a new game): nothing is taken from whoever is there now
        CHECK(a.TakeAll().empty());
    }

    std::puts("test_weapon_driver OK");
    return 0;
}
