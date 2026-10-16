package dev.cjmc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CjItemsTest {
    private static WeaponCatalog shipped() throws Exception {
        try (var in = CjItemsTest.class.getResourceAsStream("/weapons.json")) {
            return WeaponCatalog.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    @Test
    void onlyTheFistsAreTheFists() throws Exception {
        WeaponCatalog c = shipped();
        assertTrue(CjItems.isFists(c.byId("fists")));
        for (WeaponCatalog.WeaponDef d : c.all()) {
            if (!d.id().equals("fists")) {
                assertFalse(CjItems.isFists(d), d.id());
            }
        }
        assertFalse(CjItems.isFists(null));
    }

    @Test
    void theFistsAreNotAnItemSoTheyAreNeitherRegisteredNorGiven() throws Exception {
        assertFalse(shipped().byId("fists").item());
    }
}
