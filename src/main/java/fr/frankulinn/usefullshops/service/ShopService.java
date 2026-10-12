package fr.frankulinn.usefullshops.service;

import fr.frankulinn.usefullshops.model.Ids;
import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.model.ShopItem;
import fr.frankulinn.usefullshops.storage.ShopRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** All shop rules: lifecycle, items, access permission and NPC bindings. Main thread only. */
public final class ShopService {

    private final ShopRepository repository;
    private final PermissionRegistry permissions;
    private final Supplier<String> permissionPrefix;
    private final Logger logger;
    private final Map<String, Shop> shops = new LinkedHashMap<>();
    private final Map<String, String> npcIndex = new HashMap<>();

    public ShopService(ShopRepository repository, PermissionRegistry permissions,
                       Supplier<String> permissionPrefix, Logger logger) {
        this.repository = repository;
        this.permissions = permissions;
        this.permissionPrefix = permissionPrefix;
        this.logger = logger;
    }

    // ---------------------------------------------------------------- loading

    /** (Re)loads every shop from the repository. */
    public void load() {
        for (Shop shop : shops.values()) {
            if (shop.permission() != null) {
                permissions.unregister(shop.permission());
            }
        }
        shops.clear();
        npcIndex.clear();
        for (Shop shop : repository.loadAll()) {
            shops.put(shop.id(), shop);
            for (String npcId : new ArrayList<>(shop.npcIds())) {
                String owner = npcIndex.putIfAbsent(npcId, shop.id());
                if (owner != null) {
                    logger.warning("NPC " + npcId + " is bound to both '" + owner + "' and '" + shop.id()
                            + "'; keeping '" + owner + "'");
                    shop.npcIds().remove(npcId);
                }
            }
            if (shop.permission() != null) {
                permissions.register(shop.permission());
            }
        }
    }

    // ---------------------------------------------------------------- shops

    public Collection<Shop> all() {
        return Collections.unmodifiableCollection(shops.values());
    }

    public Optional<Shop> find(String id) {
        return Optional.ofNullable(shops.get(Ids.normalize(id)));
    }

    public Shop require(String id) {
        return find(id).orElseThrow(() -> new ShopException("shop-not-found", "shop", id));
    }

    public Optional<Shop> byNpc(String npcId) {
        String shopId = npcIndex.get(npcId);
        return shopId == null ? Optional.empty() : Optional.ofNullable(shops.get(shopId));
    }

    public Shop create(String rawId) {
        String id = Ids.normalize(rawId);
        if (!Ids.isValidShopId(id)) {
            throw new ShopException("invalid-id", "id", rawId);
        }
        if (shops.containsKey(id)) {
            throw new ShopException("shop-exists", "shop", id);
        }
        Shop shop = new Shop(id);
        shops.put(id, shop);
        repository.save(shop);
        return shop;
    }

    public void delete(String id) {
        Shop shop = require(id);
        shops.remove(shop.id());
        shop.npcIds().forEach(npcIndex::remove);
        releasePermission(shop.permission());
        repository.delete(shop.id());
    }

    public void rename(String id, String displayName) {
        Shop shop = require(id);
        shop.displayName(displayName);
        repository.save(shop);
    }

    // ---------------------------------------------------------------- items

    public ShopItem addItem(String shopId, String rawKey, BigDecimal price, int amount, String itemId, String itemData) {
        Shop shop = require(shopId);
        String key = Ids.normalize(rawKey);
        if (!Ids.isValidItemKey(key)) {
            throw new ShopException("invalid-key", "key", rawKey);
        }
        checkPrice(price);
        checkAmount(amount);
        if (shop.item(key).isPresent()) {
            throw new ShopException("item-exists", "key", key);
        }
        ShopItem item = new ShopItem(key, null, price, amount, itemId, itemData);
        shop.items().add(item);
        repository.save(shop);
        return item;
    }

    public void removeItem(String shopId, String key) {
        Shop shop = require(shopId);
        ShopItem item = requireItem(shop, key);
        shop.items().remove(item);
        repository.save(shop);
    }

    public void setPrice(String shopId, String key, BigDecimal price) {
        Shop shop = require(shopId);
        checkPrice(price);
        requireItem(shop, key).price(price);
        repository.save(shop);
    }

    public void setAmount(String shopId, String key, int amount) {
        Shop shop = require(shopId);
        checkAmount(amount);
        requireItem(shop, key).amount(amount);
        repository.save(shop);
    }

    private ShopItem requireItem(Shop shop, String key) {
        return shop.item(Ids.normalize(key)).orElseThrow(() -> new ShopException("item-not-found", "key", key));
    }

    private static void checkPrice(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new ShopException("invalid-price");
        }
    }

    private static void checkAmount(int amount) {
        if (amount < 1 || amount > 2304) {
            throw new ShopException("invalid-amount");
        }
    }

    // ---------------------------------------------------------------- access permission

    /** Restricts the shop; {@code node == null} generates {@code <prefix><id>}. Returns the node in use. */
    public String restrict(String shopId, String node) {
        Shop shop = require(shopId);
        String chosen = node == null ? permissionPrefix.get() + shop.id() : Ids.normalize(node);
        if (!Ids.isValidPermission(chosen)) {
            throw new ShopException("invalid-permission", "permission", chosen);
        }
        String previous = shop.permission();
        shop.permission(chosen);
        permissions.register(chosen);
        if (previous != null && !previous.equals(chosen)) {
            releasePermission(previous);
        }
        repository.save(shop);
        return chosen;
    }

    public void unrestrict(String shopId) {
        Shop shop = require(shopId);
        String previous = shop.permission();
        shop.permission(null);
        releasePermission(previous);
        repository.save(shop);
    }

    private void releasePermission(String node) {
        if (node == null) {
            return;
        }
        for (Shop other : shops.values()) {
            if (node.equals(other.permission())) {
                return;
            }
        }
        permissions.unregister(node);
    }

    // ---------------------------------------------------------------- NPC bindings

    public void bindNpc(String shopId, String npcId) {
        Shop shop = require(shopId);
        String owner = npcIndex.get(npcId);
        if (owner != null) {
            throw new ShopException(owner.equals(shop.id()) ? "npc-already-bound-here" : "npc-already-bound",
                    "shop", owner);
        }
        shop.npcIds().add(npcId);
        npcIndex.put(npcId, shop.id());
        repository.save(shop);
    }

    public void unbindNpc(String shopId, String npcId) {
        Shop shop = require(shopId);
        if (!shop.npcIds().remove(npcId)) {
            throw new ShopException("npc-not-bound", "shop", shop.id());
        }
        npcIndex.remove(npcId);
        repository.save(shop);
    }

    /** Removes the binding of a NPC whatever the shop (used when the NPC itself is deleted). */
    public void unbindNpcEverywhere(String npcId) {
        String shopId = npcIndex.remove(npcId);
        if (shopId == null) {
            return;
        }
        Shop shop = shops.get(shopId);
        if (shop != null) {
            shop.npcIds().remove(npcId);
            repository.save(shop);
        }
    }
}
