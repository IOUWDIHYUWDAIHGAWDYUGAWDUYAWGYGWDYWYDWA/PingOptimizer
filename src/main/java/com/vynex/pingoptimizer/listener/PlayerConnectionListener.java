package com.vynex.pingoptimizer.listener;

import com.vynex.pingoptimizer.network.NettyChannelInjector;
import com.vynex.pingoptimizer.service.LatencySynchronizer;
import com.vynex.pingoptimizer.service.PingCalculationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class PlayerConnectionListener implements Listener {

    private final Plugin plugin;
    private final NettyChannelInjector injector;
    private final PingCalculationService pingService;
    private final LatencySynchronizer synchronizer;

    public PlayerConnectionListener(Plugin plugin, NettyChannelInjector injector, PingCalculationService pingService, LatencySynchronizer synchronizer) {
        this.plugin = plugin;
        this.injector = injector;
        this.pingService = pingService;
        this.synchronizer = synchronizer;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Netty soketini optimize et (TCP_NODELAY vb.)
        injector.inject(player);

        // 2. Birkaç tick sonra latency değerini ilk kez senkronize et
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                synchronizer.syncPlayerLatency(player);
            }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        injector.uninject(player);
        pingService.removePlayer(player.getUniqueId());
    }
}
