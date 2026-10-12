package fr.frankulinn.usefullshops.storage;

import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.model.ShopItem;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** One YAML file per shop: {@code <dir>/<id>.yml}. Writes are atomic and done off the main thread. */
public final class YamlShopRepository implements ShopRepository {

    private static final int FORMAT_VERSION = 1;

    private final Path directory;
    private final Logger logger;
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "UsefullShops-IO");
        thread.setDaemon(true);
        return thread;
    });

    public YamlShopRepository(Path directory, Logger logger) {
        this.directory = directory;
        this.logger = logger;
    }

    @Override
    public List<Shop> loadAll() {
        List<Shop> shops = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return shops;
        }
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().forEach(path -> {
                try {
                    shops.add(read(path));
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Cannot load shop file " + path.getFileName() + ", skipped", e);
                }
            });
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot list " + directory, e);
        }
        return shops;
    }

    private Shop read(Path path) {
        String name = path.getFileName().toString();
        Shop shop = new Shop(name.substring(0, name.length() - ".yml".length()));
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(path.toFile());
        shop.displayName(yaml.getString("display-name", shop.id()));
        shop.permission(yaml.getString("permission"));
        shop.npcIds().addAll(yaml.getStringList("npcs"));
        for (Map<?, ?> entry : yaml.getMapList("items")) {
            String key = String.valueOf(entry.get("key"));
            Object display = entry.get("display-name");
            BigDecimal price = new BigDecimal(String.valueOf(entry.get("price")));
            int amount = Integer.parseInt(String.valueOf(entry.get("amount")));
            shop.items().add(new ShopItem(
                    key,
                    display == null ? null : String.valueOf(display),
                    price,
                    amount,
                    String.valueOf(entry.get("item-id")),
                    String.valueOf(entry.get("item-data"))));
        }
        return shop;
    }

    @Override
    public void save(Shop shop) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("version", FORMAT_VERSION);
        yaml.set("display-name", shop.displayName());
        if (shop.permission() != null) {
            yaml.set("permission", shop.permission());
        }
        yaml.set("npcs", new ArrayList<>(shop.npcIds()));
        List<Map<String, Object>> items = new ArrayList<>();
        for (ShopItem item : shop.items()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("key", item.key());
            if (item.displayName() != null) {
                map.put("display-name", item.displayName());
            }
            map.put("price", item.price().toPlainString());
            map.put("amount", item.amount());
            map.put("item-id", item.itemId());
            map.put("item-data", item.itemData());
            items.add(map);
        }
        yaml.set("items", items);
        String content = yaml.saveToString();
        Path target = directory.resolve(shop.id() + ".yml");
        io.execute(() -> write(target, content));
    }

    @Override
    public void delete(String id) {
        Path target = directory.resolve(id + ".yml");
        io.execute(() -> {
            try {
                Files.deleteIfExists(target);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Cannot delete " + target, e);
            }
        });
    }

    private void write(Path target, String content) {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Cannot write " + target, e);
        }
    }

    @Override
    public void close() {
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Some shop files may not have been written before shutdown");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
