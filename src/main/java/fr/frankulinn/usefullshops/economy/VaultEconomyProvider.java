package fr.frankulinn.usefullshops.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Vault-backed economy, resolved lazily because the economy plugin may enable after us. */
public final class VaultEconomyProvider implements EconomyProvider {

    private final ServicesManager services;

    public VaultEconomyProvider(ServicesManager services) {
        this.services = services;
    }

    private Economy economy() {
        RegisteredServiceProvider<Economy> registration = services.getRegistration(Economy.class);
        return registration == null ? null : registration.getProvider();
    }

    @Override
    public boolean isAvailable() {
        return economy() != null;
    }

    @Override
    public boolean has(OfflinePlayer player, BigDecimal amount) {
        Economy economy = economy();
        return economy != null && economy.has(player, amount.doubleValue());
    }

    @Override
    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        Economy economy = economy();
        return economy != null && economy.withdrawPlayer(player, amount.doubleValue()).transactionSuccess();
    }

    @Override
    public void deposit(OfflinePlayer player, BigDecimal amount) {
        Economy economy = economy();
        if (economy != null) {
            economy.depositPlayer(player, amount.doubleValue());
        }
    }

    @Override
    public String format(BigDecimal amount) {
        Economy economy = economy();
        return economy == null ? amount.toPlainString() : economy.format(amount.doubleValue());
    }

    @Override
    public BigDecimal normalize(BigDecimal amount) {
        Economy economy = economy();
        int digits = economy == null ? 2 : economy.fractionalDigits();
        return amount.setScale(digits < 0 ? 2 : digits, RoundingMode.HALF_UP);
    }
}
