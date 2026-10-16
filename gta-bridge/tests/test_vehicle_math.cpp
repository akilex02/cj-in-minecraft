#include <cmath>
#include <cstdio>
#include <cstdlib>

#include "vehicle_math.h"

#define CHECK(cond)                                                          \
  do {                                                                       \
    if (!(cond)) {                                                           \
      std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__,  \
                   #cond);                                                   \
      std::exit(1);                                                          \
    }                                                                        \
  } while (0)

using namespace cj;

static bool Close(float a, float b, float eps = 1e-4f) { return std::fabs(a - b) < eps; }

static RwMat Identity(float x = 0, float y = 0, float z = 0) {
  RwMat m{};
  m.right[0] = 1;
  m.up[1] = 1;
  m.at[2] = 1;
  m.pos[0] = x;
  m.pos[1] = y;
  m.pos[2] = z;
  return m;
}

int main() {
  const float pi = 3.14159265f;

  { // FaceCar turns the car to a GTA heading (counter-clockwise from north) and HeadingOf reads it back
    for (float h : {0.0f, 1.0f, pi / 2, pi, 3.5f, 6.2f}) {
      RwMat m = Identity(5, 6, 7);
      FaceCar(m, h);
      CHECK(Close(HeadingOf(m), h));
      CHECK(Close(m.up[0], -std::sin(h)) && Close(m.up[1], std::cos(h)) && Close(m.up[2], 0));      // forward
      CHECK(Close(m.right[0], std::cos(h)) && Close(m.right[1], std::sin(h)) && Close(m.right[2], 0)); // right hand
      CHECK(Close(m.at[0], 0) && Close(m.at[1], 0) && Close(m.at[2], 1));
      CHECK(m.pos[0] == 5 && m.pos[1] == 6 && m.pos[2] == 7); // it does not move
    }
    RwMat north = Identity();
    CHECK(Close(HeadingOf(north), 0));
    RwMat west = Identity();
    west.up[0] = -1;
    west.up[1] = 0;
    CHECK(Close(HeadingOf(west), pi / 2)); // facing west (-x) is a quarter turn counter-clockwise
    CHECK(HeadingOf(west) >= 0 && HeadingOf(west) < 2 * pi);
    RwMat east = Identity();
    east.up[0] = 1;
    east.up[1] = 0;
    CHECK(Close(HeadingOf(east), 1.5f * pi)); // not -pi/2: the range is [0, 2 pi)
  }

  { // what the car reports: place, heading, pitch, roll, speed forward, steering
    RwMat car = Identity(1514.5f, -2585.5f, 13.25f);
    const float velocity[3] = {0, 7, 0};
    VehicleStatePacket s{};
    s.seat[0] = s.seat[1] = s.seat[2] = 9.0f; // left over from an earlier reading
    s.seatHeading = 9.0f;
    FillCarState(s, car, velocity, -0.25f, nullptr);
    CHECK(s.pos[0] == 1514.5f && s.pos[1] == -2585.5f && s.pos[2] == 13.25f);
    CHECK(Close(s.heading, 0) && Close(s.pitch, 0) && Close(s.roll, 0));
    CHECK(Close(s.speed, 7) && s.steer == -0.25f);
    CHECK((s.flags & kVehicleFlagDriverIn) == 0);
    CHECK(s.seat[0] == 0 && s.seat[1] == 0 && s.seat[2] == 0 && s.seatHeading == 0); // nobody at the wheel
  }

  { // speed is along the nose: backwards is negative, sideways is nothing
    RwMat car = Identity();
    VehicleStatePacket s{};
    const float back[3] = {0, -3, 0};
    FillCarState(s, car, back, 0, nullptr);
    CHECK(Close(s.speed, -3));
    const float side[3] = {7, 0, 0};
    FillCarState(s, car, side, 0, nullptr);
    CHECK(Close(s.speed, 0));
    RwMat west = Identity();
    FaceCar(west, pi / 2);
    const float west7[3] = {-7, 0, 0};
    FillCarState(s, west, west7, 0, nullptr);
    CHECK(Close(s.speed, 7) && Close(s.heading, pi / 2));
  }

  { // nose up is a positive pitch and the right side up is a positive roll
    RwMat car = Identity();
    car.up[1] = std::cos(0.2f);
    car.up[2] = std::sin(0.2f);
    const float still[3] = {0, 0, 0};
    VehicleStatePacket s{};
    FillCarState(s, car, still, 0, nullptr);
    CHECK(Close(s.pitch, 0.2f) && Close(s.roll, 0));
    RwMat rolled = Identity();
    rolled.right[0] = std::cos(0.3f);
    rolled.right[2] = std::sin(0.3f);
    FillCarState(s, rolled, still, 0, nullptr);
    CHECK(Close(s.roll, 0.3f) && Close(s.pitch, 0));
    RwMat odd = Identity();
    odd.up[2] = 1.0001f; // rounding must not make a NaN
    odd.right[2] = -1.0001f;
    FillCarState(s, odd, still, 0, nullptr);
    CHECK(std::isfinite(s.pitch) && std::isfinite(s.roll));
    CHECK(Close(s.pitch, pi / 2) && Close(s.roll, -pi / 2));
  }

  { // CJ at the wheel: where he sits in the axes of the car, and how he is turned relative to it
    for (float h : {0.0f, 1.0f, 4.0f}) {
      RwMat car = Identity(100, 200, 10);
      FaceCar(car, h);
      RwMat cj = car;
      for (int k = 0; k < 3; k++) {
        cj.pos[k] = car.pos[k] + car.right[k] * -0.463f + car.up[k] * 0.14f + car.at[k] * -0.095f;
      }
      const float still[3] = {0, 0, 0};
      VehicleStatePacket s{};
      FillCarState(s, car, still, 0, &cj);
      CHECK((s.flags & kVehicleFlagDriverIn) != 0);
      CHECK(Close(s.seat[0], -0.463f) && Close(s.seat[1], 0.14f) && Close(s.seat[2], -0.095f));
      CHECK(Close(s.seatHeading, 0));
      RwMat turned = cj;
      FaceCar(turned, h + pi / 2); // CJ looks a quarter turn counter-clockwise from the car's nose
      for (int k = 0; k < 3; k++) {
        turned.pos[k] = cj.pos[k];
      }
      FillCarState(s, car, still, 0, &turned);
      CHECK(Close(s.seatHeading, pi / 2));
      FaceCar(turned, h - pi / 2);
      FillCarState(s, car, still, 0, &turned);
      CHECK(Close(s.seatHeading, -pi / 2));
    }
  }

  { // a correction moves the car in x and y only, and takes the speed that goes into the obstacle away, not the rest
    RwMat car = Identity(10, 20, 30);
    float v[3] = {0, 5, 2};
    CorrectCar(car, v, 0.5f, -0.25f);
    CHECK(Close(car.pos[0], 10.5f) && Close(car.pos[1], 19.75f) && car.pos[2] == 30);

    RwMat a = Identity();
    float into[3] = {0, 5, 2}; // the wall is ahead: the correction pushes back, and the speed forward goes
    CorrectCar(a, into, 0, -1);
    CHECK(Close(into[0], 0) && Close(into[1], 0) && Close(into[2], 2));

    RwMat b = Identity();
    float slide[3] = {3, 5, 0}; // the part along the wall stays
    CorrectCar(b, slide, 0, -1);
    CHECK(Close(slide[0], 3) && Close(slide[1], 0));

    RwMat c = Identity();
    float away[3] = {0, -5, 0}; // already moving the way the correction pushes: nothing to take away
    CorrectCar(c, away, 0, -1);
    CHECK(Close(away[1], -5));

    RwMat d = Identity();
    float diagonal[3] = {-4, 4, 1}; // the correction pushes toward +x, -y: the speed toward -x and +y... only the part against it goes
    CorrectCar(d, diagonal, 1, -1);
    const float len = std::sqrt(2.0f);
    const float into_d = (-4 * 1 + 4 * -1) / len; // -8 / sqrt(2): against the push
    CHECK(Close(diagonal[0], -4 - into_d * 1 / len) && Close(diagonal[1], 4 - into_d * -1 / len));
    CHECK(Close(diagonal[0], 0) && Close(diagonal[1], 0) && Close(diagonal[2], 1));

    RwMat e = Identity(1, 2, 3);
    float keep[3] = {1, 2, 3};
    CorrectCar(e, keep, 0, 0); // no correction: nothing changes and nothing breaks
    CHECK(e.pos[0] == 1 && e.pos[1] == 2 && keep[0] == 1 && keep[1] == 2 && keep[2] == 3);
  }

  { // GTA keeps the speed in metres per frame of 1/50 s; the packet carries metres per second
    const float perFrame[3] = {0.0f, 0.2f, -0.1f};
    float mps[3];
    ToMetresPerSecond(perFrame, mps);
    CHECK(Close(mps[0], 0) && Close(mps[1], 10) && Close(mps[2], -5));
  }

  { // a shift keeps the speed and the turn, and moves only x and y
    RwMat car = Identity(10, 20, 5);
    car.right[0] = 0;
    car.right[1] = -1;
    ShiftCar(car, -3.0f, 4.0f);
    CHECK(Close(car.pos[0], 7) && Close(car.pos[1], 24) && Close(car.pos[2], 5) && Close(car.right[1], -1));
  }

  std::puts("test_vehicle_math OK");
  return 0;
}
