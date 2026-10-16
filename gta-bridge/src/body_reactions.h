#pragma once
#include <cstdint>

#include "protocol.h"

namespace cj {

// What the body of CJ is asked to do (the bridge plays GTA's own animations; never the game's tasks, which would hurt or kill the real player).
// Down / GetUp: thrown face down (a fall or a heavy blow) and getting up from there; DownBack / GetUpBack: thrown on his back by an explosion.
// Climb / ClimbPull / ClimbStand: the three animations of a wall climb, one after the other (jump and hang, pull up, stand).
// Jetpack: the pose of GTA's jetpack (kept while the flag is up).
// Tread / Swim / Crawl: floating, the breaststroke and the crawl on the surface; Dive: the dive that starts when the head goes under;
// Under: swimming under the water (coming up goes straight to the surface pose: the user did not like the raised hands of GTA's exit).
enum class BodyAnim { None, Fall, Down, DownBack, GetUp, GetUpBack, Dead, Clear, Climb, ClimbPull, ClimbStand, Jetpack, Tread, Swim, Crawl, Dive, Under };

struct BodyPlan {
    BodyAnim anim = BodyAnim::None;
    int sayContext = 0; // eGlobalSpeechContext of the voice to play now, or 0
};

constexpr uint32_t kPainCooldownMs = 1500;
constexpr uint32_t kGetUpMs = 1500;     // how long the get-up animation is allowed to run before the body is cleared
constexpr uint32_t kClimbPullMs = 500;  // docs/climb-spike.md: CLIMB_PULL is asked for 500 ms after CLIMB_JUMP
constexpr uint32_t kClimbStandMs = 1300; // and CLIMB_STAND 1300 ms after it
constexpr uint32_t kSwimMinStateMs = 250; // the least time a surface pose stays before another one replaces it (the movement flag blinks)
constexpr uint32_t kDiveMs = 1070;       // docs/swim-spike.md: SWIM_DIVE_UNDER lasts 1.07 s
constexpr int kSayPainLow = 345;        // CTX_GLOBAL_PAIN_LOW
constexpr int kSayPainHigh = 344;       // CTX_GLOBAL_PAIN_HIGH: the blow that knocks him down
constexpr int kSayDeath = 343;          // CTX_GLOBAL_PAIN_DEATH_LOW

// Decides, from the body bits of the State flags (falling, knocked, dead and the hurt counter), which animation CJ should be in and when
// he should cry out. The animation is asked for once, when its flag rises; a change of the hurt counter is a hurt.
class BodyReactions {
public:
    BodyPlan Update(uint32_t flags, uint32_t nowMs) {
        BodyPlan plan;
        const bool falling = (flags & kFlagFalling) != 0;
        const bool knocked = (flags & kFlagKnocked) != 0;
        const bool dead = (flags & kFlagDead) != 0;
        const bool climbing = (flags & kFlagWallClimb) != 0;
        const bool jetpack = (flags & kFlagJetpack) != 0;
        const bool inWater = (flags & kFlagInWater) != 0;
        const bool underwater = (flags & kFlagUnderwater) != 0;
        const bool waterMoving = (flags & kFlagWaterMoving) != 0;
        const bool crawl = (flags & kFlagSwim) != 0;
        const uint8_t hurt = HurtCounter(flags);
        const bool hurtChanged = haveHurt_ && hurt != lastHurt_;
        haveHurt_ = true;
        lastHurt_ = hurt;

        if (dead) {
            if (state_ != State::Dead) {
                state_ = State::Dead;
                plan.anim = BodyAnim::Dead;
                plan.sayContext = kSayDeath; // dying has its own voice, whatever else was going on
                Said(nowMs);
            }
            return plan;
        }
        if (state_ == State::Dead) {
            state_ = State::Normal;
            plan.anim = BodyAnim::Clear;
            return plan;
        }

        bool strong = false;
        if (knocked) {
            if (state_ != State::Down) {
                state_ = State::Down;
                blast_ = (flags & kFlagBlasted) != 0; // the kind is the one seen as the knockdown starts
                plan.anim = blast_ ? BodyAnim::DownBack : BodyAnim::Down;
                strong = true;
            }
        } else if (state_ == State::Down) {
            state_ = State::GettingUp;
            getUpStart_ = nowMs;
            plan.anim = blast_ ? BodyAnim::GetUpBack : BodyAnim::GetUp;
        } else if (state_ == State::GettingUp) {
            if (nowMs - getUpStart_ >= kGetUpMs) { // over (an unsigned difference: a clock that went back counts as over too)
                state_ = State::Normal;
                plan.anim = BodyAnim::Clear;
            }
        } else if (climbing || state_ == State::Climbing) {
            if (!climbing) {
                state_ = State::Normal;
                plan.anim = BodyAnim::Clear;
            } else if (state_ != State::Climbing) {
                state_ = State::Climbing;
                climbStart_ = nowMs;
                climbStage_ = 0;
                plan.anim = BodyAnim::Climb;
            } else {
                const uint32_t elapsed = nowMs - climbStart_; // an unsigned difference: a clock that went back counts as long over
                if (elapsed >= kClimbStandMs && climbStage_ < 2) {
                    climbStage_ = 2;
                    plan.anim = BodyAnim::ClimbStand;
                } else if (elapsed >= kClimbPullMs && climbStage_ < 1) {
                    climbStage_ = 1;
                    plan.anim = BodyAnim::ClimbPull;
                }
            }
        } else if (jetpack || state_ == State::Jetpacking) {
            if (!jetpack) {
                state_ = State::Normal;
                plan.anim = BodyAnim::Clear;
            } else if (state_ != State::Jetpacking) {
                state_ = State::Jetpacking;
                plan.anim = BodyAnim::Jetpack;
            }
        } else if (inWater || state_ == State::Swimming) {
            if (!inWater) {
                state_ = State::Normal;
                plan.anim = BodyAnim::Clear;
            } else {
                const BodyAnim surface = !waterMoving ? BodyAnim::Tread : crawl ? BodyAnim::Crawl : BodyAnim::Swim;
                const uint32_t elapsed = nowMs - swimSince_; // an unsigned difference: a clock that went back counts as long over
                if (state_ != State::Swimming) {
                    state_ = State::Swimming;
                    wasUnderwater_ = underwater;
                    plan.anim = SwimNow(underwater ? BodyAnim::Dive : surface, nowMs);
                } else if (underwater != wasUnderwater_) {
                    wasUnderwater_ = underwater;
                    plan.anim = SwimNow(underwater ? BodyAnim::Dive : surface, nowMs);
                } else if (swimAnim_ == BodyAnim::Dive) {
                    if (elapsed >= kDiveMs) {
                        plan.anim = SwimNow(BodyAnim::Under, nowMs);
                    }
                } else if (swimAnim_ != BodyAnim::Under && swimAnim_ != surface && elapsed >= kSwimMinStateMs) {
                    plan.anim = SwimNow(surface, nowMs);
                }
            }
        } else if (falling) {
            if (state_ != State::Falling) {
                state_ = State::Falling;
                plan.anim = BodyAnim::Fall;
            }
        } else if (state_ == State::Falling) {
            state_ = State::Normal;
            plan.anim = BodyAnim::Clear;
        }

        if (hurtChanged && CooldownOver(nowMs)) {
            plan.sayContext = strong ? kSayPainHigh : kSayPainLow;
            Said(nowMs);
        }
        return plan;
    }

    /** The animation that has to be held right now (the stage of a climb), or None: GTA's own jump task fights it and must be kept off. */
    BodyAnim Holding() const {
        if (state_ == State::Swimming) {
            return swimAnim_;
        }
        if (state_ == State::Jetpacking) {
            return BodyAnim::Jetpack;
        }
        if (state_ != State::Climbing) {
            return BodyAnim::None;
        }
        return climbStage_ == 0 ? BodyAnim::Climb : climbStage_ == 1 ? BodyAnim::ClimbPull : BodyAnim::ClimbStand;
    }

    void Reset() { *this = BodyReactions{}; }

private:
    enum class State { Normal, Falling, Down, GettingUp, Dead, Climbing, Jetpacking, Swimming };

    // Starts a swimming pose: remembers which one and when.
    BodyAnim SwimNow(BodyAnim anim, uint32_t nowMs) {
        swimAnim_ = anim;
        swimSince_ = nowMs;
        return anim;
    }

    // an unsigned difference: a clock that went back counts as the cooldown being over
    bool CooldownOver(uint32_t nowMs) const { return !haveSaid_ || nowMs - lastSayMs_ >= kPainCooldownMs; }
    void Said(uint32_t nowMs) {
        haveSaid_ = true;
        lastSayMs_ = nowMs;
    }

    State state_ = State::Normal;
    uint32_t getUpStart_ = 0;
    uint32_t climbStart_ = 0;
    BodyAnim swimAnim_ = BodyAnim::None;
    uint32_t swimSince_ = 0;
    bool wasUnderwater_ = false;
    int climbStage_ = 0; // 0 jump, 1 pull, 2 stand: the stage whose animation was last asked for
    bool blast_ = false;
    bool haveHurt_ = false;
    uint8_t lastHurt_ = 0;
    bool haveSaid_ = false;
    uint32_t lastSayMs_ = 0;
};

} // namespace cj
