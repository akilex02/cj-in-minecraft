package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AmmoResourcesTest {
    private static final Map<String, String> NAMES = Map.of("ammo1", "Light Ammo", "ammo2", "Heavy Ammo", "ammo3", "Rocket Ammo");

    private static String read(String path) throws Exception {
        try (InputStream in = AmmoResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void eachAmmoHasItsNameModelAndTexture() throws Exception {
        JsonObject lang = JsonParser.parseString(read("/assets/cj_fabric/lang/en_us.json")).getAsJsonObject();
        for (String id : AmmoRules.ALL) {
            assertNotNull(lang.get("item.cj_fabric." + id), id + " has no name");
            assertEquals(NAMES.get(id), lang.get("item.cj_fabric." + id).getAsString(), id);
            JsonObject model = JsonParser.parseString(read("/assets/cj_fabric/models/item/" + id + ".json")).getAsJsonObject();
            assertEquals("cj_fabric:item/gta/" + id, model.getAsJsonObject("textures").get("layer0").getAsString(), id);
            assertNotNull(AmmoResourcesTest.class.getResourceAsStream("/assets/cj_fabric/textures/item/gta/" + id + ".png"), id);
        }
    }
}
