package fr.frankulinn.usefullshops.permission;

import fr.frankulinn.usefullshops.model.Shop;
import org.bukkit.entity.Player;

public final class Access {

    public static final String USE = "usefullshops.use";
    public static final String BYPASS = "usefullshops.bypass.permission";

    private Access() {
    }

    public static boolean canAccess(Player player, Shop shop) {
        return shop.permission() == null
                || player.hasPermission(shop.permission())
                || player.hasPermission(BYPASS);
    }
}
