package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WeaponModelLoaderTest {
    private static byte[] miniWeapon() throws IOException {
        try (java.io.InputStream in = WeaponModelLoaderTest.class.getResourceAsStream("/mini_weapon.glb")) {
            return in.readAllBytes();
        }
    }

    @Test
    void aGoodFileLoads(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("colt45.glb");
        Files.write(file, miniWeapon());
        WeaponModelLoader.Result r = WeaponModelLoader.read(file);
        assertNotNull(r.model());
        assertNull(r.problem());
    }

    @Test
    void aModelWithMoreThanOneJointIsAProblemNotARenderFailure(@TempDir Path dir) throws IOException {
        // CJ's own model (three joints in the test model) is a valid .glb but not a weapon: drawing it as one would index past the one
        // joint the weapon's pose has and disable the drawing of CJ himself
        Path file = dir.resolve("colt45.glb");
        Files.write(file, GlbModelTest.mini());
        WeaponModelLoader.Result r = WeaponModelLoader.read(file);
        assertNull(r.model());
        assertTrue(r.problem().contains("colt45.glb") && r.problem().contains("joint"), r.problem());
    }

    @Test
    void aMissingFileSaysHowToFixIt(@TempDir Path dir) {
        WeaponModelLoader.Result r = WeaponModelLoader.read(dir.resolve("colt45.glb"));
        assertNull(r.model());
        assertTrue(r.problem().contains("colt45.glb") && r.problem().contains("convert_assets.py --weapons"), r.problem());
    }

    @Test
    void aCorruptFileIsAProblemNotAnException(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("ak47.glb");
        Files.write(file, new byte[] {1, 2, 3, 4, 5});
        WeaponModelLoader.Result r = WeaponModelLoader.read(file);
        assertNull(r.model());
        assertTrue(r.problem().contains("ak47.glb"), r.problem());
    }

    @Test
    void aTruncatedFileIsAProblemNotAnException(@TempDir Path dir) throws IOException {
        byte[] good = GlbModelTest.mini();
        Path file = dir.resolve("m4.glb");
        Files.write(file, java.util.Arrays.copyOf(good, good.length / 2));
        assertNull(WeaponModelLoader.read(file).model());
    }
}
