# 滴水石锥加速生长 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过定期对符合条件的钟乳石尖触发 `Block#randomTick()`,将滴水石锥生长速度提升约 100 倍(期望约 44 秒/格),同时保持原版生长规则不变。

**Architecture:** 新增 `DripstoneGrowthListener`,主线程 BukkitRunnable 每游戏刻运行一次,轮询已加载区块(每 tick 限扫 `chunks-per-tick` 个,游标跨 tick 保持),找到朝下的 `POINTED_DRIPSTONE` 尖端后调用 `randomTick()` × `random-ticks-per-pass` 次。生长条件判断完全由原版逻辑负责,插件不重复实现。

**Tech Stack:** Paper API 26.1.2(compileOnly)、Java 25、无其他依赖。

**设计文档:** `docs/superpowers/specs/2026-08-07-dripstone-growth-design.md`

## Global Constraints

- Paper API 26.1.2 (compileOnly),禁止新增任何依赖。
- 所有代码在主线程运行(BukkitRunnable,禁止 async)。
- 遵循现有 listener 模式:构造器接收 `StarMSkyblockGamePlay` 实例、读取 config 自己的段落、中文注释。
- 只对"朝下的尖端"(`thickness=tip` 且 `verticalDirection=DOWN`)触发随机刻;不重写原版生长逻辑。
- 只对已加载区块生效。
- 无测试框架(项目无 test 目录),验证方式为 `./gradlew build` 编译 + `./gradlew runServer` 手动验证。
- config 新增 `dripstone-growth` 段落,结构见 Task 1。

---

### Task 1: config.yml 追加配置段

**Files:**
- Modify: `src/main/resources/config.yml`(追加到文件末尾)

**Interfaces:**
- Consumes: 无
- Produces: `dripstone-growth.enabled`(boolean)、`dripstone-growth.random-ticks-per-pass`(int)、`dripstone-growth.chunks-per-tick`(int)、`dripstone-growth.worlds`(StringList) —— Task 2 读取这些路径

- [ ] **Step 1: 在 config.yml 末尾追加配置段**

在 `src/main/resources/config.yml` 文件末尾(现有 `anvil-high-level-enchant` 段之后)追加:

```yaml
# 滴水石锥加速生长(定期对符合条件的钟乳石尖触发随机刻,加速生长/炼药锅填充/泥巴变黏土)
dripstone-growth:
  # 是否启用该功能
  enabled: true
  # 每轮完整扫描对每个钟乳石尖触发的随机刻次数(原版每随机刻 1.138% 概率生长一格,期望 100 分钟/格)
  # 默认 10 次:一轮完整扫描(约 5 秒,视已加载区块数量而定)内每秒约触发 2 次随机刻 → 约 44 秒/格(约原版 100 倍)
  random-ticks-per-pass: 10
  # 每个游戏刻最多扫描的区块数(控制主线程开销;0=不限制,可能造成卡顿)
  chunks-per-tick: 1
  # 生效的世界名称列表(留空=所有世界)
  worlds: []
```

- [ ] **Step 2: 提交**

```bash
git add src/main/resources/config.yml
git commit -m "feat: add dripstone-growth config section"
```

---

### Task 2: 创建 DripstoneGrowthListener

**Files:**
- Create: `src/main/java/team/starm/starMSkyblockGamePlay/listener/DripstoneGrowthListener.java`

**Interfaces:**
- Consumes: `StarMSkyblockGamePlay` 插件实例(构造器参数)、config 的 `dripstone-growth.*` 路径(Task 1 产出)
- Produces:
  - `DripstoneGrowthListener(StarMSkyblockGamePlay plugin)` 构造器
  - `void startGrowthTask()` —— 启动每游戏刻运行一次的 BukkitRunnable
  - `void stopGrowthTask()` —— 取消任务(Task 3 的 onDisable 调用)

- [ ] **Step 1: 创建文件,写入完整实现**

```java
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
```

- [ ] **Step 2: 编译验证**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL(此时主类尚未引用该类,应可独立编译)

- [ ] **Step 3: 提交**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/listener/DripstoneGrowthListener.java
git commit -m "feat: add DripstoneGrowthListener for accelerated dripstone growth"
```

---

### Task 3: 主类接线(onEnable 注册启动,onDisable 取消)

**Files:**
- Modify: `src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java`

**Interfaces:**
- Consumes: Task 2 产出的 `DripstoneGrowthListener` 及其 `startGrowthTask()` / `stopGrowthTask()`
- Produces: 无(该功能无对外接口)

- [ ] **Step 1: 添加 import**

在现有 import 区域(`import team.starm.starMSkyblockGamePlay.listener.EndPortalGeneratorListener;` 之前)添加:

```java
import team.starm.starMSkyblockGamePlay.listener.DripstoneGrowthListener;
```

- [ ] **Step 2: 添加字段**

在 `private LanguageManager languageManager;` 之后添加:

```java
private DripstoneGrowthListener dripstoneGrowthListener;
```

- [ ] **Step 3: onEnable 中注册并启动任务**

在 `onEnable` 中 `getServer().getPluginManager().registerEvents(new HighLevelEnchantListener(this), this);` 之后添加:

```java
dripstoneGrowthListener = new DripstoneGrowthListener(this);
getServer().getPluginManager().registerEvents(dripstoneGrowthListener, this);
dripstoneGrowthListener.startGrowthTask();
```

- [ ] **Step 4: onDisable 中取消任务**

将现有空的 `onDisable` 方法替换为:

```java
@Override
public void onDisable() {
    if (dripstoneGrowthListener != null) {
        dripstoneGrowthListener.stopGrowthTask();
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL(确认 `Block#randomTick()` 在 Paper 26.1.2 API 中可用;若编译失败报 `randomTick()` 找不到,则需改用方案 B 并在设计文档中记录偏差)

- [ ] **Step 6: 提交**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java
git commit -m "feat: wire up dripstone growth task in plugin lifecycle"
```

---

### Task 4: 手动服务器验证

**Files:**
- 无(验证任务)

**Interfaces:**
- Consumes: Task 3 完成后的可运行插件

- [ ] **Step 1: 启动开发服务器**

Run: `./gradlew runServer`
Expected: 服务器启动,插件加载无异常日志;`/starmskyblockgameplay reload` 可正常重载

- [ ] **Step 2: 验证生长加速**

在游戏内搭建滴水石农场:放置滴水石块,其上方 1 格放水源方块,滴水石块下方放 1 个滴水石锥(形成长度为 1 的钟乳石,尖端朝下)。
Expected: 约 1 分钟内尖端向下生长 1 格(原版需 100 分钟);钟乳石下方石笋同步生长

- [ ] **Step 3: 验证附带效果**

在钟乳石尖端正下方 10 格内放置炼药锅(上方无遮挡)。
Expected: 炼药锅水位加速上升(原版 45⁄256 概率/随机刻,现每秒约 2 次随机刻)

- [ ] **Step 4: 验证禁用开关**

将 `dripstone-growth.enabled` 改为 `false`,执行 `/starmskyblockgameplay reload`。
Expected: 生长恢复原版速度(无加速)

- [ ] **Step 5: 验证边界情况**

- 将钟乳石建到长度 7 格:不再继续生长(原版上限)
- 移除滴水石块上方水源:停止生长(原版条件判断生效)
- 移除钟乳石尖端下方液体阻挡场景:行为与原版一致
