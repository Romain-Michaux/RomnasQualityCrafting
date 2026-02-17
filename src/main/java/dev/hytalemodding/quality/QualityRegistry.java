package dev.hytalemodding.quality;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Scans loaded items at startup and builds a set of eligible base item IDs.
 *
 * In v2.0 metadata-only mode, we do NOT create quality variant items or inject
 * into the asset map. Quality is stored as metadata on ItemStack and stats are
 * applied at runtime via the ECS damage system.
 *
 * This class only tracks WHICH items are eligible for quality assignment.
 */
public final class QualityRegistry {

    private static final String LOG_PREFIX = "[RQC] Registry: ";

    /** Base item IDs that are eligible for quality (weapons, armor, tools). */
    private final Set<String> eligibleItemIds = new HashSet<>();

    /** Cached map of item ID → Item for runtime lookups. */
    private final Map<String, Item> itemCache = new HashMap<>();

    private int totalEligible = 0;
    private int totalScanned = 0;

    /**
     * Scans all loaded items and identifies those eligible for quality assignment.
     * Should be called once after assets are loaded.
     */
    public void scanEligibleItems() {
        long startTime = System.currentTimeMillis();

        Map<String, Item> allItems = getItemMap();
        if (allItems == null || allItems.isEmpty()) {
            System.out.println(LOG_PREFIX + "ERROR: Could not access Item asset map. Quality system inactive.");
            return;
        }

        totalScanned = allItems.size();
        System.out.println(LOG_PREFIX + "Scanning " + totalScanned + " loaded items for quality eligibility...");

        for (Map.Entry<String, Item> entry : allItems.entrySet()) {
            String itemId = entry.getKey();
            Item item = entry.getValue();

            if (item == null || item == Item.UNKNOWN) continue;

            // Skip quality variants (both v1.x suffixed and v2.0 variants)
            if (ItemQuality.hasQualitySuffix(itemId)) continue;

            if (QualityItemFactory.isEligibleForQuality(itemId, item)) {
                eligibleItemIds.add(itemId);
                itemCache.put(itemId, item);
                totalEligible++;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println(LOG_PREFIX + "Scan complete in " + elapsed + "ms.");
        System.out.println(LOG_PREFIX + "  Total scanned: " + totalScanned);
        System.out.println(LOG_PREFIX + "  Eligible items: " + totalEligible);
    }

    // ── Query methods ──

    /** Returns true if this item ID is eligible for quality assignment. */
    public boolean isEligible(@Nonnull String itemId) {
        return eligibleItemIds.contains(itemId);
    }

    /** Returns the cached Item object for the given ID, or null. */
    public Item getCachedItem(@Nonnull String itemId) {
        return itemCache.get(itemId);
    }

    /** Returns the set of all eligible item IDs (unmodifiable). */
    public Set<String> getEligibleItemIds() {
        return Collections.unmodifiableSet(eligibleItemIds);
    }

    public int getTotalEligible() { return totalEligible; }
    public int getTotalScanned() { return totalScanned; }

    // ── Asset map access (read-only, no injection) ──

    @SuppressWarnings("unchecked")
    private static Map<String, Item> getItemMap() {
        try {
            Method getAssetMap = Item.class.getMethod("getAssetMap");
            Object assetMapObj = getAssetMap.invoke(null);
            if (assetMapObj == null) return null;

            // DefaultAssetMap.getAssetMap() returns Collections.unmodifiableMap()
            // That's fine — we only need to READ for scanning, not modify.
            try {
                Method getMapMethod = assetMapObj.getClass().getMethod("getAssetMap");
                Object mapObj = getMapMethod.invoke(assetMapObj);
                if (mapObj instanceof Map) {
                    return (Map<String, Item>) mapObj;
                }
            } catch (Exception ignored) {
            }

            // Fallback: direct field access
            Class<?> current = assetMapObj.getClass();
            while (current != null && current != Object.class) {
                try {
                    Field assetMapField = current.getDeclaredField("assetMap");
                    assetMapField.setAccessible(true);
                    Object value = assetMapField.get(assetMapObj);
                    if (value instanceof Map) {
                        return (Map<String, Item>) value;
                    }
                } catch (NoSuchFieldException ignored) {
                }
                current = current.getSuperclass();
            }
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "Cannot access Item map: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        return null;
    }
}
