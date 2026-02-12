package com.baiying.pillowdream;

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

// 运行时动态绑定Velocity API，编译时无需依赖
public class VelocityMain {
    private Object proxy; // Velocity ProxyServer实例
    private Logger logger;
    private Path dataDir;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDb;
    private String mysqlUser;
    private String mysqlPwd;
    private String pluginChannel;

    // 插件初始化方法（Velocity调用）
    public void onEnable(Object proxy, Logger logger, Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;

        // 加载配置
        loadConfig();
        // 注册事件监听（反射）
        registerEvents();
        // 注册PluginMessage通道（反射）
        registerChannel();

        logger.info("PillowDream_joinmass (Velocity) 插件启动成功！作者：BaiYing");
    }

    // 加载配置（简化版，无toml依赖）
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

        // 简化配置读取（按行解析）
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

    // 注册事件监听（反射调用Velocity API）
    private void registerEvents() {
        try {
            Class<?> eventManagerClass = Class.forName("com.velocitypowered.api.event.EventManager");
            Object eventManager = proxy.getClass().getMethod("getEventManager").invoke(proxy);
            eventManagerClass.getMethod("register", Object.class, Class.class, Object.class)
                    .invoke(eventManager, this, Class.forName("com.velocitypowered.api.event.connection.PostLoginEvent"), 
                            (java.util.function.Consumer<Object>) this::onPlayerLogin);
            eventManagerClass.getMethod("register", Object.class, Class.class, Object.class)
                    .invoke(eventManager, this, Class.forName("com.velocitypowered.api.event.player.PlayerLeaveEvent"), 
                            (java.util.function.Consumer<Object>) this::onPlayerLeave);
        } catch (Exception e) {
            logger.severe("注册事件失败：" + e.getMessage());
        }
    }

    // 注册PluginMessage通道（反射）
    private void registerChannel() {
        try {
            Object channelRegistrar = proxy.getClass().getMethod("getChannelRegistrar").invoke(proxy);
            Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
            Object channel = channelClass.getMethod("create", String.class, String.class)
                    .invoke(null, pluginChannel.split(":")[0], pluginChannel.split(":")[1]);
            channelRegistrar.getClass().getMethod("register", channelClass).invoke(channelRegistrar, channel);
        } catch (Exception e) {
            logger.severe("注册通道失败：" + e.getMessage());
        }
    }

    // 玩家登录事件（反射处理）
    private void onPlayerLogin(Object event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            UUID uuid = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
            String name = (String) player.getClass().getMethod("getUsername").invoke(player);

            // 更新MySQL
            updateMySQL(uuid, name, true);
            // 发送PluginMessage
            sendPluginMessage(uuid, name, "login");

            logger.info("玩家 " + name + " 登录代理，已同步状态");
        } catch (Exception e) {
            logger.severe("处理登录事件失败：" + e.getMessage());
        }
    }

    // 玩家退出事件（反射处理）
    private void onPlayerLeave(Object event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            UUID uuid = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
            String name = (String) player.getClass().getMethod("getUsername").invoke(player);

            // 更新MySQL
            updateMySQL(uuid, name, false);
            // 发送PluginMessage
            sendPluginMessage(uuid, name, "logout");

            logger.info("玩家 " + name + " 断开代理，已同步状态");
        } catch (Exception e) {
            logger.severe("处理退出事件失败：" + e.getMessage());
        }
    }

    // 更新MySQL状态（简化版，无连接池）
    private void updateMySQL(UUID uuid, String name, boolean isOnline) {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDb + "?useSSL=false&serverTimezone=UTC",
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

    // 发送PluginMessage到所有子服（反射）
    private void sendPluginMessage(UUID uuid, String name, String type) {
        try {
            String msg = type + "|" + uuid + "|" + name;
            byte[] msgBytes = msg.getBytes();

            // 获取所有子服
            Object servers = proxy.getClass().getMethod("getAllServers").invoke(proxy);
            Class<?> serverClass = Class.forName("com.velocitypowered.api.proxy.server.RegisteredServer");
            Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
            Object channel = channelClass.getMethod("create", String.class, String.class)
                    .invoke(null, pluginChannel.split(":")[0], pluginChannel.split(":")[1]);

            // 遍历发送
            for (Object server : (java.lang.Iterable<?>) servers) {
                serverClass.getMethod("sendPluginMessage", channelClass, byte[].class)
                        .invoke(server, channel, msgBytes);
            }
        } catch (Exception e) {
            logger.severe("发送PluginMessage失败：" + e.getMessage());
        }
    }

    // 插件关闭方法
    public void onDisable() {
        logger.info("PillowDream_joinmass (Velocity) 插件已关闭！作者：BaiYing");
    }

    // Velocity插件入口（反射绑定）
    public static class EntryPoint {
        public EntryPoint(Object proxy, Logger logger, Path dataDir) {
            new VelocityMain().onEnable(proxy, logger, dataDir);
        }
    }
}
