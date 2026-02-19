package dev.hytalemodding.quality;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.system.EntityEventSystem;
import dev.hytalemodding.config.QualityConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * ECS event handler for crafting quality assignment.
 *
 * Listens to {@link CraftRecipeEvent.Post} which fires AFTER a craft completes.
 * The crafted item is already in the player's inventory when Post fires, so we
 * scan for unqualified base items matching the recipe output and swap them to
 * quality variants.
 *
 * Quality is determined entirely by item ID — no metadata is used.
 * Slot modifications are synchronous (runs on the game thread).
 *
 * Note: In practice, the {@link QualityAssigner} via LivingEntityInventoryChangeEvent
 * handles crafting as well. This system serves as a secondary path in case the
 * ECS event fires before the inventory change event for certain recipes.
 *
 * Registration (in plugin setup):
 * <pre>
 *   this.getEntityStoreRegistry().registerSystem(new CraftQualitySystem(...));
 * </pre>
 */
public final class CraftQualitySystem
        extends EntityEventSystem<EntityStore, CraftRecipeEvent.Post> {

    private static final String LOG_PREFIX = "[RQC] CraftSystem: ";

    private final QualityRegistry registry;
    private final QualityConfig config;
    private final QualityTierMapper tierMapper;

    public CraftQualitySystem(@Nonnull QualityRegistry registry,
                              @Nonnull QualityConfig config,
                              @Nonnull QualityTierMapper tierMapper) {
        super(CraftRecipeEvent.Post.class);
        this.registry = registry;
        this.config = config;
        this.tierMapper = tierMapper;
    }

    @Override
    public void onSystemRegistered() {
        // System registered in ECS
    }

    @Override
    protected boolean shouldProcessEvent(@Nonnull CraftRecipeEvent.Post event) {
        return true;
    }

    @Override
    public void handle(int index,
                       @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store,
                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull CraftRecipeEvent.Post event) {

        // ── Resolve the crafting Player from the ECS entity ──
        Player player;
        try {
            player = archetypeChunk.getComponent(index, Player.getComponentType());
        } catch (Exception e) {
            return;
        }
        if (player == null) {
            return;
        }

        // ── Extract recipe output info ──
        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe == null) {
            return;
        }

        MaterialQuantity primaryOutput = recipe.getPrimaryOutput();
        if (primaryOutput == null) {
            return;
        }

        String outputItemId = primaryOutput.getItemId();
        if (outputItemId == null || outputItemId.isEmpty()) {
            return;
        }

        // Skip if the output item is not eligible for quality
        if (!registry.isEligible(outputItemId)) {
            return;
        }

        int craftCount = event.getQuantity();
        int outputPerCraft = primaryOutput.getQuantity();
        int totalExpected = craftCount * Math.max(outputPerCraft, 1);

        // Assign quality synchronously — we're on the game thread
        try {
            assignQualityToCraftedItems(player, outputItemId, totalExpected);
        } catch (Exception ignored) {
        }
    }

    @Override
    public Query<EntityStore> getQuery() {
        // Use Query.any() to match ALL entities — ensures we receive events
        // even if the Player component type doesn't match the crafting entity's archetype.
        return Query.any();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Synchronous inventory scanning
    // ═══════════════════════════════════════════════════════════════════════════

    private void assignQualityToCraftedItems(@Nonnull Player player,
                                              @Nonnull String outputItemId,
                                              int totalExpected) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            return;
        }
        int remaining = totalExpected;
        remaining = assignQualityInContainer(inventory.getHotbar(), outputItemId, remaining, "hotbar");
        if (remaining > 0) {
            remaining = assignQualityInContainer(inventory.getStorage(), outputItemId, remaining, "storage");
        }
        if (remaining > 0) {
            assignQualityInContainer(inventory.getTools(), outputItemId, remaining, "tools");
        }
    }

    /**
     * Scans a container for base items matching the given ID that don't yet
     * have a quality variant, and assigns a random quality to each (up to maxCount).
     *
     * @return number of items still remaining to be processed
     */
    private int assignQualityInContainer(@Nullable ItemContainer container,
                                          @Nonnull String baseItemId,
                                          int maxCount,
                                          @Nonnull String containerName) {
        if (container == null || maxCount <= 0) return maxCount;

        short capacity = container.getCapacity();
        int remaining = maxCount;

        for (short slot = 0; slot < capacity && remaining > 0; slot++) {
            try {
                ItemStack item = container.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;

                String itemId = item.getItemId();
                if (!baseItemId.equals(itemId)) continue;

                // Skip if already a variant (already processed by QualityAssigner)
                if (tierMapper.isVariant(itemId)) continue;

                // This is a freshly crafted base item — assign quality
                ItemQuality quality = ItemQuality.random(config);

                String targetId = tierMapper.isInitialized()
                        ? tierMapper.getVariantId(baseItemId, quality) : baseItemId;

                ItemStack modified = new ItemStack(targetId, item.getQuantity());

                double variantMax = modified.getMaxDurability();
                if (variantMax > 0) {
                    modified = modified.withDurability(variantMax);
                }

                container.setItemStackForSlot(slot, modified);
                remaining--;

            } catch (Exception ignored) {
            }
        }

        return remaining;
    }
}
