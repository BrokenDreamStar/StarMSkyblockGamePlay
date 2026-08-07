# End Portal Generator — Design Spec

## Overview

Add a new feature to StarMSkyblockGamePlay: a special `EYE_OF_ENDER` item with a PersistentDataContainer flag. When a player right-clicks a block while holding it, the item is consumed and an unactivated End Portal (12 `END_PORTAL_FRAME` blocks, all `eye=false`) is generated centered at the clicked block's location +1 Y level.

## Architecture

### New Files

| File | Purpose |
|------|---------|
| `listener/EndPortalGeneratorListener.java` | Handles right-click detection, item validation, portal placement |
| — | No new utility classes required |

### Modified Files

| File | Change |
|------|--------|
| `StarMSkyblockGamePlay.java` | Register listener, add `giveportal` subcommand + tab completion |
| `config.yml` | Add `end-portal-generator` section |
| `messages.yml` | Add `end-portal-generator` messages |

## Configuration (`config.yml`)

```yaml
end-portal-generator:
  # 是否启用末地传送门生成功能
  enabled: true
  # 物品显示名称（支持 & 颜色代码，留空则不设置）
  item-name: "&5&l传送门生成器"
  # 物品 Lore 描述（每行一条）
  lore:
    - "&7右键点击方块以生成末地传送门"
```

## Item Identification

- **NamespacedKey:** `starmskyblockgameplay:end_portal` (`PersistentDataType.BOOLEAN`, value `true`)
- **Material:** `EYE_OF_ENDER`
- Display name and lore are read from `config.yml` and applied at item creation time (in the command).
- The PDC flag is set when creating the item via the `/starmskyblockgameplay giveportal` command.

## Command

Integrated into the existing `/starmskyblockgameplay` command:

```
/starmskyblockgameplay giveportal <player> [amount]
```

- `<player>` — online player name (required)
- `[amount]` — item count, default 1, minimum 1, integer
- Permissions: `starmskyblockgameplay.admin`
- Tab completion: player names for arg 2, quantity suggestions for arg 3

The command creates an `EYE_OF_ENDER` ItemStack, sets the `end_portal` PDC flag to `true`, applies item-name and lore from config, and gives it to the target player.

## Listener: `EndPortalGeneratorListener`

### Event: `PlayerInteractEvent`

- **Gate:** `end-portal-generator.enabled` (default `true`)
- **Action:** `RIGHT_CLICK_BLOCK`
- **Item check:** Item is `EYE_OF_ENDER` and its PDC contains `end_portal = true`
- **Consumption:** Remove 1 item from the player's hand (main hand only)

### Portal Generation Logic

1. Determine center: `clickedBlock.getLocation().add(0.5, 1, 0.5)` — centered on the block's top face, at Y+1
2. Verify all 12 frame positions are passable (air, water, or replaceable blocks like tall grass). If any position is blocked, cancel placement and send error message.
3. Set each position to `END_PORTAL_FRAME` with:
   - `eye=false` (unactivated)
   - `facing` computed toward the center of the portal
4. Send success message to player.

### Frame Positions (relative to center)

```
x=-2: (-2,-1), (-2,0), (-2,1)     → facing EAST
x=-1: (-1,-2), (-1,2)             → (-1,-2) facing SOUTH, (-1,2) facing NORTH
x=0:  (0,-2), (0,2)               → (0,-2) facing SOUTH, (0,2) facing NORTH
x=1:  (1,-2), (1,2)               → (1,-2) facing SOUTH, (1,2) facing NORTH
x=2:  (2,-1), (2,0), (2,1)        → facing WEST
```

Facing logic:
- East wall (x=-2), West wall (x=2) → frames on these walls face inward (EAST for the right wall... wait)

Actually, let me reconsider the facing directions:

The `END_PORTAL_FRAME` block has a `facing` property that determines which direction the frame block faces. In Minecraft, the frame blocks always face toward the center of the portal.

For a portal centered at (cx, cy, cz):
- Frames on the x=-2 side (negative x): the frame face points EAST (toward positive x = center)
- Frames on the x=+2 side (positive x): the frame face points WEST (toward negative x = center)
- Frames on the z=-2 side (negative z): the frame face points SOUTH (toward positive z = center)
- Frames on the z=+2 side (positive z): the frame face points NORTH (toward negative z = center)

Frame positions (all offsets relative to center):
- x=-2, z=-1: facing EAST
- x=-2, z=0: facing EAST
- x=-2, z=1: facing EAST
- x=-1, z=-2: facing SOUTH
- x=0, z=-2: facing SOUTH
- x=1, z=-2: facing SOUTH
- x=-1, z=2: facing NORTH
- x=0, z=2: facing NORTH
- x=1, z=2: facing NORTH
- x=2, z=-1: facing WEST
- x=2, z=0: facing WEST
- x=2, z=1: facing WEST

And the corners (-2,-2), (-2,2), (2,-2), (2,2) are NOT placed.

## Messages (`messages.yml`)

```yaml
end-portal-generator:
  generated: "&a已生成末地传送门！"
  blocked: "&c传送门框架位置被阻挡，无法生成传送门！"
```

## Error Handling

- Feature disabled → silently ignore (no message)
- Item not `EYE_OF_ENDER` or no PDC flag → silently ignore
- Not a right-click on a block → silently ignore
- Any frame position is occupied or non-replaceable → cancel, send `blocked` message, do NOT consume item
- World is `THE_END` — no special handling; allow placement

## Testing

- Give item via command, right-click a block → portal generates at Y+1
- Verify all 12 frame blocks have `eye=false`
- Verify facing directions are correct
- Verify item is consumed (count decreases by 1)
- Verify error message when area is obstructed
- Verify feature disabled = no effect
- Verify `/starmskyblockgameplay giveportal` tab completion works
