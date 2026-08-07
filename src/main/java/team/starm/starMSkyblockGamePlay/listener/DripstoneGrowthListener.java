package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.PointedDripstone;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.ArrayList;
import java.util.List;

/**
 * 滴水石锥加速生长:定期对已加载区块中符合条件的钟乳石尖触发随机刻,
 * 由原版逻辑完成生长(生长条件、长度上限等均不在此重复实现)。
 */
public class DripstoneGrowthListener implements Listener {

    private final StarMSkyblockGamePlay plugin;

    /** 区块扫描游标(世界索引 / 区块索引),跨游戏刻保持,一轮完成后重置 */
    private int worldIndex = 0;
    private int chunkIndex = 0;

    private BukkitRunnable growthTask;

    public DripstoneGrowthListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
    }

    /** 启动加速任务:每游戏刻运行一次,每次最多扫描 chunks-per-tick 个区块 */
    public void startGrowthTask() {
        if (growthTask != null) return;
        growthTask = new BukkitRunnable() {
            @Override
            public void run() {
                scanNextChunks();
            }
        };
        growthTask.runTaskTimer(plugin, 20L, 1L);
    }

    /** 停止加速任务(插件禁用时调用) */
    public void stopGrowthTask() {
        if (growthTask != null) {
            growthTask.cancel();
            growthTask = null;
        }
    }

    /** 每 tick 从配置重新读取参数,游标轮转地扫描部分区块 */
    private void scanNextChunks() {
        if (!plugin.getConfig().getBoolean("dripstone-growth.enabled", true)) return;

        List<World> worlds = getTargetWorlds();
        if (worlds.isEmpty()) return;

        int chunksPerTick = plugin.getConfig().getInt("dripstone-growth.chunks-per-tick", 1);
        int randomTicksPerPass = Math.max(1, plugin.getConfig().getInt("dripstone-growth.random-ticks-per-pass", 10));

        // 游标越界(世界数量变化或上一轮结束)→ 从零开始新一轮
        if (worldIndex >= worlds.size()) {
            worldIndex = 0;
            chunkIndex = 0;
        }

        // chunksPerTick == 0 表示不限量,扫描完一轮为止
        for (int scanned = 0; chunksPerTick == 0 || scanned < chunksPerTick; scanned++) {
            if (worldIndex >= worlds.size()) {
                worldIndex = 0;
                chunkIndex = 0;
                break;
            }
            World world = worlds.get(worldIndex);
            Chunk[] chunks = world.getLoadedChunks();
            if (chunkIndex >= chunks.length) {
                worldIndex++;
                chunkIndex = 0;
                continue;
            }
            scanChunk(chunks[chunkIndex++], world, randomTicksPerPass);
        }
    }

    /** 按配置的 worlds 过滤服务器世界,留空表示全部 */
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

    /** 扫描单个区块,对每个朝下的滴水石锥尖端触发 randomTicksPerPass 次随机刻 */
    private void scanChunk(Chunk chunk, World world, int randomTicksPerPass) {
        if (!chunk.isLoaded()) return;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = world.getMinHeight(); y < world.getMaxHeight(); y++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (block.getType() != Material.POINTED_DRIPSTONE) continue;
                    if (!(block.getBlockData() instanceof PointedDripstone dripstone)) continue;
                    if (dripstone.getThickness() != PointedDripstone.Thickness.TIP) continue;
                    if (dripstone.getVerticalDirection() != BlockFace.DOWN) continue;
                    for (int i = 0; i < randomTicksPerPass; i++) {
                        block.randomTick();
                    }
                }
            }
        }
    }
}
