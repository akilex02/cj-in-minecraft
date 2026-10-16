package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DurabilityResourcesTest {
    @Test
    void theEnchantableTagListsEveryWeaponThatWearsOutSoItTakesMendingAndUnbreaking() throws Exception {
        Set<String> tagged = new HashSet<>();
        try (InputStream in = DurabilityResourcesTest.class.getResourceAsStream("/data/minecraft/tags/item/enchantable/durability.json")) {
            assertNotNull(in, "the tag is missing");
            for (JsonElement e : JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("values")) {
                tagged.add(e.getAsString());
            }
        }
        Set<String> wearing = new HashSet<>();
        for (WeaponCatalog.WeaponDef d : WeaponCatalog.load().all()) {
            if (d.item() && d.durability() > 0) {
                wearing.add("cj_fabric:" + d.id());
            }
        }
        assertEquals(wearing, tagged);
    }
}
