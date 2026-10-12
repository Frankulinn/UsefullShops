package fr.frankulinn.usefullshops.service;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Ring of the latest transactions, persisted in history.yml, with an optional full log file. */
public final class PurchaseHistory {

    public record Entry(Instant time, UUID playerId, String playerName, String shopId, String itemKey,
                        String itemId, int amount, BigDecimal price) {
    }

    private final Path file;
    private final Path logFile;
    private final Logger logger;
    private final ExecutorService io;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private int size = 100;
    private boolean logToFile;

    public PurchaseHistory(Path file, Path logFile, Logger logger, ExecutorService io) {
        this.file = file;
        this.logFile = logFile;
        this.logger = logger;
        this.io = io;
    }

    public void configure(int size, boolean logToFile) {
        this.size = Math.max(1, size);
        this.logToFile = logToFile;
        while (entries.size() > this.size) {
            entries.pollFirst();
        }
    }

    public void load() {
        entries.clear();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            for (Map<?, ?> map : yaml.getMapList("entries")) {
                entries.addLast(new Entry(
                        Instant.parse(String.valueOf(map.get("time"))),
                        UUID.fromString(String.valueOf(map.get("player-id"))),
                        String.valueOf(map.get("player")),
                        String.valueOf(map.get("shop")),
                        String.valueOf(map.get("item")),
                        String.valueOf(map.get("item-id")),
                        Integer.parseInt(String.valueOf(map.get("amount"))),
                        new BigDecimal(String.valueOf(map.get("price")))));
            }
            configure(size, logToFile);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Cannot read history.yml, starting with an empty history", e);
            entries.clear();
        }
    }

    public void record(Entry entry) {
        entries.addLast(entry);
        while (entries.size() > size) {
            entries.pollFirst();
        }
        String snapshot = serialize();
        io.execute(() -> write(file, snapshot, false));
        if (logToFile) {
            String line = entry.time() + " " + entry.playerName() + " (" + entry.playerId() + ") bought "
                    + entry.amount() + "x " + entry.itemId() + " [" + entry.shopId() + "/" + entry.itemKey()
                    + "] for " + entry.price().toPlainString() + System.lineSeparator();
            io.execute(() -> write(logFile, line, true));
        }
    }

    /** Latest entries, most recent first. */
    public List<Entry> recent(int count) {
        List<Entry> result = new ArrayList<>();
        var iterator = entries.descendingIterator();
        while (iterator.hasNext() && result.size() < count) {
            result.add(iterator.next());
        }
        return result;
    }

    private String serialize() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("time", e.time().toString());
            map.put("player", e.playerName());
            map.put("player-id", e.playerId().toString());
            map.put("shop", e.shopId());
            map.put("item", e.itemKey());
            map.put("item-id", e.itemId());
            map.put("amount", e.amount());
            map.put("price", e.price().toPlainString());
            list.add(map);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("entries", list);
        return yaml.saveToString();
    }

    private void write(Path target, String content, boolean append) {
        try {
            Files.createDirectories(target.getParent());
            if (append) {
                Files.writeString(target, content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.writeString(target, content, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot write " + target, e);
        }
    }
}
