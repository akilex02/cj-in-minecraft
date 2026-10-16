#include <cmath>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <deque>
#include <string>
#include <vector>

#include "bridge_core.h"

#define CHECK(cond)                                                            \
  do {                                                                         \
    if (!(cond)) {                                                             \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__,    \
                   #cond);                                                     \
      std::exit(1);                                                            \
    }                                                                          \
  } while (0)

using namespace cj;

// Raw memory shaped like the bits of a CPed that the bridge touches.
struct FakePed {
  alignas(8) uint8_t mem[0x600]{};
  alignas(8) RwMat matrix{};

  explicit FakePed(Vec3 at, bool inVehicle = false) {
    matrix.right[0] = 1;
    matrix.up[1] = 1;
    matrix.at[2] = 1;
    *reinterpret_cast<uint8_t **>(mem + gta::kMatrixPtr) =
        reinterpret_cast<uint8_t *>(&matrix);
    SetPos(at);
    SetInVehicle(inVehicle);
  }
  float *Pos() { return matrix.pos; }
  void SetPos(Vec3 p) {
    matrix.pos[0] = p.x;
    matrix.pos[1] = p.y;
    matrix.pos[2] = p.z;
  }
  Vec3 GetPos() { return {matrix.pos[0], matrix.pos[1], matrix.pos[2]}; }
  uint32_t &PedFlags() {
    return *reinterpret_cast<uint32_t *>(mem + gta::kPedFlags);
  }
  void SetInVehicle(bool on) {
    PedFlags() = on ? (PedFlags() | gta::kPedFlagInVehicle)
                    : (PedFlags() & ~gta::kPedFlagInVehicle);
  }
};

struct FakeIo : BridgeIo {
  uint8_t *ped = nullptr;
  alignas(8) uint8_t pad[gta::kPadSize]{};
  float pedDensity = 1.0f;
  float carDensity = 0.8f;
  uint32_t now = 0;
  std::deque<std::vector<uint8_t>> inbox;
  std::vector<EchoPacket> echoes;
  std::vector<PosePacket> poses;
  std::vector<std::string> logs;
  bool skeletonAvailable = true;
  RwMat bones[3]{};
  int boneCount = 3;

  FakeIo() {
    for (auto &b : bones) {
      b.right[0] = 1;
      b.up[1] = 1;
      b.at[2] = 1;
    }
  }

  uint8_t *Ped() override { return ped; }
  uint8_t *Pad() override { return pad; }
  float *PedDensity() override { return &pedDensity; }
  float *CarDensity() override { return &carDensity; }
  uint32_t NowMs() override { return now; }
  int Recv(void *buf, size_t cap) override {
    if (inbox.empty())
      return -1;
    auto d = inbox.front();
    inbox.pop_front();
    if (d.size() > cap)
      return static_cast<int>(cap) + 1;
    std::memcpy(buf, d.data(), d.size());
    return static_cast<int>(d.size());
  }
  void SendEcho(const EchoPacket &e) override { echoes.push_back(e); }
  void SendPose(const PosePacket &p) override { poses.push_back(p); }
  bool ReadSkeleton(uint8_t *, Skeleton &out) override {
    if (!skeletonAvailable)
      return false;
    out.matrices = bones;
    out.count = boneCount;
    const int32_t ids[3] = {1, 5, 53};
    for (int i = 0; i < 3; i++)
      out.ids[i] = ids[i];
    return true;
  }
  void Log(const char *line) override { logs.emplace_back(line); }

  void PushState(uint32_t seq, float x, float y, float z, float vx = 0,
                 float vy = 0, float vz = 0, uint32_t flags = kFlagOnGround) {
    StatePacket s{};
    s.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::State)};
    s.seq = seq;
    s.pos[0] = x;
    s.pos[1] = y;
    s.pos[2] = z;
    s.vel[0] = vx;
    s.vel[1] = vy;
    s.vel[2] = vz;
    s.flags = flags;
    std::vector<uint8_t> d(sizeof(s));
    std::memcpy(d.data(), &s, sizeof(s));
    inbox.push_back(std::move(d));
  }
  // --- guns ---
  uint16_t held = 0;
  uint32_t ammo = 17;
  std::vector<uint16_t> given;
  int taken = 0;
  int stops = 0;
  struct GunCall {
    Vec3 target;
    GunCommand cmd;
  };
  std::vector<GunCall> gunCalls;
  std::vector<ShotPacket> shots;

  uint16_t HeldWeapon(uint8_t *) override { return held; }
  uint32_t AmmoInClip(uint8_t *) override { return ammo; }
  void GiveWeapon(uint8_t *, uint16_t w) override {
    given.push_back(w);
    held = w;
  }
  void TakeWeapons(uint8_t *) override {
    taken++;
    held = 0;
  }
  void ControlGun(uint8_t *, Vec3 t, GunCommand c) override { gunCalls.push_back({t, c}); }
  void StopGun(uint8_t *) override { stops++; }
  struct Facing {
    float heading;
    size_t posesSentBefore;
  };
  std::vector<Facing> facings;
  void SetPedHeading(uint8_t *, float h) override { facings.push_back({h, poses.size()}); }
  bool gunTaskActive = false;
  bool GunTaskActive(uint8_t *) override { return gunTaskActive; }
  void SendShot(const ShotPacket &s) override { shots.push_back(s); }
  std::vector<BodyAnim> bodyAnims;
  std::vector<BodyAnim> keeps; // KeepBodyAnim calls
  std::vector<int> jetSounds;  // JetpackSound calls: 0 off, 1 hover, 2 thrust
  int swimPrepared = 0;        // PrepareSwim calls
  int jetForgets = 0;          // JetpackSound(nullptr, ...): forgotten without touching the audio entity
  int bodyClears = 0;
  int bodyForgets = 0; // ClearBodyAnim(nullptr): the held animation is dropped without touching memory
  std::vector<int> pains;
  void PlayBodyAnim(uint8_t *, BodyAnim a) override { bodyAnims.push_back(a); }
  void KeepBodyAnim(uint8_t *, BodyAnim a) override { keeps.push_back(a); }
  void PrepareSwim() override { swimPrepared++; }
  void JetpackSound(uint8_t *p, int mode) override {
    jetSounds.push_back(mode);
    if (!p) jetForgets++;
  }
  void ClearBodyAnim(uint8_t *ped) override { (ped ? bodyClears : bodyForgets)++; }
  void SayPain(uint8_t *, int context) override { pains.push_back(context); }
  Outfit outfit{};
  bool outfitReadable = true;
  std::vector<std::string> outfitsSaved;
  bool ReadOutfit(uint8_t *, Outfit &out) override {
    out = outfit;
    return outfitReadable;
  }
  void SaveOutfit(const std::string &json) override { outfitsSaved.push_back(json); }
  bool dual = false;
  bool DualWielding(uint8_t *) override { return dual; }
  bool ducking = false;
  bool IsDucking(uint8_t *) override { return ducking; }
  int emptyClicks = 0;
  void PlayEmptyClick() override { emptyClicks++; }
  void PushBodyState(uint32_t seq, uint32_t flags) {
    StatePacket s{};
    s.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::State)};
    s.seq = seq;
    s.pos[1] = 64;
    s.flags = kFlagOnGround | flags;
    std::vector<uint8_t> d(sizeof(s));
    std::memcpy(d.data(), &s, sizeof(s));
    inbox.push_back(std::move(d));
  }
  uint32_t strikes = 0;
  uint32_t MeleeStrikes(uint8_t *) override { return strikes; }
  int removedProjectiles = 0;
  void RemoveProjectiles() override { removedProjectiles++; }

  void PushGunState(uint32_t seq, uint16_t weapon, uint32_t intents, float ax = 0, float ay = 1.6f, float az = -20) {
    StatePacket s{};
    s.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::State)};
    s.seq = seq;
    s.pos[1] = 64;
    s.flags = kFlagOnGround;
    s.weapon = weapon;
    s.intents = intents;
    s.aim[0] = ax;
    s.aim[1] = ay;
    s.aim[2] = az;
    std::vector<uint8_t> d(sizeof(s));
    std::memcpy(d.data(), &s, sizeof(s));
    inbox.push_back(std::move(d));
  }

  // --- the car ---
  uint8_t carMem[16]{};
  int carsCreated = 0;
  int carsDeleted = 0;
  int carExits = 0;
  int carCorrections = 0;
  int carModel = 0;
  float carHeading = 0;
  bool carReadable = true;
  std::vector<VehicleStatePacket> carStates;
  uint8_t *CreateVehicle(uint8_t *, int model, float heading) override {
    carsCreated++;
    carModel = model;
    carHeading = heading;
    return carMem;
  }
  bool SeatDriver(uint8_t *p, uint8_t *) override {
    *reinterpret_cast<uint32_t *>(p + gta::kPedFlags) |= gta::kPedFlagInVehicle;
    return true;
  }
  void MakeVehicleSafe(uint8_t *, uint8_t *) override {}
  bool ReadVehicle(uint8_t *, uint8_t *, VehicleStatePacket &out) override {
    out = {};
    out.flags = kVehicleFlagDriverIn;
    out.model = 492;
    out.pos[0] = 1514.5f;
    return carReadable;
  }
  int eatStarts = 0;
  int eatKind = -1;
  bool eatRunning = false;
  bool EatTaskRunning(uint8_t *) override { return eatRunning; }
  bool StartEating(uint8_t *, int kind) override {
    eatStarts++;
    eatKind = kind;
    eatRunning = true;
    return true;
  }
  int pedRestores = 0;
  void RestorePed(uint8_t *) override { pedRestores++; }
  void ShiftVehicle(uint8_t *, float, float) override {}
  void CorrectVehicle(uint8_t *, float, float) override { carCorrections++; }
  void ExitDriver(uint8_t *p, uint8_t *, float side) override {
    carExits++;
    *reinterpret_cast<uint32_t *>(p + gta::kPedFlags) &= ~gta::kPedFlagInVehicle;
    PedMatrix(p)->pos[0] += side; // CJ stands beside the car
  }
  void DeleteVehicle(uint8_t *) override { carsDeleted++; }
  void SendVehicleState(const VehicleStatePacket &s) override { carStates.push_back(s); }
  void PushVehicleCommand(uint32_t seq, uint16_t command, uint16_t model = 492, float yaw = 0) {
    VehicleCommandPacket c{};
    c.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::VehicleCommand)};
    c.seq = seq;
    c.command = command;
    c.model = model;
    c.yaw = yaw;
    std::vector<uint8_t> d(sizeof(c));
    std::memcpy(d.data(), &c, sizeof(c));
    inbox.push_back(std::move(d));
  }
  void PushVehicleInput(uint32_t seq, uint16_t throttle, int16_t steer = 0) {
    VehicleInputPacket i{};
    i.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::VehicleInput)};
    i.seq = seq;
    i.throttle = throttle;
    i.steer = steer;
    std::vector<uint8_t> d(sizeof(i));
    std::memcpy(d.data(), &i, sizeof(i));
    inbox.push_back(std::move(d));
  }

  int16_t PadField(size_t off) {
    int16_t v;
    std::memcpy(&v, pad + off, 2);
    return v;
  }
  bool LogHas(const char *needle) {
    for (const auto &l : logs)
      if (l.find(needle) != std::string::npos)
        return true;
    return false;
  }
};

static bool Near(Vec3 a, Vec3 b) {
  return std::fabs(a.x - b.x) < 1e-3f && std::fabs(a.y - b.y) < 1e-3f &&
         std::fabs(a.z - b.z) < 1e-3f;
}

int main() {
  { // CJ is held at the stage: X and Y are put back every frame, Z is left to
    // the game's gravity
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 10, 64, 10);
    core.Tick(io); // engages: the stage is where CJ stands
    CHECK(Near(ped.GetPos(), {100, 200, 30}));

    ped.SetPos({103, 202, 35}); // the game walked CJ forward and he rose by 5
    io.PushState(2, 11, 64, 10);
    core.Tick(io);
    CHECK(Near(ped.GetPos(), {100, 200, 35}));

    ped.SetPos({104, 203, 31}); // no new packet this frame: still held
    core.Tick(io);
    CHECK(Near(ped.GetPos(), {100, 200, 31}));
  }

  { // the pad: stick forward when moving, walk when sneaking, nothing when idle
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadLeftStickY) == 0);

    io.PushState(2, 0, 64, 0, 3.0f, 0, 4.0f); // 5 blocks/s
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadLeftStickY) == -128);
    CHECK(io.PadField(gta::kPadPedWalk) == 0);

    io.PushState(3, 0, 64, 0, 1.0f, 0, 0, kFlagOnGround | kFlagSneak);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadPedWalk) == 1);

    io.PushState(4, 0, 64, 0, 0, 0, 0); // stops
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadLeftStickY) == 0);
  }

  { // the outfit: written when first seen and when it changes, never when it cannot be read
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.outfit.muscle = 1000.0f;
    io.outfit.models[0] = 7;
    io.PushGunState(1, 0, 0);
    core.Tick(io);
    CHECK(io.outfitsSaved.size() == 1 && io.outfitsSaved[0].find("\"muscle\": 1000.000") != std::string::npos);
    io.now += 5000;
    io.PushGunState(2, 0, 0);
    core.Tick(io);
    CHECK(io.outfitsSaved.size() == 1); // the same outfit
    io.outfit.fat = 400.0f;
    io.now += 5000;
    io.PushGunState(3, 0, 0);
    core.Tick(io);
    CHECK(io.outfitsSaved.size() == 2);
    io.outfitReadable = false;
    io.outfit.fat = 900.0f;
    io.now += 5000;
    io.PushGunState(4, 0, 0);
    core.Tick(io);
    CHECK(io.outfitsSaved.size() == 2); // unreadable: nothing is written
  }

  { // the echo says when CJ holds two guns, so the client draws the second one
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    CHECK(!io.echoes.empty() && io.echoes.back().flags == 0);
    io.dual = true;
    io.PushGunState(2, 22, 0);
    core.Tick(io);
    CHECK((io.echoes.back().flags & kEchoDualWield) != 0);
    io.dual = false;
    io.PushGunState(3, 22, 0);
    core.Tick(io);
    CHECK(io.echoes.back().flags == 0);
  }

  { // the crouch wish reaches the duck button, and stops once the game's ped is crouched
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 0, 0);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadShockButtonL) == 0);
    io.PushGunState(2, 0, kIntentCrouch);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadShockButtonL) == 1); // the press
    io.ducking = true; // it took
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadShockButtonL) == 0);
    io.PushGunState(3, 0, kIntentCrouch); // still wished and crouched: nothing more
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadShockButtonL) == 0);
  }

  { // the jump pulse reaches the pad exactly once
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 65, 0, 0, 8.0f, 0, 0); // leaves the ground
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonSquare) == 1);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonSquare) == 0);
  }

  { // densities: zeroed while engaged (even if a script raises them), restored
    // on release
    FakePed ped({0, 0, 0});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    CHECK(io.pedDensity == 0.0f && io.carDensity == 0.0f);
    io.pedDensity = 1.0f; // a script resets it
    io.PushState(2, 0, 64, 0);
    core.Tick(io);
    CHECK(io.pedDensity == 0.0f && io.carDensity == 0.0f);

    io.now = 1001; // Review Focus #4: expiry
    core.Tick(io);
    CHECK(io.pedDensity == 1.0f &&
          io.carDensity == 0.8f); // the values from before the engagement

    std::memset(io.pad, 0x55, sizeof(io.pad));
    core.PrePad(io); // and the pad is no longer written
    for (size_t i = 0; i < sizeof(io.pad); i++)
      CHECK(io.pad[i] == 0x55);
  }

  { // the echo and the pose
    FakePed ped({100, 200, 30});
    ped.matrix.right[0] = 0;
    ped.matrix.right[1] = 1; // the ped is turned 90 degrees
    ped.matrix.up[0] = -1;
    ped.matrix.up[1] = 0;
    FakeIo io;
    io.ped = ped.mem;
    // bones: pelvis at the ped, head 0.7 above, a hand 0.5 in front (the ped's
    // forward is -X)
    io.bones[0].pos[0] = 100;
    io.bones[0].pos[1] = 200;
    io.bones[0].pos[2] = 30;
    io.bones[1].pos[0] = 100;
    io.bones[1].pos[1] = 200;
    io.bones[1].pos[2] = 30.7f;
    io.bones[2].pos[0] = 99.5f;
    io.bones[2].pos[1] = 200;
    io.bones[2].pos[2] = 30;
    BridgeCore core;
    io.PushState(7, 0, 64, 0);
    core.Tick(io);
    CHECK(io.echoes.size() == 1 && io.echoes[0].ackSeq == 7);
    CHECK(io.poses.size() == 1);
    const PosePacket &p = io.poses[0];
    CHECK(p.head.boneCount == 3 && p.head.ackSeq == 7);
    CHECK(p.bones[0].nodeId == 1 && p.bones[1].nodeId == 5 &&
          p.bones[2].nodeId == 53);
    CHECK(std::fabs(p.bones[1].pos[2] - 0.7f) < 1e-4f);
    CHECK(std::fabs(p.bones[2].pos[0] - 0.5f) <
          1e-4f); // in front of the ped = +X in CJ's model space

    core.Tick(io); // one pose and one echo per frame while engaged, with or
                   // without new packets
    CHECK(io.poses.size() == 2 && io.echoes.size() == 2);
  }

  { // Review Focus #1: no skeleton -> no pose, nothing breaks; too many nodes
    // -> no pose
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.skeletonAvailable = false;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    core.Tick(io);
    CHECK(io.poses.empty());
    CHECK(io.echoes.size() == 2);
    io.skeletonAvailable = true;
    io.boneCount = static_cast<int>(kMaxBones) + 1;
    core.Tick(io);
    CHECK(io.poses.empty());
  }

  { // Review Focus #3: a CJ in a vehicle is never driven; he is released if he
    // gets in while engaged
    FakePed ped({100, 200, 30}, /*inVehicle*/ true);
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0, 5.0f, 0, 0);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadLeftStickY) == 0);
    CHECK(io.pedDensity == 1.0f && io.carDensity == 0.8f);
    CHECK(io.poses.empty() && io.echoes.empty());
    CHECK(Near(ped.GetPos(), {100, 200, 30}));

    ped.SetInVehicle(
        false); // he gets out: the next packet engages where he stands
    io.PushState(2, 0, 64, 0, 5.0f, 0, 0);
    core.Tick(io);
    CHECK(io.pedDensity == 0.0f);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadLeftStickY) == -128);

    ped.SetInVehicle(true); // he gets into a car while engaged
    io.pad[gta::kPadLeftStickY] = 0;
    io.pad[gta::kPadLeftStickY + 1] = 0;
    core.PrePad(io); // the next frame's PrePad runs before its Tick can release
                     // him: it must not drive him
    CHECK(io.PadField(gta::kPadLeftStickY) == 0);
    io.PushState(3, 0, 64, 0, 5.0f, 0, 0);
    core.Tick(io);
    CHECK(io.pedDensity == 1.0f && io.carDensity == 0.8f);
    io.pad[gta::kPadLeftStickY] = 0;
    io.pad[gta::kPadLeftStickY + 1] = 0;
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadLeftStickY) == 0);
  }

  { // no player ped yet (menu / loading): packets are drained and dropped,
    // nothing is sent
    FakeIo io;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    io.PushState(2, 1, 64, 0);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.inbox.empty());
    CHECK(io.echoes.empty() && io.poses.empty());

    FakePed ped(
        {300, 400,
         20}); // the player appears; the next packet engages at his position
    io.ped = ped.mem;
    io.PushState(3, 70, 64, 70);
    core.Tick(io);
    ped.SetPos(
        {302, 402,
         20}); // an animation drift of 2.8 m, well under the re-stage threshold
    core.Tick(io);
    CHECK(Near(ped.GetPos(), {300, 400, 20}));
  }

  { // malformed datagrams never engage anything
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.inbox.push_back(std::vector<uint8_t>(12, 0xAB));
    io.inbox.push_back(std::vector<uint8_t>(61, 0));
    io.inbox.push_back(std::vector<uint8_t>(300, 0));
    core.Tick(io);
    CHECK(io.pedDensity == 1.0f && io.carDensity == 0.8f);
    CHECK(io.echoes.empty() && io.poses.empty());
  }

  { // the animation speed is measured from how far the game moves CJ each
    // frame, and logged every 2 s
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io); // engage at (100, 200)
    for (uint32_t i = 0; i < 120; i++) {
      ped.SetPos({100.1f, 200, 30}); // the game moved CJ 0.1 m this frame
      io.now += 20;                  // 50 frames per second -> 5 m/s
      io.PushState(2 + i, 0, 64, 0);
      core.Tick(io);
    }
    float speed = 0;
    bool found = false;
    for (const auto &l : io.logs) {
      if (std::sscanf(l.c_str(), "anim speed %f m/s", &speed) == 1)
        found = true;
    }
    CHECK(found);
    CHECK(std::fabs(speed - 5.0f) < 0.2f);
  }

  { // Finding 1: the game replaces the ped (loading a save): the old stage must
    // not be applied to the new ped
    FakePed a({100, 200, 30});
    FakePed b({500, 600, 20});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    CHECK(io.pedDensity == 0.0f);

    io.ped = b.mem; // a new game was loaded: a different ped, somewhere else
    io.PushState(2, 0, 64, 0);
    core.Tick(io);
    CHECK(Near(b.GetPos(), {500, 600, 20})); // not yanked to (100, 200)
    CHECK(io.LogHas("player ped changed"));

    b.SetPos({503, 603, 20}); // a normal animation drift: the new stage is
                              // where the new ped stood
    io.PushState(3, 0, 64, 0);
    core.Tick(io);
    CHECK(Near(b.GetPos(), {500, 600, 20}));
  }

  { // Finding 1: the player ped disappears while engaged: densities come back,
    // and a later ped is engaged afresh
    FakePed a({100, 200, 30});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0, 5.0f, 0, 0);
    core.Tick(io);
    CHECK(io.pedDensity == 0.0f && io.carDensity == 0.0f);

    io.ped = nullptr; // loading screen
    io.PushState(2, 0, 64, 0);
    core.Tick(io);
    CHECK(io.pedDensity == 1.0f && io.carDensity == 0.8f);
    std::memset(io.pad, 0x55, sizeof(io.pad));
    core.PrePad(io);
    for (size_t i = 0; i < sizeof(io.pad); i++)
      CHECK(io.pad[i] == 0x55);

    FakePed c({700, 800, 10});
    io.ped = c.mem;
    io.PushState(3, 0, 64, 0);
    core.Tick(io);
    CHECK(Near(c.GetPos(), {700, 800, 10}));
    CHECK(io.pedDensity == 0.0f);
  }

  { // Finding 1: the game teleports CJ far away (hospital, mission, interior):
    // re-stage there instead of pulling him back
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    ped.SetPos({900, 1500, 20});
    io.PushState(2, 0, 64, 0);
    core.Tick(io);
    CHECK(Near(ped.GetPos(), {900, 1500, 20}));
    CHECK(io.LogHas("moved CJ"));
    ped.SetPos({901, 1500.5f, 20}); // and the new stage holds from then on
    io.PushState(3, 0, 64, 0);
    core.Tick(io);
    CHECK(Near(ped.GetPos(), {900, 1500, 20}));
  }

  { // guns: the item's weapon is given, then the aim task follows the Minecraft aim vector
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io); // engages and gives the weapon
    CHECK(io.given.size() == 1 && io.given[0] == 22);
    CHECK(io.gunCalls.empty());
    CHECK(io.echoes.back().weaponHeld == 0); // the echo reports what he held when the frame started

    io.PushGunState(2, 22, kIntentAim, 0, 1.6f, -20);
    core.Tick(io);
    CHECK(io.given.size() == 1);
    CHECK(io.gunCalls.size() == 1 && io.gunCalls[0].cmd == GunCommand::Aim);
    CHECK(Near(io.gunCalls[0].target, {100, 220, 31.6f}));
    CHECK(io.echoes.back().weaponHeld == 22);

    io.PushGunState(3, 22, kIntentAim | kIntentFire, 0, 1.6f, -20);
    core.Tick(io);
    CHECK(io.gunCalls.size() == 2 && io.gunCalls[1].cmd == GunCommand::Fire);

    io.PushGunState(4, 22, 0);
    core.Tick(io);
    CHECK(io.stops == 1 && io.gunCalls.size() == 2);
  }

  { // Review Focus #3: an unusable aim vector never creates the task
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentAim, 0, 0, 0);
    core.Tick(io);
    CHECK(io.gunCalls.empty() && io.given.empty());
  }

  { // a rocket launcher in hand: GTA's own rocket is removed every frame (the mod makes the real one); no other weapon is touched
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 35;
    BridgeCore core;
    for (uint32_t seq = 1; seq <= 3; seq++) {
      io.PushGunState(seq, 35, kIntentAim);
      core.Tick(io);
    }
    CHECK(io.removedProjectiles == 3);
    FakePed ped2({100, 200, 30});
    FakeIo io2;
    io2.ped = ped2.mem;
    io2.held = 22;
    BridgeCore core2;
    for (uint32_t seq = 1; seq <= 3; seq++) {
      io2.PushGunState(seq, 22, kIntentAim);
      core2.Tick(io2);
    }
    CHECK(io2.removedProjectiles == 0);
  }

  { // the puppet cannot be hurt by the explosion or the fire of the rocket GTA fires at his feet: invulnerable while a rocket launcher is held, as before after
    const uint32_t kBoth = kPhysicalInvulnerable | kPhysicalExplosionProof | kPhysicalFireProof;
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    auto flags = [&]() { return *reinterpret_cast<uint32_t *>(ped.mem + gta::kPhysicalFlags); };
    *reinterpret_cast<uint32_t *>(ped.mem + gta::kPhysicalFlags) = 0x42u;
    io.held = 35;
    BridgeCore core;
    io.PushGunState(1, 35, kIntentAim);
    core.Tick(io);
    CHECK((flags() & kBoth) == kBoth && (flags() & ~kBoth) == 0x42u);
    io.held = 22; // another weapon: the shield goes off
    io.PushGunState(2, 22, kIntentAim);
    core.Tick(io);
    CHECK(flags() == 0x42u);
    io.held = 35;
    io.PushGunState(3, 35, kIntentAim);
    core.Tick(io);
    CHECK((flags() & kBoth) == kBoth);
    io.now += Puppet::kTimeoutMs + 1; // released: the ped is as it was
    core.Tick(io);
    CHECK(flags() == 0x42u);
  }

  { // another ped (a new game) is never given the old ped's saved flags
    FakePed a({100, 200, 30});
    FakePed b({100, 200, 30});
    FakeIo io;
    io.ped = a.mem;
    io.held = 35;
    *reinterpret_cast<uint32_t *>(a.mem + gta::kPhysicalFlags) = kPhysicalInvulnerable; // what the old ped had of its own: it must not reach the new one
    BridgeCore core;
    io.PushGunState(1, 35, kIntentAim);
    core.Tick(io);
    *reinterpret_cast<uint32_t *>(b.mem + gta::kPhysicalFlags) = 0x80u;
    io.ped = b.mem; // the game was loaded again
    io.held = 22;
    io.PushGunState(2, 22, kIntentAim);
    core.Tick(io);
    CHECK(*reinterpret_cast<uint32_t *>(b.mem + gta::kPhysicalFlags) == 0x80u);
  }

  { // melee blows: each one GTA's fight task makes is a Shot (weapon = what is held: 0 fists, 5 bat), twice on the wire, on the shared counter
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 5;
    io.strikes = 7; // blows from before the session: not reported
    BridgeCore core;
    io.PushGunState(1, 5, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    CHECK(io.shots.empty());
    io.strikes = 8;
    io.PushGunState(2, 5, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    CHECK(io.shots.size() == 2);
    CHECK(io.shots[0].shotSeq == 1 && io.shots[0].rounds == 1 && io.shots[0].weapon == 5);
    CHECK(std::memcmp(&io.shots[0], &io.shots[1], sizeof(ShotPacket)) == 0);
    io.PushGunState(3, 5, kIntentMelee, 0, 0, 0);
    core.Tick(io); // no new blow
    CHECK(io.shots.size() == 2);
    io.strikes = 10;
    io.PushGunState(4, 5, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    CHECK(io.shots.size() == 4 && io.shots[2].shotSeq == 3 && io.shots[2].rounds == 2);
  }

  { // fists: the weapon on the wire is 0
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 0, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    io.strikes = 1;
    io.PushGunState(2, 0, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    CHECK(io.shots.size() == 2 && io.shots[0].weapon == 0 && io.shots[0].rounds == 1);
  }

  { // blows and firearm rounds share one counter that only goes up
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentFire);
    core.Tick(io);
    io.ammo = 16; // one round
    io.PushGunState(2, 22, kIntentFire);
    core.Tick(io);
    CHECK(io.shots.size() == 2 && io.shots[0].shotSeq == 1);
    io.held = 0; // the player puts the gun away and punches: the counter goes on from where the rounds left it
    io.strikes = 1;
    io.PushGunState(3, 0, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    CHECK(io.shots.size() == 4 && io.shots[2].shotSeq == 2 && io.shots[2].weapon == 0);
  }

  { // a blow that is closed while a gun is in CJ's hand (the player switched item in mid-swing) is not reported as that gun's shot
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 5;
    BridgeCore core;
    io.PushGunState(1, 5, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    io.held = 22; // the Colt is in hand now
    io.strikes = 1; // the swing is closed on this frame
    io.PushGunState(2, 22, kIntentFire);
    core.Tick(io);
    CHECK(io.shots.empty());
    io.held = 5;
    io.strikes = 2; // and the next blow of a bat does count: nothing was lost for good
    io.PushGunState(3, 5, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    CHECK(io.shots.size() == 2 && io.shots[0].weapon == 5 && io.shots[0].rounds == 1);
  }

  { // a gun CJ carries from his save is never fired by the tapping attack button of a punch
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22; // the Colt of the save, back in his hand when the player's hand is empty
    BridgeCore core;
    io.PushGunState(1, 0, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonCircle) == 0);
    io.held = 0; // unarmed: the blow goes out as usual
    io.PushGunState(2, 0, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    bool pressed = false;
    for (int frame = 0; frame < 2; frame++) { // the blow button is tapped: it is down in one of two frames in a row
      std::memset(io.pad, 0, sizeof(io.pad));
      core.PrePad(io);
      pressed = pressed || io.PadField(gta::kPadButtonCircle) == 1;
    }
    CHECK(pressed);
  }

  { // free fall: the animation is asked for once when the flag rises, and cleared when it drops
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    CHECK(io.bodyAnims.empty());
    io.PushBodyState(2, kFlagFalling);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 1 && io.bodyAnims[0] == BodyAnim::Fall);
    io.PushBodyState(3, kFlagFalling);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 1); // not asked again
    io.PushBodyState(4, 0);
    core.Tick(io);
    CHECK(io.bodyClears == 1 && io.bodyAnims.size() == 1);
  }

  { // climbing a wall: the three animations in order, each once, and the last one is cleared when the flag drops
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagWallClimb);
    core.Tick(io);
    io.now += 600;
    io.PushBodyState(3, kFlagWallClimb);
    core.Tick(io);
    io.now += 800;
    io.PushBodyState(4, kFlagWallClimb);
    core.Tick(io);
    io.PushBodyState(5, kFlagWallClimb);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 3 && io.bodyAnims[0] == BodyAnim::Climb && io.bodyAnims[1] == BodyAnim::ClimbPull
          && io.bodyAnims[2] == BodyAnim::ClimbStand);
    io.PushBodyState(6, 0);
    core.Tick(io);
    CHECK(io.bodyClears == 1);
  }

  { // while climbing the stage animation is kept every frame (GTA's own jump animations fight it); never otherwise
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    core.Tick(io);
    CHECK(io.keeps.empty());
    io.PushBodyState(2, kFlagWallClimb);
    core.Tick(io);
    core.Tick(io);
    CHECK(io.keeps.size() == 2 && io.keeps[0] == BodyAnim::Climb && io.keeps[1] == BodyAnim::Climb);
    io.now += 600;
    io.PushBodyState(3, kFlagWallClimb);
    core.Tick(io);
    CHECK(io.keeps.size() == 3 && io.keeps[2] == BodyAnim::ClimbPull);
    io.PushBodyState(4, 0);
    core.Tick(io);
    core.Tick(io);
    CHECK(io.keeps.size() == 3);
  }

  { // the jetpack: the pose once, kept every frame, and the sound follows the thrust and is always switched off
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    CHECK(io.bodyAnims.empty() && io.jetSounds.empty() && io.keeps.empty());
    io.PushBodyState(2, kFlagJetpack);
    core.Tick(io);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 1 && io.bodyAnims[0] == BodyAnim::Jetpack);
    CHECK(io.keeps.size() == 2 && io.keeps[0] == BodyAnim::Jetpack);
    CHECK(io.jetSounds.size() == 2 && io.jetSounds[0] == 1 && io.jetSounds[1] == 1);      // hovering: updated every frame
    io.PushBodyState(3, kFlagJetpack | kFlagJetpackThrust);
    core.Tick(io);
    CHECK(io.jetSounds.back() == 2);
    io.PushBodyState(4, 0);
    core.Tick(io);
    CHECK(io.bodyClears == 1);
    CHECK(io.jetSounds.back() == 0);
    size_t after = io.jetSounds.size();
    core.Tick(io);
    core.Tick(io);
    CHECK(io.jetSounds.size() == after);                                                   // off is said once, not every frame
  }

  { // the jetpack sound is switched off when the puppet is released with it on, and when the ped goes away
    FakePed a({100, 200, 30});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushBodyState(1, kFlagJetpack);
    core.Tick(io);
    CHECK(io.jetSounds.back() == 1);
    io.ped = nullptr;
    core.Tick(io);
    CHECK(io.jetSounds.back() == 0);
  }

  { // another ped (a loaded game) never has the old ped's audio entity switched off: the sound is only forgotten
    FakePed a({100, 200, 30});
    FakePed b({100, 200, 30});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagJetpack);
    core.Tick(io);
    io.ped = b.mem;
    core.Tick(io);
    CHECK(io.jetSounds.back() == 0 && io.jetForgets == 1);
  }

  { // swimming: the swim block is asked for once, when the puppet is engaged; the pose is played and kept; the dive follows its time
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    core.Tick(io);
    CHECK(io.swimPrepared == 1);
    io.PushBodyState(2, kFlagInWater);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 1 && io.bodyAnims[0] == BodyAnim::Tread);
    core.Tick(io);
    CHECK(io.keeps.size() == 2 && io.keeps[0] == BodyAnim::Tread);
    io.PushBodyState(3, kFlagInWater | kFlagUnderwater);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 2 && io.bodyAnims[1] == BodyAnim::Dive);
    io.now += kDiveMs;
    io.PushBodyState(4, kFlagInWater | kFlagUnderwater);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 3 && io.bodyAnims[2] == BodyAnim::Under);
    io.PushBodyState(5, 0);
    core.Tick(io);
    CHECK(io.bodyClears == 1);
    CHECK(io.swimPrepared == 1);                                                       // never again for the same engagement
  }

  { // knocked down: lying, then getting up when the flag drops
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagKnocked);
    core.Tick(io);
    io.now += 2600;
    io.PushBodyState(3, 0);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 2 && io.bodyAnims[0] == BodyAnim::Down && io.bodyAnims[1] == BodyAnim::GetUp);
    io.now += 1600; // the get-up animation is over
    io.PushBodyState(4, 0);
    core.Tick(io);
    CHECK(io.bodyClears == 1);
  }

  { // knocked down by an explosion: on his back, getting up from his back
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagKnocked | kFlagBlasted);
    core.Tick(io);
    io.now += 2600;
    io.PushBodyState(3, 0);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 2 && io.bodyAnims[0] == BodyAnim::DownBack && io.bodyAnims[1] == BodyAnim::GetUpBack);
  }

  { // death: the animation and the death voice; the player back: cleared
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagDead);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 1 && io.bodyAnims[0] == BodyAnim::Dead);
    CHECK(io.pains.size() == 1 && io.pains[0] == kSayDeath);
    io.PushBodyState(3, kFlagDead);
    core.Tick(io);
    CHECK(io.bodyAnims.size() == 1 && io.pains.size() == 1);
    io.PushBodyState(4, 0);
    core.Tick(io);
    CHECK(io.bodyClears == 1);
  }

  { // a change of the hurt counter is a pain voice, with a cooldown
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 3u << kHurtShift);
    core.Tick(io);
    CHECK(io.pains.empty());
    io.PushBodyState(2, 4u << kHurtShift);
    core.Tick(io);
    CHECK(io.pains.size() == 1 && io.pains[0] == kSayPainLow);
    io.now += 100;
    io.PushBodyState(3, 5u << kHurtShift);
    core.Tick(io);
    CHECK(io.pains.size() == 1); // within the cooldown
    io.now += 2000;
    io.PushBodyState(4, 6u << kHurtShift);
    core.Tick(io);
    CHECK(io.pains.size() == 2);
  }

  { // releasing the puppet (a second without state) with an animation on takes it off, once; with none on it takes nothing off
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagKnocked);
    core.Tick(io);
    io.now += Puppet::kTimeoutMs + 1;
    core.Tick(io);
    CHECK(io.bodyClears == 1);
    FakePed ped2({100, 200, 30});
    FakeIo idle;
    idle.ped = ped2.mem;
    BridgeCore core2;
    idle.PushBodyState(1, 0);
    core2.Tick(idle);
    idle.now += Puppet::kTimeoutMs + 1;
    core2.Tick(idle);
    CHECK(idle.bodyClears == 0);
  }

  { // another ped (a loaded game) never gets the old ped's animation taken off, and the state starts again
    FakePed a({100, 200, 30});
    FakePed b({100, 200, 30});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagFalling);
    core.Tick(io);
    io.ped = b.mem; // the game was loaded again
    io.PushBodyState(3, kFlagFalling);
    core.Tick(io);
    CHECK(io.bodyClears == 0); // the old ped is not touched
    CHECK(io.bodyAnims.size() == 2 && io.bodyAnims[1] == BodyAnim::Fall); // and the new one, whose player is still falling, gets the animation
  }

  { // the ped disappears (loading screen) while an animation is held: the bridge forgets the held association, it never writes to it
    FakePed a({100, 200, 30});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushBodyState(1, 0);
    core.Tick(io);
    io.PushBodyState(2, kFlagFalling);
    core.Tick(io);
    io.ped = nullptr;
    core.Tick(io);
    CHECK(io.bodyClears == 0 && io.bodyForgets == 1);
  }

  { // a CJ in a vehicle is not animated
    FakePed ped({100, 200, 30}, /*inVehicle*/ true);
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushBodyState(1, kFlagFalling | kFlagDead);
    core.Tick(io);
    CHECK(io.bodyAnims.empty() && io.pains.empty());
  }

  { // shots: the clip going down is reported once per frame it changes, twice on the wire, with a growing counter
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.empty());
    io.ammo = 15; // two rounds went out this frame
    io.PushGunState(2, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.size() == 2); // sent twice
    CHECK(io.shots[0].shotSeq == 2 && io.shots[0].rounds == 2 && io.shots[0].weapon == 22);
    CHECK(std::memcmp(&io.shots[0], &io.shots[1], sizeof(ShotPacket)) == 0);
    io.PushGunState(3, 22, kIntentAim | kIntentFire);
    core.Tick(io); // the clip did not move
    CHECK(io.shots.size() == 2);
    io.ammo = 14;
    io.PushGunState(4, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.size() == 4 && io.shots[2].shotSeq == 3 && io.shots[2].rounds == 1);
  }

  { // Review Focus #2: no state for a second -> the puppet is released, and the weapon and task go away with it
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io); // gives the weapon
    io.PushGunState(2, 22, kIntentAim);
    core.Tick(io);
    io.now += Puppet::kTimeoutMs + 1;
    core.Tick(io);
    CHECK(io.taken == 1 && io.stops >= 1);
    CHECK(io.held == 0);
  }

  { // a Release that did not give anything does not take weapons the player owns
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 31; // from a script, say
    BridgeCore core;
    io.PushGunState(1, 0, 0);
    core.Tick(io);
    io.now += Puppet::kTimeoutMs + 1;
    core.Tick(io);
    CHECK(io.taken == 0 && io.held == 31);
  }

  { // a new game (another ped) is loaded: weapons are never taken from a ped the bridge did not arm
    FakePed a({100, 200, 30});
    FakePed b({500, 600, 10});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io); // gives the weapon to ped a
    io.held = 31;  // what ped b happens to hold
    io.ped = b.mem;
    core.Tick(io); // the ped changed: release
    CHECK(io.taken == 0 && io.held == 31);
  }

  { // after a release the driver starts clean: it does not remember it gave a weapon
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    io.now += Puppet::kTimeoutMs + 1;
    core.Tick(io); // released: weapon taken once
    CHECK(io.taken == 1);
    io.PushGunState(2, 0, 0); // engages again, unarmed
    core.Tick(io);
    core.Tick(io);
    CHECK(io.taken == 1);
  }

  { // the game's own gun task must be gone before the weapon is taken: a ped aiming with no weapon crashes the game
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    io.gunTaskActive = true; // the game keeps an aiming task alive
    io.PushGunState(2, 22, kIntentAim);
    core.Tick(io);
    io.PushGunState(3, 0, 0); // the item is dropped
    core.Tick(io);
    CHECK(io.stops >= 1 && io.taken == 0); // the task is told to end, the weapon stays for now
    core.Tick(io);
    CHECK(io.taken == 0); // still there
    io.gunTaskActive = false;
    core.Tick(io);
    CHECK(io.taken == 1 && io.held == 0); // gone: now the weapon is taken, once
    core.Tick(io);
    CHECK(io.taken == 1);
  }

  { // the same when the puppet expires: the weapon is taken after the task is gone, even though the bridge is no longer engaged
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    io.gunTaskActive = true;
    io.PushGunState(2, 22, kIntentAim);
    core.Tick(io);
    io.now += Puppet::kTimeoutMs + 1;
    core.Tick(io); // released
    CHECK(io.taken == 0 && io.held == 22);
    io.gunTaskActive = false;
    core.Tick(io);
    CHECK(io.taken == 1 && io.held == 0);
  }

  { // a task that never goes away does not keep the weapon forever
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    io.gunTaskActive = true;
    io.PushGunState(2, 0, 0);
    core.Tick(io);
    for (int i = 0; i < BridgeCore::kMaxTakeWaitFrames + 2; i++) {
      core.Tick(io);
    }
    CHECK(io.taken == 1);
  }

  { // a weapon waiting to be taken is never taken from another ped (a new game was loaded meanwhile)
    FakePed a({100, 200, 30});
    FakePed b({500, 600, 10});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    io.gunTaskActive = true;
    io.PushGunState(2, 0, 0); // dropped: the take waits for the task to go
    core.Tick(io);
    io.gunTaskActive = false;
    io.held = 31; // what ped b holds
    io.ped = b.mem;
    core.Tick(io);
    CHECK(io.taken == 0 && io.held == 31);
  }

  { // a blow with the bat in hand: the attack button with no aim button, no gun task, CJ turned towards the look direction
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 5;
    BridgeCore core;
    io.PushGunState(1, 5, kIntentMelee, 0, 0, 0); // no aim vector
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonCircle) == 1 && io.PadField(gta::kPadRightShoulder1) == 0);
    CHECK(io.gunCalls.empty());
    CHECK(io.facings.size() == 1 && std::fabs(io.facings[0].heading - 3.14159265f) < 1e-4f);
    io.PushGunState(2, 5, 0, 0, 0, 0); // let go
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonCircle) == 0);
  }

  { // a punch: nothing in hand, nothing given, same pad and facing
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 0, kIntentMelee, 0, 0, 0);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonCircle) == 1 && io.PadField(gta::kPadRightShoulder1) == 0);
    CHECK(io.given.empty() && io.gunCalls.empty());
    CHECK(io.facings.size() == 1);
    io.PushGunState(2, 0, 0, 0, 0, 0);
    core.Tick(io);
    std::memset(io.pad, 0, sizeof(io.pad)); // the game rewrites the pad every frame before our hook; the fake keeps its memory
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonCircle) == 0); // nothing in hand and no blow: the attack button is not pressed
  }

  { // aiming turns CJ in GTA towards where the player looks, but only after this frame's pose has been read
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentAim);
    core.Tick(io);
    CHECK(io.facings.size() == 1);
    CHECK(io.facings[0].posesSentBefore == io.poses.size()); // written after the pose of the same frame was sent
    CHECK(std::fabs(io.facings[0].heading - 3.14159265f) < 1e-4f); // yaw 0 in the test packet is south: heading pi
  }

  { // not aiming: GTA keeps its own heading
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    io.PushGunState(2, 0, 0);
    core.Tick(io);
    CHECK(io.facings.empty());
  }

  { // after the aim is let go the old heading is not applied again
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentAim);
    core.Tick(io);
    CHECK(io.facings.size() == 1);
    io.PushGunState(2, 22, 0);
    core.Tick(io);
    core.Tick(io);
    CHECK(io.facings.size() == 1);
  }

  { // Review Focus #1: the shot counter on the wire keeps growing across a weapon change (the mod ignores a counter that goes back)
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    io.held = 22;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    io.ammo = 15; // two rounds with the Colt
    io.PushGunState(2, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.back().shotSeq == 2);
    io.PushGunState(3, 30, kIntentAim | kIntentFire); // the player switched to the AK-47
    core.Tick(io);                                    // it is given: the clip baseline restarts
    io.ammo = 30;
    io.PushGunState(4, 30, kIntentAim | kIntentFire);
    core.Tick(io);
    io.ammo = 29; // one round with the AK
    io.PushGunState(5, 30, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.back().shotSeq == 3 && io.shots.back().weapon == 30);
  }

  { // the counter also keeps growing after the weapon is taken away and after the puppet is released and engaged again
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentAim | kIntentFire);
    core.Tick(io); // the bridge gives the Colt
    CHECK(io.held == 22);
    io.PushGunState(2, 22, kIntentAim | kIntentFire);
    core.Tick(io); // clip baseline: 17
    io.ammo = 15;
    io.PushGunState(3, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.back().shotSeq == 2);
    io.PushGunState(4, 0, 0); // dropped: the weapon the bridge gave is taken away
    core.Tick(io);
    CHECK(io.held == 0 && io.taken == 1);
    io.ammo = 17;
    io.PushGunState(5, 22, kIntentAim | kIntentFire); // picked up again: given
    core.Tick(io);
    io.PushGunState(6, 22, kIntentAim | kIntentFire);
    core.Tick(io); // baseline again
    io.ammo = 16;
    io.PushGunState(7, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.back().shotSeq == 3);
    io.now += Puppet::kTimeoutMs + 1;
    core.Tick(io); // released: the weapon goes
    CHECK(io.held == 0);
    io.ammo = 17;
    io.PushGunState(8, 22, kIntentAim | kIntentFire); // engaged again: given again
    core.Tick(io);
    io.PushGunState(9, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    io.ammo = 16;
    io.PushGunState(10, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.shots.back().shotSeq == 4);
  }

  { // I1: while a weapon is waiting to be taken, a new aim must not re-arm the game's task or press the aim button
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io); // the bridge gives the Colt
    io.gunTaskActive = true;
    io.PushGunState(2, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    io.PushGunState(3, 0, 0); // the player scrolls the hotbar past the weapon: the take waits for the task
    core.Tick(io);
    CHECK(io.taken == 0 && io.held == 22);
    const size_t gunCallsBefore = io.gunCalls.size();
    const int stopsBefore = io.stops;
    io.PushGunState(4, 22, kIntentAim | kIntentFire); // and back to the weapon, aiming, in the very next frame
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.gunCalls.size() == gunCallsBefore);                  // the task is not fed again
    CHECK(io.stops > stopsBefore);                                // and it is told to end again
    CHECK(io.PadField(gta::kPadRightShoulder1) == 0 && io.PadField(gta::kPadButtonCircle) == 0); // nor held alive by the pad
    CHECK(io.taken == 0);
    io.gunTaskActive = false; // the task is gone at last
    const size_t givenBefore = io.given.size();
    io.PushGunState(5, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    CHECK(io.taken == 1);                                        // the weapon is taken only now, with no task alive
    CHECK(io.given.size() == givenBefore + 1 && io.held == 22);  // and given again at once, cleanly
    io.PushGunState(6, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.gunCalls.size() > gunCallsBefore);                  // aiming works again
    CHECK(io.PadField(gta::kPadRightShoulder1) == 1);
  }

  { // M2: a take that is waiting does not survive the player ped disappearing (a new game loading)
    FakePed a({100, 200, 30});
    FakePed b({100, 200, 30}); // a new ped that may even sit at the same address in the real game; here a different one
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushGunState(1, 22, 0);
    core.Tick(io);
    io.gunTaskActive = true;
    io.PushGunState(2, 0, 0); // drop: the take is pending
    core.Tick(io);
    io.ped = nullptr;
    core.Tick(io); // loading screen: no ped
    io.ped = a.mem; // the same address comes back
    io.held = 31;   // holding something of the player's own
    io.gunTaskActive = false;
    core.Tick(io);
    CHECK(io.taken == 0 && io.held == 31);
  }

  { // the vehicle packets are not malformed datagrams; a broken one still is
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushVehicleCommand(1, kVehicleCmdNone);
    io.PushVehicleInput(2, 0);
    core.Tick(io);
    CHECK(!io.LogHas("rejected malformed"));
    io.inbox.push_back(std::vector<uint8_t>(10, 0));
    core.Tick(io);
    CHECK(io.LogHas("rejected malformed"));
  }

  { // getting into the car: the puppet is let go, the car is made and CJ seated, and the puppet is taken again; CJ is not released for being in a vehicle
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    CHECK(io.LogHas("puppet engaged"));
    io.logs.clear();
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter, 492, 90.0f);
    core.Tick(io);
    CHECK(io.LogHas("getting into the car"));
    CHECK(io.carsCreated == 1 && io.carModel == 492);
    CHECK((ped.PedFlags() & gta::kPedFlagInVehicle) != 0);
    CHECK(io.LogHas("puppet engaged"));
    CHECK(!io.LogHas("got into a vehicle"));
    CHECK(io.carStates.size() == 1 && io.carStates[0].flags == kVehicleFlagDriverIn);
  }

  { // in the car the puppet stays: states are accepted, the pose and the car's state keep going, nobody pulls CJ back to the stage
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    const size_t poses = io.poses.size();
    const size_t states = io.carStates.size();
    io.logs.clear();
    ped.SetPos({160, 230, 31}); // the car drove away with CJ in it
    io.PushState(3, 0, 64, 0);
    io.PushVehicleInput(2, 255);
    core.Tick(io);
    CHECK(Near(ped.GetPos(), {160, 230, 31}));
    CHECK(io.poses.size() == poses + 1);
    CHECK(io.carStates.size() == states + 1);
    CHECK(!io.LogHas("the game moved CJ") && !io.LogHas("got into a vehicle") && !io.LogHas("puppet released"));
    core.Tick(io); // a frame with nothing new: still there
    CHECK(Near(ped.GetPos(), {160, 230, 31}));
  }

  { // in the car nothing of a gun or a body reaction is asked of the game
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    const size_t guns = io.gunCalls.size();
    const size_t given = io.given.size();
    const size_t anims = io.bodyAnims.size();
    io.PushGunState(3, 22, kIntentAim | kIntentFire);
    core.Tick(io);
    io.PushBodyState(4, kFlagFalling);
    core.Tick(io);
    io.PushBodyState(5, kFlagDead);
    core.Tick(io);
    CHECK(io.gunCalls.size() == guns && io.given.size() == given && io.bodyAnims.size() == anims);
    CHECK(io.keeps.empty() && io.pains.empty());
  }

  { // in the car the states keep being taken (the echo carries the number of the last one)
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    io.PushState(7, 0, 64, 0);
    core.Tick(io);
    CHECK(io.echoes.back().ackSeq == 7);
    io.PushState(8, 0, 64, 0);
    io.PushVehicleInput(2, 100);
    core.Tick(io);
    CHECK(io.echoes.back().ackSeq == 8 && io.carStates.back().ackSeq == 2);
  }

  { // a vehicle packet of the wrong size is a malformed datagram
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushVehicleCommand(1, kVehicleCmdNone);
    io.inbox.back().push_back(0);
    core.Tick(io);
    CHECK(io.LogHas("rejected malformed"));
    io.logs.clear();
    for (int i = 0; i < 99; i++) { // the log only speaks every hundredth
      io.PushVehicleInput(2, 0);
      io.inbox.back().pop_back();
    }
    core.Tick(io);
    io.PushVehicleInput(3, 0);
    io.inbox.back().pop_back();
    core.Tick(io);
    CHECK(io.LogHas("rejected malformed"));
  }

  { // no car is made for CJ while he is in a vehicle of the game's own; walking-pad and animation-speed noise stay out of the car
    FakePed ped({100, 200, 30}, /*inVehicle*/ true);
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    CHECK(io.carsCreated == 0);
    CHECK(io.LogHas("already in a vehicle"));
  }

  { // in the car there is no walking-speed log, and the walking pad stays still even if the game has not flagged CJ as seated
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0, 3.0f, 0, 4.0f);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    ped.SetInVehicle(false);
    uint32_t seq = 3;
    uint32_t inputSeq = 2;
    for (int i = 0; i < 4; i++) {
      io.now += 700;
      io.PushState(seq++, 0, 64, 0, 3.0f, 0, 4.0f);
      io.PushVehicleInput(inputSeq++, 10);
      core.Tick(io);
      core.PrePad(io);
      CHECK(io.PadField(gta::kPadLeftStickY) == 0);
      CHECK(io.PadField(gta::kPadButtonCross) == 10);
    }
    CHECK(!io.LogHas("anim speed"));
  }

  { // the car was asked for and the ped vanished before any state: the car is forgotten, not driven when the ped comes back
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io); // no state yet: the car is made but the puppet is not engaged
    CHECK(io.carsCreated == 1);
    const size_t states = io.carStates.size();
    io.ped = nullptr;
    core.Tick(io);
    io.ped = ped.mem;
    core.Tick(io);
    CHECK(io.carStates.size() == states);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonCross) == 0);
  }

  { // the pad: in the car it carries the controls of the car, and the walking stick stays still
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0, 3.0f, 0, 4.0f); // moving fast on foot
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    io.PushState(3, 0, 64, 0, 3.0f, 0, 4.0f);
    io.PushVehicleInput(2, 200, -64);
    core.Tick(io);
    core.PrePad(io);
    CHECK(io.PadField(gta::kPadButtonCross) == 200);
    CHECK(io.PadField(gta::kPadLeftStickX) == -64);
    CHECK(io.PadField(gta::kPadLeftStickY) == 0);
    CHECK(io.PadField(gta::kPadPedWalk) == 0);
  }

  { // getting out: CJ stands beside the place of the car, the car is deleted, and the puppet is taken again where CJ stands now
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    io.logs.clear();
    io.PushState(3, 0, 64, 0);
    io.PushVehicleCommand(2, kVehicleCmdExit);
    core.Tick(io);
    CHECK(io.carExits == 1 && io.carsDeleted == 1);
    CHECK((ped.PedFlags() & gta::kPedFlagInVehicle) == 0);
    CHECK(io.LogHas("left the car"));
    const Vec3 outside = ped.GetPos();
    CHECK(outside.x < 100); // three metres to the left of where he sat
    io.logs.clear();
    io.PushState(4, 0, 64, 0);
    core.Tick(io);
    CHECK(io.LogHas("puppet engaged"));
    CHECK(!io.LogHas("the game moved CJ"));
    CHECK(Near(ped.GetPos(), outside)); // staged where he stands
    ped.SetPos({outside.x + 1, outside.y, outside.z});
    io.PushState(5, 0, 64, 0);
    core.Tick(io);
    CHECK(Near(ped.GetPos(), outside)); // and held there again
  }

  { // Minecraft goes quiet in the car: the car is left and deleted, once
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    io.now += 1500;
    core.Tick(io);
    CHECK(io.carExits == 1 && io.carsDeleted == 1);
    io.now += 1500;
    core.Tick(io);
    CHECK(io.carExits == 1 && io.carsDeleted == 1);
    CHECK((ped.PedFlags() & gta::kPedFlagInVehicle) == 0);
  }

  { // another ped (a new game) or none (a loading screen) while driving: the old car is forgotten and never touched
    FakePed a({100, 200, 30});
    FakePed b({500, 600, 30});
    FakeIo io;
    io.ped = a.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    io.ped = b.mem;
    core.Tick(io);
    CHECK(io.carExits == 0 && io.carsDeleted == 0);
    CHECK(io.LogHas("the player ped changed"));

    FakeIo io2;
    io2.ped = a.mem;
    a.SetInVehicle(false);
    BridgeCore core2;
    io2.PushState(1, 0, 64, 0);
    core2.Tick(io2);
    io2.PushState(2, 0, 64, 0);
    io2.PushVehicleCommand(1, kVehicleCmdEnter);
    core2.Tick(io2);
    io2.ped = nullptr;
    core2.Tick(io2);
    CHECK(io2.carExits == 0 && io2.carsDeleted == 0);
  }

  { // the car vanishes while CJ is at the wheel (GTA took him out): the bits the bridge gave him are taken back, every frame he is not driving
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    io.PushState(2, 0, 64, 0);
    io.PushVehicleCommand(1, kVehicleCmdEnter);
    core.Tick(io);
    const int whileDriving = io.pedRestores;
    io.carReadable = false;
    io.PushState(3, 0, 64, 0);
    core.Tick(io);
    io.PushState(4, 0, 64, 0);
    core.Tick(io);
    CHECK(io.pedRestores > whileDriving);
  }

  { // a player who eats makes CJ play the animation of that food, once, and not while the car, the water or a climb have his body
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    CHECK(io.eatStarts == 0);
    io.PushState(2, 0, 64, 0, 0, 0, 0, kFlagOnGround | kFlagEating | (static_cast<uint32_t>(kEatChicken) << kEatKindShift));
    core.Tick(io);
    CHECK(io.eatStarts == 1 && io.eatKind == kEatChicken);
    io.PushState(3, 0, 64, 0, 0, 0, 0, kFlagOnGround | kFlagEating | (static_cast<uint32_t>(kEatChicken) << kEatKindShift));
    core.Tick(io);
    CHECK(io.eatStarts == 1); // still playing

    FakePed swimmer({100, 200, 30});
    FakeIo io2;
    io2.ped = swimmer.mem;
    BridgeCore core2;
    io2.PushState(1, 0, 64, 0);
    core2.Tick(io2);
    io2.PushState(2, 0, 64, 0, 0, 0, 0, kFlagInWater | kFlagEating);
    core2.Tick(io2);
    CHECK(io2.eatStarts == 0);
  }

  { // a vehicle of the game's own making still lets go of the puppet, as before
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushState(1, 0, 64, 0);
    core.Tick(io);
    ped.SetInVehicle(true);
    io.PushState(2, 0, 64, 0);
    core.Tick(io);
    CHECK(io.LogHas("CJ got into a vehicle"));
    CHECK(io.carsCreated == 0);
  }

  { // the dry-fire intent: GTA's own click of a weapon that cannot fire sounds once per press, not while the button stays down
    FakePed ped({100, 200, 30});
    FakeIo io;
    io.ped = ped.mem;
    BridgeCore core;
    io.PushGunState(1, 22, kIntentAim);
    core.Tick(io);
    CHECK(io.emptyClicks == 0);
    io.PushGunState(2, 22, kIntentAim | kIntentDry);
    core.Tick(io);
    io.PushGunState(3, 22, kIntentAim | kIntentDry);
    core.Tick(io);
    CHECK(io.emptyClicks == 1);
    io.PushGunState(4, 22, kIntentAim);
    core.Tick(io);
    io.PushGunState(5, 22, kIntentDry);
    core.Tick(io);
    CHECK(io.emptyClicks == 2);
  }

  std::puts("test_bridge_core OK");
  return 0;
}
