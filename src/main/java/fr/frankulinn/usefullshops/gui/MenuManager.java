package fr.frankulinn.usefullshops.gui;

import fr.frankulinn.usefullshops.api.ShopOpenEvent;
import fr.frankulinn.usefullshops.config.Messages;
import fr.frankulinn.usefullshops.economy.EconomyProvider;
import fr.frankulinn.usefullshops.item.ItemCodec;
import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.model.ShopItem;
import fr.frankulinn.usefullshops.permission.Access;
import fr.frankulinn.usefullshops.service.PurchaseResult;
import fr.frankulinn.usefullshops.service.PurchaseService;
import fr.frankulinn.usefullshops.service.ShopService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Builds the shop menus and routes their clicks. */
public final class MenuManager {

    static final int ROWS = 6;
    static final int FIRST_ITEM_SLOT = 9;
    static final int ITEMS_PER_PAGE = 36;
    static final int SLOT_PREVIOUS = 48;
    static final int SLOT_INDICATOR = 49;
    static final int SLOT_NEXT = 50;
    static final int CONFIRM_NO = 11;
    static final int CONFIRM_ITEM = 13;
    static final int CONFIRM_YES = 15;

    private final JavaPlugin plugin;
    private final ShopService shops;
    private final PurchaseService purchases;
    private final EconomyProvider economy;
    private final Messages messages;

    public MenuManager(JavaPlugin plugin, ShopService shops, PurchaseService purchases,
                       EconomyProvider economy, Messages messages) {
        this.plugin = plugin;
        this.shops = shops;
        this.purchases = purchases;
        this.economy = economy;
        this.messages = messages;
    }

    // ---------------------------------------------------------------- opening

    /** Opens the shop for a player after access checks. Returns false when it could not be opened. */
    public boolean openChecked(Player player, Shop shop) {
        if (!Access.canAccess(player, shop)) {
            messages.send(player, "access-denied");
            return false;
        }
        return open(player, shop, 0);
    }

    /** Opens the shop without checking the access permission (administrator view). */
    public boolean open(Player player, Shop shop, int page) {
        ShopOpenEvent event = new ShopOpenEvent(player, shop.id());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }
        int pages = Math.max(1, (shop.items().size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        int current = Math.max(0, Math.min(page, pages - 1));

        MenuHolder.Shop holder = new MenuHolder.Shop(shop.id(), current);
        Inventory inventory = Bukkit.createInventory(holder, ROWS * 9, messages.parse(shop.displayName()));
        holder.inventory(inventory);

        ItemStack top = filler(Material.GREEN_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, top);
        }
        ItemStack bottom = filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, bottom);
        }
        int start = current * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE && start + i < shop.items().size(); i++) {
            inventory.setItem(FIRST_ITEM_SLOT + i, display(shop.items().get(start + i), true));
        }
        if (current > 0) {
            inventory.setItem(SLOT_PREVIOUS, button(Material.RED_BANNER, "gui-previous"));
        }
        if (current + 1 < pages) {
            inventory.setItem(SLOT_NEXT, button(Material.LIME_BANNER, "gui-next"));
        }
        inventory.setItem(SLOT_INDICATOR, button(Material.PAPER, "gui-page", "page", current + 1, "pages", pages));
        player.openInventory(inventory);
        return true;
    }

    private void openConfirm(Player player, Shop shop, ShopItem item, int page) {
        MenuHolder.Confirm holder = new MenuHolder.Confirm(shop.id(), item.key(), page);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.component("gui-confirm-title", "item", itemName(item)));
        holder.inventory(inventory);
        ItemStack background = filler(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < 27; slot++) {
            inventory.setItem(slot, background);
        }
        inventory.setItem(CONFIRM_NO, button(Material.RED_STAINED_GLASS_PANE, "gui-no"));
        inventory.setItem(CONFIRM_ITEM, display(item, false));
        inventory.setItem(CONFIRM_YES, button(Material.GREEN_STAINED_GLASS_PANE, "gui-yes"));
        player.openInventory(inventory);
    }

    // ---------------------------------------------------------------- clicks

    void handleClick(Player player, MenuHolder holder, int slot) {
        Optional<Shop> found = shops.find(holder.shopId());
        if (found.isEmpty()) {
            player.closeInventory();
            return;
        }
        Shop shop = found.get();
        if (holder instanceof MenuHolder.Shop) {
            handleShopClick(player, shop, holder.page(), slot);
        } else if (holder instanceof MenuHolder.Confirm confirm) {
            handleConfirmClick(player, shop, confirm, slot);
        }
    }

    private void handleShopClick(Player player, Shop shop, int page, int slot) {
        if (slot == SLOT_PREVIOUS && page > 0) {
            later(() -> open(player, shop, page - 1));
        } else if (slot == SLOT_NEXT && (page + 1) * ITEMS_PER_PAGE < shop.items().size()) {
            later(() -> open(player, shop, page + 1));
        } else if (slot >= FIRST_ITEM_SLOT && slot < FIRST_ITEM_SLOT + ITEMS_PER_PAGE) {
            int index = page * ITEMS_PER_PAGE + (slot - FIRST_ITEM_SLOT);
            if (index >= shop.items().size()) {
                return;
            }
            ShopItem item = shop.items().get(index);
            if (plugin.getConfig().getBoolean("confirm-purchase", true)) {
                later(() -> openConfirm(player, shop, item, page));
            } else {
                later(() -> buy(player, shop, item, page));
            }
        }
    }

    private void handleConfirmClick(Player player, Shop shop, MenuHolder.Confirm holder, int slot) {
        if (slot == CONFIRM_NO) {
            later(() -> open(player, shop, holder.page()));
        } else if (slot == CONFIRM_YES) {
            Optional<ShopItem> item = shop.item(holder.itemKey());
            if (item.isEmpty()) {
                later(() -> open(player, shop, holder.page()));
                return;
            }
            later(() -> buy(player, shop, item.get(), holder.page()));
        }
    }

    private void buy(Player player, Shop shop, ShopItem item, int page) {
        PurchaseResult result = purchases.purchase(player, shop, item);
        switch (result) {
            case SUCCESS -> messages.send(player, "purchase-success",
                    "amount", item.amount(), "item", itemName(item), "price", economy.format(item.price()));
            case NO_ACCESS -> messages.send(player, "access-denied");
            case INVALID_ITEM -> messages.send(player, "purchase-invalid");
            case NO_SPACE -> messages.send(player, "purchase-no-space");
            case NOT_ENOUGH_MONEY -> messages.send(player, "purchase-no-money", "item", itemName(item));
            case ECONOMY_UNAVAILABLE -> messages.send(player, "economy-unavailable");
            case CANCELLED -> messages.send(player, "purchase-cancelled");
            case ERROR -> messages.send(player, "purchase-error");
        }
        if (result == PurchaseResult.SUCCESS && plugin.getConfig().getBoolean("close-on-purchase", false)) {
            player.closeInventory();
        } else {
            open(player, shop, page);
        }
    }

    /** Inventory changes triggered from a click event are deferred to the next tick. */
    private void later(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    // ---------------------------------------------------------------- rendering

    private ItemStack display(ShopItem shopItem, boolean withClickHint) {
        Optional<ItemStack> decoded = ItemCodec.decode(shopItem.itemData());
        if (decoded.isEmpty()) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            barrier.editMeta(meta -> {
                meta.displayName(noItalic(messages.component("gui-invalid-item", "item", shopItem.key())));
                meta.lore(List.of(noItalic(messages.component("gui-invalid-item-lore", "id", shopItem.itemId()))));
            });
            return barrier;
        }
        ItemStack stack = decoded.get();
        stack.setAmount(Math.min(shopItem.amount(), Math.max(1, stack.getMaxStackSize())));
        List<Component> lore = new ArrayList<>();
        List<Component> existing = stack.lore();
        if (existing != null) {
            lore.addAll(existing);
        }
        lore.add(Component.empty());
        lore.add(noItalic(messages.component("gui-price",
                "price", economy.format(shopItem.price()), "amount", shopItem.amount())));
        if (withClickHint) {
            lore.add(noItalic(messages.component("gui-click-to-buy")));
        }
        stack.lore(lore);
        if (shopItem.displayName() != null) {
            stack.editMeta(meta -> meta.displayName(noItalic(messages.parse(shopItem.displayName()))));
        }
        return stack;
    }

    private Component itemName(ShopItem shopItem) {
        if (shopItem.displayName() != null) {
            return messages.parse(shopItem.displayName());
        }
        return ItemCodec.decode(shopItem.itemData())
                .map(MenuManager::stackName)
                .orElse(Component.text(shopItem.key()));
    }

    /** Custom name when set, otherwise the translatable name (works for modded items on modded clients). */
    private static Component stackName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return name;
            }
        }
        return Component.translatable(stack.translationKey());
    }

    private ItemStack filler(Material material) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Component.space());
            meta.setHideTooltip(true);
        });
        return stack;
    }

    private ItemStack button(Material material, String nameKey, Object... placeholders) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta((ItemMeta meta) -> meta.displayName(noItalic(messages.component(nameKey, placeholders))));
        return stack;
    }

    private static Component noItalic(Component component) {
        return Component.empty().decoration(TextDecoration.ITALIC, false).append(component);
    }
}
