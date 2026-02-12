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

        // 1. 加载配置
        loadConfig();
        
        // 2. 仅适配Velocity 3.4.x的事件/通道注册
        registerEventsFor34x();
        registerChannelFor34x();

        logger.info("PillowDream_joinmass (Velocity) 插件启动成功！作者：BaiYing");
    }

    // 加载配置（保持不变）
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

    // 适配Velocity 3.4.x的事件注册（PlayerDisconnectedEvent + 正确方法签名）
    private void registerEventsFor34x() {
        try {
            // 1. 获取EventManager
            Class<?> eventManagerClass = Class.forName("com.velocitypowered.api.event.EventManager");
            Object eventManager = proxy.getClass().getMethod("getEventManager").invoke(proxy);

            // 2. 3.4.x的事件类：PlayerDisconnectedEvent回到connection包
            Class<?> postLoginEventClass = Class.forName("com.velocitypowered.api.event.connection.PostLoginEvent");
            Class<?> disconnectEventClass = Class.forName("com.velocitypowered.api.event.connection.PlayerDisconnectedEvent");

            // 3. 3.4.x的register方法签名：(Object plugin, Consumer<T> listener)
            // 先注册登录事件
            eventManagerClass.getMethod("register", Object.class, Class.class, java.util.function.Consumer.class)
                    .invoke(eventManager, this, postLoginEventClass, (java.util.function.Consumer<Object>) this::onPlayerLogin);
            // 注册断开事件（替代原LeaveEvent）
            eventManagerClass.getMethod("register", Object.class, Class.class, java.util.function.Consumer.class)
                    .invoke(eventManager, this, disconnectEventClass, (java.util.function.Consumer<Object>) this::onPlayerDisconnect);

            logger.info("✅ Velocity 3.4.x事件注册成功！");
        } catch (ClassNotFoundException e) {
            logger.severe("❌ 事件类找不到：" + e.getMessage());
            logger.severe("  请确认Velocity版本为3.4.x，或检查事件类路径是否正确");
        } catch (NoSuchMethodException e) {
            logger.severe("❌ 事件注册方法找不到：" + e.getMessage());
            logger.severe("  方法签名不匹配，当前Velocity版本可能不是3.4.x");
        } catch (Exception e) {
            logger.severe("❌ 注册事件失败：" + e.getMessage());
            for (StackTraceElement elem : e.getStackTrace()) {
                logger.severe("  " + elem);
            }
        }
    }

    // 适配Velocity 3.4.x的通道注册（仅单参数register）
    private void registerChannelFor34x() {
        if (pluginChannel == null || !pluginChannel.contains(":")) {
            logger.severe("❌ 插件通道配置错误，格式应为 namespace:name，当前：" + pluginChannel);
            return;
        }

        try {
            // 1. 分割通道名
            String[] channelParts = pluginChannel.split(":", 2);
            // 2. 创建ChannelIdentifier
            Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
            Object channel = channelClass.getMethod("create", String.class, String.class)
                    .invoke(null, channelParts[0], channelParts[1]);
            // 3. 3.4.x的ChannelRegistrar仅支持单参数register
            Object channelRegistrar = proxy.getClass().getMethod("getChannelRegistrar").invoke(proxy);
            channelRegistrar.getClass().getMethod("register", channelClass).invoke(channelRegistrar, channel);

            logger.info("✅ 插件通道注册成功：" + pluginChannel);
        } catch (NoSuchMethodException e) {
            logger.severe("❌ 通道注册方法找不到：" + e.getMessage());
            logger.severe("  当前Velocity版本不支持单参数register，确认版本为3.4.x");
        } catch (Exception e) {
            logger.severe("❌ 注册通道失败：" + e.getMessage());
            for (StackTraceElement elem : e.getStackTrace()) {
                logger.severe("  " + elem);
            }
        }
    }

    // 玩家登录事件处理（不变）
    private void onPlayerLogin(Object event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            UUID uuid = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
            String name = (String) player.getClass().getMethod("getUsername").invoke(player);

            updateMySQL(uuid, name, true);
            sendPluginMessage(uuid, name, "login");

            logger.info("👤 玩家 " + name + " 登录代理，状态已同步");
        } catch (Exception e) {
            logger.severe("❌ 处理登录事件失败：" + e.getMessage());
        }
    }

    // 玩家断开事件处理（适配3.4.x的PlayerDisconnectedEvent）
    private void onPlayerDisconnect(Object event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            UUID uuid = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
            String name = (String) player.getClass().getMethod("getUsername").invoke(player);

            updateMySQL(uuid, name, false);
            sendPluginMessage(uuid, name, "logout");

            logger.info("👤 玩家 " + name + " 断开代理，状态已同步");
        } catch (Exception e) {
            logger.severe("❌ 处理断开事件失败：" + e.getMessage());
        }
    }

    // MySQL状态更新（不变）
    private void updateMySQL(UUID uuid, String name, boolean isOnline) {
        if (mysqlHost == null || mysqlDb == null || mysqlUser == null) {
            logger.severe("❌ MySQL配置未加载，跳过状态更新");
            return;
        }

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
            logger.severe("❌ MySQL更新失败：" + e.getMessage());
        }
    }

    // 发送PluginMessage到子服（不变）
    private void sendPluginMessage(UUID uuid, String name, String type) {
        if (pluginChannel == null || !pluginChannel.contains(":")) {
            logger.severe("❌ 插件通道配置错误，跳过消息发送");
            return;
        }

        try {
            String[] channelParts = pluginChannel.split(":", 2);
            String msg = type + "|" + uuid + "|" + name;
            byte[] msgBytes = msg.getBytes();

            Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
            Object channel = channelClass.getMethod("create", String.class, String.class)
                    .invoke(null, channelParts[0], channelParts[1]);

            Object servers = proxy.getClass().getMethod("getAllServers").invoke(proxy);
            Class<?> serverClass = Class.forName("com.velocitypowered.api.proxy.server.RegisteredServer");
            for (Object server : (java.lang.Iterable<?>) servers) {
                serverClass.getMethod("sendPluginMessage", channelClass, byte[].class)
                        .invoke(server, channel, msgBytes);
            }
        } catch (Exception e) {
            logger.severe("❌ 发送PluginMessage失败：" + e.getMessage());
        }
    }

    // 插件关闭
    public void onDisable() {
        logger.info("🔌 PillowDream_joinmass (Velocity) 插件已关闭！作者：BaiYing");
    }
}
