package fr.frankulinn.usefullshops.command;

import de.oliver.fancynpcs.api.FancyNpcsPlugin;
import de.oliver.fancynpcs.api.Npc;
import fr.frankulinn.usefullshops.UsefullShopsPlugin;
import fr.frankulinn.usefullshops.config.Messages;
import fr.frankulinn.usefullshops.economy.EconomyProvider;
import fr.frankulinn.usefullshops.gui.MenuManager;
import fr.frankulinn.usefullshops.item.ItemCodec;
import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.model.ShopItem;
import fr.frankulinn.usefullshops.npc.NpcSelection;
import fr.frankulinn.usefullshops.service.PurchaseHistory;
import fr.frankulinn.usefullshops.service.ShopException;
import fr.frankulinn.usefullshops.service.ShopService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** The /usefullshops command (aliases /us, /ushop, /shops). */
public final class UsCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN = "usefullshops.admin.";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    /** subcommand -> permission node */
    private static final Map<String, String> SUBCOMMANDS = Map.ofEntries(
            Map.entry("create", ADMIN + "create"),
            Map.entry("delete", ADMIN + "delete"),
            Map.entry("list", ADMIN + "list"),
            Map.entry("rename", ADMIN + "edit"),
            Map.entry("view", ADMIN + "view"),
            Map.entry("reload", ADMIN + "reload"),
            Map.entry("validate", ADMIN + "validate"),
            Map.entry("history", ADMIN + "history"),
            Map.entry("additem", ADMIN + "items"),
            Map.entry("removeitem", ADMIN + "items"),
            Map.entry("setprice", ADMIN + "items"),
            Map.entry("setamount", ADMIN + "items"),
            Map.entry("items", ADMIN + "items"),
            Map.entry("restrict", ADMIN + "permission"),
            Map.entry("unrestrict", ADMIN + "permission"),
            Map.entry("info", ADMIN + "permission"),
            Map.entry("npc", ADMIN + "npc"));

    private final UsefullShopsPlugin plugin;
    private final ShopService shops;
    private final MenuManager menus;
    private final EconomyProvider economy;
    private final PurchaseHistory history;
    private final NpcSelection selection;
    private final Messages messages;

    public UsCommand(UsefullShopsPlugin plugin, ShopService shops, MenuManager menus, EconomyProvider economy,
                     PurchaseHistory history, NpcSelection selection, Messages messages) {
        this.plugin = plugin;
        this.shops = shops;
        this.menus = menus;
        this.economy = economy;
        this.history = history;
        this.selection = selection;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String permission = SUBCOMMANDS.get(sub);
        if (permission == null) {
            messages.send(sender, "unknown-subcommand");
            return true;
        }
        if (!sender.hasPermission(permission)) {
            messages.send(sender, "no-permission");
            return true;
        }
        try {
            execute(sender, sub, Arrays.copyOfRange(args, 1, args.length));
        } catch (ShopException e) {
            messages.send(sender, e.key(), e.args());
        }
        return true;
    }

    private void execute(CommandSender sender, String sub, String[] a) {
        switch (sub) {
            case "create" -> {
                if (a.length < 1) {
                    usage(sender, "/us create <shop>");
                    return;
                }
                Shop shop = shops.create(a[0]);
                messages.send(sender, "shop-created", "shop", shop.id());
            }
            case "delete" -> {
                if (a.length < 1) {
                    usage(sender, "/us delete <shop> [confirm]");
                    return;
                }
                Shop shop = shops.require(a[0]);
                if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
                    messages.send(sender, "shop-delete-confirm", "shop", shop.id());
                    return;
                }
                shops.delete(shop.id());
                messages.send(sender, "shop-deleted", "shop", shop.id());
            }
            case "list" -> list(sender);
            case "rename" -> {
                if (a.length < 2) {
                    usage(sender, "/us rename <shop> <name>");
                    return;
                }
                shops.rename(a[0], String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
                messages.send(sender, "shop-renamed", "shop", shops.require(a[0]).id());
            }
            case "view" -> {
                if (a.length < 1 || !(sender instanceof Player player)) {
                    if (!(sender instanceof Player)) {
                        messages.send(sender, "player-only");
                    } else {
                        usage(sender, "/us view <shop>");
                    }
                    return;
                }
                menus.open(player, shops.require(a[0]), 0);
            }
            case "reload" -> {
                plugin.reloadAll();
                messages.send(sender, "reloaded");
            }
            case "validate" -> validate(sender);
            case "history" -> history(sender, a);
            case "additem" -> addItem(sender, a);
            case "removeitem" -> {
                if (a.length < 2) {
                    usage(sender, "/us removeitem <shop> <key>");
                    return;
                }
                shops.removeItem(a[0], a[1]);
                messages.send(sender, "item-removed", "key", a[1]);
            }
            case "setprice" -> {
                if (a.length < 3) {
                    usage(sender, "/us setprice <shop> <key> <price>");
                    return;
                }
                BigDecimal price = parsePrice(a[2]);
                shops.setPrice(a[0], a[1], price);
                messages.send(sender, "item-price-set", "key", a[1], "price", economy.format(price));
            }
            case "setamount" -> {
                if (a.length < 3) {
                    usage(sender, "/us setamount <shop> <key> <amount>");
                    return;
                }
                int amount = parseAmount(a[2]);
                shops.setAmount(a[0], a[1], amount);
                messages.send(sender, "item-amount-set", "key", a[1], "amount", amount);
            }
            case "items" -> items(sender, a);
            case "restrict" -> {
                if (a.length < 1) {
                    usage(sender, "/us restrict <shop> [permission]");
                    return;
                }
                String node = shops.restrict(a[0], a.length > 1 ? a[1] : null);
                messages.send(sender, "restrict-set", "shop", shops.require(a[0]).id(), "permission", node);
            }
            case "unrestrict" -> {
                if (a.length < 1) {
                    usage(sender, "/us unrestrict <shop>");
                    return;
                }
                shops.unrestrict(a[0]);
                messages.send(sender, "restrict-cleared", "shop", shops.require(a[0]).id());
            }
            case "info" -> info(sender, a);
            case "npc" -> npc(sender, a);
            default -> messages.send(sender, "unknown-subcommand");
        }
    }

    // ---------------------------------------------------------------- subcommands

    private void list(CommandSender sender) {
        if (shops.all().isEmpty()) {
            messages.send(sender, "shop-list-empty");
            return;
        }
        messages.send(sender, "shop-list-header");
        for (Shop shop : shops.all()) {
            messages.send(sender, "shop-list-entry",
                    "shop", shop.id(),
                    "items", shop.items().size(),
                    "npcs", shop.npcIds().size(),
                    "permission", shop.permission() == null ? "-" : shop.permission());
        }
    }

    private void addItem(CommandSender sender, String[] a) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return;
        }
        if (a.length < 3) {
            usage(sender, "/us additem <shop> <key> <price> [amount]");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.isEmpty()) {
            messages.send(sender, "hold-item");
            return;
        }
        BigDecimal price = parsePrice(a[2]);
        int amount = a.length > 3 ? parseAmount(a[3]) : 1;
        ShopItem item = shops.addItem(a[0], a[1], price, amount, ItemCodec.registryId(held), ItemCodec.encode(held));
        messages.send(sender, "item-added", "key", item.key(), "id", item.itemId(), "shop", shops.require(a[0]).id());
    }

    private void items(CommandSender sender, String[] a) {
        if (a.length < 1) {
            usage(sender, "/us items <shop>");
            return;
        }
        Shop shop = shops.require(a[0]);
        if (shop.items().isEmpty()) {
            messages.send(sender, "items-empty", "shop", shop.id());
            return;
        }
        messages.send(sender, "items-header", "shop", shop.id());
        for (ShopItem item : shop.items()) {
            messages.send(sender, "items-entry",
                    "key", item.key(), "id", item.itemId(), "amount", item.amount(),
                    "price", economy.format(item.price()));
        }
    }

    private void info(CommandSender sender, String[] a) {
        if (a.length < 1) {
            usage(sender, "/us info <shop>");
            return;
        }
        Shop shop = shops.require(a[0]);
        messages.send(sender, "info", "shop", shop.id(),
                "permission", shop.permission() == null ? "-" : shop.permission(),
                "items", shop.items().size(), "npcs", shop.npcIds().size());
    }

    private void validate(CommandSender sender) {
        int problems = 0;
        for (Shop shop : shops.all()) {
            for (ShopItem item : shop.items()) {
                if (ItemCodec.decode(item.itemData()).isEmpty()) {
                    problems++;
                    messages.send(sender, "validate-item-invalid",
                            "shop", shop.id(), "key", item.key(), "id", item.itemId());
                }
            }
            for (String npcId : shop.npcIds()) {
                if (FancyNpcsPlugin.get().getNpcManager().getNpcById(npcId) == null) {
                    problems++;
                    messages.send(sender, "validate-npc-missing", "shop", shop.id(), "npc", npcId);
                }
            }
        }
        if (problems == 0) {
            messages.send(sender, "validate-ok");
        }
    }

    private void history(CommandSender sender, String[] a) {
        int count = 10;
        if (a.length > 0) {
            try {
                count = Math.max(1, Math.min(100, Integer.parseInt(a[0])));
            } catch (NumberFormatException e) {
                usage(sender, "/us history [count]");
                return;
            }
        }
        List<PurchaseHistory.Entry> entries = history.recent(count);
        if (entries.isEmpty()) {
            messages.send(sender, "history-empty");
            return;
        }
        messages.send(sender, "history-header", "count", entries.size());
        for (PurchaseHistory.Entry e : entries) {
            messages.send(sender, "history-entry",
                    "time", TIME.format(e.time()), "player", e.playerName(), "shop", e.shopId(),
                    "item", e.itemId(), "amount", e.amount(), "price", economy.format(e.price()));
        }
    }

    private void npc(CommandSender sender, String[] a) {
        if (a.length < 2) {
            usage(sender, "/us npc <add|remove|list> <shop> [npc]");
            return;
        }
        String action = a[0].toLowerCase(Locale.ROOT);
        Shop shop = shops.require(a[1]);
        switch (action) {
            case "add" -> {
                if (a.length > 2) {
                    Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(a[2]);
                    if (npc == null) {
                        messages.send(sender, "npc-not-found", "npc", a[2]);
                        return;
                    }
                    shops.bindNpc(shop.id(), npc.getData().getId());
                    messages.send(sender, "npc-bound", "npc", npc.getData().getName(), "shop", shop.id());
                } else if (sender instanceof Player player) {
                    long timeout = plugin.getConfig().getLong("npc-selection-timeout", 30);
                    selection.start(player.getUniqueId(), shop.id(), timeout);
                    messages.send(sender, "npc-select-prompt", "shop", shop.id(), "seconds", timeout);
                } else {
                    messages.send(sender, "player-only");
                }
            }
            case "remove" -> {
                if (a.length < 3) {
                    usage(sender, "/us npc remove <shop> <npc>");
                    return;
                }
                String npcId = resolveNpcId(shop, a[2]);
                shops.unbindNpc(shop.id(), npcId);
                messages.send(sender, "npc-unbound", "npc", a[2], "shop", shop.id());
            }
            case "list" -> {
                if (shop.npcIds().isEmpty()) {
                    messages.send(sender, "npc-list-empty", "shop", shop.id());
                    return;
                }
                messages.send(sender, "npc-list-header", "shop", shop.id());
                for (String npcId : shop.npcIds()) {
                    Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpcById(npcId);
                    String where = "?";
                    String name = npcId;
                    if (npc != null) {
                        name = npc.getData().getName();
                        var loc = npc.getData().getLocation();
                        if (loc != null && loc.getWorld() != null) {
                            where = loc.getWorld().getName() + " " + loc.getBlockX() + " "
                                    + loc.getBlockY() + " " + loc.getBlockZ();
                        }
                    }
                    messages.send(sender, "npc-list-entry", "npc", name, "id", npcId, "location", where);
                }
            }
            default -> usage(sender, "/us npc <add|remove|list> <shop> [npc]");
        }
    }

    /** Accepts either the stored NPC id or the NPC name. */
    private String resolveNpcId(Shop shop, String input) {
        if (shop.npcIds().contains(input)) {
            return input;
        }
        Npc npc = FancyNpcsPlugin.get().getNpcManager().getNpc(input);
        return npc == null ? input : npc.getData().getId();
    }

    // ---------------------------------------------------------------- helpers

    private void sendHelp(CommandSender sender) {
        messages.send(sender, "help-header");
        SUBCOMMANDS.keySet().stream().sorted().forEach(sub -> {
            if (sender.hasPermission(SUBCOMMANDS.get(sub))) {
                messages.send(sender, "help-entry", "command", "/us " + sub, "description", messages.raw("help-" + sub));
            }
        });
    }

    private void usage(CommandSender sender, String usage) {
        messages.send(sender, "usage", "usage", usage);
    }

    private BigDecimal parsePrice(String raw) {
        try {
            BigDecimal price = economy.normalize(new BigDecimal(raw.replace(',', '.')));
            if (price.signum() <= 0) {
                throw new ShopException("invalid-price");
            }
            return price;
        } catch (NumberFormatException e) {
            throw new ShopException("invalid-price");
        }
    }

    private int parseAmount(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new ShopException("invalid-amount");
        }
    }

    // ---------------------------------------------------------------- tab completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            SUBCOMMANDS.forEach((sub, permission) -> {
                if (sender.hasPermission(permission)) {
                    options.add(sub);
                }
            });
            options.add("help");
        } else if (SUBCOMMANDS.containsKey(args[0].toLowerCase(Locale.ROOT))
                && sender.hasPermission(SUBCOMMANDS.get(args[0].toLowerCase(Locale.ROOT)))) {
            complete(args, options);
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted().toList();
    }

    private void complete(String[] args, List<String> options) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("npc")) {
            if (args.length == 2) {
                options.addAll(List.of("add", "remove", "list"));
            } else if (args.length == 3) {
                shops.all().forEach(shop -> options.add(shop.id()));
            } else if (args.length == 4) {
                String action = args[1].toLowerCase(Locale.ROOT);
                Optional<Shop> shop = shops.find(args[2]);
                if (action.equals("add")) {
                    FancyNpcsPlugin.get().getNpcManager().getAllNpcs().forEach(npc -> options.add(npc.getData().getName()));
                } else if (action.equals("remove") && shop.isPresent()) {
                    options.addAll(shop.get().npcIds());
                }
            }
            return;
        }
        if (args.length == 2) {
            if (sub.equals("reload") || sub.equals("list") || sub.equals("history") || sub.equals("validate")) {
                return;
            }
            shops.all().forEach(shop -> options.add(shop.id()));
        } else if (args.length == 3
                && (sub.equals("removeitem") || sub.equals("setprice") || sub.equals("setamount"))) {
            shops.find(args[1]).ifPresent(shop -> shop.items().forEach(item -> options.add(item.key())));
        }
    }
}
