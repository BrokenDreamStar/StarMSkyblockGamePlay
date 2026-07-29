package team.starm.starMSkyblockGamePlay;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class LanguageManager {

    private final StarMSkyblockGamePlay plugin;
    private FileConfiguration messagesConfig;
    private final File messageFile;

    public LanguageManager(StarMSkyblockGamePlay plugin) {
        this.plugin = plugin;
        this.messageFile = new File(plugin.getDataFolder(), "messages.yml");
        loadMessages();
    }

    /**
     * 从插件数据目录加载 messages.yml，不存在则从资源文件复制默认版本。
     */
    private void loadMessages() {
        if (!messageFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messagesConfig = YamlConfiguration.loadConfiguration(messageFile);

        // 合并资源中的默认值，确保新增的键在升级后自动补充
        try (InputStreamReader defaultsReader = new InputStreamReader(
                plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(defaultsReader);
            messagesConfig.setDefaults(defaults);
            messagesConfig.options().copyDefaults(true);
        } catch (IOException e) {
            plugin.getLogger().warning("无法读取默认 messages.yml: " + e.getMessage());
        }

        saveMessages();
    }

    private void saveMessages() {
        try {
            messagesConfig.save(messageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("无法保存 messages.yml: " + e.getMessage());
        }
    }

    /**
     * 获取原始消息字符串（不翻译颜色代码）。
     */
    public String getRaw(String path) {
        return messagesConfig.getString(path, "<missing key: " + path + ">");
    }

    /**
     * 获取已翻译颜色代码的消息字符串。
     */
    public String getColored(String path) {
        return translateColors(getRaw(path));
    }

    /**
     * 获取已翻译颜色代码并替换占位符的消息字符串。
     *
     * @param path        YAML 路径
     * @param placeholders 占位符映射，如 Map.of("player", "Steve")
     */
    public String getColored(String path, Map<String, String> placeholders) {
        String message = getRaw(path);
        if (placeholders != null) {
            for (var entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return translateColors(message);
    }

    /**
     * 从磁盘重新加载 messages.yml。
     */
    public void reloadMessages() {
        messagesConfig = YamlConfiguration.loadConfiguration(messageFile);

        try (InputStreamReader defaultsReader = new InputStreamReader(
                plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(defaultsReader);
            messagesConfig.setDefaults(defaults);
            messagesConfig.options().copyDefaults(true);
        } catch (IOException e) {
            plugin.getLogger().warning("无法读取默认 messages.yml: " + e.getMessage());
        }
    }

    /**
     * 将 & 符号颜色代码转换为 Minecraft 的 § 颜色代码。
     */
    private String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
