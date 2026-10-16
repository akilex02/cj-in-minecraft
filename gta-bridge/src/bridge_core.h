#pragma once
#include <cmath>
#include <algorithm>
#include <cstdint>
#include <cstdio>

#include "outfit_dump.h"
#include "pad_driver.h"
#include "ped_layout.h"
#include "eat_driver.h"
#include "pose.h"
#include "protocol.h"
#include "puppet.h"
#include "body_reactions.h"
#include "vehicle_driver.h"
#include "weapon_driver.h"

// The per-frame logic of the bridge, free of Winsock and of the game: everything it needs from the
// outside goes through BridgeIo, so it runs under native tests against fake ped and pad memory.
namespace cj {

class BridgeIo : public VehicleIo, public EatIo {
public:
    virtual ~BridgeIo() = default;
    virtual uint8_t* Ped() = 0;      // the focused player ped, or nullptr before a game is loaded
    virtual uint8_t* Pad() = 0;      // CPad::Pads[0]
    virtual float* PedDensity() = 0; // CPopulation::PedDensityMultiplier
    virtual float* CarDensity() = 0; // CCarCtrl::CarDensityMultiplier
    virtual uint32_t NowMs() = 0;
    // Copies the next pending datagram into buf and returns its size, cap+1 if it did not fit,
    // or -1 when nothing is left.
    virtual int Recv(void* buf, size_t cap) = 0;
    virtual void SendEcho(const EchoPacket& echo) = 0;
    virtual void SendPose(const PosePacket& pose) = 0;
    // Fills the skeleton of the ped; false when it has none (not drawn yet, loading...).
    virtual bool ReadSkeleton(uint8_t* ped, Skeleton& out) = 0;
    // --- guns ---
    virtual uint16_t HeldWeapon(uint8_t* ped) = 0;                          // type of the active weapon (0 = unarmed)
    virtual uint32_t AmmoInClip(uint8_t* ped) = 0;                          // rounds in the clip of the active weapon
    virtual void GiveWeapon(uint8_t* ped, uint16_t weapon) = 0;             // add it with plenty of ammo and select it
    virtual void TakeWeapons(uint8_t* ped) = 0;                             // remove every weapon
    virtual void ControlGun(uint8_t* ped, Vec3 target, GunCommand cmd) = 0; // make sure the gun task exists, aim it, give it the command
    virtual void StopGun(uint8_t* ped) = 0;                                 // end the gun task
    virtual bool GunTaskActive(uint8_t* ped) = 0;                           // the ped's attack slot still holds a gun task
    virtual void SetPedHeading(uint8_t* ped, float heading) = 0;            // turn the ped to a GTA heading (radians, counter-clockwise from north)
    virtual void SendShot(const ShotPacket& shot) = 0;
    virtual uint32_t MeleeStrikes(uint8_t* ped) = 0; // blows GTA's fight task has made since the bridge loaded (only ever grows)
    virtual void PlayBodyAnim(uint8_t* ped, BodyAnim anim) = 0; // play GTA's animation for it on the ped (and take the one that was on away)
    virtual void KeepBodyAnim(uint8_t* ped, BodyAnim anim) = 0; // keep the held animation on top (GTA's jump anims off), put it again if it was removed
    virtual void JetpackSound(uint8_t* ped, int mode) = 0;      // GTA's own jetpack engine sound: 0 off, 1 hovering, 2 thrusting (call every frame while on; nullptr ped: only forget)
    virtual void PrepareSwim() = 0;                              // make sure GTA's swim animations are loaded (called at every engagement; it does nothing when they are)
    virtual void ClearBodyAnim(uint8_t* ped) = 0;               // fade out whatever PlayBodyAnim put (never touches another ped)
    virtual bool ReadOutfit(uint8_t* ped, Outfit& out) = 0;     // what CJ wears and his fat and muscle stats; false if the game's structures are not where they should be
    virtual void SaveOutfit(const std::string& json) = 0;       // write it where the converter reads it (cj_outfit.json)
    virtual bool DualWielding(uint8_t* ped) = 0;                // the weapon in hand is fired in pairs at the ped's skill (the game's twin pistols)
    virtual bool IsDucking(uint8_t* ped) = 0;                   // the ped is crouched (GTA's duck task is on)
    virtual void PlayEmptyClick() = 0;                          // GTA's frontend click of a weapon that cannot fire (the rocket launcher's)
    virtual void SayPain(uint8_t* ped, int context) = 0;        // CPed::Say with a pain context
    virtual void RemoveProjectiles() = 0;                                   // remove every projectile GTA has in flight
    // the car (CreateVehicle, SeatDriver, ... SendVehicleState, Log) comes from VehicleIo
};

// --- raw access to the bits of a CPed the bridge touches ---------------------------------------

inline RwMat* PedMatrix(uint8_t* ped) {
    return *reinterpret_cast<RwMat**>(ped + gta::kMatrixPtr);
}

inline float* PedPosition(uint8_t* ped) {
    auto* matrix = *reinterpret_cast<uint8_t**>(ped + gta::kMatrixPtr);
    return reinterpret_cast<float*>(matrix ? matrix + gta::kMatrixPos : ped + gta::kPlacementPos);
}

inline bool PedInVehicle(uint8_t* ped) {
    return (*reinterpret_cast<uint32_t*>(ped + gta::kPedFlags) & gta::kPedFlagInVehicle) != 0;
}

// --- the bridge ----------------------------------------------------------------------------------

class BridgeCore {
public:
    // The game moves CJ by well under a metre per frame while animating him (about 0.25 m at 12 m/s); more than this
    // means something else moved him.
    static constexpr float kMaxStageDrift = 5.0f;
    // The game crashes when a ped keeps aiming with no weapon, so a weapon is taken only after the gun task is gone; this is
    // how many frames to wait for that before taking it anyway.
    static constexpr int kMaxTakeWaitFrames = 60;

    // Right after CPad::UpdatePads inside CGame::Process: give CJ the pad that matches the Minecraft player.
    void PrePad(BridgeIo& io) {
        if (vehicle_.Active()) {
            if (uint8_t* wheelPad = io.Pad()) {
                ApplyVehiclePad(wheelPad, vehicle_.Controls()); // at the wheel the pad drives the car, and CJ does not walk
            }
            return;
        }
        if (!engaged_) {
            return;
        }
        uint8_t* ped = io.Ped();
        uint8_t* pad = io.Pad();
        if (!ped || !pad || PedInVehicle(ped)) {
            return;
        }
        PadCommand command = driver_.Command(io.IsDucking(ped));
        if (driver_.Meleeing() && !IsMeleeWeapon(io.HeldWeapon(ped))) {
            command.fire = 0; // CJ holds a gun (one of his save, or the one being taken away): tapping the attack button would fire it
        }
        if (takePending_) {
            command.aim = 0; // a weapon is waiting to be taken: the game's aiming task must be allowed to end, not be held alive
            command.fire = 0;
        }
        ApplyPad(pad, command);
    }

    // After CGame::Process.
    void Tick(BridgeIo& io) {
        uint8_t* ped = io.Ped();
        if (!ped) {
            if (engaged_) {
                Release(io, "the player ped disappeared"); // a loading screen: give the densities back
            }
            takePending_ = false; // a take that was waiting belongs to a ped that is gone
            vehicle_.Forget(); // no ped: the car, if there was one, is not ours to touch
            if (!loggedNoPed_) {
                loggedNoPed_ = true;
                io.Log("no player ped yet; waiting");
            }
            DrainAndDiscard(io); // packets from before the game was loaded are stale
            return;
        }
        loggedNoPed_ = false;
        FinishPendingTake(io, ped);

        const uint32_t now = io.NowMs();
        {   // the clothes and the build of CJ, written down when they change (the converter reads them to dress the model like the game does)
            Outfit outfit;
            std::string json;
            if (io.ReadOutfit(ped, outfit) && outfit_.Changed(now, outfit, json)) {
                io.SaveOutfit(json);
            }
        }
        float* pos = PedPosition(ped);
        // Where the game left CJ after animating him this frame.
        const Vec3 sampled{pos[0], pos[1], pos[2]};
        const bool wasDriving = vehicle_.Active();
        bool inVehicle = PedInVehicle(ped);

        if (engaged_) {
            const float dx = sampled.x - stage_.x;
            const float dy = sampled.y - stage_.y;
            if (ped != engagedPed_) {
                Release(io, "the player ped changed"); // a new game was loaded: the old stage means nothing here
            } else if (!wasDriving && dx * dx + dy * dy > kMaxStageDrift * kMaxStageDrift) { // in the car CJ goes where it goes
                Release(io, "the game moved CJ"); // hospital, mission or interior teleport: re-stage where he is now
            }
        }

        bool accepted = false;
        uint8_t buffer[256];
        for (;;) {
            const int n = io.Recv(buffer, sizeof(buffer));
            if (n < 0) {
                break;
            }
            switch (PacketTypeOf(buffer, static_cast<size_t>(n))) {
            case static_cast<int>(PacketType::VehicleCommand): {
                VehicleCommandPacket command;
                if (ParseVehicleCommand(buffer, static_cast<size_t>(n), command)) {
                    vehicle_.OnCommand(command, now);
                } else {
                    NoteMalformed(io, n);
                }
                continue;
            }
            case static_cast<int>(PacketType::VehicleInput): {
                VehicleInputPacket input;
                if (ParseVehicleInput(buffer, static_cast<size_t>(n), input)) {
                    vehicle_.OnInput(input, now);
                } else {
                    NoteMalformed(io, n);
                }
                continue;
            }
            default:
                break;
            }
            StatePacket state;
            if (!ParseState(buffer, static_cast<size_t>(n), state)) {
                NoteMalformed(io, n);
                continue;
            }
            Vec3 unused;
            if (puppet_.Update(state, now, sampled, unused)) {
                const bool dry = (state.intents & kIntentDry) != 0;
                if (dry && !wasDry_) {
                    io.PlayEmptyClick(); // once per press: the bit stays set while the button is held
                }
                wasDry_ = dry;
                driver_.OnState(state);
                weapons_.OnState(state);
                lastFlags_ = state.flags;
                lastAck_ = state.seq;
                accepted = true;
            }
        }

        if (vehicle_.EnterPending() && engaged_) {
            Release(io, "getting into the car"); // whatever CJ was doing on foot ends before he sits down
        }
        vehicle_.Update(io, ped, inVehicle, now);
        const bool driving = vehicle_.Active();
        if (!driving) {
            io.RestorePed(ped); // whatever way the ride ended, CJ gets his own proof bits back
        }
        if (wasDriving && !driving) {
            if (engaged_) {
                Release(io, "left the car"); // CJ stands somewhere else now: the next state stages him there
            }
            return;
        }
        inVehicle = PedInVehicle(ped);

        if (inVehicle && !driving) {
            if (engaged_) {
                Release(io, "CJ got into a vehicle");
            }
            return;
        }
        if (engaged_ && !accepted && puppet_.Expired(now)) {
            LogF(io, "no state for %u ms: puppet released, densities restored", Puppet::kTimeoutMs);
            Release(io, nullptr);
            return;
        }
        if (accepted && !engaged_) {
            Engage(io, ped, sampled, now);
        }
        if (!engaged_) {
            return;
        }

        if (!driving) {
            MeasureAnimationSpeed(io, sampled, now);
        }
        *io.PedDensity() = 0.0f; // scripts and cull zones may raise them again
        *io.CarDensity() = 0.0f;

        const uint16_t held = io.HeldWeapon(ped);
        haveFace_ = false;
        if (!driving) {
            RunGuns(io, ped, sampled, held, now);
            RunBody(io, ped, now);
            // Eating: the animation of the food, unless something else has his body (the water, a climb, a fall, a knockdown, the jetpack or a body animation)
            constexpr uint32_t kBusy = kFlagSwim | kFlagClimbing | kFlagFalling | kFlagKnocked | kFlagDead | kFlagJetpack | kFlagInWater | kFlagWallClimb;
            const int eatKind = EatKindOfFlags(lastFlags_);
            eat_.Update(io, ped, eatKind >= 0, eatKind, (lastFlags_ & kBusy) == 0 && !bodyPlaying_, now);
        }
        io.SendEcho(MakeEcho(lastAck_, now, sampled.x, sampled.y, sampled.z, held, held != 0 && io.DualWielding(ped) ? kEchoDualWield : 0));
        Skeleton skeleton;
        RwMat* matrix = PedMatrix(ped);
        if (matrix && io.ReadSkeleton(ped, skeleton)) {
            PosePacket pose;
            if (BuildPose(pose, lastAck_, now, *matrix, skeleton)) {
                io.SendPose(pose);
            }
        }

        if (driving) {
            return; // CJ goes with the car: nobody puts him back
        }
        // Back to the stage. Z stays with the game's gravity, so CJ keeps standing on the ground.
        pos[0] = stage_.x;
        pos[1] = stage_.y;
        if (haveFace_) {
            io.SetPedHeading(ped, faceHeading_); // after the pose: the bones above were computed with the old heading
        }
    }

private:
    struct Stage {
        float x = 0.0f;
        float y = 0.0f;
    };

    template <class... Args>
    static void LogF(BridgeIo& io, const char* fmt, Args... args) {
        char line[192];
        std::snprintf(line, sizeof(line), fmt, args...);
        io.Log(line);
    }

    void NoteMalformed(BridgeIo& io, int size) {
        if (++rejected_ % 100 == 1) {
            LogF(io, "rejected malformed datagram (%u so far, last size %d)", rejected_, size);
        }
    }

    static void DrainAndDiscard(BridgeIo& io) {
        uint8_t buffer[256];
        while (io.Recv(buffer, sizeof(buffer)) >= 0) {
        }
    }

    void Engage(BridgeIo& io, uint8_t* ped, Vec3 at, uint32_t now) {
        engaged_ = true;
        engagedPed_ = ped;
        io.PrepareSwim(); // the swim anim block streams in now, not with a hitch on the first stroke
        lastStrikes_ = io.MeleeStrikes(ped); // blows from before the session are not ours to report
        stage_ = {at.x, at.y};
        savedPedDensity_ = *io.PedDensity();
        savedCarDensity_ = *io.CarDensity();
        lastTickMs_ = now;
        driftSum_ = 0.0f;
        dtSumMs_ = 0;
        frames_ = 0;
        LogF(io, "puppet engaged: CJ stays at GTA (%.1f, %.1f), densities zeroed", at.x, at.y);
    }

    // Ends the gun task and takes the weapons away once the task is gone (at once when there is none).
    void RequestTake(BridgeIo& io, uint8_t* ped) {
        io.StopGun(ped);
        if (!io.GunTaskActive(ped)) {
            io.TakeWeapons(ped);
            takePending_ = false;
            return;
        }
        takePending_ = true;
        takePed_ = ped;
        takeWaitFrames_ = 0;
    }

    void FinishPendingTake(BridgeIo& io, uint8_t* ped) {
        if (!takePending_) {
            return;
        }
        if (ped != takePed_) {
            takePending_ = false; // another ped (a new game): nothing of ours to take from it
        } else if (!io.GunTaskActive(ped) || ++takeWaitFrames_ > kMaxTakeWaitFrames) {
            io.TakeWeapons(ped);
            takePending_ = false;
        } else {
            io.StopGun(ped);
        }
    }

    void RunGuns(BridgeIo& io, uint8_t* ped, Vec3 at, uint16_t held, uint32_t now) {
        const GunPlan plan = weapons_.Plan(at, held);
        if (plan.takeWeapons) {
            RequestTake(io, ped);
            shots_.Rebase();
        }
        if (plan.giveWeapon) {
            LogF(io, "giving CJ weapon %u (he holds %u)", static_cast<unsigned>(plan.weapon), static_cast<unsigned>(held));
            io.GiveWeapon(ped, plan.weapon);
            shots_.Rebase();
        }
        if (plan.faceHeading) {
            faceHeading_ = plan.heading;
            haveFace_ = true;
        }
        if (takePending_) {
            io.StopGun(ped); // never feed the task while its weapon is about to be taken: ask again every frame for it to end
        } else if (plan.wantTask) {
            io.ControlGun(ped, plan.aimTarget, plan.command);
        } else if (plan.stopTask) {
            io.StopGun(ped);
        }
        const uint16_t rounds = shots_.Update(held, io.AmmoInClip(ped));
        if (rounds != 0) {
            const ShotPacket shot = MakeShot(lastAck_, now, shots_.Total(), held, rounds);
            io.SendShot(shot);
            io.SendShot(shot); // twice: a lost datagram must not lose a shot
        }
        const uint32_t strikes = io.MeleeStrikes(ped);
        if (strikes > lastStrikes_ && IsMeleeWeapon(held)) { // a blow closed while a gun is in hand (a switch in mid-swing) is not reported
            const uint32_t blows = std::min<uint32_t>(strikes - lastStrikes_, 0xFFFF);
            shots_.Credit(static_cast<uint16_t>(blows));
            const ShotPacket blow = MakeShot(lastAck_, now, shots_.Total(), held, static_cast<uint16_t>(blows));
            io.SendShot(blow);
            io.SendShot(blow); // twice, like the rounds of a firearm
        }
        lastStrikes_ = strikes;
        if (FiresProjectile(held)) {
            io.RemoveProjectiles(); // every frame: GTA's rocket would fly and explode in its own world, hurting the puppet
        }
        ApplyShield(ped, FiresProjectile(held));
    }

    // Invulnerable and explosion proof while a rocket launcher is held (see ProofShield). A new ped (a loaded game) ends the session first
    // (Release), which forgets the old ped's saved bits, so they never reach the new one.
    void ApplyShield(uint8_t* ped, bool want) {
        auto* flags = reinterpret_cast<uint32_t*>(ped + gta::kPhysicalFlags);
        const uint32_t next = shield_.Update(*flags, want);
        if (next != *flags) {
            *flags = next;
        }
        shieldPed_ = ped;
    }

    // The animation and the voice of what happens to the player's body (falling, knocked down, dead, hurt); see BodyReactions.
    void RunBody(BridgeIo& io, uint8_t* ped, uint32_t now) {
        const BodyPlan plan = body_.Update(lastFlags_, now);
        switch (plan.anim) {
        case BodyAnim::None:
            break;
        case BodyAnim::Clear:
            io.ClearBodyAnim(ped);
            bodyPlaying_ = false;
            break;
        default:
            io.PlayBodyAnim(ped, plan.anim);
            bodyPlaying_ = true;
            break;
        }
        const BodyAnim holding = body_.Holding();
        if (holding != BodyAnim::None) {
            io.KeepBodyAnim(ped, holding);
        }
        // the jetpack sound follows the flags; it is updated every frame while on (the game moves the volumes a step per call)
        const int jetMode = (lastFlags_ & kFlagJetpack) == 0 ? 0 : (lastFlags_ & kFlagJetpackThrust) != 0 ? 2 : 1;
        if (jetMode != 0 || jetMode_ != 0) {
            io.JetpackSound(ped, jetMode);
        }
        jetMode_ = jetMode;
        if (plan.sayContext != 0) {
            io.SayPain(ped, plan.sayContext);
        }
    }

    void Release(BridgeIo& io, const char* reason) {
        *io.PedDensity() = savedPedDensity_;
        *io.CarDensity() = savedCarDensity_;
        uint8_t* ped = io.Ped();
        if (weapons_.Given() && ped && ped == engagedPed_) {
            RequestTake(io, ped); // CJ keeps no weapon the bridge gave him
        }
        if (bodyPlaying_) {
            // CJ keeps no animation we put on him; if the ped is gone or is another one, the held association is only forgotten (nullptr)
            io.ClearBodyAnim(ped && ped == engagedPed_ ? ped : nullptr);
        }
        bodyPlaying_ = false;
        if (jetMode_ != 0) {
            io.JetpackSound(ped && ped == engagedPed_ ? ped : nullptr, 0); // no engine keeps running for a puppet that is gone
            jetMode_ = 0;
        }
        body_.Reset();
        lastFlags_ = 0;
        if (shield_.Active() && ped && ped == shieldPed_) {
            ApplyShield(ped, false); // the ped is as it was
        }
        shield_.Reset();
        weapons_.Reset();
        shots_.Rebase();
        puppet_.Reset();
        driver_.Reset();
        engaged_ = false;
        if (reason) {
            LogF(io, "puppet released: %s", reason);
        }
    }

    // How far the game moved CJ since the last frame (before we put him back) is the speed of his animation.
    void MeasureAnimationSpeed(BridgeIo& io, Vec3 sampled, uint32_t now) {
        const float dx = sampled.x - stage_.x;
        const float dy = sampled.y - stage_.y;
        driftSum_ += std::sqrt(dx * dx + dy * dy);
        dtSumMs_ += now - lastTickMs_;
        lastTickMs_ = now;
        ++frames_;
        if (dtSumMs_ >= 2000) {
            LogF(io, "anim speed %.2f m/s over %u frames", driftSum_ / (static_cast<float>(dtSumMs_) / 1000.0f), frames_);
            driftSum_ = 0.0f;
            dtSumMs_ = 0;
            frames_ = 0;
        }
    }

    Puppet puppet_;
    PadDriver driver_;
    VehicleDriver vehicle_;
    EatDriver eat_;
public:
    // The floor the car drives on (see VehicleDriver::SetArena).
    void SetArena(float x, float y, float radius) { vehicle_.SetArena(x, y, radius); }
private:
    WeaponDriver weapons_;
    ShotCounter shots_;
    OutfitWatcher outfit_;
    bool wasDry_ = false; // the dry-fire intent of the last state, for its rising edge
    bool haveFace_ = false;
    float faceHeading_ = 0.0f;
    bool takePending_ = false;
    uint8_t* takePed_ = nullptr;
    int takeWaitFrames_ = 0;
    bool engaged_ = false;
    uint8_t* engagedPed_ = nullptr;
    uint32_t lastStrikes_ = 0;
    ProofShield shield_;
    BodyReactions body_;
    bool bodyPlaying_ = false;
    int jetMode_ = 0;
    uint32_t lastFlags_ = 0;
    uint8_t* shieldPed_ = nullptr;
    bool loggedNoPed_ = false;
    Stage stage_;
    float savedPedDensity_ = 1.0f;
    float savedCarDensity_ = 1.0f;
    uint32_t lastAck_ = 0;
    uint32_t rejected_ = 0;
    uint32_t lastTickMs_ = 0;
    uint32_t dtSumMs_ = 0;
    uint32_t frames_ = 0;
    float driftSum_ = 0.0f;
};

} // namespace cj
