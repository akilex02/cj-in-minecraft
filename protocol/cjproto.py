"""Reference implementation of the CJ-in-Minecraft wire protocol (see PROTOCOL.md)."""
import math
import struct
from dataclasses import dataclass

MAGIC = 0x434A4D43
VERSION = 2
TYPE_STATE = 1
TYPE_ECHO = 2
TYPE_POSE = 3
TYPE_SHOT = 4
TYPE_VEHICLE_COMMAND = 5  # client -> bridge: get into or out of the car
TYPE_VEHICLE_INPUT = 6    # client -> bridge: the controls of the car, every frame while driving
TYPE_VEHICLE_STATE = 7    # bridge -> client: where the car is, every frame while driving
POSE_MAX_BONES = 48

FLAG_ON_GROUND = 1 << 0
FLAG_SPRINT = 1 << 1
FLAG_SNEAK = 1 << 2
FLAG_SWIM = 1 << 3  # the fast swimming pose of Minecraft (the crawl)
FLAG_CLIMBING = 1 << 4
FLAG_FALLING = 1 << 5   # free fall
FLAG_KNOCKED = 1 << 6   # knocked down: lying and getting up
FLAG_DEAD = 1 << 7
HURT_SHIFT = 8          # bits 8 to 11: how many times the player was hurt (0 to 15, wrapping); a change is the event "he was hurt"
HURT_MASK = 0xF << HURT_SHIFT
FLAG_BLASTED = 1 << 12  # the knockdown was an explosion: he falls on his back
FLAG_IN_WATER = 1 << 16  # in the water with the buoyancy at work: floating, swimming or diving
FLAG_UNDERWATER = 1 << 17  # the head is under the water
FLAG_WATER_MOVING = 1 << 18  # moving horizontally in the water
FLAG_JETPACK = 1 << 14  # flying with the jetpack: wearing it, allowed, and thrusting or in the air after a thrust
FLAG_JETPACK_THRUST = 1 << 15  # thrusting now (the jump key is held)
FLAG_WALL_CLIMB = 1 << 13  # climbing a wall of 2 or 3 blocks (not a ladder: that is FLAG_CLIMBING)
FLAG_EATING = 1 << 19  # the player is eating (using a food item); bits 20 and 21 say what kind
EAT_KIND_SHIFT = 20
EAT_BURGER = 0   # the anim EAT_Burger of GTA's FOOD block: anything that is not meat or baked
EAT_CHICKEN = 1  # EAT_Chicken: meat
EAT_PIZZA = 2    # EAT_Pizza: bread, cakes and cookies


def eating_flags(kind):
    """The flags of a player eating food of this kind."""
    if kind not in (EAT_BURGER, EAT_CHICKEN, EAT_PIZZA):
        raise ValueError(f"eating kind {kind} does not exist")
    return FLAG_EATING | (kind << EAT_KIND_SHIFT)


def eat_kind(flags):
    """The kind of food in the flags of a state, or None when the player is not eating."""
    return (flags >> EAT_KIND_SHIFT) & 3 if flags & FLAG_EATING else None

VEHICLE_CMD_NONE = 0
VEHICLE_CMD_ENTER = 1
VEHICLE_CMD_EXIT = 2
VEHICLE_CMD_RADIO_NEXT = 3  # tune the radio of the car to the next station
VEHICLE_CMD_RADIO_PREVIOUS = 4
VEHICLE_CORR_PRESENT = 1 << 0  # VehicleInput.corr_flags: corr_x/corr_y hold a correction to apply once
VEHICLE_FLAG_DRIVER_IN = 1 << 0  # VehicleState.flags: CJ is inside the car
VEHICLE_STATION_SHIFT = 8  # VehicleState.flags bits 8..15: the radio station of the car as id + 1 (0: unknown)


def vehicle_station(flags):
    """The radio station id in VehicleState.flags, or None when the bridge does not say."""
    code = (flags >> VEHICLE_STATION_SHIFT) & 0xFF
    return None if code == 0 else code - 1


def with_vehicle_station(flags, station):
    """The flags with the radio station (an id from 0 to 254, or None for unknown) in their upper byte."""
    if station is not None and not 0 <= station <= 254:
        raise ValueError(f"radio station {station} does not fit")
    code = 0 if station is None else station + 1
    return (flags & ~(0xFF << VEHICLE_STATION_SHIFT)) | (code << VEHICLE_STATION_SHIFT)

ECHO_DUAL_WIELD = 1 << 0  # Echo.flags: CJ holds two guns (the game's twin pistols at the top skill)
INTENT_AIM = 1 << 0
INTENT_FIRE = 1 << 1
INTENT_MELEE = 1 << 2
INTENT_CROUCH = 1 << 5  # the player is crouched (the C key toggles it): GTA's own crouch, kept in step by the bridge with a press of the duck button
INTENT_DRY = 1 << 4    # the fire button is down but the weapon has no bullets: GTA's empty click sounds once per press
INTENT_JUMP = 1 << 3   # the jump key is down (and the player can jump): CJ starts his jump now, the Minecraft jump comes a moment later

_HEADER = struct.Struct("<IHH")
_STATE = struct.Struct("<IIffffffffIIHHfff")  # seq, ts, pos[3], vel[3], yaw, pitch, flags, intents, weapon, pad, aim[3]
_ECHO = struct.Struct("<IIfffHH")  # ack_seq, gta_time_ms, pos[3], weapon_held, flags
_SHOT = struct.Struct("<IIIHH")  # ack_seq, gta_time_ms, shot_seq, weapon, rounds
_VEHICLE_COMMAND = struct.Struct("<IIHHf")  # seq, ts, command, model, yaw (Minecraft degrees)
_VEHICLE_INPUT = struct.Struct("<IIHHhHHHff")  # seq, ts, throttle, brake, steer, handbrake, corr_flags, pad, corr_x, corr_y
_VEHICLE_STATE = struct.Struct("<IIHH3f5f3ff")  # ack_seq, gta_time_ms, flags, model, pos[3], heading, pitch, roll, speed, steer, seat[3], seat_heading
VEHICLE_COMMAND_SIZE = _HEADER.size + _VEHICLE_COMMAND.size  # 24
VEHICLE_INPUT_SIZE = _HEADER.size + _VEHICLE_INPUT.size  # 36
VEHICLE_STATE_SIZE = _HEADER.size + _VEHICLE_STATE.size  # 68
STATE_SIZE = _HEADER.size + _STATE.size  # 72
ECHO_SIZE = _HEADER.size + _ECHO.size  # 32
SHOT_SIZE = _HEADER.size + _SHOT.size  # 24
_POSE = struct.Struct("<IIIHH")  # ack_seq, gta_time_ms, flags, bone_count, heading (u16: 0..65535 = 0..2*pi)
_BONE = struct.Struct("<i3f4f")  # node_id, pos[3], quat[4] (x, y, z, w)
POSE_HEADER_SIZE = _HEADER.size + _POSE.size  # 24
BONE_SIZE = _BONE.size  # 32


@dataclass(frozen=True)
class State:
    seq: int
    ts_ms: int
    pos: tuple
    vel: tuple
    yaw: float
    pitch: float
    flags: int = 0
    intents: int = 0
    weapon: int = 0
    aim: tuple = (0.0, 0.0, 0.0)  # feet -> aim point, Minecraft axes, blocks; (0, 0, 0) = none


@dataclass(frozen=True)
class Echo:
    ack_seq: int
    gta_time_ms: int
    pos: tuple
    weapon_held: int = 0
    flags: int = 0  # ECHO_* bits


@dataclass(frozen=True)
class Shot:
    ack_seq: int
    gta_time_ms: int
    shot_seq: int  # total rounds fired since GTA started, including this event's
    weapon: int
    rounds: int    # rounds this event reports


@dataclass(frozen=True)
class Bone:
    node_id: int
    pos: tuple
    quat: tuple


@dataclass(frozen=True)
class Pose:
    ack_seq: int
    gta_time_ms: int
    bones: tuple
    flags: int = 0
    heading: float = 0.0  # radians in [0, 2*pi): where the GTA ped faces (matrix "up"), counter-clockwise from north; 16-bit precision


@dataclass(frozen=True)
class VehicleCommand:
    seq: int
    ts_ms: int
    command: int  # VEHICLE_CMD_*
    model: int    # GTA model id (492 = Greenwood)
    yaw: float    # the player's yaw in Minecraft degrees: the car faces that way


@dataclass(frozen=True)
class VehicleInput:
    seq: int
    ts_ms: int
    throttle: int   # 0..255
    brake: int      # 0..255
    steer: int      # -127..127, positive to the right
    handbrake: int  # 0 or 255
    corr_flags: int = 0
    corr_x: float = 0.0  # a displacement to add to the car's position, GTA axes, metres
    corr_y: float = 0.0


@dataclass(frozen=True)
class VehicleState:
    ack_seq: int
    gta_time_ms: int
    flags: int
    model: int
    pos: tuple
    heading: float  # radians, counter-clockwise from north
    pitch: float
    roll: float
    speed: float    # metres per second, forwards
    steer: float    # radians; negative is to the right (GTA's m_fSteerAngle)
    seat: tuple     # CJ in the axes of the car: right, forward, up
    seat_heading: float  # CJ's heading relative to the car's


def _check_header(data, expected_type, expected_size):
    if len(data) != expected_size:
        raise ValueError(f"expected {expected_size} bytes, got {len(data)}")
    magic, version, ptype = _HEADER.unpack_from(data, 0)
    if magic != MAGIC:
        raise ValueError("bad magic")
    if version != VERSION:
        raise ValueError(f"unsupported version {version}")
    if ptype != expected_type:
        raise ValueError(f"unexpected packet type {ptype}")


def body_flags(falling, knocked, dead, hurt, blasted=False):
    """The body part of State.flags: the three state bits and the hurt counter (taken modulo 16)."""
    return ((FLAG_FALLING if falling else 0) | (FLAG_KNOCKED if knocked else 0) | (FLAG_DEAD if dead else 0)
            | ((hurt & 0xF) << HURT_SHIFT) | (FLAG_BLASTED if blasted else 0))


def hurt_of(flags):
    return (flags & HURT_MASK) >> HURT_SHIFT


def pack_state(s):
    return _HEADER.pack(MAGIC, VERSION, TYPE_STATE) + _STATE.pack(
        s.seq, s.ts_ms, *s.pos, *s.vel, s.yaw, s.pitch, s.flags, s.intents, s.weapon, 0, *s.aim)


def unpack_state(data):
    _check_header(data, TYPE_STATE, STATE_SIZE)
    seq, ts, px, py, pz, vx, vy, vz, yaw, pitch, flags, intents, weapon, _pad, ax, ay, az = _STATE.unpack_from(
        data, _HEADER.size)
    return State(seq, ts, (px, py, pz), (vx, vy, vz), yaw, pitch, flags, intents, weapon, (ax, ay, az))


def pack_echo(e):
    return _HEADER.pack(MAGIC, VERSION, TYPE_ECHO) + _ECHO.pack(e.ack_seq, e.gta_time_ms, *e.pos, e.weapon_held, e.flags)


def unpack_echo(data):
    _check_header(data, TYPE_ECHO, ECHO_SIZE)
    ack, gta_time, x, y, z, weapon_held, flags = _ECHO.unpack_from(data, _HEADER.size)
    return Echo(ack, gta_time, (x, y, z), weapon_held, flags)


def pack_shot(s):
    return _HEADER.pack(MAGIC, VERSION, TYPE_SHOT) + _SHOT.pack(s.ack_seq, s.gta_time_ms, s.shot_seq, s.weapon, s.rounds)


def unpack_shot(data):
    _check_header(data, TYPE_SHOT, SHOT_SIZE)
    return Shot(*_SHOT.unpack_from(data, _HEADER.size))


def mc_delta_to_gta(d):
    """Minecraft (x east, y up, z south) displacement -> GTA (x east, y north, z up)."""
    return (d[0], -d[2], d[1])


def mc_yaw_to_gta_heading(yaw_deg):
    """Minecraft yaw in degrees -> GTA heading in radians, in [0, 2*pi)."""
    return (math.pi - math.radians(yaw_deg)) % (2 * math.pi)


def _heading_to_u16(heading):
    return int(round((heading % (2 * math.pi)) / (2 * math.pi) * 65536)) & 0xFFFF


def pack_pose(pose):
    if len(pose.bones) > POSE_MAX_BONES:
        raise ValueError(f"too many bones: {len(pose.bones)}")
    out = _HEADER.pack(MAGIC, VERSION, TYPE_POSE) + _POSE.pack(
        pose.ack_seq, pose.gta_time_ms, pose.flags, len(pose.bones), _heading_to_u16(pose.heading))
    for b in pose.bones:
        out += _BONE.pack(b.node_id, *b.pos, *b.quat)
    return out


def unpack_pose(data):
    if len(data) < POSE_HEADER_SIZE:
        raise ValueError(f"pose too short: {len(data)} bytes")
    magic, version, ptype = _HEADER.unpack_from(data, 0)
    if magic != MAGIC:
        raise ValueError("bad magic")
    if version != VERSION:
        raise ValueError(f"unsupported version {version}")
    if ptype != TYPE_POSE:
        raise ValueError(f"unexpected packet type {ptype}")
    ack, gta_time, flags, count, heading_raw = _POSE.unpack_from(data, _HEADER.size)
    if count > POSE_MAX_BONES:
        raise ValueError(f"too many bones: {count}")
    if len(data) != POSE_HEADER_SIZE + count * BONE_SIZE:
        raise ValueError(f"expected {POSE_HEADER_SIZE + count * BONE_SIZE} bytes for {count} bones, got {len(data)}")
    bones = []
    for i in range(count):
        node_id, px, py, pz, qx, qy, qz, qw = _BONE.unpack_from(data, POSE_HEADER_SIZE + i * BONE_SIZE)
        bones.append(Bone(node_id, (px, py, pz), (qx, qy, qz, qw)))
    return Pose(ack, gta_time, tuple(bones), flags, heading_raw * 2 * math.pi / 65536)


def packet_type(data):
    """The type of a datagram from its header alone, or None when the header is not ours."""
    if len(data) < _HEADER.size:
        return None
    magic, version, ptype = _HEADER.unpack_from(data, 0)
    return ptype if magic == MAGIC and version == VERSION else None


def pack_vehicle_command(c):
    return _HEADER.pack(MAGIC, VERSION, TYPE_VEHICLE_COMMAND) + _VEHICLE_COMMAND.pack(c.seq, c.ts_ms, c.command, c.model, c.yaw)


def unpack_vehicle_command(data):
    _check_header(data, TYPE_VEHICLE_COMMAND, VEHICLE_COMMAND_SIZE)
    return VehicleCommand(*_VEHICLE_COMMAND.unpack_from(data, _HEADER.size))


def pack_vehicle_input(i):
    return _HEADER.pack(MAGIC, VERSION, TYPE_VEHICLE_INPUT) + _VEHICLE_INPUT.pack(
        i.seq, i.ts_ms, i.throttle, i.brake, i.steer, i.handbrake, i.corr_flags, 0, i.corr_x, i.corr_y)


def unpack_vehicle_input(data):
    _check_header(data, TYPE_VEHICLE_INPUT, VEHICLE_INPUT_SIZE)
    seq, ts, throttle, brake, steer, handbrake, corr_flags, _pad, cx, cy = _VEHICLE_INPUT.unpack_from(data, _HEADER.size)
    return VehicleInput(seq, ts, throttle, brake, steer, handbrake, corr_flags, cx, cy)


def pack_vehicle_state(v):
    return _HEADER.pack(MAGIC, VERSION, TYPE_VEHICLE_STATE) + _VEHICLE_STATE.pack(
        v.ack_seq, v.gta_time_ms, v.flags, v.model, *v.pos, v.heading, v.pitch, v.roll, v.speed, v.steer, *v.seat, v.seat_heading)


def unpack_vehicle_state(data):
    _check_header(data, TYPE_VEHICLE_STATE, VEHICLE_STATE_SIZE)
    f = _VEHICLE_STATE.unpack_from(data, _HEADER.size)
    return VehicleState(f[0], f[1], f[2], f[3], tuple(f[4:7]), f[7], f[8], f[9], f[10], f[11], tuple(f[12:15]), f[15])
