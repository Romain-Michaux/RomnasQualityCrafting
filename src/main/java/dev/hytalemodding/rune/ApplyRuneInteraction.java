package dev.hytalemodding.rune;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TempAssetIdUtil;

import javax.annotation.Nonnull;

public class ApplyRuneInteraction extends ChoiceInteraction {
    private final ItemContext targetContext;
    private final ItemContext runeContext;
    private final String runeId;

    public ApplyRuneInteraction(ItemContext targetContext, ItemContext runeContext, String runeId) {
        this.targetContext = targetContext;
        this.runeContext = runeContext;
        this.runeId = runeId;
    }

    @Override
    public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        Player player = (Player) store.getComponent(ref, Player.getComponentType());
        PageManager pageManager = player.getPageManager();
        ItemStack target = targetContext.getItemStack();

        if (!RuneManager.canApplyRuneTo(runeId, target)) {
            playerRef.sendMessage(Message.raw("[Runecrafting] This rune cannot be applied to that item.").color("#ff0000"));
            pageManager.setPage(ref, store, Page.None);
            return;
        }

        ItemStack runeStack = runeContext.getItemStack();
        ItemStackSlotTransaction consume = runeContext.getContainer().removeItemStackFromSlot(runeContext.getSlot(), runeStack, 1);
        if (!consume.succeeded()) {
            pageManager.setPage(ref, store, Page.None);
            return;
        }

        ItemStack updated = RuneManager.withAppliedRune(target, runeId);
        ItemStackSlotTransaction replace = targetContext.getContainer().replaceItemStackInSlot(
                targetContext.getSlot(), target, updated);
        if (!replace.succeeded()) {
            SimpleItemContainer.addOrDropItemStack(store, ref, runeContext.getContainer(), runeContext.getSlot(), runeStack.withQuantity(1));
            playerRef.sendMessage(Message.raw("[Runecrafting] Failed to apply rune.").color("#ff0000"));
            pageManager.setPage(ref, store, Page.None);
            return;
        }

        playerRef.sendMessage(Message.raw("[Runecrafting] Rune applied successfully.").color("#00ff00"));
        pageManager.setPage(ref, store, Page.None);
        SoundUtil.playSoundEvent2d(ref, TempAssetIdUtil.getSoundEventIndex("SFX_Alchemy_Bench_Craft"), SoundCategory.UI, store);
    }
}
