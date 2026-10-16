#pragma once
// Calls into the game for the car. Compiled only into the .asi (it dereferences game addresses); the arithmetic is in vehicle_math.h and
// what to do when is in vehicle_driver.h, both tested natively. These are the calls the spike checked in the real game
// (docs/vehicles-spike.md); every pointer is checked before it is followed.
#include <cmath>
#include <cstdint>

#include "gun_game.h"
#include "ped_layout.h"
#include "vehicle_math.h"

namespace cj::vehiclegame {

using CreateCarFn = uint8_t* (__cdecl*)(int model, float x, float y, float z, bool unused);
using TaskCtorFn = void(__attribute__((thiscall)) *)(void* task, void* vehicle, void* utility);
using TaskProcessFn = bool(__attribute__((thiscall)) *)(void* task, void* ped);
using FlushFn = void(__attribute__((thiscall)) *)(void* intelligence, bool immediately);
using TeleportFn = void(__attribute__((thiscall)) *)(void* ped, float x, float y, float z, bool unused);
using RemoveFn = void(__cdecl*)(void* entity);
using DeletingDtorFn = void*(__attribute__((thiscall)) *)(void* self, int deleteMemory);

inline uint8_t*& Driver(uint8_t* vehicle) { return *reinterpret_cast<uint8_t**>(vehicle + gta::kVehicleDriver); }
inline uint8_t*& PedCar(uint8_t* ped) { return *reinterpret_cast<uint8_t**>(ped + gta::kPedVehicle); }
inline uint32_t& PhysicalFlags(uint8_t* entity) { return *reinterpret_cast<uint32_t*>(entity + gta::kPhysicalFlags); }

// CJ really is the driver of this car: the car's driver field and CJ's own reference agree (the game clears CJ's when the car is deleted).
inline bool IsDriver(uint8_t* vehicle, uint8_t* ped) {
    return vehicle && ped && Driver(vehicle) == ped && PedCar(ped) == vehicle;
}

// The flat platform of the spike (modloader/CjPlatform/cj_platform.ipl: four flat seabed tiles lifted to the sky, 425 x 425 m, surface at z 1000).
constexpr float kPlatformX = 384.45f;
constexpr float kPlatformY = -2316.0f;
constexpr float kPlatformZ = 1000.0f;
constexpr float kPlatformReach = 120.0f; // the car is put back in the middle when it is this far (the floor reaches 212): 4 s at 100 km/h before the edge

// CJ to the middle of the platform, standing on it.
inline void ToPlatform(uint8_t* ped) {
    reinterpret_cast<TeleportFn>(gta::kPedTeleport)(ped, kPlatformX, kPlatformY, kPlatformZ + 1.0f, false);
}

// A new car of the model on the ground at CJ's feet (or `z` when it is not -100), facing the heading; nullptr when the game could not make it.
inline uint8_t* Create(uint8_t* ped, int model, float heading, float z = -100.0f) {
    using RequestModelFn = void(__cdecl*)(int modelId, int flags);
    using LoadAllFn = void(__cdecl*)(bool onlyPriority);
    reinterpret_cast<RequestModelFn>(gta::kRequestModel)(model, gta::kStreamingGameRequired);
    reinterpret_cast<LoadAllFn>(gta::kLoadAllRequestedModels)(false);
    const float* at = PedPosition(ped);
    uint8_t* car = reinterpret_cast<CreateCarFn>(gta::kCreateCarForScript)(model, at[0], at[1], z, false); // -100: far below, the game puts it on the ground
    if (!car) {
        return nullptr;
    }
    if (RwMat* matrix = PedMatrix(car)) {
        FaceCar(*matrix, heading);
    }
    float* speed = reinterpret_cast<float*>(car + gta::kMoveSpeed);
    speed[0] = speed[1] = speed[2] = 0.0f;
    return car;
}

// Seats CJ at the wheel at once (the task of the game, with the warp byte set: no door animation).
inline bool Seat(uint8_t* ped, uint8_t* car) {
    using TaskNewFn = void* (__cdecl*)(size_t size);
    uint8_t* task = static_cast<uint8_t*>(reinterpret_cast<TaskNewFn>(gta::kTaskNew)(gta::kCarSetPedInAsDriverSize));
    if (!task) {
        return false;
    }
    reinterpret_cast<TaskCtorFn>(gta::kCarSetPedInAsDriverCtor)(task, car, nullptr);
    task[gta::kCarSetPedInAsDriverWarp] = 1;
    reinterpret_cast<TaskProcessFn>(gta::kCarSetPedInAsDriverProcess)(task, ped); // the 28 bytes of the task are left to the game, as the spike did
    return IsDriver(car, ped);
}

// CJ out, to the side of the car, the way the game's own script command takes a character out. false when nothing was done.
inline bool Exit(uint8_t* ped, uint8_t* car, float sideMetres) {
    uint8_t* intelligence = gungame::Intelligence(ped);
    if (!intelligence || *reinterpret_cast<uint8_t**>(intelligence) != ped) { // the verified layout: its back pointer is the ped
        return false;
    }
    RwMat* matrix = PedMatrix(car);
    if (!matrix) {
        return false;
    }
    reinterpret_cast<FlushFn>(gta::kFlushImmediately)(intelligence, true);
    reinterpret_cast<TeleportFn>(gta::kPedTeleport)(ped, matrix->pos[0] + matrix->right[0] * sideMetres, matrix->pos[1] + matrix->right[1] * sideMetres,
                                                    matrix->pos[2] + 1.0f, false);
    return true;
}

// Takes the car out of the world and frees it, only when nobody sits in it any more.
inline bool Delete(uint8_t* car) {
    if (!car || Driver(car)) {
        return false;
    }
    reinterpret_cast<RemoveFn>(gta::kWorldRemove)(car);
    (*reinterpret_cast<DeletingDtorFn**>(car))[0](car, 1);
    return true;
}

// What the car is doing, for Minecraft; false when it is gone or CJ is not at its wheel.
inline bool Read(uint8_t* car, uint8_t* ped, VehicleStatePacket& out) {
    if (!IsDriver(car, ped)) {
        return false;
    }
    RwMat* matrix = PedMatrix(car);
    if (!matrix) {
        return false;
    }
    float velocity[3];
    ToMetresPerSecond(reinterpret_cast<const float*>(car + gta::kMoveSpeed), velocity);
    const float steer = *reinterpret_cast<const float*>(car + gta::kVehicleSteerAngle);
    FillCarState(out, *matrix, velocity, steer, PedMatrix(ped));
    out.model = *reinterpret_cast<const uint16_t*>(car + gta::kEntityModelIndex);
    return true;
}

// Sets the car down by (dx, dy) with all it was doing. The game's own Teleport of the vehicle (the slot 14 of its table: it takes the car out of the world, sets
// the place, resets the suspension and puts it back) does the move, so no stale wheel contact is left to throw the car about; it also zeroes the speeds, and
// those are put back after. If the slot is not the verified one the matrix is moved by hand.
constexpr uintptr_t kAutomobileTeleport = 0x6A9CA0;
constexpr int kTeleportSlot = 14;
constexpr size_t kTurnSpeedOffset = 0x50; // CVector m_vecTurnSpeed

inline void Shift(uint8_t* car, float dx, float dy) {
    uint8_t* driver = car ? Driver(car) : nullptr;
    if (!car || !IsDriver(car, driver)) {
        return;
    }
    RwMat* matrix = PedMatrix(car);
    if (!matrix) {
        return;
    }
    void** table = *reinterpret_cast<void***>(car);
    if (table[kTeleportSlot] == reinterpret_cast<void*>(kAutomobileTeleport)) {
        float* move = reinterpret_cast<float*>(car + gta::kMoveSpeed);
        float* turn = reinterpret_cast<float*>(car + kTurnSpeedOffset);
        const float savedMove[3] = {move[0], move[1], move[2]};
        const float savedTurn[3] = {turn[0], turn[1], turn[2]};
        reinterpret_cast<TeleportFn>(table[kTeleportSlot])(car, matrix->pos[0] + dx, matrix->pos[1] + dy, matrix->pos[2], false);
        for (int i = 0; i < 3; i++) {
            move[i] = savedMove[i];
            turn[i] = savedTurn[i];
        }
    } else {
        ShiftCar(*matrix, dx, dy);
    }
    if (RwMat* ped = PedMatrix(driver)) { // CJ goes with the car in the same frame
        ShiftCar(*ped, dx, dy);
    }
}

inline void Correct(uint8_t* car, float dx, float dy) {
    if (!car || !IsDriver(car, Driver(car))) {
        return;
    }
    if (RwMat* matrix = PedMatrix(car)) {
        CorrectCar(*matrix, reinterpret_cast<float*>(car + gta::kMoveSpeed), dx, dy);
    }
}

} // namespace cj::vehiclegame
