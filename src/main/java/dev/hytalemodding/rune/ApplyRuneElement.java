package dev.hytalemodding.rune;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceInteraction;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public class ApplyRuneElement extends ChoiceElement {
    private final ItemStack itemStack;

    public ApplyRuneElement(ItemStack itemStack, ItemContext targetContext, ItemContext runeContext, String runeId) {
        this.itemStack = itemStack;
        this.interactions = new ChoiceInteraction[]{new ApplyRuneInteraction(targetContext, runeContext, runeId)};
    }

    @Override
    public void addButton(@Nonnull UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, String selector, PlayerRef playerRef) {
        commandBuilder.append("#ElementList", "Pages/ApplyRuneElement.ui");
        commandBuilder.set(selector + " #Icon.ItemId", itemStack.getItemId());
        commandBuilder.set(selector + " #Name.TextSpans", Message.translation(itemStack.getItem().getTranslationKey()));
        String runeInfo = RuneManager.getAppliedRune(itemStack);
        String detail = runeInfo != null ? "Rune: " + runeInfo : "";
        commandBuilder.set(selector + " #Detail.TextSpans", Message.raw(detail).color("#aaaaaa"));
    }
}
