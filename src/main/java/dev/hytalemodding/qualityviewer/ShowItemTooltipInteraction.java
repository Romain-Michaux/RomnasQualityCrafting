package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class ShowItemTooltipInteraction extends SimpleInstantInteraction {
    
    private final String itemId;
    private final String itemName;
    private final String quality;
    private final String description;
    private final String stats;
    
    public ShowItemTooltipInteraction(String itemId, String itemName, String quality, String description, String stats) {
        this.itemId = itemId;
        this.itemName = itemName;
        this.quality = quality;
        this.description = description;
        this.stats = stats;
    }
    
    public static final BuilderCodec<ShowItemTooltipInteraction> CODEC = BuilderCodec.builder(
            ShowItemTooltipInteraction.class,
            () -> new ShowItemTooltipInteraction("", "", "", "", ""),
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

        // Update the tooltip panel via UI commands
        // This would need to be done through the page's UI builder
        // For now, we'll store the data and let the page handle it
    }
    
    public String getItemId() { return itemId; }
    public String getItemName() { return itemName; }
    public String getQuality() { return quality; }
    public String getDescription() { return description; }
    public String getStats() { return stats; }
}
