package ru.disphobia.ecobridge.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.disphobia.ecobridge.EcoBridge;

public class PlayerListener implements Listener {

    private final EcoBridge plugin;

    public PlayerListener(EcoBridge plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        // Загружаем данные из БД ДО спавна игрока
        plugin.getPlayerManager().loadPlayer(event.getUniqueId(), event.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Удаляем из памяти при выходе (сохранять не нужно, оно сохраняется при каждом изменении)
        plugin.getPlayerManager().unloadPlayer(event.getPlayer().getUniqueId());
    }
}