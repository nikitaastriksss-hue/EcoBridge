package ru.disphobia.ecobridge.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import ru.disphobia.ecobridge.EcoBridge;

import java.util.Collections;
import java.util.List;

public class VaultHook implements Economy {

    private final EcoBridge plugin;

    public VaultHook(EcoBridge plugin) {
        this.plugin = plugin;
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "EcoBridge"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return String.format("%.2f", amount); }
    @Override public String currencyNamePlural() { return "Монет"; }
    @Override public String currencyNameSingular() { return "Монета"; }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return plugin.getPlayerManager().hasAccount(player);
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return plugin.getPlayerManager().getBalance(player);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Нельзя снять отрицательную сумму");
        if (!has(player, amount)) return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Недостаточно средств");

        plugin.getPlayerManager().withdrawBalance(player, amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        if (amount < 0) return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Нельзя выдать отрицательную сумму");

        plugin.getPlayerManager().addBalance(player, amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    // ==============================================================
    // Методы для строк (ОПТИМИЗИРОВАНО: Защита от зависаний Mojang API)
    // ==============================================================
    private OfflinePlayer getOP(String name) {
        OfflinePlayer op = plugin.getPlayerManager().getOfflinePlayerByName(name);
        return op != null ? op : org.bukkit.Bukkit.getOfflinePlayer(name);
    }

    @Override public boolean hasAccount(String playerName) { return hasAccount(getOP(playerName)); }
    @Override public double getBalance(String playerName) { return getBalance(getOP(playerName)); }
    @Override public boolean has(String playerName, double amount) { return has(getOP(playerName), amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return withdrawPlayer(getOP(playerName), amount); }
    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return depositPlayer(getOP(playerName), amount); }

    // ==============================================================
    // Заглушки для банков (Vault требует их все)
    // ==============================================================
    @Override public EconomyResponse createBank(String name, String player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse deleteBank(String name) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse bankBalance(String name) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse bankHas(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "No bank support"); }
    @Override public List<String> getBanks() { return Collections.emptyList(); }
    @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }
    @Override public double getBalance(String playerName, String world) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }
    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return true; }
}