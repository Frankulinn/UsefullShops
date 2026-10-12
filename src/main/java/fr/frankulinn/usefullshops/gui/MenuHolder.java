package fr.frankulinn.usefullshops.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** Marks inventories created by UsefullShops and carries the menu state (no global state). */
public abstract class MenuHolder implements InventoryHolder {

    private final String shopId;
    private final int page;
    private Inventory inventory;

    protected MenuHolder(String shopId, int page) {
        this.shopId = shopId;
        this.page = page;
    }

    public String shopId() {
        return shopId;
    }

    public int page() {
        return page;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    /** The paginated list of items. */
    public static final class Shop extends MenuHolder {
        public Shop(String shopId, int page) {
            super(shopId, page);
        }
    }

    /** The Yes/No screen shown before buying. */
    public static final class Confirm extends MenuHolder {
        private final String itemKey;

        public Confirm(String shopId, String itemKey, int page) {
            super(shopId, page);
            this.itemKey = itemKey;
        }

        public String itemKey() {
            return itemKey;
        }
    }
}
