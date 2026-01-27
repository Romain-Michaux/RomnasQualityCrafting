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
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class OpenQualityViewerPageInteraction extends SimpleInstantInteraction {
    
    public static final BuilderCodec<OpenQualityViewerPageInteraction> CODEC = BuilderCodec.builder(
            OpenQualityViewerPageInteraction.class,
            OpenQualityViewerPageInteraction::new,
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
        
        // Create and open the quality viewer page
        QualityViewerPage page = new QualityViewerPage(playerRef, containers);
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
