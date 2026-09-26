package team.starm.starMSkyblockGamePlay.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.world.WeatheringCopperState;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 水中快速氧化：当铜方块及其变种、铜傀儡在水中时，按“原版随机刻语义”加速其氧化。
 *
 * 背景：本版本（Copper Age 起）原版铜方块的氧化依赖随机刻，水/雨不再加速氧化；
 * 铜傀儡则按游戏刻计时（每阶段约 7~8 小时），在水中也毫无加速。
 *
 * 实现（与原版“每随机刻有概率进入预氧化”同语义，仅把概率换为可配置的直接晋级概率）：
 * - 该 Paper 版本不提供随机刻事件 API，故按原版自然随机刻的**统计频率**模拟“随机刻选中”：
 *   每方块平均每 68.27 秒（1365.3 游戏刻）收到一次自然随机刻 → 本插件每 tick 以 1/1365.3
 *   的概率对每个湿铜方块掷“被随机刻选中”骰。
 * - 被“随机刻”选中后：再以概率 advance-chance-per-tick（默认 14.2%）**直接氧化到下一阶段**
 *   （跳过原版预氧化积累，材质沿 NEXT_OXIDATION 链 setType 推进）。
 * - 铜傀儡（实体没有随机刻）：同一套“模拟随机刻”抽取 —— 每 tick 以 1/1365.3 概率被选中，
 *   再以同样的 advance-chance-per-tick 概率直接氧化一阶段
 *   （{@code CopperGolem#setWeatheringState} 推进，UNAFFECTED → EXPOSED → WEATHERED → OXIDIZED）。
 * - 默认 14.2% ≈ 平均约 7 次随机刻/阶段；按每方块平均每 68.27 秒一次随机刻估算，
 *   方块与傀儡均约 **8 分钟氧化一阶段**。
 * - 上蜡的方块/铜傀儡不会氧化；已完全氧化的不再有下一阶段。
 *
 * 性能说明：湿铜由“随机刻选中”事件驱动（每 tick 每块仅一次微概率判定，选中后才做湿判定）；
 * 索引通过放置/破块/水流事件增量维护，启动全量补建 + 周期性纠偏，避免持续全量扫描；
 * 铜傀儡索引由加入/离开世界事件维护（启动补全一次），无全实体遍历。
 */
public class CopperOxidationListener implements Listener {

    private static final String CONFIG_PATH = "copper-oxidation";

    /** 原版每方块平均收到一次自然随机刻间隔的估计（wiki：平均每 68.27 秒一次 = 1365.3 游戏刻）。 */
    private static final double NATURAL_RANDOM_TICK_INTERVAL_TICKS = 1365.3;

    /** 每游戏刻被“随机刻”选中的概率 = 1/1365.3（≈0.0732%）。 */
    private static final double RANDOM_TICK_CHANCE_PER_TICK = 1.0 / NATURAL_RANDOM_TICK_INTERVAL_TICKS;

    /** advance-chance-per-tick 默认值（%）：1/0.142 ≈ 7 次随机刻 × 68.27 秒 ≈ 8 分钟/阶段。 */
    private static final double DEFAULT_ADVANCE_CHANCE = 14.2;

    /** 全量纠偏扫描间隔（固定 10 分钟；0 语义由无索引的架构不复存在，此处固定启用）。 */
    private static final long RESCAN_INTERVAL_TICKS = 10 * 60L * 20L;

    /** 索引快速剔除非合格方块：约 10 秒一次（200 tick）。 */
    private static final long PRUNE_INTERVAL_TICKS = 200;

    /** 全量扫描每 tick 处理的区块数（固定 1）。 */
    private static final int SCAN_CHUNKS_PER_TICK = 1;

    /** 6 个轴向相邻方块，用于判定“邻接水”。 */
    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.SOUTH,
            BlockFace.EAST, BlockFace.WEST
    };

    /** 可氧化铜方块家族的完整氧化链（未上蜡，按氧化顺序排列）。 */
    private static final List<List<String>> COPPER_CHAINS = List.of(
            List.of("COPPER_BLOCK", "EXPOSED_COPPER", "WEATHERED_COPPER", "OXIDIZED_COPPER"),
            List.of("CUT_COPPER", "EXPOSED_CUT_COPPER", "WEATHERED_CUT_COPPER", "OXIDIZED_CUT_COPPER"),
            List.of("CUT_COPPER_SLAB", "EXPOSED_CUT_COPPER_SLAB", "WEATHERED_CUT_COPPER_SLAB", "OXIDIZED_CUT_COPPER_SLAB"),
            List.of("CUT_COPPER_STAIRS", "EXPOSED_CUT_COPPER_STAIRS", "WEATHERED_CUT_COPPER_STAIRS", "OXIDIZED_CUT_COPPER_STAIRS"),
            List.of("CHISELED_COPPER", "EXPOSED_CHISELED_COPPER", "WEATHERED_CHISELED_COPPER", "OXIDIZED_CHISELED_COPPER"),
            List.of("COPPER_BULB", "EXPOSED_COPPER_BULB", "WEATHERED_COPPER_BULB", "OXIDIZED_COPPER_BULB"),
            List.of("COPPER_DOOR", "EXPOSED_COPPER_DOOR", "WEATHERED_COPPER_DOOR", "OXIDIZED_COPPER_DOOR"),
            List.of("COPPER_TRAPDOOR", "EXPOSED_COPPER_TRAPDOOR", "WEATHERED_COPPER_TRAPDOOR", "OXIDIZED_COPPER_TRAPDOOR"),
            List.of("COPPER_GRATE", "EXPOSED_COPPER_GRATE", "WEATHERED_COPPER_GRATE", "OXIDIZED_COPPER_GRATE"),
            List.of("COPPER_BARS", "EXPOSED_COPPER_BARS", "WEATHERED_COPPER_BARS", "OXIDIZED_COPPER_BARS"),
            List.of("COPPER_CHAIN", "EXPOSED_COPPER_CHAIN", "WEATHERED_COPPER_CHAIN", "OXIDIZED_COPPER_CHAIN"),
            List.of("COPPER_LANTERN", "EXPOSED_COPPER_LANTERN", "WEATHERED_COPPER_LANTERN", "OXIDIZED_COPPER_LANTERN"),
            List.of("COPPER_CHEST", "EXPOSED_COPPER_CHEST", "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST"),
            List.of("COPPER_GOLEM_STATUE", "EXPOSED_COPPER_GOLEM_STATUE", "WEATHERED_COPPER_GOLEM_STATUE", "OXIDIZED_COPPER_GOLEM_STATUE"),
            List.of("LIGHTNING_ROD", "EXPOSED_LIGHTNING_ROD", "WEATHERED_LIGHTNING_ROD", "OXIDIZED_LIGHTNING_ROD")
    );

    /** 材质 → 下一氧化阶段材质 的映射（上蜡、已满氧化的材质不在映射中）。 */
    private static final Map<Material, Material> NEXT_OXIDATION = buildNextOxidationMap();

    private static Map<Material, Material> buildNextOxidationMap() {
        Map<Material, Material> map = new HashMap<>();
        for (List<String> chain : COPPER_CHAINS) {
            for (int i = 0; i + 1 < chain.size(); i++) {
                Material cur = Material.getMaterial(chain.get(i));
                Material next = Material.getMaterial(chain.get(i + 1));
                if (cur != null && next != null) {
                    map.put(cur, next);
                }
            }
        }
        return Map.copyOf(map);
    }

    private final StarMSkyblockGamePlay plugin;

    /** 当前处于“湿”状态、可继续氧化的铜方块索引。 */
    private final Set<Block> indexedCopper = new HashSet<>();

    /** 当前目标世界中未上蜡/未满氧化的铜傀儡索引（事件驱动维护，启动时补全一次）。 */
    private final Set<CopperGolem> indexedGolems = new HashSet<>();

    /** 全量补建/纠偏扫描队列（区块），每 tick 限量处理。 */
    private final Deque<Chunk> scanQueue = new ArrayDeque<>();

    /** 启动补建是否已排队。 */
    private boolean initialScanQueued = false;

    /** 启动铜傀儡索引补全是否已执行。 */
    private boolean initialGolemScanQueued = false;

    /** 距上次全量扫描经过的游戏刻数。 */
    private long ticksSinceLastScan = 0;

    /** 索引剔除计数器。 */
    private long pruneCounter = 0;

    /** “随机刻”抽取的随机数源。 */
    private final Random random = new Random();

    private BukkitRunnable task;

    public CopperOxidationListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
    }

    /** 启动任务：每游戏刻运行一次。 */
    public void startTask() {
        if (task != null) return;
        task = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        task.runTaskTimer(plugin, 20L, 1L);
    }

    /** 停止任务（插件禁用时调用）。 */
    public void stopTask() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /** 放置方块（含放水）后，刷新放置点及其邻接方块的水中铜索引。 */
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled() || !isTargetWorld(event.getBlockPlaced().getWorld())) return;
        refreshAround(event.getBlockPlaced());
    }

    /** 水流到新位置后，刷新目标及其邻接方块的水中铜索引。 */
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        if (!isEnabled() || !isTargetWorld(event.getBlock().getWorld())) return;
        refreshAround(event.getBlock());
        refreshAround(event.getToBlock());
    }

    /** 破块后移除该方块，并刷新其邻接方块（可能因破掉水方块而变干）。 */
    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled() || !isTargetWorld(event.getBlock().getWorld())) return;
        Block block = event.getBlock();
        indexedCopper.remove(block);
        refreshAround(block);
    }

    /** 铜傀儡加入世界（生成/区块加载回场）→ 登记索引，避免每 tick 全实体遍历。 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityAdd(EntityAddToWorldEvent event) {
        if (!isEnabled() || !isTargetWorld(event.getEntity().getWorld())) return;
        if (event.getEntity().getType() == EntityType.COPPER_GOLEM) {
            indexedGolems.add((CopperGolem) event.getEntity());
        }
    }

    /** 铜傀儡离开世界（卸载/死亡/消失）→ 移出索引。 */
    @EventHandler(ignoreCancelled = true)
    public void onEntityRemove(EntityRemoveFromWorldEvent event) {
        if (event.getEntity().getType() == EntityType.COPPER_GOLEM) {
            indexedGolems.remove(event.getEntity());
        }
    }

    /** 每游戏刻运行：处理扫描队列，并对湿铜方块与铜傀儡做“随机刻”抽取。 */
    private void tick() {
        if (!isEnabled()) return;

        // 处理全量扫描队列（启动补建/周期纠偏），每 tick 限量摊开
        for (int i = 0; i < SCAN_CHUNKS_PER_TICK && !scanQueue.isEmpty(); i++) {
            scanChunk(scanQueue.poll());
        }

        // 启动补建：首次排队一次全量扫描，覆盖插件安装前已存在的铜方块
        if (!initialScanQueued) {
            initialScanQueued = true;
            queueFullScan();
        }

        // 周期性纠偏：重新全量扫描，补全未被放置/水流事件捕获的水中铜方块
        if (++ticksSinceLastScan >= RESCAN_INTERVAL_TICKS) {
            ticksSinceLastScan = 0;
            queueFullScan();
        }

        // 索引快速剔除：让已变干/被磨掉/上蜡/满氧化的方块约 10 秒内停止参与抽取
        if (++pruneCounter % PRUNE_INTERVAL_TICKS == 0) {
            pruneIndex();
        }

        rollCopperTicks();
        rollGolemTicks();
    }

    /** 湿铜方块按原版自然随机刻频率抽取：被“随机刻”选中后再掷晋级骰（默认 ≈ 8 分钟/阶段）。 */
    private void rollCopperTicks() {
        double advanceChance = advanceChance();
        if (advanceChance <= 0) return;

        for (Iterator<Block> it = indexedCopper.iterator(); it.hasNext(); ) {
            Block block = it.next();
            // 每 tick 仅一次微概率判定：是否被“自然随机刻”选中（平均每 68.27 秒一次）
            if (random.nextDouble() >= RANDOM_TICK_CHANCE_PER_TICK) continue;
            // 选中后才做完整校验：失效项（变干/上蜡/满氧化等）在此移除
            if (!isTargetWorld(block.getWorld()) || !isWet(block) || !isOxidizable(block.getType())) {
                it.remove();
                continue;
            }
            // 被选中即掷“晋级”骰：命中直接氧化到下一阶段（沿 NEXT_OXIDATION 链）
            if (random.nextDouble() < advanceChance) {
                Material next = NEXT_OXIDATION.get(block.getType());
                if (next != null) {
                    block.setType(next, false);
                }
            }
        }
    }

    /** 铜傀儡用同一套“模拟随机刻”抽取：与方块自然随机刻同频率、同一晋级概率。 */
    private void rollGolemTicks() {
        double advanceChance = advanceChance();
        if (advanceChance <= 0) return;

        // 启动补全：把插件加载前已存在的铜傀儡登记进索引（此后由 add/remove 事件增量维护）
        if (!initialGolemScanQueued) {
            initialGolemScanQueued = true;
            for (World world : getTargetWorlds()) {
                for (Entity entity : world.getEntities()) {
                    if (entity.getType() == EntityType.COPPER_GOLEM) {
                        indexedGolems.add((CopperGolem) entity);
                    }
                }
            }
        }

        // 每 tick 概率 = 被“模拟随机刻”选中（1/1365.3）× 晋级（advance-chance-per-tick）
        double chancePerTick = advanceChance * RANDOM_TICK_CHANCE_PER_TICK;
        for (Iterator<CopperGolem> it = indexedGolems.iterator(); it.hasNext(); ) {
            CopperGolem golem = it.next();
            if (!golem.isValid() || !isTargetWorld(golem.getWorld())) {
                it.remove();
                continue;
            }
            if (isWaxed(golem) || golem.getWeatheringState() == WeatheringCopperState.OXIDIZED) continue;
            if (golem.isInWater() && random.nextDouble() < chancePerTick) {
                advanceGolem(golem);
            }
        }
    }

    /** 读取配置：每次“随机刻”直接氧化到下一阶段的概率（%，0-100 → 0~1）。 */
    private double advanceChance() {
        return Math.clamp(plugin.getConfig().getDouble(CONFIG_PATH + ".advance-chance-per-tick", DEFAULT_ADVANCE_CHANCE), 0, 100) / 100.0;
    }

    /** 索引快速剔除不再合格的索引项（非目标世界 / 已变干 / 已不可氧化）。 */
    private void pruneIndex() {
        indexedCopper.removeIf(block ->
                !isTargetWorld(block.getWorld())
                        || !isWet(block)
                        || !isOxidizable(block.getType()));
    }

    /** 把当前所有已加载区块排入扫描队列（队列非空时不重复排队）。 */
    private void queueFullScan() {
        if (!scanQueue.isEmpty()) return;
        for (World world : getTargetWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanQueue.add(chunk);
            }
        }
    }

    /** 扫描单个区块，把符合条件的“湿铜方块”加入索引。 */
    private void scanChunk(Chunk chunk) {
        if (!chunk.isLoaded()) return;
        World world = chunk.getWorld();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (isOxidizable(block.getType()) && isWet(block)) {
                        indexedCopper.add(block);
                    }
                }
            }
        }
    }

    /**
     * 刷新索引：把中心方块及其 6 个邻接方块中“可氧化且湿”的铜方块加入索引，其余移出。
     */
    private void refreshAround(Block center) {
        checkBlock(center);
        for (BlockFace face : FACES) {
            checkBlock(center.getRelative(face));
        }
    }

    private void checkBlock(Block block) {
        if (isOxidizable(block.getType()) && isWet(block)) {
            indexedCopper.add(block);
        } else {
            indexedCopper.remove(block);
        }
    }

    /** 给铜傀儡推进一阶段。 */
    private void advanceGolem(CopperGolem golem) {
        WeatheringCopperState next = nextState(golem.getWeatheringState());
        if (next != null) {
            golem.setWeatheringState(next);
        }
    }

    /** 方块是否“湿”：自身含水（waterlogged）或任一轴向邻接方块为水。 */
    private boolean isWet(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Waterlogged waterlogged && waterlogged.isWaterlogged()) {
            return true;
        }
        for (BlockFace face : FACES) {
            if (block.getRelative(face).getType() == Material.WATER) {
                return true;
            }
        }
        return false;
    }

    /** 该材质是否属于“可氧化”的铜方块（未上蜡、未满氧化、属于铜家族）。 */
    private boolean isOxidizable(Material material) {
        return NEXT_OXIDATION.containsKey(material);
    }

    private WeatheringCopperState nextState(WeatheringCopperState state) {
        return switch (state) {
            case UNAFFECTED -> WeatheringCopperState.EXPOSED;
            case EXPOSED -> WeatheringCopperState.WEATHERED;
            case WEATHERED -> WeatheringCopperState.OXIDIZED;
            case OXIDIZED -> null;
        };
    }

    private boolean isWaxed(CopperGolem golem) {
        CopperGolem.Oxidizing oxidizing = golem.getOxidizing();
        return oxidizing instanceof CopperGolem.Oxidizing.Waxed
                || oxidizing == CopperGolem.Oxidizing.waxed();
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean(CONFIG_PATH + ".enabled", true);
    }

    private boolean isTargetWorld(World world) {
        List<String> worldNames = plugin.getConfig().getStringList(CONFIG_PATH + ".worlds");
        return worldNames.isEmpty() || worldNames.contains(world.getName());
    }

    private List<World> getTargetWorlds() {
        List<String> worldNames = plugin.getConfig().getStringList(CONFIG_PATH + ".worlds");
        List<World> worlds = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            if (worldNames.isEmpty() || worldNames.contains(world.getName())) {
                worlds.add(world);
            }
        }
        return worlds;
    }
}