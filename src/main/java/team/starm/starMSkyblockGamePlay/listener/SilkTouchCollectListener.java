package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SilkTouchCollectListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final NamespacedKey ominousKey;
    /** Single-item stack cache (item consumed before BlockPlaceEvent). */
    private final Map<UUID, Boolean> pendingOminous = new HashMap<>();

    public SilkTouchCollectListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.ominousKey = new NamespacedKey(plugin, "silk_ominous");
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Boolean ominous = pdc.get(ominousKey, PersistentDataType.BOOLEAN);
        if (ominous != null) {
            pendingOminous.put(event.getPlayer().getUniqueId(), ominous);
            new BukkitRunnable() {
                @Override
                public void run() {
                    pendingOminous.remove(event.getPlayer().getUniqueId(), ominous);
                }
            }.runTask(plugin);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("silk-touch-collectibles.enabled", true)) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool.getType().isAir() || !tool.containsEnchantment(Enchantment.SILK_TOUCH)) return;

        List<String> collectibleBlocks = plugin.getConfig().getStringList("silk-touch-collectibles.blocks");
        if (!collectibleBlocks.contains(block.getType().name())) return;

        BlockState state = block.getState();

        event.setDropItems(false);
        event.setExpToDrop(0);

        ItemStack dropItem = new ItemStack(block.getType());

        // Only attach block entity NBT if this block has one (e.g. SPAWNER, VAULT).
        // Blocks like BUDDING_AMETHYST have no TileState — just drop the item stack.
        if (state instanceof TileState ts) {
            dropItem.editMeta(meta -> {
                // Full block entity NBT (auto-restored by Minecraft on place)
                if (meta instanceof BlockStateMeta blockStateMeta) {
                    blockStateMeta.setBlockState(ts);
                }
                // Ominous vault state — BlockDataMeta overrides facing, so store
                // as a minimal PDC flag and apply manually in onBlockPlace.
                BlockData blockData = block.getBlockData();
                if (blockData instanceof org.bukkit.block.data.type.Vault vaultData
                    && vaultData.isOminous()) {
                    meta.getPersistentDataContainer()
                        .set(ominousKey, PersistentDataType.BOOLEAN, true);
                }
            });
        }

        block.getWorld().dropItem(block.getLocation().add(0.5, 0.5, 0.5), dropItem);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        ItemStack item = event.getItemInHand();
        UUID playerId = event.getPlayer().getUniqueId();

        // 1) Cache (single-item stacks)
        Boolean ominous = pendingOminous.remove(playerId);

        // 2) Item still in hand (multi-item stacks)
        if (ominous == null && item.hasItemMeta()) {
            ominous = item.getItemMeta().getPersistentDataContainer()
                .get(ominousKey, PersistentDataType.BOOLEAN);
        }

        if (ominous != null && ominous) {
            BlockData blockData = block.getBlockData();
            if (blockData instanceof org.bukkit.block.data.type.Vault vaultData) {
                vaultData.setOminous(true);
                block.setBlockData(vaultData);
            }
            // Clean up the PDC tag that Minecraft copied to the block entity.
            scheduleOminousCleanup(block);
        }
    }

    /** Removes the silk_ominous key from the block's PDC after placement. */
    private void scheduleOminousCleanup(Block block) {
        new BukkitRunnable() {
            @Override
            public void run() {
                BlockState state = block.getState();
                if (state instanceof TileState ts) {
                    PersistentDataContainer pdc = ts.getPersistentDataContainer();
                    if (pdc.has(ominousKey, PersistentDataType.BOOLEAN)) {
                        pdc.remove(ominousKey);
                        ts.update(true, false);
                    }
                }
            }
        }.runTask(plugin);
    }
}
