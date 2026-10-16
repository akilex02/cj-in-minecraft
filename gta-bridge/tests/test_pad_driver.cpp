#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>

#include "pad_driver.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

using namespace cj;

static StatePacket State(float vx, float vy, float vz, uint32_t flags) {
    StatePacket s{};
    s.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::State)};
    s.vel[0] = vx;
    s.vel[1] = vy;
    s.vel[2] = vz;
    s.flags = flags;
    return s;
}

static StatePacket StateCrouch(float vx, float vy, float vz, uint32_t flags, bool crouch) {
    StatePacket s = State(vx, vy, vz, flags);
    s.intents = crouch ? kIntentCrouch : 0;
    return s;
}

int main() {
    { // nothing received yet: the driver does not touch the pad
        PadDriver d;
        CHECK(!d.Command().active);
    }

    { // standing still, running, walking (sneak)
        PadDriver d;
        d.OnState(State(0, 0, 0, kFlagOnGround));
        PadCommand c = d.Command();
        CHECK(c.active && c.stickY == 0 && c.walk == 0 && c.cross == 0 && c.square == 0);

        d.OnState(State(3.0f, 0, 4.0f, kFlagOnGround)); // 5 blocks/s horizontal
        c = d.Command();
        CHECK(c.stickY == -128 && c.stickX == 0 && c.walk == 0 && c.cross == 0);

        d.OnState(State(0.5f, 0, 0, kFlagOnGround | kFlagSneak));
        c = d.Command();
        CHECK(c.stickY == -128 && c.walk == 1);

        d.OnState(State(0.1f, 0, 0.1f, kFlagOnGround)); // below the threshold: still idle
        c = d.Command();
        CHECK(c.stickY == 0);
    }

    { // flying with the jetpack: the pose of the jetpack does not walk or run, however fast the player moves in the air
        PadDriver d;
        d.OnState(State(3.0f, 0, 4.0f, kFlagJetpack));
        PadCommand c = d.Command();
        CHECK(c.active && c.stickY == 0 && c.stickX == 0 && c.cross == 0 && c.square == 0);
        d.OnState(State(3.0f, 0, 4.0f, kFlagJetpack | kFlagSprint));          // sprinting in the air is not mashing Cross either
        for (int i = 0; i < 4; i++) {
            c = d.Command();
            CHECK(c.stickY == 0 && c.cross == 0);
        }
        d.OnState(State(3.0f, 0, 4.0f, kFlagOnGround));                       // the jetpack off: he walks again
        CHECK(d.Command().stickY == -128);
    }

    { // in the water the game does not walk, run or jump CJ either: the swimming pose would be cut by the walk starting under it
        PadDriver d;
        d.OnState(State(3.0f, 0, 4.0f, kFlagInWater | kFlagWaterMoving));
        PadCommand c = d.Command();
        CHECK(c.active && c.stickY == 0 && c.stickX == 0 && c.cross == 0 && c.square == 0);
        d.OnState(State(0, 0, 0, kFlagOnGround));
        d.OnState(State(0, 8.0f, 0, kFlagInWater));                            // swimming up: not a jump
        CHECK(d.Command().square == 0);
        d.OnState(State(3.0f, 0, 4.0f, kFlagOnGround));                        // out of the water: he walks again
        CHECK(d.Command().stickY == -128);
    }

    { // lifting off with the jetpack is not a jump of the game (it would really jump in GTA)
        PadDriver d;
        d.OnState(State(0, 0, 0, kFlagOnGround));
        d.OnState(State(0, 8.0f, 0, kFlagJetpack | kFlagJetpackThrust));      // thrust: he leaves the ground
        CHECK(d.Command().square == 0);
        d.OnState(State(0, 0, 0, kFlagOnGround));
        d.OnState(State(0, 8.0f, 0, 0));                                       // the same without the jetpack is a jump
        CHECK(d.Command().square == 1);
    }

    { // vertical speed alone is not walking
        PadDriver d;
        d.OnState(State(0, 9.0f, 0, 0));
        CHECK(d.Command().stickY == 0);
    }

    { // sprint presses Cross on alternate frames, like mashing the button; not while sneaking
        PadDriver d;
        d.OnState(State(5.6f, 0, 0, kFlagOnGround | kFlagSprint));
        CHECK(d.Command().cross == 1);
        CHECK(d.Command().cross == 0);
        CHECK(d.Command().cross == 1);
        PadDriver s;
        s.OnState(State(5.6f, 0, 0, kFlagOnGround | kFlagSprint | kFlagSneak));
        CHECK(s.Command().cross == 0);
        CHECK(s.Command().cross == 0);
        PadDriver idle; // sprint flag but no movement
        idle.OnState(State(0, 0, 0, kFlagOnGround | kFlagSprint));
        CHECK(idle.Command().cross == 0);
    }

    { // Review Focus #6: Square is pulsed once, when leaving the ground upward
        PadDriver d;
        d.OnState(State(0, 0, 0, kFlagOnGround));
        CHECK(d.Command().square == 0);
        d.OnState(State(0, 8.0f, 0, 0)); // takes off
        CHECK(d.Command().square == 1);
        CHECK(d.Command().square == 0); // one-shot
        d.OnState(State(0, 7.0f, 0, 0)); // still in the air
        CHECK(d.Command().square == 0);
        d.OnState(State(0, 0, 0, kFlagOnGround)); // lands
        CHECK(d.Command().square == 0);
        d.OnState(State(0, 8.0f, 0, 0)); // jumps again
        CHECK(d.Command().square == 1);
    }

    { // Review Focus #6: the very first packet already in the air is not a jump; walking off a ledge is not a jump
        PadDriver d;
        d.OnState(State(0, 0, 0, 0));
        CHECK(d.Command().square == 0);
        PadDriver e;
        e.OnState(State(2.0f, 0, 0, kFlagOnGround));
        e.OnState(State(2.0f, -3.0f, 0, 0)); // leaves the ground going down
        CHECK(e.Command().square == 0);
    }

    { // Reset forgets everything
        PadDriver d;
        d.OnState(State(5.0f, 0, 0, kFlagOnGround));
        d.Reset();
        CHECK(!d.Command().active);
    }

    { // ApplyPad writes exactly the five fields and leaves every other byte alone
        uint8_t pad[gta::kPadSize];
        std::memset(pad, 0x7E, sizeof(pad));
        PadCommand c;
        c.active = true;
        c.stickX = 0;
        c.stickY = -128;
        c.walk = 1;
        c.cross = 1;
        c.square = 1;
        ApplyPad(pad, c);
        auto rd = [&](size_t off) { int16_t v; std::memcpy(&v, pad + off, 2); return v; };
        CHECK(rd(gta::kPadLeftStickX) == 0);
        CHECK(rd(gta::kPadLeftStickY) == -128);
        CHECK(rd(gta::kPadButtonSquare) == 1);
        CHECK(rd(gta::kPadButtonCross) == 1);
        CHECK(rd(gta::kPadPedWalk) == 1);
        CHECK(rd(gta::kPadShockButtonL) == 0); // the duck button is let go unless the command presses it
        int touched = 0;
        for (size_t i = 0; i < sizeof(pad); i++) {
            touched += pad[i] != 0x7E;
        }
        CHECK(touched <= 6 * 2); // six int16 fields, at most twelve bytes
        CHECK(pad[gta::kPadLeftStickY + 2] == 0x7E && pad[gta::kPadButtonSquare - 1] == 0x7E);

        uint8_t untouched[gta::kPadSize];
        std::memset(untouched, 0x7E, sizeof(untouched));
        ApplyPad(untouched, PadCommand{}); // inactive
        for (size_t i = 0; i < sizeof(untouched); i++) {
            CHECK(untouched[i] == 0x7E);
        }
    }

    { // melee (fists or bat): the attack button is pressed with no aim button, with or without a weapon or a usable aim vector
        auto melee = [](uint16_t weapon, uint32_t intents) {
            StatePacket s = State(0, 0, 0, kFlagOnGround);
            s.weapon = weapon;
            s.intents = intents; // the aim vector stays (0, 0, 0): a blow needs none
            return s;
        };
        PadDriver d;
        d.OnState(melee(0, kIntentMelee));
        PadCommand c = d.Command();
        CHECK(c.guns && c.aim == 0 && c.fire == 1); // fists
        PadDriver bat;
        bat.OnState(melee(5, kIntentMelee));
        c = bat.Command();
        CHECK(c.guns && c.aim == 0 && c.fire == 1); // bat
        d.OnState(melee(5, kIntentMelee));
        d.OnState(melee(5, 0));
        c = d.Command();
        CHECK(c.guns && c.aim == 0 && c.fire == 0); // let go: the button is released (a weapon is held)
        d.OnState(melee(0, 0));
        c = d.Command();
        CHECK(!c.guns && c.aim == 0 && c.fire == 0); // nothing in hand and no blow: the pad's guns part is untouched
        StatePacket firearm = State(0, 0, 0, kFlagOnGround);
        firearm.weapon = 22;
        firearm.intents = kIntentFire; // an unusable aim vector: a firearm is still never fired blind
        d.OnState(firearm);
        c = d.Command();
        CHECK(c.guns && c.fire == 0);
    }

    { // the jump intent starts CJ's jump the moment the key goes down, once; the later take-off of the player is not a second jump
        auto st = [](uint32_t ts, uint32_t intents, float vy, uint32_t flags) {
            StatePacket s = State(0, vy, 0, flags);
            s.tsMs = ts;
            s.intents = intents;
            return s;
        };
        PadDriver d;
        d.OnState(st(0, 0, 0, kFlagOnGround));
        CHECK(d.Command().square == 0);
        d.OnState(st(10, kIntentJump, 0, kFlagOnGround));          // the key goes down, the player is still on the ground
        CHECK(d.Command().square == 1);                              // CJ jumps now
        CHECK(d.Command().square == 0);                              // a pulse, not a held button
        d.OnState(st(20, kIntentJump, 0, kFlagOnGround));            // the key stays down: no new press
        CHECK(d.Command().square == 0);
        d.OnState(st(210, kIntentJump, 8.0f, 0));                    // 0.2 s later the player takes off: not a second jump
        CHECK(d.Command().square == 0);
        d.OnState(st(300, 0, 0, kFlagOnGround));                     // lands, key up
        CHECK(d.Command().square == 0);
        d.OnState(st(2000, kIntentJump, 0, kFlagOnGround));          // a new press jumps again
        CHECK(d.Command().square == 1);
    }

    { // the guard counts from the announced jump, wherever on the clock that is (game times are far from zero)
        auto st = [](uint32_t ts, uint32_t intents, float vy, uint32_t flags) {
            StatePacket s = State(0, vy, 0, flags);
            s.tsMs = ts;
            s.intents = intents;
            return s;
        };
        PadDriver d;
        d.OnState(st(5000000, 0, 0, kFlagOnGround));
        d.OnState(st(5000010, kIntentJump, 0, kFlagOnGround));
        CHECK(d.Command().square == 1);
        d.OnState(st(5000210, kIntentJump, 8.0f, 0)); // the take-off, 0.2 s later: the same jump
        CHECK(d.Command().square == 0);
    }

    { // a jump that was never announced (a jump key held down for hops, a script) still follows the take-off, as before
        auto st = [](uint32_t ts, float vy, uint32_t flags) {
            StatePacket s = State(0, vy, 0, flags);
            s.tsMs = ts;
            return s;
        };
        PadDriver d;
        d.OnState(st(0, 0, kFlagOnGround));
        d.OnState(st(50, 8.0f, 0));
        CHECK(d.Command().square == 1);
    }

    { // the guard after an announced jump is short: a hop that comes much later is a jump again
        auto st = [](uint32_t ts, uint32_t intents, float vy, uint32_t flags) {
            StatePacket s = State(0, vy, 0, flags);
            s.tsMs = ts;
            s.intents = intents;
            return s;
        };
        PadDriver d;
        d.OnState(st(0, kIntentJump, 0, kFlagOnGround));
        d.Command();
        d.OnState(st(100, 0, 8.0f, 0));       // inside the guard
        CHECK(d.Command().square == 0);
        d.OnState(st(200, 0, 0, kFlagOnGround));
        d.OnState(st(900, 0, 8.0f, 0));       // 0.9 s after the announced jump: a new hop
        CHECK(d.Command().square == 1);
    }

    { // melee held: the attack button is tapped (pressed, released, pressed...) because GTA throws one blow per press and chains the next
      // one on the next press; a held button gives a single punch
        StatePacket s = State(0, 0, 0, kFlagOnGround);
        s.intents = kIntentMelee;
        PadDriver d;
        d.OnState(s);
        int fires[6];
        for (int i = 0; i < 6; i++) {
            fires[i] = d.Command().fire;
        }
        CHECK(fires[0] == 1 && fires[1] == 0 && fires[2] == 1 && fires[3] == 0 && fires[4] == 1 && fires[5] == 0);
        // letting go and striking again starts with a press
        s.intents = 0;
        d.OnState(s);
        CHECK(d.Command().fire == 0);
        s.intents = kIntentMelee;
        d.OnState(s);
        CHECK(d.Command().fire == 1);
        // a firearm's fire button is held, never tapped
        StatePacket gun = State(0, 0, 0, kFlagOnGround);
        gun.weapon = 22;
        gun.intents = kIntentFire;
        gun.aim[1] = 1.6f;
        gun.aim[2] = -20.0f;
        PadDriver g;
        g.OnState(gun);
        for (int i = 0; i < 4; i++) {
            CHECK(g.Command().fire == 1);
        }
    }

    { // guns: with a weapon, aiming presses right shoulder and firing presses circle; without one the pad is untouched
        auto gun = [](uint16_t weapon, uint32_t intents) {
            StatePacket s = State(0, 0, 0, kFlagOnGround);
            s.weapon = weapon;
            s.intents = intents;
            s.aim[1] = 1.6f; // a usable aim vector: 20 blocks ahead, a little above the feet
            s.aim[2] = -20.0f;
            return s;
        };
        PadDriver d;
        d.OnState(gun(22, kIntentAim));
        PadCommand c = d.Command();
        CHECK(c.guns && c.aim == 1 && c.fire == 0);
        d.OnState(gun(22, kIntentAim | kIntentFire));
        c = d.Command();
        CHECK(c.aim == 1 && c.fire == 1);
        d.OnState(gun(22, 0));
        c = d.Command();
        CHECK(c.guns && c.aim == 0 && c.fire == 0);
        d.OnState(gun(0, kIntentAim | kIntentFire)); // intents without a weapon do nothing
        c = d.Command();
        CHECK(!c.guns && c.aim == 0 && c.fire == 0);

        // Review Focus #3: without a usable aim vector the game must not be told to aim or fire at all (it would aim at its own
        // stale camera and fire real rounds)
        for (int bad = 0; bad < 4; bad++) {
            StatePacket s = gun(22, kIntentAim | kIntentFire);
            if (bad == 0) { s.aim[0] = s.aim[1] = s.aim[2] = 0.0f; }
            if (bad == 1) { s.aim[0] = NAN; }
            if (bad == 2) { s.aim[1] = INFINITY; }
            if (bad == 3) { s.aim[1] = 5000.0f; }
            PadDriver pd;
            pd.OnState(s);
            PadCommand bc = pd.Command();
            CHECK(bc.guns && bc.aim == 0 && bc.fire == 0);
        }

        uint8_t pad[gta::kPadSize]{};
        PadCommand press;
        press.active = true;
        press.guns = true;
        press.aim = 1;
        press.fire = 1;
        ApplyPad(pad, press);
        int16_t v;
        std::memcpy(&v, pad + gta::kPadRightShoulder1, 2);
        CHECK(v == 1);
        std::memcpy(&v, pad + gta::kPadButtonCircle, 2);
        CHECK(v == 1);
        press.guns = false; // not driving the gun buttons: those fields are left alone
        press.aim = press.fire = 0;
        pad[gta::kPadRightShoulder1] = 7;
        ApplyPad(pad, press);
        std::memcpy(&v, pad + gta::kPadRightShoulder1, 2);
        CHECK(v == 7);
    }

    { // crouching: the wish of the C key becomes presses of GTA's duck button until GTA's own state matches, then no more
        PadDriver d;
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, false));
        CHECK(d.Command(false).duck == 0);
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, true));
        CHECK(d.Command(false).duck == 1);  // the press
        CHECK(d.Command(false).duck == 0);  // let go: the next press needs a rising edge
        CHECK(d.Command(false).duck == 1);  // GTA did not duck (it refuses while the ped runs): again
        CHECK(d.Command(true).duck == 0);   // it took
        for (int i = 0; i < 10; i++) {
            CHECK(d.Command(true).duck == 0);
        }
    }

    { // already as wished: no press (GTA ducked on its own, or the wish is for what it already is)
        PadDriver d;
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, false));
        d.Command(false);
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, true));
        CHECK(d.Command(true).duck == 0);
    }

    { // standing up again: the falling edge presses it once GTA is crouched
        PadDriver d;
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, true));
        d.Command(true);
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, false));
        CHECK(d.Command(true).duck == 1);
        CHECK(d.Command(false).duck == 0);
    }

    { // GTA stands him up by itself (a heavy weapon fired): the wish did not change, so nothing presses it back down
        PadDriver d;
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, true));
        d.Command(false);
        d.Command(true); // took
        for (int i = 0; i < 20; i++) {
            d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, true));
            CHECK(d.Command(false).duck == 0);
        }
    }

    { // the presses are given up after a while (the game keeps refusing)
        PadDriver d;
        d.OnState(StateCrouch(0, 0, 0, kFlagOnGround, true));
        for (int i = 0; i < 80; i++) {
            d.Command(false);
        }
        for (int i = 0; i < 10; i++) {
            CHECK(d.Command(false).duck == 0);
        }
    }

    { // crouching while he walks: the game refuses while the ped moves (measured: 15 presses with the stick forward and the walk flag, none took),
      // so the stick is let go until it has crouched, and goes back to the walk once it has
        PadDriver d;
        d.OnState(StateCrouch(1.0f, 0, 0, kFlagOnGround, false));
        CHECK(d.Command(false).stickY == -128); // walking, not crouching: the stick as ever
        d.OnState(StateCrouch(1.0f, 0, 0, kFlagOnGround, true));
        PadCommand c = d.Command(false);
        CHECK(c.duck == 1 && c.stickY == 0 && c.walk == 0); // trying: standing still
        for (int i = 0; i < 20; i++) {
            c = d.Command(false);
            CHECK(c.stickY == 0);
        }
        c = d.Command(true); // it crouched
        CHECK(c.duck == 0 && c.stickY == -128 && c.walk == 1); // and walks again, crouched
        // the tries run out without it taking: he walks on instead of standing still for ever
        PadDriver e;
        e.OnState(StateCrouch(1.0f, 0, 0, kFlagOnGround, true));
        for (int i = 0; i < 80; i++) {
            e.Command(false);
        }
        CHECK(e.Command(false).stickY == -128);
    }

    { // standing up while walking: the stick is not let go (only crouching needs it)
        PadDriver d;
        d.OnState(StateCrouch(1.0f, 0, 0, kFlagOnGround, true));
        d.Command(true);
        d.OnState(StateCrouch(1.0f, 0, 0, kFlagOnGround, false));
        PadCommand c = d.Command(true);
        CHECK(c.duck == 1 && c.stickY == -128);
    }

    { // walking crouched: the walk pace (the game does not crouch a running ped) and never the sprint
        PadDriver d;
        d.OnState(StateCrouch(3.0f, 0, 4.0f, kFlagOnGround | kFlagSprint, true));
        PadCommand c = d.Command(true);
        CHECK(c.stickY == -128 && c.walk == 1 && c.cross == 0);
        c = d.Command(true);
        CHECK(c.cross == 0);
    }

    std::puts("test_pad_driver OK");
    return 0;
}
