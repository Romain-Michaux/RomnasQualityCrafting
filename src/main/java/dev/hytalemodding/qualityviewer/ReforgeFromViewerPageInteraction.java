package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class ReforgeFromViewerPageInteraction extends SimpleInstantInteraction {
    
    public static final BuilderCodec<ReforgeFromViewerPageInteraction> CODEC = BuilderCodec.builder(
            ReforgeFromViewerPageInteraction.class,
            ReforgeFromViewerPageInteraction::new,
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
        
        // Get the current page and trigger reforge
        com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager pageManager = player.getPageManager();
        
        // Try to get current page - if it's QualityViewerPage, perform reforge
        try {
            // Try different method names that might exist
            java.lang.reflect.Method getCurrentPageMethod = null;
            try {
                getCurrentPageMethod = pageManager.getClass().getMethod("getCurrentPage", Ref.class, Store.class);
            } catch (NoSuchMethodException e1) {
                try {
                    getCurrentPageMethod = pageManager.getClass().getMethod("getCurrentPage");
                } catch (NoSuchMethodException e2) {
                    // Try to find any method that returns a page
                    for (java.lang.reflect.Method method : pageManager.getClass().getMethods()) {
                        if (method.getReturnType().getName().contains("Page") && method.getParameterCount() <= 2) {
                            getCurrentPageMethod = method;
                            break;
                        }
                    }
                }
            }
            
            if (getCurrentPageMethod != null) {
                Object currentPage;
                if (getCurrentPageMethod.getParameterCount() == 0) {
                    currentPage = getCurrentPageMethod.invoke(pageManager);
                } else {
                    currentPage = getCurrentPageMethod.invoke(pageManager, ref, store);
                }
                
                if (currentPage instanceof QualityViewerPage) {
                    QualityViewerPage page = (QualityViewerPage) currentPage;
                    ItemContext itemContext = page.getReforgeItemContext();
                    String material = page.getReforgeMaterial();
                    
                    if (itemContext != null && material != null) {
                        // Use the page's performReforge method
                        page.performReforge(ref, store, playerRef);
                        return;
                    } else {
                        playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Reforge] No item selected or material not found!").color("#ff0000"));
                        return;
                    }
                }
            }
        } catch (Exception e) {
            // Log error for debugging
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Reforge] Error: " + e.getMessage()).color("#ff0000"));
            e.printStackTrace();
        }
        
        // Fallback: show error message
        playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Reforge] Could not find page or item context!").color("#ff0000"));
    }
}
