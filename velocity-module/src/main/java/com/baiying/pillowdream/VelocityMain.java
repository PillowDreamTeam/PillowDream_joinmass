package com.baiying.pillowdream;

import com.moandjiezana.toml.Toml;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PlayerDisconnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
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
    private HikariDataSource dataSource;
    private MinecraftChannelIdentifier messageChannel;

    @Inject
    public VelocityMain(ProxyServer proxy, Logger logger, @DataDirectory Path dataDir) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDir = dataDir;
    }

    @Subscribe
    public void onProxyInit(ProxyInitializeEvent event) {
        // 加载配置
        Toml config = loadConfig();
        if (config == null) {
            logger.severe("配置文件加载失败，插件启动失败！");
            return;
        }

        // 初始化MySQL
        initMySQL(config);

        // 注册PluginMessage通道
        String channelName = config.getString("plugin_message_channel");
        messageChannel = MinecraftChannelIdentifier.create(channelName.split(":")[0], channelName.split(":")[1]);
        proxy.getChannelRegistrar().register(messageChannel);

        logger.info("PillowDream_joinmass (Velocity) 插件启动成功！作者：BaiYing");
    }

    // 玩家登录代理
    @Subscribe
    public void onPlayerLogin(PostLoginEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getUsername();

        proxy.getScheduler().buildTask(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "INSERT INTO mc_player_online_status (uuid, username, is_online) VALUES (?, ?, 1) ON DUPLICATE KEY UPDATE username=?, is_online=1";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, playerUuid.toString());
                    pstmt.setString(2, playerName);
                    pstmt.setString(3, playerName);
                    pstmt.executeUpdate();
                }

                // 推送登录消息
                sendPluginMessage(playerUuid, playerName, "login");
                logger.info("玩家 " + playerName + " 登录代理，已同步状态");
            } catch (SQLException e) {
                logger.severe("更新登录状态失败：" + e.getMessage());
            }
        }).schedule();
    }

    // 玩家断开代理
    @Subscribe
    public void onPlayerDisconnect(PlayerDisconnectedEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        String playerName = event.getPlayer().getUsername();

        proxy.getScheduler().buildTask(this, () -> {
            try (Connection conn = dataSource.getConnection()) {
                String sql = "UPDATE mc_player_online_status SET is_online=0 WHERE uuid=?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, playerUuid.toString());
                    pstmt.executeUpdate();
                }

                // 推送退出消息
                sendPluginMessage(playerUuid, playerName, "logout");
                logger.info("玩家 " + playerName + " 断开代理，已同步状态");
            } catch (SQLException e) {
                logger.severe("更新退出状态失败：" + e.getMessage());
            }
        }).schedule();
    }

    // 加载配置文件
    private Toml loadConfig() {
        File configFile = dataDir.resolve("config.toml").toFile();
        if (!configFile.exists()) {
            try {
                Files.createDirectories(dataDir);
                Files.copy(getClass().getResourceAsStream("/config.toml"), configFile.toPath());
            } catch (IOException e) {
                logger.severe("创建配置文件失败：" + e.getMessage());
                return null;
            }
        }
        return new Toml().read(configFile);
    }

    // 初始化MySQL连接池
    private void initMySQL(Toml config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getString("mysql.host") + ":" + config.getLong("mysql.port") + "/" + config.getString("mysql.database") + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
        hikariConfig.setUsername(config.getString("mysql.username"));
        hikariConfig.setPassword(config.getString("mysql.password"));
        hikariConfig.setMaximumPoolSize(config.getLong("mysql.pool_size").intValue());
        hikariConfig.setConnectionTimeout(30000);

        try {
            dataSource = new HikariDataSource(hikariConfig);
            dataSource.getConnection().close();
            logger.info("MySQL连接成功！");
        } catch (SQLException e) {
            logger.severe("MySQL连接失败：" + e.getMessage());
            dataSource = null;
        }
    }

    // 发送PluginMessage到所有子服
    private void sendPluginMessage(UUID uuid, String name, String type) {
        String message = type + "|" + uuid + "|" + name;
        byte[] messageBytes = message.getBytes();

        for (RegisteredServer server : proxy.getAllServers()) {
            server.sendPluginMessage(messageChannel, messageBytes);
        }
    }

    // 插件关闭
    public void onDisable() {
        if (dataSource != null) dataSource.close();
        logger.info("PillowDream_joinmass (Velocity) 插件已关闭！");
    }
}
