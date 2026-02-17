package dev.hytalemodding.quality;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import dev.hytalemodding.config.QualityConfig;
import org.bson.BsonDocument;
import org.bson.BsonString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Listens to inventory change events and assigns a random quality to newly
 * acquired eligible items.
 *
 * v2.0 approach:
 * - Quality is stored as metadata on the ItemStack ("rqc_quality")
 * - Item ID is swapped to a quality variant (e.g. "Weapon_Sword_Copper__rqc_Rare")
 *   so that the client shows the correct quality color and tooltip
 * - Base item ID is stored in metadata ("rqc_base_id") for reverse lookup
 * - Stat multipliers (damage, armor) are applied at runtime via QualityDamageSystem
 * - Durability multiplier is applied here since it's an ItemStack property
 */
public final class QualityAssigner {

    private static final String LOG_PREFIX = "[RQC] Assigner: ";
    public static final String METADATA_KEY = "rqc_quality";
    public static final String BASE_ID_KEY = "rqc_base_id";

    private final QualityRegistry registry;
    private final QualityConfig config;
    private final QualityTierMapper tierMapper;

    public QualityAssigner(@Nonnull QualityRegistry registry,
                           @Nonnull QualityConfig config,
                           @Nonnull QualityTierMapper tierMapper) {
        this.registry = registry;
        this.config = config;
        this.tierMapper = tierMapper;
    }

    /**
     * Registers all event handlers with the event registry.
     */
    public void registerEvents(@Nonnull EventRegistry eventRegistry) {
        // Primary handler: inventory changes (catches crafting, loot, trades, etc.)
        eventRegistry.registerGlobal(
            LivingEntityInventoryChangeEvent.class,
            this::onInventoryChange
        );
    }

    // ── Inventory change handler ──

    private void onInventoryChange(@Nonnull LivingEntityInventoryChangeEvent event) {
        Transaction transaction = event.getTransaction();
        if (!transaction.succeeded()) return;

        LivingEntity entity = event.getEntity();
        ItemContainer container = event.getItemContainer();

        if (transaction instanceof SlotTransaction) {
            handleSlotTransaction((SlotTransaction) transaction, container);
        } else if (transaction instanceof ListTransaction) {
            handleListTransaction((ListTransaction<?>) transaction, container);
        }
    }

    private void handleSlotTransaction(@Nonnull SlotTransaction slotTx,
                                        @Nonnull ItemContainer container) {
        ItemStack itemAfter = slotTx.getSlotAfter();
        if (itemAfter == null || itemAfter.isEmpty()) return;

        tryAssignQuality(itemAfter, container, slotTx.getSlot());
    }

    @SuppressWarnings("unchecked")
    private void handleListTransaction(@Nonnull ListTransaction<?> listTx,
                                        @Nonnull ItemContainer container) {
        try {
            Field listField = listTx.getClass().getDeclaredField("list");
            listField.setAccessible(true);
            List<?> transactions = (List<?>) listField.get(listTx);

            if (transactions == null) return;

            for (Object txObj : transactions) {
                SlotTransaction slotTx = extractSlotTransaction(txObj);
                if (slotTx == null || !slotTx.succeeded()) continue;

                ItemStack itemAfter = slotTx.getSlotAfter();
                if (itemAfter == null || itemAfter.isEmpty()) continue;

                tryAssignQuality(itemAfter, container, slotTx.getSlot());
            }
        } catch (Exception ignored) {
            // Reflection failed — can't process list transaction
        }
    }

    /**
     * Attempts to extract a SlotTransaction from a potentially wrapped transaction object.
     */
    @SuppressWarnings("unchecked")
    private SlotTransaction extractSlotTransaction(Object txObj) {
        if (txObj instanceof SlotTransaction) {
            return (SlotTransaction) txObj;
        }

        // Try getSlotTransaction() method
        try {
            java.lang.reflect.Method m = txObj.getClass().getMethod("getSlotTransaction");
            return (SlotTransaction) m.invoke(txObj);
        } catch (Exception ignored) {
        }

        // Try slotTransactions field
        try {
            Field f = txObj.getClass().getDeclaredField("slotTransactions");
            f.setAccessible(true);
            List<SlotTransaction> list = (List<SlotTransaction>) f.get(txObj);
            if (list != null) {
                for (SlotTransaction st : list) {
                    if (st.succeeded()) return st;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    // ── Quality assignment logic ──

    /**
     * Core quality assignment logic.
     * Checks eligibility, rolls quality, stores as metadata, and swaps item ID
     * to a quality variant so the client shows the correct quality color/tooltip.
     *
     * Also handles on-the-fly migration of v1.x items (ID suffix → variant + metadata).
     */
    private void tryAssignQuality(@Nonnull ItemStack itemStack,
                                   @Nonnull ItemContainer container,
                                   short slot) {
        String itemId = itemStack.getItemId();
        if (itemId == null || itemId.isEmpty()) return;

        // Skip if already has quality metadata (already assigned or migrated)
        if (hasQualityMetadata(itemStack)) return;

        // Skip our own variant items — but add metadata if missing (e.g. from loot drops)
        if (tierMapper.isVariant(itemId)) {
            addMetadataToVariant(itemStack, container, slot, itemId);
            return;
        }

        // ── Check for v1.x item (has quality suffix in ID) → migrate to variant ──
        ItemQuality v1Quality = ItemQuality.fromItemId(itemId);
        if (v1Quality != null) {
            try {
                String baseId = ItemQuality.extractBaseId(itemId);
                // Use the variant ID if tier mapper is ready, otherwise just use baseId
                String targetId = tierMapper.isInitialized()
                        ? tierMapper.getVariantId(baseId, v1Quality) : baseId;

                ItemStack migrated = new ItemStack(targetId, itemStack.getQuantity());
                BsonDocument metadata = itemStack.getMetadata();
                BsonDocument newMeta = (metadata != null) ? metadata.clone() : new BsonDocument();
                newMeta.put(METADATA_KEY, new BsonString(v1Quality.name()));
                newMeta.put(BASE_ID_KEY, new BsonString(baseId));
                newMeta.put("rqc_migrated", new BsonString("v1_live"));
                migrated = migrated.withMetadata(newMeta);

                double srcMax = itemStack.getMaxDurability();
                double tgtMax = migrated.getMaxDurability();
                if (srcMax > 0 && tgtMax > 0) {
                    double ratio = itemStack.getDurability() / srcMax;
                    migrated = migrated.withMaxDurability(tgtMax).withDurability(tgtMax * ratio);
                }

                container.setItemStackForSlot(slot, migrated);;
            } catch (Exception e) {
                System.out.println(LOG_PREFIX + "failed to live-migrate " + itemId + ": " + e.getMessage());
            }
            return;
        }

        // Skip if this item ID is not eligible
        if (!registry.isEligible(itemId)) return;

        // Roll a random quality
        ItemQuality quality = ItemQuality.random(config);

        try {
            // Determine target item ID: use variant with correct quality tier
            String targetId = tierMapper.isInitialized()
                    ? tierMapper.getVariantId(itemId, quality) : itemId;

            // Create new ItemStack with variant ID + quality metadata
            ItemStack modified = new ItemStack(targetId, itemStack.getQuantity());
            BsonDocument metadata = itemStack.getMetadata();
            BsonDocument newMetadata = (metadata != null) ? metadata.clone() : new BsonDocument();
            newMetadata.put(METADATA_KEY, new BsonString(quality.name()));
            newMetadata.put(BASE_ID_KEY, new BsonString(itemId));
            modified = modified.withMetadata(newMetadata);

            // Apply durability multiplier (this IS an ItemStack property we can modify)
            float durMultiplier = quality.getDurabilityMultiplier(config);
            if (durMultiplier != 1.0f) {
                double baseMax = modified.getMaxDurability();
                if (baseMax > 0) {
                    double newMax = baseMax * durMultiplier;
                    modified = modified.withMaxDurability(newMax).withDurability(newMax);
                }
            }

            // Place the modified item back in the container
            container.setItemStackForSlot(slot, modified);
        } catch (Exception e) {
            // Silently fail — don't break the game
        }
    }

    // ── Metadata stamp for loot-dropped variants ──

    /**
     * Adds rqc_quality / rqc_base_id metadata to a variant item that was
     * dropped from a loot table (which has no metadata yet).
     * This ensures that all quality variant items in inventories have proper
     * metadata for reverse lookup and display.
     */
    private void addMetadataToVariant(@Nonnull ItemStack itemStack,
                                      @Nonnull ItemContainer container,
                                      short slot,
                                      @Nonnull String variantId) {
        // Already has metadata? Nothing to do.
        if (hasQualityMetadata(itemStack)) return;

        try {
            ItemQuality quality = ItemQuality.fromItemId(variantId);
            String baseId = ItemQuality.extractBaseId(variantId);
            if (quality == null || baseId.equals(variantId)) return;

            BsonDocument metadata = itemStack.getMetadata();
            BsonDocument newMeta = (metadata != null) ? metadata.clone() : new BsonDocument();
            newMeta.put(METADATA_KEY, new BsonString(quality.name()));
            newMeta.put(BASE_ID_KEY, new BsonString(baseId));
            newMeta.put("rqc_source", new BsonString("loot"));

            ItemStack stamped = new ItemStack(variantId, itemStack.getQuantity());
            stamped = stamped.withMetadata(newMeta);

            // Preserve durability
            double srcMax = itemStack.getMaxDurability();
            double tgtMax = stamped.getMaxDurability();
            if (srcMax > 0 && tgtMax > 0) {
                double ratio = itemStack.getDurability() / srcMax;
                stamped = stamped.withMaxDurability(tgtMax).withDurability(tgtMax * ratio);
            }

            container.setItemStackForSlot(slot, stamped);
        } catch (Exception e) {
            // Silently fail — item still works fine without metadata
        }
    }

    // ── Metadata helpers (static for use by other classes) ──

    /**
     * Checks if an ItemStack already has quality metadata.
     */
    public static boolean hasQualityMetadata(@Nonnull ItemStack itemStack) {
        try {
            BsonDocument metadata = itemStack.getMetadata();
            if (metadata == null) return false;
            return metadata.containsKey(METADATA_KEY);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets the quality from an ItemStack's metadata.
     * Returns null if no quality metadata is found.
     */
    @Nullable
    public static ItemQuality getQualityFromMetadata(@Nonnull ItemStack itemStack) {
        try {
            BsonDocument metadata = itemStack.getMetadata();
            if (metadata == null) return null;

            org.bson.BsonValue value = metadata.get(METADATA_KEY);
            if (value == null || !value.isString()) return null;

            return ItemQuality.valueOf(value.asString().getValue());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets quality from either metadata or item ID suffix (v1.x compat).
     * Tries metadata first, falls back to ID parsing.
     */
    @Nullable
    public static ItemQuality getQualityFromItem(@Nonnull ItemStack itemStack) {
        // Try metadata first (v2.0)
        ItemQuality fromMeta = getQualityFromMetadata(itemStack);
        if (fromMeta != null) return fromMeta;

        // Fall back to ID suffix parsing (works for both v1.x and v2.0 variants)
        String itemId = itemStack.getItemId();
        if (itemId != null) {
            return ItemQuality.fromItemId(itemId);
        }

        return null;
    }

    /**
     * Gets the base (original) item ID from an ItemStack.
     * Checks metadata first (rqc_base_id), then parses variant separator,
     * then v1.x suffix, and falls back to the raw item ID.
     */
    @Nonnull
    public static String getBaseItemId(@Nonnull ItemStack itemStack) {
        String itemId = itemStack.getItemId();
        if (itemId == null) return "";

        // Check metadata for stored base ID (most reliable)
        try {
            BsonDocument metadata = itemStack.getMetadata();
            if (metadata != null && metadata.containsKey(BASE_ID_KEY)) {
                org.bson.BsonValue val = metadata.get(BASE_ID_KEY);
                if (val != null && val.isString()) {
                    return val.asString().getValue();
                }
            }
        } catch (Exception ignored) {}

        // Fall back to suffix parsing (handles both v1.x and v2.0 variants)
        return ItemQuality.extractBaseId(itemId);
    }
}
