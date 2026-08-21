package team.starm.starMSkyblockGamePlay.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
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
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;
import team.starm.starMSkyblockGamePlay.util.EntityTypeSpawnEggMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class SnowballConverterListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;
    private final NamespacedKey flagKey;
    private final NamespacedKey chanceKey;

    public SnowballConverterListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.flagKey = new NamespacedKey(plugin, "mob_converter");
        this.chanceKey = new NamespacedKey(plugin, "mob_converter_chance");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSnowballLaunch(ProjectileLaunchEvent event) {
        if (!plugin.getConfig().getBoolean("snowball-converter.enabled", true)) return;
        if (!(event.getEntity() instanceof Snowball snowball)) return;
        if (!(snowball.getShooter() instanceof Player player)) return;

        // Copy PDC from the held item to the snowball entity.
        // When a player throws a snowball, the item's PDC does NOT automatically
        // transfer to the projectile entity, so we must do it explicitly.
        for (ItemStack item : new ItemStack[]{
                player.getInventory().getItemInMainHand(),
                player.getInventory().getItemInOffHand()
        }) {
            if (item.getType() != Material.SNOWBALL || !item.hasItemMeta()) continue;
            var meta = item.getItemMeta();
            var itemPDC = meta.getPersistentDataContainer();
            if (!itemPDC.has(flagKey, PersistentDataType.BOOLEAN)) continue;

            var snowballPDC = snowball.getPersistentDataContainer();
            snowballPDC.set(flagKey, PersistentDataType.BOOLEAN, true);
            if (itemPDC.has(chanceKey, PersistentDataType.INTEGER)) {
                snowballPDC.set(chanceKey, PersistentDataType.INTEGER,
                        itemPDC.get(chanceKey, PersistentDataType.INTEGER));
            }
            return; // Found the converter snowball in this hand
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSnowballHit(ProjectileHitEvent event) {
        if (!plugin.getConfig().getBoolean("snowball-converter.enabled", true)) return;

        // Check projectile is a snowball
        if (!(event.getEntity() instanceof Snowball snowball)) return;

        // Check snowball has the converter PDC flag
        if (!snowball.getPersistentDataContainer().has(flagKey, PersistentDataType.BOOLEAN)) return;

        ProjectileSource shooter = snowball.getShooter();
        if (!(shooter instanceof Player player)) return;

        boolean refundOnMiss = plugin.getConfig().getBoolean("snowball-converter.refund-on-miss", true);

        // Check hit entity is a living entity
        Entity hitEntity = event.getHitEntity();
        if (hitEntity == null || !(hitEntity instanceof LivingEntity living)) {
            // No creature was hit (block hit, non-living entity, etc.) — return the ball.
            if (refundOnMiss) {
                returnSnowball(snowball, player);
                player.sendActionBar(lang.getComponent("snowball-converter.miss-returned", Map.of()));
            }
            return;
        }

        // Entity type checks
        var entityType = living.getType();

        // Build translatable entity name component (auto-translates to player's client language)
        Component entityComp = Component.translatable("entity.minecraft." + entityType.getKey().getKey());

        if (!EntityTypeSpawnEggMapper.hasSpawnEgg(entityType)) {
            sendUncatchableResult(player, refundOnMiss, snowball, entityComp,
                    "snowball-converter.no-egg-returned", "snowball-converter.no-egg");
            return;
        }

        // Whitelist / Blacklist check
        boolean useWhitelist = plugin.getConfig().getBoolean("snowball-converter.use-whitelist", false);
        if (useWhitelist) {
            List<String> whitelist = plugin.getConfig().getStringList("snowball-converter.whitelist");
            if (!whitelist.contains(entityType.name())) {
                sendUncatchableResult(player, refundOnMiss, snowball, entityComp,
                        "snowball-converter.cannot-catch-returned", "snowball-converter.cannot-catch");
                return;
            }
        } else {
            List<String> blacklist = plugin.getConfig().getStringList("snowball-converter.blacklist");
            if (blacklist.contains(entityType.name())) {
                sendUncatchableResult(player, refundOnMiss, snowball, entityComp,
                        "snowball-converter.cannot-catch-returned", "snowball-converter.cannot-catch");
                return;
            }
        }

        // Calculate chance
        int chance = resolveChance(entityType, snowball);
        if (chance <= 0) {
            // A zero (or negative) chance means this creature can never be captured.
            sendUncatchableResult(player, refundOnMiss, snowball, entityComp,
                    "snowball-converter.cannot-catch-returned", "snowball-converter.cannot-catch");
            return;
        }

        // Roll
        if (ThreadLocalRandom.current().nextInt(100) >= chance) {
            player.sendActionBar(lang.getComponent("snowball-converter.failed",
                    Map.of("entity", entityComp)));
            return;
        }

        // Success: create spawn egg
        Material eggMaterial = EntityTypeSpawnEggMapper.getSpawnEgg(entityType);
        if (eggMaterial == null) {
            sendUncatchableResult(player, refundOnMiss, snowball, entityComp,
                    "snowball-converter.no-egg-returned", "snowball-converter.no-egg");
            return;
        }

        Location loc = living.getLocation();
        living.remove();

        ItemStack egg = new ItemStack(eggMaterial, 1);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(egg);
        if (!leftover.isEmpty()) {
            loc.getWorld().dropItemNaturally(loc, leftover.values().iterator().next());
        }

        player.sendActionBar(lang.getComponent("snowball-converter.converted",
                    Map.of("entity", entityComp)));
    }

    /**
     * Sends the appropriate action-bar message for an uncatchable target and,
     * when enabled, returns the enhanced snowball to the thrower.
     */
    private void sendUncatchableResult(Player player, boolean refundOnMiss, Snowball snowball,
                                       Component entityComp, String returnedMessagePath, String baseMessagePath) {
        if (refundOnMiss) {
            returnSnowball(snowball, player);
            player.sendActionBar(lang.getComponent(returnedMessagePath, Map.of("entity", entityComp)));
        } else {
            player.sendActionBar(lang.getComponent(baseMessagePath, Map.of("entity", entityComp)));
        }
    }

    /**
     * Gives a new enhanced snowball back to the thrower. If the inventory is full,
     * the ball drops at the player's feet (or at the projectile if the player went offline).
     */
    private void returnSnowball(Snowball snowball, Player player) {
        ItemStack ball = createConverterSnowball(snowball);

        if (player.isOnline()) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(ball);
            if (!leftover.isEmpty()) {
                Location playerLoc = player.getLocation();
                if (playerLoc.getWorld() != null) {
                    for (ItemStack item : leftover.values()) {
                        playerLoc.getWorld().dropItemNaturally(playerLoc, item);
                    }
                }
            }
            return;
        }

        Location snowballLoc = snowball.getLocation();
        if (snowballLoc.getWorld() != null) {
            snowballLoc.getWorld().dropItemNaturally(snowballLoc, ball);
        }
    }

    /**
     * Rebuilds the enhanced snowball item, preserving the custom capture chance PDC
     * and re-applying the configured display name / lore.
     */
    private ItemStack createConverterSnowball(Snowball snowball) {
        ItemStack item = new ItemStack(Material.SNOWBALL);
        item.editMeta(meta -> {
            meta.setEnchantmentGlintOverride(true);

            var itemPDC = meta.getPersistentDataContainer();
            itemPDC.set(flagKey, PersistentDataType.BOOLEAN, true);
            Integer customChance = snowball.getPersistentDataContainer()
                    .get(chanceKey, PersistentDataType.INTEGER);
            if (customChance != null && customChance > 0) {
                itemPDC.set(chanceKey, PersistentDataType.INTEGER, customChance);
            }

            String itemName = plugin.getConfig().getString("snowball-converter.item-name");
            if (itemName != null && !itemName.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemName));
            }

            List<String> lore = plugin.getConfig().getStringList("snowball-converter.lore");
            if (!lore.isEmpty()) {
                meta.setLore(lore.stream()
                        .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                        .toList());
            }
        });
        return item;
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
