#include <windows.h>

#include "hook.h"
#include "log.h"
#include "ped_layout.h"

namespace {

// GTA sleeps inside its message loop (before CGame::Process runs) while ForegroundApp is 0,
// so the flag has to be forced from outside the game loop. The same thread keeps both hooks alive.
DWORD WINAPI KeepAliveThread(LPVOID) {
    for (;;) {
        *reinterpret_cast<volatile uint8_t*>(cj::gta::kForegroundApp) = 1;
        cj::WatchHooks();
        Sleep(20);
    }
}

} // namespace

BOOL APIENTRY DllMain(HMODULE module, DWORD reason, LPVOID) {
    if (reason == DLL_PROCESS_ATTACH) {
        DisableThreadLibraryCalls(module);
        cj::Log("---- cj_bridge loaded ----");
        if (cj::InstallHooks()) {
            CreateThread(nullptr, 0, KeepAliveThread, nullptr, 0, nullptr);
        }
    }
    return TRUE;
}
