package dev.cjmc.skate;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.cjmc.GlbModel;
import dev.cjmc.JointFrame;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** What the renderer needs to show the skater: CJ posed with the bones of the engine, and the board. Render thread only. */
public final class SkateView {
    private static final Logger LOG = LoggerFactory.getLogger("cj_fabric");

    private final SkateRig rig;
    private final JointFrame frame;
    private final SkateBoardModel board; // null: the board of blocks

    private SkateView(SkateRig rig, int joints, SkateBoardModel board) {
        this.rig = rig;
        this.frame = new JointFrame(joints);
        this.board = board;
    }

    /** Reads `skate_rig.json` (made by tools/make_skate_rig.py from the user's own Skate 3 files); null when it is not there or does not fit CJ's model. */
    public static SkateView load(Path file, GlbModel cj) {
        if (!Files.isRegularFile(file)) {
            LOG.info("no {}: CJ is not drawn on the skateboard (run tools/make_skate_rig.py)", file);
            return null;
        }
        try {
            JsonArray joints = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("joints");
            float[] rest = new float[9 * joints.size()];
            float[] positions = new float[3 * joints.size()];
            for (int j = 0; j < joints.size(); j++) {
                JsonArray rotation = joints.get(j).getAsJsonObject().getAsJsonArray("rotation");
                for (int i = 0; i < 9; i++) {
                    rest[9 * j + i] = rotation.get(i).getAsFloat();
                }
                JsonArray position = joints.get(j).getAsJsonObject().getAsJsonArray("position");
                for (int i = 0; i < 3; i++) {
                    positions[3 * j + i] = position.get(i).getAsFloat();
                }
            }
            SkateRig rig = new SkateRig(rest, positions, cj.jointNodeIds, cj.inverseBind);
            if (!rig.usable()) {
                LOG.warn("{} does not fit CJ's skeleton: CJ is not drawn on the skateboard", file);
                return null;
            }
            return new SkateView(rig, cj.jointNodeIds.length, SkateBoardModel.load(file.resolveSibling("skate_board.json")));
        } catch (IOException | RuntimeException e) {
            LOG.warn("could not read {}", file, e);
            return null;
        }
    }

    /** CJ's joints for the pose: his model space with the origin at the centre of the board. */
    public JointFrame frameFor(SkateProtocol.Pose pose) {
        rig.pose(pose.bones(), pose.boneCount(), frame);
        return frame;
    }

    /**
     * Turns the matrix stack (at the player's feet, as the entity renderer has it) to the frame of the skater: the origin at the root of the animation (the centre of
     * the deck when he rides, his spot on the ground when he walks), the orientation of the root.
     */
    public static void toSkater(MatrixStack matrices, SkateProtocol.Pose pose, double playerX, double playerY, double playerZ) {
        float[] r = pose.root();
        matrices.translate(r[12] - playerX, r[13] - playerY, r[14] - playerZ);
        Matrix3f m = new Matrix3f(r[0], r[1], r[2], r[4], r[5], r[6], r[8], r[9], r[10]);
        matrices.multiply(new Quaternionf().setFromUnnormalized(m));
    }

    /** The board: Skate's own in the skater's frame (the stack as toSkater left it), or the one of blocks at the deck when the user has not exported it. */
    public void drawBoard(MatrixStack matrices, SkateProtocol.Pose pose, double playerX, double playerY, double playerZ, VertexConsumerProvider vcp, int light) {
        if (board != null) {
            matrices.push();
            try {
                toSkater(matrices, pose, playerX, playerY, playerZ);
                board.draw(matrices.peek(), vcp, light, pose.bones(), pose.boneCount());
            } finally {
                matrices.pop();
            }
            return;
        }
        float[] b = pose.deckBasis();
        float[] d = pose.deckPosition();
        Matrix3f m = new Matrix3f(b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8]);
        matrices.push();
        try {
            matrices.translate(d[0] - playerX, d[1] - playerY, d[2] - playerZ);
            matrices.multiply(new Quaternionf().setFromUnnormalized(m));
            SkateBoard.draw(matrices.peek(), vcp, light);
        } finally {
            matrices.pop();
        }
    }
}
