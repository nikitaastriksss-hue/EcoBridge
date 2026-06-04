package ru.disphobia.ecobridge.manager;

import org.bukkit.Bukkit;
import ru.disphobia.ecobridge.EcoBridge;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class BaltopManager {

    private final EcoBridge plugin;
    private final List<BaltopEntry> topPlayers = new ArrayList<>();

    public BaltopManager(EcoBridge plugin) {
        this.plugin = plugin;
        startTask();
    }

    private void startTask() {
        int interval = plugin.getConfig().getInt("baltop-update-interval", 120) * 20;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::updateBaltop, 0L, interval);
    }

    public void updateBaltop() {
        List<BaltopEntry> newTop = new ArrayList<>();
        String sql = "SELECT name, balance FROM economy ORDER BY balance DESC LIMIT 10";

        try (Connection conn = plugin.getDatabase().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                newTop.add(new BaltopEntry(rs.getString("name"), rs.getDouble("balance")));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Ошибка при обновлении Baltop: " + e.getMessage());
        }

        synchronized (topPlayers) {
            topPlayers.clear();
            topPlayers.addAll(newTop);
        }
    }

    public List<BaltopEntry> getTopPlayers() {
        synchronized (topPlayers) {
            return new ArrayList<>(topPlayers);
        }
    }

    public static class BaltopEntry {
        public final String name;
        public final double balance;

        public BaltopEntry(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }
    }
}