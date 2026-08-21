# config.yml 缺失配置项自动补全 — 设计文档

日期:2026-08-07
状态:已批准(用户选择方案 A:镜像 LanguageManager 现有模式;语义为"仅补缺失的键")

## 背景与目标

插件迭代中 config.yml 持续新增配置项(如近期新增 `dripstone-growth`、`anvil-high-level-enchant` 等段落)。服务器上已部署的旧 config.yml 不会自动获得新键:虽然 Bukkit 的 `reloadConfig()` 会把打包的默认配置挂为 defaults,使 `getConfig().getXxx()` 在内存中回退到默认值,但**磁盘上的文件本身不会更新**——管理员打开 config.yml 看不到新选项,且部分按 `getKeys(false)` 迭代或 `contains()` 判断的逻辑取不到默认值。

`LanguageManager.java` 对 messages.yml 已实现同样机制(`setDefaults` + `options().copyDefaults(true)` + save,见 `loadMessages()`),config.yml 缺少同等处理。

目标:启动时与 `/starmskyblockgameplay reload` 时,若旧配置缺失键则补全写入磁盘文件;不删除任何旧键;全新安装的文件(含中文注释)不被重写。

## 方案选择

| 方案 | 思路 | 结论 |
|---|---|---|
| A(选定) | 镜像 LanguageManager 模式:`options().copyDefaults(true)` + `saveConfig()`,并用 `contains` 扫描做门控 | 与代码库既有写法完全一致,改动最小 |
| B | 新建 `util/ConfigUpdater.java` 显式递归合并 | 可记录新增键数量/类型不匹配告警,但多一个维护点;本项目所有 listener 均按路径直接读值,内存补全无必要 |
| C | `config-version` 版本号 + 按版本迁移 | 大型插件模式,对本规模过度设计(合并本身幂等) |

## 架构

全部改动在 `StarMSkyblockGamePlay.java` 内,新增一个私有方法 + 两处调用,无新文件:

- 私有方法 `fillMissingConfigKeys()`:用 `getConfig().getDefaults().getKeys(true)` 扫描,任一键在当前配置中缺失即触发补全。
- `onEnable` 中 `saveDefaultConfig()` 之后调用。
- reload 命令中 `reloadConfig()` 之后调用,补全时在回复消息中追加说明。

## 核心机制

```java
private boolean fillMissingConfigKeys() {
    Configuration defaults = getConfig().getDefaults();
    if (defaults == null) return false;
    // 注意:不能用 contains() 判断缺失 —— 它会回退到默认值,导致永远返回 true。
    // getKeys(true) 只返回配置文件自身存在的键,与默认键集对比才能找出缺失项。
    if (getConfig().getKeys(true).containsAll(defaults.getKeys(true))) {
        return false;
    }
    getConfig().options().copyDefaults(true);
    saveConfig();
    getLogger().info("检测到 config.yml 缺少配置项，已自动补全。");
    return true;
}
```

> 实现注记:初版用 `contains()` 扫描,运行验证发现 Bukkit 的 `contains()` 会回退到挂载的默认值(见 `MemoryConfigurationSection#get` 的 `getDefault` 回退),导致缺失永远检测不到。改为 `getKeys(true)` 键集对比后验证通过。这正是"运行验证捕获设计盲区"的实例。

语义(用户确认"仅补缺失的键"):

- 缺失键从打包默认配置深度复制;已有键(含列表如 `snowball-converter.whitelist`)保持用户原样;用户值永远优先。
- **注释保护**:先扫描,仅当确实缺键才 `saveConfig()` 重写文件。全新安装时文件与默认完全一致 → 不重写 → 中文注释保留;仅升级补键时重写一次,此时注释丢失为可接受代价(与 messages.yml 现有行为一致)。

## 错误处理

- `saveConfig()` 失败时 JavaPlugin 内部已记录 SEVERE 日志,无需额外处理。
- `getDefaults()` 为 null(理论不可能,config.yml 始终打包)时直接返回。

## 验证

1. `./gradlew build` 编译通过。
2. `./gradlew runServer` 手动验证(运行目录已存在缺 4 个段落的旧 config.yml,是现成测试样本):
   - 启动后旧 config.yml 被补全 `end-portal-generator`、`sculk-shrieker`、`anvil-high-level-enchant`、`dripstone-growth` 段落,控制台输出补全日志;
   - 删除任意键后重启/`reload`,键被补回;
   - 全新安装的 config.yml 不被重写(注释保留)。

## 范围外

- messages.yml 已有此行为,不动。
- 不做 `config-version`、不清理旧键。
