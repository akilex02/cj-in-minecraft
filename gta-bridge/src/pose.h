#pragma once
#include <cmath>
#include <cstddef>
#include <cstdint>

#include "protocol.h"

namespace cj {

// Same layout as RwMatrix and as the matrix of a CPlaceable: three basis vectors and the position.
struct RwMat {
    float right[3];
    uint32_t flags;
    float up[3]; // the entity's forward direction (RwMatrix "up")
    uint32_t pad1;
    float at[3]; // the entity's up direction
    uint32_t pad2;
    float pos[3];
    uint32_t pad3;
};
static_assert(sizeof(RwMat) == 64);

// A ped's bone matrices (world space) and their node ids, as read from the RpHAnimHierarchy.
struct Skeleton {
    const RwMat* matrices = nullptr;
    int32_t ids[kMaxBones]{};
    int count = 0;
};

struct Quat {
    float x, y, z, w;
};

inline float Dot3(const float a[3], const float b[3]) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

// Unit quaternion of the rotation whose columns are the basis vectors (right, up, at).
// Normalises its result, and returns the identity if the basis is degenerate.
inline Quat QuatFromBasis(const float right[3], const float up[3], const float at[3]) {
    if (Dot3(right, right) < 1e-8f || Dot3(up, up) < 1e-8f || Dot3(at, at) < 1e-8f) {
        return {0, 0, 0, 1}; // degenerate basis
    }
    const float m00 = right[0], m10 = right[1], m20 = right[2];
    const float m01 = up[0], m11 = up[1], m21 = up[2];
    const float m02 = at[0], m12 = at[1], m22 = at[2];
    Quat q{0, 0, 0, 1};
    const float trace = m00 + m11 + m22;
    if (trace > 0.0f) {
        const float s = std::sqrt(trace + 1.0f) * 2.0f;
        q = {(m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25f * s};
    } else if (m00 > m11 && m00 > m22) {
        const float s = std::sqrt(1.0f + m00 - m11 - m22) * 2.0f;
        q = {0.25f * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s};
    } else if (m11 > m22) {
        const float s = std::sqrt(1.0f + m11 - m00 - m22) * 2.0f;
        q = {(m01 + m10) / s, 0.25f * s, (m12 + m21) / s, (m02 - m20) / s};
    } else {
        const float s = std::sqrt(1.0f + m22 - m00 - m11) * 2.0f;
        q = {(m02 + m20) / s, (m12 + m21) / s, 0.25f * s, (m10 - m01) / s};
    }
    const float len = std::sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w);
    if (!(len > 1e-6f) || !std::isfinite(len)) {
        return {0, 0, 0, 1};
    }
    return {q.x / len, q.y / len, q.z / len, q.w / len};
}

// The heading of a ped as the pose carries it: the direction of its forward vector (matrix "up") on the ground, counter-clockwise
// from north (+Y), as 16 bits for a full turn. A forward vector with no horizontal part has no heading: 0.
inline uint16_t HeadingRaw(const float forward[3]) {
    if (!(forward[0] * forward[0] + forward[1] * forward[1] > 1e-8f)) {
        return 0;
    }
    constexpr float kTwoPi = 6.28318530717958647692f;
    const float heading = std::atan2(-forward[0], forward[1]); // (-pi, pi]
    // The cast to 16 bits wraps modulo 65536, which folds negative angles and a full turn into range.
    return static_cast<uint16_t>(std::lround(heading / kTwoPi * 65536.0f));
}

// Converts every bone from world space to CJ's model space and fills the packet. Model space is the one cj.glb
// and the skeleton's inverse bind matrices use: X forward, Y left, Z up. A ped's matrix gives (right, forward, up),
// so a world vector v becomes (v . forward, -(v . right), v . up).
// Returns false, leaving the bones untouched, when there is no usable skeleton.
inline bool BuildPose(PosePacket& out, uint32_t ackSeq, uint32_t gtaTimeMs, const RwMat& ped, const Skeleton& skel) {
    if (!skel.matrices || skel.count <= 0 || skel.count > static_cast<int>(kMaxBones)) {
        return false;
    }
    InitPose(out, ackSeq, gtaTimeMs);
    out.head.boneCount = static_cast<uint16_t>(skel.count);
    out.head.heading = HeadingRaw(ped.up);
    auto toModel = [&ped](const float v[3], float result[3]) {
        result[0] = Dot3(v, ped.up);     // forward
        result[1] = -Dot3(v, ped.right); // left
        result[2] = Dot3(v, ped.at);     // up
    };
    for (int i = 0; i < skel.count; i++) {
        const RwMat& b = skel.matrices[i];
        const float d[3] = {b.pos[0] - ped.pos[0], b.pos[1] - ped.pos[1], b.pos[2] - ped.pos[2]};
        BoneRecord& rec = out.bones[i];
        rec.nodeId = skel.ids[i];
        float pos[3], r[3], u[3], a[3];
        toModel(d, pos);
        toModel(b.right, r); // each bone axis, expressed in model space
        toModel(b.up, u);
        toModel(b.at, a);
        rec.pos[0] = pos[0];
        rec.pos[1] = pos[1];
        rec.pos[2] = pos[2];
        const Quat q = QuatFromBasis(r, u, a);
        rec.quat[0] = q.x;
        rec.quat[1] = q.y;
        rec.quat[2] = q.z;
        rec.quat[3] = q.w;
    }
    return true;
}

} // namespace cj
