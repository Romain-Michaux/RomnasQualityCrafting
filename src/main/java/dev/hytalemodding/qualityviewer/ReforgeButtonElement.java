package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class ReforgeButtonElement extends ChoiceElement {
    private final ItemContext itemContext;
    private final String material;
    private final QualityViewerPage page;
    
    public ReforgeButtonElement(ItemContext itemContext, String material, QualityViewerPage page) {
        this.itemContext = itemContext;
        this.material = material;
        this.page = page;
        ReforgeFromViewerInteraction interaction = new ReforgeFromViewerInteraction(itemContext, material, page);
        this.interactions = new ChoiceInteraction[]{interaction};
    }
    
    @Override
    public void addButton(@Nonnull UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String selector, PlayerRef playerRef) {
        // The button is already in the UI at selector "#Footer #ReforgeButton"
        // We need to register the interaction for this button
        // Since the button exists in the UI, we register the interaction directly
        if (this.interactions != null && this.interactions.length > 0) {
            // Register the interaction for the button selector
            // ChoiceBasePage should handle this, but we'll try to register it manually
            // The selector should be the full path to the button
            String buttonSelector = selector + " #ReforgeButton";
            // Note: eventBuilder might not have onClick method, so we rely on ChoiceBasePage
            // to handle interactions for elements that have them set
        }
    }
}
