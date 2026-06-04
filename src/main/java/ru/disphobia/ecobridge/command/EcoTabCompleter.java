package ru.disphobia.ecobridge.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EcoTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("eco")) {
            if (!sender.hasPermission("ecobridge.admin")) return completions;

            if (args.length == 1) {
                List<String> subCommands = Arrays.asList("give", "take", "set", "reset");
                return subCommands.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
            }
            if (args.length == 2) {
                return getOnlinePlayerNames(args[1]);
            }
            if (args.length == 3 && !args[0].equalsIgnoreCase("reset")) {
                return Arrays.asList("100", "1000", "10000");
            }
        }

        if (command.getName().equalsIgnoreCase("pay") || command.getName().equalsIgnoreCase("money")) {
            if (args.length == 1) return getOnlinePlayerNames(args[0]);
            if (args.length == 2 && command.getName().equalsIgnoreCase("pay")) {
                return Arrays.asList("10", "100", "1000");
            }
        }

        return completions;
    }

    private List<String> getOnlinePlayerNames(String start) {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(start.toLowerCase()))
                .collect(Collectors.toList());
    }
}