package team.starm.starMSkyblockGamePlay.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;
import team.starm.starMSkyblockGamePlay.util.EntityTypeSpawnEggMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 刷怪笼刷怪蛋强化功能：对同一个普通刷怪笼反复使用刷怪蛋，每一个蛋都提升一点刷怪效率，
 * 累计达到配置的 max-eggs（默认 30）个时，刷怪效率正好达到原版的 max-multiplier（默认 5）倍。
 *
 * 交互规则：
 * - 只有与刷怪笼中“已有生物”相同的刷怪蛋才会被消耗用于强化；
 * - 已有强化的刷怪笼使用不匹配的刷怪蛋时，先警告“会清空强化”，需玩家再次用同种刷怪蛋右键确认
 *   后才会执行原版替换并把强化清空、还原为原版刷怪笼（避免误操作与免费继承已加满的效率）；
 * - 尚未设定生物的刷怪笼使用第一个蛋：放行原版设定生物。
 *
 * 已喂蛋总数与基准刷怪参数持久化在刷怪笼方块的 PDC 中，随方块 NBT 保存（服务器重启、
 * 精准采集搬运均保留）。
 */
public class SpawnerUpgradeListener implements Listener {

    private static final String CONFIG_PATH = "spawner-upgrade";
    /** 待确认替换的有效时长（毫秒）。 */
    private static final long CONFIRM_TTL_MS = 5000L;

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;

    /** 玩家对待确认的“非对应蛋替换刷怪笼”记录：需再次右键同一刷怪笼、同种蛋确认。 */
    private final Map<UUID, PendingConfirm> pendingConfirms = new HashMap<>();

    private final NamespacedKey fedEggsKey;
    private final NamespacedKey baseMinDelayKey;
    private final NamespacedKey baseMaxDelayKey;
    private final NamespacedKey baseMaxNearbyKey;

    /** 待确认记录：目标刷怪笼位置 + 预期刷怪蛋材质 + 时间戳。 */
    private record PendingConfirm(String world, int x, int y, int z, Material egg, long timestamp) {
        boolean matches(Block block, Material eggType) {
            return this.egg == eggType
                    && this.world.equals(block.getWorld().getName())
                    && this.x == block.getX()
                    && this.y == block.getY()
                    && this.z == block.getZ();
        }
    }

    public SpawnerUpgradeListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();

        this.fedEggsKey = new NamespacedKey(plugin, "spawner_fed_eggs");
        this.baseMinDelayKey = new NamespacedKey(plugin, "spawner_base_min_delay");
        this.baseMaxDelayKey = new NamespacedKey(plugin, "spawner_base_max_delay");
        this.baseMaxNearbyKey = new NamespacedKey(plugin, "spawner_base_max_nearby");
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerEggUse(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getConfig().getBoolean(CONFIG_PATH + ".enabled", true)) return;

        var clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack used = event.getItem();

        // 空手右键：仅当刷怪笼已设定生物时才在动作栏显示强化状态；空刷怪笼不触发任何提示。
        if (used == null || used.getType().isAir()) {
            CreatureSpawner spawner = (CreatureSpawner) clicked.getState();
            if (spawner.getSpawnedType() != null) {
                showStatus(player, spawner);
            }
            return;
        }

        if (!used.getType().name().endsWith("_SPAWN_EGG")) return;

        EquipmentSlot hand = event.getHand();

        CreatureSpawner spawner = (CreatureSpawner) clicked.getState();
        EntityType spawnedType = spawner.getSpawnedType();

        // 只有与刷怪笼已有生物匹配的刷怪蛋才进入喂养（强化）分支。
        if (spawnedType != null && isMatchingEgg(used, spawnedType)) {
            feedEgg(event, player, hand, spawner);
            return;
        }

        // 非匹配刷怪蛋（包括刷怪笼尚未设定生物时的第一个蛋）：放行原版替换/设定生物。
        // 为避免免费继承已加满的效率，替换时把之前的强化清空，还原为原版刷怪笼。
        var pdc = spawner.getPersistentDataContainer();
        int fedEggs = pdc.getOrDefault(fedEggsKey, PersistentDataType.INTEGER, 0);

        if (fedEggs > 0) {
            // 已有强化：先警告会清空强化，需玩家再次右键同一刷怪笼、同种蛋确认后才执行替换。
            PendingConfirm pending = getValidPending(player.getUniqueId(), clicked, used.getType());
            if (pending == null) {
                event.setCancelled(true);
                event.setUseItemInHand(Event.Result.DENY);
                pendingConfirms.put(player.getUniqueId(),
                        new PendingConfirm(clicked.getWorld().getName(), clicked.getX(), clicked.getY(), clicked.getZ(),
                                used.getType(), System.currentTimeMillis()));
                int maxEggs = Math.max(1, plugin.getConfig().getInt(CONFIG_PATH + ".max-eggs", 30));
                player.sendMessage(lang.getComponent("spawner-upgrade.confirm-warning", Map.of(
                        "mob", translatedMobName(used),
                        "eggs", Component.text(fedEggs),
                        "max", Component.text(maxEggs)
                )));
                return;
            }
            // 已确认：放行原版替换。
            pendingConfirms.remove(player.getUniqueId());
        }

        resetUpgrade(spawner);

        // 空刷怪笼（尚未设定生物）使用刷怪蛋仅执行原版设定生物，不显示“替换”提示。
        if (spawnedType != null) {
            player.sendActionBar(lang.getComponent("spawner-upgrade.replaced",
                    Map.of("mob", translatedMobName(used))));
        }
    }

    /**
     * 获取有效（未过期且匹配同一刷怪笼与同种蛋）的待确认记录；不存在则返回 null。
     */
    private PendingConfirm getValidPending(UUID playerId, Block block, Material egg) {
        PendingConfirm pending = pendingConfirms.get(playerId);
        if (pending == null) return null;
        if (System.currentTimeMillis() - pending.timestamp() > CONFIRM_TTL_MS
                || !pending.matches(block, egg)) {
            pendingConfirms.remove(playerId);
            return null;
        }
        return pending;
    }

    /**
     * 喂养一个与刷怪笼生物匹配的刷怪蛋，提升刷怪效率。
     */
    private void feedEgg(PlayerInteractEvent event, Player player, EquipmentSlot hand, CreatureSpawner spawner) {
        // 匹配蛋用于强化，阻断原版“用刷怪蛋重新设置同类型”的行为；同时取消任何待确认的替换。
        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
        pendingConfirms.remove(player.getUniqueId());

        var pdc = spawner.getPersistentDataContainer();
        int fedEggs = pdc.getOrDefault(fedEggsKey, PersistentDataType.INTEGER, 0);

        int maxEggs = Math.max(1, plugin.getConfig().getInt(CONFIG_PATH + ".max-eggs", 30));
        double maxMultiplier = Math.max(1.0, plugin.getConfig().getDouble(CONFIG_PATH + ".max-multiplier", 5.0));

        // 已喂满：不再消耗刷怪蛋。
        if (fedEggs >= maxEggs) {
            player.sendActionBar(lang.getComponent("spawner-upgrade.max-reached", Map.of(
                    "eggs", Component.text(fedEggs),
                    "multiplier", Component.text(formatMultiplier(maxMultiplier))
            )));
            return;
        }

        // 首次喂养（1 个蛋）时记录基准刷怪参数（延迟/附近上限），作为效率计算的基础。
        // 每次刷出数量不取刷怪笼原值，统一按“初始 3 只 + 每 eggs-per-extra-mob 个蛋 +1 只”计算。
        if (fedEggs == 0 && !pdc.has(baseMinDelayKey, PersistentDataType.INTEGER)) {
            pdc.set(baseMinDelayKey, PersistentDataType.INTEGER, spawner.getMinSpawnDelay());
            pdc.set(baseMaxDelayKey, PersistentDataType.INTEGER, spawner.getMaxSpawnDelay());
            pdc.set(baseMaxNearbyKey, PersistentDataType.INTEGER, spawner.getMaxNearbyEntities());
        }

        consumeOne(player, hand);

        fedEggs++;
        pdc.set(fedEggsKey, PersistentDataType.INTEGER, fedEggs);
        applyEfficiency(spawner, pdc, fedEggs, maxEggs, maxMultiplier);
        spawner.update(true, false);

        double multiplier = currentMultiplier(fedEggs, maxEggs, maxMultiplier);
        player.sendActionBar(lang.getComponent("spawner-upgrade.fed", Map.of(
                "eggs", Component.text(fedEggs),
                "max", Component.text(maxEggs),
                "multiplier", Component.text(formatMultiplier(multiplier)),
                "count", Component.text(currentSpawnCount(fedEggs))
        )));
    }

    /**
     * 空手右键刷怪笼：在动作栏显示当前已放入的刷怪蛋数量与效率（不消耗）。
     */
    private void showStatus(Player player, CreatureSpawner spawner) {
        var pdc = spawner.getPersistentDataContainer();
        int fedEggs = pdc.getOrDefault(fedEggsKey, PersistentDataType.INTEGER, 0);

        int maxEggs = Math.max(1, plugin.getConfig().getInt(CONFIG_PATH + ".max-eggs", 30));
        double maxMultiplier = Math.max(1.0, plugin.getConfig().getDouble(CONFIG_PATH + ".max-multiplier", 5.0));

        player.sendActionBar(lang.getComponent("spawner-upgrade.status", Map.of(
                "eggs", Component.text(fedEggs),
                "max", Component.text(maxEggs),
                "multiplier", Component.text(formatMultiplier(currentMultiplier(fedEggs, maxEggs, maxMultiplier))),
                "count", Component.text(currentSpawnCount(fedEggs))
        )));
    }

    /**
     * 统一的初始每次刷出数量（默认 3，可用 base-spawn-count 配置）。
     */
    private int defaultSpawnCount() {
        return Math.max(1, plugin.getConfig().getInt(CONFIG_PATH + ".base-spawn-count", 3));
    }

    /**
     * 统一的刷怪笼附近同类实体上限基准值（默认 24，可用 base-max-nearby-entities 配置）。
     * 0 个蛋时也生效，避免怪物堆在笼旁触发原版附近上限而整轮停产。
     */
    private int defaultMaxNearbyEntities() {
        return Math.max(1, plugin.getConfig().getInt(CONFIG_PATH + ".base-max-nearby-entities", 24));
    }

    /**
     * 当前每次刷出数量 = 统一的初始数量 + 已喂蛋数 ÷ eggs-per-extra-mob（默认每 5 个蛋 +1 只）。
     */
    private int currentSpawnCount(int fedEggs) {
        int eggsPerExtraMob = plugin.getConfig().getInt(CONFIG_PATH + ".eggs-per-extra-mob", 5);
        int base = defaultSpawnCount();
        return eggsPerExtraMob > 0
                ? Math.max(1, base + fedEggs / eggsPerExtraMob)
                : Math.max(1, base);
    }

    /**
     * 清空刷怪笼的强化数据并还原为“统一初始状态”（生物被替换后使用）：
     * 延迟还原为首次喂养时存档的基准值，每次刷出数量统一还原为 base-spawn-count，
     * 附近实体上限还原为配置的 base-max-nearby-entities。
     */
    private void resetUpgrade(CreatureSpawner spawner) {
        var pdc = spawner.getPersistentDataContainer();
        boolean hadBase = pdc.has(baseMinDelayKey, PersistentDataType.INTEGER)
                || pdc.has(baseMaxDelayKey, PersistentDataType.INTEGER)
                || pdc.has(baseMaxNearbyKey, PersistentDataType.INTEGER);

        if (hadBase) {
            int baseMin = pdc.getOrDefault(baseMinDelayKey, PersistentDataType.INTEGER, Math.max(1, spawner.getMinSpawnDelay()));
            int baseMax = Math.max(baseMin, pdc.getOrDefault(baseMaxDelayKey, PersistentDataType.INTEGER, spawner.getMaxSpawnDelay()));

            spawner.setMinSpawnDelay(baseMin);
            spawner.setMaxSpawnDelay(baseMax);
            spawner.setDelay(Math.max(baseMin, Math.min(baseMax, spawner.getDelay())));
        }

        // 无论是否曾强化，每次数量与附近上限都恢复到配置的基准值（0 个蛋时也生效）。
        spawner.setSpawnCount(defaultSpawnCount());
        spawner.setMaxNearbyEntities(defaultMaxNearbyEntities());
        pdc.set(fedEggsKey, PersistentDataType.INTEGER, 0);
        pdc.remove(baseMinDelayKey);
        pdc.remove(baseMaxDelayKey);
        pdc.remove(baseMaxNearbyKey);
        spawner.update(true, false);
    }

    /**
     * 已喂 n 个刷怪蛋时的刷怪效率倍率（原版的多少倍）。
     * 线性增长：1 个蛋 = 原版基准，喂满 maxEggs 个时 = maxMultiplier 倍，之后不再增长。
     */
    private double currentMultiplier(int fedEggs, int maxEggs, double maxMultiplier) {
        int capped = Math.min(fedEggs, maxEggs);
        return 1.0 + (maxMultiplier - 1.0) * capped / maxEggs;
    }

    /**
     * 按当前蛋数重算刷怪参数：
     * - 刷怪 CD（min/max 延迟）除以倍率 → 刷新更频繁；
     * - 附近实体上限大于基准/每次数量/倍率产物（避免硬上限卡死产出）；
     * - 每次刷出的数量：统一的初始数量 + 已喂蛋数 ÷ eggs-per-extra-mob（默认 3 只起步，每 5 个蛋 +1 只）。
     */
    private void applyEfficiency(CreatureSpawner spawner, PersistentDataContainer pdc,
                                 int fedEggs, int maxEggs, double maxMultiplier) {
        int baseMin = pdc.getOrDefault(baseMinDelayKey, PersistentDataType.INTEGER, Math.max(1, spawner.getMinSpawnDelay()));
        int baseMax = pdc.getOrDefault(baseMaxDelayKey, PersistentDataType.INTEGER, Math.max(baseMin, spawner.getMaxSpawnDelay()));
        int baseNearby = Math.max(defaultMaxNearbyEntities(),
                pdc.getOrDefault(baseMaxNearbyKey, PersistentDataType.INTEGER, Math.max(1, spawner.getMaxNearbyEntities())));

        double m = currentMultiplier(fedEggs, maxEggs, maxMultiplier);
        int newCount = currentSpawnCount(fedEggs);

        int newMin = Math.max(1, (int) Math.round(baseMin / m));
        int newMax = Math.max(newMin, (int) Math.round(baseMax / m));
        int newNearby = Math.max(baseNearby, Math.max(newCount, (int) Math.round(baseNearby * m)));

        spawner.setSpawnCount(newCount);
        spawner.setMinSpawnDelay(newMin);
        spawner.setMaxSpawnDelay(newMax);
        spawner.setMaxNearbyEntities(newNearby);

        int currentDelay = spawner.getDelay();
        spawner.setDelay(Math.max(newMin, Math.min(newMax, (int) Math.round(currentDelay / m))));
    }

    /**
     * 判断该刷怪蛋是否匹配刷怪笼当前的刷怪类型。
     */
    private boolean isMatchingEgg(ItemStack item, EntityType spawnedType) {
        Material egg = EntityTypeSpawnEggMapper.getSpawnEgg(spawnedType);
        return egg != null && item.getType() == egg;
    }

    /**
     * 获取刷怪蛋对应的生物可翻译名称（未收录时退回材质名）。
     */
    private Component translatedMobName(ItemStack egg) {
        EntityType type = EntityTypeSpawnEggMapper.getEntityTypeByEgg(egg.getType());
        if (type != null) {
            return Component.translatable("entity.minecraft." + type.getKey().getKey());
        }
        return Component.text(egg.getType().name());
    }

    /**
     * 消耗手中的 1 个刷怪蛋。
     */
    private void consumeOne(Player player, EquipmentSlot hand) {
        ItemStack stack = hand == EquipmentSlot.HAND
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        if (stack.getAmount() <= 1) {
            if (hand == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(null);
            } else {
                player.getInventory().setItemInOffHand(null);
            }
        } else {
            stack.setAmount(stack.getAmount() - 1);
            if (hand == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(stack);
            } else {
                player.getInventory().setItemInOffHand(stack);
            }
        }
    }

    /**
     * 将整数倍率格式化为不含小数的字符串（如 2、5），否则保留两位小数（如 1.13）。
     */
    private String formatMultiplier(double multiplier) {
        if (multiplier == Math.floor(multiplier) && !Double.isInfinite(multiplier)) {
            return String.valueOf((int) multiplier);
        }
        return String.format("%.2f", multiplier);
    }
}
