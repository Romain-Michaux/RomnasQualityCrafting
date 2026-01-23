package dev.hytalemodding.reforge;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class OpenReforgePageInteraction extends SimpleInstantInteraction {
    
    public static final BuilderCodec<OpenReforgePageInteraction> CODEC = BuilderCodec.builder(
            OpenReforgePageInteraction.class,
            OpenReforgePageInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(
            @NonNullDecl InteractionType interactionType,
            @NonNullDecl InteractionContext interactionContext,
            @NonNullDecl CooldownHandler cooldownHandler) {
        
        CommandBuffer<EntityStore> commandBuffer = interactionContext.getCommandBuffer();
        if (commandBuffer == null) {
            return;
        }

        Store<EntityStore> store = commandBuffer.getExternalData().getStore();
        Ref<EntityStore> ref = interactionContext.getEntity();
        
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }

        // Extract material from item ID or use default
        String material = "Crude"; // Default
        com.hypixel.hytale.server.core.inventory.ItemStack itemStack = interactionContext.getHeldItem();
        com.hypixel.hytale.server.core.inventory.ItemContext heldItemContext = null;
        
        if (itemStack != null) {
            String itemId = itemStack.getItemId();
            // Extract material from item ID (e.g., Tool_Reforge_Kit_Iron -> Iron)
            if (itemId.contains("_Reforge_Kit_")) {
                String[] parts = itemId.split("_Reforge_Kit_");
                if (parts.length > 1) {
                    material = parts[1];
                }
            }
            
            // Get held item context for consuming the kit
            // Find the slot containing the held item in both main inventory and hotbar
            ItemContainer mainInventory = player.getInventory().getStorage();
            ItemContainer hotbar = player.getInventory().getHotbar();
            
            ItemContainer heldItemContainer = null;
            short heldItemSlot = -1;
            
            // Search in main inventory first
            for (short i = 0; i < mainInventory.getCapacity(); i++) {
                ItemStack slotItem = mainInventory.getItemStack(i);
                if (slotItem != null && !slotItem.isEmpty() && slotItem.getItemId().equals(itemId)) {
                    // Check if it's the same item stack (same reference or same item)
                    if (ItemStack.isSameItemType(slotItem, itemStack)) {
                        heldItemContainer = mainInventory;
                        heldItemSlot = i;
                        break;
                    }
                }
            }
            
            // If not found in main inventory, search in hotbar
            if (heldItemContainer == null && hotbar != null) {
                for (short i = 0; i < hotbar.getCapacity(); i++) {
                    ItemStack slotItem = hotbar.getItemStack(i);
                    if (slotItem != null && !slotItem.isEmpty() && slotItem.getItemId().equals(itemId)) {
                        // Check if it's the same item stack (same reference or same item)
                        if (ItemStack.isSameItemType(slotItem, itemStack)) {
                            heldItemContainer = hotbar;
                            heldItemSlot = i;
                            break;
                        }
                    }
                }
            }
            
            if (heldItemContainer != null && heldItemSlot >= 0) {
                heldItemContext = new com.hypixel.hytale.server.core.inventory.ItemContext(heldItemContainer, heldItemSlot, itemStack);
            }
        }

        // Get player inventory containers (main inventory and hotbar) for scanning items
        ItemContainer mainInventory = player.getInventory().getStorage();
        ItemContainer hotbar = player.getInventory().getHotbar();
        
        // Create array of containers to scan
        ItemContainer[] containers;
        if (hotbar != null) {
            containers = new ItemContainer[]{mainInventory, hotbar};
        } else {
            containers = new ItemContainer[]{mainInventory};
        }
        
        // Create and open the reforge page
        if (heldItemContext != null) {
            ItemReforgePage page = new ItemReforgePage(playerRef, containers, material, heldItemContext);
            player.getPageManager().openCustomPage(ref, store, page);
        }
    }
}
