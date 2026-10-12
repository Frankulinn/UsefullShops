package fr.frankulinn.usefullshops.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Shop {

    private final String id;
    private String displayName;
    private String permission;
    private final Set<String> npcIds = new LinkedHashSet<>();
    private final List<ShopItem> items = new ArrayList<>();

    public Shop(String id) {
        this.id = id;
        this.displayName = id;
    }

    public String id() {
        return id;
    }

    /** Title of the menu (MiniMessage). */
    public String displayName() {
        return displayName;
    }

    public void displayName(String displayName) {
        this.displayName = displayName;
    }

    /** Permission required to use the shop, or {@code null} when it is open to everyone. */
    public String permission() {
        return permission;
    }

    public void permission(String permission) {
        this.permission = permission;
    }

    public Set<String> npcIds() {
        return npcIds;
    }

    public List<ShopItem> items() {
        return items;
    }

    public Optional<ShopItem> item(String key) {
        for (ShopItem item : items) {
            if (item.key().equals(key)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
