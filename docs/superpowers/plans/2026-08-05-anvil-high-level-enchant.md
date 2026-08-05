# Anvil High-Level Enchant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let players apply command-given enchanted books whose levels exceed the vanilla maximum (e.g., Efficiency X) on the anvil — while preventing players from crafting over-level enchantments from vanilla-legal books — and make the "too expensive" experience-cost threshold configurable.

**Architecture:** A `PrepareAnvilEvent` listener reads the book's enchantments and, **only when the book or item carries an enchantment above its vanilla max**, corrects the result item's enchantment level to `max(bookLevel, itemLevel)`. All-vanilla-legal combinations keep vanilla behavior (two Efficiency V books still give Efficiency V). Also applies the configurable `AnvilView#setMaximumRepairCost(...)` ("too expensive" cap). Does **not** use `bypassEnchantmentLevelRestriction`, which would be too broad.

**Tech Stack:** Paper API 26.1.2, Java 25, Gradle 9.6.1 (Kotlin DSL). No external dependencies.

## Global Constraints

- Paper API 26.1.2 only — no external libraries (per `CLAUDE.md`).
- All listeners live in `src/main/java/team/starm/starMSkyblockGamePlay/listener/` and take the `StarMSkyblockGamePlay` plugin instance via constructor.
- Config is read live via `plugin.getConfig()` (no caching); this feature needs no messages, so no `messages.yml` change and no `LanguageManager` use.
- This project has **no unit-test framework**. Verification = `./gradlew build` (compile check) + manual `./gradlew runServer` testing per the spec's test cases.
- `config.yml` / source files use UTF-8 with Chinese comments — keep that style.
- `PrepareAnvilEvent` fires at the end of every anvil `createResult()` pass; the view properties set here take effect from the next pass. Because the player's first placement (only one slot filled) cannot trigger the clamp/too-expensive checks, the values are always in effect by the time both slots hold items. No re-triggering needed.

---

### Task 1: Add the `anvil-high-level-enchant` config section

**Files:**
- Modify: `src/main/resources/config.yml` (append after the `sculk-shrieker` section, end of file)

**Interfaces:**
- Produces: config keys `anvil-high-level-enchant.enabled` (boolean, default `true`) and `anvil-high-level-enchant.max-repair-cost` (int, default `40`), read by Task 2.

- [ ] **Step 1: Append the config section**

Open `src/main/resources/config.yml` and append these lines at the end (after the `sculk-shrieker` block, which ends with `enabled: true`):

```yaml
# 铁砧超等级附魔书（允许应用超过原版最高等级的附魔书，如效率10）
anvil-high-level-enchant:
  # 是否启用该功能
  enabled: true
  # 铁砧"过于昂贵"的经验花费上限（原版为 40；设为 0 或负数表示不设上限）
  max-repair-cost: 40
```

- [ ] **Step 2: Verify YAML parses**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL` (the config file is copied to the jar during `processResources`; a YAML syntax error would fail the build).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/config.yml
git commit -m "feat: add anvil-high-level-enchant config section"
```

---

### Task 2: Create and register `AnvilEnchantBypassListener`

**Files:**
- Create: `src/main/java/team/starm/starMSkyblockGamePlay/listener/AnvilEnchantBypassListener.java`
- Modify: `src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java`

**Interfaces:**
- Consumes: config keys from Task 1.
- Produces: listener class `AnvilEnchantBypassListener` (constructor: `(StarMSkyblockGamePlay plugin)`), registered in `onEnable`.

- [ ] **Step 1: Create the listener class**

Create `src/main/java/team/starm/starMSkyblockGamePlay/listener/HighLevelEnchantListener.java` with exactly this content:

```java
package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.Map;

/**
 * 铁砧超等级附魔书：允许命令给予的、超过原版最高等级的附魔书（如效率10）被完整应用。
 *
 * 仅在"附魔书或物品的某个附魔等级超过其原版上限"时才修正结果等级（取书与物品的较大等级）。
 * 原版合法的组合（如两本效率5）完全保持原版行为，因此玩家无法自行合成超出原版上限的等级。
 */
public class HighLevelEnchantListener implements Listener {

    private final StarMSkyblockGamePlay plugin;

    public HighLevelEnchantListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("anvil-high-level-enchant.enabled", true)) return;

        AnvilView view = event.getView();
        int maxRepairCost = plugin.getConfig().getInt("anvil-high-level-enchant.max-repair-cost", 40);
        if (maxRepairCost <= 0) {
            maxRepairCost = Integer.MAX_VALUE; // 不设"过于昂贵"上限
        }
        view.setMaximumRepairCost(maxRepairCost);

        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();
        ItemStack result = event.getResult();
        if (first == null || second == null || result == null || result.isEmpty()) return;

        ItemStack fixed = null;
        for (Map.Entry<Enchantment, Integer> entry : getEnchants(second).entrySet()) {
            Enchantment ench = entry.getKey();
            int bookLevel = entry.getValue();
            int itemLevel = getLevel(first, ench);

            // 书与物品的等级都在原版上限内 → 保持原版行为（含截断，如两本效率5仍为效率5）
            if (bookLevel <= ench.getMaxLevel() && itemLevel <= ench.getMaxLevel()) continue;

            // 原版已因不兼容/不适用而拒绝该附魔 → 保持原版拒绝，不强行附加
            if (getLevel(result, ench) <= 0) continue;

            int correctLevel = Math.max(bookLevel, itemLevel); // 不叠加，无法通过合成超出已有最高等级
            if (fixed == null) fixed = result.clone();
            setLevel(fixed, ench, correctLevel);
        }

        if (fixed != null) {
            event.setResult(fixed);
        }
    }

    /** 读取物品附魔等级（附魔书读存储附魔，其余读普通附魔）。 */
    private static int getLevel(ItemStack item, Enchantment ench) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchantLevel(ench);
        }
        return item.getEnchantmentLevel(ench);
    }

    /** 读取物品的附魔集合（附魔书读存储附魔）。 */
    private static Map<Enchantment, Integer> getEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants();
        }
        return item.getEnchantments();
    }

    /** 写入物品附魔等级（附魔书写存储附魔，其余写普通附魔），忽略等级上限。 */
    private static void setLevel(ItemStack item, Enchantment ench, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(ench, level, true);
            item.setItemMeta(storageMeta);
        } else {
            meta.addEnchant(ench, level, true);
            item.setItemMeta(meta);
        }
    }
}
```

- [ ] **Step 2: Register the listener**

In `src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java`:

1. Add the import so imports stay alphabetically sorted (between `EndPortalGeneratorListener` and `LightningGuardianConvertListener`):

```java
import team.starm.starMSkyblockGamePlay.listener.HighLevelEnchantListener;
```

2. In `onEnable()`, add the registration after the `EndPortalGeneratorListener` line:

```java
getServer().getPluginManager().registerEvents(new EndPortalGeneratorListener(this), this);
getServer().getPluginManager().registerEvents(new HighLevelEnchantListener(this), this);
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/listener/HighLevelEnchantListener.java src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java
git commit -m "feat: restrict over-level anvil enchant to command-given books only"
```

---

### Task 3: Manual verification on a live server

**Files:** none (runtime verification only)

**Interfaces:**
- Consumes: the listener from Task 2 + config from Task 1.

- [ ] **Step 1: Start the server**

Run: `./gradlew runServer`

- [ ] **Step 2: Obtain an over-level test book**

In the server console (as op), give an Efficiency X book. On Paper 26.1.2 (MC 1.21.4+) this component syntax works:

```
/give @s minecraft:enchanted_book[enchantments={levels:{"minecraft:efficiency":10}}]
```

If the server rejects the syntax, use the item-component equivalent for the installed MC version.

- [ ] **Step 3: Verify the over-level book applies fully**

Place a clean diamond pickaxe in the anvil's left slot, then the command-given Efficiency X book in the right slot.
Expected: the result shows **Efficiency X** (vanilla would show Efficiency V). Take it and confirm mining speed reflects Efficiency X.

- [ ] **Step 4: Verify vanilla-legal books cannot craft over-level**

Combine two vanilla Efficiency V books → result is **Efficiency V** (vanilla clamp, **NOT** VI).

- [ ] **Step 5: Verify two command-given over-level books do not stack**

Combine two command-given Efficiency X books → result is **Efficiency X** (max taken, **NOT** XI — over-level levels are never increased by combining).

- [ ] **Step 6: Verify an existing over-level item is not downgraded**

Apply a vanilla Efficiency V book to a tool that already carries Efficiency X (from a command book) → the tool **keeps Efficiency X** (no downgrade to V).

- [ ] **Step 7: Verify compatibility is still enforced**

Apply a Sharpness X book to a tool already carrying Smite → the enchantment is rejected as conflicting (incompatible), exactly as vanilla.

- [ ] **Step 8: Verify the configurable too-expensive threshold**

1. Apply a multi-enchantment book whose computed cost ≥ 40 → with the default `max-repair-cost: 40` the result is rejected as "too expensive".
2. Set `max-repair-cost: 200` in `config.yml`, run `/starmskyblockgameplay reload`, re-place the book → the result now appears (the player must have enough levels to take it).
3. Set `max-repair-cost: 0`, reload → no book is ever "too expensive".

- [ ] **Step 9: Verify the disable toggle**

Set `anvil-high-level-enchant.enabled: false`, reload, re-place the Efficiency X book → the vanilla clamp returns (Efficiency X → Efficiency V).

- [ ] **Step 10: Confirm normal books are unaffected**

Apply a normal Efficiency V book → behaves exactly as vanilla (Efficiency V, normal cost).
