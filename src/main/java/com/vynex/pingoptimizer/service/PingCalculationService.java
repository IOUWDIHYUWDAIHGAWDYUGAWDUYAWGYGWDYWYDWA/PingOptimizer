package com.vynex.pingoptimizer.service;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Akıllı Ping Hesaplama ve Düzeltme Servisi.
 * Yüksek pingleri (örn. 134 ms, 61 ms) doğal bir eğriyle yeşil bant seviyesine çeker.
 */
public class PingCalculationService {

    private final Map<UUID, Double> smoothedPings = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> manualOverrides = new ConcurrentHashMap<>();

    private String mode;
    private int targetGreenLimit;
    private int naturalVariation;
    private int minimumFloor;
    private double percentageMultiplier;
    private boolean jitterSmoothing;
    private volatile boolean optimizationEnabled = true;

    public void loadConfig(FileConfiguration config) {
        this.optimizationEnabled = config.getBoolean("tab-ping.enabled", true);
        this.mode = config.getString("tab-ping.mode", "SMART_CURVE");
        this.targetGreenLimit = config.getInt("tab-ping.smart-curve.target-green-limit", 36);
        this.naturalVariation = config.getInt("tab-ping.smart-curve.natural-variation", 3);
        this.minimumFloor = config.getInt("tab-ping.smart-curve.minimum-floor", 16);
        this.percentageMultiplier = config.getDouble("tab-ping.percentage.multiplier", 0.30);
        this.jitterSmoothing = config.getBoolean("tab-ping.jitter-smoothing", true);
    }

    public boolean isOptimizationEnabled() {
        return optimizationEnabled;
    }

    public void setOptimizationEnabled(boolean enabled) {
        this.optimizationEnabled = enabled;
    }

    /**
     * Oyuncunun gerçek (ham) sunucu pingini döndürür.
     */
    public int getRawPing(Player player) {
        if (player == null || !player.isOnline()) {
            return 0;
        }
        try {
            return player.getPing();
        } catch (Throwable t) {
            return 20;
        }
    }

    /**
     * Oyuncu için optimize edilmiş, aşağı çekilmiş pingi hesaplar.
     */
    public int getOptimizedPing(Player player) {
        if (player == null || !player.isOnline()) {
            return 0;
        }

        UUID uuid = player.getUniqueId();
        if (manualOverrides.containsKey(uuid)) {
            return manualOverrides.get(uuid);
        }

        int rawPing = getRawPing(player);
        if (!optimizationEnabled || rawPing <= minimumFloor) {
            return rawPing;
        }

        double calculatedPing;

        switch (mode.toUpperCase()) {
            case "PERCENTAGE":
                calculatedPing = Math.max(minimumFloor, rawPing * percentageMultiplier);
                break;

            case "FIXED_CAP":
                calculatedPing = Math.min(rawPing, targetGreenLimit);
                break;

            case "SMART_CURVE":
            default:
                // Akıllı sıkıştırma eğrisi:
                // Eğer ping zaten düşükse (ör. 20 ms), hiç dokunmaz veya 18-20 bandında tutar.
                // Eğer ping yüksekse (ör. 61 ms veya 134 ms), doğal logaritmik eğri ile yeşil sınıra sıkıştırır.
                if (rawPing <= targetGreenLimit) {
                    calculatedPing = rawPing;
                } else {
                    // Matematiksel sıkıştırma: hedef banda yumuşak geçiş
                    double excess = rawPing - targetGreenLimit;
                    double compressionFactor = 1.0 + Math.log10(1.0 + (excess / 15.0));
                    calculatedPing = targetGreenLimit + (excess / (compressionFactor * 3.5));
                    
                    // Üst sınırı aşırı aşmasını engelle
                    if (calculatedPing > targetGreenLimit + 8) {
                        calculatedPing = targetGreenLimit + 4 + (Math.sin(rawPing) * 2.0);
                    }
                }
                break;
        }

        // Doğal dalgalanma ekle (sabit ve yapay durmaması için +- variation)
        if (naturalVariation > 0) {
            int seedVariation = ThreadLocalRandom.current().nextInt(-naturalVariation, naturalVariation + 1);
            calculatedPing += seedVariation;
        }

        calculatedPing = Math.max(minimumFloor, calculatedPing);

        // Ping dalgalanmasını (Jitter) yumuşatmak için Üstel Hareketli Ortalama (EMA Filter)
        if (jitterSmoothing) {
            double previous = smoothedPings.getOrDefault(uuid, calculatedPing);
            // Alpha: %30 yeni değer, %70 eski ortalama
            double alpha = 0.30;
            double smoothed = (alpha * calculatedPing) + ((1.0 - alpha) * previous);
            smoothedPings.put(uuid, smoothed);
            return (int) Math.round(smoothed);
        } else {
            return (int) Math.round(calculatedPing);
        }
    }

    /**
     * Bu optimizasyon sayesinde oyuncudan ne kadar ms tasarruf edildiğini döndürür.
     */
    public int getSavedMs(Player player) {
        int raw = getRawPing(player);
        int opt = getOptimizedPing(player);
        return Math.max(0, raw - opt);
    }

    public void setManualOverride(UUID uuid, int ping) {
        manualOverrides.put(uuid, ping);
    }

    public void removePlayer(UUID uuid) {
        smoothedPings.remove(uuid);
        manualOverrides.remove(uuid);
    }
}
