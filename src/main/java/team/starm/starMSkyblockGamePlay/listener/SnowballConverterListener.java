package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;
import team.starm.starMSkyblockGamePlay.util.EntityTypeSpawnEggMapper;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class SnowballConverterListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;
    private final NamespacedKey flagKey;
    private final NamespacedKey chanceKey;
    private final Random random = ThreadLocalRandom.current();

    public SnowballConverterListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.flagKey = new NamespacedKey(plugin, "mob_converter");
        this.chanceKey = new NamespacedKey(plugin, "mob_converter_chance");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!plugin.getConfig().getBoolean("snowball-converter.enabled", true)) return;

        // Check projectile is a snowball
        if (!(event.getEntity() instanceof Snowball snowball)) return;

        // Check snowball has the converter PDC flag
        if (!snowball.getPersistentDataContainer().has(flagKey, PersistentDataType.BOOLEAN)) return;

        // Check hit entity is a living entity (not the shooter)
        Entity hitEntity = event.getHitEntity();
        if (hitEntity == null || !(hitEntity instanceof LivingEntity living)) return;

        ProjectileSource shooter = snowball.getShooter();
        if (!(shooter instanceof Player player)) return;

        // Don't convert the shooter themselves (edge case)
        if (hitEntity.equals(player)) return;

        // Entity type checks
        var entityType = living.getType();
        if (!EntityTypeSpawnEggMapper.hasSpawnEgg(entityType)) {
            player.sendMessage(lang.getColored("snowball-converter.no-egg"));
            return;
        }

        // Blacklist check
        List<String> blacklist = plugin.getConfig().getStringList("snowball-converter.blacklist");
        if (blacklist.contains(entityType.name())) return;

        // Calculate chance
        int chance = resolveChance(entityType, snowball);
        if (chance <= 0) return;

        // Roll
        if (random.nextInt(100) >= chance) {
            player.sendMessage(lang.getColored("snowball-converter.failed"));
            return;
        }

        // Success: create spawn egg
        Material eggMaterial = EntityTypeSpawnEggMapper.getSpawnEgg(entityType);
        if (eggMaterial == null) {
            player.sendMessage(lang.getColored("snowball-converter.no-egg"));
            return;
        }

        Location loc = living.getLocation();
        living.remove();

        ItemStack egg = new ItemStack(eggMaterial, 1);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(egg);
        if (!leftover.isEmpty()) {
            loc.getWorld().dropItemNaturally(loc, egg);
        }

        player.sendMessage(lang.getColored("snowball-converter.converted"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSnowballDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("snowball-converter.enabled", true)) return;

        // Cancel damage from converter snowballs so they don't hurt AND convert
        if (!(event.getDamager() instanceof Snowball snowball)) return;
        if (!snowball.getPersistentDataContainer().has(flagKey, PersistentDataType.BOOLEAN)) return;

        event.setCancelled(true);
    }

    /**
     * Resolve the conversion chance: snowball PDC override > mob-chances config > global-chance config.
     */
    private int resolveChance(EntityType entityType, Snowball snowball) {
        // 1) Snowball PDC custom chance
        int custom = snowball.getPersistentDataContainer()
                .getOrDefault(chanceKey, PersistentDataType.INTEGER, 0);
        if (custom > 0) return custom;

        // 2) Per-entity type config
        String path = "snowball-converter.mob-chances." + entityType.name();
        if (plugin.getConfig().isInt(path)) {
            return plugin.getConfig().getInt(path);
        }

        // 3) Global default
        return plugin.getConfig().getInt("snowball-converter.global-chance", 30);
    }
}
