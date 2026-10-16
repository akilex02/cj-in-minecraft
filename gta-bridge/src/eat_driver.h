#pragma once
// The eating animation: while the player eats (a food item in use in Minecraft) CJ plays one of the animations of GTA's FOOD block. The game side is
// behind EatIo (bridge.cpp); the rules are here and tested natively.
#include <cstdint>

#include "protocol.h"

namespace cj {

class EatIo {
public:
    virtual ~EatIo() = default;
    // The animation of a bite of food is playing on the ped (the task of ours in his partial-animation slot).
    virtual bool EatTaskRunning(uint8_t* ped) = 0;
    // Starts the animation of this kind (kEat*); false when the game could not (the block is not there).
    virtual bool StartEating(uint8_t* ped, int kind) = 0;
};

class EatDriver {
public:
    static constexpr uint32_t kRetryMs = 1000;

    // Once per frame. `eating`/`kind` come from the flags of the state; `allowed` is false while something else has the body (the car, the water, a climb...).
    void Update(EatIo& io, uint8_t* ped, bool eating, int kind, bool allowed, uint32_t now) {
        if (!ped || !eating || !allowed || kind < kEatBurger || kind > kEatPizza) {
            return;
        }
        if (io.EatTaskRunning(ped)) {
            return;
        }
        if (failed_ && static_cast<uint32_t>(now - failedMs_) < kRetryMs) { // unsigned: a clock that goes back does not hold it for ever
            return;
        }
        failed_ = !io.StartEating(ped, kind); // it ended before he finished eating: again
        failedMs_ = now;
    }

private:
    bool failed_ = false;
    uint32_t failedMs_ = 0;
};

} // namespace cj
