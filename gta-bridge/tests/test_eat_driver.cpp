#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <vector>

#include "eat_driver.h"

#define CHECK(cond)                                                          \
  do {                                                                       \
    if (!(cond)) {                                                           \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__,  \
                   #cond);                                                   \
      std::exit(1);                                                          \
    }                                                                        \
  } while (0)

using namespace cj;

struct FakeEatIo : EatIo {
  bool running = false;
  bool startOk = true;
  int starts = 0;
  int lastKind = -1;
  bool EatTaskRunning(uint8_t *) override { return running; }
  bool StartEating(uint8_t *, int kind) override {
    starts++;
    lastKind = kind;
    if (startOk) running = true;
    return startOk;
  }
};

int main() {
  uint8_t pedMem[4]{};
  uint8_t *ped = pedMem;

  { // the flags: eating and the kind next to it
    CHECK(kFlagEating == (1u << 19));
    CHECK(EatKindOfFlags(kFlagEating | (2u << kEatKindShift)) == kEatPizza);
    CHECK(EatKindOfFlags(kFlagEating) == kEatBurger);
    CHECK(EatKindOfFlags(kFlagEating | (1u << kEatKindShift)) == kEatChicken);
    CHECK(EatKindOfFlags(2u << kEatKindShift) == -1); // a kind without the flag
    CHECK(EatKindOfFlags(kFlagOnGround | kFlagSprint) == -1);
    CHECK(EatKindOfFlags(kFlagEating | (3u << kEatKindShift)) == -1); // a kind that does not exist: nothing is played
  }
  { // the animation starts when the player starts eating, with the kind, once
    FakeEatIo io;
    EatDriver d;
    d.Update(io, ped, false, 0, true, 100);
    CHECK(io.starts == 0);
    d.Update(io, ped, true, kEatChicken, true, 110);
    CHECK(io.starts == 1 && io.lastKind == kEatChicken);
    d.Update(io, ped, true, kEatChicken, true, 120); // still playing
    CHECK(io.starts == 1);
  }
  { // if it ends before the player has finished eating, it plays again
    FakeEatIo io;
    EatDriver d;
    d.Update(io, ped, true, kEatBurger, true, 100);
    io.running = false;
    d.Update(io, ped, true, kEatBurger, true, 110);
    CHECK(io.starts == 2);
  }
  { // when he stops eating nothing new starts, and a finished one is not repeated
    FakeEatIo io;
    EatDriver d;
    d.Update(io, ped, true, kEatBurger, true, 100);
    io.running = false;
    d.Update(io, ped, false, 0, true, 110);
    d.Update(io, ped, false, 0, true, 120);
    CHECK(io.starts == 1);
  }
  { // something else going on (driving, swimming, a fall...): no eating animation
    FakeEatIo io;
    EatDriver d;
    d.Update(io, ped, true, kEatBurger, false, 100);
    CHECK(io.starts == 0);
    d.Update(io, ped, true, kEatBurger, true, 110);
    CHECK(io.starts == 1);
  }
  { // an animation that could not start is tried again after a second, not every frame
    FakeEatIo io;
    io.startOk = false;
    EatDriver d;
    d.Update(io, ped, true, kEatBurger, true, 100);
    d.Update(io, ped, true, kEatBurger, true, 110);
    d.Update(io, ped, true, kEatBurger, true, 999);
    CHECK(io.starts == 1);
    d.Update(io, ped, true, kEatBurger, true, 1100);
    CHECK(io.starts == 2);
  }
  { // a clock that goes backwards does not freeze the retries for ever
    FakeEatIo io;
    io.startOk = false;
    EatDriver d;
    d.Update(io, ped, true, kEatBurger, true, 100000);
    d.Update(io, ped, true, kEatBurger, true, 50);
    CHECK(io.starts == 2);
  }
  { // no ped: nothing is done
    FakeEatIo io;
    EatDriver d;
    d.Update(io, nullptr, true, kEatBurger, true, 100);
    CHECK(io.starts == 0);
  }
  std::puts("test_eat_driver OK");
  return 0;
}
