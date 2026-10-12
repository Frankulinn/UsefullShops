package fr.frankulinn.usefullshops.service;

/** Business error carrying a message key and its placeholders (name, value pairs). */
public final class ShopException extends RuntimeException {

    private final String key;
    private final Object[] args;

    public ShopException(String key, Object... args) {
        super(key, null, false, false);
        this.key = key;
        this.args = args;
    }

    public String key() {
        return key;
    }

    public Object[] args() {
        return args;
    }
}
