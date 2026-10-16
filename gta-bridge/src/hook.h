#pragma once

namespace cj {
// Redirects the `call CGame::Process` in Idle() (runs bridge::Tick after it) and the `call CPad::UpdatePads`
// inside CGame::Process (runs bridge::PrePad after it). Returns false, patching nothing, if the exe does not
// match the expected address map or the Process call cannot be hooked.
bool InstallHooks();

// Poll from a thread: re-hooks either call if another plugin overwrote it.
void WatchHooks();
} // namespace cj
