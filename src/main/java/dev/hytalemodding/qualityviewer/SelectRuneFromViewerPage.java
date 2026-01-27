package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.rune.ApplyRuneInteraction;
import dev.hytalemodding.rune.RuneManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Shown from the Quality Viewer when "Apply Rune" is clicked. Lists rune items in the
 * player's inventory that can be applied to the currently selected viewer item.
 * Choosing a rune applies it and refreshes the viewer.
 */
public class SelectRuneFromViewerPage extends ChoiceBasePage {

    public SelectRuneFromViewerPage(
            @Nonnull PlayerRef playerRef,
            @Nonnull ItemContainer[] itemContainers,
            @Nonnull ItemContext targetItemContext,
            @Nonnull QualityViewerPage viewerPage) {
        super(playerRef, buildRuneElements(itemContainers, targetItemContext, viewerPage), "Pages/ApplyRunePage.ui");
    }

    @Nonnull
    private static ChoiceElement[] buildRuneElements(
            @Nonnull ItemContainer[] itemContainers,
            @Nonnull ItemContext targetItemContext,
            @Nonnull QualityViewerPage viewerPage) {
        ItemStack target = targetItemContext.getItemStack();
        List<ChoiceElement> elements = new ObjectArrayList<>();
        for (ItemContainer container : itemContainers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (stack == null || stack.isEmpty()) continue;
                String runeId = RuneManager.runeIdFromItemId(stack.getItemId());
                if (runeId == null || !RuneManager.canApplyRuneTo(runeId, target)) continue;
                ItemContext runeContext = new ItemContext(container, slot, stack);
                ChoiceInteraction interaction = new ApplyRuneFromViewerChoiceInteraction(
                        targetItemContext, runeContext, runeId, viewerPage);
                elements.add(new SelectRuneChoiceElement(stack, interaction));
            }
        }
        return elements.toArray(new ChoiceElement[0]);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder,
                      @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        if (getElements().length > 0) {
            super.build(ref, commandBuilder, eventBuilder, store);
        } else {
            commandBuilder.append(getPageLayout());
            commandBuilder.clear("#ElementList");
            commandBuilder.appendInline("#ElementList",
                    "Label { Text: %server.customUI.qualityViewerPage.noRunesInInventory; Style: (Alignment: Center); }");
        }
    }

    private static final class SelectRuneChoiceElement extends ChoiceElement {
        private final ItemStack runeStack;

        SelectRuneChoiceElement(ItemStack runeStack, ChoiceInteraction interaction) {
            this.runeStack = runeStack;
            this.interactions = new ChoiceInteraction[]{interaction};
        }

        @Override
        public void addButton(@Nonnull UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String selector, PlayerRef playerRef) {
            commandBuilder.append("#ElementList", "Pages/ApplyRuneElement.ui");
            commandBuilder.set(selector + " #Icon.ItemId", runeStack.getItemId());
            commandBuilder.set(selector + " #Name.TextSpans", Message.translation(runeStack.getItem().getTranslationKey()));
            commandBuilder.set(selector + " #Detail.TextSpans", Message.raw("Click to apply").color("#96a9be"));
        }
    }

    private static final class ApplyRuneFromViewerChoiceInteraction extends ChoiceInteraction {
        private final ItemContext targetContext;
        private final ItemContext runeContext;
        private final String runeId;
        private final QualityViewerPage viewerPage;

        ApplyRuneFromViewerChoiceInteraction(ItemContext targetContext, ItemContext runeContext,
                                             String runeId, QualityViewerPage viewerPage) {
            this.targetContext = targetContext;
            this.runeContext = runeContext;
            this.runeId = runeId;
            this.viewerPage = viewerPage;
        }

        @Override
        public void run(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
            new ApplyRuneInteraction(targetContext, runeContext, runeId).run(store, ref, playerRef);
            viewerPage.refreshPage(ref, store);
        }
    }
}
