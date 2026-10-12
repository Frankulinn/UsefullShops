package fr.frankulinn.usefullshops;

import fr.frankulinn.usefullshops.api.UsefullShopsAPI;
import fr.frankulinn.usefullshops.command.UsCommand;
import fr.frankulinn.usefullshops.config.Messages;
import fr.frankulinn.usefullshops.economy.EconomyProvider;
import fr.frankulinn.usefullshops.economy.VaultEconomyProvider;
import fr.frankulinn.usefullshops.gui.MenuListener;
import fr.frankulinn.usefullshops.gui.MenuManager;
import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.npc.NpcListener;
import fr.frankulinn.usefullshops.npc.NpcSelection;
import fr.frankulinn.usefullshops.permission.Access;
import fr.frankulinn.usefullshops.permission.BukkitPermissionRegistry;
import fr.frankulinn.usefullshops.service.PurchaseHistory;
import fr.frankulinn.usefullshops.service.PurchaseService;
import fr.frankulinn.usefullshops.service.ShopService;
import fr.frankulinn.usefullshops.storage.YamlShopRepository;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class UsefullShopsPlugin extends JavaPlugin {

    private ExecutorService io;
    private YamlShopRepository repository;
    private BukkitPermissionRegistry permissionRegistry;
    private PurchaseHistory history;
    private ShopService shops;
    private Messages messages;
    private MenuManager menus;
    private EconomyProvider economy;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        messages.reload();

        io = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "UsefullShops-History");
            thread.setDaemon(true);
            return thread;
        });
        Path data = getDataFolder().toPath();

        economy = new VaultEconomyProvider(getServer().getServicesManager());
        permissionRegistry = new BukkitPermissionRegistry(getServer().getPluginManager());
        repository = new YamlShopRepository(data.resolve("shops"), getLogger());
        shops = new ShopService(repository, permissionRegistry,
                () -> getConfig().getString("permission-prefix", "usefullshops.shop."), getLogger());
        shops.load();

        history = new PurchaseHistory(data.resolve("history.yml"), data.resolve("logs").resolve("purchases.log"),
                getLogger(), io);
        history.configure(getConfig().getInt("history.size", 100), getConfig().getBoolean("history.log-to-file", false));
        history.load();

        PurchaseService purchases = new PurchaseService(economy, history, getLogger());
        menus = new MenuManager(this, shops, purchases, economy, messages);
        NpcSelection selection = new NpcSelection();

        getServer().getPluginManager().registerEvents(new MenuListener(menus), this);
        getServer().getPluginManager().registerEvents(new NpcListener(this, shops, menus, selection, messages), this);

        UsCommand command = new UsCommand(this, shops, menus, economy, history, selection, messages);
        PluginCommand pluginCommand = getCommand("usefullshops");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }

        getServer().getServicesManager().register(UsefullShopsAPI.class, new ApiImpl(), this, ServicePriority.Normal);

        // The economy plugin may enable after us: check once the server has finished loading.
        getServer().getScheduler().runTask(this, () -> {
            if (!economy.isAvailable()) {
                getLogger().severe("No Vault economy provider found: purchases are disabled until one is registered.");
            }
        });
        getLogger().info("Loaded " + shops.all().size() + " shop(s).");
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (permissionRegistry != null) {
            permissionRegistry.unregisterAll();
        }
        if (repository != null) {
            repository.close();
        }
        if (io != null) {
            io.shutdown();
            try {
                io.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Reloads config.yml, the messages and every shop from disk. */
    public void reloadAll() {
        reloadConfig();
        messages.reload();
        history.configure(getConfig().getInt("history.size", 100), getConfig().getBoolean("history.log-to-file", false));
        shops.load();
    }

    private final class ApiImpl implements UsefullShopsAPI {

        @Override
        public Set<String> getShopIds() {
            Set<String> ids = new TreeSet<>();
            shops.all().forEach(shop -> ids.add(shop.id()));
            return ids;
        }

        @Override
        public boolean shopExists(String shopId) {
            return shops.find(shopId).isPresent();
        }

        @Override
        public boolean canAccess(Player player, String shopId) {
            return shops.find(shopId).map((Shop shop) -> Access.canAccess(player, shop)).orElse(false);
        }

        @Override
        public boolean openShop(Player player, String shopId) {
            return shops.find(shopId).map(shop -> menus.openChecked(player, shop)).orElse(false);
        }
    }
}
