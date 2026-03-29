package dev.hytalemodding.quality;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.config.QualityConfig;

import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Assigns a random quality tier to newly acquired eligible items.
 *
 * Quality is determined entirely by the item ID:
 * - Base items (e.g. "Weapon_Sword_Copper") are swapped to quality variants
 *   (e.g. "Weapon_Sword_Copper__rqc_Legendary") so the client shows the
 *   correct quality color/tooltip.
 * - All stat multipliers (damage, armor, tools, durability) are baked into
 *   variant Item assets by QualityTierMapper at startup.
 * - No metadata (BsonDocument) is used — quality can be read back from the item ID.
 *
 * Event handling:
 * - This class uses InventoryChangeEvent (ECS) to catch all item
 *   acquisition sources (crafting, loot pickup, trades, etc.).
 * - Slot modifications are done synchronously on the game thread.
 *
 * Registration (in plugin setup):
 * <pre>
 *   this.getEntityStoreRegistry().registerSystem(new QualityAssigner(...));
 * </pre>
 */
public final class QualityAssigner
        extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static final String LOG_PREFIX = "[RQC] Assigner: ";

    private final QualityRegistry registry;
    private final QualityConfig config;
    private final QualityTierMapper tierMapper;

    public QualityAssigner(@Nonnull QualityRegistry registry,
                           @Nonnull QualityConfig config,
                           @Nonnull QualityTierMapper tierMapper) {
        super(InventoryChangeEvent.class);
        this.registry = registry;
        this.config = config;
        this.tierMapper = tierMapper;
    }

    @Override
    public void onSystemRegistered() {
        // System registered in ECS
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  InventoryChangeEvent handler — all item acquisition sources
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull InventoryChangeEvent event) {
        Transaction transaction = event.getTransaction();
        if (!transaction.succeeded()) return;

        ItemContainer container = event.getItemContainer();

        if (transaction instanceof SlotTransaction slotTx) {
            // Covers SlotTransaction AND ItemStackSlotTransaction (subclass)
            handleSlotTransaction(slotTx, container);
        } else if (transaction instanceof ItemStackTransaction itemStackTx) {
            // Crafting and item-add operations — has getSlotTransactions()
            handleItemStackTransaction(itemStackTx, container);
        } else if (transaction instanceof MoveTransaction<?> moveTx) {
            // Item movement between containers
            handleMoveTransaction(moveTx, container);
        } else if (transaction instanceof ListTransaction<?> listTx) {
            // Batch transaction — contains a list of inner transactions
            handleListTransaction(listTx, container);
        } else {
            // Unknown transaction type — try scanning all slots
            scanContainerForUnqualifiedItems(container);
        }
    }

    private void handleSlotTransaction(@Nonnull SlotTransaction slotTx,
                                        @Nonnull ItemContainer container) {
        ItemStack itemAfter = slotTx.getSlotAfter();
        if (itemAfter == null || itemAfter.isEmpty()) return;

        tryAssignQuality(itemAfter, container, slotTx.getSlot());
    }

    /**
     * Handles ItemStackTransaction — the main transaction type for crafting
     * and adding items to inventory. Contains a list of ItemStackSlotTransactions.
     */
    private void handleItemStackTransaction(@Nonnull ItemStackTransaction itemStackTx,
                                             @Nonnull ItemContainer container) {
        List<ItemStackSlotTransaction> slotTxList = itemStackTx.getSlotTransactions();
        if (slotTxList == null || slotTxList.isEmpty()) return;

        for (ItemStackSlotTransaction slotTx : slotTxList) {
            if (!slotTx.succeeded()) continue;

            ItemStack itemAfter = slotTx.getSlotAfter();
            if (itemAfter == null || itemAfter.isEmpty()) continue;

            tryAssignQuality(itemAfter, container, slotTx.getSlot());
        }
    }

    /**
     * Handles MoveTransaction — item movement between containers.
     * The "add" part goes to the destination container.
     */
    @SuppressWarnings("unchecked")
    private void handleMoveTransaction(@Nonnull MoveTransaction<?> moveTx,
                                        @Nonnull ItemContainer container) {
        Transaction addTx = moveTx.getAddTransaction();
        if (addTx == null || !addTx.succeeded()) return;

        ItemContainer destContainer = moveTx.getOtherContainer();
        if (destContainer == null) destContainer = container;

        if (addTx instanceof SlotTransaction slotTx) {
            ItemStack itemAfter = slotTx.getSlotAfter();
            if (itemAfter != null && !itemAfter.isEmpty()) {
                tryAssignQuality(itemAfter, destContainer, slotTx.getSlot());
            }
        } else if (addTx instanceof ItemStackTransaction itemStackTx) {
            handleItemStackTransaction(itemStackTx, destContainer);
        }
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
                if (txObj instanceof SlotTransaction slotTx) {
                    if (!slotTx.succeeded()) continue;
                    ItemStack itemAfter = slotTx.getSlotAfter();
                    if (itemAfter == null || itemAfter.isEmpty()) continue;
                    tryAssignQuality(itemAfter, container, slotTx.getSlot());
                } else if (txObj instanceof ItemStackTransaction itemStackTx) {
                    if (!itemStackTx.succeeded()) continue;
                    handleItemStackTransaction(itemStackTx, container);
                } else if (txObj instanceof MoveTransaction<?> moveTx) {
                    if (!moveTx.succeeded()) continue;
                    handleMoveTransaction(moveTx, container);
                }
            }
        } catch (Exception e) {
            // ListTransaction reflection failed - skip silently
        }
    }

    /**
     * Fallback: Scans all slots in a container for unqualified eligible items.
     * Used when we encounter an unknown transaction type.
     */
    private void scanContainerForUnqualifiedItems(@Nonnull ItemContainer container) {
        try {
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack item = container.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;
                tryAssignQuality(item, container, slot);
            }
        } catch (Exception e) {
            // Container scan failed - skip silently
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Synchronous quality assignment
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Checks if this item needs quality assignment and, if so, swaps it
     * to a quality variant immediately on the game thread.
     */
    private void tryAssignQuality(@Nonnull ItemStack itemStack,
                                   @Nonnull ItemContainer container,
                                   short slot) {
        String itemId = itemStack.getItemId();
        if (itemId == null || itemId.isEmpty()) return;

        // Already a quality variant — nothing to do
        if (tierMapper.isVariant(itemId)) return;

        // Runtime ignore-list check — catches state variants (e.g.
        // *Tool_Watering_Can_State_Filled_Water) that share a prefix with
        // an ignored base item. Strips Hytale's '*' prefix before matching.
        if (QualityItemFactory.isIgnored(itemId)) return;

        // v1.x item (has quality suffix like _Legendary) — migrate to variant
        // Only if the extracted base ID is actually an eligible item
        boolean isV1Item = false;
        if (ItemQuality.fromItemId(itemId) != null) {
            String candidateBase = ItemQuality.extractBaseId(itemId);
            isV1Item = registry.isEligible(candidateBase);
        }

        // Eligible base item — assign random quality
        boolean isEligibleBase = !isV1Item && registry.isEligible(itemId);

        if (!isV1Item && !isEligibleBase) return;

        try {
            if (isV1Item) {
                migrateV1Item(itemStack, container, slot, itemId);
            } else {
                assignNewQuality(itemStack, container, slot, itemId);
            }
        } catch (Exception e) {
            // Assignment failed - skip silently
        }
    }

    /**
     * Migrates a v1.x item (quality suffix in ID) to a proper variant.
     * Preserves all metadata (e.g. enchantments from other mods) during migration.
     */
    private void migrateV1Item(@Nonnull ItemStack item,
                                @Nonnull ItemContainer container,
                                short slot,
                                @Nonnull String itemId) {
        ItemQuality v1Quality = ItemQuality.fromItemId(itemId);
        if (v1Quality == null) return;

        String baseId = ItemQuality.extractBaseId(itemId);
        String targetId = tierMapper.isInitialized()
                ? tierMapper.getVariantId(baseId, v1Quality) : baseId;

        // Preserve metadata from the original item (enchantments, etc.)
        BsonDocument originalMetadata = item.getMetadata();
        ItemStack migrated = new ItemStack(targetId, item.getQuantity(), originalMetadata);

        double srcMax = item.getMaxDurability();
        double tgtMax = migrated.getMaxDurability();
        if (srcMax > 0 && tgtMax > 0) {
            double ratio = item.getDurability() / srcMax;
            migrated = migrated.withDurability(tgtMax * ratio);
        }

        container.setItemStackForSlot(slot, migrated);
    }

    /**
     * Assigns a random quality to an eligible base item.
     * Preserves all metadata (e.g. enchantments from other mods) during assignment.
     */
    private void assignNewQuality(@Nonnull ItemStack item,
                                   @Nonnull ItemContainer container,
                                   short slot,
                                   @Nonnull String itemId) {
        ItemQuality quality = ItemQuality.random(config);

        String targetId = tierMapper.isInitialized()
                ? tierMapper.getVariantId(itemId, quality) : itemId;

        // Preserve metadata from the original item (enchantments, etc.)
        BsonDocument originalMetadata = item.getMetadata();
        ItemStack modified = new ItemStack(targetId, item.getQuantity(), originalMetadata);

        // Durability is already baked into the variant Item asset by
        // QualityTierMapper.applyDurabilityMultiplier(). Just set current
        // durability to the variant's (already-scaled) maxDurability.
        double variantMax = modified.getMaxDurability();
        if (variantMax > 0) {
            modified = modified.withDurability(variantMax);
        }

        container.setItemStackForSlot(slot, modified);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Static helpers — quality from item ID (used by other classes)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gets quality from an ItemStack by parsing its item ID.
     * Returns null if the item has no quality suffix.
     */
    @Nullable
    public static ItemQuality getQualityFromItem(@Nonnull ItemStack itemStack) {
        String itemId = itemStack.getItemId();
        if (itemId != null) {
            return ItemQuality.fromItemId(itemId);
        }
        return null;
    }

    /**
     * Gets the base (original) item ID from an ItemStack by stripping
     * the quality suffix from the item ID.
     */
    @Nonnull
    public static String getBaseItemId(@Nonnull ItemStack itemStack) {
        String itemId = itemStack.getItemId();
        if (itemId == null) return "";
        return ItemQuality.extractBaseId(itemId);
    }
}
