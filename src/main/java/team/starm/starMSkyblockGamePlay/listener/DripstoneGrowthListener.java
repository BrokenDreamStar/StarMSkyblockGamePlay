package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 滴水石锥加速生长:通过事件索引维护符合条件的钟乳石尖列表,
 * 定时对索引中的尖端触发随机刻,由原版逻辑完成生长(生长条件、长度上限等均不在此重复实现)。
 *
 * 性能说明:索引由放置事件增量维护,启动时全量补建一次并周期性纠偏,
 * 避免了对已加载区块的持续全量扫描(逐方块读取是原实现的主线程性能瓶颈)。
 */
public class DripstoneGrowthListener implements Listener {

    /** 一个钟乳石结构的最大链长度(滴水石块下方最多 7 格) */
    private static final int MAX_STRUCTURE_DEPTH = 8;

    private final StarMSkyblockGamePlay plugin;

    /** 当前有效的钟乳石尖端位置索引 */
    private final Set<Block> indexedTips = new HashSet<>();

    /** 全量补建/纠偏扫描队列(区块),每 tick 限量处理 */
    private final Deque<Chunk> scanQueue = new ArrayDeque<>();

    /** 启动补建是否已排队 */
    private boolean initialScanQueued = false;

    /** 距上次全量扫描经过的游戏刻数 */
    private long ticksSinceLastScan = 0;

    /** 触发轮计数器 */
    private long tickCounter = 0;

    private BukkitRunnable growthTask;

    public DripstoneGrowthListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
    }

    /** 启动任务:每游戏刻运行一次 */
    public void startGrowthTask() {
        if (growthTask != null) return;
        growthTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        };
        growthTask.runTaskTimer(plugin, 20L, 1L);
    }

    /** 停止任务(插件禁用时调用) */
    public void stopGrowthTask() {
        if (growthTask != null) {
            growthTask.cancel();
            growthTask = null;
        }
    }

    /** 放置滴水石锥/滴水石块/水源时,检查是否构成有效结构并索引其尖端 */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled()) return;
        Block placed = event.getBlockPlaced();
        if (!isTargetWorld(placed.getWorld())) return;
        Material type = placed.getType();
        if (type != Material.POINTED_DRIPSTONE && type != Material.DRIPSTONE_BLOCK && type != Material.WATER) {
            return;
        }
        tryIndexAround(placed);
    }

    /** 每游戏刻运行:处理扫描队列,并按间隔触发随机刻 */
    private void tick() {
        if (!isEnabled()) return;

        int scanChunksPerTick = Math.max(1, plugin.getConfig().getInt("dripstone-growth.scan-chunks-per-tick", 1));
        int rescanIntervalMinutes = plugin.getConfig().getInt("dripstone-growth.rescan-interval-minutes", 10);
        int checkIntervalTicks = Math.max(1, plugin.getConfig().getInt("dripstone-growth.check-interval-ticks", 100));
        int randomTicksPerPass = Math.clamp(plugin.getConfig().getInt("dripstone-growth.random-ticks-per-pass", 10), 1, 1000);

        // 处理全量扫描队列(启动补建/周期纠偏),每 tick 限量摊开
        for (int i = 0; i < scanChunksPerTick && !scanQueue.isEmpty(); i++) {
            scanChunk(scanQueue.poll());
        }

        // 启动补建:首次排队一次全量扫描,覆盖插件安装前已存在的结构
        if (!initialScanQueued) {
            initialScanQueued = true;
            queueFullScan();
        }

        // 周期性纠偏:重新全量扫描,补全未被放置事件捕获的结构
        if (rescanIntervalMinutes > 0 && ++ticksSinceLastScan >= rescanIntervalMinutes * 60L * 20L) {
            ticksSinceLastScan = 0;
            queueFullScan();
        }

        // 每 checkIntervalTicks 触发一轮随机刻
        if (++tickCounter % checkIntervalTicks != 0) return;
        triggerRound(randomTicksPerPass);
    }

    /** 把当前所有已加载区块排入扫描队列(队列非空时不重复排队) */
    private void queueFullScan() {
        if (!scanQueue.isEmpty()) return;
        for (World world : getTargetWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanQueue.add(chunk);
            }
        }
    }

    /** 扫描单个区块,把符合条件的朝下滴水石锥尖加入索引 */
    private void scanChunk(Chunk chunk) {
        if (!chunk.isLoaded()) return;
        World world = chunk.getWorld();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.POINTED_DRIPSTONE) continue;
                    if (!isDownwardTip(block)) continue;
                    if (hasValidWaterSource(block)) {
                        indexedTips.add(block);
                    }
                }
            }
        }
    }

    /** 对索引中的每个有效尖端触发一轮随机刻,并维护索引 */
    private void triggerRound(int randomTicksPerPass) {
        if (indexedTips.isEmpty()) return;
        List<Block> toRemove = null;
        List<Block> toAdd = null;
        for (Block tip : indexedTips) {
            if (!isValidStructure(tip)) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(tip);
                continue;
            }
            for (int i = 0; i < randomTicksPerPass; i++) {
                tip.randomTick();
            }
            // 若已生长,尖端下移,索引跟随
            Block below = tip.getRelative(BlockFace.DOWN);
            if (isDownwardTip(below)) {
                if (toRemove == null) toRemove = new ArrayList<>();
                toRemove.add(tip);
                if (toAdd == null) toAdd = new ArrayList<>();
                toAdd.add(below);
            }
        }
        if (toRemove != null) {
            indexedTips.removeAll(toRemove);
            if (toAdd != null) indexedTips.addAll(toAdd);
        }
    }

    /** 放置事件后尝试在放置点附近建立结构索引 */
    private void tryIndexAround(Block placed) {
        // 场景 A:放置点是滴水石块,或滴水石块在其正下方(如在水源上放滴水石块/在滴水石块上放水)
        Block dripstoneBlock = null;
        if (placed.getType() == Material.DRIPSTONE_BLOCK) {
            dripstoneBlock = placed;
        } else if (placed.getRelative(BlockFace.DOWN).getType() == Material.DRIPSTONE_BLOCK) {
            dripstoneBlock = placed.getRelative(BlockFace.DOWN);
        }
        if (dripstoneBlock != null) {
            Block tip = findTipBelow(dripstoneBlock);
            if (tip != null && hasValidWaterSource(tip)) {
                indexedTips.add(tip);
            }
            return;
        }
        // 场景 B:放置的是朝下的滴水石锥尖端(新建结构或延长已有结构)
        if (placed.getType() == Material.POINTED_DRIPSTONE && isDownwardTip(placed)) {
            if (hasValidWaterSource(placed)) {
                indexedTips.add(placed);
            }
        }
    }

    /** 从滴水石块向下搜索钟乳石链的尖端(最长 7 格) */
    private Block findTipBelow(Block dripstoneBlock) {
        Block cursor = dripstoneBlock;
        for (int i = 0; i < MAX_STRUCTURE_DEPTH; i++) {
            cursor = cursor.getRelative(BlockFace.DOWN);
            if (cursor.getType() != Material.POINTED_DRIPSTONE) return null;
            if (isDownwardTip(cursor)) return cursor;
        }
        return null;
    }

    /** 检查尖端结构仍有效:仍是朝下尖端,且水源条件满足 */
    private boolean isValidStructure(Block tip) {
        return isDownwardTip(tip) && hasValidWaterSource(tip);
    }

    /** 判断是否为朝下的滴水石锥尖端 */
    private boolean isDownwardTip(Block block) {
        if (block.getType() != Material.POINTED_DRIPSTONE) return false;
        if (!(block.getBlockData() instanceof PointedDripstone dripstone)) return false;
        return dripstone.getThickness() == PointedDripstone.Thickness.TIP
                && dripstone.getVerticalDirection() == BlockFace.DOWN;
    }

    /** 检查钟乳石结构的水源条件:尖端向上(经链条)到滴水石块,其上方为水源方块(非水流) */
    private boolean hasValidWaterSource(Block tip) {
        Block cursor = tip.getRelative(BlockFace.UP);
        int depth = 0;
        while (depth < MAX_STRUCTURE_DEPTH && cursor.getType() == Material.POINTED_DRIPSTONE) {
            cursor = cursor.getRelative(BlockFace.UP);
            depth++;
        }
        if (cursor.getType() != Material.DRIPSTONE_BLOCK) return false;
        Block water = cursor.getRelative(BlockFace.UP);
        if (water.getType() != Material.WATER) return false;
        if (!(water.getBlockData() instanceof Levelled waterData)) return false;
        return waterData.getLevel() == 0;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("dripstone-growth.enabled", true);
    }

    private boolean isTargetWorld(World world) {
        List<String> worldNames = plugin.getConfig().getStringList("dripstone-growth.worlds");
        return worldNames.isEmpty() || worldNames.contains(world.getName());
    }

    private List<World> getTargetWorlds() {
        List<String> worldNames = plugin.getConfig().getStringList("dripstone-growth.worlds");
        List<World> worlds = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            if (worldNames.isEmpty() || worldNames.contains(world.getName())) {
                worlds.add(world);
            }
        }
        return worlds;
    }
}
