#include <cmath>
#include <cstdio>
#include <cstdlib>

#include "puppet.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

using namespace cj;

static StatePacket MakeState(uint32_t seq, float x, float y, float z) {
    StatePacket s{};
    s.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::State)};
    s.seq = seq;
    s.pos[0] = x;
    s.pos[1] = y;
    s.pos[2] = z;
    return s;
}

static bool Near(Vec3 a, Vec3 b) {
    return std::fabs(a.x - b.x) < 1e-3f && std::fabs(a.y - b.y) < 1e-3f && std::fabs(a.z - b.z) < 1e-3f;
}

int main() {
    const Vec3 ped{100.0f, 200.0f, 30.0f};
    Vec3 t{};

    { // first packet anchors CJ where he stands; later packets move him by the mapped delta
        Puppet p;
        CHECK(p.Update(MakeState(1, 10, 64, -5), 0, ped, t));
        CHECK(Near(t, ped));
        // dx=+1 east, dy=+1 up, dz=-2 (north) -> GTA (+1, +2, +1)
        CHECK(p.Update(MakeState(2, 11, 65, -7), 16, ped, t));
        CHECK(Near(t, {101.0f, 202.0f, 31.0f}));
    }

    { // Review Focus #2: NaN / Inf / absurd values are rejected and must not anchor
        Puppet p;
        CHECK(!p.Update(MakeState(1, std::nanf(""), 64, 0), 0, ped, t));
        StatePacket inf = MakeState(1, 0, 64, 0);
        inf.vel[1] = INFINITY;
        CHECK(!p.Update(inf, 0, ped, t));
        StatePacket nanYaw = MakeState(1, 0, 64, 0);
        nanYaw.yaw = std::nanf("");
        CHECK(!p.Update(nanYaw, 0, ped, t));
        CHECK(!p.Update(MakeState(1, 4.0e7f, 64, 0), 0, ped, t));
        CHECK(p.Update(MakeState(1, 10, 64, 10), 0, ped, t)); // a valid one still anchors at the ped
        CHECK(Near(t, ped));
    }

    { // Review Focus #3: duplicates and older packets are rejected
        Puppet p;
        CHECK(p.Update(MakeState(5, 0, 64, 0), 0, ped, t));
        CHECK(!p.Update(MakeState(5, 1, 64, 0), 1, ped, t));
        CHECK(!p.Update(MakeState(4, 1, 64, 0), 2, ped, t));
        CHECK(p.Update(MakeState(6, 1, 64, 0), 3, ped, t));
    }

    { // Review Focus #3: sequence wraparound
        Puppet p;
        CHECK(p.Update(MakeState(0xFFFFFFFFu, 0, 64, 0), 0, ped, t));
        CHECK(p.Update(MakeState(0u, 1, 64, 0), 1, ped, t));
    }

    { // Review Focus #4: Minecraft restarts (seq back to 0) after >1 s of silence -> re-anchor
        Puppet p;
        CHECK(p.Update(MakeState(100, 0, 64, 0), 0, ped, t));
        CHECK(!p.Expired(1000));
        CHECK(p.Expired(1001));
        const Vec3 newPed{500.0f, 600.0f, 40.0f};
        CHECK(p.Update(MakeState(0, 50, 64, 50), 2000, newPed, t));
        CHECK(Near(t, newPed));
    }

    { // Review Focus #5: far-away / teleported players are clamped inside the GTA map
        Puppet p;
        CHECK(p.Update(MakeState(1, 0, 64, 0), 0, ped, t));
        CHECK(p.Update(MakeState(2, 1.0e6f, 64, 0), 1, ped, t));
        CHECK(t.x == Puppet::kGtaLimitXY && t.y == 200.0f);
        CHECK(p.Update(MakeState(3, 0, 64, 1.0e6f), 2, ped, t)); // south -> GTA -Y
        CHECK(t.y == -Puppet::kGtaLimitXY);
        CHECK(p.Update(MakeState(4, 0, 1.0e6f, 0), 3, ped, t));
        CHECK(t.z == Puppet::kGtaMaxZ);
        CHECK(p.Update(MakeState(5, 0, -1.0e6f, 0), 4, ped, t));
        CHECK(t.z == Puppet::kGtaMinZ);
    }

    { // Reset forgets everything
        Puppet p;
        CHECK(p.Update(MakeState(9, 0, 64, 0), 0, ped, t));
        p.Reset();
        CHECK(!p.Expired(100000));
        CHECK(p.Update(MakeState(1, 0, 64, 0), 5, ped, t));
    }

    { // A rejected packet must not refresh the expiry timer or move the anchor
        Puppet p;
        CHECK(p.Update(MakeState(1, 0, 64, 0), 0, ped, t));
        CHECK(!p.Update(MakeState(2, std::nanf(""), 64, 0), 900, ped, t));
        CHECK(!p.Update(MakeState(1, 3, 64, 0), 950, ped, t)); // duplicate seq
        CHECK(p.Expired(1001));
    }

    { // Minecraft restarts within 1 s: seq goes back, CJ is ignored until the 1 s expiry, then he recovers
        Puppet p;
        CHECK(p.Update(MakeState(100, 0, 64, 0), 0, ped, t));
        CHECK(!p.Update(MakeState(0, 0, 64, 0), 500, ped, t));
        CHECK(p.Update(MakeState(0, 0, 64, 0), 1501, ped, t));
        CHECK(Near(t, ped));
    }

    std::puts("test_puppet OK");
    return 0;
}
