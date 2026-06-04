package ru.disphobia.ecobridge.hook;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import ru.disphobia.ecobridge.EcoBridge;
import ru.disphobia.ecobridge.manager.BaltopManager;

import java.util.List;

public class EcoPlaceholders extends PlaceholderExpansion {

    private final EcoBridge plugin;

    public EcoPlaceholders(EcoBridge plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "ecobridge"; }
    @Override public String getAuthor() { return "Disphobia"; }
    @Override public String getVersion() { return "1.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public String onPlaceholderRequest(Player player, String params) {
        if (params.startsWith("baltop_name_")) {
            int pos = getPos(params.replace("baltop_name_", ""));
            List<BaltopManager.BaltopEntry> top = plugin.getBaltopManager().getTopPlayers();
            if (pos > 0 && pos <= top.size()) return top.get(pos - 1).name;
            return plugin.getConfig().getString("messages.baltop-empty", "---");
        }

        if (params.startsWith("baltop_bal_")) {
            int pos = getPos(params.replace("baltop_bal_", ""));
            List<BaltopManager.BaltopEntry> top = plugin.getBaltopManager().getTopPlayers();
            if (pos > 0 && pos <= top.size()) return String.format("%.2f", top.get(pos - 1).balance);
            return "0.00";
        }

        return null;
    }

    private int getPos(String str) {
        try { return Integer.parseInt(str); } catch (NumberFormatException e) { return -1; }
    }
}