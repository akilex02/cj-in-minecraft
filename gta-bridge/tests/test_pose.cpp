#include <cmath>
#include <cstdio>
#include <cstdlib>

#include "pose.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

using namespace cj;

static RwMat MakeMat(const float r[3], const float u[3], const float a[3], const float p[3]) {
    RwMat m{};
    for (int i = 0; i < 3; i++) {
        m.right[i] = r[i];
        m.up[i] = u[i];
        m.at[i] = a[i];
        m.pos[i] = p[i];
    }
    return m;
}

static bool Near(float a, float b, float eps = 1e-4f) { return std::fabs(a - b) < eps; }

// A quaternion and its negation are the same rotation.
static bool QuatIs(Quat q, float x, float y, float z, float w) {
    const bool same = Near(q.x, x) && Near(q.y, y) && Near(q.z, z) && Near(q.w, w);
    const bool neg = Near(q.x, -x) && Near(q.y, -y) && Near(q.z, -z) && Near(q.w, -w);
    return same || neg;
}

static bool RecIs(const BoneRecord& b, int32_t id, float px, float py, float pz) {
    return b.nodeId == id && Near(b.pos[0], px) && Near(b.pos[1], py) && Near(b.pos[2], pz);
}

int main() {
    const float X[3] = {1, 0, 0}, Y[3] = {0, 1, 0}, Z[3] = {0, 0, 1};
    const float NX[3] = {-1, 0, 0}, NY[3] = {0, -1, 0}, NZ[3] = {0, 0, -1};
    const float O[3] = {0, 0, 0};

    { // QuatFromBasis: identity and the four branches of the conversion
        CHECK(QuatIs(QuatFromBasis(X, Y, Z), 0, 0, 0, 1));
        // +90 degrees about Z: right=(0,1,0) up=(-1,0,0) at=(0,0,1)
        CHECK(QuatIs(QuatFromBasis(Y, NX, Z), 0, 0, 0.70710678f, 0.70710678f));
        // +90 degrees about X: right=(1,0,0) up=(0,0,1) at=(0,-1,0)
        CHECK(QuatIs(QuatFromBasis(X, Z, NY), 0.70710678f, 0, 0, 0.70710678f));
        // +90 degrees about Y: right=(0,0,-1) up=(0,1,0) at=(1,0,0)
        CHECK(QuatIs(QuatFromBasis(NZ, Y, X), 0, 0.70710678f, 0, 0.70710678f));
        // 180 degrees about Z (negative trace): right=(-1,0,0) up=(0,-1,0) at=(0,0,1)
        CHECK(QuatIs(QuatFromBasis(NX, NY, Z), 0, 0, 1, 0));
    }

    { // Review Focus #2: scaled and degenerate matrices still give a unit quaternion, never NaN
        const float sx[3] = {1.05f, 0, 0}, sy[3] = {0, 1.05f, 0}, sz[3] = {0, 0, 1.05f};
        Quat q = QuatFromBasis(sx, sy, sz);
        CHECK(Near(std::sqrt(q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w), 1.0f, 1e-3f));
        CHECK(QuatIs(q, 0, 0, 0, 1));
        Quat d = QuatFromBasis(O, O, O); // all zero
        CHECK(std::isfinite(d.x) && std::isfinite(d.y) && std::isfinite(d.z) && std::isfinite(d.w));
        CHECK(QuatIs(d, 0, 0, 0, 1));
    }

    { // BuildPose: identity ped, bone 0.7 above the ped
        const float pedPos[3] = {100, 200, 30};
        RwMat ped = MakeMat(X, Y, Z, pedPos);
        const float bonePos[3] = {100, 200, 30.7f};
        RwMat bones[2] = {MakeMat(X, Y, Z, pedPos), MakeMat(X, Y, Z, bonePos)};
        Skeleton sk;
        sk.matrices = bones;
        sk.count = 2;
        sk.ids[0] = 1;
        sk.ids[1] = 5;
        PosePacket out;
        CHECK(BuildPose(out, 9, 1234, ped, sk));
        CHECK(out.head.boneCount == 2 && out.head.ackSeq == 9 && out.head.gtaTimeMs == 1234);
        CHECK(out.Size() == 24 + 64);
        CHECK(RecIs(out.bones[0], 1, 0, 0, 0));
        CHECK(RecIs(out.bones[1], 5, 0, 0, 0.7f));
        // A bone aligned with the ped (its local X along the ped's right) is, in CJ's model space (X forward, Y left, Z up),
        // turned -90 degrees about Z: the model's forward is the ped's forward and the model's left is minus the ped's right.
        CHECK(QuatIs({out.bones[1].quat[0], out.bones[1].quat[1], out.bones[1].quat[2], out.bones[1].quat[3]}, 0, 0, -0.70710678f, 0.70710678f));
    }

    { // BuildPose: a ped turned 90 degrees about Z; a bone half a metre in front of him, same orientation
        // Ped basis: right=(0,1,0) up(forward)=(-1,0,0) at=(0,0,1). Forward in the world is -X.
        const float pedPos[3] = {10, 20, 5};
        RwMat ped = MakeMat(Y, NX, Z, pedPos);
        const float bonePos[3] = {9.5f, 20, 5}; // 0.5 along the ped's forward (-X)
        RwMat bones[1] = {MakeMat(Y, NX, Z, bonePos)};
        Skeleton sk;
        sk.matrices = bones;
        sk.count = 1;
        sk.ids[0] = 24;
        PosePacket out;
        CHECK(BuildPose(out, 1, 1, ped, sk));
        CHECK(RecIs(out.bones[0], 24, 0.5f, 0, 0)); // in CJ's model space the ped's forward is +X
        CHECK(QuatIs({out.bones[0].quat[0], out.bones[0].quat[1], out.bones[0].quat[2], out.bones[0].quat[3]}, 0, 0, -0.70710678f, 0.70710678f));
    }

    { // BuildPose: a bone rotated +90 degrees about the ped's Z, in a rotated ped
        RwMat ped = MakeMat(Y, NX, Z, O);
        // ped rotated by Rz(90); the bone is ped * Rz(90) = Rz(180): right=(-1,0,0) up=(0,-1,0) at=(0,0,1)
        RwMat bones[1] = {MakeMat(NX, NY, Z, O)};
        Skeleton sk;
        sk.matrices = bones;
        sk.count = 1;
        sk.ids[0] = 2;
        PosePacket out;
        CHECK(BuildPose(out, 1, 1, ped, sk));
        // +90 degrees about Z relative to the ped, and the model space is the ped's frame turned -90: they cancel
        CHECK(QuatIs({out.bones[0].quat[0], out.bones[0].quat[1], out.bones[0].quat[2], out.bones[0].quat[3]}, 0, 0, 0, 1));
    }

    { // a bone 0.3 to the ped's right and 0.2 ahead of him: model space is X forward, Y left, Z up
        const float pedPos[3] = {0, 0, 0};
        RwMat ped = MakeMat(X, Y, Z, pedPos); // right=(1,0,0) forward=(0,1,0) up=(0,0,1)
        const float bonePos[3] = {0.3f, 0.2f, 0.5f};
        RwMat bones[1] = {MakeMat(X, Y, Z, bonePos)};
        Skeleton sk;
        sk.matrices = bones;
        sk.count = 1;
        sk.ids[0] = 24;
        PosePacket out;
        CHECK(BuildPose(out, 1, 1, ped, sk));
        CHECK(RecIs(out.bones[0], 24, 0.2f, -0.3f, 0.5f)); // forward 0.2 -> +X, right 0.3 -> Y = -0.3 (Y is left)
    }

    { // Review Focus #1: no skeleton, zero nodes, or too many nodes -> no pose
        RwMat ped = MakeMat(X, Y, Z, O);
        RwMat bones[1] = {MakeMat(X, Y, Z, O)};
        PosePacket out;
        Skeleton none;
        CHECK(!BuildPose(out, 1, 1, ped, none));
        Skeleton zero;
        zero.matrices = bones;
        zero.count = 0;
        CHECK(!BuildPose(out, 1, 1, ped, zero));
        Skeleton many;
        many.matrices = bones;
        many.count = static_cast<int>(kMaxBones) + 1;
        CHECK(!BuildPose(out, 1, 1, ped, many));
        Skeleton negative;
        negative.matrices = bones;
        negative.count = -3;
        CHECK(!BuildPose(out, 1, 1, ped, negative));
    }

    { // the heading of the ped goes with the pose: counter-clockwise from north, 16 bits for a full turn
        const float XX[3] = {1, 0, 0}, YY[3] = {0, 1, 0}, ZZ[3] = {0, 0, 1};
        const float NXX[3] = {-1, 0, 0}, NYY[3] = {0, -1, 0};
        RwMat bone = MakeMat(XX, YY, ZZ, O);
        Skeleton sk;
        sk.matrices = &bone;
        sk.count = 1;
        sk.ids[0] = 5;
        auto headingOf = [&](const float right[3], const float up[3]) {
            RwMat ped = MakeMat(right, up, ZZ, O);
            PosePacket out;
            CHECK(BuildPose(out, 1, 1, ped, sk));
            return out.head.heading;
        };
        CHECK(headingOf(XX, YY) == 0);           // facing north (+Y)
        CHECK(headingOf(YY, NXX) == 16384);      // facing west (-X): a quarter turn counter-clockwise
        CHECK(headingOf(NXX, NYY) == 32768);     // facing south
        CHECK(headingOf(NYY, XX) == 49152);      // facing east: three quarters, never negative
        CHECK(HeadingRaw(YY) == 0 && HeadingRaw(NXX) == 16384);
        const float tilt[3] = {0, 0.6f, 0.8f};   // a tilted forward vector still gives its horizontal heading
        CHECK(HeadingRaw(tilt) == 0);
        const float straightUp[3] = {0, 0, 1};
        CHECK(HeadingRaw(straightUp) == 0);      // no horizontal direction at all: a defined value, not NaN garbage
    }

    std::puts("test_pose OK");
    return 0;
}
