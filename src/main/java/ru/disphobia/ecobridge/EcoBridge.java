package ru.disphobia.ecobridge;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import java.util.Map;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import ru.disphobia.ecobridge.command.EcoCommands;
import ru.disphobia.ecobridge.command.EcoTabCompleter;
import ru.disphobia.ecobridge.database.MySQLManager;
import ru.disphobia.ecobridge.hook.EcoPlaceholders;
import ru.disphobia.ecobridge.hook.VaultHook;
import ru.disphobia.ecobridge.listener.PlayerListener;
import ru.disphobia.ecobridge.manager.BaltopManager;
import ru.disphobia.ecobridge.manager.PlayerManager;
import ru.disphobia.ecobridge.redis.RedisManager;

public final class EcoBridge extends JavaPlugin {

    private MySQLManager database;
    private RedisManager redisManager;
    private PlayerManager playerManager;
    private BaltopManager baltopManager; // Новый менеджер

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        database = new MySQLManager(this);
        database.connect();

        redisManager = new RedisManager(this);
        redisManager.connect();

        playerManager = new PlayerManager(this);
        baltopManager = new BaltopManager(this); // Инициализация

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        EcoCommands commands = new EcoCommands(this);
        EcoTabCompleter tabCompleter = new EcoTabCompleter();

        getCommand("money").setExecutor(commands);
        getCommand("money").setTabCompleter(tabCompleter);

        getCommand("pay").setExecutor(commands);
        getCommand("pay").setTabCompleter(tabCompleter);

        getCommand("eco").setExecutor(commands);
        getCommand("eco").setTabCompleter(tabCompleter);

        getCommand("baltop").setExecutor(commands);
        // baltop не нужен tabcompleter

        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            getServer().getServicesManager().register(Economy.class, new VaultHook(this), this, ServicePriority.Highest);
            getLogger().info("Vault Economy: ПОДКЛЮЧЕН");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EcoPlaceholders(this).register();
            getLogger().info("PlaceholderAPI: ПОДКЛЮЧЕН");
        }

        getLogger().info("EcoBridge v1.0 успешно загружен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("Начало выключения EcoBridge...");

        // 1. Сначала отключаем Redis, чтобы другие серверы не присылали нам новые данные
        if (redisManager != null) {
            redisManager.disconnect();
        }

        if (playerManager != null) {
            // 2. Ждем, пока все текущие транзакции (покупки/продажи из очереди) досохраняются в БД
            getLogger().info("Ожидание завершения фоновых запросов к БД...");
            playerManager.shutdown();

            // 3. Теперь ваша страховка: принудительно сохраняем кеш (СИНХРОННО)
            getLogger().info("Сохранение балансов игроков перед выключением...");
            for (Map.Entry<java.util.UUID, Double> entry : playerManager.getCachedBalances().entrySet()) {
                // Небольшая оптимизация: у вас уже есть имя в базе, чтобы не грузить Bukkit API лишний раз,
                // можно передавать просто "Unknown" - наш SQL запрос (COALESCE) не перезапишет реальное имя нулём.
                // Либо оставляем ваш вариант, он тоже работает.
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                playerManager.saveBalanceSync(entry.getKey(), name, entry.getValue());
            }
        }

        // 4. И только в самом конце рубим подключение к MySQL
        if (database != null) {
            database.disconnect();
        }

        getLogger().info("Все балансы сохранены! EcoBridge выключен.");
    }

    public MySQLManager getDatabase() { return database; }
    public RedisManager getRedisManager() { return redisManager; }
    public PlayerManager getPlayerManager() { return playerManager; }
    public BaltopManager getBaltopManager() { return baltopManager; } // Геттер
}
