#pragma once
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>

#include "ped_layout.h"
#include "protocol.h"
#include "radio.h"

// The driving mode of the bridge: Minecraft asks for a car, CJ is seated in it, the controls that arrive every frame are
// what the pad says, and the state of the car goes back every frame. Free of the game: everything it needs goes through VehicleIo.
namespace cj {

class VehicleIo {
public:
    virtual ~VehicleIo() = default;
    // A new car of the model on the ground next to the ped, facing the GTA heading (radians); nullptr when the game could not make it.
    virtual uint8_t* CreateVehicle(uint8_t* ped, int model, float heading) = 0;
    virtual bool SeatDriver(uint8_t* ped, uint8_t* vehicle) = 0;          // put CJ at its wheel at once (no door animation)
    virtual void MakeVehicleSafe(uint8_t* vehicle, uint8_t* ped) = 0;     // the car cannot be destroyed and CJ cannot be hurt in it
    // Fills what the car is doing; false when it is gone or CJ is no longer its driver. The header is the caller's.
    virtual bool ReadVehicle(uint8_t* vehicle, uint8_t* ped, VehicleStatePacket& out) = 0;
    virtual void ShiftVehicle(uint8_t* vehicle, float dx, float dy) = 0; // move the car (and CJ in it) by a displacement, keeping its speed: the car is set down somewhere else
    virtual void CorrectVehicle(uint8_t* vehicle, float dx, float dy) = 0; // move it by a displacement (GTA axes, metres) and take the speed that goes into it away
    virtual void ExitDriver(uint8_t* ped, uint8_t* vehicle, float sideMetres) = 0; // CJ out, standing that far to the right of the car (negative: left)
    virtual void DeleteVehicle(uint8_t* vehicle) = 0;
    virtual void SendVehicleState(const VehicleStatePacket& state) = 0;
    virtual void Log(const char* line) = 0;
    // The radio of the car: the station the game says is tuned (-1: unknown), and tuning it to another one.
    virtual int RadioStation() { return -1; }
    virtual void RetuneRadio(int station) { (void)station; }
    // The floor the car drives on is there (a ground probe finds it): no car is made before.
    virtual bool FloorReady() { return true; }
    // Gives CJ back what MakeVehicleSafe took (his own proof bits); harmless when there is nothing to give back.
    virtual void RestorePed(uint8_t* ped) { (void)ped; }
};

struct VehicleControls {
    int16_t throttle = 0;  // 0..255
    int16_t brake = 0;     // 0..255
    int16_t steer = 0;     // -127..127, positive to the right
    int16_t handbrake = 0; // 0 or 255
};

// Writes the controls into a CPad (raw memory): the stick turns, Cross accelerates, Square brakes (and reverses when the car is stopped), the shoulder
// button is the handbrake. Leaves every other field alone.
inline void ApplyVehiclePad(uint8_t* pad, const VehicleControls& c) {
    auto put = [pad](size_t offset, int16_t value) { std::memcpy(pad + offset, &value, sizeof(value)); };
    put(gta::kPadLeftStickX, c.steer);
    put(gta::kPadButtonCross, c.throttle);
    put(gta::kPadButtonSquare, c.brake);
    put(gta::kPadRightShoulder1, c.handbrake);
}

class VehicleDriver {
public:
    static constexpr uint32_t kTimeoutMs = 1000; // without an input or a command for longer than this the car is left
    static constexpr float kExitSide = -3.0f;    // CJ gets out to the left of the car
    static constexpr float kMaxCorrection = 20.0f;
    static constexpr int kFirstModel = 400;      // the vehicles of GTA San Andreas
    static constexpr int kLastModel = 611;

    bool Active() const { return driving_; }
    bool EnterPending() const { return pendingEnter_ && !driving_; }
    VehicleControls Controls() const { return controls_; }

    void OnCommand(const VehicleCommandPacket& c, uint32_t now) {
        if (haveCommand_ && static_cast<int32_t>(c.seq - lastCommandSeq_) <= 0) {
            return; // a repeat or a late one
        }
        haveCommand_ = true;
        lastCommandSeq_ = c.seq;
        lastLifeMs_ = driving_ ? now : lastLifeMs_;
        if (c.command == kVehicleCmdEnter) {
            pendingEnter_ = true;
            model_ = c.model;
            heading_ = GtaHeading(c.yaw);
        } else if (c.command == kVehicleCmdExit) {
            pendingExit_ = true;
        } else if (driving_ && c.command == kVehicleCmdRadioNext) {
            radioSteps_++; // at the wheel only: a press with no car does not wait for the next one
        } else if (driving_ && c.command == kVehicleCmdRadioPrevious) {
            radioSteps_--;
        }
    }

    void OnInput(const VehicleInputPacket& in, uint32_t now) {
        if (!driving_) {
            return;
        }
        if (haveInput_ && static_cast<int32_t>(in.seq - lastInputSeq_) <= 0) {
            return;
        }
        haveInput_ = true;
        lastInputSeq_ = in.seq;
        lastLifeMs_ = now;
        controls_.throttle = Clamp(in.throttle, 0, 255);
        controls_.brake = Clamp(in.brake, 0, 255);
        controls_.steer = Clamp(in.steer, -127, 127);
        controls_.handbrake = in.handbrake != 0 ? 255 : 0;
        // a broken number (NaN, infinity) fails these comparisons, so it is never applied
        if ((in.corrFlags & kVehicleCorrPresent) != 0 && std::fabs(in.corrX) <= kMaxCorrection && std::fabs(in.corrY) <= kMaxCorrection) {
            pendingCorrection_ = true;
            corrX_ = in.corrX;
            corrY_ = in.corrY;
        }
    }

    // Once per frame, after the packets have been handled. pedInVehicle: CJ is in a vehicle of the game's own making.
    void Update(VehicleIo& io, uint8_t* ped, bool pedInVehicle, uint32_t now) {
        if (!ped) {
            Forget();
            pendingEnter_ = false;
            pendingExit_ = false;
            return;
        }
        if (driving_ && ped != ped_) {
            io.Log("vehicle: the player ped changed; the car is forgotten");
            Forget();
        }
        if (!driving_) {
            const bool enter = pendingEnter_;
            pendingEnter_ = false;
            pendingExit_ = false;
            if (enter) {
                Enter(io, ped, pedInVehicle, now);
            }
            if (!driving_) {
                return;
            }
        }
        pendingEnter_ = false;
        if (pendingExit_) {
            Leave(io, ped, "asked to get out");
            return;
        }
        if (now - lastLifeMs_ > kTimeoutMs) {
            Leave(io, ped, "no input from Minecraft for a second");
            haveCommand_ = false; // Minecraft is gone: the next one counts its commands from the start
            return;
        }
        if (pendingCorrection_) {
            io.CorrectVehicle(vehicle_, corrX_, corrY_);
            pendingCorrection_ = false;
        }
        VehicleStatePacket state{};
        if (!io.ReadVehicle(vehicle_, ped, state)) {
            io.Log("vehicle: the car is gone or CJ is not at its wheel any more");
            Forget();
            return;
        }
        if (arenaRadius_ > 0.0f && (std::fabs(state.pos[0] - arenaX_) > arenaRadius_ || std::fabs(state.pos[1] - arenaY_) > arenaRadius_)) {
            io.ShiftVehicle(vehicle_, arenaX_ - state.pos[0], arenaY_ - state.pos[1]); // near the edge of the floor: back to the middle of it
            if (!io.ReadVehicle(vehicle_, ped, state)) {
                io.Log("vehicle: the car is gone or CJ is not at its wheel any more");
                Forget();
                return;
            }
        }
        // The radio: presses since the last frame move the dial from the station asked for last (the game is slow to say it) or else from the one it says.
        const int tuned = io.RadioStation();
        if (radioTarget_ >= 0 && tuned == radioTarget_) {
            radioTarget_ = -1; // the game caught up
        }
        if (radioSteps_ != 0) {
            const int from = radioTarget_ >= 0 ? radioTarget_ : tuned;
            const int to = RadioStepBy(from, radioSteps_);
            radioSteps_ = 0;
            if (to != from) {
                io.RetuneRadio(to);
                radioTarget_ = to;
            }
        }
        state.flags = StationFlags(state.flags, radioTarget_ >= 0 ? radioTarget_ : tuned);
        state.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::VehicleState)};
        state.ackSeq = haveInput_ ? lastInputSeq_ : 0;
        state.gtaTimeMs = now;
        io.SendVehicleState(state);
    }

    // The floor the car drives on is a square of half-side `radius` around (x, y); the car that leaves it is put back in the middle. 0 turns it off.
    void SetArena(float x, float y, float radius) {
        arenaX_ = x;
        arenaY_ = y;
        arenaRadius_ = radius;
    }

    // The ped is gone (a loading screen, another game): the car belongs to a world that no longer exists and is only forgotten, never touched.
    void Forget() {
        driving_ = false;
        vehicle_ = nullptr;
        ped_ = nullptr;
        controls_ = {};
        radioTarget_ = -1;
        radioSteps_ = 0;
        pendingCorrection_ = false;
        haveInput_ = false;
    }

private:
    static int16_t Clamp(int value, int low, int high) {
        return static_cast<int16_t>(value < low ? low : value > high ? high : value);
    }

    // Minecraft yaw (degrees, 0 faces south) -> GTA heading (radians in [0, 2 pi), counter-clockwise from north).
    static float GtaHeading(float yawDegrees) {
        const double twoPi = 6.283185307179586;
        double heading = std::fmod(3.141592653589793 - yawDegrees * 3.141592653589793 / 180.0, twoPi);
        if (heading < 0) {
            heading += twoPi;
        }
        return static_cast<float>(heading);
    }

    template <class... Args>
    static void LogF(VehicleIo& io, const char* fmt, Args... args) {
        char line[160];
        std::snprintf(line, sizeof(line), fmt, args...);
        io.Log(line);
    }

    void Enter(VehicleIo& io, uint8_t* ped, bool pedInVehicle, uint32_t now) {
        if (pedInVehicle) {
            io.Log("vehicle: CJ is already in a vehicle; no car is made");
            return;
        }
        if (model_ < kFirstModel || model_ > kLastModel) {
            LogF(io, "vehicle: %d is not a vehicle model", model_);
            return;
        }
        if (!io.FloorReady()) {
            io.Log("vehicle: the floor in the sky is not there yet; no car is made");
            return;
        }
        uint8_t* car = io.CreateVehicle(ped, model_, heading_);
        if (!car) {
            io.Log("vehicle: the game could not create the car");
            return;
        }
        if (!io.SeatDriver(ped, car)) {
            io.DeleteVehicle(car);
            io.Log("vehicle: could not seat CJ in the car; it was deleted");
            return;
        }
        io.MakeVehicleSafe(car, ped);
        vehicle_ = car;
        ped_ = ped;
        radioTarget_ = -1;
        radioSteps_ = 0;
        driving_ = true;
        lastLifeMs_ = now;
        LogF(io, "vehicle: CJ is driving model %d", model_);
    }

    void Leave(VehicleIo& io, uint8_t* ped, const char* why) {
        VehicleStatePacket probe{};
        if (!io.ReadVehicle(vehicle_, ped, probe)) { // the car is gone or CJ is not at its wheel (a loaded save can bring the same ped address back): nothing of it is touched
            io.Log("vehicle: the car is gone; it is forgotten");
            Forget();
            return;
        }
        io.ExitDriver(ped, vehicle_, kExitSide);
        io.DeleteVehicle(vehicle_);
        LogF(io, "vehicle: out of the car (%s); it was deleted", why);
        Forget();
    }

    bool driving_ = false;
    uint8_t* vehicle_ = nullptr;
    uint8_t* ped_ = nullptr;
    bool pendingEnter_ = false;
    bool pendingExit_ = false;
    int model_ = 0;
    float heading_ = 0.0f;
    bool haveCommand_ = false;
    uint32_t lastCommandSeq_ = 0;
    bool haveInput_ = false;
    uint32_t lastInputSeq_ = 0;
    uint32_t lastLifeMs_ = 0;
    VehicleControls controls_;
    bool pendingCorrection_ = false;
    int radioTarget_ = -1; // the station asked for and not yet said by the game, or -1
    int radioSteps_ = 0;   // presses of next (+) and previous (-) since the last frame
    float arenaX_ = 0.0f;
    float arenaY_ = 0.0f;
    float arenaRadius_ = 0.0f;
    float corrX_ = 0.0f;
    float corrY_ = 0.0f;
};

} // namespace cj
