package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/** mini_vehicle.glb is written by tools/make_mini_vehicle.py: a car with four coloured and textured body parts and one wheel. */
class VehicleModelTest {
    private static GlbModel mini() throws IOException {
        try (InputStream in = VehicleModelTest.class.getResourceAsStream("/mini_vehicle.glb")) {
            return GlbModel.read(in.readAllBytes());
        }
    }

    @Test
    void aModelWithoutLightSwapsHasNone() throws IOException {
        assertEquals(0, VehicleModel.from(mini()).lightSwaps.length);
    }

    @Test
    void theLightSwapsTellWhichImageIsDrawnAsWhichWhenTheLightsAreOn() throws IOException {
        GlbModel source = mini();
        com.google.gson.JsonObject extras = source.assetExtras.deepCopy();
        extras.add("lightSwaps", com.google.gson.JsonParser.parseString("[[1, 3], [2, 4]]"));
        VehicleModel v = VehicleModel.from(source, extras);
        assertEquals(2, v.lightSwaps.length);
        assertArrayEquals(new int[] {1, 3}, v.lightSwaps[0]);
        assertEquals(3, v.litImage(1));
        assertEquals(4, v.litImage(2));
        assertEquals(0, v.litImage(0), "an image with no twin is drawn as itself");
        assertEquals(7, v.litImage(7));
    }

    @Test
    void brokenLightSwapsAreRefused() throws IOException {
        GlbModel source = mini();
        for (String bad : new String[] {"[[1]]", "[[1, 2, 3]]", "[1, 2]", "\"x\"", "[[-1, 2]]", "[[1, 99]]"}) {
            com.google.gson.JsonObject extras = source.assetExtras.deepCopy();
            extras.add("lightSwaps", com.google.gson.JsonParser.parseString(bad));
            assertThrows(IllegalArgumentException.class, () -> VehicleModel.from(source, extras), bad);
        }
    }

    @Test
    void thePartsAreSplitIntoTheBodyAndTheWheel() throws IOException {
        VehicleModel v = VehicleModel.from(mini());
        assertEquals(4, v.body.size());
        assertEquals(1, v.wheels.size());
        assertEquals("wheel", v.wheels.get(0).name());
        assertTrue(v.body.stream().noneMatch(p -> p.name().startsWith("wheel")));
    }

    @Test
    void vertexesGoFromGtaAxesToMinecraftAxes() throws IOException {
        GlbModel source = mini();
        VehicleModel v = VehicleModel.from(source);
        // GTA: x right, y forward, z up. Minecraft model space facing south: x left, y up, z forward.
        float[] wheel = source.parts.get(4).positions();
        float[] mc = v.wheels.get(0).positions();
        assertEquals(wheel.length, mc.length);
        for (int i = 0; i < wheel.length; i += 3) {
            assertEquals(-wheel[i], mc[i], 1e-6f);
            assertEquals(wheel[i + 2], mc[i + 1], 1e-6f);
            assertEquals(wheel[i + 1], mc[i + 2], 1e-6f);
        }
        float[] normals = source.parts.get(4).normals();
        float[] mcNormals = v.wheels.get(0).normals();
        for (int i = 0; i < normals.length; i += 3) {
            assertEquals(-normals[i], mcNormals[i], 1e-6f);
            assertEquals(normals[i + 2], mcNormals[i + 1], 1e-6f);
            assertEquals(normals[i + 1], mcNormals[i + 2], 1e-6f);
        }
        assertArrayEquals(source.parts.get(0).uvs(), v.body.get(0).uvs());
        assertArrayEquals(source.parts.get(0).indices(), v.body.get(0).indices());
        assertEquals(source.parts.get(0).image(), v.body.get(0).image());
    }

    @Test
    void theOriginalModelIsNotChanged() throws IOException {
        GlbModel source = mini();
        float before = source.parts.get(4).positions()[0];
        VehicleModel.from(source);
        assertEquals(before, source.parts.get(4).positions()[0]);
    }

    @Test
    void theWheelsAreInTheOrderFrontLeftFrontRightBackLeftBackRight() throws IOException {
        VehicleModel v = VehicleModel.from(mini());
        // the file has lf (-1, 1.5, -0.3), rf (1, 1.5, -0.3), lb (-1, -1.5, -0.3), rb (1, -1.5, -0.3) in GTA's x right, y forward, z up
        assertArrayEquals(new float[] {1.0f, -0.3f, 1.5f}, v.wheelPositions[0], 1e-6f); // left is +x in Minecraft's model space
        assertArrayEquals(new float[] {-1.0f, -0.3f, 1.5f}, v.wheelPositions[1], 1e-6f);
        assertArrayEquals(new float[] {1.0f, -0.3f, -1.5f}, v.wheelPositions[2], 1e-6f);
        assertArrayEquals(new float[] {-1.0f, -0.3f, -1.5f}, v.wheelPositions[3], 1e-6f);
        assertTrue(VehicleModel.isFront(0) && VehicleModel.isFront(1) && !VehicleModel.isFront(2) && !VehicleModel.isFront(3));
        assertTrue(VehicleModel.isLeft(0) && !VehicleModel.isLeft(1) && VehicleModel.isLeft(2) && !VehicleModel.isLeft(3));
    }

    @Test
    void theDriversSeatAndTheWheelRadius() throws IOException {
        VehicleModel v = VehicleModel.from(mini());
        assertArrayEquals(new float[] {0.5f, -0.1f, 0.1f}, v.driverSeat, 1e-6f); // GTA (-0.5, 0.1, -0.1)
        assertEquals(0.35f, v.wheelRadius, 1e-6f);
    }

    @Test
    void aModelWithoutTheVehicleExtrasIsRejectedWithAMessage() throws IOException {
        try (InputStream in = VehicleModelTest.class.getResourceAsStream("/mini.glb")) {
            GlbModel cj = GlbModel.read(in.readAllBytes());
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> VehicleModel.from(cj));
            assertTrue(e.getMessage().contains("wheels"), e.getMessage());
        }
    }

    private static com.google.gson.JsonObject extras(String json) {
        return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
    }

    private static final String WHEELS = "\"wheels\": {\"lf\": [-1, 1.5, -0.3], \"rf\": [1, 1.5, -0.3], \"lb\": [-1, -1.5, -0.3], \"rb\": [1, -1.5, -0.3]}";

    @Test
    void theNumbersComeFromTheExtrasOfTheFile() throws IOException {
        VehicleModel v = VehicleModel.from(mini(), extras("{" + WHEELS + ", \"driverSeat\": [-0.4, 0.2, 0.3], \"wheelRadius\": 0.5}"));
        assertEquals(0.5f, v.wheelRadius, 1e-6f);
        assertArrayEquals(new float[] {0.4f, 0.3f, 0.2f}, v.driverSeat, 1e-6f);
    }

    @Test
    void anyMissingOrBrokenNumberIsRejected() throws IOException {
        GlbModel source = mini();
        String[] bad = {
            "{\"driverSeat\": [0, 0, 0], \"wheelRadius\": 0.3}",                                               // no wheels
            "{" + WHEELS + ", \"wheelRadius\": 0.3}",                                                          // no seat
            "{" + WHEELS + ", \"driverSeat\": [0, 0, 0]}",                                                     // no radius
            "{" + WHEELS.replace("\"rb\": [1, -1.5, -0.3]", "\"xx\": [1, -1.5, -0.3]") + ", \"driverSeat\": [0, 0, 0], \"wheelRadius\": 0.3}", // a wheel less
            "{" + WHEELS + ", \"driverSeat\": [0, 0], \"wheelRadius\": 0.3}",                                  // a seat of two numbers
            "{" + WHEELS.replace("[1, 1.5, -0.3]", "[1, 1.5]") + ", \"driverSeat\": [0, 0, 0], \"wheelRadius\": 0.3}", // a wheel of two numbers
            "{" + WHEELS + ", \"driverSeat\": [0, 0, 0], \"wheelRadius\": 0}",                                // no radius at all
            "{" + WHEELS + ", \"driverSeat\": [0, 0, 0], \"wheelRadius\": -0.3}"};
        for (String json : bad) {
            assertThrows(IllegalArgumentException.class, () -> VehicleModel.from(source, extras(json)), json);
        }
    }

    @Test
    void aModelWithNoWheelMeshOrNoBodyIsRejected() throws IOException {
        GlbModel source = mini();
        assertFalse(source.parts.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> VehicleModel.from(onlyParts(source, true)));
        assertThrows(IllegalArgumentException.class, () -> VehicleModel.from(onlyParts(source, false)));
    }

    private static GlbModel onlyParts(GlbModel source, boolean wheels) {
        return source.withParts(source.parts.stream().filter(p -> p.name().startsWith("wheel") == wheels).toList());
    }
}
