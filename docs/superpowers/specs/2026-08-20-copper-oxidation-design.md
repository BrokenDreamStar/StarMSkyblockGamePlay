# 水中快速氧化 — 设计文档

日期:2026-08-20
状态:已批准

## 背景与目标

本版本（Copper Age 起）原版铜方块氧化只依赖随机刻，**水/雨不再加速氧化**（与 1.17+ 旧版不同）；
铜傀儡按游戏刻计时（每阶段约 7~8 小时），在水中也毫无加速。空岛服务器玩家希望将铜方块/铜傀儡
长时间放在水中时**显著加速氧化**，用于快速产出氧化铜（青铜/锈蚀装饰）与体验铜的锈蚀玩法。

目标：

- 铜方块及其变种（铜块、切制铜、台阶/楼梯、活板门、门、栅栏、锁链、铜灯、铜栏杆、
  铜储物箱、避雷针、铜傀儡雕像等）处于“湿”状态时，加速其氧化。
- 未被上蜡的铜傀儡在水中时，同样加速氧化。
- **上蜡的方块/铜傀儡不加速**；**已完全氧化**的不再推进。
- 速度可配：铜方块/变种默认**平均约 10 分钟氧化一阶段**，铜傀儡默认**平均约 15 分钟**。

## 方案设计

### “湿”判定

- 方块：自身 `waterlogged = true`，或 6 个轴向邻接方块之一是 `WATER`（水源或水流）。
  （与本版本原版的“邻接水”判定一致。）
- 铜傀儡：`Entity#isInWater()` 为 true。

### 氧化推进方式

本版本 Paper API 不再为铜方块暴露 `Oxidizable` 块数据接口，氧化阶段由**不同 Material** 表达
（`COPPER_X` → `EXPOSED_COPPER_X` → `WEATHERED_COPPER_X` → `OXIDIZED_COPPER_X`，含
`WAXED_` 前缀的上蜡变体）。

- **铜方块：调用真实 `Block#randomTick()`**（如同滴水石锥对尖端调用 randomTick），
  由原版“预氧化 + 邻接分组”逻辑驱动晋级，保留原版规则（分组减速、只推进 1 阶段等）。
  在类加载时按 **显式氧化链**（每铜家族 4 阶段材质名写死）构建静态判定映射
  `Map<Material, Material> NEXT_OXIDATION`，仅用于 `isOxidizable()` 过滤：
  上蜡材质、`OXIDIZED_` 终态材质不在映射中，天然跳过，不会把湿铜加速套用到它们头上。
  - 用显式链而非“前缀拼接”的原因：普通铜块命名有特例（`EXPOSED_COPPER_BLOCK` 实际叫
    `EXPOSED_COPPER`，去掉了 `_BLOCK` 后缀），而切制铜 / 避雷针等又是前缀拼接，字符串解析不可靠。
- **铜傀儡（实体无 randomTick()）**：用
  `CopperGolem#getWeatheringState()/setWeatheringState(WeatheringCopperState)` 按
  `UNAFFECTED → EXPOSED → WEATHERED → OXIDIZED` 推进；上蜡判定
  `getOxidizing() instanceof CopperGolem.Oxidizing.Waxed`（或与 `Oxidizing.waxed()` 恒等比较）。

### 随机刻节奏（可配）

- 铜方块：每隔 `check-interval-ticks` 触发一轮，每轮对每个“湿”且可氧化的铜方块调用
  `randomTick()` 共 `random-ticks-per-pass` 次。**真实随机刻**的预氧化概率为 64/1125，
  晋级再乘邻接系数（孤立未氧化 m=0.75，氧化中 m=1）→ 平均约 **19.5 个随机刻/阶段**。
  默认 `check-interval-ticks=600`（30 秒）+ `random-ticks-per-pass=1` →
  1 随机刻/30 秒 ≈ 19.5×30s ≈ **9.75 分钟/阶段 ≈ 10 分钟**（快速调大 per-pass 即可）。
- 铜傀儡：每轮以概率 `p = (checkIntervalTicks/20) / golem-random-tick-interval-seconds`
  抽取，被选中即 `+1 阶段`。默认 600/20÷900 = 1/30 → 30 轮×30s = **900 秒 = 15 分钟/阶段**。

| 配置项 | 默认 | 含义 |
| --- | --- | --- |
| `check-interval-ticks` | 600（30 秒） | 每轮触发间隔（对湿铜调 randomTick / 对铜傀儡抽取） |
| `random-ticks-per-pass` | 1 | 每轮对每个湿铜方块调用 randomTick() 的次数（越大越快） |
| `golem-random-tick-interval-seconds` | 900（15 分钟） | 水中铜傀儡平均被选中一次所需秒数（选中即氧化一阶段） |
| `rescan-interval-minutes` | 10 | 全量纠偏/补建扫描间隔（0=关闭） |
| `scan-chunks-per-tick` | 1 | 全量扫描每 tick 处理的区块数 |
| `worlds` | [] | 生效世界列表（空=全部） |

### 索引维护（与滴水石锥加速一致的事件驱动架构）

- 事件：`BlockPlaceEvent` / `BlockFromToEvent`（水流）/ `BlockBreakEvent` → 刷新放置点/流向/
  破块点及其 6 邻接方块，若为“可氧化且湿”的铜方块则加入索引 `Set<Block>`，否则移除。
- 启动时全量补建一次加载区块中的“湿铜”索引；周期性（`rescan-interval-minutes`）全量纠偏，
  补全未被事件捕获（如世界编辑/高频水流）的情况。全量扫描按 `scan-chunks-per-tick` 限量摊开。
- 处理轮校验索引项：非湿 / 不可氧化（上蜡、满氧化、被磨掉）即时移出，闭环自愈。

### 边界与注意

- 铜傀儡在满氧化后由原版逻辑自动转为雕像（需在空气中）；本功能不干预，只推进到 OXIDIZED。
- 调用 `randomTick()` 时随机刻频率为每 30 秒 1 次（默认），远低于原版每方块约 68 秒 1 次的
  随机刻，且只作用于“湿铜”，对主线程影响可忽略；走的是原版氧化逻辑，无材质替换、无物理级联。
- 铜傀儡遍历使用 `World#getEntities()` 过滤 `EntityType.COPPER_GOLEM`，忽略未加载区块外的实体。

## 配置示例

```yaml
copper-oxidation:
  enabled: true
  worlds: []
  check-interval-ticks: 600
  random-ticks-per-pass: 1
  golem-random-tick-interval-seconds: 900
  rescan-interval-minutes: 10
  scan-chunks-per-tick: 1
```
