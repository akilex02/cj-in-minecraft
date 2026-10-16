package dev.cjmc.combat;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/** The entity types of the mod (registered on both sides, since the integrated server needs them too). */
public final class CjEntities {
    public static final String MOD_ID = "cj_fabric";
    public static EntityType<RocketEntity> ROCKET;

    private CjEntities() {}

    public static void register() {
        Identifier id = Identifier.of(MOD_ID, "rocket");
        ROCKET = Registry.register(Registries.ENTITY_TYPE, id,
                EntityType.Builder.<RocketEntity>create(RocketEntity::new, SpawnGroup.MISC)
                        .dimensions(0.3f, 0.3f).maxTrackingRange(16).trackingTickInterval(1)
                        .disableSummon().disableSaving().build(id.toString()));
    }
}
