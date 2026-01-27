package dev.hytalemodding.rune;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

public class OpenApplyRunePageInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<OpenApplyRunePageInteraction> CODEC = BuilderCodec.builder(
            OpenApplyRunePageInteraction.class,
            OpenApplyRunePageInteraction::new,
            SimpleInstantInteraction.CODEC
    ).build();

    @Override
    protected void firstRun(
            @NonNullDecl InteractionType interactionType,
            @NonNullDecl InteractionContext interactionContext,
            @NonNullDecl CooldownHandler cooldownHandler) {

        CommandBuffer<EntityStore> commandBuffer = interactionContext.getCommandBuffer();
        if (commandBuffer == null) return;

        Store<EntityStore> store = commandBuffer.getExternalData().getStore();
        Ref<EntityStore> ref = interactionContext.getEntity();

        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        ItemStack held = interactionContext.getHeldItem();
        if (held == null || held.isEmpty()) return;

        String runeId = RuneManager.runeIdFromItemId(held.getItemId());
        if (runeId == null) return;

        ItemContext heldContext = findHeldItemContext(player, held);
        if (heldContext == null) return;

        ItemContainer mainInventory = player.getInventory().getStorage();
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer[] containers = hotbar != null
                ? new ItemContainer[]{mainInventory, hotbar}
                : new ItemContainer[]{mainInventory};

        ApplyRunePage page = new ApplyRunePage(playerRef, containers, runeId, heldContext);
        player.getPageManager().openCustomPage(ref, store, page);
    }

    private static ItemContext findHeldItemContext(Player player, ItemStack held) {
        String itemId = held.getItemId();
        ItemContainer main = player.getInventory().getStorage();
        for (short i = 0; i < main.getCapacity(); i++) {
            ItemStack s = main.getItemStack(i);
            if (s != null && !s.isEmpty() && s.getItemId().equals(itemId) && ItemStack.isSameItemType(s, held)) {
                return new ItemContext(main, i, s);
            }
        }
        ItemContainer hotbar = player.getInventory().getHotbar();
        if (hotbar != null) {
            for (short i = 0; i < hotbar.getCapacity(); i++) {
                ItemStack s = hotbar.getItemStack(i);
                if (s != null && !s.isEmpty() && s.getItemId().equals(itemId) && ItemStack.isSameItemType(s, held)) {
                    return new ItemContext(hotbar, i, s);
                }
            }
        }
        return null;
    }
}
