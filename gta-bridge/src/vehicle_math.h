#pragma once
// The arithmetic on the matrices of a car and of CJ at its wheel. Free of the game: vehicle_game.h feeds it real memory, the tests fake memory.
#include <algorithm>
#include <cmath>
#include <cstdint>

#include "pose.h"
#include "protocol.h"

namespace cj {

// Where a car or a ped faces on the ground, as a GTA heading: radians in [0, 2 pi), counter-clockwise from north (matrix "up" is the nose).
inline float HeadingOf(const RwMat& m) {
    float heading = std::atan2(-m.up[0], m.up[1]);
    if (heading < 0) {
        heading += 6.2831853f;
    }
    return heading;
}

// Turns the matrix to face the GTA heading, upright; the position is left alone.
inline void FaceCar(RwMat& m, float heading) {
    m.right[0] = std::cos(heading);
    m.right[1] = std::sin(heading);
    m.right[2] = 0.0f;
    m.up[0] = -std::sin(heading);
    m.up[1] = std::cos(heading);
    m.up[2] = 0.0f;
    m.at[0] = 0.0f;
    m.at[1] = 0.0f;
    m.at[2] = 1.0f;
}

// GTA keeps the speed in metres per frame of 1/50 s; the packet carries metres per second.
inline void ToMetresPerSecond(const float* perFrame, float* out) {
    for (int i = 0; i < 3; i++) {
        out[i] = perFrame[i] * 50.0f;
    }
}

inline float VecDot(const float* a, const float* b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

// Fills what the car is doing for Minecraft. `driver` is CJ's matrix when he sits at the wheel (nullptr: nobody does).
// pitch is positive nose up and roll positive right side up; steer is GTA's own angle (negative to the right).
inline void FillCarState(VehicleStatePacket& out, const RwMat& car, const float* velocity, float steer, const RwMat* driver) {
    out.pos[0] = car.pos[0];
    out.pos[1] = car.pos[1];
    out.pos[2] = car.pos[2];
    out.heading = HeadingOf(car);
    out.pitch = std::asin(std::clamp(car.up[2], -1.0f, 1.0f));
    out.roll = std::asin(std::clamp(car.right[2], -1.0f, 1.0f));
    out.speed = VecDot(velocity, car.up);
    out.steer = steer;
    out.flags = driver ? kVehicleFlagDriverIn : 0;
    out.seat[0] = out.seat[1] = out.seat[2] = 0.0f;
    out.seatHeading = 0.0f;
    if (!driver) {
        return;
    }
    const float d[3] = {driver->pos[0] - car.pos[0], driver->pos[1] - car.pos[1], driver->pos[2] - car.pos[2]};
    out.seat[0] = VecDot(d, car.right);
    out.seat[1] = VecDot(d, car.up);
    out.seat[2] = VecDot(d, car.at);
    // CJ's nose turned counter-clockwise from the car's nose by s has Dot(nose, right) = -sin s and Dot(nose, up) = cos s
    out.seatHeading = std::atan2(-VecDot(driver->up, car.right), VecDot(driver->up, car.up));
}

// Moves the car by (dx, dy) and takes from its speed the part that goes against that push (the obstacle is the other way): the rest of the speed stays.
inline void CorrectCar(RwMat& car, float* velocity, float dx, float dy) {
    car.pos[0] += dx;
    car.pos[1] += dy;
    const float length = std::sqrt(dx * dx + dy * dy); // zero: the divisions give NaN and the test below fails, so nothing changes
    const float cx = dx / length;
    const float cy = dy / length;
    const float into = velocity[0] * cx + velocity[1] * cy;
    if (into < 0.0f) {
        velocity[0] -= into * cx;
        velocity[1] -= into * cy;
    }
}

// Sets the car down by (dx, dy) with all it was doing: the speed and the turn stay.
inline void ShiftCar(RwMat& car, float dx, float dy) {
    car.pos[0] += dx;
    car.pos[1] += dy;
}

} // namespace cj
