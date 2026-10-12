package fr.frankulinn.usefullshops.storage;

import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.model.ShopItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlShopRepositoryTest {

    @TempDir
    Path dir;

    @Test
    void roundTrip() {
        YamlShopRepository repository = new YamlShopRepository(dir, Logger.getAnonymousLogger());
        Shop shop = new Shop("boulangerie");
        shop.displayName("<gold>Boulangerie");
        shop.permission("usefullshops.shop.boulangerie");
        shop.npcIds().add("npc-1");
        shop.npcIds().add("npc-2");
        ShopItem bread = new ShopItem("bread", null, new BigDecimal("12.50"), 4, "minecraft:bread", "QUJD");
        ShopItem cake = new ShopItem("cake", "<white>Gateau", new BigDecimal("3"), 1, "mod:cake", "REVG");
        shop.items().add(bread);
        shop.items().add(cake);
        repository.save(shop);
        repository.close();

        List<Shop> loaded = new YamlShopRepository(dir, Logger.getAnonymousLogger()).loadAll();

        assertEquals(1, loaded.size());
        Shop result = loaded.get(0);
        assertEquals("boulangerie", result.id());
        assertEquals("<gold>Boulangerie", result.displayName());
        assertEquals("usefullshops.shop.boulangerie", result.permission());
        assertEquals(List.of("npc-1", "npc-2"), List.copyOf(result.npcIds()));
        assertEquals(2, result.items().size());
        ShopItem b = result.item("bread").orElseThrow();
        assertEquals(new BigDecimal("12.50"), b.price());
        assertEquals(4, b.amount());
        assertEquals("minecraft:bread", b.itemId());
        assertEquals("QUJD", b.itemData());
        assertNull(b.displayName());
        assertEquals("<white>Gateau", result.item("cake").orElseThrow().displayName());
    }

    @Test
    void corruptFileDoesNotPreventOthersFromLoading() throws Exception {
        YamlShopRepository repository = new YamlShopRepository(dir, Logger.getAnonymousLogger());
        repository.save(new Shop("good"));
        repository.close();
        Files.writeString(dir.resolve("bad.yml"), "items:\n  - key: x\n    price: notanumber\n    amount: 1\n");

        List<Shop> loaded = new YamlShopRepository(dir, Logger.getAnonymousLogger()).loadAll();

        assertEquals(1, loaded.size());
        assertEquals("good", loaded.get(0).id());
    }

    @Test
    void deleteRemovesTheFile() {
        YamlShopRepository repository = new YamlShopRepository(dir, Logger.getAnonymousLogger());
        repository.save(new Shop("gone"));
        repository.delete("gone");
        repository.close();
        assertTrue(!Files.exists(dir.resolve("gone.yml")));
    }
}
