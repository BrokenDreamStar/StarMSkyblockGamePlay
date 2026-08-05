package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.util.Map;

/**
 * 铁砧超等级附魔书：允许命令给予的、超过原版最高等级的附魔书（如效率10）被完整应用。
 *
 * 仅在"附魔书或物品的某个附魔等级超过其原版上限"时才修正结果等级（取书与物品的较大等级）。
 * 原版合法的组合（如两本效率5）完全保持原版行为，因此玩家无法自行合成超出原版上限的等级。
 */
public class HighLevelEnchantListener implements Listener {

    private final StarMSkyblockGamePlay plugin;

    public HighLevelEnchantListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!plugin.getConfig().getBoolean("anvil-high-level-enchant.enabled", true)) return;

        AnvilView view = event.getView();
        int maxRepairCost = plugin.getConfig().getInt("anvil-high-level-enchant.max-repair-cost", 40);
        if (maxRepairCost <= 0) {
            maxRepairCost = Integer.MAX_VALUE; // 不设"过于昂贵"上限
        }
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

            // 书与物品的等级都在原版上限内 → 保持原版行为（含截断，如两本效率5仍为效率5）
            if (bookLevel <= ench.getMaxLevel() && itemLevel <= ench.getMaxLevel()) continue;

            // 原版已因不兼容/不适用而拒绝该附魔 → 保持原版拒绝，不强行附加
            if (getLevel(result, ench) <= 0) continue;

            int correctLevel = Math.max(bookLevel, itemLevel); // 不叠加，无法通过合成超出已有最高等级
            if (fixed == null) fixed = result.clone();
            setLevel(fixed, ench, correctLevel);
        }

        if (fixed != null) {
            event.setResult(fixed);
        }
    }

    /** 读取物品附魔等级（附魔书读存储附魔，其余读普通附魔）。 */
    private static int getLevel(ItemStack item, Enchantment ench) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchantLevel(ench);
        }
        return item.getEnchantmentLevel(ench);
    }

    /** 读取物品的附魔集合（附魔书读存储附魔）。 */
    private static Map<Enchantment, Integer> getEnchants(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            return storageMeta.getStoredEnchants();
        }
        return item.getEnchantments();
    }

    /** 写入物品附魔等级（附魔书写存储附魔，其余写普通附魔），忽略等级上限。 */
    private static void setLevel(ItemStack item, Enchantment ench, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta) {
            storageMeta.addStoredEnchant(ench, level, true);
            item.setItemMeta(storageMeta);
        } else {
            meta.addEnchant(ench, level, true);
            item.setItemMeta(meta);
        }
    }
}
