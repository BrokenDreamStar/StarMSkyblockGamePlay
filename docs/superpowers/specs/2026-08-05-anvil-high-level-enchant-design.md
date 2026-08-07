# Anvil High-Level Enchant — Design Spec

## Overview

Add a feature to StarMSkyblockGamePlay: allow players to apply enchanted books whose enchantment levels exceed the vanilla maximum (e.g., an Efficiency X book given via command) on the anvil — **without** allowing players to craft over-level enchantments themselves from vanilla-legal books.

Vanilla clamps any enchantment applied via the anvil to `Enchantment#getMaxLevel()` (5 for Efficiency, 5 for Sharpness, etc.), so an Efficiency X book becomes Efficiency V. This feature applies the full level **only when the book or item already carries an enchantment above its vanilla max**; all-vanilla-legal combinations keep vanilla behavior (e.g., two Efficiency V books still give Efficiency V, never VI).

## Architecture

### New Files

| File | Purpose |
|------|---------|
| `listener/HighLevelEnchantListener.java` | Listens to `PrepareAnvilEvent`; corrects the result enchantment level only for over-level inputs |

### Modified Files

| File | Change |
|------|--------|
| `StarMSkyblockGamePlay.java` | Register `HighLevelEnchantListener` in `onEnable` |
| `config.yml` | Add `anvil-high-level-enchant` section |

No `messages.yml` changes — the feature is silent and requires no user-facing messages.

## Root Cause (verified in the 26.1.2 server jar)

`net.minecraft.world.inventory.AnvilMenu.createResult()` combines the item's and book's enchantments, then applies:

```java
if (resultLevel > enchantment.getMaxLevel() && !this.bypassEnchantmentLevelRestriction) {
    resultLevel = enchantment.getMaxLevel();
}
```

The `bypassEnchantmentLevelRestriction` field is initialized to `false` and never set by vanilla — so the clamp always applies. Paper exposes it as `AnvilView#bypassEnchantmentLevelRestriction(boolean)`, but enabling it globally is **too broad**: it also lets vanilla-legal combinations exceed the cap (two Efficiency V books → Efficiency VI), which the user does not want. Hence this feature does **not** use that flag; it corrects the result item directly instead.

## Solution

In `PrepareAnvilEvent`, after vanilla has computed the result:

- Read the second slot (book) enchantments via `EnchantmentStorageMeta` (books store enchantments in `STORED_ENCHANTMENTS`, which `ItemStack#getEnchantments()` does **not** return).
- For each book enchantment `E`:
  - If `bookLevel <= max(E)` **and** `itemLevel <= max(E)` → skip. Vanilla behavior is correct (including clamping, so `Efficiency V + Efficiency V → Efficiency V`).
  - If the vanilla result does not contain `E` → skip (vanilla rejected it as incompatible/not-applicable; do not force it).
  - Otherwise (book or item carries `E` above its max) → set the result's `E` to `max(bookLevel, itemLevel)` via `ItemMeta#addEnchant` (tools) or `EnchantmentStorageMeta#addStoredEnchant` (books), with `ignoreLevelRestriction=true`.

This ensures **no anvil operation ever produces an enchantment level higher than the highest input book/item level** — over-level enchantments can only enter the game through a command-given book, and the anvil merely transfers/preserves them.

## Configuration (`config.yml`)

```yaml
# 铁砧超等级附魔书（允许应用超过原版最高等级的附魔书，如效率10）
anvil-high-level-enchant:
  # 是否启用该功能
  enabled: true
  # 铁砧"过于昂贵"的经验花费上限（原版为 40；设为 0 或负数表示不设上限）
  max-repair-cost: 40
```

## Listener: `HighLevelEnchantListener`

### Event: `PrepareAnvilEvent` (priority NORMAL)

```java
@EventHandler
public void onPrepareAnvil(PrepareAnvilEvent event) {
    if (!plugin.getConfig().getBoolean("anvil-high-level-enchant.enabled", true)) return;

    AnvilView view = event.getView();
    int maxRepairCost = plugin.getConfig().getInt("anvil-high-level-enchant.max-repair-cost", 40);
    if (maxRepairCost <= 0) maxRepairCost = Integer.MAX_VALUE;
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
        if (bookLevel <= ench.getMaxLevel() && itemLevel <= ench.getMaxLevel()) continue; // 原版行为
        if (getLevel(result, ench) <= 0) continue; // 不兼容/未应用，保持原版拒绝
        int correctLevel = Math.max(bookLevel, itemLevel); // 不叠加，无法通过合成超出已有最高等级
        if (fixed == null) fixed = result.clone();
        setLevel(fixed, ench, correctLevel);
    }
    if (fixed != null) event.setResult(fixed);
}
```

Helper methods read/write enchantment level via `ItemMeta` / `EnchantmentStorageMeta` so both tools and enchanted-book results are handled.

### Timing

The event fires after vanilla builds the result, and this listener **modifies the result item directly** — so the corrected level appears immediately on the first placement of the book. No reliance on the bypass flag's next-pass timing.

## Behavior

- **Command-given Efficiency X book + clean tool** → Efficiency X (vanilla would clamp to V).
- **Two vanilla Efficiency V books** → Efficiency V (vanilla behavior preserved; no VI).
- **Two command-given Efficiency X books** → Efficiency X (takes the max; **no XI** — over-level levels are never increased by combining).
- **Vanilla Efficiency V book + tool already carrying Efficiency X** → Efficiency X preserved (no downgrade).
- **Incompatible over-level book** (e.g., Sharpness X on a Smite tool) → still rejected by vanilla, unchanged.
- **Experience cost** stays as vanilla computed (based on the clamped level, so an Efficiency X book costs ~5 levels). Configuring `max-repair-cost` still lifts the "too expensive" gate for multi-enchantment books.

## Error Handling

- Feature disabled → silently ignore (no message, vanilla behavior preserved).
- Empty inputs / empty result / too-expensive empty result → silently return.
- No other failure modes — the listener only reads enchantments and, conditionally, replaces the result item's enchantment levels.

## Testing

Manual testing via `./gradlew runServer` (user-driven).

**Obtaining a test book** (command-given, over-level):

```
/give @s minecraft:enchanted_book[enchantments={levels:{"minecraft:efficiency":10}}]
```

(Adjust the component syntax if the server's MC version differs.)

**Test cases:**

1. Efficiency X book + clean pickaxe → result is **Efficiency X**; taking it and mining confirms level X.
2. Two vanilla Efficiency V books → result is **Efficiency V** (NOT VI).
3. Two command-given Efficiency X books → result is **Efficiency X** (NOT XI).
4. Efficiency V book applied to a tool already carrying Efficiency X → **Efficiency X preserved** (no downgrade to V).
5. Conflicting enchantment still rejected (Sharpness X book on a Smite tool).
6. `anvil-high-level-enchant.enabled: false` + reload → Efficiency X book clamps to Efficiency V (feature off).
7. Normal enchantments (Efficiency V book) behave exactly as vanilla.
8. With default `max-repair-cost: 40`, a multi-enchantment book whose cost reaches 40 is rejected as "too expensive".
9. Set `max-repair-cost: 200` + reload → the same book now produces a result; `0` removes the cap entirely.
