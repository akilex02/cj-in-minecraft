#include "bridge.h"

#include <winsock2.h>
#include <windows.h>

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstring>

#include "bridge_core.h"
#include "gun_game.h"
#include "log.h"
#include "ped_layout.h"
#include "vehicle_game.h"

namespace cj::bridge {
namespace {

constexpr uint16_t kGtaPort = 34560;  // we listen here
constexpr uint16_t kEchoPort = 34561; // Minecraft (or the probe) listens here

SOCKET OpenSocket() {
    WSADATA wsa;
    if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) {
        Log("WSAStartup failed");
        return INVALID_SOCKET;
    }
    SOCKET s = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (s == INVALID_SOCKET) {
        Log("socket() failed: %d", WSAGetLastError());
        return INVALID_SOCKET;
    }
    u_long nonBlocking = 1;
    ioctlsocket(s, FIONBIO, &nonBlocking);
    // Windows reports ICMP "port unreachable" from a previous sendto as a recv error; turn that off.
    BOOL noConnReset = FALSE;
    DWORD bytes = 0;
    WSAIoctl(s, 0x9800000C /* SIO_UDP_CONNRESET */, &noConnReset, sizeof(noConnReset), nullptr, 0, &bytes, nullptr, nullptr);

    sockaddr_in addr{};
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
    addr.sin_port = htons(kGtaPort);
    if (bind(s, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) != 0) {
        Log("bind(127.0.0.1:%u) failed: %d", kGtaPort, WSAGetLastError());
        closesocket(s);
        return INVALID_SOCKET;
    }
    Log("listening on 127.0.0.1:%u, sending to 127.0.0.1:%u", kGtaPort, kEchoPort);
    return s;
}

uint8_t* FocusedPlayerPed() {
    const uint8_t focus = *reinterpret_cast<volatile uint8_t*>(gta::kPlayerInFocus);
    if (focus >= gta::kMaxPlayers) {
        return nullptr;
    }
    auto* info = reinterpret_cast<uint8_t*>(gta::kPlayersArray) + focus * gta::kPlayerInfoSize;
    return *reinterpret_cast<uint8_t**>(info); // CPlayerInfo::m_pPed is the first member; null before a game is loaded
}

// Winsock and the live game behind the BridgeIo interface that BridgeCore (and its tests) use.
// --- the turn of CJ in GTA ------------------------------------------------------------------------------------------------
// The matrix of the ped (heading H: right = (cos, sin, 0), forward = (-sin, cos, 0)) and the rotation fields the game keeps in
// step with it. The fields are only written when they agreed with the matrix when first seen (their offsets were not verified
// before this); otherwise only the matrix is turned.
bool g_rotationChecked = false;
bool g_rotationOk = false;

void ApplyHeading(uint8_t* ped, float heading) {
    RwMat* matrix = PedMatrix(ped);
    if (!matrix) {
        return;
    }
    float* current = reinterpret_cast<float*>(ped + gta::kPedCurrentRotation);
    float* aiming = reinterpret_cast<float*>(ped + gta::kPedAimingRotation);
    if (!g_rotationChecked) {
        g_rotationChecked = true;
        const float matrixHeading = std::atan2(-matrix->up[0], matrix->up[1]);
        float diff = std::fmod(std::fabs(*current - matrixHeading), 6.2831853f);
        diff = std::min(diff, 6.2831853f - diff);
        g_rotationOk = std::isfinite(*current) && diff < 0.3f;
        cj::Log("ped rotation fields: current=%.3f aiming=%.3f matrix heading=%.3f -> %s", *current, *aiming, matrixHeading,
                g_rotationOk ? "in step, will be written" : "NOT in step, matrix only");
    }
    const float c = std::cos(heading);
    const float s = std::sin(heading);
    matrix->right[0] = c;
    matrix->right[1] = s;
    matrix->up[0] = -s;
    matrix->up[1] = c;
    if (g_rotationOk) {
        *current = heading;
        *aiming = heading;
    }
}

class WinIo : public BridgeIo {
public:
    bool Open() {
        sock_ = OpenSocket();
        sendTo_.sin_family = AF_INET;
        sendTo_.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        sendTo_.sin_port = htons(kEchoPort);
        return sock_ != INVALID_SOCKET;
    }

    uint8_t* Ped() override { return FocusedPlayerPed(); }
    uint8_t* Pad() override { return reinterpret_cast<uint8_t*>(gta::kPadsArray); }
    float* PedDensity() override { return reinterpret_cast<float*>(gta::kPedDensityMultiplier); }
    float* CarDensity() override { return reinterpret_cast<float*>(gta::kCarDensityMultiplier); }
    uint32_t NowMs() override { return GetTickCount(); }

    int Recv(void* buf, size_t cap) override {
        for (;;) {
            const int n = recv(sock_, static_cast<char*>(buf), static_cast<int>(cap), 0);
            if (n != SOCKET_ERROR) {
                return n;
            }
            const int err = WSAGetLastError();
            if (err == WSAEMSGSIZE) {
                return static_cast<int>(cap) + 1; // oversized datagram: consumed, will be rejected by the parser
            }
            if (err == WSAECONNRESET) {
                continue; // stale ICMP error from an earlier sendto: skip it
            }
            return -1; // WSAEWOULDBLOCK: nothing left
        }
    }

    void SendEcho(const EchoPacket& echo) override { Send(&echo, sizeof(echo)); }
    void SendPose(const PosePacket& pose) override { Send(&pose, pose.Size()); }

    // RpHAnimHierarchy* GetAnimHierarchyFromSkinClump(RpClump*): numNodes, the world-space matrix array and the node ids.
    bool ReadSkeleton(uint8_t* ped, Skeleton& out) override {
        void* clump = *reinterpret_cast<void**>(ped + gta::kEntityClump);
        if (!clump) {
            return false;
        }
        // The game puts the breast bones (they move the shoulder's skin) right in its PreRender, after the pose is read here: do it first, or they
        // arrive as the raw animation left them and the shoulders fold in as soon as an arm moves. It copies from the upper arms, so doing it twice is harmless.
        reinterpret_cast<void(__cdecl*)(void*)>(gta::kShoulderBoneRotation)(clump);
        using GetHierarchyFn = uint8_t*(__cdecl*)(void*);
        auto* hierarchy = reinterpret_cast<GetHierarchyFn>(gta::kGetAnimHierarchyFromSkinClump)(clump);
        if (!hierarchy) {
            return false;
        }
        const int32_t count = *reinterpret_cast<int32_t*>(hierarchy + gta::kHierNumNodes);
        const auto* matrices = *reinterpret_cast<const RwMat**>(hierarchy + gta::kHierMatrixArray);
        const uint8_t* nodeInfo = *reinterpret_cast<const uint8_t**>(hierarchy + gta::kHierNodeInfo);
        if (count <= 0 || count > static_cast<int32_t>(kMaxBones) || !matrices || !nodeInfo) {
            return false;
        }
        out.matrices = matrices;
        out.count = count;
        for (int32_t i = 0; i < count; i++) {
            out.ids[i] = *reinterpret_cast<const int32_t*>(nodeInfo + static_cast<size_t>(i) * gta::kNodeInfoStride);
        }
        return true;
    }

    // The blows of GTA's fight task so far: it is read off the ped's attack-slot task (see StrikeDetector and docs/melee-spike.md).
    uint32_t MeleeStrikes(uint8_t* ped) override {
        uint8_t* manager = gungame::TaskManager(ped);
        uint8_t* task = manager ? gungame::AttackTask(manager) : nullptr;
        bool alive = false;
        int8_t move = -1;
        uintptr_t anim = 0;
        float time = 0.0f;
        float hitTime = 0.0f;
        if (task) {
            void** vtable = *reinterpret_cast<void***>(task);
            using TypeFn = int(__attribute__((thiscall)) *)(void*);
            if (reinterpret_cast<TypeFn>(vtable[4])(task) == gta::kTaskFightType) {
                alive = true;
                move = static_cast<int8_t>(task[gta::kFightMove]);
                const int8_t combo = static_cast<int8_t>(task[gta::kFightComboSet]);
                uint8_t* animation = *reinterpret_cast<uint8_t**>(task + gta::kFightAnim);
                if (animation && reinterpret_cast<uintptr_t>(animation) > 0x10000 && combo >= 0 && combo < 12 && move >= 0 && move <= 2) {
                    anim = reinterpret_cast<uintptr_t>(animation);
                    time = *reinterpret_cast<float*>(animation + gta::kAnimCurrentTime);
                    hitTime = *reinterpret_cast<float*>(gta::kMeleeComboData + gta::kMeleeInfoSize * combo + gta::kMeleeInfoHit + 4 * move);
                }
            }
        }
        strikesTotal_ += strikeDetector_.Update(alive, move, anim, time, hitTime);
        return strikesTotal_;
    }

    uint16_t HeldWeapon(uint8_t* ped) override { return gungame::Held(ped); }
    uint32_t AmmoInClip(uint8_t* ped) override { return gungame::AmmoInClip(ped); }
    // Gives the weapon, remembering what it displaces in the same slot so that taking it away can leave the arsenal as it was.
    void GiveWeapon(uint8_t* ped, uint16_t weapon) override {
        ForgetOtherPed(ped);
        const int slot = WeaponSlot(weapon);
        const bool known = slot >= 0 && WeaponModelId(weapon) != 0;
        const uint16_t slotType = known ? gungame::SlotType(ped, slot) : 0;
        const uint32_t slotAmmo = known ? gungame::SlotAmmo(ped, slot) : 0;
        if (!gungame::Give(ped, weapon)) {
            if (weapon != lastUnsupported_) {
                lastUnsupported_ = weapon;
                cj::Log("weapon %u has no known model: not given (the game crashes without its model loaded)", static_cast<unsigned>(weapon));
            }
            return;
        }
        arsenal_.OnGive(slot, weapon, slotType, slotAmmo);
    }

    // Takes away what the bridge gave and gives back what it replaced: the rest of the player's arsenal is not touched.
    void TakeWeapons(uint8_t* ped) override {
        ForgetOtherPed(ped);
        for (const Arsenal::Step& step : arsenal_.TakeAll()) {
            gungame::Clear(ped, step.clear);
            if (step.restoreType != 0) {
                if (!gungame::Give(ped, step.restoreType, step.restoreAmmo)) {
                    cj::Log("could not give back weapon %u: it has no known model", static_cast<unsigned>(step.restoreType));
                }
            }
        }
    }
    // The game aims CJ's arm at the target we write into its own gun task, and only accepts targets within about 115 degrees to one
    // side and 130 to the other of the heading of its camera: so the camera must look the same way. (The pad aim button, which
    // keeps the game's task alive, is pressed by PadDriver.)
    void ControlGun(uint8_t* ped, Vec3 target, GunCommand) override {
        const float* pos = PedPosition(ped);
        const Vec3 muzzle{pos[0], pos[1], pos[2] + 0.7f};
        const bool cameraOk = gungame::AimCamera(target - muzzle);
        const bool taskOk = gungame::RetargetGameTask(ped, target);
        if (++gunCalls_ % 120 == 1) {
            char line[256];
            gungame::Describe(ped, line, sizeof(line));
            cj::Log("aiming: target=(%.1f %.1f %.1f) camera=%d task=%d; %s", target.x, target.y, target.z, cameraOk ? 1 : 0, taskOk ? 1 : 0, line);
        }
    }
    void StopGun(uint8_t* ped) override { gungame::Stop(ped); }
    bool GunTaskActive(uint8_t* ped) override { return gungame::GunTaskActive(ped); }

    // Turns CJ in GTA to a heading (the game does not turn him by itself when he aims).
    void SetPedHeading(uint8_t* ped, float heading) override { ApplyHeading(ped, heading); }

    void SendShot(const ShotPacket& shot) override { Send(&shot, sizeof(shot)); }

    // The animation CJ is held in (falling, lying, dead): kept so it can be faded out. The getting up removes itself and is not kept.
    // Every use checks the association still is what it was (same ped, same table, same id) before writing to it.
    // The held association is only written to while it is still in the ped's list: GTA deletes them on its own (the jump task fades and
    // removes ours), and a freed block must never be read or written.
    bool HeldIsLive(uint8_t* ped) {
        if (!bodyAssoc_ || ped != bodyPed_) {
            return false;
        }
        uint8_t* clump = *reinterpret_cast<uint8_t**>(ped + gta::kEntityClump);
        if (!clump) {
            return false;
        }
        using FirstFn = uint8_t* (__cdecl*)(void*);
        uint8_t* a = reinterpret_cast<FirstFn>(gta::kClumpFirstAssoc)(clump);
        for (int i = 0; a && i < 32; i++) {
            if (a == bodyAssoc_) {
                // the same table, id AND group: the jetpack pose has the id of the ped's own idle (3) in another group, and a block that
                // GTA freed can be handed to a new association
                const bool same = *reinterpret_cast<uintptr_t*>(a) == bodyVtable_ && *reinterpret_cast<int16_t*>(a + gta::kAnimId) == bodyId_
                                  && *reinterpret_cast<int16_t*>(a + gta::kAnimGroupId) == bodyGroup_;
                if (!same && !loggedStaleHeld_) {
                    loggedStaleHeld_ = true;
                    cj::Log("held animation: the association at the remembered address is another one (group %d id %d, expected group %d id %d)",
                        *reinterpret_cast<int16_t*>(a + gta::kAnimGroupId), *reinterpret_cast<int16_t*>(a + gta::kAnimId), bodyGroup_, bodyId_);
                }
                return same;
            }
            uint8_t* link = *reinterpret_cast<uint8_t**>(a + 4);
            a = link ? link - 4 : nullptr;
        }
        return false;
    }

    void StopHeldBodyAnim(uint8_t* ped) {
        if (HeldIsLive(ped)) {
            *reinterpret_cast<float*>(bodyAssoc_ + gta::kAnimBlendDelta) = -4.0f;
            *reinterpret_cast<uint16_t*>(bodyAssoc_ + gta::kAnimFlags) |= gta::kAnimBlendAutoRemove; // fades out and deletes itself
        }
        bodyAssoc_ = nullptr;
        bodyPed_ = nullptr;
    }

    // A pose made by hand as a partial association: BlendAnimation would fade and delete CJ's own standing and walking animations (he stays
    // lying down when it goes), and AddAnimation leaves them alone (docs/jetpack-spike.md). `clear` are flag bits to take off (the movement
    // animations of the swim group come with ones the game uses to delete them), `loop` makes it repeat. The group must be created: AddAnimation
    // on a group that is not crashes the game, so it is looked at first.
    bool GroupReady(int group) {
        uint8_t* groups = *reinterpret_cast<uint8_t**>(gta::kAnimGroupsArray);
        if (!groups || static_cast<uint32_t>(group) >= *reinterpret_cast<uint32_t*>(gta::kAnimGroupsCount)) {
            return false;
        }
        uint8_t* entry = groups + gta::kAnimGroupSize * group;
        return *reinterpret_cast<uint32_t*>(entry + 8) != 0 && *reinterpret_cast<int32_t*>(entry + 16) == group;
    }

    void PlayPartial(uint8_t* ped, int group, int id, uint16_t clear, bool loop) {
        uint8_t* clump = *reinterpret_cast<uint8_t**>(ped + gta::kEntityClump);
        if (!clump) {
            return;
        }
        if (!GroupReady(group)) {
            if (!loggedGroupNotReady_) {
                loggedGroupNotReady_ = true;
                cj::Log("animation group %d is not loaded: the pose is not played", group);
            }
            return;
        }
        StopHeldBodyAnim(ped);
        using AddFn = uint8_t* (__cdecl*)(void*, int, int);
        uint8_t* assoc = reinterpret_cast<AddFn>(gta::kAddAnimation)(clump, group, id);
        if (!assoc) {
            return;
        }
        uint16_t& flags = *reinterpret_cast<uint16_t*>(assoc + gta::kAnimFlags);
        flags = static_cast<uint16_t>(flags & ~clear);
        flags |= gta::kAnimPlaying | gta::kAnimPartial;
        if (loop) {
            flags |= gta::kAnimLooped;
        }
        *reinterpret_cast<float*>(assoc + 0x18) = 0.0f;
        *reinterpret_cast<float*>(assoc + gta::kAnimBlendDelta) = 8.0f;
        bodyAssoc_ = assoc;
        bodyPed_ = ped;
        bodyId_ = static_cast<int16_t>(id);
        bodyGroup_ = static_cast<int16_t>(group);
        bodyVtable_ = *reinterpret_cast<uintptr_t*>(assoc);
    }

    // The swim anim block is streamed in when the puppet is engaged, so the first stroke does not wait for the disk, and a reference keeps
    // it. It is looked at at every engagement: a game that is loaded again may have let go of the block, and then it is asked for again.
    void PrepareSwim() override {
        loggedGroupNotReady_ = false;
        if (GroupReady(gta::kAnimSwimGroup)) {
            return;
        }
        using IdxFn = int(__cdecl*)(const char*);
        using ReqFn = void(__cdecl*)(int, int);
        using AllFn = void(__cdecl*)(bool);
        using RefFn = void(__cdecl*)(int);
        const int index = reinterpret_cast<IdxFn>(gta::kAnimBlockIndexByName)("swim");
        if (index < 0) {
            cj::Log("the swim animation block was not found");
            return;
        }
        reinterpret_cast<ReqFn>(gta::kRequestModel)(gta::kAnimBlockModelBase + index, gta::kStreamingGameRequired | 0x8);
        reinterpret_cast<AllFn>(gta::kLoadAllRequestedModels)(false);
        reinterpret_cast<RefFn>(gta::kAnimBlockRef)(index);
        cj::Log("swim animation block %d loaded: group %d is %s", index, gta::kAnimSwimGroup, GroupReady(gta::kAnimSwimGroup) ? "ready" : "NOT ready");
    }

    // The animation of eating: a CTaskSimpleRunNamedAnim of the FOOD block in the partial-animation slot of the ped, which the task manager runs and
    // removes when it ends. The block is streamed in (and kept) the first time.
    bool EatTaskRunning(uint8_t* ped) override {
        uint8_t* manager = gungame::TaskManager(ped);
        if (!manager) {
            return false;
        }
        uint8_t* task = *reinterpret_cast<uint8_t**>(manager + gta::kTaskMgrSecondary + 4 * gta::kTaskPartialSlot);
        return task && *reinterpret_cast<uintptr_t*>(task) == gta::kNamedAnimVtable;
    }

    bool StartEating(uint8_t* ped, int kind) override {
        static const char* const kNames[] = {"EAT_Burger", "EAT_Chicken", "EAT_Pizza"};
        if (kind < 0 || kind > 2) {
            return false;
        }
        uint8_t* manager = gungame::TaskManager(ped);
        if (!manager) {
            return false;
        }
        using IdxFn = int(__cdecl*)(const char*);
        using ReqFn = void(__cdecl*)(int, int);
        using AllFn = void(__cdecl*)(bool);
        using RefFn = void(__cdecl*)(int);
        const int index = reinterpret_cast<IdxFn>(gta::kAnimBlockIndexByName)("food");
        if (index < 0) {
            cj::Log("eating: the food animation block was not found");
            return false;
        }
        reinterpret_cast<ReqFn>(gta::kRequestModel)(gta::kAnimBlockModelBase + index, gta::kStreamingGameRequired | 0x8);
        reinterpret_cast<AllFn>(gta::kLoadAllRequestedModels)(false);
        if (!foodBlockReferenced_) {
            foodBlockReferenced_ = true;
            reinterpret_cast<RefFn>(gta::kAnimBlockRef)(index);
            cj::Log("eating: the food animation block %d is loaded", index);
        }
        void* memory = reinterpret_cast<gungame::TaskNewFn>(gta::kTaskNew)(gta::kNamedAnimSize);
        if (!memory) {
            return false;
        }
        using NamedCtorFn = void*(__attribute__((thiscall)) *)(void* self, const char* name, const char* ifp, uint32_t flags, float blendDelta, int endTime,
                                                                 bool dontInterrupt, bool runInSequence, bool offsetPed, bool holdLastFrame);
        void* task = reinterpret_cast<NamedCtorFn>(gta::kNamedAnimCtor)(memory, kNames[kind], "FOOD", gta::kEatAnimFlags, 4.0f, -1, false, false, false, false);
        reinterpret_cast<gungame::SetSecondaryFn>(gta::kSetTaskSecondary)(manager, task, gta::kTaskPartialSlot);
        return true;
    }

    // GTA's own jetpack engine, through the audio entity of the ped: switched on once, updated every frame (each call moves the
    // volumes a step) and off when the mode is 0. A ped that is gone (nullptr) is only forgotten.
    void JetpackSound(uint8_t* ped, int mode) override {
        using AudioFn = void(__attribute__((thiscall)) *)(void*);
        using UpdateFn = void(__attribute__((thiscall)) *)(void*, float, float);
        if (mode == 0) {
            if (jetOn_ && ped && ped == jetPed_) {
                uint8_t* audio = ped + gta::kPedAudio;
                reinterpret_cast<AudioFn>(gta::kJetpackOff)(audio);
                reinterpret_cast<AudioFn>(gta::kJetpackStop)(audio);
            }
            jetOn_ = false;
            jetPed_ = nullptr;
            return;
        }
        if (!ped) {
            return;
        }
        if (jetOn_ && ped != jetPed_) {
            jetOn_ = false; // another ped (a loaded game): the old audio entity is gone
        }
        uint8_t* audio = ped + gta::kPedAudio;
        if (!jetOn_) {
            reinterpret_cast<AudioFn>(gta::kJetpackOn)(audio);
            jetOn_ = true;
            jetPed_ = ped;
        }
        reinterpret_cast<UpdateFn>(gta::kJetpackUpdate)(audio, mode == 2 ? gta::kJetpackThrustParam : gta::kJetpackHoverParam, 0.0f);
    }

    void PlayBodyAnim(uint8_t* ped, BodyAnim anim) override {
        uint8_t* clump = *reinterpret_cast<uint8_t**>(ped + gta::kEntityClump);
        int id = 0;
        bool hold = true;
        switch (anim) {
        case BodyAnim::Fall: id = gta::kAnimFallFall; break;
        case BodyAnim::Down:
        case BodyAnim::Dead: id = gta::kAnimLieFront; break;
        case BodyAnim::DownBack: id = gta::kAnimLieBack; break;
        case BodyAnim::GetUp: id = gta::kAnimGetUpFront; hold = false; break;
        case BodyAnim::GetUpBack: id = gta::kAnimGetUpBack; hold = false; break;
        case BodyAnim::Climb: id = gta::kAnimClimbJump; break;
        case BodyAnim::ClimbPull: id = gta::kAnimClimbPull; break;
        case BodyAnim::ClimbStand: id = gta::kAnimClimbStand; break;
        case BodyAnim::Jetpack: PlayPartial(ped, gta::kAnimJetpackGroup, gta::kAnimJetpackIdle, 0, false); return;
        case BodyAnim::Tread: PlayPartial(ped, gta::kAnimGroupPed, gta::kAnimTread, gta::kAnimSwimClear, false); return;
        case BodyAnim::Swim: PlayPartial(ped, gta::kAnimSwimGroup, gta::kAnimSwimBreast, gta::kAnimSwimClear, false); return;
        case BodyAnim::Crawl: PlayPartial(ped, gta::kAnimSwimGroup, gta::kAnimSwimCrawl, gta::kAnimSwimClear, false); return;
        case BodyAnim::Dive: PlayPartial(ped, gta::kAnimSwimGroup, gta::kAnimSwimDive, gta::kAnimSwimClear, false); return;
        case BodyAnim::Under: PlayPartial(ped, gta::kAnimSwimGroup, gta::kAnimSwimUnder, gta::kAnimSwimClear, true); return;
        default: return;
        }
        if (!clump) {
            return;
        }
        StopHeldBodyAnim(ped); // the one that was on fades out as the new one comes in
        using BlendFn = uint8_t* (__cdecl*)(void*, int, int, float);
        uint8_t* assoc = reinterpret_cast<BlendFn>(gta::kBlendAnimation)(clump, gta::kAnimGroupPed, id, 8.0f);
        if (hold && assoc) {
            bodyAssoc_ = assoc;
            bodyPed_ = ped;
            bodyId_ = static_cast<int16_t>(id);
            bodyGroup_ = static_cast<int16_t>(gta::kAnimGroupPed);
            bodyVtable_ = *reinterpret_cast<uintptr_t*>(assoc);
        }
    }

    // While a climb is on, GTA's own jump task keeps blending its jump, glide and landing animations over ours, and fades ours out
    // (docs/climb-spike.md): the held one is raised again every frame, GTA's are faded the way the game aborts them, and the held
    // one is asked for again if it was removed altogether.
    void KeepBodyAnim(uint8_t* ped, BodyAnim anim) override {
        uint8_t* clump = *reinterpret_cast<uint8_t**>(ped + gta::kEntityClump);
        if (!clump) {
            return;
        }
        using FirstFn = uint8_t* (__cdecl*)(void*);
        uint8_t* a = reinterpret_cast<FirstFn>(gta::kClumpFirstAssoc)(clump);
        const bool live = HeldIsLive(ped);
        bool found = false;
        for (int i = 0; a && i < 32; i++) {
            const int id = *reinterpret_cast<int16_t*>(a + gta::kAnimId);
            float& delta = *reinterpret_cast<float*>(a + gta::kAnimBlendDelta);
            uint16_t& flags = *reinterpret_cast<uint16_t*>(a + gta::kAnimFlags);
            if (live && a == bodyAssoc_) {
                found = true;
                if (delta < 0.0f) {
                    delta = 8.0f;
                    flags &= static_cast<uint16_t>(~gta::kAnimBlendAutoRemove);
                }
            } else if (id >= gta::kAnimJumpFirst && id <= gta::kAnimJumpLast) {
                delta = -16.0f;
                flags |= gta::kAnimBlendAutoRemove;
            }
            uint8_t* link = *reinterpret_cast<uint8_t**>(a + 4);
            a = link ? link - 4 : nullptr;
        }
        if (!found) {
            bodyAssoc_ = nullptr; // it is gone from the list: forget it without touching it
            bodyPed_ = nullptr;
            PlayBodyAnim(ped, anim);
        }
    }

    void ClearBodyAnim(uint8_t* ped) override { StopHeldBodyAnim(ped); } // nullptr: forget the held association without writing

    void SayPain(uint8_t* ped, int context) override {
        using SayFn = int16_t(__attribute__((thiscall)) *)(void*, int, uint32_t, float, bool, bool, bool);
        reinterpret_cast<SayFn>(gta::kPedSay)(ped, context, 0, 1.0f, true, true, false);
    }

    void RemoveProjectiles() override { reinterpret_cast<void(__cdecl*)()>(gta::kRemoveAllProjectiles)(); }
    void Log(const char* line) override { cj::Log("%s", line); }

    // --- the car (vehicle_game.h has the calls the spike checked in the real game) ---
    uint8_t* CreateVehicle(uint8_t* ped, int model, float heading) override {
        vehiclegame::ToPlatform(ped); // the spike of the platform: the car lives on a flat floor in the sky
        return vehiclegame::Create(ped, model, heading, vehiclegame::kPlatformZ + 1.5f);
    }
    bool SeatDriver(uint8_t* ped, uint8_t* car) override { return vehiclegame::Seat(ped, car); }
    // The car cannot be destroyed (bullet, fire, collision, melee, explosion proof and invulnerable) and neither can CJ in it; CJ's own bits come back when he gets out.
    void MakeVehicleSafe(uint8_t* car, uint8_t* ped) override {
        vehiclegame::PhysicalFlags(car) |= gta::kPhysicalProofBits;
        if (proofPed_ != ped) { // a second time the bits are already the bridge's own: they are not CJ's to save
            savedPedProof_ = vehiclegame::PhysicalFlags(ped) & gta::kPhysicalProofBits;
        }
        proofPed_ = ped;
        vehiclegame::PhysicalFlags(ped) |= gta::kPhysicalProofBits;
    }
    void RestorePed(uint8_t* ped) override {
        if (ped && ped == proofPed_) {
            uint32_t& flags = vehiclegame::PhysicalFlags(ped);
            flags = (flags & ~gta::kPhysicalProofBits) | savedPedProof_;
            proofPed_ = nullptr;
        }
    }
    // The floor in the sky is there when a line dropped from above it hits something at its height. The tiles are objects (made by a script), so the
    // probe looks at buildings and objects; the sea floor far below is not the answer.
    bool FloorReady() override {
        using LineFn = bool(__cdecl*)(const float* origin, float distance, void* colPoint, void** entity, bool buildings, bool vehicles, bool peds, bool objects,
                                      bool dummies, bool seeThrough, bool ignoreSomeObjects);
        const float origin[3] = {vehiclegame::kPlatformX, vehiclegame::kPlatformY, vehiclegame::kPlatformZ + 50.0f};
        alignas(4) uint8_t colPoint[64] = {};
        void* entity = nullptr;
        const bool hit = reinterpret_cast<LineFn>(gta::kProcessVerticalLine)(origin, -1000.0f, colPoint, &entity, true, false, false, true, false, false, false);
        float z = 0.0f;
        std::memcpy(&z, colPoint + 8, sizeof(z)); // CColPoint::m_vecPoint.z
        const bool ready = hit && std::fabs(z - vehiclegame::kPlatformZ) < 2.0f;
        if (!ready) {
            cj::Log("vehicle: line probe at the floor: hit=%d z=%.2f", hit ? 1 : 0, z);
        }
        return ready;
    }
    bool ReadVehicle(uint8_t* car, uint8_t* ped, VehicleStatePacket& out) override { return vehiclegame::Read(car, ped, out); }
    // The station the game has tuned: a byte of its radio track manager (found live: it follows the station through 1..11 and 13, the radio off).
    int RadioStation() override {
        const uint8_t station = *reinterpret_cast<const uint8_t*>(gta::kRadioTrackManager + gta::kRadioStationOffset);
        return station <= 13 ? station : -1;
    }
    // CJ's clothes (the keys of the ten models and the eighteen textures) and build (fat and muscle stats), from the game's own structures. Every pointer is
    // checked before it is followed: a wrong layout must give "no reading", never a crash.
    bool ReadOutfit(uint8_t* ped, Outfit& out) override {
        auto plausible = [](const void* p) {
            const uintptr_t a = reinterpret_cast<uintptr_t>(p);
            return a >= 0x10000 && a < 0x7FFF0000 && (a & 3) == 0;
        };
        const uint8_t* data = *reinterpret_cast<uint8_t* const*>(ped + gta::kPedPlayerData);
        if (!plausible(data)) {
            return false;
        }
        const uint8_t* desc = *reinterpret_cast<uint8_t* const*>(data + gta::kPlayerDataClothes);
        if (!plausible(desc)) {
            return false;
        }
        std::memcpy(out.models, desc, sizeof(out.models));
        std::memcpy(out.textures, desc + gta::kClothesTextures, sizeof(out.textures));
        std::memcpy(&out.fat, desc + gta::kClothesFat, sizeof(float));
        out.muscle = reinterpret_cast<const float*>(gta::kStatsFloat)[gta::kStatMuscle];
        return true;
    }
    void SaveOutfit(const std::string& json) override {
        if (FILE* f = std::fopen("cj_outfit.json", "wb")) {
            std::fwrite(json.data(), 1, json.size(), f);
            std::fclose(f);
            cj::Log("outfit written to cj_outfit.json");
        }
    }
    // Whether the weapon in hand is one the game fires in pairs at the ped's skill (the Colt 45, the Micro SMG, the sawn-off and the Tec-9 at the top skill):
    // the game draws a second gun in the left hand then. Asked of the game itself (its weapon info for the weapon and the skill), not guessed from the stats.
    bool DualWielding(uint8_t* ped) override {
        const uint16_t type = HeldWeapon(ped);
        if (type == 0) {
            return false;
        }
        using SkillFn = int(__attribute__((thiscall)) *)(void*);
        using InfoFn = const uint8_t*(__attribute__((cdecl)) *)(int, int);
        const int skill = reinterpret_cast<SkillFn>(gta::kGetWeaponSkill)(ped);
        const uint8_t* info = reinterpret_cast<InfoFn>(gta::kGetWeaponInfo)(type, skill);
        if (!info) {
            return false;
        }
        uint32_t flags;
        std::memcpy(&flags, info + gta::kWeaponInfoFlags, sizeof(flags));
        static uint32_t loggedFor = 0xFFFFFFFF; // one line per weapon and skill, so the layout can be checked in the log
        const uint32_t seen = static_cast<uint32_t>(type) << 8 | static_cast<uint32_t>(skill & 0xFF);
        if (seen != loggedFor) {
            loggedFor = seen;
            cj::Log("weapon %d skill %d: weapon info flags %08x (twin pistol bit: %d)", type, skill, flags, (flags & gta::kWeaponFlagTwinPistol) != 0 ? 1 : 0);
        }
        return (flags & gta::kWeaponFlagTwinPistol) != 0;
    }
    // The ped is crouched while the game's duck task (the secondary task of the duck slot) exists.
    bool IsDucking(uint8_t* ped) override {
        uint8_t* manager = gungame::TaskManager(ped);
        return manager && *reinterpret_cast<uint8_t**>(manager + gta::kTaskMgrSecondary + 4 * gta::kTaskDuckSlot) != nullptr;
    }
    // CAudioEngine::ReportFrontendAudioEvent(AE_FRONTEND_FIRE_FAIL_ROCKET): the click the game plays when a weapon cannot fire.
    void PlayEmptyClick() override {
        using ReportFn = void(__attribute__((thiscall)) *)(void*, int, float, float);
        reinterpret_cast<ReportFn>(gta::kAudioReportFrontend)(reinterpret_cast<void*>(gta::kAudioEngine), gta::kAudioFrontendFireFailRocket, 0.0f, 1.0f);
    }
    // CAudioEngine::RetuneRadio(station), the call the game itself makes when the player changes the station.
    void RetuneRadio(int station) override {
        using RetuneFn = void(__attribute__((stdcall)) *)(int8_t);
        reinterpret_cast<RetuneFn>(gta::kAudioRetuneRadio)(static_cast<int8_t>(station));
    }
    void ShiftVehicle(uint8_t* car, float dx, float dy) override { vehiclegame::Shift(car, dx, dy); }
    void CorrectVehicle(uint8_t* car, float dx, float dy) override { vehiclegame::Correct(car, dx, dy); }
    void ExitDriver(uint8_t* ped, uint8_t* car, float sideMetres) override {
        if (!vehiclegame::Exit(ped, car, sideMetres)) {
            cj::Log("vehicle: could not take CJ out of the car (the layout of his intelligence is not the verified one)");
            return;
        }
        RestorePed(ped);
    }
    void DeleteVehicle(uint8_t* car) override {
        if (!vehiclegame::Delete(car)) {
            cj::Log("vehicle: the car was not deleted because someone still sits in it");
        }
    }
    void SendVehicleState(const VehicleStatePacket& state) override { Send(&state, sizeof(state)); }

private:
    void Send(const void* data, size_t size) {
        sendto(sock_, static_cast<const char*>(data), static_cast<int>(size), 0,
               reinterpret_cast<const sockaddr*>(&sendTo_), sizeof(sendTo_));
    }

    SOCKET sock_ = INVALID_SOCKET;
    sockaddr_in sendTo_{};
    bool loggedGunLayout_ = false;
    bool foodBlockReferenced_ = false; // the FOOD anim block was asked for and referenced
    uint8_t* proofPed_ = nullptr;   // the ped whose proof bits the car gave, and the bits he had
    uint32_t savedPedProof_ = 0;
    // What the bridge gave belongs to one ped: another one (a new game) starts with a clean record.
    void ForgetOtherPed(uint8_t* ped) {
        if (ped != arsenalPed_) {
            arsenal_.Reset();
            arsenalPed_ = ped;
        }
    }

    Arsenal arsenal_;
    StrikeDetector strikeDetector_;
    uint8_t* bodyAssoc_ = nullptr;
    uint8_t* bodyPed_ = nullptr;
    bool jetOn_ = false;
    bool loggedGroupNotReady_ = false;
    uint8_t* jetPed_ = nullptr;
    int16_t bodyId_ = 0;
    int16_t bodyGroup_ = 0;
    bool loggedStaleHeld_ = false;
    uintptr_t bodyVtable_ = 0;
    uint32_t strikesTotal_ = 0;
    uint8_t* arsenalPed_ = nullptr;
    unsigned gunCalls_ = 0;
    uint16_t lastUnsupported_ = 0;
};

WinIo g_io;
BridgeCore g_core;
bool g_opened = false;
bool g_open = false;

bool EnsureOpen() {
    if (!g_opened) {
        g_opened = true;
        g_open = g_io.Open();
        g_core.SetArena(vehiclegame::kPlatformX, vehiclegame::kPlatformY, vehiclegame::kPlatformReach);
    }
    return g_open;
}

} // namespace

void Tick() {
    if (EnsureOpen()) {
        g_core.Tick(g_io);
    }
}

void PrePad() {
    if (EnsureOpen()) {
        g_core.PrePad(g_io);
    }
}

} // namespace cj::bridge
