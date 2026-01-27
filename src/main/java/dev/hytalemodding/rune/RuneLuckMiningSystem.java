package dev.hytalemodding.rune;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * ECS system that grants one extra roll of ore/crystal drops when breaking blocks
 * with a pickaxe that has the Luck rune. Mirrors SimpleEnchantments EnchantmentFortuneSystem.
 */
public class RuneLuckMiningSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public RuneLuckMiningSystem() {
        super(BreakBlockEvent.class);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull BreakBlockEvent event) {

        if (event.isCancelled()) {
            return;
        }

        ItemStack tool = event.getItemInHand();
        if (tool == null || tool.isEmpty()) {
            return;
        }

        if (!RuneManager.isPickaxe(tool) || !RuneManager.RUNE_LUCK.equals(RuneManager.getAppliedRune(tool))) {
            return;
        }

        BlockType blockType = event.getBlockType();
        if (blockType == null) {
            return;
        }

        BlockGathering gathering = blockType.getGathering();
        BlockBreakingDropType breaking = gathering != null ? gathering.getBreaking() : null;
        if (breaking == null) {
            return;
        }

        // One extra roll of drops (Luck rune = double ore yield)
        List<ItemStack> extraDrops = new ArrayList<>();
        for (ItemStack drop : BlockHarvestUtils.getDrops(blockType, 1, breaking.getItemId(), breaking.getDropListId())) {
            if (drop != null && !drop.isEmpty() && isOreOrCrystalItem(drop.getItemId())) {
                extraDrops.add(drop);
            }
        }

        if (!extraDrops.isEmpty()) {
            Vector3i targetBlock = event.getTargetBlock();
            Vector3d dropPosition = new Vector3d(
                targetBlock.getX() + 0.5,
                targetBlock.getY(),
                targetBlock.getZ() + 0.5
            );
            Holder<EntityStore>[] itemEntities = ItemComponent.generateItemDrops(
                commandBuffer, extraDrops, dropPosition, Vector3f.ZERO
            );
            if (itemEntities.length > 0) {
                commandBuffer.addEntities(itemEntities, AddReason.SPAWN);
            }
        }
    }

    private static boolean isOreOrCrystalItem(String itemId) {
        if (itemId == null) return false;
        String lower = itemId.toLowerCase();
        return lower.contains("ore") || lower.contains("crystal");
    }
}
