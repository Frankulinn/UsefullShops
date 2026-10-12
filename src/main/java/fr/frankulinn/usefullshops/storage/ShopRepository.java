package fr.frankulinn.usefullshops.storage;

import fr.frankulinn.usefullshops.model.Shop;

import java.util.List;

public interface ShopRepository {

    List<Shop> loadAll();

    /** Takes a snapshot of the shop immediately; the disk write may happen later. */
    void save(Shop shop);

    void delete(String id);

    /** Waits for pending writes. */
    void close();
}
