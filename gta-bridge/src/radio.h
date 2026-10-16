#pragma once
// The radio of the car. The stations are the ids the game's track manager uses (found live, docs/vehicle-results.md): 1 to 11 are the eleven stations,
// 13 is the radio off; 0, 12 (the user's tracks) and the rest are not on the dial. The id of the station is told to Minecraft in the upper byte of the
// flags of the vehicle state, as id + 1, so 0 means unknown. Pure.
#include <cstdint>

namespace cj {

constexpr int kRadioFirst = 1;
constexpr int kRadioLast = 11;
constexpr int kRadioOff = 13;
constexpr int kRadioStops = kRadioLast - kRadioFirst + 2; // the stations and the radio off
constexpr unsigned kVehicleStationShift = 8;

inline bool OnDial(int station) { return (station >= kRadioFirst && station <= kRadioLast) || station == kRadioOff; }

// `steps` stops forward (negative: back) round the dial. A station that is not on the dial starts from the first one going forward and from the
// radio off going back; with no steps nothing changes.
inline int RadioStepBy(int station, int steps) {
    if (steps == 0) {
        return station;
    }
    int index;
    if (OnDial(station)) {
        index = station == kRadioOff ? kRadioStops - 1 : station - kRadioFirst;
    } else {
        index = steps > 0 ? -1 : kRadioStops; // one step from the first (or the last) stop
    }
    int next = (index + steps) % kRadioStops;
    if (next < 0) {
        next += kRadioStops;
    }
    return next == kRadioStops - 1 ? kRadioOff : next + kRadioFirst;
}

inline int RadioStep(int station, int direction) { return RadioStepBy(station, direction > 0 ? 1 : -1); }

// The flags with the station of the radio in the upper byte (id + 1); unknown (-1) or a broken id leaves the byte empty.
inline uint16_t StationFlags(uint16_t flags, int station) {
    const uint16_t low = static_cast<uint16_t>(flags & 0x00FFu);
    if (station < 0 || station > 254) {
        return low;
    }
    return static_cast<uint16_t>(low | ((station + 1) << kVehicleStationShift));
}

// The station in the flags, or -1 when they do not say.
inline int StationOfFlags(uint16_t flags) {
    const int code = (flags >> kVehicleStationShift) & 0xFF;
    return code == 0 ? -1 : code - 1;
}

} // namespace cj
