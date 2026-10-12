package fr.frankulinn.usefullshops.permission;

import fr.frankulinn.usefullshops.service.PermissionRegistry;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;

import java.util.HashSet;
import java.util.Set;

/** Registers shop permissions so that permission plugins can auto-complete them. */
public final class BukkitPermissionRegistry implements PermissionRegistry {

    private final PluginManager pluginManager;
    private final Set<String> registeredByUs = new HashSet<>();

    public BukkitPermissionRegistry(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public void register(String node) {
        if (pluginManager.getPermission(node) != null) {
            return; // declared by someone else (or already by us)
        }
        pluginManager.addPermission(new Permission(node, "Access to a UsefullShops shop", PermissionDefault.FALSE));
        registeredByUs.add(node);
    }

    @Override
    public void unregister(String node) {
        if (registeredByUs.remove(node)) {
            pluginManager.removePermission(node);
        }
    }

    public void unregisterAll() {
        for (String node : Set.copyOf(registeredByUs)) {
            unregister(node);
        }
    }
}
