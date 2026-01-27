package dev.hytalemodding.qualityviewer;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceBasePage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.inventory.ItemContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.quality.QualityManager;
import dev.hytalemodding.rune.RuneManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class QualityViewerPage extends ChoiceBasePage {
    private String selectedItemTranslationKey = "";
    private String selectedQuality = "";
    private String selectedQualityColor = "#ffffff";
    private String selectedStats = "";
    private ItemContext selectedItemContext = null;
    private String reforgeMaterial = null;
    private ItemContext reforgeItemContext = null;
    private final ItemContainer[] itemContainers;
    private final PlayerRef playerRef;

    public QualityViewerPage(@Nonnull PlayerRef playerRef, @Nonnull ItemContainer[] itemContainers) {
        super(playerRef, QualityViewerPage.createItemElements(itemContainers), "Pages/QualityViewerPage.ui");
        this.playerRef = playerRef;
        this.itemContainers = itemContainers;
    }
    
    @Nonnull
    private static ChoiceElement[] createItemElements(@Nonnull ItemContainer[] itemContainers) {
        // Create a temporary list - we'll update elements with page reference in build()
        List<ChoiceElement> elements = new ObjectArrayList();
        
        for (ItemContainer itemContainer : itemContainers) {
            if (itemContainer == null) {
                continue;
            }
            
            for (short slot = 0; slot < itemContainer.getCapacity(); ++slot) {
                ItemStack itemStack = itemContainer.getItemStack(slot);
                if (!ItemStack.isEmpty(itemStack)) {
                    String itemId = itemStack.getItemId();
                    
                    if (QualityManager.hasQualityInId(itemId) && QualityManager.canHaveQuality(itemStack)) {
                        ItemContext itemContext = new ItemContext(itemContainer, slot, itemStack);
                        // Pass null for page initially - will be set in build()
                        elements.add(new QualityViewerElement(itemStack, itemContext, null));
                    }
                }
            }
        }
        
        return (ChoiceElement[]) elements.toArray((x$0) -> new ChoiceElement[x$0]);
    }
    

    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        // Update all elements with page reference
        for (ChoiceElement element : this.getElements()) {
            if (element instanceof QualityViewerElement) {
                ((QualityViewerElement) element).setPage(this);
            }
        }
        
        if (this.getElements().length > 0) {
            super.build(ref, commandBuilder, eventBuilder, store);
        } else {
            commandBuilder.append(this.getPageLayout());
            commandBuilder.clear("#ElementList");
            commandBuilder.appendInline("#ElementList", "Label { Text: %server.customUI.qualityViewerPage.noItems; Style: (Alignment: Center); }");
        }
        
        // Update tooltip panel with selected item info - use TextSpans with Message for proper formatting
        if (!selectedItemTranslationKey.isEmpty()) {
            commandBuilder.set("#TooltipPanel.Visible", true);
            commandBuilder.set("#TooltipPanel #Header #TooltipName.TextSpans", Message.translation(selectedItemTranslationKey));
            commandBuilder.set("#TooltipPanel #Header #TooltipQuality.TextSpans", Message.raw(selectedQuality).color(selectedQualityColor));
            commandBuilder.set("#TooltipPanel #StatsSection #TooltipStats.Text", selectedStats);
            
            // Runecrafting: show applied rune and compatible runes
            updateRunecraftingSection(commandBuilder);
            
            // Update reforge cost and resource count
            updateReforgeInfo(ref, commandBuilder, eventBuilder, store);
        } else {
            commandBuilder.set("#TooltipPanel.Visible", false);
            commandBuilder.set("#Footer #CostLabel.Text", "");
            commandBuilder.set("#Footer #ResourceCount.Text", "");
            commandBuilder.set("#Footer #ReforgeButton.Visible", false);
        }

        // Wire footer/runecrafting buttons via UI event bindings (client sends event -> handleDataEvent)
        if (reforgeItemContext != null && reforgeMaterial != null) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#Footer #ReforgeButton",
                EventData.of("Action", "reforge"),
                true
            );
        }
        if (selectedItemContext != null && hasCompatibleRunes(selectedItemContext.getItemStack())) {
            eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                "#ApplyRuneButton",
                EventData.of("Action", "applyRune"),
                true
            );
        }
    }

    private static boolean hasCompatibleRunes(ItemStack stack) {
        return RuneManager.canApplyRuneTo(RuneManager.RUNE_BURN, stack)
            || RuneManager.canApplyRuneTo(RuneManager.RUNE_POISON, stack)
            || RuneManager.canApplyRuneTo(RuneManager.RUNE_LUCK, stack)
            || RuneManager.canApplyRuneTo(RuneManager.RUNE_SPEED, stack);
    }
    
    private void updateReforgeInfo(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        if (selectedItemContext == null) {
            commandBuilder.set("#Footer #CostLabel.Text", "");
            commandBuilder.set("#Footer #ResourceCount.Text", "");
            commandBuilder.set("#Footer #ReforgeButton.Visible", false);
            return;
        }
        
        ItemStack selectedItem = selectedItemContext.getItemStack();
        String itemId = selectedItem.getItemId();
        
        // Extract material from item ID
        String material = extractMaterialFromItemId(itemId);
        if (material == null) {
            commandBuilder.set("#Footer #CostLabel.Text", "Cannot determine material");
            commandBuilder.set("#Footer #ResourceCount.Text", "");
            commandBuilder.set("#Footer #ReforgeButton.Visible", false);
            return;
        }
        
        // Find reforge kit for this material
        String reforgeKitId = "Tool_Reforge_Kit_" + material;
        
        // Count available reforge kits in inventory
        int kitCount = countItemsInInventory(reforgeKitId);
        
        // Get kit name for display
        String kitName = reforgeKitId.replace("Tool_Reforge_Kit_", "");
        
        // Update UI
        commandBuilder.set("#Footer #CostLabel.Text", "1x " + kitName + " Reforge Kit");
        commandBuilder.set("#Footer #ResourceCount.Text", "Available: " + kitCount);
        commandBuilder.set("#Footer #ReforgeButton.Visible", true);
        // Note: Enabled property doesn't exist for TextButton, we'll handle validation in the interaction
        
        // Store reforge data for button click
        this.reforgeMaterial = material;
        this.reforgeItemContext = selectedItemContext;
    }
    
    private String extractMaterialFromItemId(String itemId) {
        // Extract material from item ID (e.g., Weapon_Sword_Iron_Common -> Iron)
        String[] materials = {"Crude", "Copper", "Iron", "Cobalt", "Mithril", "Thorium", "Adamantite"};
        for (String material : materials) {
            if (itemId.contains("_" + material + "_")) {
                return material;
            }
        }
        return null;
    }
    
    private int countItemsInInventory(String itemId) {
        int count = 0;
        for (ItemContainer container : itemContainers) {
            if (container == null) continue;
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!ItemStack.isEmpty(stack) && stack.getItemId().equals(itemId)) {
                    count += stack.getQuantity();
                }
            }
        }
        return count;
    }

    /** Runecrafting block uses fixed elements in the .ui; we only set content/visibility, no clear/appendInline. */
    private void updateRunecraftingSection(@Nonnull UICommandBuilder commandBuilder) {
        String base = "#TooltipPanel #RunecraftingSection #RunecraftingContent";
        String line1Sel = base + " #RunecraftingLine1";
        String line2Sel = base + " #RunecraftingLine2";
        String btnSel = base + " #ApplyRuneButton";
        if (selectedItemContext == null) {
            commandBuilder.set(line1Sel + ".TextSpans", Message.translation("server.customUI.qualityViewerPage.selectItemForRune"));
            commandBuilder.set(line2Sel + ".Text", "");
            commandBuilder.set(btnSel + ".Visible", false);
            return;
        }
        ItemStack stack = selectedItemContext.getItemStack();
        String applied = RuneManager.getAppliedRune(stack);
        String appliedKey = "server.customUI.qualityViewerPage.appliedRune" + (applied != null ? applied : "None");
        commandBuilder.set(line1Sel + ".TextSpans", Message.translation(appliedKey));

        List<String> compatible = new ArrayList<>();
        if (RuneManager.canApplyRuneTo(RuneManager.RUNE_BURN, stack)) compatible.add("Burn");
        if (RuneManager.canApplyRuneTo(RuneManager.RUNE_POISON, stack)) compatible.add("Poison");
        if (RuneManager.canApplyRuneTo(RuneManager.RUNE_LUCK, stack)) compatible.add("Luck");
        if (RuneManager.canApplyRuneTo(RuneManager.RUNE_SPEED, stack)) compatible.add("Speed");

        String compatKey = "server.customUI.qualityViewerPage.compatibleRunes"
            + (compatible.isEmpty() ? "None" : String.join("", compatible));
        commandBuilder.set(line2Sel + ".TextSpans", Message.translation(compatKey));

        boolean showButton = !compatible.isEmpty();
        commandBuilder.set(btnSel + ".Visible", showButton);
        if (showButton) {
            commandBuilder.set(btnSel + ".TextSpans", Message.translation("server.customUI.qualityViewerPage.applyRune"));
        }
    }
    
    public void setSelectedItem(String itemTranslationKey, String quality, String qualityColor, String stats, ItemContext itemContext) {
        this.selectedItemTranslationKey = itemTranslationKey;
        this.selectedQuality = quality;
        this.selectedQualityColor = qualityColor;
        this.selectedStats = stats;
        this.selectedItemContext = itemContext;
    }
    
    public void refreshPage(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        // Get fresh inventory containers
        com.hypixel.hytale.server.core.entity.entities.Player player = (com.hypixel.hytale.server.core.entity.entities.Player) store.getComponent(ref, com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player != null) {
            ItemContainer mainInventory = player.getInventory().getStorage();
            ItemContainer hotbar = player.getInventory().getHotbar();
            ItemContainer[] freshContainers;
            if (hotbar != null) {
                freshContainers = new ItemContainer[]{mainInventory, hotbar};
            } else {
                freshContainers = new ItemContainer[]{mainInventory};
            }
            
            // Recreate the page with updated inventory
            QualityViewerPage newPage = new QualityViewerPage(playerRef, freshContainers);
            player.getPageManager().openCustomPage(ref, store, newPage);
        }
    }
    
    public void performReforge(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef) {
        if (reforgeItemContext == null || reforgeMaterial == null) {
            return;
        }
        
        // Use ReforgeFromViewerInteraction to perform the reforge
        ReforgeFromViewerInteraction reforgeInteraction = new ReforgeFromViewerInteraction(reforgeItemContext, reforgeMaterial, this);
        reforgeInteraction.run(store, ref, playerRef);
    }
    
    public ItemContext getReforgeItemContext() {
        return reforgeItemContext;
    }
    
    public String getReforgeMaterial() {
        return reforgeMaterial;
    }

    /** Selected item in the tooltip panel; used by runecrafting "Apply Rune" flow. */
    public ItemContext getSelectedItemContext() {
        return selectedItemContext;
    }

    /**
     * Handles click events: Reforge/Apply Rune from our addEventBinding, rest (e.g. list item selection)
     * is passed to the parent so choice clicks still work.
     */
    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String raw) {
        String action = parseActionFromRawEvent(raw);
        if ("reforge".equals(action) || "applyRune".equals(action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            PlayerRef pr = player != null ? store.getComponent(ref, PlayerRef.getComponentType()) : null;
            if (player != null && pr != null) {
                if ("reforge".equals(action)) {
                    performReforge(ref, store, pr);
                } else {
                    openApplyRuneFromViewer(ref, store, pr, player);
                }
            }
            return;
        }
        // Let the parent handle list selection (choice) and any other events
        super.handleDataEvent(ref, store, raw);
    }

    /** Parse "Action" value from raw JSON-like event string sent by client. */
    private static String parseActionFromRawEvent(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        // Expect {"Action":"reforge"} or {"Action":"applyRune"} (key may be "Action" or "action")
        int i = raw.indexOf("\"Action\"");
        if (i < 0) i = raw.indexOf("\"action\"");
        if (i < 0) return null;
        int colon = raw.indexOf(':', i);
        if (colon < 0) return null;
        int start = raw.indexOf('"', colon);
        if (start < 0) return null;
        int end = raw.indexOf('"', start + 1);
        if (end < 0) return null;
        return raw.substring(start + 1, end);
    }

    private void openApplyRuneFromViewer(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull PlayerRef pr,
            @Nonnull Player player) {
        ItemContext targetContext = getSelectedItemContext();
        if (targetContext == null) return;
        ItemStack target = targetContext.getItemStack();
        if (!hasCompatibleRunes(target)) return;
        ItemContainer main = player.getInventory().getStorage();
        ItemContainer hotbar = player.getInventory().getHotbar();
        ItemContainer[] containers = hotbar != null
                ? new ItemContainer[]{main, hotbar}
                : new ItemContainer[]{main};
        SelectRuneFromViewerPage selectPage = new SelectRuneFromViewerPage(pr, containers, targetContext, this);
        player.getPageManager().openCustomPage(ref, store, selectPage);
    }

}
