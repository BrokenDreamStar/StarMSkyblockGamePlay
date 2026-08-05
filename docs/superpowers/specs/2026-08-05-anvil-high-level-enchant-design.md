# Anvil High-Level Enchant — Design Spec

## Overview

Add a feature to StarMSkyblockGamePlay: allow players to apply enchanted books whose enchantment levels exceed the vanilla maximum (e.g., an Efficiency X book) on the anvil.

Vanilla clamps any enchantment applied via the anvil to `Enchantment#getMaxLevel()` (5 for Efficiency, 5 for Sharpness, etc.), so an Efficiency X book becomes Efficiency V. This feature disables that clamp so the book's full level is applied.

## Architecture

### New Files

| File | Purpose |
|------|---------|
| `listener/AnvilEnchantBypassListener.java` | Listens to `PrepareAnvilEvent`; enables Paper's anvil level-restriction bypass on the anvil view |

### Modified Files

| File | Change |
|------|--------|
| `StarMSkyblockGamePlay.java` | Register `AnvilEnchantBypassListener` in `onEnable` |
| `config.yml` | Add `anvil-high-level-enchant` section |

No `messages.yml` changes — the feature is silent and requires no user-facing messages.

## Root Cause (verified in the 26.1.2 server jar)

`net.minecraft.world.inventory.AnvilMenu.createResult()` combines the item's and book's enchantments, then applies:

```java
if (resultLevel > enchantment.getMaxLevel() && !this.bypassEnchantmentLevelRestriction) {
    resultLevel = enchantment.getMaxLevel();
}
```

The `bypassEnchantmentLevelRestriction` field is initialized to `false` in the constructor and is never set by vanilla — so the clamp always applies, even in Creative mode.

## Solution

Paper exposes this field as a native API on `org.bukkit.inventory.view.AnvilView`:

```java
void bypassEnchantmentLevelRestriction(boolean bypassEnchantmentLevelRestriction)
```

Its javadoc states it "allows for, e.g., enchanted books to be applied fully, even if their enchantments are beyond the limit." Setting it `true` on the anvil view skips the clamp, so the book's full level is applied and the experience cost is computed from the true level.

The implementation is a ~10-line listener; no manual recomputation of the anvil's combining rules is needed. (Alternative approaches — manually recomputing enchantments in `PrepareAnvilEvent`, or intercepting result-take clicks — were rejected as needlessly complex / giving a wrong result preview.)

## Configuration (`config.yml`)

```yaml
# 铁砧超等级附魔书（允许应用超过原版最高等级的附魔书，如效率10）
anvil-high-level-enchant:
  # 是否启用该功能
  enabled: true
```

## Listener: `AnvilEnchantBypassListener`

### Event: `PrepareAnvilEvent` (priority NORMAL)

```java
@EventHandler
public void onPrepareAnvil(PrepareAnvilEvent event) {
    if (!plugin.getConfig().getBoolean("anvil-high-level-enchant.enabled", true)) return;
    event.getView().bypassEnchantmentLevelRestriction(true);
}
```

Gate check: `anvil-high-level-enchant.enabled` (default `true`) — disabled → silently ignore, vanilla behavior preserved.

### Timing (verified in the server jar)

`PrepareAnvilEvent` fires at the end of every `createResult()` pass, including the early "one slot empty" passes that cannot produce a clamped result. The flag is therefore set on the player's first placement (tool or book), and is already `true` by the time both slots hold items — so the result shown for the item + book combination is correct immediately. No re-triggering of `createResult()` is required.

The flag persists for the lifetime of the anvil menu and resets when the menu is closed.

## Behavior

- **Efficiency X book + tool** → the tool gets Efficiency X (vanilla would clamp to V).
- **Experience cost** scales with the true level (Efficiency X ≈ 10 levels), staying under the vanilla "too expensive" threshold (40) for normal over-level books.
- **"Too expensive" is preserved** (user's choice): a book whose computed cost reaches the vanilla `maximumRepairCost` (40) is still rejected by the anvil.
- **Enchantment compatibility is unaffected** — conflicting enchantments (e.g., Sharpness + Smite) are still rejected; the bypass only removes the level clamp.
- **Book + book**: combining two identical over-level books yields `level + 1` (vanilla rule, now unclamped) — e.g., two Efficiency X books → Efficiency XI.

## Error Handling

- Feature disabled → silently ignore (no message, vanilla behavior preserved)
- No other failure modes — the API is a pure boolean flag toggle on the view.

## Testing

Manual testing via `./gradlew runServer` (user-driven).

**Obtaining a test book:** the plugin has no give-book command, so use a console/admin command with item components, e.g.:

```
/give @s minecraft:enchanted_book[enchantments={levels:{"minecraft:efficiency":10}}]
```

(Adjust the component syntax if the server's MC version differs.)

**Test cases:**

1. Place a tool in slot 0 and the Efficiency X book in slot 1 → result shows Efficiency X; take it and verify the effect in-game.
2. Combine two Efficiency X books → Efficiency XI.
3. Confirm the level cost reflects the true level (Efficiency X ≈ 10 levels), not the clamped 5.
4. Confirm a conflicting enchantment is still rejected (Sharpness X book on a Smite tool).
5. Set `anvil-high-level-enchant.enabled: false` and reload → the vanilla clamp returns (Efficiency X → Efficiency V).
6. Confirm normal enchantments (e.g., Efficiency V book) behave exactly as vanilla.
