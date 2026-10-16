#include <cstdio>
#include <cstdlib>

#include "body_reactions.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

using namespace cj;

static uint32_t Hurt(uint32_t n) { return (n & 0xFu) << kHurtShift; }

int main() {
    { // nothing going on: nothing to do, ever
        BodyReactions r;
        for (uint32_t t = 0; t < 5000; t += 20) {
            BodyPlan p = r.Update(kFlagOnGround, t);
            CHECK(p.anim == BodyAnim::None && p.sayContext == 0);
        }
    }

    { // free fall: the animation once, then nothing while it lasts, then cleared
        BodyReactions r;
        CHECK(r.Update(0, 0).anim == BodyAnim::None);
        CHECK(r.Update(kFlagFalling, 20).anim == BodyAnim::Fall);
        CHECK(r.Update(kFlagFalling, 40).anim == BodyAnim::None);
        CHECK(r.Update(kFlagFalling | kFlagOnGround, 60).anim == BodyAnim::None);
        CHECK(r.Update(0, 80).anim == BodyAnim::Clear);
        CHECK(r.Update(0, 100).anim == BodyAnim::None);
        CHECK(r.Update(kFlagFalling, 120).anim == BodyAnim::Fall); // and again the next time
    }

    { // knocked down: lying once, getting up when it ends, cleared when the get-up animation is over
        BodyReactions r;
        CHECK(r.Update(0, 0).anim == BodyAnim::None);
        CHECK(r.Update(kFlagKnocked, 100).anim == BodyAnim::Down);
        CHECK(r.Update(kFlagKnocked, 200).anim == BodyAnim::None);
        CHECK(r.Update(0, 2600).anim == BodyAnim::GetUp);          // the flag dropped
        CHECK(r.Update(0, 2700).anim == BodyAnim::None);           // the get-up animation runs
        CHECK(r.Update(0, 4099).anim == BodyAnim::None);
        CHECK(r.Update(0, 4100).anim == BodyAnim::Clear);          // 1500 ms after it started: cleared
        CHECK(r.Update(0, 4200).anim == BodyAnim::None);
    }

    { // knocked down by an explosion: thrown on his back, and he gets up from his back
        BodyReactions r;
        CHECK(r.Update(0, 0).anim == BodyAnim::None);
        CHECK(r.Update(kFlagKnocked | kFlagBlasted, 100).anim == BodyAnim::DownBack);
        CHECK(r.Update(kFlagKnocked | kFlagBlasted, 200).anim == BodyAnim::None);
        CHECK(r.Update(0, 2600).anim == BodyAnim::GetUpBack);
        CHECK(r.Update(0, 4100).anim == BodyAnim::Clear);
    }

    { // the kind is the one seen when the knockdown starts: the flag appearing or going later changes nothing
        BodyReactions r;
        CHECK(r.Update(kFlagKnocked, 100).anim == BodyAnim::Down);
        CHECK(r.Update(kFlagKnocked | kFlagBlasted, 200).anim == BodyAnim::None);
        CHECK(r.Update(kFlagBlasted, 2600).anim == BodyAnim::GetUp);       // face down all the way: a normal knockdown
        BodyReactions q;
        CHECK(q.Update(kFlagKnocked | kFlagBlasted, 100).anim == BodyAnim::DownBack);
        CHECK(q.Update(kFlagKnocked, 200).anim == BodyAnim::None);
        CHECK(q.Update(0, 2600).anim == BodyAnim::GetUpBack);
    }

    { // the blast bit means nothing outside a knockdown, and the next knockdown is judged afresh
        BodyReactions r;
        CHECK(r.Update(kFlagBlasted, 0).anim == BodyAnim::None);
        CHECK(r.Update(kFlagKnocked | kFlagBlasted, 100).anim == BodyAnim::DownBack);
        r.Update(0, 2600);
        r.Update(0, 4100);
        CHECK(r.Update(kFlagKnocked, 5000).anim == BodyAnim::Down);
        CHECK(r.Update(0, 7600).anim == BodyAnim::GetUp);
    }

    { // a blast that kills goes to the death all the same
        BodyReactions r;
        CHECK(r.Update(kFlagKnocked | kFlagBlasted, 0).anim == BodyAnim::DownBack);
        CHECK(r.Update(kFlagDead | kFlagBlasted, 100).anim == BodyAnim::Dead);
    }

    { // falling and then knocked (the landing): lying, not the fall
        BodyReactions r;
        CHECK(r.Update(kFlagFalling, 0).anim == BodyAnim::Fall);
        CHECK(r.Update(kFlagKnocked, 100).anim == BodyAnim::Down);
        CHECK(r.Update(kFlagKnocked | kFlagFalling, 120).anim == BodyAnim::None); // falling is ignored while lying
    }

    { // death: the animation once, kept while it lasts, cleared when the player is back
        BodyReactions r;
        CHECK(r.Update(0, 0).anim == BodyAnim::None);
        BodyPlan dead = r.Update(kFlagDead, 100);
        CHECK(dead.anim == BodyAnim::Dead && dead.sayContext == kSayDeath);
        for (uint32_t t = 120; t < 10000; t += 500) {
            CHECK(r.Update(kFlagDead, t).anim == BodyAnim::None);
        }
        CHECK(r.Update(0, 10000).anim == BodyAnim::Clear);
        CHECK(r.Update(0, 10020).anim == BodyAnim::None);
    }

    { // dying while knocked down or falling goes straight to the death: no get-up
        BodyReactions r;
        r.Update(kFlagKnocked, 0);
        CHECK(r.Update(kFlagDead | kFlagKnocked, 100).anim == BodyAnim::Dead);
        CHECK(r.Update(kFlagDead, 200).anim == BodyAnim::None);   // the knocked flag dropped: still dead, no get-up
        BodyReactions q;
        q.Update(kFlagFalling, 0);
        CHECK(q.Update(kFlagDead, 100).anim == BodyAnim::Dead);
        CHECK(q.Update(0, 200).anim == BodyAnim::Clear);
    }

    { // the pain voice: a change of the counter says it, once; a normal hit is the low pain
        BodyReactions r;
        CHECK(r.Update(Hurt(3), 0).sayContext == 0);                      // the first reading is only the baseline
        CHECK(r.Update(Hurt(3), 20).sayContext == 0);
        CHECK(r.Update(Hurt(4), 40).sayContext == kSayPainLow);
        CHECK(r.Update(Hurt(4), 60).sayContext == 0);
        CHECK(r.Update(Hurt(5), 600).sayContext == 0);                    // within the cooldown (1500 ms from the last voice): silent
        CHECK(r.Update(Hurt(6), 1539).sayContext == 0);
        CHECK(r.Update(Hurt(7), 1540).sayContext == kSayPainLow);         // 1500 ms after the last voice
    }

    { // the counter wraps from 15 to 0 and that is a change too
        BodyReactions r;
        r.Update(Hurt(15), 0);
        CHECK(r.Update(Hurt(0), 20).sayContext == kSayPainLow);
    }

    { // a hit that knocks him down is the high pain, said as the knockdown starts
        BodyReactions r;
        r.Update(Hurt(1), 0);
        BodyPlan p = r.Update(kFlagKnocked | Hurt(2), 100);
        CHECK(p.anim == BodyAnim::Down && p.sayContext == kSayPainHigh);
    }

    { // a hit while knocked down or the same frame as the death says nothing new: the death has its own voice
        BodyReactions r;
        r.Update(Hurt(1), 0);
        BodyPlan dead = r.Update(kFlagDead | Hurt(2), 100);
        CHECK(dead.anim == BodyAnim::Dead && dead.sayContext == kSayDeath);
        CHECK(r.Update(kFlagDead | Hurt(3), 3000).sayContext == 0);        // dead: no more pain
    }

    { // Reset forgets everything: the next fall is a fall again and the next counter is a baseline
        BodyReactions r;
        r.Update(kFlagFalling | Hurt(1), 0);
        r.Reset();
        BodyPlan p = r.Update(kFlagFalling | Hurt(5), 100);
        CHECK(p.anim == BodyAnim::Fall && p.sayContext == 0);
    }

    { // a clock that goes back never leaves the voice or the get-up stuck
        BodyReactions r;
        r.Update(Hurt(1), 100000);
        CHECK(r.Update(Hurt(2), 100100).sayContext == kSayPainLow);
        CHECK(r.Update(Hurt(3), 50).sayContext == kSayPainLow);            // the clock went back
        BodyReactions q;
        q.Update(kFlagKnocked, 100000);
        q.Update(0, 102600);                                               // getting up
        CHECK(q.Update(0, 10).anim == BodyAnim::Clear);                    // the clock went back: not stuck getting up
    }

    { // climbing a wall: jump and hang, then the pull at 500 ms, then the stand at 1300 ms, and cleared when the flag drops
        BodyReactions r;
        CHECK(r.Update(kFlagOnGround, 0).anim == BodyAnim::None);
        CHECK(r.Update(kFlagWallClimb, 100).anim == BodyAnim::Climb);
        CHECK(r.Update(kFlagWallClimb, 599).anim == BodyAnim::None);
        CHECK(r.Update(kFlagWallClimb, 600).anim == BodyAnim::ClimbPull);          // 500 ms after it started
        CHECK(r.Update(kFlagWallClimb, 1399).anim == BodyAnim::None);
        CHECK(r.Update(kFlagWallClimb, 1400).anim == BodyAnim::ClimbStand);        // 1300 ms after it started
        CHECK(r.Update(kFlagWallClimb, 2000).anim == BodyAnim::None);
        CHECK(r.Update(0, 2200).anim == BodyAnim::Clear);
        CHECK(r.Update(0, 2300).anim == BodyAnim::None);
        CHECK(r.Update(kFlagWallClimb, 5000).anim == BodyAnim::Climb);             // and again the next time
    }

    { // while climbing, the animation that must be held is the one of the stage; otherwise none
        BodyReactions r;
        CHECK(r.Holding() == BodyAnim::None);
        r.Update(kFlagWallClimb, 0);
        CHECK(r.Holding() == BodyAnim::Climb);
        r.Update(kFlagWallClimb, 600);
        CHECK(r.Holding() == BodyAnim::ClimbPull);
        r.Update(kFlagWallClimb, 1400);
        CHECK(r.Holding() == BodyAnim::ClimbStand);
        r.Update(0, 2200);
        CHECK(r.Holding() == BodyAnim::None);
        BodyReactions q;
        q.Update(kFlagWallClimb, 0);
        q.Update(kFlagWallClimb | kFlagDead, 100);
        CHECK(q.Holding() == BodyAnim::None);
    }

    { // the jetpack: the pose once when the flag rises, cleared when it drops; the thrust flag alone does nothing
        BodyReactions r;
        CHECK(r.Update(kFlagOnGround, 0).anim == BodyAnim::None);
        CHECK(r.Update(kFlagJetpackThrust, 20).anim == BodyAnim::None);
        CHECK(r.Update(kFlagJetpack, 40).anim == BodyAnim::Jetpack);
        CHECK(r.Update(kFlagJetpack | kFlagJetpackThrust, 60).anim == BodyAnim::None);
        CHECK(r.Update(kFlagJetpack, 80).anim == BodyAnim::None);
        CHECK(r.Holding() == BodyAnim::Jetpack);
        CHECK(r.Update(0, 100).anim == BodyAnim::Clear);
        CHECK(r.Holding() == BodyAnim::None);
        CHECK(r.Update(0, 120).anim == BodyAnim::None);
        CHECK(r.Update(kFlagJetpack, 140).anim == BodyAnim::Jetpack);          // and again the next time
    }

    { // the jetpack is not pre-empted by a fall, and dying or being knocked down win over it
        BodyReactions r;
        CHECK(r.Update(kFlagJetpack | kFlagFalling, 0).anim == BodyAnim::Jetpack);
        CHECK(r.Update(kFlagJetpack | kFlagFalling, 20).anim == BodyAnim::None);
        CHECK(r.Update(kFlagFalling, 40).anim == BodyAnim::Clear);               // the jetpack goes: cleared first
        CHECK(r.Update(kFlagFalling, 60).anim == BodyAnim::Fall);                // and then the fall starts
        BodyReactions q;
        q.Update(kFlagJetpack, 0);
        CHECK(q.Update(kFlagJetpack | kFlagDead, 20).anim == BodyAnim::Dead);
        CHECK(q.Holding() == BodyAnim::None);
        BodyReactions k;
        k.Update(kFlagJetpack, 0);
        CHECK(k.Update(kFlagJetpack | kFlagKnocked, 20).anim == BodyAnim::Down);
    }

    { // the jetpack and the climb: a climb that is under way is not taken over
        BodyReactions r;
        CHECK(r.Update(kFlagWallClimb, 0).anim == BodyAnim::Climb);
        CHECK(r.Update(kFlagWallClimb | kFlagJetpack, 20).anim == BodyAnim::None);
        CHECK(r.Holding() == BodyAnim::Climb);
    }

    { // the durations are the measured ones (docs/swim-spike.md), and a pose that fits is not asked for again and again
        CHECK(kDiveMs == 1070 && kSwimMinStateMs == 250);
        BodyReactions r;
        CHECK(r.Update(kFlagInWater, 0).anim == BodyAnim::Tread);
        CHECK(r.Update(kFlagInWater, 250).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater, 2000).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 2100).anim == BodyAnim::Swim);
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 2350).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 5000).anim == BodyAnim::None);
    }

    { // floating: the pose once, kept, cleared when the water is left
        BodyReactions r;
        CHECK(r.Update(kFlagOnGround, 0).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater, 20).anim == BodyAnim::Tread);
        CHECK(r.Update(kFlagInWater, 40).anim == BodyAnim::None);
        CHECK(r.Holding() == BodyAnim::Tread);
        CHECK(r.Update(0, 60).anim == BodyAnim::Clear);
        CHECK(r.Holding() == BodyAnim::None);
        CHECK(r.Update(0, 80).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater, 100).anim == BodyAnim::Tread);            // and again the next time
        CHECK(r.Update(kFlagWaterMoving, 120).anim == BodyAnim::Clear);        // the movement bit alone is not the water
    }

    { // swimming on the surface: breaststroke, crawl with the fast pose, and back to floating, never faster than the minimum time
        BodyReactions r;
        CHECK(r.Update(kFlagInWater, 0).anim == BodyAnim::Tread);
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 100).anim == BodyAnim::None);          // 100 ms: too soon to change
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 249).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 250).anim == BodyAnim::Swim);
        CHECK(r.Holding() == BodyAnim::Swim);
        CHECK(r.Update(kFlagInWater, 300).anim == BodyAnim::None);                             // stopped, but the stroke has lasted 50 ms
        CHECK(r.Update(kFlagInWater, 499).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater, 500).anim == BodyAnim::Tread);
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving | kFlagSwim, 800).anim == BodyAnim::Crawl);
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 1000).anim == BodyAnim::None);         // the fast pose dropped 200 ms after the change
        CHECK(r.Update(kFlagInWater | kFlagWaterMoving, 1050).anim == BodyAnim::Swim);
    }

    { // diving: the dive once, then swimming under the water when it is over, for as long as the head is under
        BodyReactions r;
        CHECK(r.Update(kFlagInWater, 0).anim == BodyAnim::Tread);
        CHECK(r.Update(kFlagInWater | kFlagUnderwater, 100).anim == BodyAnim::Dive);           // the head goes under: dive at once
        CHECK(r.Holding() == BodyAnim::Dive);
        CHECK(r.Update(kFlagInWater | kFlagUnderwater, 100 + kDiveMs - 1).anim == BodyAnim::None);
        CHECK(r.Update(kFlagInWater | kFlagUnderwater, 100 + kDiveMs).anim == BodyAnim::Under);
        CHECK(r.Holding() == BodyAnim::Under);
        CHECK(r.Update(kFlagInWater | kFlagUnderwater, 5000).anim == BodyAnim::None);
        // coming up: straight to the surface pose that fits the movement (the exit of GTA, with the hands raised, is not used)
        CHECK(r.Update(kFlagInWater, 6000).anim == BodyAnim::Tread);
        CHECK(r.Update(kFlagInWater, 6100).anim == BodyAnim::None);
        // coming up swimming, and a dive started again at once
        BodyReactions q;
        q.Update(kFlagInWater | kFlagUnderwater, 0);
        q.Update(kFlagInWater | kFlagUnderwater, kDiveMs);
        CHECK(q.Update(kFlagInWater | kFlagWaterMoving, 3000).anim == BodyAnim::Swim);
        CHECK(q.Update(kFlagInWater | kFlagWaterMoving | kFlagUnderwater, 3100).anim == BodyAnim::Dive);
    }

    { // entering the water with the head already under, and leaving it from below
        BodyReactions r;
        CHECK(r.Update(kFlagInWater | kFlagUnderwater, 0).anim == BodyAnim::Dive);
        CHECK(r.Update(kFlagInWater | kFlagUnderwater, kDiveMs).anim == BodyAnim::Under);
        CHECK(r.Update(0, kDiveMs + 20).anim == BodyAnim::Clear);
        CHECK(r.Holding() == BodyAnim::None);
    }

    { // the other states win: death and knockdown over the water, and a climb or a jetpack flight is not taken over
        BodyReactions r;
        r.Update(kFlagInWater, 0);
        CHECK(r.Update(kFlagInWater | kFlagDead, 20).anim == BodyAnim::Dead);
        CHECK(r.Holding() == BodyAnim::None);
        BodyReactions k;
        k.Update(kFlagInWater, 0);
        CHECK(k.Update(kFlagInWater | kFlagKnocked, 20).anim == BodyAnim::Down);
        BodyReactions c;
        c.Update(kFlagWallClimb, 0);
        CHECK(c.Update(kFlagWallClimb | kFlagInWater, 20).anim == BodyAnim::None);
        CHECK(c.Holding() == BodyAnim::Climb);
        BodyReactions j;
        j.Update(kFlagJetpack, 0);
        CHECK(j.Update(kFlagJetpack | kFlagInWater, 20).anim == BodyAnim::None);
        CHECK(j.Holding() == BodyAnim::Jetpack);
        BodyReactions f;
        CHECK(f.Update(kFlagInWater | kFlagFalling, 0).anim == BodyAnim::Tread);                // the fall does not pre-empt the water
        CHECK(f.Update(kFlagFalling, 20).anim == BodyAnim::Clear);
        CHECK(f.Update(kFlagFalling, 40).anim == BodyAnim::Fall);
    }

    { // a clock that goes back never leaves the dive stuck
        BodyReactions r;
        r.Update(kFlagInWater | kFlagUnderwater, 100000);
        CHECK(r.Update(kFlagInWater | kFlagUnderwater, 10).anim == BodyAnim::Under);
    }

    { // a late update never plays the stages that are already over, and the flag dropping early clears at once
        BodyReactions r;
        CHECK(r.Update(kFlagWallClimb, 0).anim == BodyAnim::Climb);
        CHECK(r.Update(kFlagWallClimb, 1500).anim == BodyAnim::ClimbStand);        // the pull is skipped
        CHECK(r.Update(kFlagWallClimb, 1600).anim == BodyAnim::None);
        BodyReactions q;
        q.Update(kFlagWallClimb, 0);
        CHECK(q.Update(0, 200).anim == BodyAnim::Clear);
    }

    { // climbing begins from a fall, and the climb never goes back to the fall by itself
        BodyReactions r;
        CHECK(r.Update(kFlagFalling, 0).anim == BodyAnim::Fall);
        CHECK(r.Update(kFlagFalling | kFlagWallClimb, 20).anim == BodyAnim::Climb);
        CHECK(r.Update(kFlagFalling | kFlagWallClimb, 40).anim == BodyAnim::None);
    }

    { // dying or being knocked down wins over the climb; the pain voice still works
        BodyReactions r;
        r.Update(kFlagWallClimb | Hurt(1), 0);
        CHECK(r.Update(kFlagWallClimb | Hurt(2), 100).sayContext == kSayPainLow);
        CHECK(r.Update(kFlagWallClimb | kFlagDead | Hurt(2), 200).anim == BodyAnim::Dead);
        BodyReactions q;
        q.Update(kFlagWallClimb, 0);
        CHECK(q.Update(kFlagWallClimb | kFlagKnocked, 100).anim == BodyAnim::Down);
        CHECK(q.Update(kFlagWallClimb | kFlagKnocked, 200).anim == BodyAnim::None);
    }

    { // a clock that goes back never leaves the climb stuck at an early stage
        BodyReactions r;
        r.Update(kFlagWallClimb, 100000);
        CHECK(r.Update(kFlagWallClimb, 10).anim == BodyAnim::ClimbStand);          // an unsigned difference: it counts as long over
    }

    std::printf("test_body_reactions OK\n");
    return 0;
}
