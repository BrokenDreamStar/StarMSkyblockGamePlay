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
