package com.vynex.pingoptimizer.command;

import com.vynex.pingoptimizer.PingOptimizerPlugin;
import com.vynex.pingoptimizer.service.PingCalculationService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PingOptimizerCommand implements CommandExecutor, TabCompleter {

    private final PingOptimizerPlugin plugin;
    private final PingCalculationService pingService;

    public PingOptimizerCommand(PingOptimizerPlugin plugin, PingCalculationService pingService) {
        this.plugin = plugin;
        this.pingService = pingService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("pingoptimizer.admin")) {
            sender.sendMessage(ChatColor.RED + "Bu komutu kullanmak için yetkiniz yok.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.DARK_GRAY + "---------------- " + ChatColor.GREEN + "PingOptimizer Durumu" + ChatColor.DARK_GRAY + " ----------------");
            for (Player player : Bukkit.getOnlinePlayers()) {
                int raw = pingService.getRawPing(player);
                int opt = pingService.getOptimizedPing(player);
                int saved = pingService.getSavedMs(player);

                String rawColor = raw > 100 ? ChatColor.RED.toString() : (raw > 50 ? ChatColor.YELLOW.toString() : ChatColor.GREEN.toString());
                String optColor = opt <= 40 ? ChatColor.GREEN.toString() : ChatColor.YELLOW.toString();

                sender.sendMessage(ChatColor.GRAY + "• " + ChatColor.WHITE + player.getName() + ": "
                        + rawColor + raw + " ms" + ChatColor.DARK_GRAY + " ➔ "
                        + optColor + opt + " ms "
                        + ChatColor.AQUA + "(-" + saved + " ms tasarruf)");
            }
            sender.sendMessage(ChatColor.DARK_GRAY + "--------------------------------------------------");
            return true;
        }

        if (args[0].equalsIgnoreCase("on")) {
            pingService.setOptimizationEnabled(true);
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getLatencySynchronizer().syncPlayerLatency(p);
            }
            sender.sendMessage(ChatColor.GREEN + "✓ PingOptimizer AÇILDI! Tab listesinde pingler optimize edilmiş olarak gösteriliyor.");
            return true;
        }

        if (args[0].equalsIgnoreCase("off")) {
            pingService.setOptimizationEnabled(false);
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getLatencySynchronizer().syncPlayerLatency(p);
            }
            sender.sendMessage(ChatColor.GOLD + "⚠ PingOptimizer KAPATILDI! Tab listesinde artık tamamen HAM (gerçek) ping gösteriliyor.");
            return true;
        }

        if (args[0].equalsIgnoreCase("toggle")) {
            boolean newState = !pingService.isOptimizationEnabled();
            pingService.setOptimizationEnabled(newState);
            for (Player p : Bukkit.getOnlinePlayers()) {
                plugin.getLatencySynchronizer().syncPlayerLatency(p);
            }
            if (newState) {
                sender.sendMessage(ChatColor.GREEN + "✓ PingOptimizer AÇILDI! (Tab optimize)");
            } else {
                sender.sendMessage(ChatColor.GOLD + "⚠ PingOptimizer KAPATILDI! (Tab orijinal ham ping)");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPluginConfig();
            sender.sendMessage(ChatColor.GREEN + "✓ PingOptimizer yapılandırması başarıyla yenilendi.");
            return true;
        }

        if (args[0].equalsIgnoreCase("set") && args.length >= 3) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Oyuncu bulunamadı: " + args[1]);
                return true;
            }

            try {
                int targetPing = Integer.parseInt(args[2]);
                pingService.setManualOverride(target.getUniqueId(), targetPing);
                sender.sendMessage(ChatColor.GREEN + "✓ " + target.getName() + " için ping " + targetPing + " ms olarak ayarlandı!");
            } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "Geçerli bir sayı giriniz: " + args[2]);
            }
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "=== PingOptimizer Komutları ===");
        sender.sendMessage(ChatColor.YELLOW + "/po stats " + ChatColor.GRAY + "- Oyuncuların ham ve optimize edilmiş pinglerini gösterir.");
        sender.sendMessage(ChatColor.YELLOW + "/po reload " + ChatColor.GRAY + "- Config dosyasını yeniler.");
        sender.sendMessage(ChatColor.YELLOW + "/po set <oyuncu> <ms> " + ChatColor.GRAY + "- Belirli bir oyuncunun pingini manuel sabitler.");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("stats", "on", "off", "toggle", "reload", "set");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return Arrays.asList("20", "25", "30", "35", "40");
        }
        return List.of();
    }
}
