#pragma once
#include <cstddef>
#include <cstdint>

// GTA SA 1.0 US addresses and offsets (see gta-reversed-master/source/game_sa).
// tools/verify_exe_addresses.py checks the first three against the exe.
namespace cj::gta {

constexpr uintptr_t kFindPlayerPed = 0x56E210;
constexpr uintptr_t kProcessCallSite = 0x53E981; // `call CGame::Process` inside Idle()
constexpr uintptr_t kProcessFunc = 0x53BEE0;     // CGame::Process

constexpr uintptr_t kPlayersArray = 0xB7CD98;  // CWorld::Players
constexpr uintptr_t kPlayerInFocus = 0xB7CD74; // CWorld::PlayerInFocus (uint8)
constexpr size_t kPlayerInfoSize = 0x190;      // sizeof(CPlayerInfo); m_pPed is its first member
constexpr uint8_t kMaxPlayers = 2;
constexpr uintptr_t kForegroundApp = 0x8D621C; // bool; the game sleeps in its message loop while it is 0

// CPlaceable / CEntity / CPhysical layout
constexpr size_t kPlacementPos = 0x04;  // m_placement.m_vPosn
constexpr size_t kMatrixPtr = 0x14;     // CMatrixLink*
constexpr size_t kEntityFlags = 0x1C;   // bit 0 = m_bUsesCollision
constexpr size_t kPhysicalFlags = 0x40; // bit 1 = bApplyGravity
constexpr size_t kMoveSpeed = 0x44;     // CVector m_vecMoveSpeed
constexpr size_t kMatrixPos = 0x30;     // CMatrix::m_pos

// CPed: m_nPedFlags dword; bit 8 is bInVehicle (FindPlayerVehicle does `mov edx,[ped+0x46C]; test dh,1`)
constexpr size_t kPedFlags = 0x46C;
constexpr uint32_t kPedFlagInVehicle = 1u << 8;

// CPad (gta-reversed Pad.h / ControllerState.h): Pads[0].NewState is the first member; every field is an int16.
constexpr uintptr_t kPadsArray = 0xB73458; // CPad::Pads
constexpr size_t kPadSize = 0x134;         // sizeof(CPad)
constexpr size_t kPadLeftStickX = 0x00;    // move left (-128) / right (+128)
constexpr size_t kPadLeftStickY = 0x02;    // forward (-128) / back (+128)
constexpr size_t kPadShockButtonL = 0x24;  // ShockButtonL: GTA's duck button (DuckJustDown is its rising edge)
constexpr size_t kPadButtonSquare = 0x1C;  // jump / climb
constexpr size_t kPadButtonCross = 0x20;   // sprint
constexpr size_t kPadPedWalk = 0x2A;       // m_bPedWalk: walk instead of run

// Population densities (gta-reversed Population.h / CarCtrl.h)
constexpr uintptr_t kPedDensityMultiplier = 0x8D2530; // CPopulation::PedDensityMultiplier
constexpr uintptr_t kCarDensityMultiplier = 0x8A5B20; // CCarCtrl::CarDensityMultiplier

// The calls the bridge redirects, and the real functions behind them
constexpr uintptr_t kUpdatePadsCallSite = 0x53BEE6; // `call CPad::UpdatePads` at the top of CGame::Process
constexpr uintptr_t kUpdatePadsFunc = 0x541DD0;     // CPad::UpdatePads

// Skeleton access
constexpr uintptr_t kGetAnimHierarchyFromSkinClump = 0x734A40; // RpHAnimHierarchy* (cdecl)(RpClump*)
constexpr uintptr_t kShoulderBoneRotation = 0x5DF560;           // CPed::ShoulderBoneRotation(RpClump*) cdecl: the game's PreRender sets the breast bones from the upper arms and the clavicles (every frame, for CJ)
constexpr size_t kEntityClump = 0x18;                          // CEntity::m_pRwObject (the RpClump)
constexpr size_t kHierNumNodes = 0x04;                         // RpHAnimHierarchy::numNodes
constexpr size_t kHierMatrixArray = 0x08;                      // RpHAnimHierarchy::pMatrixArray (RwMatrix[], world space)
constexpr size_t kHierNodeInfo = 0x10;                         // RpHAnimHierarchy::pNodeInfo
constexpr size_t kNodeInfoStride = 16;                         // sizeof(RpHAnimNodeInfo); nodeID is the first field

// CPad: aim ("target") and fire. The player's own on-foot task reads CPad::GetTarget (RightShoulder1) and GetWeapon (Circle).
constexpr size_t kPadRightShoulder1 = 0x0C;
constexpr size_t kPadButtonCircle = 0x22;

// Guns (gta-reversed Ped.h, Weapon.h, PedIntelligence.h, TaskManager.h, TaskSimpleUseGun.h)
constexpr size_t kPedIntelligence = 0x47C;       // CPed::m_pIntelligence (found by scanning a live ped: the member whose target starts with the ped)
constexpr size_t kIntelligenceTaskMgr = 0x04;    // CPedIntelligence::m_TaskMgr (right after m_pPed)
constexpr size_t kTaskMgrSecondary = 0x14;       // CTaskManager::m_aSecondaryTasks (after the 5 primary task pointers)
constexpr size_t kTaskAttackSlot = 0;            // TASK_SECONDARY_ATTACK
constexpr size_t kTaskDuckSlot = 1;              // TASK_SECONDARY_DUCK: present while the ped is crouched
constexpr size_t kPedWeapons = 0x5A0;            // CPed::m_aWeapons[13]
constexpr size_t kWeaponSize = 0x1C;
constexpr size_t kPedPlayerData = 0x480;         // CPed::m_pPlayerData, the member after m_pIntelligence (gta-reversed Ped.h)
constexpr size_t kPlayerDataClothes = 0x04;      // CPlayerPedData::m_pPedClothesDesc (after m_pWanted)
constexpr size_t kClothesTextures = 0x28;        // CPedClothesDesc::m_anTextureKeys, after the ten model keys
constexpr size_t kClothesFat = 0x70;             // CPedClothesDesc::m_fFatStat (after the eighteen texture keys); the muscle one is at 0x74
constexpr uintptr_t kStatsFloat = 0xB79380;      // CStats::StatTypesFloat
constexpr int kStatFat = 21;                     // eStats::STAT_FAT
constexpr int kStatMuscle = 23;                  // eStats::STAT_MUSCLE: the game takes the share of the ripped body from this stat
constexpr uintptr_t kGetWeaponSkill = 0x5E6580;  // CPed::GetWeaponSkill() thiscall: the skill (poor, standard, pro...) for the weapon in hand, from the stats
constexpr uintptr_t kGetWeaponInfo = 0x743C60;   // CWeaponInfo::GetWeaponInfo(type, skill) cdecl
constexpr size_t kWeaponInfoFlags = 0x18;        // CWeaponInfo::m_nFlags: after m_nWeaponFire, the two ranges, the two model ids and the slot
constexpr uint32_t kWeaponFlagTwinPistol = 1u << 11; // bTwinPistol: fired in pairs, one gun in each hand
constexpr size_t kPedActiveSlot = 0x718;         // CPed::m_nActiveWeaponSlot (uint8)
constexpr size_t kWeaponType = 0x00;             // CWeapon::m_Type
constexpr size_t kWeaponAmmoInClip = 0x08;       // CWeapon::m_AmmoInClip
constexpr size_t kWeaponTotalAmmo = 0x0C;        // CWeapon::m_TotalAmmo
constexpr uint8_t kNumWeaponSlots = 13;
constexpr size_t kUseGunTargetPos = 0x20;        // CTaskSimpleUseGun::m_TargetPos (CVector)
constexpr size_t kUseGunSize = 0x3C;             // sizeof(CTaskSimpleUseGun)

constexpr size_t kUseGunArmIkInUse = 0x39;     // CTaskSimpleUseGun::m_IsArmIKInUse
constexpr size_t kUseGunLookIkInUse = 0x3A;    // CTaskSimpleUseGun::m_IsLookIKInUse
constexpr size_t kTaskGetTypeSlot = 4;         // vtable slot of CTask::GetTaskType (~CTask, Clone, GetSubTask, IsSimple, GetTaskType)
constexpr int kTaskTypeUseGun = 1017;          // TASK_SIMPLE_USE_GUN
constexpr uintptr_t kRequestModel = 0x4087E0;       // CStreaming::RequestModel(modelId, flags)       cdecl
constexpr uintptr_t kLoadAllRequestedModels = 0x40EA10; // CStreaming::LoadAllRequestedModels(onlyPriority) cdecl
constexpr int kStreamingGameRequired = 0x2;         // STREAMING_GAME_REQUIRED
constexpr size_t kPedCurrentRotation = 0x558;   // CPed::m_fCurrentRotation: the heading the game turns the ped to (same angle as the matrix heading)
constexpr size_t kPedAimingRotation = 0x55C;    // CPed::m_fAimingRotation
constexpr uintptr_t kGiveWeapon = 0x5E6080;      // CPed::GiveWeapon(type, ammo, bool)            thiscall
constexpr uintptr_t kSetCurrentWeapon = 0x5E6280;// CPed::SetCurrentWeapon(eWeaponType)           thiscall
constexpr uintptr_t kRemoveAllProjectiles = 0x7399B0; // CProjectileInfo::RemoveAllProjectiles()          cdecl, no arguments
// CTaskSimpleFight (gta-reversed TaskSimpleFight.h, verified live in docs/melee-spike.md): type 1016, in the attack slot
constexpr int kTaskFightType = 1016;
constexpr size_t kFightAnim = 0x1C;      // m_pAnim (CAnimBlendAssociation*)
constexpr size_t kFightComboSet = 0x24;  // m_nComboSet (int8)
constexpr size_t kFightMove = 0x25;      // m_nCurrentMove (int8): 0 to 2 are blows, 3 block, 4 idle
constexpr size_t kAnimCurrentTime = 0x20; // CAnimBlendAssociation: current time in seconds (measured live)
constexpr uintptr_t kMeleeComboData = 0xC170D0; // CTaskSimpleFight::m_aComboData: 12 x CMeleeInfo (0x88 bytes)
constexpr size_t kMeleeInfoSize = 0x88;
constexpr size_t kMeleeInfoHit = 0x08;   // CMeleeInfo::m_fHit[5]: the time of the impact of each move
// Animations and speech of the body reactions (gta-reversed, verified live in docs/body-spike.md)
constexpr uintptr_t kBlendAnimation = 0x4D4610;   // CAnimManager::BlendAnimation(clump, group, id, blendDelta)   cdecl, returns CAnimBlendAssociation*
constexpr uintptr_t kPedSay = 0x5EFFE0;           // CPed::Say(context, startDelay, probability, overrideSilence, forceAudible, frontEnd)   thiscall
constexpr size_t kAnimBlendDelta = 0x1C;          // CAnimBlendAssociation: blend rate (float)
constexpr size_t kAnimGroupId = 0x0E;             // CAnimBlendAssociation: animation group (int16)
constexpr size_t kAnimId = 0x2C;                  // CAnimBlendAssociation: animation id (int16)
constexpr size_t kAnimFlags = 0x2E;               // CAnimBlendAssociation: flags (uint16)
constexpr uint16_t kAnimBlendAutoRemove = 0x4;    // ANIMATION_IS_BLEND_AUTO_REMOVE: deletes itself once faded out
constexpr int kAnimGroupPed = 0;                  // ANIM_GROUP_DEFAULT
constexpr int kAnimFallFall = 120;                // FALL_FALL: looping free fall
constexpr int kAnimLieFront = 26;                 // KO_SKID_BACK: falls flat on the ground face down and holds the last frame (lying, dead)
constexpr int kAnimLieBack = 24;                  // KO_SKID_FRONT: thrown on his back and holds the last frame (an explosion)
constexpr int kAnimGetUpFront = 115;              // GETUP_FRONT: gets up from face down (1.37 s, removes itself)
constexpr uintptr_t kClumpFirstAssoc = 0x4D15E0;  // RpAnimBlendClumpGetFirstAssociation(clump)  cdecl; the next one is *(assoc + 4) - 4 (the link), null at the end
constexpr int kAnimJumpFirst = 116;               // JUMP_LAUNCH .. FALL_COLLAPSE (123): the jump, glide, fall and landing animations of GTA's own jump task
constexpr int kAnimJumpLast = 123;
constexpr uintptr_t kAddAnimation = 0x4D3AA0;     // CAnimManager::AddAnimation(clump, group, id)  cdecl: adds the association without touching the others (BlendAnimation fades and deletes them)
constexpr uint16_t kAnimPlaying = 0x1;            // ANIMATION_IS_PLAYING
constexpr uint16_t kAnimPartial = 0x10;           // ANIMATION_IS_PARTIAL: lies over the base animations instead of replacing them
constexpr int kAnimJetpackGroup = 70;             // ANIM_GROUP_PLAYERJETPACK
constexpr int kAnimJetpackIdle = 3;               // JETPACK_IDLE: the pose of CJ with the jetpack (a looped, almost static pose)
constexpr size_t kPedAudio = 0x138;               // CPed::m_pedAudio (CAEPedAudioEntity), checked live through the speech entity at +0x294
constexpr uintptr_t kJetpackOn = 0x4E28A0;        // CAEPedAudioEntity::TurnOnJetPack()               thiscall on the audio entity
constexpr uintptr_t kJetpackUpdate = 0x4E0EE0;    // CAEPedAudioEntity::UpdateJetPack(thrustFwd, thrustAngle)   thiscall, once per frame
constexpr uintptr_t kJetpackOff = 0x4E2A70;       // CAEPedAudioEntity::TurnOffJetPack()
constexpr uintptr_t kJetpackStop = 0x4E1120;      // CAEPedAudioEntity::StopJetPackSound()
constexpr float kJetpackHoverParam = 0.0f;        // UpdateJetPack's thrustFwd while hovering and while thrusting (tuned by ear)
constexpr float kJetpackThrustParam = 1.0f;
constexpr int kAnimSwimGroup = 71;                // ANIM_GROUP_SWIM: in its own anim block ("swim"), streamed in on demand (docs/swim-spike.md)
constexpr int kAnimTread = 14;                    // SWIM_TREAD: floating (group 0)
constexpr int kAnimSwimBreast = 311;
constexpr int kAnimSwimCrawl = 312;
constexpr int kAnimSwimDive = 313;                // one pass of 1.07 s, removes itself
constexpr int kAnimSwimUnder = 314;               // one stroke that keeps its last frame: looped by hand
constexpr uint16_t kAnimSwimClear = 0x64;         // blend-auto-remove, synchronised and velocity extraction: the game deletes movement anims it does not own
constexpr uint16_t kAnimLooped = 0x2;             // ANIMATION_IS_LOOPED
constexpr uintptr_t kAnimBlockIndexByName = 0x4D3990; // CAnimManager::GetAnimationBlockIndex(const char*)  cdecl
constexpr uintptr_t kAnimBlockRef = 0x4D3FB0;     // CAnimManager::AddAnimBlockRef(index)
constexpr uintptr_t kAnimGroupsArray = 0xB4EA34;  // CAnimManager::ms_aAnimAssocGroups (a pointer to groups of 0x14 bytes), and
constexpr uintptr_t kAnimGroupsCount = 0xB4EA28;  // ms_numAnimAssocDefinitions
constexpr size_t kAnimGroupSize = 0x14;
constexpr int kAnimBlockModelBase = 25575;        // RESOURCE_ID_IFP: the streaming id of an anim block is this plus its index
constexpr int kAnimClimbJump = 128;               // CLIMB_JUMP: jumps and hangs from the ledge (0.57 s, holds the last frame)
constexpr int kAnimClimbPull = 130;               // CLIMB_PULL: pulls himself up the wall (0.87 s, holds the last frame)
constexpr int kAnimClimbStand = 131;              // CLIMB_STAND: stands up on the ledge (0.80 s, holds the last frame)
constexpr int kAnimGetUpBack = 112;               // GETUP_0: gets up from his back (1.37 s, removes itself)
constexpr uintptr_t kClearWeapon = 0x5E62B0;     // CPed::ClearWeapon(eWeaponType)                thiscall (removes one weapon)
// The car (docs/vehicles-spike.md, docs/vehicle-measurements.md)
// Eating: CTaskSimpleRunNamedAnim plays a named animation of an anim block (the FOOD block of anim.img has EAT_Burger, EAT_Chicken, EAT_Pizza); verified in the
// disassembly (the constructor ends with `ret 0x24`, nine arguments; its class has the task type 0x191) and live.
constexpr uintptr_t kNamedAnimCtor = 0x61A990;     // CTaskSimpleRunNamedAnim(name, ifp, flags, blendDelta, endTime, dontInterrupt, runInSequence, offsetPed, holdLastFrame) thiscall
constexpr size_t kNamedAnimSize = 0x64;            // the size the game allocates for it (the constructor writes up to +0x5C)
constexpr uintptr_t kNamedAnimVtable = 0x86D54C;
constexpr size_t kTaskPartialSlot = 4;             // TASK_SECONDARY_PARTIAL_ANIM
constexpr uint32_t kEatAnimFlags = 0x18;           // ANIMATION_IS_PARTIAL (it lies over the walking) and 0x8, which the game gives its own partial named animations: it removes itself when it ends, or the last pose stays
constexpr uintptr_t kRadioTrackManager = 0x8CB6F8;   // AERadioTrackManager
constexpr size_t kRadioStationOffset = 0xAD;          // its byte with the station that is tuned (1..11, 13 the radio off)
constexpr uintptr_t kAudioEngine = 0xB6BC90;          // CAudioEngine AudioEngine
constexpr uintptr_t kAudioReportFrontend = 0x506EA0;   // CAudioEngine::ReportFrontendAudioEvent(eventId, volumeChange, speed)   thiscall
constexpr int kAudioFrontendFireFailRocket = 42;       // AE_FRONTEND_FIRE_FAIL_ROCKET: the click of a weapon that cannot fire
constexpr uintptr_t kAudioRetuneRadio = 0x507E10;     // CAudioEngine::RetuneRadio(int8 station) stdcall
constexpr uintptr_t kProcessVerticalLine = 0x5674E0;      // CWorld::ProcessVerticalLine(CVector* origin, float distance, CColPoint*, CEntity**, buildings, vehicles, peds, objects, dummies, seeThrough, ignoreSomeObjects) cdecl
constexpr uintptr_t kCreateCarForScript = 0x431F80;       // CCarCtrl::CreateCarForScript(model, x, y, z, false) cdecl: a car on the ground at (x, y) when z is very low
constexpr uintptr_t kCarSetPedInAsDriverCtor = 0x6470E0;  // CTaskSimpleCarSetPedInAsDriver::ctor(vehicle, utility = nullptr) thiscall; the task is 0x1C bytes
constexpr size_t kCarSetPedInAsDriverSize = 0x1C;
constexpr size_t kCarSetPedInAsDriverWarp = 0x18;         // byte: seat him at once, with no door animation
constexpr uintptr_t kCarSetPedInAsDriverProcess = 0x64B950; // ProcessPed(ped) thiscall
constexpr uintptr_t kFlushImmediately = 0x601640;         // CPedIntelligence::FlushImmediately(bool) thiscall
constexpr uintptr_t kPedTeleport = 0x5E4110;              // CPed::Teleport(x, y, z, bool) thiscall
constexpr uintptr_t kWorldRemove = 0x563280;              // CWorld::Remove(entity) cdecl
constexpr size_t kPedVehicle = 0x58C;                     // CPed::m_pVehicle (a registered reference: the game clears it when the car is deleted)
constexpr size_t kEntityModelIndex = 0x22;                // CEntity::m_nModelIndex (u16)
constexpr size_t kVehicleDriver = 0x460;                  // CVehicle::m_pDriver
constexpr size_t kVehicleSteerAngle = 0x494;              // CVehicle::m_fSteerAngle: radians, negative to the right (measured)
constexpr uint32_t kPhysicalProofBits = 0x3Fu << 18;      // bullet, fire, collision, melee proof, invulnerable, explosion proof
constexpr uintptr_t kTaskNew = 0x61A5A0;         // CTask::operator new(size_t)                   cdecl
constexpr uintptr_t kUseGunCtor = 0x61DE60;      // CTaskSimpleUseGun(target, CVector, cmd, burst, immediate)  thiscall
constexpr uintptr_t kUseGunControl = 0x61E040;   // CTaskSimpleUseGun::ControlGun(ped, target, cmd) thiscall
constexpr uintptr_t kSetTaskSecondary = 0x681B60;// CTaskManager::SetTaskSecondary(task, slot)    thiscall

// Camera: while the player aims, the game aims at the active camera's front vector (CCamera::Find3rdPersonCamTargetVector).
constexpr uintptr_t kTheCamera = 0xB6F028;     // CCamera TheCamera
constexpr size_t kCameraActiveCam = 0x59;      // CCamera::m_nActiveCam (uint8)
constexpr size_t kCameraCams = 0x1B4;          // CCamera::m_aCams[3]
constexpr size_t kCamSize = 0x238;             // sizeof(CCam)
constexpr size_t kCamHorizontalAngle = 0x7C;   // CCam::m_fHorizontalAngle: the heading of the camera minus pi/2 (found by scanning a live camera)
constexpr size_t kCamFront = 0x150;            // CCam::m_vecFront (found by scanning a live camera: a unit vector with the heading of CJ at TheCamera+0x304)

} // namespace cj::gta
