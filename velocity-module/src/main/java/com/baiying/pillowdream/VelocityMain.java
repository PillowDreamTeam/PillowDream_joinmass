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
        // 修正反射调用逻辑
        registerEventsFixed();
        registerChannelFixed();

        logger.info("PillowDream_joinmass (Velocity) 插件启动成功！作者：BaiYing");
    }

    // 原有loadConfig方法不变，此处省略（保持和之前一致）
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

    // 修复后的事件注册方法（匹配Velocity 3.x真实API签名）
    private void registerEventsFixed() {
        try {
            // 1. 获取EventManager实例（正确）
            Class<?> eventManagerClass = Class.forName("com.velocitypowered.api.event.EventManager");
            Object eventManager = proxy.getClass().getMethod("getEventManager").invoke(proxy);

            // 2. 加载事件类（正确）
            Class<?> postLoginEventClass = Class.forName("com.velocitypowered.api.event.connection.PostLoginEvent");
            Class<?> playerLeaveEventClass = Class.forName("com.velocitypowered.api.event.player.PlayerLeaveEvent");

            // 3. 修正register方法调用：Velocity 3.x的register方法签名是 (Object plugin, Class<T> eventClass, Consumer<T> listener)
            // 方法1：优先尝试标准签名（3.x主流版本）
            try {
                // 注册登录事件
                eventManagerClass.getMethod("register", Object.class, Class.class, java.util.function.Consumer.class)
                        .invoke(eventManager, this, postLoginEventClass, (java.util.function.Consumer<Object>) this::onPlayerLogin);
                // 注册退出事件
                eventManagerClass.getMethod("register", Object.class, Class.class, java.util.function.Consumer.class)
                        .invoke(eventManager, this, playerLeaveEventClass, (java.util.function.Consumer<Object>) this::onPlayerLeave);
                logger.info("事件注册成功（标准签名）！");
            } catch (NoSuchMethodException e) {
                // 方法2：兼容旧版本签名（3.0.x）
                eventManagerClass.getMethod("register", Object.class, Class.class, Object.class)
                        .invoke(eventManager, this, postLoginEventClass, (java.util.function.Consumer<Object>) this::onPlayerLogin);
                eventManagerClass.getMethod("register", Object.class, Class.class, Object.class)
                        .invoke(eventManager, this, playerLeaveEventClass, (java.util.function.Consumer<Object>) this::onPlayerLeave);
                logger.info("事件注册成功（兼容旧版本签名）！");
            }
        } catch (Exception e) {
            // 打印详细异常（包含方法签名/类路径）
            logger.severe("注册事件失败：" + e.getMessage());
            logger.severe("异常详情：");
            for (StackTraceElement elem : e.getStackTrace()) {
                logger.severe("  " + elem.toString());
            }
        }
    }

    // 修复后的通道注册方法（匹配Velocity 3.x真实API签名）
    private void registerChannelFixed() {
        if (pluginChannel == null || pluginChannel.isEmpty()) {
            logger.severe("插件通道配置为空，跳过通道注册！");
            return;
        }

        try {
            // 1. 分割通道名（格式：namespace:name）
            String[] channelParts = pluginChannel.split(":");
            if (channelParts.length != 2) {
                logger.severe("插件通道格式错误，应为 namespace:name，当前：" + pluginChannel);
                return;
            }

            // 2. 创建MinecraftChannelIdentifier实例（正确）
            Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
            Object channel = channelClass.getMethod("create", String.class, String.class)
                    .invoke(null, channelParts[0], channelParts[1]);

            // 3. 获取ChannelRegistrar并注册（修正方法调用）
            Object channelRegistrar = proxy.getClass().getMethod("getChannelRegistrar").invoke(proxy);
            // 优先尝试标准register方法
            try {
                channelRegistrar.getClass().getMethod("register", channelClass).invoke(channelRegistrar, channel);
                logger.info("插件通道注册成功：" + pluginChannel);
            } catch (NoSuchMethodException e) {
                // 兼容旧版本（批量注册）
                channelRegistrar.getClass().getMethod("register", Iterable.class)
                        .invoke(channelRegistrar, java.util.Collections.singletonList(channel));
                logger.info("插件通道注册成功（兼容旧版本）：" + pluginChannel);
            }
        } catch (Exception e) {
            // 打印详细异常
            logger.severe("注册通道失败：" + e.getMessage());
            logger.severe("异常详情：");
            for (StackTraceElement elem : e.getStackTrace()) {
                logger.severe("  " + elem.toString());
            }
        }
    }

    // 原有onPlayerLogin方法不变，此处省略
    private void onPlayerLogin(Object event) {
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
    }

    // 原有onPlayerLeave方法不变，此处省略
    private void onPlayerLeave(Object event) {
        try {
            Object player = event.getClass().getMethod("getPlayer").invoke(event);
            UUID uuid = (UUID) player.getClass().getMethod("getUniqueId").invoke(player);
            String name = (String) player.getClass().getMethod("getUsername").invoke(player);

            updateMySQL(uuid, name, false);
            sendPluginMessage(uuid, name, "logout");

            logger.info("玩家 " + name + " 断开代理，已同步状态");
        } catch (Exception e) {
            logger.severe("处理退出事件失败：" + e.getMessage());
        }
    }

    // 原有updateMySQL方法不变，此处省略
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

    // 原有sendPluginMessage方法不变，此处省略
    private void sendPluginMessage(UUID uuid, String name, String type) {
        try {
            String msg = type + "|" + uuid + "|" + name;
            byte[] msgBytes = msg.getBytes();

            Object servers = proxy.getClass().getMethod("getAllServers").invoke(proxy);
            Class<?> serverClass = Class.forName("com.velocitypowered.api.proxy.server.RegisteredServer");
            Class<?> channelClass = Class.forName("com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier");
            Object channel = channelClass.getMethod("create", String.class, String.class)
                    .invoke(null, pluginChannel.split(":")[0], pluginChannel.split(":")[1]);

            for (Object server : (java.lang.Iterable<?>) servers) {
                serverClass.getMethod("sendPluginMessage", channelClass, byte[].class)
                        .invoke(server, channel, msgBytes);
            }
        } catch (Exception e) {
            logger.severe("发送PluginMessage失败：" + e.getMessage());
        }
    }

    public void onDisable() {
        logger.info("PillowDream_joinmass (Velocity) 插件已关闭！作者：BaiYing");
    }
}
