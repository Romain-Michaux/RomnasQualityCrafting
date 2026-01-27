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
        String qualityColor = getQualityColor(currentQuality);
        
        commandBuilder.append("#ElementList", "Pages/ItemReforgeElement.ui");
        commandBuilder.set(selector + " #Icon.ItemId", this.itemStack.getItemId().toString());
        commandBuilder.set(selector + " #Name.TextSpans", Message.translation(this.itemStack.getItem().getTranslationKey()));
        commandBuilder.set(selector + " #Quality.TextSpans", Message.raw(qualityText).color(qualityColor));
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
    
    private String getQualityColor(ItemQuality quality) {
        if (quality == null) {
            return "#ffffff";
        }
        
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
