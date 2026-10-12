package fr.frankulinn.usefullshops.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Fired before a shop menu is opened to a player. */
public final class ShopOpenEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final String shopId;
    private boolean cancelled;

    public ShopOpenEvent(Player player, String shopId) {
        this.player = player;
        this.shopId = shopId;
    }

    public Player getPlayer() {
        return player;
    }

    public String getShopId() {
        return shopId;
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
