package dev.cjmc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Wire format shared with the GTA bridge. See protocol/PROTOCOL.md. */
public final class Protocol {
    public static final int MAGIC = 0x434A4D43;
    public static final short VERSION = 2;
    public static final short TYPE_STATE = 1;
    public static final short TYPE_ECHO = 2;
    public static final short TYPE_POSE = 3;
    public static final short TYPE_SHOT = 4;
    public static final short TYPE_VEHICLE_COMMAND = 5; // client -> bridge: get into or out of the car
    public static final short TYPE_VEHICLE_INPUT = 6;   // client -> bridge: the controls of the car, every frame while driving
    public static final short TYPE_VEHICLE_STATE = 7;   // bridge -> client: where the car is, every frame while driving
    public static final int VEHICLE_COMMAND_SIZE = 24;
    public static final int VEHICLE_INPUT_SIZE = 36;
    public static final int VEHICLE_STATE_SIZE = 68;
    public static final int STATE_SIZE = 72;
    public static final int ECHO_SIZE = 32;
    public static final int SHOT_SIZE = 24;
    public static final int POSE_HEADER_SIZE = 24;
    public static final int BONE_SIZE = 32;
    public static final int POSE_MAX_BONES = 48;
    /** The largest datagram the bridge sends; a receive buffer must be at least this big. */
    public static final int MAX_DATAGRAM = POSE_HEADER_SIZE + BONE_SIZE * POSE_MAX_BONES;

    public static final int FLAG_ON_GROUND = 1;
    public static final int FLAG_SPRINT = 2;
    public static final int FLAG_SNEAK = 4;
    public static final int FLAG_SWIM = 8; // the fast swimming pose of Minecraft (the crawl)
    public static final int FLAG_CLIMBING = 16;
    public static final int FLAG_FALLING = 32;  // free fall
    public static final int FLAG_KNOCKED = 64;  // knocked down: lying and getting up
    public static final int FLAG_DEAD = 128;
    public static final int HURT_SHIFT = 8;     // bits 8 to 11: how many times the player was hurt (0 to 15, wrapping); a change is the event "he was hurt"
    public static final int HURT_MASK = 0xF << HURT_SHIFT;
    public static final int FLAG_BLASTED = 1 << 12; // the knockdown was an explosion: he falls on his back
    public static final int FLAG_IN_WATER = 1 << 16; // in the water with the buoyancy at work: floating, swimming or diving
    public static final int FLAG_UNDERWATER = 1 << 17; // the head is under the water
    public static final int FLAG_WATER_MOVING = 1 << 18; // moving horizontally in the water
    public static final int FLAG_JETPACK = 1 << 14; // flying with the jetpack: wearing it, allowed, and thrusting or in the air after a thrust
    public static final int FLAG_JETPACK_THRUST = 1 << 15; // thrusting now (the jump key is held)
    public static final int FLAG_WALL_CLIMB = 1 << 13; // climbing a wall of 2 or 3 blocks (not a ladder: that is FLAG_CLIMBING)
    public static final int FLAG_EATING = 1 << 19; // the player is eating (using a food item); bits 20 and 21 say what kind
    public static final int EAT_KIND_SHIFT = 20;
    public static final int EAT_BURGER = 0;  // the anim EAT_Burger of GTA's FOOD block: anything that is not meat or baked
    public static final int EAT_CHICKEN = 1; // EAT_Chicken: meat
    public static final int EAT_PIZZA = 2;   // EAT_Pizza: bread, cakes and cookies

    public static final int VEHICLE_CMD_NONE = 0;
    public static final int VEHICLE_CMD_ENTER = 1;
    public static final int VEHICLE_CMD_EXIT = 2;
    public static final int VEHICLE_CMD_RADIO_NEXT = 3;     // tune the radio of the car to the next station
    public static final int VEHICLE_CMD_RADIO_PREVIOUS = 4;
    public static final int VEHICLE_CORR_PRESENT = 1; // VehicleInput.corrFlags: corrX/corrY hold a correction to apply once
    public static final int VEHICLE_FLAG_DRIVER_IN = 1; // VehicleState.flags: CJ is inside the car

    public static final int INTENT_AIM = 1;
    public static final int INTENT_FIRE = 2;
    public static final int INTENT_MELEE = 4;
    public static final int INTENT_CROUCH = 32; // the player is crouched (the C key toggles it): GTA's own crouch, kept in step by the bridge
    public static final int INTENT_DRY = 16;  // the fire button is down but the weapon has no bullets: GTA's empty click sounds once per press
    public static final int INTENT_JUMP = 8;  // the jump key is down (and the player can jump): CJ starts his jump now, the Minecraft jump comes a moment later

    /** aimX/Y/Z: vector from the player's feet to the aim point, Minecraft axes, blocks; (0, 0, 0) = none. */
    public record State(int seq, int tsMs, float px, float py, float pz, float vx, float vy, float vz,
                        float yaw, float pitch, int flags, int intents, short weapon,
                        float aimX, float aimY, float aimZ) {}

    public static final int ECHO_DUAL_WIELD = 1; // Echo.flags bit 0: CJ holds two guns (the game's twin pistols at the top skill)

    /** `flags`: ECHO_* bits (the two last bytes of the echo). */
    public record Echo(int ackSeq, int gtaTimeMs, float x, float y, float z, int weaponHeld, int flags) {
        /** CJ holds two guns of the same kind, one in each hand. */
        public boolean dualWield() {
            return (flags & ECHO_DUAL_WIELD) != 0;
        }
    }

    /** shotSeq is the total of rounds fired since GTA started, including this event's. */
    public record Shot(int ackSeq, int gtaTimeMs, int shotSeq, int weapon, int rounds) {}

    /** command is VEHICLE_CMD_*; model is the GTA model id (492 = Greenwood); yaw is the player's Minecraft yaw in degrees: the car faces that way. */
    public record VehicleCommand(int seq, int tsMs, int command, short model, float yaw) {}

    /** throttle and brake 0..255, steer -127..127 (positive to the right), handbrake 0 or 255; corrX/corrY (GTA axes, metres) apply once when corrFlags has VEHICLE_CORR_PRESENT. */
    public record VehicleInput(int seq, int tsMs, int throttle, int brake, short steer, int handbrake, int corrFlags, float corrX, float corrY) {}

    /** The car in the GTA world (radians, metres), and CJ in its axes: right, forward, up. steer is GTA's m_fSteerAngle: negative is to the right. */
    public record VehicleState(int ackSeq, int gtaTimeMs, int flags, int model, float px, float py, float pz, float heading, float pitch, float roll,
                               float speed, float steer, float seatRight, float seatForward, float seatUp, float seatHeading) {}

    /** A bone in CJ's model space (X forward, Y left, Z up): position and rotation (x, y, z, w). */
    public record Bone(int nodeId, float px, float py, float pz, float qx, float qy, float qz, float qw) {}

    /** heading: where the GTA ped faces, radians in [0, 2*pi), counter-clockwise from north; the bones are relative to it. */
    public record Pose(int ackSeq, int gtaTimeMs, int flags, float heading, Bone[] bones) {
        public Pose(int ackSeq, int gtaTimeMs, int flags, Bone[] bones) {
            this(ackSeq, gtaTimeMs, flags, 0f, bones);
        }
    }

    private Protocol() {}

    public static byte[] encodeState(State s) {
        ByteBuffer b = ByteBuffer.allocate(STATE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(MAGIC).putShort(VERSION).putShort(TYPE_STATE);
        b.putInt(s.seq()).putInt(s.tsMs());
        b.putFloat(s.px()).putFloat(s.py()).putFloat(s.pz());
        b.putFloat(s.vx()).putFloat(s.vy()).putFloat(s.vz());
        b.putFloat(s.yaw()).putFloat(s.pitch());
        b.putInt(s.flags()).putInt(s.intents());
        b.putShort(s.weapon()).putShort((short) 0);
        b.putFloat(s.aimX()).putFloat(s.aimY()).putFloat(s.aimZ());
        return b.array();
    }

    /** The type of a datagram from its header alone, or -1 when the header is not ours. */
    public static int typeOf(byte[] data, int len) {
        if (len < 8) {
            return -1;
        }
        ByteBuffer b = ByteBuffer.wrap(data, 0, 8).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getShort() != VERSION) {
            return -1;
        }
        return b.getShort() & 0xFFFF;
    }

    public static byte[] encodeVehicleCommand(VehicleCommand c) {
        ByteBuffer b = ByteBuffer.allocate(VEHICLE_COMMAND_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(MAGIC).putShort(VERSION).putShort(TYPE_VEHICLE_COMMAND);
        b.putInt(c.seq()).putInt(c.tsMs()).putShort((short) c.command()).putShort(c.model()).putFloat(c.yaw());
        return b.array();
    }

    public static byte[] encodeVehicleInput(VehicleInput i) {
        ByteBuffer b = ByteBuffer.allocate(VEHICLE_INPUT_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(MAGIC).putShort(VERSION).putShort(TYPE_VEHICLE_INPUT);
        b.putInt(i.seq()).putInt(i.tsMs()).putShort((short) i.throttle()).putShort((short) i.brake()).putShort(i.steer());
        b.putShort((short) i.handbrake()).putShort((short) i.corrFlags()).putShort((short) 0).putFloat(i.corrX()).putFloat(i.corrY());
        return b.array();
    }

    /** Returns null when the datagram is not a valid vehicle state. */
    public static VehicleState decodeVehicleState(byte[] data, int len) {
        if (len != VEHICLE_STATE_SIZE) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data, 0, len).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getShort() != VERSION || b.getShort() != TYPE_VEHICLE_STATE) {
            return null;
        }
        return new VehicleState(b.getInt(), b.getInt(), b.getShort() & 0xFFFF, b.getShort() & 0xFFFF, b.getFloat(), b.getFloat(), b.getFloat(),
                b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat());
    }

    /** Returns null when the datagram is not a valid echo. */
    public static Echo decodeEcho(byte[] data, int len) {
        if (len != ECHO_SIZE) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data, 0, len).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getShort() != VERSION || b.getShort() != TYPE_ECHO) {
            return null;
        }
        return new Echo(b.getInt(), b.getInt(), b.getFloat(), b.getFloat(), b.getFloat(), b.getShort() & 0xFFFF, b.getShort() & 0xFFFF);
    }

    /** Returns null when the datagram is not a valid shot event. */
    public static Shot decodeShot(byte[] data, int len) {
        if (len != SHOT_SIZE) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data, 0, len).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getShort() != VERSION || b.getShort() != TYPE_SHOT) {
            return null;
        }
        return new Shot(b.getInt(), b.getInt(), b.getInt(), b.getShort() & 0xFFFF, b.getShort() & 0xFFFF);
    }

    /** Returns null when the datagram is not a valid pose (wrong size, header, bone count or a non-finite number). */
    public static Pose decodePose(byte[] data, int len) {
        if (len < POSE_HEADER_SIZE) {
            return null;
        }
        ByteBuffer b = ByteBuffer.wrap(data, 0, len).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC || b.getShort() != VERSION || b.getShort() != TYPE_POSE) {
            return null;
        }
        int ack = b.getInt();
        int time = b.getInt();
        int flags = b.getInt();
        int count = b.getShort() & 0xFFFF;
        float heading = (float) ((b.getShort() & 0xFFFF) * (2 * Math.PI / 65536));
        if (count > POSE_MAX_BONES || len != POSE_HEADER_SIZE + count * BONE_SIZE) {
            return null;
        }
        Bone[] bones = new Bone[count];
        for (int i = 0; i < count; i++) {
            Bone bone = new Bone(b.getInt(), b.getFloat(), b.getFloat(), b.getFloat(),
                    b.getFloat(), b.getFloat(), b.getFloat(), b.getFloat());
            if (!Float.isFinite(bone.px()) || !Float.isFinite(bone.py()) || !Float.isFinite(bone.pz())
                    || !Float.isFinite(bone.qx()) || !Float.isFinite(bone.qy()) || !Float.isFinite(bone.qz())
                    || !Float.isFinite(bone.qw())) {
                return null;
            }
            bones[i] = bone;
        }
        return new Pose(ack, time, flags, heading, bones);
    }

    /** The body part of State.flags: the three state bits and the hurt counter (taken modulo 16). */
    public static int bodyFlags(boolean falling, boolean knocked, boolean dead, int hurt, boolean blasted) {
        return (falling ? FLAG_FALLING : 0) | (knocked ? FLAG_KNOCKED : 0) | (dead ? FLAG_DEAD : 0) | ((hurt & 0xF) << HURT_SHIFT)
                | (blasted ? FLAG_BLASTED : 0);
    }

    public static int hurtOf(int flags) {
        return (flags & HURT_MASK) >> HURT_SHIFT;
    }
}
