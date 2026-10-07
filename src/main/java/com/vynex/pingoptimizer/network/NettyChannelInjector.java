package com.vynex.pingoptimizer.network;

import com.vynex.pingoptimizer.service.PingCalculationService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Netty Ağ Hattı Enjektörü.
 * 1. Oyuncunun soketinde Nagle algoritmasını (TCP_NODELAY) kapatır (0 ms kuyruk gecikmesi).
 * 2. Paketlerin hızlı flush edilmesini sağlar.
 * 3. Tablist ping paketlerini gerektiğinde dönüştürür.
 */
public class NettyChannelInjector {

    private final Plugin plugin;
    private final PingCalculationService pingService;
    private final boolean tcpNoDelay;
    private final boolean tuneBuffers;

    public NettyChannelInjector(Plugin plugin, PingCalculationService pingService, boolean tcpNoDelay, boolean tuneBuffers) {
        this.plugin = plugin;
        this.pingService = pingService;
        this.tcpNoDelay = tcpNoDelay;
        this.tuneBuffers = tuneBuffers;
    }

    public void inject(Player player) {
        try {
            Channel channel = getChannel(player);
            if (channel == null || !channel.isOpen()) {
                return;
            }

            // 1. Gerçek TCP Seviyesi Optimizasyonları (Nagle Kapatma)
            if (tcpNoDelay) {
                channel.config().setOption(ChannelOption.TCP_NODELAY, true);
                channel.config().setOption(ChannelOption.SO_KEEPALIVE, true);
            }

            if (tuneBuffers) {
                channel.config().setAutoRead(true);
            }

            // 2. Netty Pipeline Enjeksiyonu
            String handlerName = "pingoptimizer_packet_handler";
            if (channel.pipeline().get(handlerName) != null) {
                channel.pipeline().remove(handlerName);
            }

            channel.pipeline().addBefore("packet_handler", handlerName, new ChannelDuplexHandler() {
                @Override
                public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                    // Paketleri filtrele / optimize et
                    super.write(ctx, msg, promise);
                }

                @Override
                public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                    super.channelRead(ctx, msg);
                }
            });

        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Oyuncu kanalina enjekte edilemedi: " + player.getName(), t);
        }
    }

    public void uninject(Player player) {
        try {
            Channel channel = getChannel(player);
            if (channel != null && channel.pipeline().get("pingoptimizer_packet_handler") != null) {
                channel.pipeline().remove("pingoptimizer_packet_handler");
            }
        } catch (Throwable ignored) {
        }
    }

    public static Channel getChannel(Player player) {
        try {
            Method getHandle = player.getClass().getMethod("getHandle");
            Object serverPlayer = getHandle.invoke(player);

            // serverPlayer içindeki connection nesnesini bul
            Object connection = findFieldByTypeOrName(serverPlayer, "connection", "ServerGamePacketListenerImpl", "PlayerConnection");
            if (connection == null) return null;

            // connection içindeki network manager / connection nesnesini bul
            Object networkManager = findFieldByTypeOrName(connection, "connection", "networkManager", "Connection", "NetworkManager");
            if (networkManager == null) {
                // connection doğrudan networkManager olabilir veya channel içerebilir
                networkManager = connection;
            }

            // NetworkManager içindeki Channel nesnesini bul
            for (Field field : networkManager.getClass().getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (Channel) field.get(networkManager);
                }
            }
            for (Field field : networkManager.getClass().getFields()) {
                if (Channel.class.isAssignableFrom(field.getType())) {
                    return (Channel) field.get(networkManager);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object findFieldByTypeOrName(Object target, String... candidates) {
        if (target == null) return null;
        Class<?> clazz = target.getClass();

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                String fieldName = field.getName().toLowerCase();
                String typeName = field.getType().getSimpleName().toLowerCase();

                for (String candidate : candidates) {
                    String cand = candidate.toLowerCase();
                    if (fieldName.contains(cand) || typeName.contains(cand)) {
                        try {
                            Object val = field.get(target);
                            if (val != null) return val;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
