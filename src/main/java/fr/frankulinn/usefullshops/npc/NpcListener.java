package fr.frankulinn.usefullshops.npc;

import de.oliver.fancynpcs.api.Npc;
import de.oliver.fancynpcs.api.actions.ActionTrigger;
import de.oliver.fancynpcs.api.events.NpcInteractEvent;
import de.oliver.fancynpcs.api.events.NpcRemoveEvent;
import fr.frankulinn.usefullshops.config.Messages;
import fr.frankulinn.usefullshops.gui.MenuManager;
import fr.frankulinn.usefullshops.model.Shop;
import fr.frankulinn.usefullshops.permission.Access;
import fr.frankulinn.usefullshops.service.ShopException;
import fr.frankulinn.usefullshops.service.ShopService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/** Bridges FancyNpcs and the shops: NPC click -> menu, NPC removal -> binding cleanup, binding by click. */
public final class NpcListener implements Listener {

    private final JavaPlugin plugin;
    private final ShopService shops;
    private final MenuManager menus;
    private final NpcSelection selection;
    private final Messages messages;

    public NpcListener(JavaPlugin plugin, ShopService shops, MenuManager menus, NpcSelection selection, Messages messages) {
        this.plugin = plugin;
        this.shops = shops;
        this.menus = menus;
        this.selection = selection;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(NpcInteractEvent event) {
        if (Bukkit.isPrimaryThread()) {
            handle(event);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> handle(event));
        }
    }

    private void handle(NpcInteractEvent event) {
        Player player = event.getPlayer();
        Npc npc = event.getNpc();
        String npcId = npc.getData().getId();

        Optional<String> pending = selection.take(player.getUniqueId());
        if (pending.isPresent()) {
            event.setCancelled(true);
            try {
                shops.bindNpc(pending.get(), npcId);
                messages.send(player, "npc-bound", "npc", npc.getData().getName(), "shop", pending.get());
            } catch (ShopException e) {
                messages.send(player, e.key(), e.args());
            }
            return;
        }

        ActionTrigger trigger = event.getInteractionType();
        if (trigger != ActionTrigger.RIGHT_CLICK && trigger != ActionTrigger.ANY_CLICK) {
            return;
        }
        Optional<Shop> shop = shops.byNpc(npcId);
        if (shop.isEmpty()) {
            return;
        }
        if (!player.hasPermission(Access.USE)) {
            messages.send(player, "access-denied");
            return;
        }
        menus.openChecked(player, shop.get());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemove(NpcRemoveEvent event) {
        shops.unbindNpcEverywhere(event.getNpc().getData().getId());
    }
}
