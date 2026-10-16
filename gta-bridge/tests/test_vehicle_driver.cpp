#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "vehicle_driver.h"

#define CHECK(cond)                                                          \
  do {                                                                       \
    if (!(cond)) {                                                           \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__,  \
                   #cond);                                                   \
      std::exit(1);                                                          \
    }                                                                        \
  } while (0)

using namespace cj;

struct FakeVehicleIo : VehicleIo {
  uint8_t carMemory[16]{};
  uint8_t *carToCreate = carMemory; // nullptr: the game could not create it
  bool seatOk = true;
  bool readOk = true;
  std::vector<std::string> calls; // in order
  std::vector<std::string> logs;
  int created = 0;
  int model = 0;
  float heading = 0;
  uint8_t *seatedPed = nullptr;
  uint8_t *exitedPed = nullptr;
  float exitSide = 0;
  uint8_t *deleted = nullptr;
  float corrDx = 0, corrDy = 0;
  int corrections = 0;
  std::vector<VehicleStatePacket> states;
  VehicleStatePacket reading{};

  uint8_t *CreateVehicle(uint8_t *, int m, float h) override {
    calls.push_back("create");
    created++;
    model = m;
    heading = h;
    return carToCreate;
  }
  bool SeatDriver(uint8_t *ped, uint8_t *) override {
    calls.push_back("seat");
    seatedPed = ped;
    return seatOk;
  }
  void MakeVehicleSafe(uint8_t *, uint8_t *) override { calls.push_back("safe"); }
  bool ReadVehicle(uint8_t *, uint8_t *, VehicleStatePacket &out) override {
    calls.push_back("read");
    out = reading;
    return readOk;
  }
  void CorrectVehicle(uint8_t *, float dx, float dy) override {
    calls.push_back("correct");
    corrDx = dx;
    corrDy = dy;
    corrections++;
  }
  int station = -1;      // what the game says is tuned
  int retuned = -1;      // the last station the driver asked for
  int retuneCalls = 0;
  int RadioStation() override { return station; }
  void RetuneRadio(int s) override {
    calls.push_back("retune");
    retuned = s;
    retuneCalls++;
    // the game is slow: it keeps saying the old one (the test moves `station` by hand)
  }
  bool floorReady = true;
  bool FloorReady() override {
    calls.push_back("floor");
    return floorReady;
  }
  float shiftDx = 0, shiftDy = 0;
  int shifts = 0;
  VehicleStatePacket afterShift{}; // what the next read gives after a shift
  bool hasAfterShift = false;
  void ShiftVehicle(uint8_t *, float dx, float dy) override {
    calls.push_back("shift");
    shiftDx = dx;
    shiftDy = dy;
    shifts++;
    if (hasAfterShift)
      reading = afterShift;
  }
  void ExitDriver(uint8_t *ped, uint8_t *, float side) override {
    calls.push_back("exit");
    exitedPed = ped;
    exitSide = side;
  }
  void DeleteVehicle(uint8_t *v) override {
    calls.push_back("delete");
    deleted = v;
  }
  void SendVehicleState(const VehicleStatePacket &s) override {
    calls.push_back("send");
    states.push_back(s);
  }
  void Log(const char *line) override { logs.emplace_back(line); }
  bool LogHas(const char *needle) {
    for (const auto &l : logs)
      if (l.find(needle) != std::string::npos)
        return true;
    return false;
  }
  int Count(const char *name) {
    int n = 0;
    for (const auto &c : calls)
      if (c == name)
        n++;
    return n;
  }
};

static VehicleCommandPacket Command(uint32_t seq, uint16_t command, uint16_t model = 492, float yaw = 0) {
  VehicleCommandPacket c{};
  c.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::VehicleCommand)};
  c.seq = seq;
  c.command = command;
  c.model = model;
  c.yaw = yaw;
  return c;
}

static VehicleInputPacket Input(uint32_t seq, uint16_t throttle = 0, uint16_t brake = 0, int16_t steer = 0, uint16_t handbrake = 0) {
  VehicleInputPacket i{};
  i.h = {kMagic, kVersion, static_cast<uint16_t>(PacketType::VehicleInput)};
  i.seq = seq;
  i.throttle = throttle;
  i.brake = brake;
  i.steer = steer;
  i.handbrake = handbrake;
  return i;
}

static void Enter(VehicleDriver &d, FakeVehicleIo &io, uint8_t *ped, uint32_t now, uint32_t seq = 1, float yaw = 0) {
  d.OnCommand(Command(seq, kVehicleCmdEnter, 492, yaw), now);
  d.Update(io, ped, false, now);
}

static void ReviewTests(uint8_t *ped) {
  { // the car is gone when it is time to get out (a save was loaded and the same ped address came back): nothing is exited or deleted
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.calls.clear();
    io.readOk = false;
    d.OnCommand(Command(2, kVehicleCmdExit), 120);
    d.Update(io, ped, true, 120);
    CHECK(io.Count("exit") == 0 && io.Count("delete") == 0);
    CHECK(!d.Active());
  }
  { // the same when it is the watchdog that asks
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.calls.clear();
    io.readOk = false;
    d.Update(io, ped, true, 2000);
    CHECK(io.Count("exit") == 0 && io.Count("delete") == 0);
    CHECK(!d.Active());
  }
  { // no floor in the sky yet: no car is made, and the next key press (when it is there) makes it
    FakeVehicleIo io;
    VehicleDriver d;
    io.floorReady = false;
    Enter(d, io, ped, 100, 1);
    CHECK(io.created == 0 && !d.Active() && io.LogHas("floor"));
    io.floorReady = true;
    Enter(d, io, ped, 200, 2);
    CHECK(io.created == 1 && d.Active());
  }
  { // Minecraft was closed and opened again while GTA ran: its commands start again from a small number and are not taken for old ones
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 50000);
    d.Update(io, ped, true, 5000); // no sign of life: the car is left
    CHECK(!d.Active());
    io.calls.clear();
    Enter(d, io, ped, 6000, 0);
    CHECK(d.Active() && io.created == 2);
  }
}

static void RadioTests(uint8_t *ped) {
  { // next and previous move the dial from what the game says is tuned
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = 4;
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.Update(io, ped, true, 110);
    CHECK(io.retuneCalls == 1 && io.retuned == 5);
    d.OnCommand(Command(3, kVehicleCmdRadioPrevious), 120);
    io.station = 5;
    d.Update(io, ped, true, 120);
    CHECK(io.retuneCalls == 2 && io.retuned == 4);
  }
  { // the game is slow to say it: a second press goes on from the station asked for, not from the old one
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = 4;
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.Update(io, ped, true, 110);
    d.OnCommand(Command(3, kVehicleCmdRadioNext), 120);
    d.Update(io, ped, true, 120); // the game still says 4
    CHECK(io.retuned == 6);
  }
  { // presses in the same frame add up and tune once
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = 4;
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.OnCommand(Command(3, kVehicleCmdRadioNext), 110);
    d.OnCommand(Command(4, kVehicleCmdRadioPrevious), 110);
    d.OnCommand(Command(5, kVehicleCmdRadioNext), 110);
    d.Update(io, ped, true, 110);
    CHECK(io.retuneCalls == 1 && io.retuned == 6);
  }
  { // opposite presses that cancel leave the radio alone
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = 4;
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.OnCommand(Command(3, kVehicleCmdRadioPrevious), 110);
    d.Update(io, ped, true, 110);
    CHECK(io.retuneCalls == 0);
  }
  { // not at the wheel there is no radio to tune
    FakeVehicleIo io;
    VehicleDriver d;
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.Update(io, ped, false, 110);
    CHECK(io.retuneCalls == 0 && !d.Active());
    // and the press does not wait for the next car
    Enter(d, io, ped, 200, 3);
    CHECK(io.retuneCalls == 0);
  }
  { // an old repeated command does nothing twice
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = 4;
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 111);
    d.Update(io, ped, true, 111);
    CHECK(io.retuned == 5);
  }
  { // the state tells the station: the one asked for until the game says it, then the game's
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = 4;
    d.Update(io, ped, true, 105);
    CHECK(StationOfFlags(io.states.back().flags) == 4);
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.Update(io, ped, true, 110);
    CHECK(StationOfFlags(io.states.back().flags) == 5); // the game still says 4
    io.station = 5;
    d.Update(io, ped, true, 120);
    CHECK(StationOfFlags(io.states.back().flags) == 5);
    io.station = 9; // GTA's own keys changed it
    d.Update(io, ped, true, 130);
    CHECK(StationOfFlags(io.states.back().flags) == 9);
  }
  { // an unknown station is told as unknown, and the other flags are kept
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = -1;
    io.reading.flags = kVehicleFlagDriverIn;
    d.Update(io, ped, true, 110);
    CHECK(StationOfFlags(io.states.back().flags) == -1);
    CHECK((io.states.back().flags & kVehicleFlagDriverIn) != 0);
  }
  { // a new ride does not remember the last station asked for
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1);
    io.station = 4;
    d.OnCommand(Command(2, kVehicleCmdRadioNext), 110);
    d.Update(io, ped, true, 110);
    d.OnCommand(Command(3, kVehicleCmdExit), 120);
    d.Update(io, ped, true, 120);
    CHECK(!d.Active());
    io.station = 8;
    Enter(d, io, ped, 200, 4);
    CHECK(StationOfFlags(io.states.back().flags) == 8);
  }
}

static void ArenaTests(uint8_t *ped) {
  { // inside the arena nothing moves; the edge itself is still inside
    FakeVehicleIo io;
    VehicleDriver d;
    d.SetArena(100.0f, -200.0f, 50.0f);
    io.reading.pos[0] = 150.0f;
    io.reading.pos[1] = -250.0f;
    Enter(d, io, ped, 100, 1, 0.0f);
    d.Update(io, ped, true, 120);
    CHECK(io.shifts == 0);
  }
  { // outside: the car goes back to the middle, keeping its way, and what Minecraft is told is the state after the move
    FakeVehicleIo io;
    VehicleDriver d;
    d.SetArena(100.0f, -200.0f, 50.0f);
    Enter(d, io, ped, 100, 1, 0.0f);
    io.calls.clear();
    io.states.clear();
    io.reading.pos[0] = 160.0f;
    io.reading.pos[1] = -190.0f;
    io.afterShift = io.reading;
    io.afterShift.pos[0] = 100.0f;
    io.afterShift.pos[1] = -200.0f;
    io.hasAfterShift = true;
    d.Update(io, ped, true, 120);
    CHECK((io.calls == std::vector<std::string>{"read", "shift", "read", "send"}));
    CHECK(std::fabs(io.shiftDx - (-60.0f)) < 1e-4f && std::fabs(io.shiftDy - (-10.0f)) < 1e-4f);
    CHECK(io.states.size() == 1 && io.states[0].pos[0] == 100.0f && io.states[0].pos[1] == -200.0f);
  }
  { // either axis counts
    FakeVehicleIo io;
    VehicleDriver d;
    d.SetArena(0.0f, 0.0f, 10.0f);
    Enter(d, io, ped, 100, 1, 0.0f);
    io.reading.pos[0] = 0.0f;
    io.reading.pos[1] = -10.5f;
    d.Update(io, ped, true, 120);
    CHECK(io.shifts == 1 && std::fabs(io.shiftDy - 10.5f) < 1e-4f);
  }
  { // without an arena (the default) the car is never moved
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 100, 1, 0.0f);
    io.reading.pos[0] = 1e6f;
    d.Update(io, ped, true, 120);
    CHECK(io.shifts == 0);
  }
}

int main() {
  uint8_t pedMemory[8]{};
  uint8_t *ped = pedMemory;
  uint8_t otherPedMemory[8]{};
  ArenaTests(ped);
  RadioTests(ped);
  ReviewTests(ped);

  { // getting in: the car is created with the model and the heading asked for, CJ is seated, the car is made safe, and it is the mode of driving
    FakeVehicleIo io;
    VehicleDriver d;
    CHECK(!d.Active());
    Enter(d, io, ped, 100, 1, 90.0f); // yaw 90 faces west: GTA heading pi/2
    CHECK((io.calls == std::vector<std::string>{"floor", "create", "seat", "safe", "read", "send"}));
    CHECK(io.model == 492);
    CHECK(std::fabs(io.heading - 1.5707963f) < 1e-5f);
    CHECK(io.seatedPed == ped);
    CHECK(d.Active());
  }

  { // the heading is the Minecraft yaw turned into GTA's: yaw 0 faces south (heading pi), yaw 180 north (0), yaw -90 east (3 pi / 2)
    const float pi = 3.14159265f;
    const float cases[][2] = {{0.0f, pi}, {180.0f, 0.0f}, {-90.0f, 1.5f * pi}, {90.0f, 0.5f * pi}, {270.0f, 1.5f * pi}, {-180.0f, 0.0f}};
    for (const auto &c : cases) {
      FakeVehicleIo io;
      VehicleDriver d;
      Enter(d, io, ped, 0, 1, c[0]);
      CHECK(io.heading >= 0.0f && io.heading < 2 * pi);
      CHECK(std::fabs(io.heading - c[1]) < 1e-4f || std::fabs(std::fabs(io.heading - c[1]) - 2 * pi) < 1e-4f);
    }
  }

  { // a second Enter while driving makes no second car, and neither does a repeated or an older command
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    d.OnCommand(Command(2, kVehicleCmdEnter), 10);
    d.Update(io, ped, false, 10);
    CHECK(io.created == 1);
    d.OnCommand(Command(3, kVehicleCmdExit), 20);
    d.Update(io, ped, false, 20);
    CHECK(!d.Active());
    d.OnCommand(Command(2, kVehicleCmdEnter), 30); // an old Enter arriving late
    d.Update(io, ped, false, 30);
    CHECK(io.created == 1 && !d.Active());
    d.OnCommand(Command(3, kVehicleCmdExit), 40);
    d.OnCommand(Command(3, kVehicleCmdEnter), 40); // the same number again
    d.Update(io, ped, false, 40);
    CHECK(io.created == 1);
    d.OnCommand(Command(4, kVehicleCmdEnter), 50); // a new one does
    d.Update(io, ped, false, 50);
    CHECK(io.created == 2 && d.Active());
  }

  { // a sequence number that wrapped round is newer than the one before it
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0, 0xFFFFFFFFu);
    d.OnCommand(Command(0, kVehicleCmdExit), 5); // 0 comes after 0xFFFFFFFF
    d.Update(io, ped, false, 5);
    CHECK(!d.Active());
  }

  { // the model must be one of GTA's vehicles; a car that cannot be created, or seated, leaves nothing behind
    FakeVehicleIo io;
    VehicleDriver d;
    for (uint16_t bad : {0, 399, 612, 0xFFFF}) {
      d.OnCommand(Command(bad + 1u, kVehicleCmdEnter, bad), 0);
      d.Update(io, ped, false, 0);
      CHECK(!d.Active() && io.created == 0);
    }
    d.OnCommand(Command(70000, kVehicleCmdEnter, 400), 0);
    d.Update(io, ped, false, 0);
    CHECK(d.Active() && io.created == 1);
  }
  { // the edges of the range
    FakeVehicleIo io;
    VehicleDriver d;
    d.OnCommand(Command(1, kVehicleCmdEnter, 611), 0);
    d.Update(io, ped, false, 0);
    CHECK(d.Active());
  }

  { // no car could be created: nothing else is called and it is told in the log
    FakeVehicleIo io;
    io.carToCreate = nullptr;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    CHECK(!d.Active());
    CHECK((io.calls == std::vector<std::string>{"floor", "create"}));
    CHECK(io.LogHas("could not create"));
  }

  { // CJ could not be seated: the car just made is deleted
    FakeVehicleIo io;
    io.seatOk = false;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    CHECK(!d.Active());
    CHECK((io.calls == std::vector<std::string>{"floor", "create", "seat", "delete"}));
    CHECK(io.deleted == io.carMemory);
    CHECK(io.LogHas("could not seat"));
  }

  { // CJ is already in a vehicle of the game: no car is made
    FakeVehicleIo io;
    VehicleDriver d;
    d.OnCommand(Command(1, kVehicleCmdEnter), 0);
    d.Update(io, ped, true, 0);
    CHECK(!d.Active() && io.created == 0);
    CHECK(io.LogHas("already in a vehicle"));
  }

  { // no ped: nothing is called and a pending Enter is forgotten
    FakeVehicleIo io;
    VehicleDriver d;
    d.OnCommand(Command(1, kVehicleCmdEnter), 0);
    d.Update(io, nullptr, false, 0);
    d.Update(io, ped, false, 1);
    CHECK(io.calls.empty() && !d.Active());
  }

  { // driving: every frame the car is read and its state is sent with the sequence of the last input, and the controls are what the inputs say
    FakeVehicleIo io;
    io.reading.pos[0] = 1514.5f;
    io.reading.speed = 7.0f;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    io.calls.clear();
    io.states.clear();
    d.OnInput(Input(5, 200, 30, -64, 255), 16);
    d.Update(io, ped, false, 33);
    CHECK((io.calls == std::vector<std::string>{"read", "send"}));
    CHECK(io.states.size() == 1);
    CHECK(io.states[0].ackSeq == 5 && io.states[0].gtaTimeMs == 33);
    CHECK(io.states[0].pos[0] == 1514.5f && io.states[0].speed == 7.0f);
    CHECK(io.states[0].h.magic == kMagic && io.states[0].h.version == kVersion && io.states[0].h.type == static_cast<uint16_t>(PacketType::VehicleState));
    const VehicleControls c = d.Controls();
    CHECK(c.throttle == 200 && c.brake == 30 && c.steer == -64 && c.handbrake == 255);
  }

  { // the controls are held while driving and are zero when not; out of range values are brought into range
    FakeVehicleIo io;
    VehicleDriver d;
    CHECK(d.Controls().throttle == 0 && d.Controls().steer == 0);
    d.OnInput(Input(1, 255, 0, 100), 0); // not driving: ignored
    CHECK(d.Controls().throttle == 0);
    Enter(d, io, ped, 0);
    d.OnInput(Input(1, 999, 300, 500, 7), 1);
    VehicleControls c = d.Controls();
    CHECK(c.throttle == 255 && c.brake == 255 && c.steer == 127 && c.handbrake == 255);
    d.OnInput(Input(2, 0, 0, -500, 0), 2);
    c = d.Controls();
    CHECK(c.throttle == 0 && c.steer == -127 && c.handbrake == 0);
  }

  { // an older or repeated input does not replace the newer one; a wrapped number is newer
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    d.OnInput(Input(10, 100), 1);
    d.OnInput(Input(9, 200), 2);
    CHECK(d.Controls().throttle == 100);
    d.OnInput(Input(10, 50), 3);
    CHECK(d.Controls().throttle == 100);
    d.OnInput(Input(11, 150), 4);
    CHECK(d.Controls().throttle == 150);
    d.OnInput(Input(0xFFFFFFF0u, 20), 5); // far behind: old
    CHECK(d.Controls().throttle == 150);
    VehicleDriver w;
    FakeVehicleIo io2;
    Enter(w, io2, ped, 0);
    w.OnInput(Input(0xFFFFFFFFu, 60), 1);
    w.OnInput(Input(0, 70), 2);
    CHECK(w.Controls().throttle == 70);
  }

  { // a correction is applied once per new input, before the car is read
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    io.calls.clear();
    VehicleInputPacket in = Input(1);
    in.corrFlags = kVehicleCorrPresent;
    in.corrX = 0.5f;
    in.corrY = -0.25f;
    d.OnInput(in, 1);
    d.Update(io, ped, false, 2);
    CHECK((io.calls == std::vector<std::string>{"correct", "read", "send"}));
    CHECK(io.corrDx == 0.5f && io.corrDy == -0.25f);
    d.Update(io, ped, false, 3); // no new packet: not again
    CHECK(io.corrections == 1);
    d.OnInput(in, 4); // the same packet again (same seq): not again
    d.Update(io, ped, false, 5);
    CHECK(io.corrections == 1);
    in.seq = 2;
    d.OnInput(in, 6);
    d.Update(io, ped, false, 7);
    CHECK(io.corrections == 2);
    VehicleInputPacket none = Input(3); // no flag: the numbers mean nothing
    none.corrX = 9;
    d.OnInput(none, 8);
    d.Update(io, ped, false, 9);
    CHECK(io.corrections == 2);
    VehicleInputPacket bad = Input(4);
    bad.corrFlags = kVehicleCorrPresent;
    bad.corrX = NAN;
    d.OnInput(bad, 10);
    d.Update(io, ped, false, 11);
    VehicleInputPacket huge = Input(5);
    huge.corrFlags = kVehicleCorrPresent;
    huge.corrY = 1e9f;
    d.OnInput(huge, 12);
    d.Update(io, ped, false, 13);
    CHECK(io.corrections == 2); // a broken or absurd correction is not applied
    VehicleInputPacket edge = Input(6);
    edge.corrFlags = kVehicleCorrPresent;
    edge.corrX = -20.0f;
    d.OnInput(edge, 14);
    d.Update(io, ped, false, 15);
    CHECK(io.corrections == 3 && io.corrDx == -20.0f); // the limit itself is allowed
    VehicleInputPacket nanY = Input(8);
    nanY.corrFlags = kVehicleCorrPresent;
    nanY.corrY = NAN;
    d.OnInput(nanY, 18);
    d.Update(io, ped, false, 19);
    VehicleInputPacket infX = Input(9);
    infX.corrFlags = kVehicleCorrPresent;
    infX.corrX = INFINITY;
    d.OnInput(infX, 20);
    d.Update(io, ped, false, 21);
    VehicleInputPacket bigX = Input(10);
    bigX.corrFlags = kVehicleCorrPresent;
    bigX.corrX = 1e9f;
    d.OnInput(bigX, 22);
    d.Update(io, ped, false, 23);
    CHECK(io.corrections == 3);
    VehicleInputPacket edgeY = Input(11);
    edgeY.corrFlags = kVehicleCorrPresent;
    edgeY.corrY = 20.0f;
    d.OnInput(edgeY, 24);
    d.Update(io, ped, false, 25);
    CHECK(io.corrections == 4 && io.corrDy == 20.0f);
    VehicleInputPacket over = Input(12);
    over.corrFlags = kVehicleCorrPresent;
    over.corrY = 20.5f;
    d.OnInput(over, 16);
    d.Update(io, ped, false, 17);
    CHECK(io.corrections == 4);
  }

  { // getting out: CJ exits to the left and then the car is deleted, in that order, and nothing more is sent
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    io.calls.clear();
    d.OnCommand(Command(2, kVehicleCmdExit), 10);
    d.Update(io, ped, false, 10);
    CHECK((io.calls == std::vector<std::string>{"read", "exit", "delete"}));
    CHECK(io.exitedPed == ped && io.exitSide == -3.0f && io.deleted == io.carMemory);
    CHECK(!d.Active());
    io.calls.clear();
    d.OnCommand(Command(3, kVehicleCmdExit), 20); // out while free: nothing
    d.Update(io, ped, false, 20);
    d.Update(io, ped, false, 21);
    CHECK(io.calls.empty());
  }

  { // the watchdog: no input and no command for 1000 ms and the car is left and deleted; a bit less is fine
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 5000);
    d.Update(io, ped, false, 6000);
    CHECK(d.Active()); // exactly 1000 ms
    d.OnInput(Input(1), 6000);
    d.Update(io, ped, false, 6999);
    CHECK(d.Active());
    d.OnCommand(Command(9, kVehicleCmdNone), 7000); // any command is a sign of life
    d.Update(io, ped, false, 7999);
    CHECK(d.Active());
    io.calls.clear();
    d.Update(io, ped, false, 8001);
    CHECK((io.calls == std::vector<std::string>{"read", "exit", "delete"}));
    CHECK(!d.Active());
    CHECK(io.LogHas("no input"));
  }

  { // a clock that goes backwards does not leave it hanging for ever: the difference is unsigned, so the car is left
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 5000);
    d.Update(io, ped, false, 4000);
    CHECK(!d.Active() && io.Count("delete") == 1);
  }

  { // a correction that was waiting when the ride ended is not applied to the next car
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    VehicleInputPacket in = Input(1);
    in.corrFlags = kVehicleCorrPresent;
    in.corrX = 1.0f;
    d.OnInput(in, 1);
    d.Forget();
    d.OnCommand(Command(5, kVehicleCmdEnter), 2);
    d.Update(io, ped, false, 2);
    CHECK(d.Active());
    CHECK(io.corrections == 0);
  }

  { // Forget (the ped is gone) touches nothing, and a new ride starts clean
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    d.OnInput(Input(1, 255, 0, 100), 1);
    io.calls.clear();
    d.Forget();
    CHECK(io.calls.empty() && !d.Active());
    CHECK(d.Controls().throttle == 0 && d.Controls().steer == 0);
    d.OnCommand(Command(5, kVehicleCmdEnter), 2);
    d.Update(io, ped, false, 2);
    CHECK(d.Active());
    d.OnInput(Input(1, 10), 3); // the sequence of the last ride does not block this one
    CHECK(d.Controls().throttle == 10);
  }

  { // the ped changed under the driver (another game was loaded): the old car is forgotten, never touched
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    io.calls.clear();
    d.Update(io, otherPedMemory, false, 5);
    CHECK(io.calls.empty() && !d.Active());
  }

  { // the car vanished (the game removed it, or CJ is no longer its driver): back to free, nothing deleted twice
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    io.readOk = false;
    io.calls.clear();
    d.Update(io, ped, false, 5);
    CHECK((io.calls == std::vector<std::string>{"read"}) && !d.Active());
    CHECK(io.LogHas("gone"));
  }

  { // once free, the controls are zero again and a new ride starts clean
    FakeVehicleIo io;
    VehicleDriver d;
    Enter(d, io, ped, 0);
    d.OnInput(Input(1, 255, 0, 100), 1);
    d.OnCommand(Command(2, kVehicleCmdExit), 2);
    d.Update(io, ped, false, 2);
    CHECK(d.Controls().throttle == 0 && d.Controls().steer == 0);
    d.OnCommand(Command(3, kVehicleCmdEnter), 3);
    d.Update(io, ped, false, 3);
    CHECK(d.Active() && d.Controls().throttle == 0);
    d.OnInput(Input(1, 10), 4); // the sequence of the last ride does not block this one
    CHECK(d.Controls().throttle == 10);
  }

  { // EnterPending tells the core to let go of the puppet before the car is made
    VehicleDriver d;
    CHECK(!d.EnterPending());
    d.OnCommand(Command(1, kVehicleCmdEnter), 0);
    CHECK(d.EnterPending());
    FakeVehicleIo io;
    d.Update(io, ped, false, 0);
    CHECK(!d.EnterPending());
    d.OnCommand(Command(2, kVehicleCmdEnter), 1); // already driving: nothing to wait for
    CHECK(!d.EnterPending());
  }

  { // the controls go into the pad: the stick turns, Cross accelerates, Square brakes (and reverses), the shoulder button is the handbrake
    alignas(8) uint8_t pad[gta::kPadSize]{};
    auto get = [&pad](size_t offset) {
      int16_t v;
      std::memcpy(&v, pad + offset, 2);
      return v;
    };
    const int16_t seven = 7;
    std::memcpy(pad + gta::kPadLeftStickY, &seven, 2); // fields of the pad that are not the car's stay as they were
    std::memcpy(pad + gta::kPadPedWalk, &seven, 2);
    std::memcpy(pad + gta::kPadButtonCircle, &seven, 2);
    VehicleControls c;
    c.throttle = 200;
    c.brake = 30;
    c.steer = -64;
    c.handbrake = 255;
    ApplyVehiclePad(pad, c);
    CHECK(get(gta::kPadLeftStickX) == -64);
    CHECK(get(gta::kPadButtonCross) == 200);
    CHECK(get(gta::kPadButtonSquare) == 30);
    CHECK(get(gta::kPadRightShoulder1) == 255);
    CHECK(get(gta::kPadLeftStickY) == 7 && get(gta::kPadPedWalk) == 7 && get(gta::kPadButtonCircle) == 7);
    ApplyVehiclePad(pad, VehicleControls{});
    CHECK(get(gta::kPadLeftStickX) == 0 && get(gta::kPadButtonCross) == 0 && get(gta::kPadButtonSquare) == 0 && get(gta::kPadRightShoulder1) == 0);
  }

  std::puts("test_vehicle_driver OK");
  return 0;
}
