package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

/**
 * Triggered when the user clicks "Apply Rune" in the Quality Viewer.
 * Opens SelectRuneFromViewerPage to pick a rune from inventory, then apply it to the selected item.
 */
public class ApplyRuneFromViewerPageInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<ApplyRuneFromViewerPageInteraction> CODEC = BuilderCodec.builder(
            ApplyRuneFromViewerPageInteraction.class,
            ApplyRuneFromViewerPageInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(
            @NonNullDecl InteractionType interactionType,
            @NonNullDecl InteractionContext interactionContext,
            @NonNullDecl CooldownHandler cooldownHandler) {

        CommandBuffer<EntityStore> commandBuffer = interactionContext.getCommandBuffer();
        if (commandBuffer == null) return;

        var store = commandBuffer.getExternalData().getStore();
        Ref<EntityStore> ref = interactionContext.getEntity();

        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        try {
            var pageManager = player.getPageManager();
            java.lang.reflect.Method getCurrentPageMethod = null;
            try {
                getCurrentPageMethod = pageManager.getClass().getMethod("getCurrentPage", Ref.class, Store.class);
            } catch (NoSuchMethodException e1) {
                try {
                    getCurrentPageMethod = pageManager.getClass().getMethod("getCurrentPage");
                } catch (NoSuchMethodException e2) {
                    for (java.lang.reflect.Method m : pageManager.getClass().getMethods()) {
                        if (m.getReturnType().getName().contains("Page") && m.getParameterCount() <= 2) {
                            getCurrentPageMethod = m;
                            break;
                        }
                    }
                }
            }
            if (getCurrentPageMethod == null) return;
            Object currentPage = getCurrentPageMethod.getParameterCount() == 0
                    ? getCurrentPageMethod.invoke(pageManager)
                    : getCurrentPageMethod.invoke(pageManager, ref, store);

            if (currentPage instanceof QualityViewerPage viewerPage) {
                ItemContext targetContext = viewerPage.getSelectedItemContext();
                if (targetContext == null) {
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Runecrafting] Select an item first.").color("#ff0000"));
                    return;
                }
                ItemStack target = targetContext.getItemStack();
                boolean canAccept = dev.hytalemodding.rune.RuneManager.canApplyRuneTo(dev.hytalemodding.rune.RuneManager.RUNE_BURN, target)
                        || dev.hytalemodding.rune.RuneManager.canApplyRuneTo(dev.hytalemodding.rune.RuneManager.RUNE_POISON, target)
                        || dev.hytalemodding.rune.RuneManager.canApplyRuneTo(dev.hytalemodding.rune.RuneManager.RUNE_LUCK, target)
                        || dev.hytalemodding.rune.RuneManager.canApplyRuneTo(dev.hytalemodding.rune.RuneManager.RUNE_SPEED, target);
                if (!canAccept) {
                    playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Runecrafting] This item cannot accept runes.").color("#ff0000"));
                    return;
                }

                ItemContainer main = player.getInventory().getStorage();
                ItemContainer hotbar = player.getInventory().getHotbar();
                ItemContainer[] containers = hotbar != null
                        ? new ItemContainer[]{main, hotbar}
                        : new ItemContainer[]{main};

                SelectRuneFromViewerPage selectPage = new SelectRuneFromViewerPage(playerRef, containers, targetContext, viewerPage);
                player.getPageManager().openCustomPage(ref, store, selectPage);
            }
        } catch (Exception e) {
            playerRef.sendMessage(com.hypixel.hytale.server.core.Message.raw("[Runecrafting] Error: " + e.getMessage()).color("#ff0000"));
        }
    }
}
