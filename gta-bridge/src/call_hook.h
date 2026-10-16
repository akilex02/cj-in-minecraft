#pragma once
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <cstring>

namespace cj {

// Platform hooks. On Windows they make code writable and flush the instruction cache; native tests leave them null.
using MemProtectFn = bool (*)(void* addr, size_t size, bool writable);
inline MemProtectFn g_memProtect = nullptr;
using HookLogFn = void (*)(const char* line);
inline HookLogFn g_hookLog = nullptr;

// Redirects one `call rel32` to a replacement function. The replacement chains to what the call used to
// target. If another plugin later overwrites the call, Watch() re-hooks it and chains through the new
// target, keeping every plugin in the chain.
class CallHook {
public:
    static constexpr int kMaxRehooks = 5;

    CallHook(const char* name, uint8_t* site, void* replacement, uintptr_t trueFunction)
        : name_(name), site_(site), replacement_(replacement), trueFn_(trueFunction) {}

    // Returns false, patching nothing, when the site is not a call instruction.
    bool Install() {
        if (site_[0] != 0xE8) {
            Logf("%s: 0x%X is not a call (first byte %02X); hook not installed", name_, Addr(site_), site_[0]);
            return false;
        }
        chains_[0] = TargetOf(ReadRel());
        count_.store(1, std::memory_order_release);
        if (!Patch()) {
            return false;
        }
        installed_ = true;
        Logf("%s: hook installed at 0x%X (original target 0x%X)", name_, Addr(site_), Addr(chains_[0]));
        return true;
    }

    // Poll this from a thread; cheap when nothing changed.
    void Watch() {
        if (!installed_) {
            return;
        }
        const int32_t rel = ReadRel();
        const uint8_t op = site_[0];
        if (op == 0xE8 && rel == patchedRel_) {
            pendingValid_ = false;
            return;
        }
        if (givenUp_) {
            return;
        }
        if (rehooks_ >= kMaxRehooks) {
            if (!reportedLimit_) {
                reportedLimit_ = true;
                Logf("%s: no more re-hooks (%d used); later changes are ignored", name_, rehooks_);
            }
            return;
        }
        // Another plugin may still be writing the operand: act only on two equal readings in a row.
        const uint64_t key = (static_cast<uint64_t>(op) << 32) | static_cast<uint32_t>(rel);
        if (!pendingValid_ || pendingKey_ != key) {
            pendingValid_ = true;
            pendingKey_ = key;
            return;
        }
        pendingValid_ = false;
        if (op != 0xE8) {
            givenUp_ = true;
            Logf("%s: call site was replaced by something that is not a call (bytes %02X %02X %02X %02X %02X); giving up",
                 name_, site_[0], site_[1], site_[2], site_[3], site_[4]);
            return;
        }
        const uintptr_t target = TargetOf(rel);
        const uint32_t n = count_.load(std::memory_order_relaxed);
        if (target == chains_[n - 1]) {
            // The plugin we already chain to put its own hook back: chaining to it twice would run it twice.
            if (!reportedRepatch_) {
                reportedRepatch_ = true;
                Logf("%s: the plugin at 0x%X re-applied its hook; re-patching without re-chaining", name_, Addr(target));
            }
            Patch();
            return;
        }
        ++rehooks_;
        chains_[n] = target; // newest last; written before the count that publishes it
        count_.store(n + 1, std::memory_order_release);
        Logf("%s: another plugin changed the call, it now targets 0x%X; re-hooking and chaining through it (%d/%d)",
             name_, Addr(target), rehooks_, kMaxRehooks);
        Patch();
    }

    bool Called() const { return called_; }

    // Used inside the replacement function:
    //   CallHook::Scope scope(hook);
    //   call(scope.Target());
    //   if (scope.Outermost()) { ...our work... }
    class Scope {
    public:
        explicit Scope(CallHook& hook) : hook_(hook), depth_(hook.depth_++) {
            if (!hook.called_) {
                hook.called_ = true;
                hook.Logf("%s: first call from the game loop", hook.name_);
            }
        }
        ~Scope() { --hook_.depth_; }
        Scope(const Scope&) = delete;
        Scope& operator=(const Scope&) = delete;

        // What to call as "the original". Plugins that overwrote the call and saved our wrapper as their own original
        // call back into us: each nested call (depth) goes one plugin further back along the chain list, and past the
        // oldest one it reaches the real function.
        uintptr_t Target() const {
            const uint32_t n = hook_.count_.load(std::memory_order_acquire);
            return depth_ < static_cast<int>(n) ? hook_.chains_[n - 1 - static_cast<uint32_t>(depth_)] : hook_.trueFn_;
        }
        bool Outermost() const { return depth_ == 0; }

    private:
        CallHook& hook_;
        int depth_;
    };

private:
    static unsigned Addr(const void* p) { return static_cast<unsigned>(reinterpret_cast<uintptr_t>(p)); }
    static unsigned Addr(uintptr_t v) { return static_cast<unsigned>(v); }

    int32_t ReadRel() const {
        int32_t rel = 0;
        std::memcpy(&rel, site_ + 1, sizeof(rel));
        return rel;
    }
    uintptr_t TargetOf(int32_t rel) const {
        return reinterpret_cast<uintptr_t>(site_) + 5 + static_cast<uintptr_t>(static_cast<intptr_t>(rel));
    }

    bool Patch() {
        const intptr_t delta = reinterpret_cast<intptr_t>(replacement_) - static_cast<intptr_t>(reinterpret_cast<uintptr_t>(site_) + 5);
        const int32_t rel = static_cast<int32_t>(delta);
        if (g_memProtect && !g_memProtect(site_, 5, true)) {
            Logf("%s: could not make the call site writable", name_);
            return false;
        }
        patchedRel_ = rel;
        std::memcpy(site_ + 1, &rel, sizeof(rel)); // a single 4-byte store inside one cache line
        if (g_memProtect) {
            g_memProtect(site_, 5, false);
        }
        return true;
    }

    template <class... Args>
    void Logf(const char* fmt, Args... args) const {
        if (!g_hookLog) {
            return;
        }
        char line[256];
        std::snprintf(line, sizeof(line), fmt, args...);
        g_hookLog(line);
    }

    const char* name_;
    uint8_t* site_;
    void* replacement_;
    uintptr_t trueFn_;
    uintptr_t chains_[kMaxRehooks + 1]{}; // [0] what the call targeted at install; later entries are plugins that came after
    std::atomic<uint32_t> count_{0};
    int32_t patchedRel_ = 0;
    bool installed_ = false;
    bool called_ = false;
    int depth_ = 0;
    int rehooks_ = 0;
    bool givenUp_ = false;
    bool reportedLimit_ = false;
    bool reportedRepatch_ = false;
    bool pendingValid_ = false;
    uint64_t pendingKey_ = 0;
};

} // namespace cj
