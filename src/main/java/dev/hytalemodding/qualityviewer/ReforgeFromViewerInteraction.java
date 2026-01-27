package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;
import dev.hytalemodding.quality.ItemQuality;
import dev.hytalemodding.quality.QualityManager;

import javax.annotation.Nonnull;

public class ReforgeFromViewerInteraction extends ChoiceInteraction {
    private final ItemContext itemContext;
    private final String material;
    private final QualityViewerPage page;
    
    public ReforgeFromViewerInteraction(ItemContext itemContext, String material, QualityViewerPage page) {
        this.itemContext = itemContext;
        this.material = material;
        this.page = page;
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        PageManager pageManager = playerComponent.getPageManager();
        ItemStack itemStack = this.itemContext.getItemStack();
        
        // Verify item has quality
        String itemId = itemStack.getItemId();
        if (!QualityManager.hasQualityInId(itemId)) {
            playerRef.sendMessage(Message.raw("[Reforge] This item doesn't have a quality!").color("#ff0000"));
            return;
        }
        
        // Find reforge kit in inventory
        String reforgeKitId = "Tool_Reforge_Kit_" + material;
        ItemContainer mainInventory = playerComponent.getInventory().getStorage();
        ItemContainer hotbar = playerComponent.getInventory().getHotbar();
        
        ItemContainer kitContainer = null;
        short kitSlot = -1;
        ItemStack kitStack = null;
        
        // Search in main inventory
        for (short i = 0; i < mainInventory.getCapacity(); i++) {
            ItemStack stack = mainInventory.getItemStack(i);
            if (!ItemStack.isEmpty(stack) && stack.getItemId().equals(reforgeKitId)) {
                kitContainer = mainInventory;
                kitSlot = i;
                kitStack = stack;
                break;
            }
        }
        
        // Search in hotbar if not found
        if (kitContainer == null && hotbar != null) {
            for (short i = 0; i < hotbar.getCapacity(); i++) {
                ItemStack stack = hotbar.getItemStack(i);
                if (!ItemStack.isEmpty(stack) && stack.getItemId().equals(reforgeKitId)) {
                    kitContainer = hotbar;
                    kitSlot = i;
                    kitStack = stack;
                    break;
                }
            }
        }
        
        if (kitContainer == null || kitStack == null) {
            playerRef.sendMessage(Message.raw("[Reforge] You need a " + material + " Reforge Kit!").color("#ff0000"));
            return;
        }
        
        // Generate new random quality using config weights
        ItemQuality newQuality = ItemQuality.randomFromConfig();
        ItemStack reforgedItem = QualityManager.reforgeItem(itemStack, newQuality);
        
        if (reforgedItem == null || reforgedItem.getItemId().equals(itemId)) {
            playerRef.sendMessage(Message.raw("[Reforge] Failed to reforge item!").color("#ff0000"));
            return;
        }
        
        // Consume reforge kit
        ItemStackSlotTransaction kitTransaction = kitContainer.removeItemStackFromSlot(kitSlot, kitStack, 1);
        if (!kitTransaction.succeeded()) {
            playerRef.sendMessage(Message.raw("[Reforge] Failed to consume reforge kit!").color("#ff0000"));
            return;
        }
        
        // Replace item in inventory
        ItemStackSlotTransaction replaceTransaction = this.itemContext.getContainer().replaceItemStackInSlot(
            this.itemContext.getSlot(),
            itemStack,
            reforgedItem
        );
        
        if (!replaceTransaction.succeeded()) {
            // Restore kit if replacement failed
            SimpleItemContainer.addOrDropItemStack(store, ref, kitContainer, kitSlot, kitStack.withQuantity(1));
            playerRef.sendMessage(Message.raw("[Reforge] Failed to replace item in inventory!").color("#ff0000"));
            return;
        }
        
        // Success message
        playerRef.sendMessage(Message.join(
            Message.raw("[Reforge] Item reforged! New quality: "),
            Message.raw(newQuality.getDisplayName()).color(getQualityColor(newQuality))
        ));
        
        // Play sound
        SoundUtil.playSoundEvent2d(ref, TempAssetIdUtil.getSoundEventIndex("SFX_Item_Repair"), SoundCategory.UI, store);
        
        // Refresh the page to show updated items
        page.refreshPage(ref, store);
    }
    
    private String getQualityColor(@Nonnull ItemQuality quality) {
        switch (quality) {
            case POOR: return "#808080";
            case COMMON: return "#ffffff";
            case UNCOMMON: return "#00ff00";
            case RARE: return "#0080ff";
            case EPIC: return "#8000ff";
            case LEGENDARY: return "#ff8000";
            default: return "#ffffff";
        }
    }
}
