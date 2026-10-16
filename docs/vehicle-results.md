# Vehicles (the Greenwood): results

Press `G` and CJ gets into a Greenwood (model 492); press `G` again and he gets out. GTA moves the car with its own physics; Minecraft draws it and carries
the player along. Verified live with the user on 2026-10-09 and 2026-10-10.

## What was built

- **Protocol v2**: `VEHICLE_COMMAND` (enter/exit), `VEHICLE_INPUT` (throttle, brake, steer, handbrake, a correction), `VEHICLE_STATE` (place, heading, speed, steer, CJ's seat). Golden files shared by Python, C++ and Java.
- **Bridge (`gta-bridge`)**: `VehicleDriver` (modes Free/Driving, watchdog of 1 s, deduplicated commands), the game calls in `vehicle_game.h`, the arithmetic in `vehicle_math.h`. The car is made proof against everything, and so is CJ in it; his own bits come back when he gets out.
- **Minecraft (`dev.cjmc.vehicle`, pure and tested)**: `VehicleFrame`, `VehicleCollision` (the box of the car against blocks), `VehicleTerrain` (gravity, tilt from the four wheels), `VehicleRules`, `VehicleSession` (the state machine), `RunOver`, `CarCamera` (outside the package). Adapters: `VehicleClient`, `VehicleWorld`, `RunOverClient`, `VehicleRig` and `CjRenderer.renderInCar` for drawing.
- **Converter**: the Greenwood from the user's own files into `vehicles/greenwood.glb` (named frames, wheels, paint baked into textures).

## Final constants

| What | Value |
|---|---|
| Car box | 4.9 x 1.9 x 1.5 blocks; wheel radius 0.35; wheelbase 3.41; track 1.85 |
| Step the car climbs | 1.0 (a full block, like a horse); was 0.6 |
| Speed in the packet | m/s (GTA keeps it in metres per frame of 1/50 s: x50) |
| Paint | (118,145,150) body, (230,226,205) roof: light grey-blue with a cream roof |
| Camera at the wheel | 7.5 blocks behind, eased in 0.5 s, the FOV untouched |
| Run over | from 4 m/s; 1.2 health per m/s above it, cap 40; knockback up to 1.6; 10 ticks between hits on the same entity |
| Floor | four flat seabed tiles at z 1000, x 148..596, y -2508..-2111 (at least); centre (384.45, -2316) |
| Floor reach | the car goes back to the centre when it is 120 m from it in x or y |

## Radio, smoke, inventory (2026-10-10)

- **Radio**: the left and right arrows tune the car's radio (commands 3 and 4 of `VEHICLE_COMMAND`; the station comes back in bits 8..15 of `VEHICLE_STATE.flags` as id + 1). GTA's track manager (`0x8CB6F8`) holds the tuned station in the byte at +0xAD: 1..11 are the eleven stations, 13 is the radio off. The call is `CAudioEngine::RetuneRadio(int8)` at 0x507E10 (stdcall), the one the game itself makes. The dial is 1..11 then off, then round again; presses in one frame add up and the station asked for is reported until the game says it. The name shows for 5 s (`RadioDisplay`, drawn by `VehicleClient`). The names in `RadioStations` are in the order of the game and were confirmed by ear.
- **Exhaust smoke** (`ExhaustSmoke`): a light puff at the back right of the car, more often when moving.
- **Inventory preview**: CJ replaces Steve in the survival and creative screens, turned by the body yaw the screen sets (`InventoryScreenMixin`, `CjRenderer.renderPreview`).
- **Weapons**: no jumping with the rocket launcher (as the minigun); the fists are an item (`cj_fabric:fists`, the Fist icon) and the only way CJ punches; the empty hand is vanilla again.

## Headlights, the thin CJ, eating (2026-10-10, second round)

- **Headlights** (`L`): the lenses of the model are told apart by position (front: y above 1.5, rear: below -1.5; the indicators and the mirrors are left alone). Each has an off texture and a lit twin (`lightSwaps` in the extras of the glb); with the lights on they are drawn as the twin at full brightness, in the glass pass too (the lenses are glass). The light on the world is a few `minecraft:light` blocks (levels 14, 13 and 12 at 3.5, 7.5 and 11.5 blocks ahead) put in the client's own copy of the world (`HeadlightsClient`): nothing is sent to the server or saved.
- **Thin CJ**: the body files of `player.img` hold three clumps, one per build (the first is the muscular one, the second the fat one, the third the thin one); the converter reads the third. The clothes are the vest (its texture painted over the skin one by alpha), `jeans.dff` with `jeansdenim.txd` and `chucks.dff` with `chucksblack.txd`.
- **Eating**: the FOOD block of `anim.img` (`EAT_Burger`, `EAT_Chicken`, `EAT_Pizza`) played by a `CTaskSimpleRunNamedAnim` (constructor 0x61A990, nine arguments, flags 0x18 = partial + the 0x8 the game gives its own) in the partial-animation slot (4) of the ped. Without the 0x8 the last pose stayed after the animation ended.
- **Effects**: Speed I and Jump Boost I (it was II); a Jump Boost II of the old shape found on a player is replaced.
- **Screen**: the radio name sits just above the status bars; F7 (bindable) hides or shows the CJ bridge line.

## The floor in the sky

GTA's own map stopped the car (walls) or dropped it (buildings without a floor), and the ground is not flat. A text IPL through modloader crashed the game while
loading (a null model info), so the floor is made by a CLEO script (`tools/make_platform_script.py` writes
`modloader/CjPlatform/cleo/cj_platform.cs`): it waits 8 s, loads four flat tiles of the sea floor (`sbsbed3/4/5/6law2`, collision of zero thickness) and makes them
objects, not dynamic, at z 1000 over the sea, where nothing else is. At the first `G` CJ is taken to the middle of the floor and the car is made there.
Near the edge the car is set down in the middle with the game's own `CAutomobile::Teleport` (slot 14 of its table, checked at 0x6A9CA0: it resets the suspension;
moving the matrix by hand left stale wheel contacts and the car flipped), and its speeds are put back. Minecraft sees a jump of more than 30 blocks and anchors
its frame again, so the drive looks continuous.

## Limits

- One model (Greenwood), no motorbikes, no cars parked in the world, no doors, lights, damage or engine sound of its own.
- The car is seen only in third person (the local player is not drawn in first person).
- The drive is on the floor in the sky: the GTA map under it is not used, and the street is Minecraft's.
- Running things over is for the living entities of Minecraft (single player: it goes through the integrated server, like the shots). GTA's own peds and cars are not around.
- After the first `G` CJ stays on the floor in the sky, also on foot.
- Needs the CLEO script and the updated `cj_bridge.asi`. Before the script has made the floor (about 8 s after the game starts) `G` does nothing: a line probe (`CWorld::ProcessVerticalLine`, buildings and objects) must hit at z 1000 first.

## Dead ends

- Dropping the contacts of the car with the map (walls) while keeping the floor: buildings have no floor of their own, and slopes still tipped the car.
- A text IPL for the floor: the game crashes while loading the map.
