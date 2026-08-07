# End Portal Generator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a special EYE_OF_ENDER item that generates an unactivated End Portal when right-clicking a block.

**Architecture:** New `EndPortalGeneratorListener` following the existing listener pattern (PDC-based item identification, config gating, LanguageManager messages). The item is created via a new `giveportal` subcommand in the existing `/starmskyblockgameplay` command.

**Tech Stack:** Paper API 26.1.2, Java 25, Bukkit/Adventure API

**Design Spec:** `docs/superpowers/specs/2026-07-30-end-portal-generator-design.md`

## Global Constraints

- Package: `team.starm.starMSkyblockGamePlay`
- Listeners extend `Listener`, take `StarMSkyblockGamePlay plugin` as constructor arg
- Feature gating via `enabled: true/false` in config.yml
- PDC NamespacedKey prefix: `starmskyblockgameplay:`
- Messages via `LanguageManager` (getColored/getComponent) from messages.yml
- Commands integrated into existing `/starmskyblockgameplay` dispatch
- No external dependencies beyond Paper API

---

### Task 1: Add config section

**Files:**
- Modify: `src/main/resources/config.yml`

**Interfaces:**
- Consumes: nothing
- Produces: `end-portal-generator.enabled`, `end-portal-generator.item-name`, `end-portal-generator.lore` config paths

- [ ] **Step 1: Add end-portal-generator section to config.yml**

Insert after the `lightning-converter` section (before the end of the file):

```yaml
# 末地传送门生成器（使用带有特殊 NBT 的末影之眼右键点击方块生成末地传送门）
end-portal-generator:
  # 是否启用该功能
  enabled: true
  # 物品显示名称（支持 & 颜色代码，留空则不设置）
  item-name: "&5&l传送门生成器"
  # 物品 Lore 描述（每行一条）
  lore:
    - "&7右键点击方块以生成末地传送门"
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/config.yml
git commit -m "feat: add end-portal-generator config section"
```

---

### Task 2: Add messages

**Files:**
- Modify: `src/main/resources/messages.yml`

**Interfaces:**
- Consumes: nothing
- Produces: `end-portal-generator.generated`, `end-portal-generator.blocked` message paths

- [ ] **Step 1: Add end-portal-generator messages to messages.yml**

Insert after the `lightning-converter` section in messages.yml:

```yaml
end-portal-generator:
  # 传送门已生成
  generated: "&a已生成末地传送门！"
  # 传送门生成失败（方块被阻挡）
  blocked: "&c传送门框架位置被阻挡，无法生成传送门！"
```

- [ ] **Step 2: Commit**

```bash
git add src/main/resources/messages.yml
git commit -m "feat: add end-portal-generator messages"
```

---

### Task 3: Create EndPortalGeneratorListener

**Files:**
- Create: `src/main/java/team/starm/starMSkyblockGamePlay/listener/EndPortalGeneratorListener.java`

**Interfaces:**
- Consumes: `plugin.getConfig()` for `end-portal-generator.*`, `plugin.getLanguageManager()` for messages, `plugin` for `NamespacedKey`
- Produces: registered listener handling `PlayerInteractEvent` for portal generation

- [ ] **Step 1: Create the listener class**

```java
package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

public class EndPortalGeneratorListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;
    private final NamespacedKey portalKey;

    // Frame positions relative to center (12 positions, corners excluded)
    private static final int[][] FRAME_OFFSETS = {
        // x=-2 side (facing EAST)
        {-2, -1, 0}, {-2, 0, 0}, {-2, 1, 0},
        // z=-2 side (facing SOUTH)
        {-1, -2, 0}, {0, -2, 0}, {1, -2, 0},
        // z=+2 side (facing NORTH)
        {-1, 2, 0}, {0, 2, 0}, {1, 2, 0},
        // x=+2 side (facing WEST)
        {2, -1, 0}, {2, 0, 0}, {2, 1, 0},
    };

    public EndPortalGeneratorListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.portalKey = new NamespacedKey(plugin, "end_portal");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("end-portal-generator.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!meta.getPersistentDataContainer().has(portalKey, PersistentDataType.BOOLEAN)) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Center: clicked block location + (0.5, 1, 0.5)
        Location center = clickedBlock.getLocation().add(0.5, 1, 0.5);

        // Check all 12 frame positions are clear
        for (int[] offset : FRAME_OFFSETS) {
            Block frameBlock = center.clone().add(offset[0], offset[1], offset[2]).getBlock();
            if (!isReplaceable(frameBlock)) {
                player.sendMessage(lang.getColored("end-portal-generator.blocked"));
                return;
            }
        }

        // Place frame blocks
        for (int[] offset : FRAME_OFFSETS) {
            Block frameBlock = center.clone().add(offset[0], offset[1], offset[2]).getBlock();
            frameBlock.setType(Material.END_PORTAL_FRAME, false);

            BlockData blockData = frameBlock.getBlockData();
            if (blockData instanceof EndPortalFrame frame) {
                frame.setEye(false);
                frame.setFacing(getFacing(offset[0], offset[2]));
                frameBlock.setBlockData(frame, false);
            }
        }

        // Consume 1 item
        item.setAmount(item.getAmount() - 1);

        player.sendMessage(lang.getColored("end-portal-generator.generated"));
    }

    /**
     * Check if a block can be replaced (air, water, cave_air, or other non-solid blocks).
     */
    private boolean isReplaceable(Block block) {
        return block.isEmpty() || block.isLiquid() || Tag.REPLACEABLE_BY_TREES.isTagged(block.getType());
    }

    /**
     * Determine the facing direction based on the frame's position relative to center.
     *
     * @param dx relative X offset from center
     * @param dz relative Z offset from center
     * @return the facing direction toward the portal center
     */
    private org.bukkit.block.BlockFace getFacing(int dx, int dz) {
        if (dx == -2) return org.bukkit.block.BlockFace.EAST;  // left wall → face right (center)
        if (dx == 2) return org.bukkit.block.BlockFace.WEST;   // right wall → face left (center)
        if (dz == -2) return org.bukkit.block.BlockFace.SOUTH; // front wall → face back (center)
        if (dz == 2) return org.bukkit.block.BlockFace.NORTH;  // back wall → face front (center)
        return org.bukkit.block.BlockFace.NORTH; // fallback (should never reach here)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/listener/EndPortalGeneratorListener.java
git commit -m "feat: add EndPortalGeneratorListener"
```

---

### Task 4: Register listener and add giveportal command

**Files:**
- Modify: `src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java`

**Interfaces:**
- Consumes: `EndPortalGeneratorListener`, `config.yml` for item-name/lore
- Produces: registered listener, working `/starmskyblockgameplay giveportal <player> [amount]` command with tab completion

- [ ] **Step 1: Add import for EndPortalGeneratorListener**

Add to the imports section:

```java
import team.starm.starMSkyblockGamePlay.listener.EndPortalGeneratorListener;
```

- [ ] **Step 2: Register listener in onEnable()**

Add after the `LightningGuardianConvertListener` registration:

```java
getServer().getPluginManager().registerEvents(new EndPortalGeneratorListener(this), this);
```

- [ ] **Step 3: Add giveportal subcommand in onCommand()**

In the command usage help (line 53), update to include `giveportal`:

```java
sender.sendMessage("§c用法: /starmskyblockgameplay reload | givesnowball <玩家> [数量] [概率] | giveportal <玩家> [数量]");
```

In the reload command check, add `giveportal` handling. Insert after the `givesnowball` block (after line 128) and before the "未知子命令" fallback (line 130):

```java
if (args[0].equalsIgnoreCase("giveportal")) {
    if (args.length < 2) {
        sender.sendMessage("§c用法: /starmskyblockgameplay giveportal <玩家> [数量]");
        return true;
    }

    Player target = getServer().getPlayer(args[1]);
    if (target == null) {
        sender.sendMessage("§c找不到玩家: " + args[1]);
        return true;
    }

    int amount = 1;
    if (args.length >= 3) {
        try { amount = Math.max(1, Integer.parseInt(args[2])); }
        catch (NumberFormatException e) {
            sender.sendMessage("§c数量必须是有效整数。");
            return true;
        }
    }

    NamespacedKey portalKey = new NamespacedKey(this, "end_portal");

    ItemStack eye = new ItemStack(Material.ENDER_EYE, amount);
    eye.editMeta(meta -> {
        meta.getPersistentDataContainer().set(portalKey, PersistentDataType.BOOLEAN, true);
        String itemName = getConfig().getString("end-portal-generator.item-name");
        if (itemName != null && !itemName.isEmpty()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', itemName));
        }
        List<String> lore = getConfig().getStringList("end-portal-generator.lore");
        if (!lore.isEmpty()) {
            meta.setLore(lore.stream()
                    .map(line -> ChatColor.translateAlternateColorCodes('&', line))
                    .toList());
        }
    });

    Map<Integer, ItemStack> leftover = target.getInventory().addItem(eye);
    if (!leftover.isEmpty()) {
        target.getWorld().dropItemNaturally(target.getLocation(), eye);
        sender.sendMessage("§e部分末影之眼因背包空间不足已掉落至地上。");
    }
    sender.sendMessage(lang.getColored("end-portal-generator.generated",
            Map.of("player", target.getName(), "amount", String.valueOf(amount))));
    return true;
}
```

Note: The `lang.getColored()` message at the end needs a new message key. We should use a "give-success" message instead. Let me adjust:

Actually, looking at the snowball pattern, it uses the language manager with a give-player message. Let me add a `give-success` message to messages.yml and reference it properly.

Wait, the message `end-portal-generator.generated` is already used for the "已生成传送门" message. I need a separate key. Let me add `give-success` to messages.yml in Task 2.

Let me revise Task 2 to include:
```yaml
  # 命令给予成功
  give-success: "&a已将 {amount} 个传送门生成器给予玩家 {player}"
```

And use it in the command:
```java
sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
        String.format("&a已将 %d 个传送门生成器给予玩家 %s", amount, target.getName())));
```

Actually, let me keep it simple and just use a hardcoded message like the snowball command does, or better yet use the LanguageManager properly. The snowball command does:
```java
sender.sendMessage(lang.getColored("snowball-converter.give-snowball",
    Map.of("player", target.getName(),
            "amount", String.valueOf(amount),
            "item-name", getConfig().getString("snowball-converter.item-name", ""))));
```

So I need a `give-success` message with placeholders. Let me update Task 2 to add this message, and use it in the command.

Actually, re-reading the snowball message, it references the item name. So let me make it consistent.

Let me update:

messages.yml task becomes:
```yaml
end-portal-generator:
  # 传送门已生成
  generated: "&a已生成末地传送门！"
  # 传送门生成失败（方块被阻挡）
  blocked: "&c传送门框架位置被阻挡，无法生成传送门！"
  # 命令给予成功
  give-success: "&a已将 {amount} 个{item-name}给予玩家 {player}"
```

And the command message:
```java
sender.sendMessage(lang.getColored("end-portal-generator.give-success",
    Map.of("player", target.getName(),
            "amount", String.valueOf(amount),
            "item-name", getConfig().getString("end-portal-generator.item-name", ""))));
```

- [ ] **Step 4: Add tab completion for giveportal**

In `onTabComplete`, add `giveportal` to the first argument completions:

Update the args[0] filter completions line:
```java
if (args.length == 1) {
    return filterCompletions(args[0], "reload", "givesnowball", "giveportal");
}
```

Add a `giveportal` player name completion (after the `givesnowball` block):

```java
if (args.length == 2 && args[0].equalsIgnoreCase("giveportal")) {
    return getServer().getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
            .sorted()
            .toList();
}

if (args.length == 3 && args[0].equalsIgnoreCase("giveportal")) {
    return filterCompletions(args[2], "1", "16", "32", "64");
}
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/team/starm/starMSkyblockGamePlay/StarMSkyblockGamePlay.java
git commit -m "feat: register EndPortalGeneratorListener and add giveportal command"
```

---

### Task 5: Build and verify

**Files:**
- Modify: none (build only)

- [ ] **Step 1: Build the project**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run server for manual verification**

```bash
./gradlew runServer
```

In-game tests:
1. `/starmskyblockgameplay giveportal <yourname>` — should receive a named EYE_OF_ENDER
2. Right-click on a block with the item — should generate an unactivated End Portal at Y+1
3. Right-click on a ground block where the area above is obstructed — should get "blocked" message
4. `/starmskyblockgameplay reload` — config changes should apply
5. Verify tab completion works for `/starmskyblockgameplay giveportal `

- [ ] **Step 3: Final commit of any build fix changes**

```bash
git commit -am "fix: address build issues"
```
