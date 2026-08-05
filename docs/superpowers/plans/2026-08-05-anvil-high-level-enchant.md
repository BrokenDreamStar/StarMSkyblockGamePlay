# Anvil High-Level Enchant Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let players apply enchanted books whose levels exceed the vanilla maximum (e.g., Efficiency X) on the anvil, and make the "too expensive" experience-cost threshold configurable.

**Architecture:** A single `PrepareAnvilEvent` listener calls Paper's native `AnvilView#bypassEnchantmentLevelRestriction(true)` (disables the vanilla level clamp so the book's full level is applied) and `AnvilView#setMaximumRepairCost(...)` (raises/lowers the "too expensive" cap). No manual recomputation of the anvil's combining rules.

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

Create `src/main/java/team/starm/starMSkyblockGamePlay/listener/AnvilEnchantBypassListener.java` with exactly this content:

```java
package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.view.AnvilView;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

public class AnvilEnchantBypassListener implements Listener {

    private final StarMSkyblockGamePlay plugin;

    public AnvilEnchantBypassListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
    }

    /**
     * 允许铁砧应用超过原版最高等级的附魔书（如效率10），并应用可配置的"过于昂贵"上限。
     * 仅在放入物品和附魔书时生效；该 view 属性在菜单生命周期内保持。
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("anvil-high-level-enchant.enabled", true)) return;

        AnvilView view = event.getView();
        view.bypassEnchantmentLevelRestriction(true);

        int maxRepairCost = plugin.getConfig().getInt("anvil-high-level-enchant.max-repair-cost", 40);
        if (maxRepairCost <= 0) {
            maxRepairCost = Integer.MAX_VALUE; // 不设"过于昂贵"上限
        }
        view.setMaximumRepairCost(maxRepairCost);
    }
}
```

- [ ] **Step 2: Register the listener**

In `src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java`:

1. Add the import so imports stay alphabetically sorted (before `EndPortalGeneratorListener`):

```java
import team.starm.starMSkyblockGamePlay.listener.AnvilEnchantBypassListener;
```

2. In `onEnable()`, add the registration after the `SculkShriekerListener` line:

```java
getServer().getPluginManager().registerEvents(new SculkShriekerListener(this), this);
getServer().getPluginManager().registerEvents(new AnvilEnchantBypassListener(this), this);
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/listener/AnvilEnchantBypassListener.java src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java
git commit -m "feat: add AnvilEnchantBypassListener to allow over-level enchanted books"
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

- [ ] **Step 3: Verify the level clamp is lifted**

Place a clean diamond pickaxe in the anvil's left slot, then the Efficiency X book in the right slot.
Expected: the result shows **Efficiency X** (vanilla would show Efficiency V), and the level cost is ~10. Take it and confirm mining speed reflects Efficiency X.

- [ ] **Step 4: Verify book + book combining**

Combine two Efficiency X books → result is **Efficiency XI** (the vanilla `+1` rule, now unclamped).

- [ ] **Step 5: Verify compatibility is still enforced**

Apply a Sharpness X book to a tool already carrying Smite → the enchantment is rejected as conflicting (incompatible), exactly as vanilla.

- [ ] **Step 6: Verify the configurable too-expensive threshold**

1. Apply a book whose computed cost ≥ 40 (e.g., a very high-level Sharpness book) → with the default `max-repair-cost: 40` the result is rejected as "too expensive".
2. Set `max-repair-cost: 200` in `config.yml`, run `/starmskyblockgameplay reload`, re-place the book → the result now appears with the full cost (the player must have enough levels to take it).
3. Set `max-repair-cost: 0`, reload → no book is ever "too expensive" (only the player's XP gates the take).

- [ ] **Step 7: Verify the disable toggle**

Set `anvil-high-level-enchant.enabled: false`, reload, re-place the Efficiency X book → the vanilla clamp returns (Efficiency X → Efficiency V).

- [ ] **Step 8: Confirm normal books are unaffected**

Apply a normal Efficiency V book → behaves exactly as vanilla (Efficiency V, normal cost).
