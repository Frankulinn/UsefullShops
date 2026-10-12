package fr.frankulinn.usefullshops.model;

import java.math.BigDecimal;

/** An item sold by a shop. {@code itemData} is the Base64 of the serialized ItemStack (see ItemCodec). */
public final class ShopItem {

    private final String key;
    private final String itemId;
    private final String itemData;
    private String displayName;
    private BigDecimal price;
    private int amount;

    public ShopItem(String key, String displayName, BigDecimal price, int amount, String itemId, String itemData) {
        this.key = key;
        this.displayName = displayName;
        this.price = price;
        this.amount = amount;
        this.itemId = itemId;
        this.itemData = itemData;
    }

    public String key() {
        return key;
    }

    /** Custom name shown in menus, or {@code null} to use the item's own name. */
    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    /** Price of one lot ({@link #amount()} items). */
    public BigDecimal price() {
        return price;
    }

    public void price(BigDecimal price) {
        this.price = price;
    }

    public int amount() {
        return amount;
    }

    public void amount(int amount) {
        this.amount = amount;
    }

    /** Registry id (namespace:path), informative. */
    public String itemId() {
        return itemId;
    }

    public String itemData() {
        return itemData;
    }
}
