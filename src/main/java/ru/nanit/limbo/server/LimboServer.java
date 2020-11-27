package ru.nanit.limbo.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import napi.configurate.serializing.NodeSerializers;
import ru.nanit.limbo.configuration.LimboConfig;
import ru.nanit.limbo.configuration.SocketAddressSerializer;
import ru.nanit.limbo.connection.ClientChannelInitializer;
import ru.nanit.limbo.connection.ClientConnection;
import ru.nanit.limbo.protocol.packets.play.PacketBossBar;
import ru.nanit.limbo.protocol.packets.play.PacketChatMessage;
import ru.nanit.limbo.server.data.*;
import ru.nanit.limbo.util.Logger;
import ru.nanit.limbo.world.DimensionRegistry;

import java.net.SocketAddress;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class LimboServer {

    private LimboConfig config;
    private Connections connections;
    private DimensionRegistry dimensionRegistry;

    private PacketChatMessage joinMessage;
    private PacketBossBar joinBossBar;

    public LimboConfig getConfig(){
        return config;
    }

    public Connections getConnections(){
        return connections;
    }

    public DimensionRegistry getDimensionRegistry() {
        return dimensionRegistry;
    }

    public PacketChatMessage getJoinMessage() {
        return joinMessage;
    }

    public PacketBossBar getJoinBossBar() {
        return joinBossBar;
    }

    public void start() throws Exception {
        Logger.info("Starting server...");

        NodeSerializers.register(SocketAddress.class, new SocketAddressSerializer());
        NodeSerializers.register(InfoForwarding.class, new InfoForwarding.Serializer());
        NodeSerializers.register(PingData.class, new PingData.Serializer());
        NodeSerializers.register(BossBar.class, new BossBar.Serializer());
        NodeSerializers.register(Position.class, new Position.Serializer());

        config = new LimboConfig(Paths.get("./"));
        config.load();

        Logger.setLevel(config.getDebugLevel());

        dimensionRegistry = new DimensionRegistry();
        dimensionRegistry.load(config.getDimensionType());

        connections = new Connections();

        initInGameData();

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(this::broadcastKeepAlive, 0L, 5L, TimeUnit.SECONDS);

        new ServerBootstrap()
                .group(new NioEventLoopGroup(), new NioEventLoopGroup())
                .channel(NioServerSocketChannel.class)
                .childHandler(new ClientChannelInitializer(this))
                .localAddress(config.getAddress())
                .bind();

        Logger.info("Server started on %s", config.getAddress());
    }

    private void initInGameData(){
        if (config.isUseJoinMessage()){
            joinMessage = new PacketChatMessage();
            joinMessage.setJsonData(config.getJoinMessage());
            joinMessage.setPosition(PacketChatMessage.Position.CHAT);
            joinMessage.setSender(UUID.randomUUID());
        }

        if (config.isUseBossBar()){
            joinBossBar = new PacketBossBar();
            joinBossBar.setBossBar(config.getBossBar());
            joinBossBar.setUuid(UUID.randomUUID());
        }
    }

    private void broadcastKeepAlive(){
        connections.getAllConnections().forEach(ClientConnection::sendKeepAlive);
    }

}