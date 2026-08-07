package team.starm.starMSkyblockGamePlay.listener;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.SculkShrieker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

public class SculkShriekerListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;

    public SculkShriekerListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    /**
     * 使用回响碎片右键幽匿尖啸体，将其 can_summon 值设为 true（使其可召唤监守者）。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("sculk-shrieker.enabled", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ECHO_SHARD) return;

        // 仅主手
        if (event.getHand() != EquipmentSlot.HAND) return;

        var clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.SCULK_SHRIEKER) return;

        BlockData blockData = clickedBlock.getBlockData();
        if (!(blockData instanceof SculkShrieker shrieker)) return;

        if (shrieker.isCanSummon()) {
            player.sendActionBar(LegacyComponentSerializer.legacySection()
                    .deserialize(lang.getColored("sculk-shrieker.already-activated")));
            return;
        }

        shrieker.setCanSummon(true);
        clickedBlock.setBlockData(shrieker, false);

        // 取消交互事件，防止主副手双重触发
        event.setCancelled(true);

        // 消耗 1 个回响碎片（创造模式不消耗）
        if (player.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }

        player.sendActionBar(LegacyComponentSerializer.legacySection()
                .deserialize(lang.getColored("sculk-shrieker.activated")));
    }
}
