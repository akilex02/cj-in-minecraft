package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HeadingsTest {
    private static final double HALF_PI = Math.PI / 2;

    @Test
    void gtaHeadingsMapToMinecraftYaws() {
        // GTA heading is counter-clockwise from north; Minecraft yaw 0 faces south, 90 west, -90 east, 180 north
        assertEquals(0f, Headings.mcYawFromGta((float) Math.PI), 1e-4f);        // south
        assertEquals(90f, Headings.mcYawFromGta((float) HALF_PI), 1e-4f);       // west
        assertEquals(-90f, Headings.mcYawFromGta((float) (3 * HALF_PI)), 1e-4f); // east
        assertEquals(-180f, Headings.mcYawFromGta(0f), 1e-4f); // north is yaw 180, wrapped to -180
    }

    @Test
    void theResultIsAlwaysInTheWrappedRange() {
        for (float h = -7f; h < 14f; h += 0.37f) {
            float yaw = Headings.mcYawFromGta(h);
            assertEquals(true, yaw >= -180f && yaw < 180f, "heading " + h + " gave " + yaw);
        }
    }
}
