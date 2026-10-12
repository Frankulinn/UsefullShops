package fr.frankulinn.usefullshops.item;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

/**
 * Lossless item (de)serialization. The whole ItemStack, including data components and modded data,
 * goes through Paper's binary format, never through the Material alone.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemStack stack) {
        ItemStack copy = stack.clone();
        copy.setAmount(1);
        return Base64.getEncoder().encodeToString(copy.serializeAsBytes());
    }

    /** Empty when the data is corrupt or the item cannot be rebuilt (e.g. a removed mod). */
    public static Optional<ItemStack> decode(String data) {
        try {
            ItemStack stack = ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
            return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
        } catch (Exception | LinkageError e) {
            return Optional.empty();
        }
    }

    /** Registry id {@code namespace:path} for display purposes. */
    public static String registryId(ItemStack stack) {
        try {
            return stack.getType().getKey().toString();
        } catch (Exception e) {
            return stack.getType().name().toLowerCase(Locale.ROOT);
        }
    }
}
