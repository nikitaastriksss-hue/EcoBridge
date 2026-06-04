package ru.disphobia.ecobridge.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import ru.disphobia.ecobridge.EcoBridge;
import ru.disphobia.ecobridge.manager.BaltopManager;

import java.util.List;

public class EcoCommands implements CommandExecutor {

    private final EcoBridge plugin;

    public EcoCommands(EcoBridge plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // КОМАНДА /BALTOP
        if (command.getName().equalsIgnoreCase("baltop")) {
            List<BaltopManager.BaltopEntry> top = plugin.getBaltopManager().getTopPlayers();
            sender.sendMessage(plugin.getConfig().getString("messages.baltop-header").replace("&", "§"));

            String format = plugin.getConfig().getString("messages.baltop-format").replace("&", "§");
            for (int i = 0; i < top.size(); i++) {
                BaltopManager.BaltopEntry entry = top.get(i);
                sender.sendMessage(format
                        .replace("%pos%", String.valueOf(i + 1))
                        .replace("%name%", entry.name)
                        .replace("%balance%", String.format("%.2f", entry.balance)));
            }
            sender.sendMessage(plugin.getConfig().getString("messages.baltop-footer").replace("&", "§"));
            return true;
        }

        // Выполняем остальные команды асинхронно, чтобы поиск игроков по базе не лагал сервер!
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            // КОМАНДА /MONEY
            if (command.getName().equalsIgnoreCase("money")) {
                if (args.length == 0 && sender instanceof Player) {
                    double bal = plugin.getPlayerManager().getBalance((OfflinePlayer) sender);
                    sender.sendMessage("§aВаш баланс: §e" + String.format("%.2f", bal) + " $");
                    return;
                }
                if (args.length == 1) {
                    OfflinePlayer target = plugin.getPlayerManager().getOfflinePlayerByName(args[0]);
                    if (target == null) {
                        sender.sendMessage("§cИгрок не найден в базе.");
                        return;
                    }
                    double bal = plugin.getPlayerManager().getBalance(target);
                    sender.sendMessage("§aБаланс " + target.getName() + ": §e" + String.format("%.2f", bal) + " $");
                    return;
                }
            }

            // КОМАНДА /PAY
            if (command.getName().equalsIgnoreCase("pay") && sender instanceof Player) {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /pay <игрок> <сумма>");
                    return;
                }
                Player p = (Player) sender;
                OfflinePlayer target = plugin.getPlayerManager().getOfflinePlayerByName(args[0]);

                if (target == null) {
                    sender.sendMessage("§cИгрок не найден в базе.");
                    return;
                }
                if (p.getUniqueId().equals(target.getUniqueId())) {
                    sender.sendMessage("§cВы не можете перевести деньги самому себе.");
                    return;
                }

                double amount;
                try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException e) {
                    sender.sendMessage("§cУкажите корректную сумму.");
                    return;
                }

                if (amount <= 0) {
                    sender.sendMessage("§cСумма должна быть больше нуля.");
                    return;
                }

                if (plugin.getPlayerManager().getBalance(p) < amount) {
                    sender.sendMessage("§cНедостаточно средств.");
                    return;
                }

                // Снимаем и начисляем
                plugin.getPlayerManager().withdrawBalance(p, amount);
                plugin.getPlayerManager().addBalance(target, amount);

                // Отправляем сообщение отправителю
                p.sendMessage("§aВы перевели §e" + String.format("%.2f", amount) + " $ §aигроку §e" + target.getName());

                // Кросс-серверное уведомление получателю (через Redis)
                String msgForTarget = "§aВы получили §e" + String.format("%.2f", amount) + " $ §aот игрока §e" + p.getName();
                plugin.getRedisManager().sendCrossServerMessage(target.getUniqueId(), msgForTarget);

                return;
            }

            // КОМАНДА /ECO (АДМИНСКАЯ)
            if (command.getName().equalsIgnoreCase("eco")) {
                if (!sender.hasPermission("ecobridge.admin")) {
                    sender.sendMessage("§cНет прав.");
                    return;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /eco <give|take|set|reset> <игрок> [сумма]");
                    return;
                }

                String action = args[0].toLowerCase();
                OfflinePlayer target = plugin.getPlayerManager().getOfflinePlayerByName(args[1]);

                if (target == null) {
                    sender.sendMessage("§cИгрок не найден в базе.");
                    return;
                }

                if (action.equals("reset")) {
                    double startBal = plugin.getConfig().getDouble("economy.starting-balance", 0.0);
                    plugin.getPlayerManager().setBalance(target, startBal);
                    sender.sendMessage("§aБаланс игрока " + target.getName() + " обнулен (установлен на " + startBal + ").");
                    return;
                }

                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /eco " + action + " <игрок> <сумма>");
                    return;
                }

                double amount;
                try { amount = Double.parseDouble(args[2]); } catch (NumberFormatException e) {
                    sender.sendMessage("§cУкажите корректную сумму.");
                    return;
                }

                switch (action) {
                    case "give":
                        plugin.getPlayerManager().addBalance(target, amount);
                        sender.sendMessage("§aВы выдали §e" + amount + " $ §aигроку " + target.getName());
                        break;
                    case "take":
                        double current = plugin.getPlayerManager().getBalance(target);
                        if (current - amount < 0) amount = current; // Чтобы баланс не уходил в минус
                        plugin.getPlayerManager().withdrawBalance(target, amount);
                        sender.sendMessage("§aВы забрали §e" + amount + " $ §aу игрока " + target.getName());
                        break;
                    case "set":
                        plugin.getPlayerManager().setBalance(target, amount);
                        sender.sendMessage("§aВы установили баланс §e" + amount + " $ §aигроку " + target.getName());
                        break;
                    default:
                        sender.sendMessage("§cНеизвестное действие. Используйте: give, take, set, reset.");
                }
            }
        });
        return true;
    }
}