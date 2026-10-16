#pragma once
#include <bit>
#include <cstddef>
#include <cstdint>
#include <cstring>

namespace cj {

static_assert(std::endian::native == std::endian::little, "the wire format is little-endian");

constexpr uint32_t kMagic = 0x434A4D43;
constexpr uint16_t kVersion = 2;
enum class PacketType : uint16_t { State = 1, Echo = 2, Pose = 3, Shot = 4, VehicleCommand = 5, VehicleInput = 6, VehicleState = 7 };

constexpr size_t kMaxBones = 48;

// Bit flags of StatePacket::flags (same values as FLAG_* in protocol/cjproto.py)
constexpr uint32_t kFlagOnGround = 1u << 0;
constexpr uint32_t kFlagSprint = 1u << 1;
constexpr uint32_t kFlagSneak = 1u << 2;
constexpr uint32_t kFlagSwim = 1u << 3;           // the fast swimming pose of Minecraft (the crawl)
constexpr uint32_t kFlagClimbing = 1u << 4;
constexpr uint32_t kFlagFalling = 1u << 5; // free fall
constexpr uint32_t kFlagKnocked = 1u << 6; // knocked down: lying and getting up
constexpr uint32_t kFlagDead = 1u << 7;
constexpr uint32_t kHurtShift = 8;         // bits 8 to 11: how many times the player was hurt (0 to 15, wrapping); a change is the event "he was hurt"
constexpr uint32_t kHurtMask = 0xFu << kHurtShift;
constexpr uint32_t kFlagBlasted = 1u << 12; // the knockdown was an explosion: he falls on his back
constexpr uint32_t kFlagInWater = 1u << 16;       // in the water with the buoyancy at work: floating, swimming or diving
constexpr uint32_t kFlagUnderwater = 1u << 17;    // the head is under the water
constexpr uint32_t kFlagWaterMoving = 1u << 18;   // moving horizontally in the water
constexpr uint32_t kFlagJetpack = 1u << 14;       // flying with the jetpack: wearing it, allowed, and thrusting or in the air after a thrust
constexpr uint32_t kFlagJetpackThrust = 1u << 15; // thrusting now (the jump key is held)
constexpr uint32_t kFlagEating = 1u << 19;        // the player is eating (using a food item); bits 20 and 21 say what kind
constexpr unsigned kEatKindShift = 20;
constexpr int kEatBurger = 0;   // the anim EAT_Burger of GTA's FOOD block: anything that is not meat or baked
constexpr int kEatChicken = 1;  // EAT_Chicken: meat
constexpr int kEatPizza = 2;    // EAT_Pizza: bread, cakes and cookies

// The kind of food in the flags of a state, or -1 when the player is not eating (or the kind is not one we know).
inline int EatKindOfFlags(uint32_t flags) {
    if ((flags & kFlagEating) == 0) {
        return -1;
    }
    const int kind = static_cast<int>((flags >> kEatKindShift) & 3u);
    return kind <= kEatPizza ? kind : -1;
}
constexpr uint32_t kFlagWallClimb = 1u << 13; // climbing a wall of 2 or 3 blocks (not a ladder: that is kFlagClimbing)
inline uint8_t HurtCounter(uint32_t flags) { return static_cast<uint8_t>((flags & kHurtMask) >> kHurtShift); }

// Bit flags of StatePacket::intents (same values as INTENT_* in protocol/cjproto.py)
constexpr uint32_t kIntentAim = 1u << 0;
constexpr uint32_t kIntentFire = 1u << 1;
constexpr uint32_t kIntentMelee = 1u << 2;
constexpr uint32_t kIntentCrouch = 1u << 5; // the player is crouched (the C key toggles it): GTA's own crouch, which the bridge keeps in step with a press of the duck button
constexpr uint32_t kIntentDry = 1u << 4;  // the fire button is down but the weapon has no bullets: GTA's click of a weapon that cannot fire sounds once per press
constexpr uint32_t kIntentJump = 1u << 3; // the jump key is down (and the player can jump): CJ starts his jump now, the Minecraft jump comes a moment later

constexpr uint16_t kEchoDualWield = 1u << 0; // CJ holds two guns: the weapon he holds is one the game fires in pairs (twin pistols, at the top skill)

constexpr uint16_t kVehicleCmdNone = 0;
constexpr uint16_t kVehicleCmdEnter = 1;
constexpr uint16_t kVehicleCmdExit = 2;
constexpr uint16_t kVehicleCmdRadioNext = 3;     // tune the radio of the car to the next station
constexpr uint16_t kVehicleCmdRadioPrevious = 4;
constexpr uint16_t kVehicleCorrPresent = 1u << 0; // VehicleInputPacket::corrFlags: corrX/corrY hold a correction to apply once
constexpr uint16_t kVehicleFlagDriverIn = 1u << 0; // VehicleStatePacket::flags: CJ is inside the car

#pragma pack(push, 1)
struct Header {
    uint32_t magic;
    uint16_t version;
    uint16_t type;
};
struct StatePacket {
    Header h;
    uint32_t seq;
    uint32_t tsMs;
    float pos[3];
    float vel[3];
    float yaw;
    float pitch;
    uint32_t flags;
    uint32_t intents;
    uint16_t weapon;
    uint16_t pad;
    float aim[3]; // feet -> aim point, Minecraft axes, blocks; all zero = none
};
struct EchoPacket {
    Header h;
    uint32_t ackSeq;
    uint32_t gtaTimeMs;
    float pos[3];
    uint16_t weaponHeld;
    uint16_t flags; // kEcho* bits
};
struct ShotPacket {
    Header h;
    uint32_t ackSeq;
    uint32_t gtaTimeMs;
    uint32_t shotSeq; // total rounds fired since GTA started, including this event's
    uint16_t weapon;
    uint16_t rounds;
};
struct VehicleCommandPacket {
    Header h;
    uint32_t seq;
    uint32_t tsMs;
    uint16_t command; // kVehicleCmd*
    uint16_t model;   // GTA model id (492 = Greenwood)
    float yaw;        // the player's yaw in Minecraft degrees: the car faces that way
};
struct VehicleInputPacket {
    Header h;
    uint32_t seq;
    uint32_t tsMs;
    uint16_t throttle; // 0..255
    uint16_t brake;    // 0..255
    int16_t steer;     // -127..127, positive to the right
    uint16_t handbrake; // 0 or 255
    uint16_t corrFlags;
    uint16_t pad;
    float corrX; // a displacement to add to the car's position, GTA axes, metres
    float corrY;
};
struct VehicleStatePacket {
    Header h;
    uint32_t ackSeq;
    uint32_t gtaTimeMs;
    uint16_t flags;
    uint16_t model;
    float pos[3];
    float heading; // radians, counter-clockwise from north
    float pitch;
    float roll;
    float speed; // metres per second, forwards
    float steer; // radians; negative is to the right (GTA's m_fSteerAngle)
    float seat[3]; // CJ in the axes of the car: right, forward, up
    float seatHeading; // CJ's heading relative to the car's
};
struct BoneRecord {
    int32_t nodeId;
    float pos[3];
    float quat[4]; // x, y, z, w
};
struct PoseHeader {
    Header h;
    uint32_t ackSeq;
    uint32_t gtaTimeMs;
    uint32_t flags;
    uint16_t boneCount;
    uint16_t heading; // where the ped faces: 0..65535 = 0..2*pi, counter-clockwise from north (see HeadingRaw)
};
struct PosePacket {
    PoseHeader head;
    BoneRecord bones[kMaxBones];
    size_t Size() const { return sizeof(PoseHeader) + head.boneCount * sizeof(BoneRecord); }
};
#pragma pack(pop)

static_assert(sizeof(StatePacket) == 72);
static_assert(sizeof(EchoPacket) == 32);
static_assert(sizeof(ShotPacket) == 24);
static_assert(sizeof(VehicleCommandPacket) == 24);
static_assert(sizeof(VehicleInputPacket) == 36);
static_assert(sizeof(VehicleStatePacket) == 68);
static_assert(sizeof(BoneRecord) == 32);
static_assert(sizeof(PoseHeader) == 24);

inline void InitPose(PosePacket& p, uint32_t ackSeq, uint32_t gtaTimeMs) {
    p.head = {{kMagic, kVersion, static_cast<uint16_t>(PacketType::Pose)}, ackSeq, gtaTimeMs, 0, 0, 0};
}

inline bool ParseState(const void* data, size_t len, StatePacket& out) {
    if (len != sizeof(StatePacket)) {
        return false;
    }
    std::memcpy(&out, data, sizeof(out));
    return out.h.magic == kMagic && out.h.version == kVersion
        && out.h.type == static_cast<uint16_t>(PacketType::State);
}

// The type of a datagram from its header alone, or -1 when the header is not ours.
inline int PacketTypeOf(const void* data, size_t len) {
    if (len < sizeof(Header)) {
        return -1;
    }
    Header h;
    std::memcpy(&h, data, sizeof(h));
    return h.magic == kMagic && h.version == kVersion ? h.type : -1;
}

template <class Packet>
inline bool ParseExact(const void* data, size_t len, PacketType type, Packet& out) {
    if (len != sizeof(Packet)) {
        return false;
    }
    std::memcpy(&out, data, sizeof(out));
    return out.h.magic == kMagic && out.h.version == kVersion && out.h.type == static_cast<uint16_t>(type);
}

inline bool ParseVehicleCommand(const void* data, size_t len, VehicleCommandPacket& out) {
    return ParseExact(data, len, PacketType::VehicleCommand, out);
}

inline bool ParseVehicleInput(const void* data, size_t len, VehicleInputPacket& out) {
    return ParseExact(data, len, PacketType::VehicleInput, out);
}

inline bool ParseVehicleState(const void* data, size_t len, VehicleStatePacket& out) {
    return ParseExact(data, len, PacketType::VehicleState, out);
}

inline VehicleStatePacket MakeVehicleState(uint32_t ackSeq, uint32_t gtaTimeMs, uint16_t flags, uint16_t model, float x, float y, float z, float heading,
                                           float pitch, float roll, float speed, float steer, float seatRight, float seatForward, float seatUp,
                                           float seatHeading) {
    VehicleStatePacket v{};
    v.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::VehicleState)};
    v.ackSeq = ackSeq;
    v.gtaTimeMs = gtaTimeMs;
    v.flags = flags;
    v.model = model;
    v.pos[0] = x;
    v.pos[1] = y;
    v.pos[2] = z;
    v.heading = heading;
    v.pitch = pitch;
    v.roll = roll;
    v.speed = speed;
    v.steer = steer;
    v.seat[0] = seatRight;
    v.seat[1] = seatForward;
    v.seat[2] = seatUp;
    v.seatHeading = seatHeading;
    return v;
}

inline EchoPacket MakeEcho(uint32_t ackSeq, uint32_t gtaTimeMs, float x, float y, float z, uint16_t weaponHeld = 0, uint16_t flags = 0) {
    EchoPacket e{};
    e.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::Echo)};
    e.ackSeq = ackSeq;
    e.gtaTimeMs = gtaTimeMs;
    e.pos[0] = x;
    e.pos[1] = y;
    e.pos[2] = z;
    e.weaponHeld = weaponHeld;
    e.flags = flags;
    return e;
}

inline ShotPacket MakeShot(uint32_t ackSeq, uint32_t gtaTimeMs, uint32_t shotSeq, uint16_t weapon, uint16_t rounds) {
    ShotPacket s{};
    s.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::Shot)};
    s.ackSeq = ackSeq;
    s.gtaTimeMs = gtaTimeMs;
    s.shotSeq = shotSeq;
    s.weapon = weapon;
    s.rounds = rounds;
    return s;
}

} // namespace cj
