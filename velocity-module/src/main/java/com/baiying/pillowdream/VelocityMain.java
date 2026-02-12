package com.baiying.pillowdream;

import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Logger;

@Plugin(
        id = "pillowdream_joinmass",
        name = "PillowDream_joinmass",
        version = "1.0.0",
        description = "同步群组服进退服消息",
        authors = {"BaiYing"}
)
public class VelocityMain {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDir;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDb;
    private String mysqlUser;
    private String mysqlPwd;
    private String pluginChannel;

    @Inject
    public VelocityMain(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;

        loadConfig();
        initPlugin();

        logger.info("PillowDream_joinmass (Velocity) 插件启动成功！作者：BaiYing");
    }

    private void loadConfig() {
        File configFile = dataDir.resolve("config.toml").toFile();
        if (!configFile.exists()) {
            try {
                Files.createDirectories(dataDir);
                String defaultConfig = """
                        [mysql]
                        host = "localhost"
                        port = 3306
                        database = "mc_groupchat"
                        username = "root"
                        password = "your_mysql_password"
                        pool_size = 10

                        plugin_message_channel = "pillowdream:joinmass"
                        """;
                Files.write(configFile.toPath(), defaultConfig.getBytes());
            } catch (IOException e) {
                logger.severe("创建配置文件失败：" + e.getMessage());
                return;
            }
        }

        try {
            for (String line : Files.readAllLines(configFile.toPath())) {
                line = line.trim();
                if (line.startsWith("host = ")) mysqlHost = line.split("=")[1].replace("\"", "").trim();
                if (line.startsWith("port = ")) mysqlPort = Integer.parseInt(line.split("=")[1].trim());
                if (line.startsWith("database = ")) mysqlDb = line.split("=")[1].replace("\"", "").trim();
                if (line.startsWith("username = ")) mysqlUser = line.split("=")[1].replace("\"", "").trim();
                if (line.startsWith("password = ")) mysqlPwd = line.split("=")[1].replace("\"", "").trim();
                if (line.startsWith("plugin_message_channel = ")) pluginChannel = line.split("=")[1].replace("\"", "").trim();
            }
        } catch (Exception e) {
            logger.severe("加载配置失败：" + e.getMessage());
        }
    }

    private void initPlugin() {
        try {
            Class<?> eventManagerClass = Class.forName("com.velocitypowered.api.event.EventManager");
            Object eventManager = proxy.getClass().getMethod("getEventManager").invoke(proxy);
            Class<?> postLoginClass = Class.forName("com.velocitypowered.api.event.connection.PostLoginEvent");
            Class<?> disconnectClass = Class.forName("com.velocitypowered.api.event.connection.DisconnectEvent");

            java.lang.reflect.Method registerMethod = eventManagerClass.getMethod("register", Object.class, Class.class, java.util.function.Consumer.class);
            
            registerMethod.invoke(eventManager, this, postLoginClass, (java.util.function.Consumer<Object>) event -> {
                try {
                    Object player = event.getClass().getMethod("getPlayer").invoke(event);
                    UUID uuid = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
                    String name = (String) player.getClass().getMethod("getUsername").invoke(player);

                    updateMySQL(uuid, name, true);
                    sendPluginMessage(uuid, name, "login");
                    logger.info("玩家 " + name + " 登录代理，已同步状态");
                } catch (Exception e) {
                    logger.severe("处理登录事件失败：" + e.getMessage());
                }
            });
            
            registerMethod.invoke(eventManager, this, disconnectClass, (java.util.function.Consumer<Object>) event -> {
                try {
                    Object player = event.getClass().getMethod("getPlayer").invoke(event);
                    UUID uuid = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
                    String name = (String) player.getClass().getMethod("getUsername").invoke(player);

                    updateMySQL(uuid, name, false);
                    sendPluginMessage(uuid, name, "logout");
                    logger.info("玩家 " + name + " 断开代理，已同步状态");
                } catch (Exception e) {
                    logger.severe("处理断开事件失败：" + e.getMessage());
                }
            });

            if (pluginChannel != null && pluginChannel.contains(":")) {
                String[] parts = pluginChannel.split(":", 2);
                Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
                Object channel = channelClass.getMethod("create", String.class, String.class).invoke(null, parts[0], parts[1]);
                Object registrar = proxy.getClass().getMethod("getChannelRegistrar").invoke(proxy);
                registrar.getClass().getMethod("register", channelClass).invoke(registrar, channel);
            }
        } catch (Exception e) {
            logger.severe("初始化插件失败：" + e.getMessage());
        }
    }

    private void updateMySQL(UUID uuid, String name, boolean isOnline) {
        if (mysqlHost == null || mysqlDb == null || mysqlUser == null) return;
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDb + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                mysqlUser, mysqlPwd)) {
            if (isOnline) {
                String sql = "INSERT INTO mc_player_online_status (uuid, username, is_online) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE username=?, is_online=1";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.setString(2, name);
                    pstmt.setString(3, name);
                    pstmt.executeUpdate();
                }
            } else {
                String sql = "UPDATE mc_player_online_status SET is_online=0 WHERE uuid=?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, uuid.toString());
                    pstmt.executeUpdate();
                }
            }
        } catch (SQLException e) {
            logger.severe("更新MySQL失败：" + e.getMessage());
        }
    }

    private void sendPluginMessage(UUID uuid, String name, String type) {
        if (pluginChannel == null || !pluginChannel.contains(":")) return;
        try {
            String[] parts = pluginChannel.split(":", 2);
            Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
            Object channel = channelClass.getMethod("create", String.class, String.class).invoke(null, parts[0], parts[1]);
            String msg = type + "|" + uuid + "|" + name;
            byte[] msgBytes = msg.getBytes();

            Object servers = proxy.getClass().getMethod("getAllServers").invoke(proxy);
            Class<?> serverClass = Class.forName("com.velocitypowered.api.proxy.server.RegisteredServer");
            java.lang.reflect.Method sendMethod = serverClass.getMethod("sendPluginMessage", channelClass, byte[].class);

            for (Object server : (java.lang.Iterable<?>) servers) {
                sendMethod.invoke(server, channel, msgBytes);
            }
        } catch (Exception e) {
            logger.severe("发送PluginMessage失败：" + e.getMessage());
        }
    }

    public void onDisable() {
        logger.info("PillowDream_joinmass (Velocity) 插件已关闭！作者：BaiYing");
    }
}
