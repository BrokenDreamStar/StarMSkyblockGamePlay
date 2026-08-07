package team.starm.starMSkyblockGamePlay.util;

import org.bukkit.entity.EntityType;
import org.bukkit.Material;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public final class EntityTypeSpawnEggMapper {

    private static final Map<EntityType, Material> SPAWN_EGG_MAP = buildMap();

    private static Map<EntityType, Material> buildMap() {
        Map<EntityType, Material> map = new HashMap<>();
        // --- Overworld passive ---
        map.put(EntityType.ALLAY, Material.ALLAY_SPAWN_EGG);
        map.put(EntityType.ARMADILLO, Material.ARMADILLO_SPAWN_EGG);
        map.put(EntityType.AXOLOTL, Material.AXOLOTL_SPAWN_EGG);
        map.put(EntityType.BAT, Material.BAT_SPAWN_EGG);
        map.put(EntityType.BEE, Material.BEE_SPAWN_EGG);
        map.put(EntityType.BLAZE, Material.BLAZE_SPAWN_EGG);
        map.put(EntityType.BOGGED, Material.BOGGED_SPAWN_EGG);
        map.put(EntityType.BREEZE, Material.BREEZE_SPAWN_EGG);
        map.put(EntityType.CAMEL, Material.CAMEL_SPAWN_EGG);
        map.put(EntityType.CAT, Material.CAT_SPAWN_EGG);
        map.put(EntityType.CAVE_SPIDER, Material.CAVE_SPIDER_SPAWN_EGG);
        map.put(EntityType.CHICKEN, Material.CHICKEN_SPAWN_EGG);
        map.put(EntityType.COD, Material.COD_SPAWN_EGG);
        map.put(EntityType.COW, Material.COW_SPAWN_EGG);
        map.put(EntityType.CREEPER, Material.CREEPER_SPAWN_EGG);
        map.put(EntityType.DOLPHIN, Material.DOLPHIN_SPAWN_EGG);
        map.put(EntityType.DONKEY, Material.DONKEY_SPAWN_EGG);
        map.put(EntityType.DROWNED, Material.DROWNED_SPAWN_EGG);
        map.put(EntityType.ELDER_GUARDIAN, Material.ELDER_GUARDIAN_SPAWN_EGG);
        map.put(EntityType.ENDERMAN, Material.ENDERMAN_SPAWN_EGG);
        map.put(EntityType.ENDERMITE, Material.ENDERMITE_SPAWN_EGG);
        map.put(EntityType.EVOKER, Material.EVOKER_SPAWN_EGG);
        map.put(EntityType.FOX, Material.FOX_SPAWN_EGG);
        map.put(EntityType.FROG, Material.FROG_SPAWN_EGG);
        map.put(EntityType.GHAST, Material.GHAST_SPAWN_EGG);
        map.put(EntityType.GLOW_SQUID, Material.GLOW_SQUID_SPAWN_EGG);
        map.put(EntityType.GOAT, Material.GOAT_SPAWN_EGG);
        map.put(EntityType.GUARDIAN, Material.GUARDIAN_SPAWN_EGG);
        map.put(EntityType.HOGLIN, Material.HOGLIN_SPAWN_EGG);
        map.put(EntityType.HORSE, Material.HORSE_SPAWN_EGG);
        map.put(EntityType.HUSK, Material.HUSK_SPAWN_EGG);
        // ILLUSIONER has no spawn egg in Paper 26.1.2
        map.put(EntityType.IRON_GOLEM, Material.IRON_GOLEM_SPAWN_EGG);
        map.put(EntityType.LLAMA, Material.LLAMA_SPAWN_EGG);
        map.put(EntityType.MAGMA_CUBE, Material.MAGMA_CUBE_SPAWN_EGG);
        map.put(EntityType.MOOSHROOM, Material.MOOSHROOM_SPAWN_EGG);
        map.put(EntityType.MULE, Material.MULE_SPAWN_EGG);
        map.put(EntityType.NAUTILUS, Material.NAUTILUS_SPAWN_EGG);
        map.put(EntityType.OCELOT, Material.OCELOT_SPAWN_EGG);
        map.put(EntityType.PANDA, Material.PANDA_SPAWN_EGG);
        map.put(EntityType.PARCHED, Material.PARCHED_SPAWN_EGG);
        map.put(EntityType.PARROT, Material.PARROT_SPAWN_EGG);
        map.put(EntityType.PHANTOM, Material.PHANTOM_SPAWN_EGG);
        map.put(EntityType.PIG, Material.PIG_SPAWN_EGG);
        map.put(EntityType.PIGLIN, Material.PIGLIN_SPAWN_EGG);
        map.put(EntityType.PIGLIN_BRUTE, Material.PIGLIN_BRUTE_SPAWN_EGG);
        map.put(EntityType.PILLAGER, Material.PILLAGER_SPAWN_EGG);
        map.put(EntityType.POLAR_BEAR, Material.POLAR_BEAR_SPAWN_EGG);
        map.put(EntityType.PUFFERFISH, Material.PUFFERFISH_SPAWN_EGG);
        map.put(EntityType.RABBIT, Material.RABBIT_SPAWN_EGG);
        map.put(EntityType.RAVAGER, Material.RAVAGER_SPAWN_EGG);
        map.put(EntityType.SALMON, Material.SALMON_SPAWN_EGG);
        map.put(EntityType.SHEEP, Material.SHEEP_SPAWN_EGG);
        map.put(EntityType.SHULKER, Material.SHULKER_SPAWN_EGG);
        map.put(EntityType.SILVERFISH, Material.SILVERFISH_SPAWN_EGG);
        map.put(EntityType.SKELETON, Material.SKELETON_SPAWN_EGG);
        map.put(EntityType.SKELETON_HORSE, Material.SKELETON_HORSE_SPAWN_EGG);
        map.put(EntityType.SLIME, Material.SLIME_SPAWN_EGG);
        map.put(EntityType.SNIFFER, Material.SNIFFER_SPAWN_EGG);
        map.put(EntityType.SNOW_GOLEM, Material.SNOW_GOLEM_SPAWN_EGG);
        map.put(EntityType.SPIDER, Material.SPIDER_SPAWN_EGG);
        map.put(EntityType.SQUID, Material.SQUID_SPAWN_EGG);
        map.put(EntityType.STRAY, Material.STRAY_SPAWN_EGG);
        map.put(EntityType.STRIDER, Material.STRIDER_SPAWN_EGG);
        map.put(EntityType.TADPOLE, Material.TADPOLE_SPAWN_EGG);
        map.put(EntityType.TRADER_LLAMA, Material.TRADER_LLAMA_SPAWN_EGG);
        map.put(EntityType.TROPICAL_FISH, Material.TROPICAL_FISH_SPAWN_EGG);
        map.put(EntityType.TURTLE, Material.TURTLE_SPAWN_EGG);
        map.put(EntityType.VEX, Material.VEX_SPAWN_EGG);
        map.put(EntityType.VILLAGER, Material.VILLAGER_SPAWN_EGG);
        map.put(EntityType.VINDICATOR, Material.VINDICATOR_SPAWN_EGG);
        map.put(EntityType.WANDERING_TRADER, Material.WANDERING_TRADER_SPAWN_EGG);
        map.put(EntityType.WARDEN, Material.WARDEN_SPAWN_EGG);
        map.put(EntityType.WITCH, Material.WITCH_SPAWN_EGG);
        map.put(EntityType.WITHER_SKELETON, Material.WITHER_SKELETON_SPAWN_EGG);
        map.put(EntityType.WOLF, Material.WOLF_SPAWN_EGG);
        map.put(EntityType.ZOGLIN, Material.ZOGLIN_SPAWN_EGG);
        map.put(EntityType.ZOMBIE, Material.ZOMBIE_SPAWN_EGG);
        map.put(EntityType.ZOMBIE_HORSE, Material.ZOMBIE_HORSE_SPAWN_EGG);
        map.put(EntityType.ZOMBIE_NAUTILUS, Material.ZOMBIE_NAUTILUS_SPAWN_EGG);
        map.put(EntityType.ZOMBIE_VILLAGER, Material.ZOMBIE_VILLAGER_SPAWN_EGG);
        map.put(EntityType.ZOMBIFIED_PIGLIN, Material.ZOMBIFIED_PIGLIN_SPAWN_EGG);
        return Collections.unmodifiableMap(map);
    }

    public static Material getSpawnEgg(EntityType type) {
        return SPAWN_EGG_MAP.get(type);
    }

    public static boolean hasSpawnEgg(EntityType type) {
        return SPAWN_EGG_MAP.containsKey(type);
    }

    private EntityTypeSpawnEggMapper() {}
}
