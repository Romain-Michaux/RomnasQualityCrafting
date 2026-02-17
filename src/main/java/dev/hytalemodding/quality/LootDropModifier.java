package dev.hytalemodding.quality;

import com.hypixel.hytale.server.core.asset.type.item.config.ItemDrop;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemDropList;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ChoiceItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.ItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.MultipleItemDropContainer;
import com.hypixel.hytale.server.core.asset.type.item.config.container.SingleItemDropContainer;
import dev.hytalemodding.config.QualityConfig;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Modifies ItemDropList assets at startup so that eligible items drop as
 * quality variants instead of base items.
 *
 * For each SingleItemDropContainer whose ItemDrop.itemId matches an eligible
 * base item, the container is replaced with a ChoiceItemDropContainer containing
 * 6 SingleItemDropContainer children — one per quality tier — each weighted
 * by the loot config values.
 *
 * This ensures that loot drops are quality-aware with separate probability
 * distribution from crafting (configurable via LootWeight* in config.json).
 *
 * The QualityAssigner already handles items entering inventories; items that
 * arrive as quality variants (from modified drop tables) are recognized by
 * their quality suffix and skipped by the assigner (no double-assignment).
 */
public final class LootDropModifier {

    private static final String LOG_PREFIX = "[RQC] LootDrop: ";

    private final QualityConfig config;
    private final QualityTierMapper tierMapper;
    private final QualityRegistry registry;

    private int dropListsModified = 0;
    private int dropsReplaced = 0;

    public LootDropModifier(@Nonnull QualityConfig config,
                            @Nonnull QualityTierMapper tierMapper,
                            @Nonnull QualityRegistry registry) {
        this.config = config;
        this.tierMapper = tierMapper;
        this.registry = registry;
    }

    /**
     * Scans all ItemDropList assets and replaces eligible item drops
     * with weighted quality variant choices.
     */
    public void modifyDropLists() {
        if (!config.isLootQualityEnabled()) {
            System.out.println(LOG_PREFIX + "Loot quality is DISABLED in config. Skipping drop list modification.");
            return;
        }

        long startTime = System.currentTimeMillis();
        System.out.println(LOG_PREFIX + "Modifying drop lists for quality variants...");

        // Log loot weights for visibility
        System.out.println(LOG_PREFIX + "  Loot weights: Poor=" + config.getLootWeightPoor()
                + " Common=" + config.getLootWeightCommon()
                + " Uncommon=" + config.getLootWeightUncommon()
                + " Rare=" + config.getLootWeightRare()
                + " Epic=" + config.getLootWeightEpic()
                + " Legendary=" + config.getLootWeightLegendary());

        try {
            // Access all ItemDropList assets
            Map<String, ItemDropList> dropListMap = getDropListMap();
            if (dropListMap == null || dropListMap.isEmpty()) {
                System.out.println(LOG_PREFIX + "No ItemDropList assets found. Skipping.");
                return;
            }

            System.out.println(LOG_PREFIX + "  Found " + dropListMap.size() + " ItemDropList assets to scan.");

            for (Map.Entry<String, ItemDropList> entry : dropListMap.entrySet()) {
                String dropListId = entry.getKey();
                ItemDropList dropList = entry.getValue();
                if (dropList == null) continue;

                ItemDropContainer container = dropList.getContainer();
                if (container == null) continue;

                ItemDropContainer modified = processContainer(container);
                if (modified != container) {
                    // Container was replaced — update the ItemDropList
                    setFieldValue(dropList, "container", modified);
                    dropListsModified++;
                }
            }
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "ERROR during drop list modification: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println(LOG_PREFIX + "Drop list modification complete in " + elapsed + "ms.");
        System.out.println(LOG_PREFIX + "  Drop lists modified: " + dropListsModified);
        System.out.println(LOG_PREFIX + "  Individual drops replaced with quality choices: " + dropsReplaced);
    }

    // ── Container tree traversal ──

    /**
     * Recursively processes a container, replacing SingleItemDropContainers
     * that reference eligible items with ChoiceItemDropContainers.
     *
     * @return the same container if no changes, or a replacement container
     */
    private ItemDropContainer processContainer(ItemDropContainer container) {
        if (container instanceof SingleItemDropContainer) {
            return processSingleDrop((SingleItemDropContainer) container);
        } else if (container instanceof MultipleItemDropContainer) {
            processMultiple((MultipleItemDropContainer) container);
            return container;
        } else if (container instanceof ChoiceItemDropContainer) {
            processChoice((ChoiceItemDropContainer) container);
            return container;
        }
        // DroplistItemDropContainer: references another ItemDropList by ID —
        // that list will be processed on its own iteration, so nothing to do here.
        // EmptyItemDropContainer: nothing to do.
        return container;
    }

    /**
     * If this SingleItemDropContainer references an eligible item,
     * replaces it with a ChoiceItemDropContainer containing 6 quality variant drops.
     */
    private ItemDropContainer processSingleDrop(SingleItemDropContainer single) {
        ItemDrop drop = single.getDrop();
        if (drop == null) return single;

        String itemId = drop.getItemId();
        if (itemId == null || itemId.isEmpty()) return single;

        // Skip if already a quality variant
        if (tierMapper.isVariant(itemId)) return single;

        // Skip if not eligible for quality
        if (!registry.isEligible(itemId)) return single;

        // ── Build a ChoiceItemDropContainer with 6 quality variant drops ──
        return buildQualityChoice(itemId, drop, single);
    }

    /**
     * Processes containers inside a MultipleItemDropContainer array.
     * Replaces elements in the array in-place via reflection.
     */
    private void processMultiple(MultipleItemDropContainer multiple) {
        try {
            Field containersField = MultipleItemDropContainer.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            ItemDropContainer[] containers = (ItemDropContainer[]) containersField.get(multiple);
            if (containers == null) return;

            for (int i = 0; i < containers.length; i++) {
                if (containers[i] == null) continue;
                ItemDropContainer replacement = processContainer(containers[i]);
                if (replacement != containers[i]) {
                    containers[i] = replacement;
                }
            }
        } catch (Exception e) {
            // Reflection failed — skip this container
        }
    }

    /**
     * Processes containers inside a ChoiceItemDropContainer's WeightedMap.
     * Since WeightedMap is immutable, we rebuild it if any child was modified.
     */
    private void processChoice(ChoiceItemDropContainer choice) {
        try {
            Field containersField = ChoiceItemDropContainer.class.getDeclaredField("containers");
            containersField.setAccessible(true);
            Object weightedMap = containersField.get(choice);
            if (weightedMap == null) return;

            // IWeightedMap.internalKeys() returns the elements array
            java.lang.reflect.Method internalKeysMethod = weightedMap.getClass().getMethod("internalKeys");
            ItemDropContainer[] keys = (ItemDropContainer[]) internalKeysMethod.invoke(weightedMap);
            if (keys == null || keys.length == 0) return;

            boolean anyChanged = false;
            ItemDropContainer[] processed = new ItemDropContainer[keys.length];
            for (int i = 0; i < keys.length; i++) {
                if (keys[i] == null) {
                    processed[i] = null;
                    continue;
                }
                processed[i] = processContainer(keys[i]);
                if (processed[i] != keys[i]) {
                    anyChanged = true;
                }
            }

            if (anyChanged) {
                // Rebuild the WeightedMap with the modified containers
                // Use WeightedMap.builder(emptyArray).putAll(containers, getWeight).build()
                rebuildWeightedMap(containersField, choice, processed);
            }
        } catch (Exception e) {
            // Reflection failed — skip this choice container
        }
    }

    /**
     * Rebuilds the IWeightedMap for a ChoiceItemDropContainer with modified containers.
     */
    private void rebuildWeightedMap(Field containersField,
                                     ChoiceItemDropContainer choice,
                                     ItemDropContainer[] newContainers) {
        try {
            // WeightedMap.builder(new ItemDropContainer[0])
            java.lang.reflect.Method builderMethod = 
                com.hypixel.hytale.common.map.WeightedMap.class.getMethod("builder", Object[].class);
            Object builder = builderMethod.invoke(null, (Object) new ItemDropContainer[0]);

            // For each container, builder.put(container, container.getWeight())
            java.lang.reflect.Method putMethod = builder.getClass().getMethod("put", Object.class, double.class);
            for (ItemDropContainer c : newContainers) {
                if (c == null) continue;
                putMethod.invoke(builder, c, c.getWeight());
            }

            // builder.build()
            java.lang.reflect.Method buildMethod = builder.getClass().getMethod("build");
            Object newWeightedMap = buildMethod.invoke(builder);

            containersField.set(choice, newWeightedMap);
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "  Warning: failed to rebuild WeightedMap: " + e.getMessage());
        }
    }

    // ── Quality choice builder ──

    /**
     * Creates a ChoiceItemDropContainer containing 6 SingleItemDropContainers,
     * one per quality tier, weighted by the loot config.
     *
     * @param baseItemId  the original (base) item ID
     * @param originalDrop the original ItemDrop (used for quantity/metadata)
     * @param originalContainer the original SingleItemDropContainer (used for outer weight)
     */
    private ItemDropContainer buildQualityChoice(String baseItemId,
                                                  ItemDrop originalDrop,
                                                  SingleItemDropContainer originalContainer) {
        ItemDropContainer[] tierContainers = new ItemDropContainer[ItemQuality.values().length];
        int idx = 0;

        for (ItemQuality quality : ItemQuality.values()) {
            String variantId = tierMapper.getVariantId(baseItemId, quality);
            if (variantId == null) {
                // Variant not found — skip this tier
                continue;
            }

            // Create a new ItemDrop with the variant ID but same quantity/metadata
            ItemDrop variantDrop = new ItemDrop(
                    variantId,
                    originalDrop.getMetadata(),
                    originalDrop.getQuantityMin(),
                    originalDrop.getQuantityMax()
            );

            // Weight for this quality tier from loot config
            double tierWeight = quality.getLootWeight(config);

            // Create a SingleItemDropContainer for this variant
            SingleItemDropContainer tierContainer = new SingleItemDropContainer(variantDrop, tierWeight);

            tierContainers[idx++] = tierContainer;
        }

        if (idx == 0) {
            // No variants could be created — keep original
            return originalContainer;
        }

        // Trim array to actual size
        if (idx < tierContainers.length) {
            ItemDropContainer[] trimmed = new ItemDropContainer[idx];
            System.arraycopy(tierContainers, 0, trimmed, 0, idx);
            tierContainers = trimmed;
        }

        // Create a ChoiceItemDropContainer with the original container's weight
        // so it fits in the parent's probability correctly
        double outerWeight = originalContainer.getWeight();
        ChoiceItemDropContainer qualityChoice = new ChoiceItemDropContainer(tierContainers, outerWeight);

        dropsReplaced++;

        if (dropsReplaced <= 5) {
            System.out.println(LOG_PREFIX + "  Replaced drop: " + baseItemId
                    + " → 6 quality choices (weight=" + outerWeight + ")");
        }

        return qualityChoice;
    }

    // ── Asset map access ──

    /**
     * Gets the mutable map of all ItemDropList assets.
     * ItemDropList uses DefaultAssetMap, which has a mutable assetMap field.
     */
    @SuppressWarnings("unchecked")
    private Map<String, ItemDropList> getDropListMap() {
        try {
            Object assetMap = ItemDropList.getAssetMap();
            if (assetMap == null) return null;

            // DefaultAssetMap.getAssetMap() returns unmodifiable view.
            // We need the internal mutable map for reading.
            // Actually, we only need to read + modify individual ItemDropList objects
            // (not add/remove from the map), so the unmodifiable view is fine.
            java.lang.reflect.Method getMapMethod = assetMap.getClass().getMethod("getAssetMap");
            return (Map<String, ItemDropList>) getMapMethod.invoke(assetMap);
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "  Warning: failed to access ItemDropList map: " + e.getMessage());
            // Fallback: try getAllDrops or iterate via reflection
            return getDropListMapFallback();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, ItemDropList> getDropListMapFallback() {
        try {
            Object assetMap = ItemDropList.getAssetMap();
            if (assetMap == null) return null;

            // Try accessing the internal 'assetMap' field directly
            Field assetMapField = assetMap.getClass().getDeclaredField("assetMap");
            assetMapField.setAccessible(true);
            return (Map<String, ItemDropList>) assetMapField.get(assetMap);
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "  Warning: fallback map access also failed: " + e.getMessage());
            return null;
        }
    }

    // ── Reflection utility ──

    private static void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName + " not found in " + clazz.getName() + " hierarchy");
    }

    // ── Getters ──

    public int getDropListsModified() { return dropListsModified; }
    public int getDropsReplaced() { return dropsReplaced; }
}
