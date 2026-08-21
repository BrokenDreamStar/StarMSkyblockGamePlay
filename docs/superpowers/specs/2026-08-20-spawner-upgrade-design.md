# 刷怪笼刷怪蛋强化 — 设计文档

日期:2026-08-20
状态:已批准

## 背景与目标

原版刷怪笼只能被刷怪蛋替换刷怪类型,无法"喂养"强化。空岛服务器玩家希望把同一个刷怪笼培养得越来越强:对同一个(普通)刷怪笼反复使用刷怪蛋,每个蛋提升一点刷怪效率,累计达到配置的蛋数上限时达到原版的配置倍率。

目标(**无等级系统**):

- 允许玩家对同一个刷怪笼**多次**使用刷怪蛋(替代原版"右键即替换类型")。
- **每个刷怪蛋**都提升一点刷怪效率,喂满 `max-eggs` 个时刷怪效率正好达到原版的 `max-multiplier` 倍。
- **可配置**:达到最大效率所需蛋数(`max-eggs`)、最大效率倍率(`max-multiplier`)、是否要求蛋类型匹配(`require-matching-egg`)。

## 方案设计

### 效率模型(线性)

- 已喂 `n` 个刷怪蛋时的刷怪效率倍率为:

  ```
  multiplier(n) = 1 + (maxMultiplier - 1) × min(n, maxEggs) / maxEggs
  ```

- `n = 0`(未喂养)= 原版 1 倍;`n = maxEggs`(喂满)= `maxMultiplier` 倍;之后不再增长。
- 默认配置 `max-eggs: 30`、`max-multiplier: 5`:`1 + 4 × n / 30`,即每个蛋约提升 0.133 倍,30 个蛋达到 5 倍。

### 刷怪效率倍率 = 原版的多少倍

原版刷怪笼的产出速率 ≈ `spawnCount / 平均延迟`(平均延迟 = `(minSpawnDelay + maxSpawnDelay) / 2`),并且受 `maxNearbyEntities`(默认 6)硬上限约束。

本功能同时从"缩短刷怪 CD"与"增加每次刷出数量"两个维度提升效率:

- `minSpawnDelay` / `maxSpawnDelay` 除以倍率 M(至少为 1 tick):刷新更频繁;
- **每次刷出的数量统一计算**,不沿用刷怪笼原有 spawnCount:
  初始 = `base-spawn-count`(默认 **3**,无论什么生物),
  之后每放入 `eggs-per-extra-mob` 个蛋 +1 只,即 `3 + floor(已喂蛋数 / 5)`(喂满 30 个 → 3 + 6 = 9 只/次);
- `maxNearbyEntities` 至少取 `基准上限 × M` 且不小于每次数量,避免 6 只的硬上限卡死产出。

其中倍率 `M = 1 + (maxMultiplier - 1) × 已喂蛋数 / max-eggs` 只作用于刷怪 CD(喂满 30 个时
CD 缩短为原版的 1/5)。延迟与附近上限的基准值取该刷怪笼**第一次被喂养时(1 个蛋)的当前值**存入 PDC,
这样既尊重原版默认值,也兼容其他插件/指令已改过的自定义刷怪笼,且不会逐次累积导致 CD 失控;
但每次刷出数量不取原值,统一按上述公式设置。

### 交互规则

- 事件:`PlayerInteractEvent`(RIGHT_CLICK_BLOCK)点击 `SPAWNER`(Material.SPAWNER)。
- **空手右键**:在**动作栏**显示 `status`(已放入 `{eggs}/{max}` 个刷怪蛋、当前倍率),仅查询、不消耗。
- **只匹配已有生物**:
  - 仅当刷怪笼**已设定生物**且刷怪蛋与该生物相同(如僵尸刷怪笼 + 僵尸刷怪蛋)时,才进入喂养(强化)分支:
    - `setCancelled(true)` + `setUseItemInHand(DENY)`,阻断原版行为;
    - 消耗 1 个蛋,应用当前倍率,在**动作栏**提示 `fed`(已喂蛋数/上限、当前倍率);
    - 已喂满 `max-eggs`:动作栏提示 `max-reached`,不消耗刷怪蛋。
  - **其他一切情况**(刷怪笼尚未设定生物、或刷怪蛋与已有生物不符):
    - 刷怪笼**已有强化**(`fedEggs > 0`)时,采用**二次确认**:首次右键不消耗蛋、不替换,
      取消事件并提示 `confirm-warning`(会清空强化、已放入蛋数),记录待确认状态
      (按玩家 UUID + 刷怪笼位置 + 蛋材质,5 秒有效);
      玩家在有效期内再次右键**同一刷怪笼、同种蛋**才放行原版替换;
    - 确定替换时:不取消事件,由原版把刷怪笼生物替换/设置为刷怪蛋对应生物;
      同时在事件里调用 `resetUpgrade()` 将该刷怪笼的强化清空(已喂蛋数归零、清基准键、还原基准刷怪参数),
      动作栏提示 `replaced`(避免误操作毁掉强化、也避免免费继承已加满的效率)。

### 数据模型(PDC Keys)

以 `NamespacedKey(plugin, ...)` 存于刷怪笼 `CreatureSpawner`(TileState)的 PDC:

| Key | 类型 | 含义 |
| --- | --- | --- |
| `spawner_fed_eggs` | INTEGER | 已喂的刷怪蛋总数 |
| `spawner_base_min_delay` | INTEGER | 首次喂养时的 minSpawnDelay(基准) |
| `spawner_base_max_delay` | INTEGER | 首次喂养时的 maxSpawnDelay(基准) |
| `spawner_base_max_nearby` | INTEGER | 首次喂养时的 maxNearbyEntities(基准) |

> 每次刷出数量不存 PDC:统一按 `base-spawn-count + ⌊已喂蛋数 / eggs-per-extra-mob⌋` 实时计算。

### 精准采集搬运(保留强化进度)

强化数据位于刷怪笼方块自身的 PDC(方块 TileState 的一部分)。配合本插件的 `SilkTouchCollectListener`
(默认开启,且 `silk-touch-collectibles.blocks` 默认包含 `SPAWNER`):

- 破坏时 `blockStateMeta.setBlockState(ts)` 把**完整方块块实体 NBT(含 `spawner_fed_eggs` 与基准参数、
  以及已重算的延迟/数量/上限)**序列化进刷怪笼方块物品。
- 放置时由服务端自动从物品的 BlockStateMeta 还原到新方块,强化进度、已喂蛋数、当前效率原样保留。
- 之后继续喂蛋时会从 PDC 读取已喂蛋数、按存档的基准参数继续计算,不会因为搬运而丢失或重置。

> 依赖:该保证依赖 `silk-touch-collectibles.enabled`(默认 true)且列表包含 `SPAWNER`(默认包含)。
> 若管理员刻意关闭完整采集或从列表移除 SPAWNER,该保证不生效。

## 配置(config.yml 追加)

```yaml
# 刷怪笼刷怪蛋强化功能(对同一个普通刷怪笼反复使用刷怪蛋,每个蛋都提升一点刷怪效率)
# 只有与刷怪笼中已有生物匹配的刷怪蛋才会被消耗用于强化;其他刷怪蛋执行原版替换生物操作,
# 并把该刷怪笼的强化重置为原版(已喂蛋数归零、效率还原 1 倍)
spawner-upgrade:
  enabled: true
  # 达到最大效率所需的刷怪蛋总数(默认 30,达到后不再消耗刷怪蛋)
  max-eggs: 30
  # 喂满 max-eggs 个刷怪蛋时,刷怪 CD 缩短为原版的 1/max-multiplier(默认 5 = 1/5)
  max-multiplier: 5
  # 统一的初始每次刷出数量(无论什么生物,初始都是这个数量)
  base-spawn-count: 3
  # 每放入多少个刷怪蛋,每次刷出的数量增加 1 只(默认 5,即每 5 个蛋 +1 只;0 表示不增加数量)
  eggs-per-extra-mob: 5
```

## 架构

新文件 `listener/SpawnerUpgradeListener.java`,遵循现有 listener 模式:

- 构造器接收 `StarMSkyblockGamePlay` 插件实例,预建 NamespacedKey 常量,读取 config 的 `spawner-upgrade` 段落;
- `StarMSkyblockGamePlay.onEnable` 注册该 listener;
- 无命令、无独立持久化文件(数据存方块 PDC)。

## 边界与错误处理

- 刷怪笼类型未设定(刚放置):任意第一个蛋都走"原版替换/设定生物"分支,只设定生物、不参与喂养(不消耗为强化),随后用匹配蛋才开始强化。
- 类型无对应刷怪蛋(如 `ILLUSIONER`):因无法产出匹配的刷怪蛋,该刷怪笼无法强化,属预期行为。
- `max-eggs` 小于 1:按 1 处理;`max-multiplier` 小于 1:按 1 处理(即不提升效率)。
- 已喂满:继续使用匹配蛋仅提示 `max-reached`,不消耗。
- 替换生物:放行原版替换,同时 `resetUpgrade()` 清空强化数据并还原基准刷怪参数。
- 备份/搬运:数据通过 PDC 随方块 NBT 持久化,精准采集(方块物品)与服务器重启均保留。
- 配置热改:已存有基准数据和蛋数的刷怪笼,下次喂养时按新配置(`max-eggs`/`max-multiplier`)重算生效;未继续喂养的存量刷怪笼保持旧档数据。

## 测试

1. `./gradlew build` — 编译验证(Paper API 26.1.2)。
2. `./gradlew runServer` 手动验证:
   - 放置普通刷怪笼,用对应刷怪蛋喂养,观察每次动作栏 `fed` 提示与刷怪频率逐步提升;
   - 空手右键刷怪笼 → 动作栏显示 `status`(已放入蛋数/上限、当前倍率);
   - 对已强化的刷怪笼用不匹配的刷怪蛋 → 首次提示 `confirm-warning` 且不替换;再次右键同种蛋 → 执行替换并提示 `replaced`,强化被重置为原版;
   - 喂满 30 个 → 提示 `max-reached`,刷怪蛋不再消耗,效率为原版 5 倍;
   - 已强化刷怪笼用精准采集挖掘 → 掉落方块物品 → 放置新位置 → 已喂蛋数与效率保留;
   - 服务器重启后数据保留;
   - `enabled: false` 时行为与原版完全一致。
