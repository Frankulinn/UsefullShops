package fr.frankulinn.usefullshops.economy;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.util.Optional;

public interface EconomyProvider {

    /** Whether an economy is currently available. */
    boolean isAvailable();

    boolean has(OfflinePlayer player, BigDecimal amount);

    boolean withdraw(OfflinePlayer player, BigDecimal amount);

    void deposit(OfflinePlayer player, BigDecimal amount);

    String format(BigDecimal amount);

    /** Rounds a price to the precision supported by the economy. */
    BigDecimal normalize(BigDecimal amount);

    static Optional<EconomyProvider> none() {
        return Optional.empty();
    }
}
