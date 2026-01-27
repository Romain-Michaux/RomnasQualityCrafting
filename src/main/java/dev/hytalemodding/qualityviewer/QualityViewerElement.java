package dev.hytalemodding.qualityviewer;

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

public class QualityViewerElement extends ChoiceElement {
    protected ItemStack itemStack;
    protected com.hypixel.hytale.server.core.inventory.ItemContext itemContext;
    protected QualityViewerPage page;

    public QualityViewerElement(ItemStack itemStack, com.hypixel.hytale.server.core.inventory.ItemContext itemContext, QualityViewerPage page) {
        this.itemStack = itemStack;
        this.itemContext = itemContext;
        this.page = page;
    }
    
    public void setPage(QualityViewerPage page) {
        this.page = page;
        // Update interaction with page reference
        if (this.interactions != null && this.interactions.length > 0 && this.interactions[0] instanceof ShowTooltipInteraction) {
            ((ShowTooltipInteraction) this.interactions[0]).setPage(page);
        }
    }

    public void addButton(@Nonnull UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String selector, PlayerRef playerRef) {
        String itemId = this.itemStack.getItemId();
        ItemQuality currentQuality = getQualityFromItemId(itemId);
        String qualityText = currentQuality != null ? currentQuality.getDisplayName() : "Unknown";
        String qualityColor = getQualityColor(currentQuality);
        
        // Get item info - store translation keys instead of strings
        String itemTranslationKey = this.itemStack.getItem().getTranslationKey();
        String baseItemId = QualityManager.getBaseItemId(itemId);
        
        // Get durability info
        double durability = this.itemStack.getDurability();
        double maxDurability = this.itemStack.getMaxDurability();
        String durabilityText = "";
        if (maxDurability > 0) {
            int current = (int) Math.ceil(durability);
            int max = (int) Math.ceil(maxDurability);
            durabilityText = current + "/" + max;
        }
        
        // Build stats text with better formatting
        StringBuilder statsText = new StringBuilder();
        if (currentQuality != null) {
            statsText.append("• Damage: ").append(String.format("%.1f", currentQuality.getDamageMultiplier())).append("x\n");
            statsText.append("• Durability: ").append(String.format("%.1f", currentQuality.getDurabilityMultiplier())).append("x");
        }
        
        // Append the item slot UI to #ElementList (like ItemReforgeElement)
        commandBuilder.append("#ElementList", "Pages/QualityViewerElement.ui");
        
        // Set the item icon
        commandBuilder.set(selector + " #ItemIcon.ItemId", this.itemStack.getItemId().toString());
        
        // Set item name
        commandBuilder.set(selector + " #ItemName.TextSpans", Message.translation(this.itemStack.getItem().getTranslationKey()));
        
        // Set quality with color using Message
        commandBuilder.set(selector + " #QualityLabel.TextSpans", Message.raw(qualityText).color(qualityColor));
        
        // Set durability
        if (!durabilityText.isEmpty()) {
            commandBuilder.set(selector + " #DurabilityLabel.Text", durabilityText);
        } else {
            commandBuilder.set(selector + " #DurabilityLabel.Text", "");
        }
        
        // Create interaction to show tooltip
        ShowTooltipInteraction tooltipInteraction = new ShowTooltipInteraction(
            itemTranslationKey, qualityText, qualityColor, statsText.toString(), this.itemContext, this.page
        );
        this.interactions = new ChoiceInteraction[]{tooltipInteraction};
    }
    
    private String getQualityColor(ItemQuality quality) {
        if (quality == null) {
            return "#ffffff";
        }
        switch (quality) {
            case POOR: return "#808080";      // Grey
            case COMMON: return "#ffffff";    // White
            case UNCOMMON: return "#00ff00";  // Green
            case RARE: return "#0080ff";      // Blue
            case EPIC: return "#8000ff";      // Purple
            case LEGENDARY: return "#ff8000";  // Orange
            default: return "#ffffff";
        }
    }
    
    private ItemQuality getQualityFromItemId(@Nonnull String itemId) {
        String baseId = QualityManager.getBaseItemId(itemId);
        if (baseId.equals(itemId)) {
            return null; // No quality found
        }
        
        String qualitySuffix = itemId.substring(baseId.length() + 1); // +1 for the "_"
        for (ItemQuality quality : ItemQuality.values()) {
            if (quality.getDisplayName().equals(qualitySuffix)) {
                return quality;
            }
        }
        return null;
    }
}
