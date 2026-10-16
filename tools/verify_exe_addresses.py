#!/usr/bin/env python3
"""Checks that gta_sa.exe matches the 1.0 US address map used by gta-reversed."""
import struct
import sys
from pathlib import Path

IMAGE_BASE = 0x400000


def load_sections(data):
    pe = struct.unpack_from("<I", data, 0x3C)[0]
    count = struct.unpack_from("<H", data, pe + 6)[0]
    opt_size = struct.unpack_from("<H", data, pe + 20)[0]
    table = pe + 24 + opt_size
    sections = []
    for i in range(count):
        _, vsize, va, rsize, raw = struct.unpack_from("<8sIIII", data, table + i * 40)
        sections.append((IMAGE_BASE + va, min(vsize, rsize), raw))
    return sections


def read_va(data, sections, va, size):
    for start, length, raw in sections:
        if start <= va and va + size <= start + length:
            offset = raw + va - start
            return data[offset:offset + size]
    raise ValueError(f"VA {va:#x} is not backed by the file")


def main():
    path = Path(sys.argv[1] if len(sys.argv) > 1 else "GTA San Andreas/gta_sa.exe")
    data = path.read_bytes()
    sections = load_sections(data)
    failures = 0

    def check(name, ok, detail=""):
        nonlocal failures
        print(("OK   " if ok else "FAIL ") + name + (f" ({detail})" if detail else ""))
        failures += 0 if ok else 1

    # CWorld::FindPlayerPed: mov eax,[esp+4]; test eax,eax; jge +7; movzx eax,[PlayerInFocus 0xB7CD74];
    # imul eax,eax,0x190 (sizeof CPlayerInfo); mov eax,[eax+Players 0xB7CD98]
    expected = bytes.fromhex("8b44240485c07d070fb60574cdb70069c0900100008b8098cdb7")
    got = read_va(data, sections, 0x56E210, len(expected))
    check("FindPlayerPed @0x56E210 matches gta-reversed", got == expected, got.hex())

    # Idle() calls CGame::Process at 0x53E981 (E8 rel32 -> 0x53BEE0)
    call = read_va(data, sections, 0x53E981, 5)
    target = (0x53E981 + 5 + struct.unpack("<i", call[1:])[0]) & 0xFFFFFFFF
    check("call site @0x53E981 is E8 -> CGame::Process 0x53BEE0", call[0] == 0xE8 and target == 0x53BEE0, f"{call.hex()} -> {target:#x}")

    # CGame::Process prologue
    prologue = read_va(data, sections, 0x53BEE0, 7)
    check("CGame::Process prologue @0x53BEE0", prologue == bytes.fromhex("83ec0c535657e8"), prologue.hex())

    # CGame::Process calls CPad::UpdatePads at 0x53BEE6 (E8 rel32 -> 0x541DD0) before the world runs
    pads = read_va(data, sections, 0x53BEE6, 5)
    pads_target = (0x53BEE6 + 5 + struct.unpack("<i", pads[1:])[0]) & 0xFFFFFFFF
    check("call site @0x53BEE6 is E8 -> CPad::UpdatePads 0x541DD0", pads[0] == 0xE8 and pads_target == 0x541DD0, f"{pads.hex()} -> {pads_target:#x}")

    # The game functions M2 calls: the first bytes of each, read from this exe (they all look like real prologues).
    for name, address, expected in (
        ("CPed::GiveWeapon", 0x5E6080, "51538b5c240c5556"),
        ("CPed::SetCurrentWeapon(type)", 0x5E6280, "8b442404566a0150"),
        ("CPed::ClearWeapon(type)", 0x5E62B0, "56578b7c240c6a01"),
        ("CProjectileInfo::RemoveAllProjectiles()", 0x7399B0, "53555657e9649fccffbfc891"),
        ("CAnimManager::BlendAnimation(clump, group, id, delta)", 0x4D4610, "83ec148b4c24188b1534"),
        ("CPed::Say(context, ...)", 0x5EFFE0, "8b4424046685c0742a8b"),
        ("RpAnimBlendClumpGetFirstAssociation(clump)", 0x4D15E0, "8b44240490e9"),
        ("CAnimManager::AddAnimation(clump, group, id)", 0x4D3AA0, "8b44240c8b1534eab400"),
        ("CAnimManager::GetAnimationBlockIndex(name)", 0x4D3990, "8b44240450e8a6ffffff"),
        ("CAnimManager::AddAnimBlockRef(index)", 0x4D3FB0, "8b442404c1e00505a0d4"),
        ("CAEPedAudioEntity::TurnOnJetPack", 0x4E28A0, "568bf18b8e94000000"),
        ("CAEPedAudioEntity::UpdateJetPack", 0x4E0EE0, "8a819800000084c00f84"),
        ("CAEPedAudioEntity::TurnOffJetPack", 0x4E2A70, "568bf1e8a8e6ffff"),
        ("CAEPedAudioEntity::StopJetPackSound", 0x4E1120, "568bf18b8e9c000000"),
        ("CTask::operator new", 0x61A5A0, "8b0da844b700e955"),
        ("CTaskSimpleUseGun ctor", 0x61DE60, "6aff68d8f3830064"),
        ("CTaskSimpleUseGun::ControlGun", 0x61E040, "558b6c240c568bf1"),
        ("CTaskManager::SetTaskSecondary", 0x681B60, "538b5c240856578b"),
        ("CStreaming::RequestModel", 0x4087E0, "538b5c240c558b6c"),
        ("CStreaming::LoadAllRequestedModels", 0x40EA10, "e949baffff5633f6"),
        ("CCarCtrl::CreateCarForScript", 0x431F80, "6aff68bb74830064"),
        ("CTaskSimpleCarSetPedInAsDriver ctor", 0x6470E0, "6aff68e826840064"),
        ("CTaskSimpleCarSetPedInAsDriver::ProcessPed", 0x64B950, "6aff64a100000000"),
        ("CPedIntelligence::FlushImmediately", 0x601640, "6aff6826e8830064"),
        ("CPed::Teleport", 0x5E4110, "568bf18b86980500"),
        ("CWorld::Remove", 0x563280, "568b7424088b068b"),
        ("CAutomobile::Teleport", 0x6A9CA0, "568bf156e8d795eb"),
        ("CWorld::ProcessVerticalLine", 0x5674E0, "83ec2c90909090e9"),
        ("CAudioEngine::RetuneRadio", 0x507E10, "b954e58a00e8"),
        ("CTaskSimpleRunNamedAnim::ctor", 0x61A990, "6aff6828f1830064"),
    ):
        got = read_va(data, sections, address, len(expected) // 2)
        check(f"{name} @{address:#x}", got.hex() == expected, got.hex())

    print("size:", len(data), "bytes")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
