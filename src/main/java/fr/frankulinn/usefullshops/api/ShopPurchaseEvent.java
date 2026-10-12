package fr.frankulinn.usefullshops.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/** Fired before the money is withdrawn. Cancelling it aborts the purchase. */
public final class ShopPurchaseEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String shopId;
    private final String itemKey;
    private final String itemId;
    private final int amount;
    private final BigDecimal price;
    private boolean cancelled;

    public ShopPurchaseEvent(Player player, String shopId, String itemKey, String itemId, int amount, BigDecimal price) {
        this.player = player;
        this.shopId = shopId;
        this.itemKey = itemKey;
        this.itemId = itemId;
        this.amount = amount;
        this.price = price;
    }

    public Player getPlayer() {
        return player;
    }

    public String getShopId() {
        return shopId;
    }

    public String getItemKey() {
        return itemKey;
    }

    /** Registry id of the item, e.g. {@code minecraft:bread}. */
    public String getItemId() {
        return itemId;
    }

    public int getAmount() {
        return amount;
    }

    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
