# StarMSkyblockGamePlay

一个基于 [Paper](https://papermc.io/) 的空岛服务器玩法扩展插件，集成了试炼刷怪笼冷却缩减、宝库黑名单移除、精准采集完整方块、雪球捕捉生物、末地传送门生成器、幽匿尖啸体强化、铁砧超等级附魔书、滴水石锥加速生长、刷怪笼刷怪蛋升级等功能。

- **作者**: [BrokenDream_Star](https://github.com/BrokenDreamStar)、DeepSeek
- **网站**: https://starm.team/

## 功能特性

- 🧪 **试炼刷怪笼冷却缩减**
  - 右键试炼刷怪笼消耗 1 个铜块，减少其冷却时间（减少量可在配置中调整）
  - 可禁止使用刷怪蛋替换试炼刷怪笼
- 🔓 **宝库 / 不祥宝库黑名单移除**
  - 右键宝库消耗 1 个铜块，将玩家从宝库奖励黑名单中移除
  - 支持普通宝库与不祥宝库，各自独立配置每日次数上限与重置时间
  - 数据持久化保存，每日自动重置（默认 04:00）
- 💎 **精准采集完整方块**
  - 使用精准采集工具破坏时，完整保留方块的全部 NBT 数据（如刷怪笼的刷怪类型）
  - 支持刷怪笼、试炼刷怪笼、宝库、紫水晶母岩等可配置方块列表
- ❄️ **雪球转换（精灵球）**
  - 右键投掷精灵球击中生物时，有概率将其捕捉为对应刷怪蛋
  - 支持白名单 / 黑名单模式，可按生物类型单独设置转换概率
  - 未命中生物或命中无法捕捉的生物时自动返还精灵球（可配置）
- ⚡ **闪电转化**
  - 闪电命中守卫者时将其转化为远古守卫者
- 🌀 **末地传送门生成器**
  - 使用带有特殊 NBT 的末影之眼右键点击方块，即可生成末地传送门
- 📢 **回响碎片强化尖啸体**
  - 右键幽匿尖啸体消耗 1 个回响碎片，使其可以正常召唤监守者
- 📖 **铁砧超等级附魔书**
  - 允许在铁砧上应用超过原版最高等级的附魔书（如效率 X）
  - 铁砧"过于昂贵"的经验花费上限可在配置中调整
- 💧 **滴水石锥加速生长**
  - 对符合条件的钟乳石结构定期触发随机刻，生长速度提升约 100 倍（约 44 秒/格）
  - 顺带加速炼药锅填充与泥巴 → 黏土转化，保持原版生长规则不变
- 🥚 **刷怪笼刷怪蛋强化**
  - 对同一个普通刷怪笼反复使用**与刷怪笼已有生物匹配的刷怪蛋**，每个蛋提升一点刷怪效率
  - 效率提升 = **缩短刷怪 CD**（喂满 30 个 → CD 缩短为原版的 1/5）+ **增加每次刷出数量**（无论什么生物初始都统一为 3 只，之后每 5 个蛋 +1 只）
  - 空手右键刷怪笼、或用匹配刷怪蛋喂养时，都会在**动作栏**显示已放入的刷怪蛋数量、每次数量与当前效率
  - 对已强化的刷怪笼使用不匹配的刷怪蛋时，先警告会清空强化（二次右键确认后才替换），避免误操作毁掉强化
  - 替换生物时强化重置为原版（避免免费继承效率）
  - 已喂蛋数随方块 NBT 持久化（服务器重启、精准采集搬运均保留）

## 环境要求

| 依赖 | 版本 |
| --- | --- |
| 服务端 | Paper 26.1.2+ |
| Java | 25+ |

## 命令与权限

| 命令 | 描述 | 权限 |
| --- | --- | --- |
| `/starmskyblockgameplay reload` | 重载配置与消息 | `starmskyblockgameplay.admin`（默认 OP） |
| `/starmskyblockgameplay givesnowball <玩家> [数量] [概率]` | 给予精灵球（可指定捕捉概率） | `starmskyblockgameplay.admin` |
| `/starmskyblockgameplay giveportal <玩家> [数量]` | 给予末地传送门生成器 | `starmskyblockgameplay.admin` |

## 构建与安装

```bash
# 构建
./gradlew build

# 产物位于 build/libs/StarMSkyblockGamePlay-<version>.jar
# 将其放入服务器的 plugins 目录并重启（或使用 PlugMan 等热加载插件）
```

## 配置

所有功能均可在 `config.yml` 中通过 `enabled` 开关控制，主要配置项：

- `trial-spawner.cooldown-reduction-ticks` — 每个铜块减少的冷却 tick 数（默认 3600 = 3 分钟）
- `vault.blacklist-removal-limit` / `ominous-vault.blacklist-removal-limit` — 每日黑名单移除次数上限（`0` 表示不限）
- `vault.reset-time` / `ominous-vault.reset-time` — 每日重置时间（`HH:mm` 格式）
- `silk-touch-collectibles.blocks` — 精准采集可完整采集的方块列表
- `snowball-converter.global-chance` / `mob-chances` — 雪球转换全局概率与按生物概率
- `snowball-converter.use-whitelist` / `whitelist` / `blacklist` — 雪球转换生物过滤
- `snowball-converter.refund-on-miss` — 未命中或命中无法捕捉生物时返还精灵球
- `end-portal-generator.item-name` / `lore` — 传送门生成器物品显示
- `anvil-high-level-enchant.max-repair-cost` — 铁砧"过于昂贵"经验上限（原版为 40）
- `dripstone-growth.random-ticks-per-pass` / `check-interval-ticks` — 滴水石锥生长速度调节
- `dripstone-growth.rescan-interval-minutes` / `scan-chunks-per-tick` — 结构索引纠偏扫描
- `dripstone-growth.worlds` — 滴水石锥加速生效的世界列表（留空=所有世界）
- `spawner-upgrade.max-eggs` / `max-multiplier` / `base-spawn-count` / `eggs-per-extra-mob` — 刷怪笼刷怪蛋强化达到最大效率所需蛋数、CD 缩短倍率、统一的初始每次数量与每加 1 只所需蛋数；只有与已有生物匹配的刷怪蛋能强化，否则放行原版替换生物并重置强化

玩家可见的提示消息可在 `messages.yml` 中自定义。
