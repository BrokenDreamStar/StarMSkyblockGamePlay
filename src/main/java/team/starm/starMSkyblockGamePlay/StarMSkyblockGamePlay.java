package team.starm.starMSkyblockGamePlay;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import team.starm.starMSkyblockGamePlay.listener.SilkTouchCollectListener;
import team.starm.starMSkyblockGamePlay.listener.SnowballConverterListener;
import team.starm.starMSkyblockGamePlay.listener.TrialSpawnerListener;
import team.starm.starMSkyblockGamePlay.listener.VaultListener;

import java.util.Map;

public final class StarMSkyblockGamePlay extends JavaPlugin {

    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.languageManager = new LanguageManager(this);

        getServer().getPluginManager().registerEvents(new TrialSpawnerListener(this), this);
        VaultListener vaultListener = new VaultListener(this);
        getServer().getPluginManager().registerEvents(vaultListener, this);
        vaultListener.startResetTask();
        getServer().getPluginManager().registerEvents(new SilkTouchCollectListener(this), this);
        getServer().getPluginManager().registerEvents(new SnowballConverterListener(this), this);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("starmskyblockgameplay")) {
            return false;
        }

        if (!sender.hasPermission("starmskyblockgameplay.admin")) {
            sender.sendMessage("§c你没有权限执行此命令。");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§c用法: /starmskyblockgameplay reload | givesnowball <玩家> [数量] [概率]");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            languageManager.reloadMessages();
            sender.sendMessage("§aStarMSkyblockGamePlay 配置已重载。");
            getLogger().info("配置与消息已重载（由 " + sender.getName() + " 执行）。");
            return true;
        }

        if (args[0].equalsIgnoreCase("givesnowball")) {
            if (args.length < 2) {
                sender.sendMessage("§c用法: /starmskyblockgameplay givesnowball <玩家> [数量] [概率]");
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

            int chance = 0;
            if (args.length >= 4) {
                try {
                    chance = Math.clamp(Integer.parseInt(args[3]), 0, 100);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§c概率必须是 0-100 的整数。");
                    return true;
                }
            }

            NamespacedKey flagKey = new NamespacedKey(this, "mob_converter");
            NamespacedKey chanceKey = new NamespacedKey(this, "mob_converter_chance");
            final int finalChance = chance;

            ItemStack snowball = new ItemStack(Material.SNOWBALL, amount);
            snowball.editMeta(meta -> {
                meta.getPersistentDataContainer().set(flagKey, PersistentDataType.BOOLEAN, true);
                if (finalChance > 0) {
                    meta.getPersistentDataContainer().set(chanceKey, PersistentDataType.INTEGER, finalChance);
                }
            });

            target.getInventory().addItem(snowball);
            sender.sendMessage(languageManager.getColored("snowball-converter.give-snowball",
                    Map.of("player", target.getName(), "amount", String.valueOf(amount))));
            return true;
        }

        sender.sendMessage("§c未知子命令。用法: /starmskyblockgameplay reload | givesnowball <玩家> [数量] [概率]");
        return true;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public void onDisable() {
    }
}
