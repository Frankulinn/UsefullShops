package fr.frankulinn.usefullshops.service;

import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.storage.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopServiceTest {

    private static final class MemoryRepository implements ShopRepository {
        final List<Shop> stored = new ArrayList<>();

        @Override
        public List<Shop> loadAll() {
            return new ArrayList<>(stored);
        }

        @Override
        public void save(Shop shop) {
        }

        @Override
        public void delete(String id) {
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeRegistry implements PermissionRegistry {
        final Set<String> nodes = new HashSet<>();

        @Override
        public void register(String node) {
            nodes.add(node);
        }

        @Override
        public void unregister(String node) {
            nodes.remove(node);
        }
    }

    private MemoryRepository repository;
    private FakeRegistry registry;
    private ShopService service;

    @BeforeEach
    void setUp() {
        repository = new MemoryRepository();
        registry = new FakeRegistry();
        service = new ShopService(repository, registry, () -> "usefullshops.shop.", Logger.getAnonymousLogger());
    }

    private static String key(ShopException e) {
        return e.key();
    }

    @Test
    void createNormalizesAndRejectsDuplicatesAndInvalidIds() {
        assertEquals("boulangerie", service.create("Boulangerie").id());
        assertEquals("shop-exists", key(assertThrows(ShopException.class, () -> service.create("BOULANGERIE"))));
        assertEquals("invalid-id", key(assertThrows(ShopException.class, () -> service.create("bad id"))));
        assertEquals("invalid-id", key(assertThrows(ShopException.class, () -> service.create("../x"))));
    }

    @Test
    void itemsAreValidated() {
        service.create("shop");
        service.addItem("shop", "bread", new BigDecimal("1.50"), 4, "minecraft:bread", "data");
        assertEquals("item-exists", key(assertThrows(ShopException.class,
                () -> service.addItem("shop", "bread", BigDecimal.ONE, 1, "x", "y"))));
        assertEquals("invalid-price", key(assertThrows(ShopException.class,
                () -> service.addItem("shop", "free", BigDecimal.ZERO, 1, "x", "y"))));
        assertEquals("invalid-amount", key(assertThrows(ShopException.class,
                () -> service.addItem("shop", "none", BigDecimal.ONE, 0, "x", "y"))));
        service.setPrice("shop", "bread", new BigDecimal("2"));
        assertEquals(new BigDecimal("2"), service.require("shop").item("bread").orElseThrow().price());
        service.removeItem("shop", "bread");
        assertTrue(service.require("shop").items().isEmpty());
    }

    @Test
    void anNpcBelongsToOneShopOnlyButAShopCanHaveSeveralNpcs() {
        service.create("a");
        service.create("b");
        service.bindNpc("a", "npc1");
        service.bindNpc("a", "npc2");
        assertEquals("npc-already-bound-here", key(assertThrows(ShopException.class, () -> service.bindNpc("a", "npc1"))));
        assertEquals("npc-already-bound", key(assertThrows(ShopException.class, () -> service.bindNpc("b", "npc1"))));
        assertEquals("a", service.byNpc("npc2").orElseThrow().id());

        service.unbindNpcEverywhere("npc1");
        assertTrue(service.byNpc("npc1").isEmpty());
        service.bindNpc("b", "npc1");
        assertEquals("b", service.byNpc("npc1").orElseThrow().id());
    }

    @Test
    void permissionIsGeneratedSharedAndReleased() {
        service.create("a");
        service.create("b");
        assertEquals("usefullshops.shop.a", service.restrict("a", null));
        assertTrue(registry.nodes.contains("usefullshops.shop.a"));

        service.restrict("a", "metier.boulanger");
        service.restrict("b", "metier.boulanger");
        assertFalse(registry.nodes.contains("usefullshops.shop.a"));

        service.unrestrict("a");
        assertTrue(registry.nodes.contains("metier.boulanger"), "still used by b");
        service.delete("b");
        assertFalse(registry.nodes.contains("metier.boulanger"));

        assertEquals("invalid-permission", key(assertThrows(ShopException.class, () -> service.restrict("a", "bad node"))));
    }

    @Test
    void deleteFreesNpcs() {
        service.create("a");
        service.bindNpc("a", "npc1");
        service.delete("a");
        assertTrue(service.byNpc("npc1").isEmpty());
        service.create("a");
        service.bindNpc("a", "npc1");
    }

    @Test
    void loadKeepsTheFirstOwnerOfAnNpcAndRegistersPermissions() {
        Shop first = new Shop("first");
        first.npcIds().add("npc1");
        first.permission("perm.first");
        Shop second = new Shop("second");
        second.npcIds().add("npc1");
        repository.stored.add(first);
        repository.stored.add(second);

        service.load();

        assertEquals("first", service.byNpc("npc1").orElseThrow().id());
        assertTrue(second.npcIds().isEmpty());
        assertTrue(registry.nodes.contains("perm.first"));
    }
}
