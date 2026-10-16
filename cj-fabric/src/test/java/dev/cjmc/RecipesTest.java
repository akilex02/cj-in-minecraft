package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RecipesTest {
    private static final List<String> IDS = List.of("ammo1", "ammo2", "ammo3", "colt45", "desert_eagle", "micro_uzi", "chromegun",
            "ak47", "m4", "minigun", "rocket_launcher", "baseball_bat", "jetpack", "skateboard");
    private static final Map<String, Integer> COUNTS = Map.of("ammo1", 32, "ammo2", 16, "ammo3", 4);

    private static JsonObject recipe(String id) throws Exception {
        try (InputStream in = RecipesTest.class.getResourceAsStream("/data/cj_fabric/recipe/" + id + ".json")) {
            assertNotNull(in, "recipe " + id + " is missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyRecipeIsAWellFormedShapedRecipeForItsItem() throws Exception {
        for (String id : IDS) {
            JsonObject r = recipe(id);
            assertEquals("minecraft:crafting_shaped", r.get("type").getAsString(), id);
            JsonObject result = r.getAsJsonObject("result");
            assertEquals("cj_fabric:" + id, result.get("id").getAsString(), id);
            assertEquals(COUNTS.getOrDefault(id, 1), result.get("count").getAsInt(), id);
            JsonArray pattern = r.getAsJsonArray("pattern");
            assertTrue(pattern.size() >= 1 && pattern.size() <= 3, id + " has " + pattern.size() + " rows");
            JsonObject key = r.getAsJsonObject("key");
            for (var row : pattern) {
                assertEquals(3, row.getAsString().length(), id + " row " + row);
                for (char c : row.getAsString().toCharArray()) {
                    assertTrue(c == ' ' || key.has(String.valueOf(c)), id + " uses the undefined key '" + c + "'");
                }
            }
            for (String k : key.keySet()) {
                assertTrue(pattern.toString().contains(k), id + " defines the unused key " + k);
            }
        }
    }

    @Test
    void theAmmoRecipesFollowTheNumpadLayoutOfTheUser() throws Exception {
        assertEquals(List.of("NNN", "NgN", "NrN"), rows("ammo1"));
        assertEquals(List.of("NgN", "NgN", "rNr"), rows("ammo2"));
        assertEquals(List.of("NfN", "NfN", "rgr"), rows("ammo3"));
    }

    @Test
    void theShotgunTakesAnyPlank() throws Exception {
        assertEquals("minecraft:planks", recipe("chromegun").getAsJsonObject("key").getAsJsonObject("P").get("tag").getAsString());
    }

    private static List<String> rows(String id) throws Exception {
        return recipe(id).getAsJsonArray("pattern").asList().stream().map(e -> e.getAsString()).toList();
    }
}
