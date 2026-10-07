package com.vynex.pingoptimizer.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Gerçek oyuncular için Latency Senkronizörü:
 * 1. Minecraft 1.21.x (ServerCommonPacketListenerImpl.latency) alanını günceller.
 * 2. Böylece player.getPing() doğrudan optimize edilmiş değeri döndürür.
 * 3. ClientboundPlayerInfoUpdatePacket paketini tüm istemcilere (Lunar, Badlion, Vanilla)
 *    göndererek Tab listesindeki ping değerini kesin olarak günceller.
 */
public class LatencySynchronizer {

    private final Plugin plugin;
    private final PingCalculationService pingService;
    private BukkitTask task;

    // Reflection önbelleği
    private Field connectionLatencyField;
    private Field playerLatencyField;
    private Method sendPacketMethod;
    private Constructor<?> updateLatencyPacketConstructor;
    private Object updateLatencyAction;
    private boolean reflectionInitialized = false;

    public LatencySynchronizer(Plugin plugin, PingCalculationService pingService) {
        this.plugin = plugin;
        this.pingService = pingService;
    }

    public void start() {
        stop();
        // Her 20 tick (1 saniye) gerçek oyuncuların pingini Tab listesinde güncelle
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                syncPlayerLatency(player);
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void syncPlayerLatency(Player player) {
        if (player == null || !player.isOnline()) return;

        try {
            int optimized = pingService.getOptimizedPing(player);

            Method getHandleMethod = player.getClass().getMethod("getHandle");
            Object serverPlayer = getHandleMethod.invoke(player);

            if (!reflectionInitialized) {
                initReflection(serverPlayer);
                reflectionInitialized = true;
            }

            // 1. Connection (ServerCommonPacketListenerImpl) içindeki latency'yi güncelle (1.20 - 1.21.x)
            Object connection = getConnection(serverPlayer);
            if (connection != null && connectionLatencyField != null) {
                connectionLatencyField.setInt(connection, optimized);
            }

            // 2. ServerPlayer içindeki latency'yi güncelle (Eski sürümler uyumluluğu)
            if (playerLatencyField != null) {
                playerLatencyField.setInt(serverPlayer, optimized);
            }

            // 3. Tab paketini (ClientboundPlayerInfoUpdatePacket) tüm oyunculara yayınla
            broadcastTabUpdate(serverPlayer);

        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINEST, "Latency senkronizasyon hatasi: " + player.getName(), t);
        }
    }

    private void broadcastTabUpdate(Object targetServerPlayer) {
        if (updateLatencyPacketConstructor == null || updateLatencyAction == null) return;

        try {
            // new ClientboundPlayerInfoUpdatePacket(Action.UPDATE_LATENCY, targetServerPlayer)
            Object packet = updateLatencyPacketConstructor.newInstance(updateLatencyAction, targetServerPlayer);

            // Tüm online oyunculara gönder
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                Object viewerHandle = viewer.getClass().getMethod("getHandle").invoke(viewer);
                Object viewerConn = getConnection(viewerHandle);
                if (viewerConn != null && sendPacketMethod != null) {
                    sendPacketMethod.invoke(viewerConn, packet);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private Object getConnection(Object serverPlayer) {
        try {
            Field f = serverPlayer.getClass().getField("connection");
            return f.get(serverPlayer);
        } catch (Exception e1) {
            try {
                for (Field field : serverPlayer.getClass().getDeclaredFields()) {
                    if (field.getType().getSimpleName().contains("PacketListener") ||
                        field.getType().getSimpleName().contains("Connection")) {
                        field.setAccessible(true);
                        return field.get(serverPlayer);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private void initReflection(Object serverPlayer) {
        try {
            // ServerPlayer içindeki latency alanını ara
            playerLatencyField = findIntField(serverPlayer.getClass(), "latency", "ping");

            // connection içindeki latency alanını ara (Minecraft 1.21.1: ServerCommonPacketListenerImpl)
            Object connection = getConnection(serverPlayer);
            if (connection != null) {
                connectionLatencyField = findIntField(connection.getClass(), "latency", "ping");

                // send(Packet) metodunu bul
                for (Method m : connection.getClass().getMethods()) {
                    if (m.getName().equals("send") && m.getParameterCount() == 1) {
                        sendPacketMethod = m;
                        break;
                    }
                }
            }

            // ClientboundPlayerInfoUpdatePacket sınıfını bul
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            Class<?> actionEnum = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");

            // Action.UPDATE_LATENCY enum değerini bul
            for (Object enumConstant : actionEnum.getEnumConstants()) {
                if (enumConstant.toString().equals("UPDATE_LATENCY")) {
                    updateLatencyAction = enumConstant;
                    break;
                }
            }

            // Constructor(Action, ServerPlayer) bul
            for (Constructor<?> c : packetClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 2 && params[0].equals(actionEnum) && params[1].isAssignableFrom(serverPlayer.getClass())) {
                    c.setAccessible(true);
                    updateLatencyPacketConstructor = c;
                    break;
                }
            }

            plugin.getLogger().info("✓ NMS Latency & Tab Paket Enjeksiyonu basariyla baslatildi.");
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "NMS Reflection kismi yuklenirken hata (Fallback API modu aktif): " + t.getMessage());
        }
    }

    private Field findIntField(Class<?> startClass, String... names) {
        Class<?> clazz = startClass;
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == int.class) {
                    for (String name : names) {
                        if (f.getName().equalsIgnoreCase(name)) {
                            f.setAccessible(true);
                            return f;
                        }
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }
}
