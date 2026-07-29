# Snowball Converter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a new listener that converts mobs to spawn eggs when hit by special NBT snowballs.

**Architecture:** New `SnowballConverterListener` handles `ProjectileHitEvent` and `EntityDamageByEntityEvent`. A static utility `EntityTypeSpawnEggMapper` maps `EntityType` → `Material` spawn egg. Admin command `/starmskyblockgameplay givesnowball` grants tagged snowballs.

**Tech Stack:** Paper API 26.1.2, Java 25, no external dependencies.

## Global Constraints

- Follow existing project patterns (PDC with `NamespacedKey`, `LanguageManager.getColored()`, config toggles with `enabled` flag)
- All messages go through `messages.yml` with placeholder substitution
- Config section: `snowball-converter.*`
- PDC key: `NamespacedKey(plugin, "mob_converter")` for flag, `NamespacedKey(plugin, "mob_converter_chance")` for optional chance override
- No new dependencies beyond Paper API
- Entity type names in config use Bukkit's `EntityType.name()` convention

---

### Task 1: EntityType to Spawn Egg Mapping Utility

**Files:**
- Create: `src/main/java/team/starm/starMSkyblockGamePlay/util/EntityTypeSpawnEggMapper.java`

**Interfaces:**
- Consumes: Nothing
- Produces: `EntityTypeSpawnEggMapper.getSpawnEgg(EntityType) → Material | null`

- [ ] **Step 1: Create the utility class**

```java
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
        map.put(EntityType.ILLUSIONER, Material.ILLUSIONER_SPAWN_EGG);
        map.put(EntityType.IRON_GOLEM, Material.IRON_GOLEM_SPAWN_EGG);
        map.put(EntityType.LLAMA, Material.LLAMA_SPAWN_EGG);
        map.put(EntityType.MAGMA_CUBE, Material.MAGMA_CUBE_SPAWN_EGG);
        map.put(EntityType.MOOSHROOM, Material.MOOSHROOM_SPAWN_EGG);
        map.put(EntityType.MULE, Material.MULE_SPAWN_EGG);
        map.put(EntityType.OCELOT, Material.OCELOT_SPAWN_EGG);
        map.put(EntityType.PANDA, Material.PANDA_SPAWN_EGG);
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew build`
Expected: Build succeeds

- [ ] **Step 3: Create util directory and commit**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/util/EntityTypeSpawnEggMapper.java
git commit -m "feat: add EntityTypeSpawnEggMapper utility"
```

---

### Task 2: SnowballConverterListener

**Files:**
- Create: `src/main/java/team/starm/starMSkyblockGamePlay/listener/SnowballConverterListener.java`

**Interfaces:**
- Consumes: `EntityTypeSpawnEggMapper.getSpawnEgg(EntityType) → Material`, `LanguageManager.getColored(path, placeholders)`
- Produces: Listener registered in main plugin class

- [ ] **Step 1: Create the listener**

```java
package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
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
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew build`
Expected: Build succeeds

- [ ] **Step 3: Commit**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/listener/SnowballConverterListener.java
git commit -m "feat: add SnowballConverterListener"
```

---

### Task 3: Configuration and Messages

**Files:**
- Modify: `src/main/resources/config.yml`
- Modify: `src/main/resources/messages.yml`

- [ ] **Step 1: Add snowball-converter section to config.yml**

Append to end of `src/main/resources/config.yml`:

```yaml
# 雪球转换功能（击中生物有概率转换为对应刷怪蛋）
snowball-converter:
  # 是否启用该功能
  enabled: true
  # 全局转换概率（百分比 0-100）
  global-chance: 30
  # 黑名单生物类型列表（EntityType 枚举名）
  blacklist:
    - IRON_GOLEM
    - SNOW_GOLEM
    - WITHER
    - ELDER_GUARDIAN
  # 独立转换概率（按生物类型覆盖全局概率）
  mob-chances:
    ZOMBIE: 50
    CREEPER: 40
    SKELETON: 35
```

- [ ] **Step 2: Add messages to messages.yml**

Append to end of `src/main/resources/messages.yml`:

```yaml
snowball-converter:
  converted: "&a成功将生物转换为刷怪蛋！"
  failed: "&7生物抵抗了转换效果..."
  no-egg: "&c该生物没有对应的刷怪蛋"
  give-snowball: "&a已给予 {player} {amount} 个特殊雪球"
```

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config.yml src/main/resources/messages.yml
git commit -m "feat: add snowball-converter config and messages"
```

---

### Task 4: Command Handler and Listener Registration

**Files:**
- Modify: `src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java`

- [ ] **Step 1: Add the givesnowball subcommand and register the new listener**

Changes to `StarMSkyblockGamePlay.java`:

1. Add import for `SnowballConverterListener`:
```java
import team.starm.starMSkyblockGamePlay.listener.SnowballConverterListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
```

2. Register the listener in `onEnable()`, after the other listeners:
```java
getServer().getPluginManager().registerEvents(new SnowballConverterListener(this), this);
```

3. Add the `givesnowball` subcommand in `onCommand()`:

After the `reload` subcommand block, add:

```java
if (args[0].equalsIgnoreCase("givesnowball")) {
    if (args.length < 2) {
        sender.sendMessage("§c用法: /starmskyblockgameplay givesnowball <玩家> [数量] [概率]");
        return true;
    }

    Player target = getServer().getPlayer(args[1]);
    if (target == null) {
        sender.sendMessage("§c找不到玩家: " + args[1]);
        return true;
    }

    int amount = 1;
    if (args.length >= 3) {
        try { amount = Math.max(1, Integer.parseInt(args[2])); }
        catch (NumberFormatException e) {
            sender.sendMessage("§c数量必须是有效整数。");
            return true;
        }
    }

    int chance = 0;
    if (args.length >= 4) {
        try {
            chance = Math.clamp(Integer.parseInt(args[3]), 0, 100);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c概率必须是 0-100 的整数。");
            return true;
        }
    }

    NamespacedKey flagKey = new NamespacedKey(this, "mob_converter");
    NamespacedKey chanceKey = new NamespacedKey(this, "mob_converter_chance");

    ItemStack snowball = new ItemStack(Material.SNOWBALL, amount);
    snowball.editMeta(meta -> {
        meta.getPersistentDataContainer().set(flagKey, PersistentDataType.BOOLEAN, true);
        if (chance > 0) {
            meta.getPersistentDataContainer().set(chanceKey, PersistentDataType.INTEGER, chance);
        }
    });

    target.getInventory().addItem(snowball);
    sender.sendMessage(lang.getColored("snowball-converter.give-snowball",
            Map.of("player", target.getName(), "amount", String.valueOf(amount))));
    return true;
}
```

4. Add required imports at top:
```java
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.Map;
```

Full updated `onCommand` method structure:
```java
@Override
public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
    if (!command.getName().equalsIgnoreCase("starmskyblockgameplay")) {
        return false;
    }

    if (!sender.hasPermission("starmskyblockgameplay.admin")) {
        sender.sendMessage("§c你没有权限执行此命令。");
        return true;
    }

    if (args.length < 1) {
        sender.sendMessage("§c用法: /starmskyblockgameplay reload | givesnowball <玩家> [数量] [概率]");
        return true;
    }

    if (args[0].equalsIgnoreCase("reload")) {
        reloadConfig();
        languageManager.reloadMessages();
        sender.sendMessage("§aStarMSkyblockGamePlay 配置已重载。");
        getLogger().info("配置与消息已重载（由 " + sender.getName() + " 执行）。");
        return true;
    }

    if (args[0].equalsIgnoreCase("givesnowball")) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /starmskyblockgameplay givesnowball <玩家> [数量] [概率]");
            return true;
        }

        Player target = getServer().getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("§c找不到玩家: " + args[1]);
            return true;
        }

        int amount = 1;
        if (args.length >= 3) {
            try { amount = Math.max(1, Integer.parseInt(args[2])); }
            catch (NumberFormatException e) {
                sender.sendMessage("§c数量必须是有效整数。");
                return true;
            }
        }

        int chance = 0;
        if (args.length >= 4) {
            try {
                chance = Math.clamp(Integer.parseInt(args[3]), 0, 100);
            } catch (NumberFormatException e) {
                sender.sendMessage("§c概率必须是 0-100 的整数。");
                return true;
            }
        }

        NamespacedKey flagKey = new NamespacedKey(this, "mob_converter");
        NamespacedKey chanceKey = new NamespacedKey(this, "mob_converter_chance");

        ItemStack snowball = new ItemStack(Material.SNOWBALL, amount);
        snowball.editMeta(meta -> {
            meta.getPersistentDataContainer().set(flagKey, PersistentDataType.BOOLEAN, true);
            if (chance > 0) {
                meta.getPersistentDataContainer().set(chanceKey, PersistentDataType.INTEGER, chance);
            }
        });

        target.getInventory().addItem(snowball);
        sender.sendMessage(languageManager.getColored("snowball-converter.give-snowball",
                Map.of("player", target.getName(), "amount", String.valueOf(amount))));
        return true;
    }

    sender.sendMessage("§c未知子命令。用法: /starmskyblockgameplay reload | givesnowball <玩家> [数量] [概率]");
    return true;
}
```

- [ ] **Step 2: Build and verify**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java
git commit -m "feat: add givesnowball command and register SnowballConverterListener"
```

---

### Task 5: Final Verification

- [ ] **Step 1: Clean build**

Run: `./gradlew clean build`
Expected: BUILD SUCCESSFUL in < 30s

- [ ] **Step 2: Final commit**

```bash
git add .
git commit -m "feat: complete snowball converter feature"
```
