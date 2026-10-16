#pragma once
#include <cmath>
#include <cstdint>
#include <vector>

#include "protocol.h"
#include "puppet.h"

namespace cj {

// Values of the game's eGunCommand. The task keeps the highest command it was given in a frame.
enum class GunCommand : int8_t { None = 0, Aim = 1, Fire = 2, EndNow = 7 };

// The model of a firearm (CWeaponInfo::m_nModelId1). The game crashes in CPed::AddWeaponModel when it is given a weapon whose
// model is not streamed in, so only weapons listed here are ever given. 0 = not supported.
inline int WeaponModelId(uint16_t weapon) {
    switch (weapon) {
    case 22: return 346; // Colt 45
    case 23: return 347; // Silenced pistol
    case 24: return 348; // Desert Eagle
    case 25: return 349; // Shotgun
    case 26: return 350; // Sawn-off
    case 27: return 351; // Combat shotgun
    case 28: return 352; // Micro SMG
    case 29: return 353; // MP5
    case 30: return 355; // AK-47
    case 31: return 356; // M4
    case 32: return 372; // Tec-9
    case 33: return 357; // Country rifle
    case 35: return 359; // Rocket launcher
    case 5: return 336;  // Baseball bat
    case 38: return 362; // Minigun
    default: return 0;
    }
}

// The horizontal orbit angle of the game's camera that makes it look along `dir`: the GTA heading of the direction (counter-clockwise from
// north) minus a quarter turn (measured in a live camera: CCam+0x7C held the heading minus pi/2). The game takes the heading it turns
// CJ to, and the target limits it applies, from this angle (it recalculates the camera's front vector from it every frame), so it
// has to follow the player's aim. (A direction with no horizontal part gives atan2(0, 0) = 0, a defined heading.)
inline float CameraHorizontalAngle(Vec3 dir) { return std::atan2(-dir.x, dir.y) - 1.57079632679f; }

// Only the fists (nothing in hand) and the baseball bat make blows. Anything else CJ holds in GTA is a gun, and a tapped attack button
// would fire it.
inline bool IsMeleeWeapon(uint16_t weapon) { return weapon == 0 || weapon == 5; }

// Rocket launchers (RPG, heat-seeking): the mod makes their rocket itself, so the one GTA fires in its own world is removed.
inline bool FiresProjectile(uint16_t weapon) { return weapon == 35 || weapon == 36; }

// Bits of CPhysical::physicalFlags (gta::kPhysicalFlags) that make a ped immune to fire / to damage / to explosions (gta-reversed).
constexpr uint32_t kPhysicalFireProof = 1u << 19;
constexpr uint32_t kPhysicalInvulnerable = 1u << 22;
constexpr uint32_t kPhysicalExplosionProof = 1u << 23;

// While a rocket launcher is held, GTA's own rocket can explode the very frame it is fired (aimed at the ground, say), before it can be
// removed, and the explosion would hurt the puppet. The shield makes the ped invulnerable, explosion proof and fire proof meanwhile (the explosion leaves flames that burn him too), and gives
// back the ped's own bits of those two flags when it goes off.
class ProofShield {
public:
    // Returns the flags the ped should have now.
    uint32_t Update(uint32_t flags, bool want) {
        constexpr uint32_t kBoth = kPhysicalInvulnerable | kPhysicalExplosionProof | kPhysicalFireProof;
        if (want) {
            if (!active_) {
                saved_ = flags & kBoth;
                active_ = true;
            }
            return flags | kBoth;
        }
        if (active_) {
            active_ = false;
            return (flags & ~kBoth) | saved_;
        }
        return flags;
    }
    bool Active() const { return active_; }
    // Forgets the saved bits without writing them anywhere (another ped, a new game).
    void Reset() { active_ = false; saved_ = 0; }

private:
    bool active_ = false;
    uint32_t saved_ = 0;
};

// The weapon slot (CWeaponInfo::m_nSlot) of the firearms the bridge handles: the game keeps one weapon per slot. -1 = not one of them.
inline int WeaponSlot(uint16_t weapon) {
    switch (weapon) {
    case 22: case 23: case 24: return 2;
    case 25: case 26: case 27: return 3;
    case 28: case 29: case 32: return 4;
    case 30: case 31: return 5;
    case 33: case 34: return 6;
    case 38: return 7; // heavy weapons: the minigun
    case 35: return 7; // heavy weapons: the rocket launcher
    case 5: return 1;  // melee: the baseball bat
    default: return -1;
    }
}

// Remembers what the weapons the bridge gives replaced, so taking them away leaves the player's own arsenal as it was: only what
// the bridge gave is removed, and the weapon it displaced from the same slot is put back with its ammo.
class Arsenal {
public:
    static constexpr int kSlots = 13;

    struct Step {
        uint16_t clear = 0;        // remove this weapon
        uint16_t restoreType = 0;  // then give this one back (0 = nothing to give back)
        uint32_t restoreAmmo = 0;
    };

    // Called just before `weapon` is given: `slotType` / `slotAmmo` are what the slot holds right now.
    void OnGive(int slot, uint16_t weapon, uint16_t slotType, uint32_t slotAmmo) {
        if (slot < 0 || slot >= kSlots) {
            return;
        }
        if (ours_[slot] == 0) {
            if (slotType == weapon) {
                return; // the player already owned exactly this weapon: it stays his
            }
            savedType_[slot] = slotType; // may be 0: an empty slot
            savedAmmo_[slot] = slotAmmo;
        }
        ours_[slot] = weapon;
    }

    // What to do to give everything back; forgets it.
    std::vector<Step> TakeAll() {
        std::vector<Step> steps;
        for (int slot = 0; slot < kSlots; slot++) {
            if (ours_[slot] != 0) {
                steps.push_back({ours_[slot], savedType_[slot], savedAmmo_[slot]});
            }
        }
        Reset();
        return steps;
    }

    void Reset() { *this = Arsenal{}; }

private:
    uint16_t ours_[kSlots]{};
    uint16_t savedType_[kSlots]{};
    uint32_t savedAmmo_[kSlots]{};
};

struct GunPlan {
    bool giveWeapon = false;  // CJ does not hold the weapon yet: give it and select it
    bool takeWeapons = false; // the item was dropped: take away what we gave
    uint16_t weapon = 0;      // the weapon to give
    bool wantTask = false;    // a gun task must exist this frame, aiming at aimTarget with `command`
    bool stopTask = false;    // end the gun task
    GunCommand command = GunCommand::None;
    Vec3 aimTarget{};         // GTA world position
    bool faceHeading = false; // turn CJ in GTA to `heading` (after this frame's pose has been read)
    float heading = 0.0f;     // GTA heading in [0, 2*pi): counter-clockwise from north
};

// Whether an aim vector (feet to aim point, in blocks) can be aimed at: finite, not zero, not absurdly long.
inline bool AimUsable(const float aim[3], float maxLength = 1000.0f) {
    if (!std::isfinite(aim[0]) || !std::isfinite(aim[1]) || !std::isfinite(aim[2])) {
        return false;
    }
    const float len2 = aim[0] * aim[0] + aim[1] * aim[1] + aim[2] * aim[2];
    return len2 > 1e-6f && len2 <= maxLength * maxLength;
}

// Decides what to do about weapons for each Minecraft state. Pure logic: no game memory, no sockets.
class WeaponDriver {
public:
    static constexpr float kMaxAim = 1000.0f; // blocks; anything longer is not a real aim vector

    void OnState(const StatePacket& s) {
        weapon_ = s.weapon;
        intents_ = s.intents;
        aim_ = {s.aim[0], s.aim[1], s.aim[2]};
        yaw_ = s.yaw;
    }

    // heldWeapon: the weapon type CJ really holds right now (0 = none).
    GunPlan Plan(Vec3 pedPos, uint16_t heldWeapon) {
        GunPlan p;
        if (weapon_ == 0) {
            if (given_) {
                p.takeWeapons = true;
                given_ = false;
            }
            if (taskActive_) {
                p.stopTask = true;
                taskActive_ = false;
            }
            if ((intents_ & kIntentMelee) != 0) {
                p.faceHeading = Heading(&p.heading); // a punch: turn CJ towards the look direction
            }
            return p;
        }
        if (heldWeapon != weapon_) {
            p.giveWeapon = true;
            p.weapon = weapon_;
            given_ = true;
            if (taskActive_) {
                p.stopTask = true; // the task belongs to the weapon he held before
                taskActive_ = false;
            }
            return p;
        }
        const bool aiming = (intents_ & kIntentAim) != 0;
        const bool firing = (intents_ & kIntentFire) != 0;
        if ((aiming || firing) && AimUsable()) {
            p.wantTask = true;
            p.command = firing ? GunCommand::Fire : GunCommand::Aim;
            p.aimTarget = pedPos + McDeltaToGta(aim_);
            taskActive_ = true;
            p.faceHeading = Heading(&p.heading); // CJ must face where the player looks: GTA only twists his torso a limited way
        } else if (taskActive_) {
            p.stopTask = true;
            taskActive_ = false;
        }
        if ((intents_ & kIntentMelee) != 0 && !p.faceHeading) {
            p.faceHeading = Heading(&p.heading); // a blow with a melee weapon in hand: same
        }
        return p;
    }

    bool Given() const { return given_; }
    void Reset() { *this = WeaponDriver{}; }

private:
    // The GTA heading (counter-clockwise from north, in [0, 2 pi)) of the direction the player looks; false when the yaw is unusable.
    bool Heading(float* out) const {
        if (!std::isfinite(yaw_)) {
            return false;
        }
        constexpr double kTwoPi = 6.283185307179586;
        double heading = std::fmod(3.141592653589793 - static_cast<double>(yaw_) * 0.017453292519943295, kTwoPi);
        if (heading < 0.0) {
            heading += kTwoPi;
        }
        *out = static_cast<float>(heading >= kTwoPi ? 0.0 : heading);
        return true;
    }

    bool AimUsable() const {
        const float aim[3] = {aim_.x, aim_.y, aim_.z};
        return cj::AimUsable(aim, kMaxAim);
    }

    uint16_t weapon_ = 0;
    uint32_t intents_ = 0;
    Vec3 aim_{};
    float yaw_ = 0.0f;
    bool given_ = false;
    bool taskActive_ = false;
};

// Counts the rounds fired by watching the clip of the active weapon go down. A refill (reload) or a weapon change is not a shot.
// Counts the blows of GTA's fight task (CTaskSimpleFight): one per blow animation, at the moment the animation reaches the hit time of
// its combo (the impact), or when the blow is closed if the next blow cut it short or the task went away. Only moves 0 to 2 are blows
// (3 is a block, 4 the idle stance). A new blow is told by a different move or animation, or by the animation time going back.
class StrikeDetector {
public:
    uint16_t Update(bool taskAlive, int8_t move, uintptr_t anim, float animTime, float hitTime) {
        const bool blow = taskAlive && move >= 0 && move <= 2 && anim != 0 && std::isfinite(animTime);
        uint16_t blows = 0;
        if (!blow) {
            return Close();
        }
        if (!active_ || move != move_ || anim != anim_ || animTime < lastTime_ - 0.05f) {
            blows += Close();
            active_ = true;
            move_ = move;
            anim_ = anim;
            counted_ = false;
        }
        lastTime_ = animTime;
        if (!counted_ && animTime >= hitTime) { // a NaN hit time never compares true: only closing the blow counts it
            counted_ = true;
            blows++;
        }
        return blows;
    }

    // Forgets the blow under way without counting it (another ped, a new session).
    void Reset() { *this = StrikeDetector{}; }

private:
    uint16_t Close() {
        const uint16_t blows = (active_ && !counted_) ? 1 : 0;
        active_ = false;
        counted_ = false;
        return blows;
    }

    bool active_ = false;
    bool counted_ = false;
    int8_t move_ = 0;
    uintptr_t anim_ = 0;
    float lastTime_ = 0.0f;
};

class ShotCounter {
public:
    uint16_t Update(uint16_t weapon, uint32_t ammoInClip) {
        uint32_t fired = 0;
        if (have_ && weapon == prevWeapon_ && ammoInClip < prevAmmo_) {
            fired = prevAmmo_ - ammoInClip;
            if (fired > 0xFFFF) {
                fired = 0xFFFF;
            }
        }
        have_ = true;
        prevWeapon_ = weapon;
        prevAmmo_ = ammoInClip;
        total_ += fired;
        return static_cast<uint16_t>(fired);
    }

    // Counts rounds that are not read off a clip (the blows of a fight task): they go on the same wire counter.
    uint16_t Credit(uint16_t rounds) {
        total_ += rounds;
        return rounds;
    }

    uint32_t Total() const { return total_; }
    // Forgets the clip that was being watched (another weapon, another session) but keeps the total, which only ever grows:
    // the receiver treats a counter that goes back as a restart of the game.
    void Rebase() { have_ = false; }
    void Reset() { *this = ShotCounter{}; }

private:
    bool have_ = false;
    uint16_t prevWeapon_ = 0;
    uint32_t prevAmmo_ = 0;
    uint32_t total_ = 0;
};

} // namespace cj
