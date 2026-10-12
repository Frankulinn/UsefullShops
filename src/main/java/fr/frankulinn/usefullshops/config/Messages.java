package fr.frankulinn.usefullshops.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Localized MiniMessage texts from messages_&lt;lang&gt;.yml, with the bundled English file as fallback. */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String[] BUNDLED = {"messages_fr.yml", "messages_en.yml"};

    private final JavaPlugin plugin;
    private YamlConfiguration lang = new YamlConfiguration();
    private YamlConfiguration fallback = new YamlConfiguration();
    private String prefix = "";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        for (String name : BUNDLED) {
            if (!new File(plugin.getDataFolder(), name).exists()) {
                plugin.saveResource(name, false);
            }
        }
        fallback = bundled("messages_en.yml");
        String code = plugin.getConfig().getString("language", "fr").toLowerCase(Locale.ROOT);
        File file = new File(plugin.getDataFolder(), "messages_" + code + ".yml");
        if (file.isFile()) {
            lang = YamlConfiguration.loadConfiguration(file);
        } else {
            plugin.getLogger().warning("Unknown language '" + code + "', using English");
            lang = fallback;
        }
        prefix = plugin.getConfig().getString("prefix", "");
    }

    private YamlConfiguration bundled(String name) {
        try (Reader reader = new InputStreamReader(plugin.getResource(name), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception e) {
            return new YamlConfiguration();
        }
    }

    public String raw(String key) {
        String value = lang.getString(key);
        if (value == null) {
            value = fallback.getString(key);
        }
        return value == null ? key : value;
    }

    /** Parses a MiniMessage string typed by an administrator (shop names). */
    public Component parse(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /** Message without prefix. Placeholders are passed as name, value pairs; a Component value stays rich. */
    public Component component(String key, Object... placeholders) {
        return MM.deserialize(raw(key), resolver(placeholders));
    }

    public List<Component> components(String key, Object... placeholders) {
        List<Component> lines = new ArrayList<>();
        for (String line : raw(key).split("\n")) {
            lines.add(MM.deserialize(line, resolver(placeholders)));
        }
        return lines;
    }

    public void send(CommandSender target, String key, Object... placeholders) {
        target.sendMessage(MM.deserialize(prefix).append(component(key, placeholders)));
    }

    private static TagResolver resolver(Object[] pairs) {
        TagResolver.Builder builder = TagResolver.builder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String name = String.valueOf(pairs[i]);
            Object value = pairs[i + 1];
            builder.resolver(value instanceof Component component
                    ? Placeholder.component(name, component)
                    : Placeholder.unparsed(name, String.valueOf(value)));
        }
        return builder.build();
    }
}
