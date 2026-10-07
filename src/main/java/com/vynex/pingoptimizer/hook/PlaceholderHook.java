package com.vynex.pingoptimizer.hook;

import com.vynex.pingoptimizer.service.PingCalculationService;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class PlaceholderHook extends PlaceholderExpansion {

    private final Plugin plugin;
    private final PingCalculationService pingService;
    private final boolean colored;

    public PlaceholderHook(Plugin plugin, PingCalculationService pingService, boolean colored) {
        this.plugin = plugin;
        this.pingService = pingService;
        this.colored = colored;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "pingoptimizer";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Vynex";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        int optimizedPing = pingService.getOptimizedPing(player);
        int rawPing = pingService.getRawPing(player);
        int saved = pingService.getSavedMs(player);

        String color = ChatColor.GREEN.toString();
        if (optimizedPing > 75) {
            color = ChatColor.RED.toString();
        } else if (optimizedPing > 40) {
            color = ChatColor.YELLOW.toString();
        }

        switch (params.toLowerCase()) {
            case "ping":
                return colored ? (color + optimizedPing + " ms") : String.valueOf(optimizedPing);

            case "ping_num":
                return String.valueOf(optimizedPing);

            case "raw_ping":
                return String.valueOf(rawPing);

            case "saved_ms":
                return String.valueOf(saved);

            case "color":
                return color;

            case "summary":
                return color + optimizedPing + " ms (" + ChatColor.GRAY + "-" + saved + " ms" + color + ")";

            default:
                return null;
        }
    }
}
