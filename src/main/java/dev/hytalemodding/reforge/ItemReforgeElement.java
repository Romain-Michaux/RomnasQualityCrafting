package dev.hytalemodding.reforge;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import dev.hytalemodding.quality.ItemQuality;
import dev.hytalemodding.quality.QualityManager;

import javax.annotation.Nonnull;

public class ItemReforgeElement extends ChoiceElement {
    protected ItemStack itemStack;

    public ItemReforgeElement(ItemStack itemStack, ReforgeItemInteraction interaction) {
        this.itemStack = itemStack;
        this.interactions = new ChoiceInteraction[]{interaction};
    }

    public void addButton(@Nonnull UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String selector, PlayerRef playerRef) {
        String itemId = this.itemStack.getItemId();
        ItemQuality currentQuality = getQualityFromItemId(itemId);
        String qualityText = currentQuality != null ? currentQuality.getDisplayName() : "Unknown";
        
        commandBuilder.append("#ElementList", "Pages/ItemReforgeElement.ui");
        commandBuilder.set(selector + " #Icon.ItemId", this.itemStack.getItemId().toString());
        commandBuilder.set(selector + " #Name.TextSpans", Message.translation(this.itemStack.getItem().getTranslationKey()));
        commandBuilder.set(selector + " #Quality.Text", qualityText);
    }
    
    private ItemQuality getQualityFromItemId(@Nonnull String itemId) {
        String baseId = QualityManager.getBaseItemId(itemId);
        if (baseId.equals(itemId)) {
            return null; // Pas de qualité trouvée
        }
        
        String qualitySuffix = itemId.substring(baseId.length() + 1); // +1 pour le "_"
        for (ItemQuality quality : ItemQuality.values()) {
            if (quality.getDisplayName().equals(qualitySuffix)) {
                return quality;
            }
        }
        return null;
    }
}
