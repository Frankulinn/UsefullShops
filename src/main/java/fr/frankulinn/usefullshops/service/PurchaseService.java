package fr.frankulinn.usefullshops.service;

import fr.frankulinn.usefullshops.api.ShopPurchaseEvent;
import fr.frankulinn.usefullshops.api.ShopPurchasedEvent;
import fr.frankulinn.usefullshops.economy.EconomyProvider;
import fr.frankulinn.usefullshops.item.ItemCodec;
import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.model.ShopItem;
import fr.frankulinn.usefullshops.permission.Access;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/** The purchase transaction. Main thread only. */
public final class PurchaseService {

    private final EconomyProvider economy;
    private final PurchaseHistory history;
    private final Logger logger;

    public PurchaseService(EconomyProvider economy, PurchaseHistory history, Logger logger) {
        this.economy = economy;
        this.history = history;
        this.logger = logger;
    }

    public PurchaseResult purchase(Player player, Shop shop, ShopItem item) {
        if (!Access.canAccess(player, shop)) {
            return PurchaseResult.NO_ACCESS;
        }
        Optional<ItemStack> decoded = ItemCodec.decode(item.itemData());
        if (decoded.isEmpty()) {
            return PurchaseResult.INVALID_ITEM;
        }
        if (!economy.isAvailable()) {
            return PurchaseResult.ECONOMY_UNAVAILABLE;
        }
        List<ItemStack> stacks = split(decoded.get(), item.amount());
        if (!hasSpace(player, stacks)) {
            return PurchaseResult.NO_SPACE;
        }
        BigDecimal price = item.price();
        ShopPurchaseEvent event = new ShopPurchaseEvent(player, shop.id(), item.key(), item.itemId(), item.amount(), price);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return PurchaseResult.CANCELLED;
        }
        if (!economy.has(player, price)) {
            return PurchaseResult.NOT_ENOUGH_MONEY;
        }
        if (!economy.withdraw(player, price)) {
            return PurchaseResult.ERROR;
        }
        try {
            var leftover = player.getInventory().addItem(stacks.toArray(ItemStack[]::new));
            leftover.values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        } catch (RuntimeException e) {
            economy.deposit(player, price);
            logger.log(Level.SEVERE, "Giving items failed, purchase refunded", e);
            return PurchaseResult.ERROR;
        }
        history.record(new PurchaseHistory.Entry(Instant.now(), player.getUniqueId(), player.getName(),
                shop.id(), item.key(), item.itemId(), item.amount(), price));
        Bukkit.getPluginManager().callEvent(
                new ShopPurchasedEvent(player, shop.id(), item.key(), item.itemId(), item.amount(), price));
        return PurchaseResult.SUCCESS;
    }

    /** Splits a lot into stacks honouring the item's own max stack size (modded items may differ from 64). */
    static List<ItemStack> split(ItemStack base, int amount) {
        int max = Math.max(1, base.getMaxStackSize());
        List<ItemStack> stacks = new ArrayList<>();
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = base.clone();
            stack.setAmount(Math.min(max, remaining));
            stacks.add(stack);
            remaining -= stack.getAmount();
        }
        return stacks;
    }

    private static boolean hasSpace(Player player, List<ItemStack> stacks) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        Inventory simulation = Bukkit.createInventory(null, storage.length);
        for (int i = 0; i < storage.length; i++) {
            simulation.setItem(i, storage[i] == null ? null : storage[i].clone());
        }
        return simulation.addItem(stacks.toArray(ItemStack[]::new)).isEmpty();
    }
}
