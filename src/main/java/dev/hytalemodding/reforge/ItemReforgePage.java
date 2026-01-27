package dev.hytalemodding.reforge;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.quality.QualityManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.util.List;

public class ItemReforgePage extends ChoiceBasePage {
    private final String material;
    
    public ItemReforgePage(@Nonnull PlayerRef playerRef, @Nonnull ItemContainer[] itemContainers, @Nonnull String material, @Nonnull com.hypixel.hytale.server.core.inventory.ItemContext heldItemContext) {
        super(playerRef, getItemElements(itemContainers, material, heldItemContext), "Pages/ItemReforgePage.ui");
        this.material = material;
    }

    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        if (this.getElements().length > 0) {
            super.build(ref, commandBuilder, eventBuilder, store);
        } else {
            commandBuilder.append(this.getPageLayout());
            commandBuilder.clear("#ElementList");
            commandBuilder.appendInline("#ElementList", "Label { Text: %server.customUI.itemReforgePage.noItems; Style: (Alignment: Center); }");
        }
    }

    @Nonnull
    protected static ChoiceElement[] getItemElements(@Nonnull ItemContainer[] itemContainers, @Nonnull String material, @Nonnull ItemContext heldItemContext) {
        List<ChoiceElement> elements = new ObjectArrayList();

        // Scan all containers (main inventory and hotbar)
        for (ItemContainer itemContainer : itemContainers) {
            if (itemContainer == null) {
                continue;
            }
            
            for (short slot = 0; slot < itemContainer.getCapacity(); ++slot) {
                ItemStack itemStack = itemContainer.getItemStack(slot);
                if (!ItemStack.isEmpty(itemStack)) {
                    String itemId = itemStack.getItemId();
                    
                    // Vérifier que l'item a une qualité et peut être reforgé
                    if (QualityManager.hasQualityInId(itemId) && QualityManager.canHaveQuality(itemStack)) {
                        // Vérifier que l'item correspond au matériau du kit (simplifié)
                        if (matchesMaterial(itemId, material)) {
                            ItemContext itemContext = new ItemContext(itemContainer, slot, itemStack);
                            elements.add(new ItemReforgeElement(itemStack, new ReforgeItemInteraction(itemContext, heldItemContext)));
                        }
                    }
                }
            }
        }

        return (ChoiceElement[]) elements.toArray((x$0) -> new ChoiceElement[x$0]);
    }
    
    private static boolean matchesMaterial(@Nonnull String itemId, @Nonnull String material) {
        // Vérifier si l'item ID contient le matériau et se termine par une qualité
        // Format: Weapon_Sword_Iron_Common, Tool_Pickaxe_Crude_Common, Armor_Adamantite_Chest_Common
        
        // Vérifier que l'item ID contient le matériau
        if (!itemId.contains(material)) {
            return false;
        }
        
        // Vérifier que l'item se termine par une qualité connue
        String[] qualityNames = {"Junk", "Common", "Uncommon", "Rare", "Epic", "Legendary"};
        for (String quality : qualityNames) {
            if (itemId.endsWith("_" + quality)) {
                return true;
            }
        }
        
        return false;
    }
}
