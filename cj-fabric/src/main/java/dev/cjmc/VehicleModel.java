package dev.cjmc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A vehicle read from a file of tools/convert_assets.py (vehicles/<name>.glb) and turned for drawing: its meshes in the model space of Minecraft
 * (x left, y up, z forward, as the entity models face south), split into the body and the wheel, with the places of the four wheels, the
 * driver's seat and the radius of a wheel. GTA's vehicle space is x right, y forward, z up.
 */
public final class VehicleModel {
    private static final String[] CORNERS = {"lf", "rf", "lb", "rb"};

    public final GlbModel source;
    public final List<GlbModel.Part> body;
    public final List<GlbModel.Part> wheels;
    /** The centre of each wheel, in the order front left, front right, back left, back right. */
    public final float[][] wheelPositions;
    public final float[] driverSeat;
    public final float wheelRadius;
    /** [dim image, lit image] pairs: with the lights on the first is drawn as the second (headlights and tail lights). */
    public final int[][] lightSwaps;

    private VehicleModel(GlbModel source, List<GlbModel.Part> body, List<GlbModel.Part> wheels, float[][] wheelPositions, float[] driverSeat, float wheelRadius,
                         int[][] lightSwaps) {
        this.source = source;
        this.body = body;
        this.wheels = wheels;
        this.wheelPositions = wheelPositions;
        this.driverSeat = driverSeat;
        this.wheelRadius = wheelRadius;
        this.lightSwaps = lightSwaps;
    }

    /** The image to draw instead of `image` when the lights are on: its lit twin, or itself when it has none. */
    public int litImage(int image) {
        for (int[] swap : lightSwaps) {
            if (swap[0] == image) {
                return swap[1];
            }
        }
        return image;
    }

    public static boolean isFront(int corner) {
        return corner < 2;
    }

    public static boolean isLeft(int corner) {
        return corner % 2 == 0;
    }

    public static VehicleModel from(GlbModel model) {
        return from(model, model.assetExtras);
    }

    static VehicleModel from(GlbModel model, JsonObject extras) {
        JsonObject wheelPlaces = member(extras, "wheels").getAsJsonObject();
        float[][] wheelPositions = new float[4][];
        for (int i = 0; i < 4; i++) {
            wheelPositions[i] = toMinecraft(vector(member(wheelPlaces, CORNERS[i])));
        }
        float[] seat = toMinecraft(vector(member(extras, "driverSeat")));
        float radius = member(extras, "wheelRadius").getAsFloat();
        if (!(radius > 0f)) {
            throw new IllegalArgumentException("the wheel radius is " + radius);
        }
        List<GlbModel.Part> body = new ArrayList<>();
        List<GlbModel.Part> wheels = new ArrayList<>();
        for (GlbModel.Part part : model.parts) {
            GlbModel.Part converted = new GlbModel.Part(part.name(), convert(part.positions()), convert(part.normals()), part.uvs(), part.indices(),
                    part.joints(), part.weights(), part.image());
            (part.name().startsWith("wheel") ? wheels : body).add(converted);
        }
        if (body.isEmpty() || wheels.isEmpty()) {
            throw new IllegalArgumentException("a vehicle needs a body and a wheel, this model has " + body.size() + " body parts and " + wheels.size() + " wheel parts");
        }
        return new VehicleModel(model, body, wheels, wheelPositions, seat, radius, lightSwaps(extras, model.images.size()));
    }

    private static int[][] lightSwaps(JsonObject extras, int images) {
        if (!extras.has("lightSwaps")) {
            return new int[0][];
        }
        JsonElement element = extras.get("lightSwaps");
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException("lightSwaps is not a list");
        }
        JsonArray list = element.getAsJsonArray();
        int[][] swaps = new int[list.size()][];
        for (int i = 0; i < swaps.length; i++) {
            if (!list.get(i).isJsonArray() || list.get(i).getAsJsonArray().size() != 2) {
                throw new IllegalArgumentException("a light swap is two image numbers");
            }
            swaps[i] = new int[2];
            for (int k = 0; k < 2; k++) {
                int n = list.get(i).getAsJsonArray().get(k).getAsInt();
                if (n < 0 || n >= images) {
                    throw new IllegalArgumentException("a light swap names the image " + n + " of " + images);
                }
                swaps[i][k] = n;
            }
        }
        return swaps;
    }

    private static JsonElement member(JsonObject object, String name) {
        if (!object.has(name)) {
            throw new IllegalArgumentException("the model has no \"" + name + "\" in its extras: it is not a vehicle; run tools/convert_assets.py --vehicles");
        }
        return object.get(name);
    }

    private static float[] vector(JsonElement element) {
        JsonArray array = element.getAsJsonArray();
        if (array.size() != 3) {
            throw new IllegalArgumentException("a place in the model has " + array.size() + " numbers, not 3");
        }
        return new float[] {array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
    }

    private static float[] toMinecraft(float[] gta) {
        return new float[] {-gta[0], gta[2], gta[1]};
    }

    private static float[] convert(float[] gta) {
        float[] out = new float[gta.length];
        for (int i = 0; i < gta.length; i += 3) {
            out[i] = -gta[i];
            out[i + 1] = gta[i + 2];
            out[i + 2] = gta[i + 1];
        }
        return out;
    }
}
