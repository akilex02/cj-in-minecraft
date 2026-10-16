#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "call_hook.h"

#define CHECK(cond)                                                                        \
    do {                                                                                   \
        if (!(cond)) {                                                                     \
            std::fprintf(stderr, "%s:%d: CHECK failed: %s\n", __FILE__, __LINE__, #cond);   \
            std::exit(1);                                                                  \
        }                                                                                  \
    } while (0)

using namespace cj;

static std::vector<std::string> g_events;
static std::vector<std::string> g_logs;
static CallHook* g_hook = nullptr;
alignas(16) static uint8_t g_site[16];

static void LogCapture(const char* line) { g_logs.emplace_back(line); }

static bool LogHas(const char* needle) {
    for (const auto& l : g_logs) {
        if (l.find(needle) != std::string::npos) return true;
    }
    return false;
}

// The real function, a plugin X that was already hooked in before us, and our replacement.
static void True() { g_events.push_back("True"); }
static void X() { g_events.push_back("X"); True(); }
static void Ours() {
    CallHook::Scope scope(*g_hook);
    reinterpret_cast<void (*)()>(scope.Target())();
    if (scope.Outermost()) {
        g_events.push_back("tick");
    }
}
// A plugin that overwrote the call and kept calling the real function directly (what happened in the user's game).
static void W2() { g_events.push_back("W2"); True(); }
// Plugins that overwrote the call after our patch and saved OUR wrapper as their "original".
static void W() { g_events.push_back("W"); Ours(); }
static void W3() { g_events.push_back("W3"); Ours(); }

// Six distinct plugins that overwrite the call and go straight to the real function.
static void P1() { g_events.push_back("P1"); True(); }
static void P2() { g_events.push_back("P2"); True(); }
static void P3() { g_events.push_back("P3"); True(); }
static void P4() { g_events.push_back("P4"); True(); }
static void P5() { g_events.push_back("P5"); True(); }
static void P6() { g_events.push_back("P6"); True(); }

static void Poke(void (*fn)()) {
    g_site[0] = 0xE8;
    const intptr_t delta = reinterpret_cast<intptr_t>(fn) - reinterpret_cast<intptr_t>(g_site + 5);
    const int32_t rel = static_cast<int32_t>(delta);
    CHECK(static_cast<intptr_t>(rel) == delta); // fits in rel32
    std::memcpy(g_site + 1, &rel, 4);
}

// What the game does: execute the call instruction.
static void GameCallsTheSite() {
    int32_t rel = 0;
    std::memcpy(&rel, g_site + 1, 4);
    const uintptr_t target = reinterpret_cast<uintptr_t>(g_site + 5) + static_cast<uintptr_t>(static_cast<intptr_t>(rel));
    reinterpret_cast<void (*)()>(target)();
}

static std::vector<std::string> Run() {
    g_events.clear();
    GameCallsTheSite();
    return g_events;
}

static void Fresh(CallHook& hook) {
    g_hook = &hook;
    g_logs.clear();
}

int main() {
    g_hookLog = LogCapture;
    using V = std::vector<std::string>;

    { // Install redirects the call and chains to what it pointed at
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        CHECK((Run() == V{"X", "True", "tick"}));
        CHECK(hook.Called());
        hook.Watch();
        hook.Watch();
        CHECK((Run() == V{"X", "True", "tick"})); // nothing changed, nothing re-hooked
    }

    { // Installing over something that is not a call patches nothing
        g_site[0] = 0x90;
        const uint8_t before[5] = {g_site[0], g_site[1], g_site[2], g_site[3], g_site[4]};
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(!hook.Install());
        CHECK(std::memcmp(before, g_site, 5) == 0);
    }

    { // Another plugin overwrites the call and goes straight to the real function: we re-hook and chain through it
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        Poke(W2);
        CHECK((Run() == V{"W2", "True"})); // until we notice, our tick does not run
        hook.Watch(); // Review Focus #7: one reading is not enough to act on
        CHECK((Run() == V{"W2", "True"}));
        hook.Watch(); // the same value twice in a row: act
        CHECK((Run() == V{"W2", "True", "tick"}));
        CHECK(LogHas("re-hooking"));
    }

    { // Review Focus #5: a plugin that saved OUR wrapper as its original must not push plugin X out of the chain
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        Poke(W);
        hook.Watch();
        hook.Watch();
        // game -> Ours -> W -> Ours (re-entered: goes to what we chained before W, which is X) -> True; one tick
        CHECK((Run() == V{"W", "X", "True", "tick"}));
    }

    { // Review Focus #5 / Finding 2: a second plugin chaining back into us neither recurses forever nor drops anyone
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        Poke(W);
        hook.Watch();
        hook.Watch();
        Poke(W3);
        hook.Watch();
        hook.Watch();
        // game -> Ours -> W3 -> Ours (depth 1: W) -> Ours (depth 2: X) -> True: every plugin runs exactly once
        CHECK((Run() == V{"W3", "W", "X", "True", "tick"}));
    }

    { // Finding 2: a plugin that re-applies its own hook is chained once, not twice, and does not use up the re-hook budget
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        Poke(W);
        hook.Watch();
        hook.Watch(); // re-hooked: chain is X, W
        for (int i = 0; i < 10; i++) {
            Poke(W); // W puts its hook back, again and again
            hook.Watch();
            hook.Watch();
        }
        CHECK((Run() == V{"W", "X", "True", "tick"})); // W once, X once
        Poke(W3); // a different plugin still gets chained afterwards: the budget was not spent on W
        hook.Watch();
        hook.Watch();
        CHECK((Run() == V{"W3", "W", "X", "True", "tick"}));
    }

    { // A call replaced by something that is not a call: give up quietly, never touch it
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        g_site[0] = 0xE9; // a jmp
        hook.Watch();
        hook.Watch();
        CHECK(g_site[0] == 0xE9);
        CHECK(LogHas("giving up"));
        const size_t logs = g_logs.size();
        hook.Watch();
        hook.Watch();
        CHECK(g_logs.size() == logs); // quiet afterwards
    }

    { // A reading that keeps changing is a plugin still writing: it is never acted on
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        Poke(W2);
        hook.Watch();
        Poke(W3); // changed between two polls
        hook.Watch();
        CHECK(!LogHas("re-hooking"));
        hook.Watch(); // now two equal readings in a row
        CHECK(LogHas("re-hooking"));
    }

    { // After kMaxRehooks different plugins the hook stops fighting and says so once
        Poke(X);
        CallHook hook("test", g_site, reinterpret_cast<void*>(&Ours), reinterpret_cast<uintptr_t>(&True));
        Fresh(hook);
        CHECK(hook.Install());
        void (*plugins[])() = {P1, P2, P3, P4, P5};
        static_assert(sizeof(plugins) / sizeof(plugins[0]) == CallHook::kMaxRehooks);
        for (auto* plugin : plugins) {
            Poke(plugin);
            hook.Watch();
            hook.Watch();
        }
        CHECK(!LogHas("no more re-hooks"));
        Poke(P6);
        hook.Watch();
        hook.Watch();
        CHECK(LogHas("no more re-hooks"));
        const size_t logs = g_logs.size();
        hook.Watch();
        hook.Watch();
        CHECK(g_logs.size() == logs);
    }

    std::puts("test_call_hook OK");
    return 0;
}
