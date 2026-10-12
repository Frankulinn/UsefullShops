package fr.frankulinn.usefullshops.npc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Players waiting to click a NPC to bind it to a shop (in memory, with expiry). */
public final class NpcSelection {

    private record Pending(String shopId, long expiresAt) {
    }

    private final Map<UUID, Pending> pending = new HashMap<>();

    public void start(UUID player, String shopId, long timeoutSeconds) {
        pending.put(player, new Pending(shopId, System.currentTimeMillis() + timeoutSeconds * 1000));
    }

    /** Returns and clears the pending shop id if it has not expired. */
    public Optional<String> take(UUID player) {
        Pending taken = pending.remove(player);
        if (taken == null || taken.expiresAt() < System.currentTimeMillis()) {
            return Optional.empty();
        }
        return Optional.of(taken.shopId());
    }

    public void cancel(UUID player) {
        pending.remove(player);
    }
}
