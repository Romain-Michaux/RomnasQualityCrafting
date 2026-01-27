package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class ShowTooltipInteraction extends ChoiceInteraction {
    private final String itemTranslationKey;
    private final String quality;
    private final String qualityColor;
    private final String stats;
    private final com.hypixel.hytale.server.core.inventory.ItemContext itemContext;
    private QualityViewerPage page;
    
    public ShowTooltipInteraction(String itemTranslationKey, String quality, String qualityColor, String stats, com.hypixel.hytale.server.core.inventory.ItemContext itemContext, QualityViewerPage page) {
        this.itemTranslationKey = itemTranslationKey;
        this.quality = quality;
        this.qualityColor = qualityColor;
        this.stats = stats;
        this.itemContext = itemContext;
        this.page = page;
    }
    
    public void setPage(QualityViewerPage page) {
        this.page = page;
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        if (page != null) {
            // Update the page's selected item
            page.setSelectedItem(itemTranslationKey, quality, qualityColor, stats, itemContext);
            
            // Reopen the page with updated tooltip data
            Player player = (Player) store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                PageManager pageManager = player.getPageManager();
                pageManager.openCustomPage(ref, store, page);
            }
        }
    }
}
