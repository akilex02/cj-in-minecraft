#include "hook.h"

#include <windows.h>

#include <cstdint>
#include <cstring>

#include "bridge.h"
#include "call_hook.h"
#include "log.h"
#include "ped_layout.h"

namespace cj {
namespace {

void __cdecl HookedProcess();
void __cdecl HookedUpdatePads();

CallHook g_processHook("CGame::Process call", reinterpret_cast<uint8_t*>(gta::kProcessCallSite),
                       reinterpret_cast<void*>(&HookedProcess), gta::kProcessFunc);
CallHook g_padHook("CPad::UpdatePads call", reinterpret_cast<uint8_t*>(gta::kUpdatePadsCallSite),
                   reinterpret_cast<void*>(&HookedUpdatePads), gta::kUpdatePadsFunc);

void __cdecl HookedProcess() {
    CallHook::Scope scope(g_processHook);
    reinterpret_cast<void(__cdecl*)()>(scope.Target())();
    if (scope.Outermost()) {
        bridge::Tick();
    }
}

void __cdecl HookedUpdatePads() {
    CallHook::Scope scope(g_padHook);
    reinterpret_cast<void(__cdecl*)()>(scope.Target())();
    if (scope.Outermost()) {
        bridge::PrePad();
    }
}

// FindPlayerPed: mov eax,[esp+4]; test eax,eax; jge +7; movzx eax,byte [0xB7CD74]; imul eax,eax,0x190; mov eax,[eax+0xB7CD98]
bool AddressMapMatches() {
    static const uint8_t expected[] = {0x8B, 0x44, 0x24, 0x04, 0x85, 0xC0, 0x7D, 0x07, 0x0F, 0xB6, 0x05, 0x74, 0xCD,
                                       0xB7, 0x00, 0x69, 0xC0, 0x90, 0x01, 0x00, 0x00, 0x8B, 0x80, 0x98, 0xCD, 0xB7};
    return std::memcmp(reinterpret_cast<const void*>(gta::kFindPlayerPed), expected, sizeof(expected)) == 0;
}

bool ProtectCode(void* addr, size_t size, bool writable) {
    static DWORD previous = 0;
    if (writable) {
        return VirtualProtect(addr, size, PAGE_EXECUTE_READWRITE, &previous) != 0;
    }
    DWORD ignored = 0;
    const bool ok = VirtualProtect(addr, size, previous, &ignored) != 0;
    FlushInstructionCache(GetCurrentProcess(), addr, size);
    return ok;
}

void LogLine(const char* line) { Log("%s", line); }

} // namespace

bool InstallHooks() {
    g_memProtect = ProtectCode;
    g_hookLog = LogLine;
    if (!AddressMapMatches()) {
        Log("ADDRESS MISMATCH: FindPlayerPed @0x%X does not match gta-reversed; hooks not installed", (unsigned)gta::kFindPlayerPed);
        return false;
    }
    if (!g_processHook.Install()) {
        return false;
    }
    if (!g_padHook.Install()) {
        Log("the pad hook is not installed: CJ will not be driven (poses still flow)");
    }
    return true;
}

void WatchHooks() {
    g_processHook.Watch();
    g_padHook.Watch();
}

} // namespace cj
