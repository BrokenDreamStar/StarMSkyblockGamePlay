package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.Map;

public class EndPortalGeneratorListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;
    private final NamespacedKey portalKey;

    // Frame positions relative to center (12 positions, corners excluded)
    // Each entry: {dx, dy, dz} (all dy=0 since frames are all at center Y)
    private static final int[][] FRAME_OFFSETS = {
        // x=-2 side (facing EAST)
        {-2, 0, -1}, {-2, 0, 0}, {-2, 0, 1},
        // z=-2 side (facing SOUTH)
        {-1, 0, -2}, {0, 0, -2}, {1, 0, -2},
        // z=+2 side (facing NORTH)
        {-1, 0, 2}, {0, 0, 2}, {1, 0, 2},
        // x=+2 side (facing WEST)
        {2, 0, -1}, {2, 0, 0}, {2, 0, 1},
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

        // Main hand only
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Only allow in the Overworld
        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) {
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize(lang.getColored("end-portal-generator.world-restricted",
                            Map.of("item-name", plugin.getConfig().getString("end-portal-generator.item-name", "")))));
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) return;

        // Center: clicked block location + (0.5, 1, 0.5)
        Location center = clickedBlock.getLocation().add(0.5, 1, 0.5);

        // Check all 12 frame positions are within world bounds and clear
        var world = clickedBlock.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();
        for (int[] offset : FRAME_OFFSETS) {
            int frameY = center.getBlockY() + offset[1];
            if (frameY < minY || frameY >= maxY) {
                player.sendMessage(lang.getColored("end-portal-generator.blocked"));
                return;
            }
            Block frameBlock = center.clone().add(offset[0], offset[1], offset[2]).getBlock();
            if (!isReplaceable(frameBlock)) {
                player.sendMessage(lang.getColored("end-portal-generator.blocked"));
                return;
            }
        }

        // Cancel the event so interactive blocks (chest, door, etc.) don't also open
        event.setCancelled(true);

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

        // Consume 1 item (only in survival/adventure mode)
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        player.sendMessage(lang.getColored("end-portal-generator.generated"));
    }

    /**
     * Check if a block can be replaced (air, water, cave_air, or other non-solid blocks).
     * Excludes lava and other dangerous liquids.
     */
    private boolean isReplaceable(Block block) {
        if (block.isEmpty()) return true;
        Material type = block.getType();
        if (type == Material.LAVA) return false;
        return type == Material.WATER || Tag.REPLACEABLE_BY_TREES.isTagged(type);
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
