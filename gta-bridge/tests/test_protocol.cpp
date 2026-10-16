#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iterator>
#include <string>
#include <vector>

#include "protocol.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

static std::vector<uint8_t> ReadFile(const std::string& path) {
    std::ifstream f(path, std::ios::binary);
    CHECK(f.good());
    return {std::istreambuf_iterator<char>(f), std::istreambuf_iterator<char>()};
}

int main(int argc, char** argv) {
    CHECK(argc == 2); // path to protocol/golden
    const std::string dir = argv[1];
    using namespace cj;

    // The jump intent is its own bit, apart from aim, fire and melee.
    CHECK(kIntentJump == (1u << 3));
    CHECK((kIntentJump & (kIntentAim | kIntentFire | kIntentMelee)) == 0);

    // The body flags: the falling, knocked and dead bits and the hurt counter do not overlap each other or the older flags.
    {
        const uint32_t older = kFlagOnGround | kFlagSprint | kFlagSneak | kFlagSwim | kFlagClimbing;
        CHECK(kFlagFalling == (1u << 5) && kFlagKnocked == (1u << 6) && kFlagDead == (1u << 7));
        CHECK(kHurtShift == 8 && kHurtMask == 0xF00u);
        CHECK(kFlagBlasted == (1u << 12));
        CHECK(kFlagWallClimb == (1u << 13));
        CHECK(kFlagJetpack == (1u << 14) && kFlagJetpackThrust == (1u << 15));
        CHECK(kFlagInWater == (1u << 16) && kFlagUnderwater == (1u << 17) && kFlagWaterMoving == (1u << 18));
        const uint32_t usedNow = older | kFlagFalling | kFlagKnocked | kFlagDead | kHurtMask | kFlagBlasted | kFlagWallClimb | kFlagJetpack | kFlagJetpackThrust;
        CHECK(((kFlagInWater | kFlagUnderwater | kFlagWaterMoving) & usedNow) == 0);
        CHECK((kFlagInWater & kFlagUnderwater) == 0 && (kFlagInWater & kFlagWaterMoving) == 0 && (kFlagUnderwater & kFlagWaterMoving) == 0);
        CHECK((kFlagJetpack & kFlagJetpackThrust) == 0);
        const uint32_t usedBits = older | kFlagFalling | kFlagKnocked | kFlagDead | kHurtMask | kFlagBlasted | kFlagWallClimb;
        CHECK((kFlagJetpack & usedBits) == 0 && (kFlagJetpackThrust & usedBits) == 0);
        CHECK((kFlagWallClimb & (older | kFlagFalling | kFlagKnocked | kFlagDead | kHurtMask | kFlagBlasted)) == 0);
        const uint32_t fields[] = {kFlagFalling, kFlagKnocked, kFlagDead, kHurtMask, kFlagBlasted};
        for (int i = 0; i < 5; i++) {
            CHECK((fields[i] & older) == 0);
            for (int j = i + 1; j < 5; j++) {
                CHECK((fields[i] & fields[j]) == 0);
            }
        }
        CHECK(HurtCounter(kFlagFalling | kFlagDead | (5u << kHurtShift)) == 5);
        CHECK(HurtCounter(kFlagOnGround | kFlagSprint) == 0);
        CHECK(HurtCounter(0xFu << kHurtShift) == 15);
        CHECK(HurtCounter((0xFu << kHurtShift) + (1u << kHurtShift)) == 0); // 16 wraps to 0 and the carry is not a flag
    }

    // The golden state packet written by the Python reference parses to the fixed values.
    auto golden = ReadFile(dir + "/state_v2.bin");
    CHECK(golden.size() == sizeof(StatePacket));
    StatePacket s{};
    CHECK(ParseState(golden.data(), golden.size(), s));
    CHECK(s.seq == 7 && s.tsMs == 1234);
    CHECK(s.pos[0] == 10.5f && s.pos[1] == 64.0f && s.pos[2] == -3.25f);
    CHECK(s.vel[0] == 0.1f && s.vel[1] == 0.0f && s.vel[2] == -0.2f);
    CHECK(s.yaw == 90.0f && s.pitch == -15.0f);
    CHECK(s.flags == 3 && s.intents == 3 && s.weapon == 22);
    CHECK(s.aim[0] == 1.5f && s.aim[1] == 1.25f && s.aim[2] == -20.0f);

    // An echo built here is byte-identical to the Python one.
    { // the flags of the echo sit in its last two bytes: bit 0 says CJ holds two guns
        EchoPacket dual = MakeEcho(7, 4321, 2488.5f, -1666.25f, 13.5f, 22, kEchoDualWield);
        const uint8_t* raw = reinterpret_cast<const uint8_t*>(&dual);
        CHECK(raw[30] == 1 && raw[31] == 0);
        CHECK(kEchoDualWield == 1);
        CHECK(MakeEcho(7, 4321, 0, 0, 0, 22).flags == 0);
    }
    auto goldenEcho = ReadFile(dir + "/echo_v2.bin");
    EchoPacket e = MakeEcho(7, 4321, 2488.5f, -1666.25f, 13.5f, 22);
    CHECK(goldenEcho.size() == sizeof(e));
    CHECK(std::memcmp(goldenEcho.data(), &e, sizeof(e)) == 0);

    // Review Focus #1: wrong size, magic, version or type are rejected.
    StatePacket out{};
    CHECK(!ParseState(golden.data(), golden.size() - 1, out));
    std::vector<uint8_t> longer = golden;
    longer.push_back(0);
    CHECK(!ParseState(longer.data(), longer.size(), out));
    CHECK(!ParseState(golden.data(), 0, out));

    auto badMagic = golden;
    badMagic[0] ^= 0xFF;
    CHECK(!ParseState(badMagic.data(), badMagic.size(), out));

    auto badVersion = golden;
    badVersion[4] = 3;
    CHECK(!ParseState(badVersion.data(), badVersion.size(), out));

    auto wrongType = golden;
    wrongType[6] = 2; // Echo
    CHECK(!ParseState(wrongType.data(), wrongType.size(), out));

    // The Pose packet built here is byte-identical to the Python golden one.
    auto goldenPose = ReadFile(dir + "/pose_v2.bin");
    PosePacket pose;
    InitPose(pose, 7, 4321);
    pose.head.boneCount = 3;
    pose.head.heading = 15646; // 1.5 rad
    pose.bones[0] = {1, {0.0f, 0.0f, 0.1f}, {0.0f, 0.0f, 0.0f, 1.0f}};
    pose.bones[1] = {5, {0.0f, 0.0f, 0.7f}, {0.0f, 0.0f, 0.0f, 1.0f}};
    pose.bones[2] = {53, {0.1f, 0.2f, -0.9f}, {0.5f, 0.5f, 0.5f, 0.5f}};
    CHECK(goldenPose.size() == pose.Size());
    CHECK(pose.Size() == 24 + 32 * 3);
    CHECK(std::memcmp(goldenPose.data(), &pose, pose.Size()) == 0);
    CHECK(sizeof(PosePacket) == 24 + 32 * kMaxBones);

    // The Shot packet built here is byte-identical to the Python golden one.
    auto goldenShot = ReadFile(dir + "/shot_v2.bin");
    ShotPacket shot = MakeShot(7, 4321, 12, 22, 3);
    CHECK(goldenShot.size() == sizeof(shot));
    CHECK(sizeof(ShotPacket) == 24 && sizeof(StatePacket) == 72 && sizeof(EchoPacket) == 32);
    CHECK(std::memcmp(goldenShot.data(), &shot, sizeof(shot)) == 0);

    // The vehicle packets: sizes, the golden files, and rejection of anything that is not exactly the packet.
    {
        CHECK(static_cast<uint16_t>(PacketType::VehicleCommand) == 5 && static_cast<uint16_t>(PacketType::VehicleInput) == 6 &&
              static_cast<uint16_t>(PacketType::VehicleState) == 7);
        CHECK(sizeof(VehicleCommandPacket) == 24 && sizeof(VehicleInputPacket) == 36 && sizeof(VehicleStatePacket) == 68);
        CHECK(kVehicleCmdNone == 0 && kVehicleCmdEnter == 1 && kVehicleCmdExit == 2);
        CHECK(kVehicleCorrPresent == 1 && kVehicleFlagDriverIn == 1);

        auto goldenCommand = ReadFile(dir + "/vehicle_command_v2.bin");
        VehicleCommandPacket command{};
        CHECK(ParseVehicleCommand(goldenCommand.data(), goldenCommand.size(), command));
        CHECK(command.seq == 7 && command.tsMs == 1234 && command.command == kVehicleCmdEnter && command.model == 492 && command.yaw == 90.0f);

        auto goldenInput = ReadFile(dir + "/vehicle_input_v2.bin");
        VehicleInputPacket input{};
        CHECK(ParseVehicleInput(goldenInput.data(), goldenInput.size(), input));
        CHECK(input.seq == 8 && input.tsMs == 1300 && input.throttle == 200 && input.brake == 30 && input.steer == -64 && input.handbrake == 255);
        CHECK(input.corrFlags == kVehicleCorrPresent && input.corrX == 0.5f && input.corrY == -0.25f);

        auto goldenState = ReadFile(dir + "/vehicle_state_v2.bin");
        VehicleStatePacket vs = MakeVehicleState(8, 4321, kVehicleFlagDriverIn, 492, 1514.5f, -2585.5f, 13.25f, 1.5f, 0.0625f, -0.03125f, 12.5f, -0.25f,
                                                 -0.5f, 0.125f, -0.0625f, 0.5f);
        CHECK(goldenState.size() == sizeof(vs));
        CHECK(std::memcmp(goldenState.data(), &vs, sizeof(vs)) == 0);

        // Every parser rejects a wrong size, magic, version or type, and another packet's bytes.
        auto bad = [](std::vector<uint8_t> d, size_t offset, uint8_t value) { d[offset] = value; return d; };
        for (auto* g : {&goldenCommand, &goldenInput, &goldenState}) {
            std::vector<uint8_t> longer = *g;
            longer.push_back(0);
            std::vector<std::vector<uint8_t>> broken = {std::vector<uint8_t>(g->begin(), g->end() - 1), longer, bad(*g, 0, static_cast<uint8_t>((*g)[0] ^ 0xFF)),
                                                        bad(*g, 4, 3), bad(*g, 6, 1)};
            for (auto& b : broken) {
                VehicleCommandPacket c{};
                VehicleInputPacket i{};
                VehicleStatePacket v{};
                const bool isCommand = g == &goldenCommand, isInput = g == &goldenInput;
                CHECK(!(isCommand && ParseVehicleCommand(b.data(), b.size(), c)));
                CHECK(!(isInput && ParseVehicleInput(b.data(), b.size(), i)));
                CHECK(!(!isCommand && !isInput && ParseVehicleState(b.data(), b.size(), v)));
            }
        }
        VehicleInputPacket i2{};
        VehicleStatePacket v2{};
        CHECK(!ParseVehicleInput(goldenCommand.data(), goldenCommand.size(), i2));
        CHECK(!ParseVehicleState(goldenInput.data(), goldenInput.size(), v2));
        CHECK(!ParseState(goldenState.data(), goldenState.size(), out));
        CHECK(!ParseVehicleState(golden.data(), golden.size(), v2)); // a State is not a VehicleState

        // The type comes from the header alone.
        CHECK(PacketTypeOf(golden.data(), golden.size()) == static_cast<int>(PacketType::State));
        CHECK(PacketTypeOf(goldenInput.data(), goldenInput.size()) == static_cast<int>(PacketType::VehicleInput));
        CHECK(PacketTypeOf(goldenState.data(), goldenState.size()) == static_cast<int>(PacketType::VehicleState));
        CHECK(PacketTypeOf(goldenPose.data(), goldenPose.size()) == static_cast<int>(PacketType::Pose));
        CHECK(PacketTypeOf(goldenState.data(), 7) == -1);
        CHECK(PacketTypeOf(goldenState.data(), 8) == static_cast<int>(PacketType::VehicleState));
        CHECK(PacketTypeOf(badMagic.data(), badMagic.size()) == -1);
        CHECK(PacketTypeOf(badVersion.data(), badVersion.size()) == -1);
    }

    std::puts("test_protocol OK");
    return 0;
}
