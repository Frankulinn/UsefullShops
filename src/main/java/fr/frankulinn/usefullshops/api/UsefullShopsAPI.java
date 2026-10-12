package fr.frankulinn.usefullshops.api;

import org.bukkit.entity.Player;

import java.util.Set;

/**
 * Public API, obtained through Bukkit's ServicesManager:
 * {@code Bukkit.getServicesManager().load(UsefullShopsAPI.class)}.
 */
public interface UsefullShopsAPI {

    /** Identifiers of all shops. */
    Set<String> getShopIds();

    boolean shopExists(String shopId);

    /** Whether the player owns the permission required by the shop (always true for an unrestricted shop). */
    boolean canAccess(Player player, String shopId);

    /** Opens the shop menu; returns false if the shop does not exist, access is denied or an event cancelled it. */
    boolean openShop(Player player, String shopId);
}
