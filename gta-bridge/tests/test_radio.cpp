#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <initializer_list>

#include "protocol.h"
#include "radio.h"

#define CHECK(cond)                                                          \
  do {                                                                       \
    if (!(cond)) {                                                           \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__,  \
                   #cond);                                                   \
      std::exit(1);                                                          \
    }                                                                        \
  } while (0)

using namespace cj;

int main() {
  { // the dial: the eleven stations in order, then the radio off, then round again
    CHECK(RadioStep(1, +1) == 2 && RadioStep(10, +1) == 11);
    CHECK(RadioStep(11, +1) == kRadioOff);
    CHECK(RadioStep(kRadioOff, +1) == 1);
    CHECK(RadioStep(2, -1) == 1);
    CHECK(RadioStep(1, -1) == kRadioOff);
    CHECK(RadioStep(kRadioOff, -1) == 11);
  }
  { // a station that is not on the dial (unknown, the user tracks, a broken value): the first or the last one, by the direction
    for (int unknown : {-1, 0, 12, 14, 99, -50}) {
      CHECK(RadioStep(unknown, +1) == 1);
      CHECK(RadioStep(unknown, -1) == kRadioOff);
    }
  }
  { // several presses at once: they add up and go round
    CHECK(RadioStepBy(3, 2) == 5);
    CHECK(RadioStepBy(3, -2) == 1);
    CHECK(RadioStepBy(11, 2) == 1);   // 11 -> off -> 1
    CHECK(RadioStepBy(1, -2) == 11);  // 1 -> off -> 11
    CHECK(RadioStepBy(4, 0) == 4);
    CHECK(RadioStepBy(4, 12) == 4);   // a full turn is twelve stops
    CHECK(RadioStepBy(-1, 0) == -1);  // nothing asked: nothing changes, even for an unknown station
  }
  { // the station travels in the upper byte of the flags as id + 1
    CHECK(StationFlags(1, 5) == (1u | (6u << 8)));
    CHECK(StationOfFlags(StationFlags(1, 5)) == 5);
    CHECK(StationOfFlags(1) == -1);
    CHECK(StationFlags(0xFF, -1) == 0xFFu);             // unknown: the upper byte is empty
    CHECK(StationFlags(0xFFFF, 3) == (0x00FFu | (4u << 8))); // the low byte stays, the old station goes
    CHECK(StationFlags(1, 300) == 1u);                   // a broken id says nothing
  }
  { // the commands after enter and exit
    CHECK(kVehicleCmdRadioNext == 3 && kVehicleCmdRadioPrevious == 4);
  }
  std::puts("test_radio OK");
  return 0;
}
