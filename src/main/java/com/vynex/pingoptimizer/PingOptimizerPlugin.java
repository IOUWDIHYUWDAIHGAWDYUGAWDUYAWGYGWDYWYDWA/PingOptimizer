package com.vynex.pingoptimizer;

import com.vynex.pingoptimizer.command.PingOptimizerCommand;
import com.vynex.pingoptimizer.hook.PlaceholderHook;
import com.vynex.pingoptimizer.listener.PlayerConnectionListener;
import com.vynex.pingoptimizer.network.NettyChannelInjector;
import com.vynex.pingoptimizer.service.LatencySynchronizer;
import com.vynex.pingoptimizer.service.PingCalculationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PingOptimizerPlugin extends JavaPlugin {

    private PingCalculationService pingService;
    private NettyChannelInjector nettyInjector;
    private LatencySynchronizer latencySynchronizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // 1. Servisleri Başlat
        this.pingService = new PingCalculationService();
        this.pingService.loadConfig(getConfig());

        boolean tcpNoDelay = getConfig().getBoolean("network.tcp-nodelay", true);
        boolean tuneBuffers = getConfig().getBoolean("network.tune-write-buffers", true);
        this.nettyInjector = new NettyChannelInjector(this, pingService, tcpNoDelay, tuneBuffers);

        this.latencySynchronizer = new LatencySynchronizer(this, pingService);
        this.latencySynchronizer.start();

        // 2. Olay Dinleyicilerini Kaydet
        getServer().getPluginManager().registerEvents(
                new PlayerConnectionListener(this, nettyInjector, pingService, latencySynchronizer),
                this
        );

        // 3. Komutları Kaydet
        PingOptimizerCommand cmd = new PingOptimizerCommand(this, pingService);
        Objects.requireNonNull(getCommand("pingoptimizer")).setExecutor(cmd);
        Objects.requireNonNull(getCommand("pingoptimizer")).setTabCompleter(cmd);

        // 4. PlaceholderAPI Desteği (Varsa Otomatik Entegre Ol)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            boolean colored = getConfig().getBoolean("placeholders.colored", true);
            new PlaceholderHook(this, pingService, colored).register();
            getLogger().info("PlaceholderAPI destegi basariyla aktif edildi! (%pingoptimizer_ping%)");
        }

        // 5. Halihazırda oyunda olan oyuncuları hemen optimize et (Reload durumunda)
        for (Player online : Bukkit.getOnlinePlayers()) {
            nettyInjector.inject(online);
            latencySynchronizer.syncPlayerLatency(online);
        }

        getLogger().info("=========================================");
        getLogger().info(" PingOptimizer v" + getDescription().getVersion() + " aktif edildi!");
        getLogger().info(" Paper 1.21.x Netty & Tab Optimizasyonu devrede.");
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        if (latencySynchronizer != null) {
            latencySynchronizer.stop();
        }

        if (nettyInjector != null) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                nettyInjector.uninject(online);
            }
        }

        getLogger().info("PingOptimizer devre disi birakildi.");
    }

    public void reloadPluginConfig() {
        reloadConfig();
        if (pingService != null) {
            pingService.loadConfig(getConfig());
        }
    }

    public PingCalculationService getPingService() {
        return pingService;
    }

    public LatencySynchronizer getLatencySynchronizer() {
        return latencySynchronizer;
    }
}
