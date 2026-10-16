#pragma once
#include <cmath>
#include <cstdint>
#include <cstring>

#include "ped_layout.h"
#include "protocol.h"
#include "weapon_driver.h"

namespace cj {

// What to write into CPad::Pads[0].NewState so GTA animates CJ like the Minecraft player moves.
struct PadCommand {
    bool active = false;
    int16_t stickX = 0;
    int16_t stickY = 0; // -128 = forward
    int16_t walk = 0;   // m_bPedWalk
    int16_t cross = 0;  // sprint
    int16_t square = 0; // jump
    int16_t duck = 0;   // ShockButtonL: the duck button (the game reads its rising edge: crouch or stand)
    bool guns = false;  // drive the aim and fire buttons too
    int16_t aim = 0;    // RightShoulder1: the "target" button
    int16_t fire = 0;   // Circle
};

class PadDriver {
public:
    static constexpr float kMoveThreshold = 0.3f; // blocks/s of horizontal speed
    static constexpr float kJumpMinVy = 2.0f;     // blocks/s of upward speed that counts as a jump
    static constexpr int kDuckTries = 60;         // frames the duck button is pressed (every other one) while the game has not taken it
    static constexpr uint32_t kJumpGuardMs = 400; // after an announced jump the player's own take-off (a moment later) is not a second jump

    void OnState(const StatePacket& s) {
        const float vx = s.vel[0];
        const float vy = s.vel[1];
        const float vz = s.vel[2];
        // Flying with the jetpack or swimming, CJ keeps the pose and is moved by Minecraft: the game must not walk, run or jump him
        // (the walk would start under the pose and cut it, and a jump would really lift the puppet in GTA).
        const bool floating = (s.flags & (kFlagJetpack | kFlagInWater)) != 0; // flying or swimming: the pose is not walked on
        speed_ = floating ? 0.0f : std::sqrt(vx * vx + vz * vz);
        sneak_ = (s.flags & kFlagSneak) != 0;
        sprint_ = (s.flags & kFlagSprint) != 0; // (it only counts with a speed, and a pose in the air or the water has none)
        // Aim and fire are only pressed with an aim vector to aim at: the game's own task aims at its camera otherwise, and fires.
        armed_ = s.weapon != 0;
        const bool usable = AimUsable(s.aim);
        aiming_ = usable && (s.intents & kIntentAim) != 0;
        firing_ = usable && (s.intents & kIntentFire) != 0;
        melee_ = (s.intents & kIntentMelee) != 0; // fists or bat: the attack button alone, no aim vector needed
        // The C key is a toggle in GTA too: a change of the wish is one press of the duck button (retried while the game refuses, as it does to a
        // ped that runs). Only a change starts the presses, so a ped the game stands up by itself (a heavy weapon fired) is not pushed down again.
        const bool crouch = (s.intents & kIntentCrouch) != 0;
        if (crouch != crouch_) {
            duckTries_ = kDuckTries;
            duckDown_ = true;
        }
        crouch_ = crouch;
        const bool onGround = (s.flags & kFlagOnGround) != 0;
        // The jump key going down starts CJ's jump at once (the Minecraft jump comes a moment later, see JumpDelay); the take-off that follows
        // inside the guard is the same jump. A jump nobody announced (hops with the key held, scripts) still follows the take-off.
        const bool jumpIntent = (s.intents & kIntentJump) != 0;
        if (jumpIntent && !prevJumpIntent_) {
            jumpPending_ = true;
            guardStartMs_ = s.tsMs;
            haveGuard_ = true;
        }
        prevJumpIntent_ = jumpIntent;
        const bool guarded = haveGuard_ && s.tsMs - guardStartMs_ < kJumpGuardMs; // an unsigned difference: a clock that went back is not guarded
        if (haveState_ && prevOnGround_ && !onGround && vy > kJumpMinVy && !guarded && !floating) {
            jumpPending_ = true;
        }
        prevOnGround_ = onGround;
        haveState_ = true;
    }

    // One call per game frame; the jump pulse and the sprint mashing advance with each call.
    PadCommand Command(bool gtaCrouched = false) {
        PadCommand c;
        if (!haveState_) {
            return c;
        }
        c.active = true;
        // The game refuses to crouch a ped that moves (measured live: fifteen presses with the stick forward and the walk flag, none took, while a
        // ped standing still crouched on the first): while it is being crouched the stick is let go, and the walk goes on once it has crouched.
        const bool crouching = duckTries_ > 0 && crouch_ && !gtaCrouched;
        if (speed_ >= kMoveThreshold && !crouching) {
            c.stickY = -128;
            c.walk = (sneak_ || crouch_) ? 1 : 0;
            if (sprint_ && !sneak_ && !crouch_) {
                sprintOn_ = !sprintOn_;
                c.cross = sprintOn_ ? 1 : 0;
            }
        }
        if (duckTries_ > 0) {
            if (gtaCrouched == crouch_) {
                duckTries_ = 0; // the game is as wished
            } else {
                c.duck = duckDown_ ? 1 : 0;
                duckDown_ = !duckDown_;
                duckTries_--;
            }
        }
        if (jumpPending_) {
            c.square = 1;
            jumpPending_ = false;
        }
        if (armed_ || melee_) {
            c.guns = true;
            c.aim = aiming_ ? 1 : 0;
            c.fire = firing_ ? 1 : 0;
            if (melee_) {
                // A melee blow is the attack button with no aim button; GTA throws one blow per press (a held button gives a single
                // punch), so while the player holds the mouse button the button is tapped: pressed, released, pressed...
                c.fire = meleeUp_ ? 0 : 1;
                meleeUp_ = !meleeUp_;
            } else {
                meleeUp_ = false; // the next blow starts with a press
            }
        }
        return c;
    }

    // The mouse button of a blow is down (fists or bat): the core only lets it reach GTA when CJ really holds a melee weapon.
    bool Meleeing() const { return melee_; }

    void Reset() { *this = PadDriver{}; }

private:
    bool haveState_ = false;
    float speed_ = 0.0f;
    bool sneak_ = false;
    bool crouch_ = false;
    int duckTries_ = 0;
    bool duckDown_ = false;
    bool sprint_ = false;
    bool prevOnGround_ = false;
    bool jumpPending_ = false;
    bool prevJumpIntent_ = false;
    bool haveGuard_ = false;
    uint32_t guardStartMs_ = 0;
    bool sprintOn_ = false;
    bool armed_ = false;
    bool aiming_ = false;
    bool firing_ = false;
    bool melee_ = false;
    bool meleeUp_ = false;
};

// Writes the command into a CPad (raw memory). Leaves every field it does not own alone.
inline void ApplyPad(uint8_t* pad, const PadCommand& c) {
    if (!c.active) {
        return;
    }
    auto put = [pad](size_t offset, int16_t value) { std::memcpy(pad + offset, &value, sizeof(value)); };
    put(gta::kPadLeftStickX, c.stickX);
    put(gta::kPadLeftStickY, c.stickY);
    put(gta::kPadButtonSquare, c.square);
    put(gta::kPadButtonCross, c.cross);
    put(gta::kPadShockButtonL, c.duck);
    put(gta::kPadPedWalk, c.walk);
    if (c.guns) {
        put(gta::kPadRightShoulder1, c.aim);
        put(gta::kPadButtonCircle, c.fire);
    }
}

} // namespace cj
