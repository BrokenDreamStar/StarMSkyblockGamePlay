package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Material;
import org.bukkit.block.TrialSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.Map;

public class TrialSpawnerListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;

    public TrialSpawnerListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
    }

    /**
     * 阻止玩家在试炼刷怪笼上使用刷怪蛋
     */
    @EventHandler
    public void onPreventSpawnEgg(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("trial-spawner.prevent-ominous-spawn-egg", true)) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        var clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.TRIAL_SPAWNER) return;

        ItemStack item = event.getItem();
        if (item == null || !item.getType().name().endsWith("_SPAWN_EGG")) return;

        event.setCancelled(true);
        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.getConfig().getBoolean("trial-spawner.enabled", true)) return;

        var clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.TRIAL_SPAWNER) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != Material.COPPER_BLOCK) return;

        TrialSpawner spawner = (TrialSpawner) clickedBlock.getState();
        long cooldownEnd = spawner.getCooldownEnd();
        if (cooldownEnd <= 0) {
            player.sendMessage(lang.getColored("trial-spawner.not-in-cooldown"));
            return;
        }

        int reduction = plugin.getConfig().getInt("trial-spawner.cooldown-reduction-ticks", 6000);
        long worldTime = clickedBlock.getWorld().getGameTime();
        long newCooldownEnd = Math.max(cooldownEnd - reduction, worldTime);

        spawner.setCooldownEnd(newCooldownEnd);
        spawner.update();

        if (item.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(item.getAmount() - 1);
        }

        event.setCancelled(true);

        if (newCooldownEnd <= worldTime + 1) {
            player.sendMessage(lang.getColored("trial-spawner.cooldown-ended"));
        } else {
            long remainingSeconds = (newCooldownEnd - worldTime) / 20;
            player.sendMessage(lang.getColored("trial-spawner.cooldown-reduced", Map.of(
                    "reduction_seconds", String.valueOf(reduction / 20),
                    "remaining_seconds", String.valueOf(remainingSeconds)
            )));
        }
    }
}
