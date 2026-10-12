package fr.frankulinn.usefullshops.service;

/** Declares/removes the permission nodes of restricted shops on the server. */
public interface PermissionRegistry {

    void register(String node);

    void unregister(String node);
}
