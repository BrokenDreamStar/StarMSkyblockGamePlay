# Sculk Shrieker — Design Spec

## Overview

Add a new feature to StarMSkyblockGamePlay: a player holding an `ECHO_SHARD` in the main hand can right-click a `SCULK_SHRIEKER` block to set its `can_summon` value to `true` (making it able to summon a Warden). One Echo Shard is consumed per successful use (not consumed in Creative mode).

## Architecture

### New Files

| File | Purpose |
|------|---------|
| `listener/SculkShriekerListener.java` | Handles right-click detection, item validation, block state modification |
| — | No new utility classes required |

### Modified Files

| File | Change |
|------|--------|
| `StarMSkyblockGamePlay.java` | Register `SculkShriekerListener` in `onEnable` |
| `config.yml` | Add `sculk-shrieker` section |
| `messages.yml` | Add `sculk-shrieker` messages |

No command changes — Echo Shards are a vanilla item obtained in game, so no give subcommand is needed.

## Configuration (`config.yml`)

```yaml
sculk-shrieker:
  # 是否启用回响碎片强化尖啸体功能
  enabled: true
```

## Messages (`messages.yml`)

```yaml
sculk-shrieker:
  activated: "&a消耗 1 个回响碎片，该尖啸体已可召唤监守者！"
  already-activated: "&c该尖啸体已可召唤监守者"
```

## Listener: `SculkShriekerListener`

### Event: `PlayerInteractEvent` (priority HIGH, ignoreCancelled)

Gate checks, in order:

1. `sculk-shrieker.enabled` (default `true`) — disabled → silently ignore
2. `event.getAction() == RIGHT_CLICK_BLOCK` — otherwise silently ignore
3. Item is `ECHO_SHARD` (via `event.getItem()`) — otherwise silently ignore
4. `event.getHand() == EquipmentSlot.HAND` (main hand only) — otherwise silently ignore
5. Clicked block is `SCULK_SHRIEKER` — otherwise silently ignore

### Block Modification

Uses the block data approach (the lighter, semantically correct path since `can_summon` is a block state property):

```java
BlockData blockData = clickedBlock.getBlockData();
if (blockData instanceof SculkShrieker shrieker) {   // org.bukkit.block.data.type.SculkShrieker
    shrieker.setCanSummon(true);
    clickedBlock.setBlockData(shrieker, false);
}
```

### Behavior

Messages are shown in the **action bar** (via `player.sendActionBar` + `LegacyComponentSerializer`), keeping the chat clean; text remains configurable through `messages.yml`.

- **Already `can_summon=true`:** show `already-activated` action bar message, do NOT consume the Echo Shard, do not cancel the event (vanilla right-click on a shrieker has no side effects).
- **Success:** set `can_summon=true`, cancel the event (prevents dual-hand interactions), consume 1 Echo Shard (skipped in Creative mode, matching `EndPortalGeneratorListener`), show `activated` action bar message.

## Error Handling

- Feature disabled → silently ignore (no message)
- Item not `ECHO_SHARD` / off-hand / not right-click on block → silently ignore
- Block not a `SCULK_SHRIEKER` → silently ignore
- Creative mode → feature works, item not consumed

## Testing

Manual testing via `./gradlew runServer` (user-driven):

- Right-click a player-placed shrieker with an Echo Shard → `can_summon` becomes true, shard consumed, success message
- Right-click again → `already-activated` message, shard NOT consumed
- Creative mode → shard not consumed
- Feature disabled → no effect
- Other items / other blocks / off-hand → no effect
