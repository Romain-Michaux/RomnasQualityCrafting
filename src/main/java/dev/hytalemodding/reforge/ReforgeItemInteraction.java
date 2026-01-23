package dev.hytalemodding.reforge;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
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

public class ReforgeItemInteraction extends ChoiceInteraction {
    protected final ItemContext itemContext;
    protected final ItemContext heldItemContext;

    public ReforgeItemInteraction(ItemContext itemContext, ItemContext heldItemContext) {
        this.itemContext = itemContext;
        this.heldItemContext = heldItemContext;
    }

    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        Player playerComponent = (Player) store.getComponent(ref, Player.getComponentType());
        PageManager pageManager = playerComponent.getPageManager();
        ItemStack itemStack = this.itemContext.getItemStack();
        
        // Vérifier que l'item a une qualité
        String itemId = itemStack.getItemId();
        if (!QualityManager.hasQualityInId(itemId)) {
            playerRef.sendMessage(Message.raw("[Reforge] This item doesn't have a quality!").color("#ff0000"));
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        
        // Générer une nouvelle qualité aléatoire
        ItemQuality newQuality = ItemQuality.random();
        ItemStack reforgedItem = QualityManager.reforgeItem(itemStack, newQuality);
        
        if (reforgedItem == null || reforgedItem.getItemId().equals(itemId)) {
            playerRef.sendMessage(Message.raw("[Reforge] Failed to reforge item!").color("#ff0000"));
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        
        // Consommer le reforge kit (comme le repair kit)
        ItemContainer heldItemContainer = this.heldItemContext.getContainer();
        short heldItemSlot = this.heldItemContext.getSlot();
        ItemStack heldItemStack = this.heldItemContext.getItemStack();
        ItemStackSlotTransaction slotTransaction = heldItemContainer.removeItemStackFromSlot(heldItemSlot, heldItemStack, 1);
        if (!slotTransaction.succeeded()) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        
        // Remplacer l'item dans l'inventaire
        ItemStackSlotTransaction replaceTransaction = this.itemContext.getContainer().replaceItemStackInSlot(
            this.itemContext.getSlot(), 
            itemStack, 
            reforgedItem
        );
        
        if (!replaceTransaction.succeeded()) {
            // Si le remplacement échoue, remettre le kit dans l'inventaire
            SimpleItemContainer.addOrDropItemStack(store, ref, heldItemContainer, heldItemSlot, heldItemStack.withQuantity(1));
            playerRef.sendMessage(Message.raw("[Reforge] Failed to replace item in inventory!").color("#ff0000"));
            pageManager.setPage(ref, store, Page.None);
            return;
        }
        
        // Message de succès
        Message newItemStackMessage = Message.translation(reforgedItem.getItem().getTranslationKey());
        playerRef.sendMessage(Message.join(
            Message.raw("[Reforge] Item reforged! New quality: "),
            Message.raw(newQuality.getDisplayName()).color(getQualityColor(newQuality))
        ));
        
        pageManager.setPage(ref, store, Page.None);
        SoundUtil.playSoundEvent2d(ref, TempAssetIdUtil.getSoundEventIndex("SFX_Item_Repair"), SoundCategory.UI, store);
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
