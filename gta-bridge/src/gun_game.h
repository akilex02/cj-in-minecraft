#pragma once
// Calls into the game for guns. Compiled only into the .asi (it dereferences game addresses); everything it
// decides is in weapon_driver.h and tested natively. Every pointer is checked before it is followed.
#include <cmath>
#include <cstdint>
#include <cstdio>

#include "ped_layout.h"
#include "weapon_driver.h"

namespace cj::gungame {

using GiveFn = int(__attribute__((thiscall)) *)(void* ped, int type, unsigned ammo, bool unused);
using SelectFn = void(__attribute__((thiscall)) *)(void* ped, int type);
using TaskNewFn = void*(__cdecl*)(size_t size);
using UseGunCtorFn = void*(__attribute__((thiscall)) *)(void* task, void* targetEntity, float x, float y, float z,
                                                        int command, int burstLength, int aimImmediate);
using ControlFn = bool(__attribute__((thiscall)) *)(void* task, void* ped, void* targetEntity, int command);
using SetSecondaryFn = void(__attribute__((thiscall)) *)(void* taskManager, void* task, unsigned slot);

constexpr unsigned kInfiniteAmmo = 99999; // above 25 000 the game treats a player's ammo as infinite

inline uint8_t* Intelligence(uint8_t* ped) { return *reinterpret_cast<uint8_t**>(ped + gta::kPedIntelligence); }

// CPedIntelligence::m_pPed must point back at the ped, or the layout assumption is wrong and nothing is touched.
inline uint8_t* TaskManager(uint8_t* ped) {
    uint8_t* intelligence = Intelligence(ped);
    if (!intelligence || *reinterpret_cast<uint8_t**>(intelligence) != ped) {
        return nullptr;
    }
    return intelligence + gta::kIntelligenceTaskMgr;
}

inline uint8_t*& AttackTask(uint8_t* taskManager) {
    return *reinterpret_cast<uint8_t**>(taskManager + gta::kTaskMgrSecondary + 4 * gta::kTaskAttackSlot);
}

inline uint16_t Held(uint8_t* ped) {
    const uint8_t slot = *(ped + gta::kPedActiveSlot);
    if (slot >= gta::kNumWeaponSlots) {
        return 0;
    }
    return static_cast<uint16_t>(*reinterpret_cast<uint32_t*>(ped + gta::kPedWeapons + slot * gta::kWeaponSize + gta::kWeaponType));
}

inline uint32_t AmmoInClip(uint8_t* ped) {
    const uint8_t slot = *(ped + gta::kPedActiveSlot);
    if (slot >= gta::kNumWeaponSlots) {
        return 0;
    }
    return *reinterpret_cast<uint32_t*>(ped + gta::kPedWeapons + slot * gta::kWeaponSize + gta::kWeaponAmmoInClip);
}

using RequestModelFn = void(__cdecl*)(int modelId, int flags);
using LoadAllFn = void(__cdecl*)(bool onlyPriority);

// The weapon type and the total ammo of a weapon slot.
inline uint16_t SlotType(uint8_t* ped, int slot) {
    return static_cast<uint16_t>(*reinterpret_cast<uint32_t*>(ped + gta::kPedWeapons + slot * gta::kWeaponSize + gta::kWeaponType));
}
inline uint32_t SlotAmmo(uint8_t* ped, int slot) {
    return *reinterpret_cast<uint32_t*>(ped + gta::kPedWeapons + slot * gta::kWeaponSize + gta::kWeaponTotalAmmo);
}

// Streams the weapon's model in (the game crashes when a weapon without a loaded model is given). False: not a supported weapon.
inline bool Give(uint8_t* ped, uint16_t weapon, unsigned ammo = kInfiniteAmmo) {
    const int model = WeaponModelId(weapon);
    if (model == 0) {
        return false;
    }
    reinterpret_cast<RequestModelFn>(gta::kRequestModel)(model, gta::kStreamingGameRequired);
    reinterpret_cast<LoadAllFn>(gta::kLoadAllRequestedModels)(false);
    reinterpret_cast<GiveFn>(gta::kGiveWeapon)(ped, weapon, ammo, false);
    reinterpret_cast<SelectFn>(gta::kSetCurrentWeapon)(ped, weapon);
    return true;
}

// Removes one weapon (and only that one).
inline void Clear(uint8_t* ped, uint16_t weapon) { reinterpret_cast<SelectFn>(gta::kClearWeapon)(ped, weapon); }

// The vtable of the gun task, learned the first time we build one: it tells our task from any other attack task.
inline void*& GunVtable() {
    static void* vtable = nullptr;
    return vtable;
}

inline bool IsGunTask(uint8_t* task) { return task && GunVtable() && *reinterpret_cast<void**>(task) == GunVtable(); }

// Makes sure the ped has a CTaskSimpleUseGun in its attack slot, points it at `target` and gives it the command.
// Returns false when the layout does not look right (nothing was done).
inline bool Control(uint8_t* ped, Vec3 target, GunCommand command) {
    uint8_t* manager = TaskManager(ped);
    if (!manager) {
        return false;
    }
    uint8_t* task = AttackTask(manager);
    if (!IsGunTask(task)) {
        void* memory = reinterpret_cast<TaskNewFn>(gta::kTaskNew)(gta::kUseGunSize);
        if (!memory) {
            return false;
        }
        task = static_cast<uint8_t*>(reinterpret_cast<UseGunCtorFn>(gta::kUseGunCtor)(
            memory, nullptr, target.x, target.y, target.z, static_cast<int>(GunCommand::Aim), 1, 0));
        GunVtable() = *reinterpret_cast<void**>(task);
        reinterpret_cast<SetSecondaryFn>(gta::kSetTaskSecondary)(manager, task, gta::kTaskAttackSlot);
    }
    float* where = reinterpret_cast<float*>(task + gta::kUseGunTargetPos);
    where[0] = target.x;
    where[1] = target.y;
    where[2] = target.z;
    reinterpret_cast<ControlFn>(gta::kUseGunControl)(task, ped, nullptr, static_cast<int>(command));
    return true;
}

// One line about the gun task for the log: the task pointer, its flags and command bytes, its target, and the ped's heading.
inline int Describe(uint8_t* ped, char* out, size_t cap) {
    uint8_t* manager = TaskManager(ped);
    if (!manager) {
        return std::snprintf(out, cap, "no task manager");
    }
    uint8_t* task = AttackTask(manager);
    if (!task) {
        return std::snprintf(out, cap, "attack slot empty");
    }
    const float* target = reinterpret_cast<const float*>(task + gta::kUseGunTargetPos);
    const uint8_t* b = task;
    // matrix 'up' is the ped's forward direction
    uint8_t* matrix = *reinterpret_cast<uint8_t**>(ped + gta::kMatrixPtr);
    const float* up = reinterpret_cast<const float*>(matrix + 0x10);
    return std::snprintf(out, cap, "task=%p ours=%d finished=%u skipAim=%u next=%d last=%d armIK=%u lookIK=%u target=(%.1f %.1f %.1f) heading=(%.2f %.2f)",
                         static_cast<void*>(task), IsGunTask(task) ? 1 : 0, b[8], b[14], static_cast<int8_t>(b[15]),
                         static_cast<int8_t>(b[16]), b[0x39], b[0x3A], target[0], target[1], target[2], up[0], up[1]);
}

// The attack task if it is a CTaskSimpleUseGun (asked through the task's own GetTaskType), else null.
inline uint8_t* GameGunTask(uint8_t* ped, int* typeOut = nullptr) {
    uint8_t* manager = TaskManager(ped);
    if (!manager) {
        return nullptr;
    }
    uint8_t* task = AttackTask(manager);
    if (!task) {
        return nullptr;
    }
    void** vtable = *reinterpret_cast<void***>(task);
    const int type = reinterpret_cast<int(__attribute__((thiscall)) *)(void*)>(vtable[gta::kTaskGetTypeSlot])(task);
    if (typeOut) {
        *typeOut = type;
    }
    return type == gta::kTaskTypeUseGun ? task : nullptr;
}

// Points the game's own gun task at a world position: its target, and a reset of the IK flags so the arm is pointed again.
inline bool RetargetGameTask(uint8_t* ped, Vec3 target) {
    uint8_t* task = GameGunTask(ped);
    if (!task) {
        return false;
    }
    float* where = reinterpret_cast<float*>(task + gta::kUseGunTargetPos);
    where[0] = target.x;
    where[1] = target.y;
    where[2] = target.z;
    task[gta::kUseGunArmIkInUse] = 0;
    task[gta::kUseGunLookIkInUse] = 0;
    return true;
}

// Whether the ped's attack slot holds a gun task (ours or the game's own).
inline bool GunTaskActive(uint8_t* ped) { return GameGunTask(ped) != nullptr; }

// Asks the gun task (ours or the game's own) to end now; the task manager removes it on its next pass.
inline void Stop(uint8_t* ped) {
    uint8_t* task = GameGunTask(ped);
    if (task) {
        reinterpret_cast<ControlFn>(gta::kUseGunControl)(task, ped, nullptr, static_cast<int>(GunCommand::EndNow));
    }
}

inline float* ActiveCam() {
    const uint8_t index = *reinterpret_cast<uint8_t*>(gta::kTheCamera + gta::kCameraActiveCam);
    if (index > 2) {
        return nullptr;
    }
    return reinterpret_cast<float*>(gta::kTheCamera + gta::kCameraCams + index * gta::kCamSize);
}

// Points the active camera along `dir` (not necessarily unit): its horizontal angle, which the game recalculates the front vector from
// every frame, and the front vector itself. Returns false when there is no usable camera or direction.
inline bool AimCamera(Vec3 dir) {
    float* cam = ActiveCam();
    const float len = std::sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
    if (!cam || !(len > 1e-6f)) {
        return false;
    }
    *reinterpret_cast<float*>(reinterpret_cast<uint8_t*>(cam) + gta::kCamHorizontalAngle) = CameraHorizontalAngle(dir);
    float* front = reinterpret_cast<float*>(reinterpret_cast<uint8_t*>(cam) + gta::kCamFront);
    front[0] = dir.x / len;
    front[1] = dir.y / len;
    front[2] = dir.z / len;
    return true;
}

} // namespace cj::gungame
