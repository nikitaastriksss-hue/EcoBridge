package ru.disphobia.ecobridge.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import ru.disphobia.ecobridge.EcoBridge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlayerManager {

    private final EcoBridge plugin;

    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpdate = new ConcurrentHashMap<>();
    private final Map<UUID, Object> balanceLocks = new ConcurrentHashMap<>();

    // ИСПРАВЛЕНИЕ 1: Однопоточная очередь для БД.
    // Гарантирует, что сохранения будут выполняться СТРОГО по очереди.
    // 0 никогда не запишется после 31кк.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    public PlayerManager(EcoBridge plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Double> getCachedBalances() {
        return balances;
    }

    /* =========================================================
       PUBLIC API
       ========================================================= */

    public double getBalance(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        double balance = balances.computeIfAbsent(uuid, this::getBalanceFromDB);
        logBalanceOperation("getbalance", uuid, player.getName(), 0.0, balance);
        return balance;
    }

    public void setBalance(OfflinePlayer player, double amount) {
        UUID uuid = player.getUniqueId();
        synchronized (getBalanceLock(uuid)) {
            double sanitized = Math.max(0.0, amount);
            applyLocalBalance(uuid, sanitized);
            saveBalanceAndPublishAsync(uuid, player.getName(), sanitized);
            logBalanceOperation("setbalance", uuid, player.getName(), amount, sanitized);
        }
    }

    public void addBalance(OfflinePlayer player, double amount) {
        if (amount <= 0) return;
        UUID uuid = player.getUniqueId();
        synchronized (getBalanceLock(uuid)) {
            double newBalance = getBalance(player) + amount;
            applyLocalBalance(uuid, newBalance);
            saveBalanceAndPublishAsync(uuid, player.getName(), newBalance);
            logBalanceOperation("addmoney", uuid, player.getName(), amount, newBalance);
        }
    }

    public void withdrawBalance(OfflinePlayer player, double amount) {
        if (amount <= 0) return;
        UUID uuid = player.getUniqueId();
        synchronized (getBalanceLock(uuid)) {
            double current = getBalance(player);
            if (amount > current) amount = current;
            double newBalance = current - amount;
            applyLocalBalance(uuid, newBalance);
            saveBalanceAndPublishAsync(uuid, player.getName(), newBalance);
            logBalanceOperation("withdrawmoney", uuid, player.getName(), amount, newBalance);
        }
    }

    public boolean hasAccount(OfflinePlayer player) {
        return balances.containsKey(player.getUniqueId()) || getBalanceFromDB(player.getUniqueId()) >= 0;
    }

    public void loadPlayer(UUID uuid, String name) {
        // ИСПРАВЛЕНИЕ 2: Защита от быстрого релога.
        // Если игрок перезашел, а его данные еще висят в кэше, не читаем из БД (чтобы не прочитать старые данные).
        if (balances.containsKey(uuid)) {
            upsertPlayerNameAsync(uuid, name);
            return;
        }

        double balance = getBalanceFromDB(uuid);
        balances.put(uuid, balance);
        lastUpdate.put(uuid, System.currentTimeMillis());
        upsertPlayerNameAsync(uuid, name);
        logBalanceOperation("load", uuid, name, 0.0, balance);
    }

    public void unloadPlayer(UUID uuid) {
        // ИСПРАВЛЕНИЕ 3: Задержка выгрузки из кэша.
        // Ждем 10 секунд перед удалением игрока из памяти. Спасает от багов при крашах и быстрых релогах.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(uuid);
            // Если игрок так и не зашел обратно через 10 секунд - выгружаем
            if (p == null || !p.isOnline()) {
                balances.remove(uuid);
                lastUpdate.remove(uuid);
                // ВНИМАНИЕ: balanceLocks.remove(uuid) удалять НЕЛЬЗЯ!
                // Это ломало сихнронизацию, если игрок выходил во время транзакции.
            }
        }, 200L); // 200 тиков = 10 секунд
    }

    public OfflinePlayer getOfflinePlayerByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;

        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM economy WHERE LOWER(name)=LOWER(?) LIMIT 1")) {
            stmt.setString(1, name);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Bukkit.getOfflinePlayer(UUID.fromString(rs.getString("uuid")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        OfflinePlayer fallback = Bukkit.getOfflinePlayer(name);
        return fallback.hasPlayedBefore() ? fallback : null;
    }

    public void updateCache(UUID uuid, double balance) {
        synchronized (getBalanceLock(uuid)) {
            double current = balances.getOrDefault(uuid, -1D);
            if (balance == 0.0 && current > 0.0) return;
            applyLocalBalance(uuid, balance);
            logBalanceOperation("redis-sync", uuid, null, 0.0, balance);
        }
    }

    /* =========================================================
       INTERNAL LOGIC (Асинхронные задачи и БД)
       ========================================================= */

    private void applyLocalBalance(UUID uuid, double balance) {
        balances.put(uuid, balance);
        lastUpdate.put(uuid, System.currentTimeMillis());
    }

    private Object getBalanceLock(UUID uuid) {
        return balanceLocks.computeIfAbsent(uuid, x -> new Object());
    }

    private void saveBalanceAndPublishAsync(UUID uuid, String name, double balance) {
        dbExecutor.submit(() -> {
            // 1. СНАЧАЛА гарантированно сохраняем в MySQL
            saveBalanceSync(uuid, name, balance);

            // 2. И ТОЛЬКО ПОТОМ отправляем уведомление в Redis
            if (plugin.getRedisManager() != null) {
                plugin.getRedisManager().publishUpdate(uuid, balance);
            }
        });
    }

    public void saveBalanceSync(UUID uuid, String name, double balance) {
        String sql = "INSERT INTO economy (uuid, name, balance) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE name = COALESCE(NULLIF(VALUES(name), 'Unknown'), name), balance = VALUES(balance)";

        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, uuid.toString());
            stmt.setString(2, sanitizeName(name));
            stmt.setDouble(3, balance);
            stmt.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().severe("Не удалось сохранить баланс игрока " + name + " (UUID: " + uuid + ")");
            e.printStackTrace();
        }
    }

    private double getBalanceFromDB(UUID uuid) {
        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT balance FROM economy WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return plugin.getConfig().getDouble("economy.starting-balance", 0.0);
    }

    private void upsertPlayerNameAsync(UUID uuid, String name) {
        if (name == null || name.isEmpty() || name.equals("Unknown")) return;
        // Здесь можно оставить Bukkit Scheduler, так как порядок сохранения имени не критичен
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabase().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("UPDATE economy SET name=? WHERE uuid=?")) {
                stmt.setString(1, name);
                stmt.setString(2, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException ignored) {}
        });
    }

    private String sanitizeName(String name) {
        return (name == null || name.isEmpty()) ? "Unknown" : name;
    }

    // ИСПРАВЛЕНИЕ 4: Метод закрытия потока при выключении сервера
    public void shutdown() {
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            dbExecutor.shutdownNow();
        }
    }

    /* =========================================================
       LOGGING (Без изменений)
       ========================================================= */

    private boolean isBalanceDebugEnabled() {
        return plugin.getConfig().getBoolean("logging.balance.enabled", false);
    }

    private boolean shouldLogAction(String action) {
        if (!isBalanceDebugEnabled()) return false;
        return plugin.getConfig().getBoolean("logging.balance.actions." + action, false);
    }

    private void logBalanceOperation(String action, UUID uuid, String name, double amount, double finalBalance) {
        if (!shouldLogAction(action)) return;
        String playerName = sanitizeName(name);
        String caller = detectCallerPlugin();

        Bukkit.getConsoleSender().sendMessage(String.format(
                "%s[EcoBridge:%s]%s player=%s%s%s uuid=%s amount=%.2f result=%.2f caller=%s",
                getActionColor(action), action, ChatColor.RESET, ChatColor.YELLOW,
                playerName, ChatColor.RESET, uuid, amount, finalBalance, caller
        ));
    }

    private ChatColor getActionColor(String action) {
        switch (action.toLowerCase()) {
            case "addmoney": return ChatColor.GREEN;
            case "withdrawmoney": return ChatColor.RED;
            case "setbalance": return ChatColor.GOLD;
            case "getbalance": return ChatColor.AQUA;
            default: return ChatColor.GRAY;
        }
    }

    private String detectCallerPlugin() {
        for (StackTraceElement el : Thread.currentThread().getStackTrace()) {
            String className = el.getClassName();
            if (className.equals(PlayerManager.class.getName()) || className.equals(Thread.class.getName())
                    || className.startsWith("java.") || className.startsWith("jdk.") || className.startsWith("sun.")) continue;
            try {
                Class<?> clazz = Class.forName(className);
                Plugin p = JavaPlugin.getProvidingPlugin(clazz);
                if (p != null) return p.getName();
            } catch (Throwable ignored) {}
        }
        return plugin.getName();
    }
}