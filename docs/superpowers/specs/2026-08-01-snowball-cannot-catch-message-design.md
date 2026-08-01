# 雪球命中不可捕捉生物提示 — 设计

**日期**: 2026-08-01
**分支**: feat/end-portal-generator
**状态**: 已批准（方案 A）

## 背景

`SnowballConverterListener.onSnowballHit` 中，转换雪球命中生物时有几个静默返回路径。其中「没有对应刷怪蛋」已有 `no-egg` 消息，但「被白名单/黑名单排除」的情况完全无反馈，玩家拿雪球砸不可捕捉生物（如村民、末影人）时不知道原因。

## 需求

- 转换雪球命中**被白名单/黑名单排除**的生物时，向投掷者发送一条提示消息
- 不改动其他行为：`no-egg` 消息、概率为 0 的静默、砸到非生物（方块/物品展示框等）均不提示
- 消息文案可在 `messages.yml` 中配置

## 设计（方案 A：最小改动）

### 1. `messages.yml`

在 `snowball-converter:` 节点下新增：

```yaml
cannot-catch: "&c{entity} 无法捕捉"
```

### 2. `SnowballConverterListener.java`

在 `onSnowballHit` 的白名单/黑名单判断分支（现有 `return` 处）各加一条发送：

```java
if (useWhitelist) {
    List<String> whitelist = plugin.getConfig().getStringList("snowball-converter.whitelist");
    if (!whitelist.contains(entityType.name())) {
        player.sendMessage(lang.getComponent("snowball-converter.cannot-catch",
                Map.of("entity", entityComp)));
        return;
    }
} else {
    List<String> blacklist = plugin.getConfig().getStringList("snowball-converter.blacklist");
    if (blacklist.contains(entityType.name())) {
        player.sendMessage(lang.getComponent("snowball-converter.cannot-catch",
                Map.of("entity", entityComp)));
        return;
    }
}
```

`entityComp`（翻译组件，`entity.minecraft.<type>`）在该分支之前已构建（line 99），可直接复用；`{entity}` 占位符沿用 `failed`/`converted` 的现有模式，自动按玩家客户端语言显示生物名。

## 变更文件

| 文件 | 变更 |
|---|---|
| `src/main/resources/messages.yml` | 新增 `snowball-converter.cannot-catch` 键 |
| `src/main/java/.../listener/SnowballConverterListener.java` | 黑白名单排除分支发送提示 |

## 验证

`./gradlew build` 通过（编译验证；插件无测试框架）。
