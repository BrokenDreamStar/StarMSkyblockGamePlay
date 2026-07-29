package team.starm.starMSkyblockGamePlay.listener;

import org.bukkit.Material;
import org.bukkit.block.Vault;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import team.starm.starMSkyblockGamePlay.LanguageManager;
import team.starm.starMSkyblockGamePlay.StarMSkyblockGamePlay;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class VaultListener implements Listener {

    private final StarMSkyblockGamePlay plugin;
    private final LanguageManager lang;
    private final File dataFile;
    private FileConfiguration dataConfig;

    public VaultListener(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.lang = plugin.getLanguageManager();
        this.dataFile = new File(plugin.getDataFolder(), "vault-data.yml");
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe(lang.getRaw("logger.vault-data-create-error")
                        .replace("{error}", e.getMessage()));
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveData() {
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe(lang.getRaw("logger.vault-data-save-error")
                        .replace("{error}", e.getMessage()));
        }
    }

    private String getCurrentCycle(String configPath) {
        String timeStr = plugin.getConfig().getString(configPath + ".reset-time", "04:00");
        LocalTime resetTime;
        try {
            resetTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
        } catch (DateTimeParseException e) {
            resetTime = LocalTime.of(4, 0);
        }
        LocalDate date = LocalDate.now();
        if (LocalTime.now().isBefore(resetTime)) {
            date = date.minusDays(1);
        }
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @EventHandler
    public void onVaultInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        var clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.VAULT) return;

        Player player = event.getPlayer();
        if (player.isSneaking()) return;

        ItemStack usedItem = event.getItem();
        if (usedItem == null || usedItem.getType().isAir()) return;

        Vault vault = (Vault) clickedBlock.getState();

        if (usedItem.getType() == Material.COPPER_BLOCK) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);

            ItemStack keyItem = vault.getKeyItem();
            boolean isOminous = keyItem != null && keyItem.getType() == Material.OMINOUS_TRIAL_KEY;
            String configPath = isOminous ? "ominous-vault" : "vault";
            String dataPrefix = isOminous ? "ominous-" : "";

            if (plugin.getConfig().getBoolean(configPath + ".enabled", true)) {
                handleCopperRemoval(event, vault, isOminous, configPath, dataPrefix);
            }
            return;
        }
    }

    private void handleCopperRemoval(PlayerInteractEvent event, Vault vault, boolean isOminous, String configPath, String dataPrefix) {
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        if (!vault.hasRewardedPlayer(player.getUniqueId())) {
            String vaultType = lang.getColored(isOminous ? "vault-types.ominous" : "vault-types.normal");
            player.sendActionBar(lang.getColored("vault.not-in-blacklist", Map.of("vault_type", vaultType)));
            return;
        }

        int removalLimit = plugin.getConfig().getInt(configPath + ".blacklist-removal-limit", 5);

        if (removalLimit > 0) {
            String basePath = "players." + player.getUniqueId() + "." + dataPrefix + "vault";
            String currentCycle = getCurrentCycle(configPath);
            String storedCycle = dataConfig.getString(basePath + ".cycle", "");

            int removalCount;
            if (!currentCycle.equals(storedCycle)) {
                removalCount = 0;
                dataConfig.set(basePath + ".cycle", currentCycle);
                dataConfig.set(basePath + ".removals", 0);
            } else {
                removalCount = dataConfig.getInt(basePath + ".removals", 0);
            }

            if (removalCount >= removalLimit) {
                String vaultType = lang.getColored(isOminous ? "vault-types.ominous" : "vault-types.normal");
                player.sendActionBar(lang.getColored("vault.limit-reached", Map.of(
                        "vault_type", vaultType,
                        "count", String.valueOf(removalCount),
                        "limit", String.valueOf(removalLimit)
                )));
                return;
            }

            dataConfig.set(basePath + ".removals", removalCount + 1);
            saveData();
        }

        if (mainHand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            mainHand.setAmount(mainHand.getAmount() - 1);
        }

        vault.removeRewardedPlayer(player.getUniqueId());
        vault.update();

        String vaultType = lang.getColored(isOminous ? "vault-types.ominous" : "vault-types.normal");
        player.sendActionBar(lang.getColored("vault.removed", Map.of("vault_type", vaultType)));
    }

    public void startResetTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkReset("vault", "");
                checkReset("ominous-vault", "ominous-");
            }
        }.runTaskTimer(plugin, 200L, 600L);
    }

    private void checkReset(String configPath, String dataPrefix) {
        String currentCycle = getCurrentCycle(configPath);
        String lastReset = dataConfig.getString("last-reset." + configPath, "");

        if (!currentCycle.equals(lastReset)) {
            var playersSection = dataConfig.getConfigurationSection("players");
            if (playersSection != null) {
                for (String uuidStr : playersSection.getKeys(false)) {
                    dataConfig.set("players." + uuidStr + "." + dataPrefix + "vault", null);
                }
            }
            dataConfig.set("last-reset." + configPath, currentCycle);
            saveData();
            plugin.getLogger().info(lang.getRaw("logger.vault-daily-reset")
                    .replace("{vault_name}", configPath));
        }
    }
}
