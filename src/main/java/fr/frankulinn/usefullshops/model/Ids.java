package fr.frankulinn.usefullshops.model;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation of the identifiers typed by administrators. */
public final class Ids {

    private static final Pattern SHOP_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern ITEM_KEY = Pattern.compile("[a-z0-9_-]{1,32}");
    private static final Pattern PERMISSION = Pattern.compile("[a-z0-9._-]{1,100}");

    private Ids() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT);
    }

    public static boolean isValidShopId(String id) {
        return SHOP_ID.matcher(id).matches();
    }

    public static boolean isValidItemKey(String key) {
        return ITEM_KEY.matcher(key).matches();
    }

    public static boolean isValidPermission(String node) {
        return PERMISSION.matcher(node).matches() && !node.startsWith(".") && !node.endsWith(".");
    }
}
