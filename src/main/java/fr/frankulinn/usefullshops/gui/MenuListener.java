package fr.frankulinn.usefullshops.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Every interaction with a UsefullShops menu is cancelled (anti-duplication); only clicks on the menu are routed. */
public final class MenuListener implements Listener {

    private final MenuManager menus;

    public MenuListener(MenuManager menus) {
        this.menus = menus;
    }

    private static MenuHolder holderOf(Inventory top) {
        InventoryHolder holder = top.getHolder(false);
        return holder instanceof MenuHolder menu ? menu : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        MenuHolder holder = holderOf(event.getView().getTopInventory());
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ClickType click = event.getClick();
        if (click == ClickType.LEFT || click == ClickType.RIGHT) {
            menus.handleClick(player, holder, event.getSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (holderOf(event.getView().getTopInventory()) != null) {
            event.setCancelled(true);
        }
    }
}
