package ru.disphobia.ecobridge.redis;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import ru.disphobia.ecobridge.EcoBridge;

import java.util.UUID;

public class RedisManager {

    private final EcoBridge plugin;
    private JedisPool pool;
    private JedisPubSub pubSub;

    public RedisManager(EcoBridge plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        String host = plugin.getConfig().getString("redis.host");
        int port = plugin.getConfig().getInt("redis.port");
        String password = plugin.getConfig().getString("redis.password");

        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(8);

        if (password == null || password.isEmpty()) {
            pool = new JedisPool(config, host, port, 2000);
        } else {
            pool = new JedisPool(config, host, port, 2000, password);
        }

        startSubscriber();
        plugin.getLogger().info("Подключение к Redis: УСТАНОВЛЕНО");
    }

    private void startSubscriber() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = pool.getResource()) {
                pubSub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {

                        Bukkit.getScheduler().runTask(plugin, () -> {

                            if (channel.equals("eco:sync")) {
                                String[] parts = message.split(":");
                                UUID uuid = UUID.fromString(parts[0]);
                                double newBalance = Double.parseDouble(parts[1]);

                                // ИСПРАВЛЕНИЕ: Проверяем именно по Кешу, а не по Bukkit.getPlayer().
                                // Это спасает синхронизацию в момент, когда игрок висит на экране загрузки.
                                if (plugin.getPlayerManager().getCachedBalances().containsKey(uuid)) {
                                    plugin.getPlayerManager().updateCache(uuid, newBalance);
                                }
                            }

                            else if (channel.equals("eco:notify")) {
                                int firstColon = message.indexOf(':');
                                if (firstColon != -1) {
                                    UUID targetUuid = UUID.fromString(message.substring(0, firstColon));
                                    String text = message.substring(firstColon + 1);

                                    Player target = Bukkit.getPlayer(targetUuid);
                                    if (target != null) {
                                        target.sendMessage(text.replace("&", "§"));
                                    }
                                }
                            }

                        });
                    }
                };

                jedis.subscribe(pubSub, "eco:sync", "eco:notify");

            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка подписки Redis: " + e.getMessage());
            }
        });
    }

    // Метод для синхронизации баланса
    public void publishUpdate(UUID uuid, double balance) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish("eco:sync", uuid.toString() + ":" + balance);
            } catch (Exception ignored) {}
        });
    }

    // НОВЫЙ МЕТОД: Отправка сообщения игроку на любой сервер
    public void sendCrossServerMessage(UUID target, String message) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.publish("eco:notify", target.toString() + ":" + message);
            } catch (Exception ignored) {}
        });
    }

    public void disconnect() {
        if (pubSub != null) {
            try {
                pubSub.unsubscribe();
            } catch (Exception ignored) {}
        }
        if (pool != null) {
            pool.close();
        }
    }
}