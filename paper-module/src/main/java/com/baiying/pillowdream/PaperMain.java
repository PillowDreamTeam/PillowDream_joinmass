package com.baiying.pillowdream;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class PaperMain extends JavaPlugin implements Listener, PluginMessageListener {
    private String pluginMessageChannel;
    private String loginMessage;
    private String logoutMessage;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();
        FileConfiguration config = getConfig();

        // 加载配置
        pluginMessageChannel = config.getString("plugin_message_channel");
        loginMessage = config.getString("login_message");
        logoutMessage = config.getString("logout_message");

        // 注册事件和通道
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(Bukkit.getMessenger()).registerIncomingPluginChannel(this, pluginMessageChannel, this);
        Bukkit.getMessenger().registerOutgoingPluginChannel(this, pluginMessageChannel);

        getLogger().info("PillowDream_joinmass (Paper) 插件启动成功！作者：BaiYing");
    }

    @Override
    public void onDisable() {
        Bukkit.getMessenger().unregisterIncomingPluginChannel(this);
        Bukkit.getMessenger().unregisterOutgoingPluginChannel(this);
        getLogger().info("PillowDream_joinmass (Paper) 插件已关闭！");
    }

    // 取消子服默认登录消息
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
    }

    // 取消子服默认退出消息
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage(null);
    }

    // 接收Velocity的消息
    @Override
    public void onPluginMessageReceived(String channel, Player ignored, byte[] messageBytes) {
        if (!channel.equals(pluginMessageChannel)) return;

        String message = new String(messageBytes, StandardCharsets.UTF_8);
        String[] parts = message.split("\\|");
        if (parts.length != 3) {
            getLogger().warning("无效消息：" + message);
            return;
        }

        String type = parts[0];
        String playerName = parts[2];

        Bukkit.getScheduler().runTask(this, () -> {
            String broadcastMsg;
            switch (type) {
                case "login":
                    broadcastMsg = loginMessage.replace("{player}", playerName);
                    break;
                case "logout":
                    broadcastMsg = logoutMessage.replace("{player}", playerName);
                    break;
                default:
                    getLogger().warning("未知类型：" + type);
                    return;
            }

            // 广播消息
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(broadcastMsg);
            }
            getLogger().info("广播：" + broadcastMsg);
        });
    }
}
