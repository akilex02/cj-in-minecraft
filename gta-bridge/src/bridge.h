#pragma once

namespace cj::bridge {
// Once per game frame, on the game thread, right after CGame::Process.
void Tick();
// Right after CPad::UpdatePads inside CGame::Process: writes the simulated pad.
void PrePad();
} // namespace cj::bridge
