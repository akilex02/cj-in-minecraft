#pragma once
#include <algorithm>
#include <cmath>
#include <cstdint>

#include "protocol.h"

namespace cj {

struct Vec3 {
    float x = 0.0f;
    float y = 0.0f;
    float z = 0.0f;
};
inline Vec3 operator+(Vec3 a, Vec3 b) { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
inline Vec3 operator-(Vec3 a, Vec3 b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }

// Minecraft displacement (x east, y up, z south) -> GTA (x east, y north, z up). Mirrors cjproto.mc_delta_to_gta.
inline Vec3 McDeltaToGta(Vec3 d) { return {d.x, -d.z, d.y}; }

// Decides where CJ must be for each Minecraft state. Pure logic: no game memory, no sockets.
class Puppet {
public:
    static constexpr uint32_t kTimeoutMs = 1000;
    static constexpr float kMaxCoord = 3.0e7f; // Minecraft world border
    static constexpr float kGtaLimitXY = 2900.0f;
    static constexpr float kGtaMinZ = -100.0f;
    static constexpr float kGtaMaxZ = 1500.0f;

    // Returns true and fills outTarget when the packet is accepted.
    bool Update(const StatePacket& s, uint32_t nowMs, Vec3 pedPos, Vec3& outTarget) {
        if (Expired(nowMs)) {
            Reset();
        }
        const Vec3 pos{s.pos[0], s.pos[1], s.pos[2]};
        const Vec3 vel{s.vel[0], s.vel[1], s.vel[2]};
        const float yaw = s.yaw;
        const float pitch = s.pitch;
        if (!IsSane(pos) || !IsSane(vel) || !std::isfinite(yaw) || !std::isfinite(pitch)) {
            return false;
        }
        if (haveSeq_ && static_cast<int32_t>(s.seq - lastSeq_) <= 0) {
            return false;
        }
        if (!anchored_) {
            anchored_ = true;
            mcAnchor_ = pos;
            gtaAnchor_ = pedPos;
        }
        haveSeq_ = true;
        lastSeq_ = s.seq;
        lastAcceptMs_ = nowMs;

        const Vec3 t = gtaAnchor_ + McDeltaToGta(pos - mcAnchor_);
        outTarget = {std::clamp(t.x, -kGtaLimitXY, kGtaLimitXY),
                     std::clamp(t.y, -kGtaLimitXY, kGtaLimitXY),
                     std::clamp(t.z, kGtaMinZ, kGtaMaxZ)};
        return true;
    }

    bool Expired(uint32_t nowMs) const { return anchored_ && nowMs - lastAcceptMs_ > kTimeoutMs; }
    void Reset() { *this = Puppet{}; }

private:
    static bool IsSane(Vec3 v) {
        return std::isfinite(v.x) && std::isfinite(v.y) && std::isfinite(v.z)
            && std::fabs(v.x) <= kMaxCoord && std::fabs(v.y) <= kMaxCoord && std::fabs(v.z) <= kMaxCoord;
    }

    bool anchored_ = false;
    Vec3 mcAnchor_{};
    Vec3 gtaAnchor_{};
    bool haveSeq_ = false;
    uint32_t lastSeq_ = 0;
    uint32_t lastAcceptMs_ = 0;
};

} // namespace cj
