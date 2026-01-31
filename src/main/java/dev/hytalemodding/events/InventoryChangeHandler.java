package dev.hytalemodding.events;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import dev.hytalemodding.quality.ItemQuality;
import dev.hytalemodding.quality.QualityManager;

import javax.annotation.Nonnull;

public class InventoryChangeHandler {

    public static void onInventoryChange(@Nonnull LivingEntityInventoryChangeEvent event) {
        LivingEntity entity = event.getEntity();
        Transaction transaction = event.getTransaction();
        ItemContainer container = event.getItemContainer();

        if (!transaction.succeeded()) {
            return;
        }
        
        // Debug: Log all transactions to see what's happening
        // System.out.println("[RomnasQualityCrafting] DEBUG - Transaction type: " + transaction.getClass().getSimpleName() + ", succeeded: " + transaction.succeeded());
        
            // Si c'est un SlotTransaction, utiliser la méthode originale
        if (transaction instanceof SlotTransaction) {
            SlotTransaction slotTransaction = (SlotTransaction) transaction;
            ItemStack itemAfter = slotTransaction.getSlotAfter();
            
            if (itemAfter == null || itemAfter.isEmpty()) {
                return;
            }
            
            String itemId = itemAfter.getItemId();
            //System.out.println("[RomnasQualityCrafting] DEBUG SlotTransaction - Item ID: " + itemId);
            
            // IMPORTANT: Vérifier que l'item n'a pas déjà une qualité AVANT de le traiter
            // Cela évite la boucle infinie quand on modifie l'item
            if (QualityManager.getQualityFromItem(itemAfter) != null) {
                //System.out.println("[RomnasQualityCrafting] DEBUG - Item already has quality in metadata, skipping");
                return;
            }
            
            // Skip if item ID already ends with a quality name (e.g., Weapon_Sword_Copper_Common)
            if (QualityManager.hasQualityInId(itemId)) {
                //System.out.println("[RomnasQualityCrafting] DEBUG - Item ID already has quality suffix, skipping");
                return;
            }
            
            // Debug: Log item ID for troubleshooting
            if (itemId != null && (itemId.contains("Shovel") || itemId.contains("shovel"))) {
                //System.out.println("[RomnasQualityCrafting] DEBUG Shovel detected - ID: " + itemId);
                //System.out.println("[RomnasQualityCrafting] DEBUG - isTool: " + QualityManager.isTool(itemAfter));
                //System.out.println("[RomnasQualityCrafting] DEBUG - canHaveQuality: " + QualityManager.canHaveQuality(itemAfter));
                //.out.println("[RomnasQualityCrafting] DEBUG - hasJsonAssets: " + QualityManager.hasJsonAssetsForBaseItem(itemId));
            }
            
            // Vérifier que c'est une arme, armure ou outil avant de traiter
            if (!QualityManager.canHaveQuality(itemAfter)) {
                //System.out.println("[RomnasQualityCrafting] DEBUG - Item cannot have quality: " + itemId);
                return;
            }
            
            // Vérifier si des assets JSON existent pour cet item avant de traiter
            // Si non, ne pas modifier l'item (évite les crashes avec les flèches, etc.)
            if (!QualityManager.hasJsonAssetsForBaseItem(itemId)) {
                //System.out.println("[RomnasQualityCrafting] DEBUG - No JSON assets found for: " + itemId);
                return;
            }
            
            //System.out.println("[RomnasQualityCrafting] DEBUG - Processing item: " + itemId);
            processItem(itemAfter, container, slotTransaction.getSlot(), entity);
            return;
        }
        
        // Si c'est une ListTransaction (utilisée pour le crafting), traiter chaque slot
        if (transaction instanceof ListTransaction) {
            @SuppressWarnings("unchecked")
            ListTransaction<?> listTransaction = (ListTransaction<?>) transaction;
            
            // Accéder au champ 'list' directement via réflexion
            try {
                java.lang.reflect.Field listField = listTransaction.getClass().getDeclaredField("list");
                listField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<?> transactions = (java.util.List<?>) listField.get(listTransaction);
                
                if (transactions != null) {
                    for (Object transactionObj : transactions) {
                        // La liste peut contenir différents types : SlotTransaction, ItemStackSlotTransaction, etc.
                        SlotTransaction slotTransaction = null;
                        
                        // Si c'est directement un SlotTransaction
                        if (transactionObj instanceof SlotTransaction) {
                            slotTransaction = (SlotTransaction) transactionObj;
                        } else {
                            // Sinon, essayer d'extraire le SlotTransaction depuis ItemStackSlotTransaction
                            try {
                                java.lang.reflect.Method getSlotTransactionMethod = transactionObj.getClass().getMethod("getSlotTransaction");
                                slotTransaction = (SlotTransaction) getSlotTransactionMethod.invoke(transactionObj);
                            } catch (Exception e) {
                                // Essayer d'autres méthodes pour obtenir le SlotTransaction
                                try {
                                    // Peut-être que c'est dans slotTransactions (pluriel)
                                    java.lang.reflect.Field slotTransactionsField = transactionObj.getClass().getDeclaredField("slotTransactions");
                                    slotTransactionsField.setAccessible(true);
                                    @SuppressWarnings("unchecked")
                                    java.util.List<SlotTransaction> slotTransactions = (java.util.List<SlotTransaction>) slotTransactionsField.get(transactionObj);
                                    
                                    if (slotTransactions != null && !slotTransactions.isEmpty()) {
                                        // Prendre le premier SlotTransaction qui a réussi
                                        for (SlotTransaction st : slotTransactions) {
                                            if (st.succeeded()) {
                                                slotTransaction = st;
                                                break;
                                            }
                                        }
                                    }
                                } catch (Exception e2) {
                                    // Ignorer les erreurs
                                }
                            }
                        }
                        
                        if (slotTransaction == null || !slotTransaction.succeeded()) {
                            continue;
                        }
                        
                        ItemStack itemAfter = slotTransaction.getSlotAfter();
                        
                        if (itemAfter == null || itemAfter.isEmpty()) {
                            continue;
                        }
                        
                        // Skip if item already has quality (either in metadata or in ID)
                        if (QualityManager.getQualityFromItem(itemAfter) != null) {
                            continue;
                        }
                        
                        // Skip if item ID already ends with a quality name (e.g., Weapon_Sword_Copper_Common)
                        String itemId = itemAfter.getItemId();
                        //System.out.println("[RomnasQualityCrafting] DEBUG ListTransaction - Item ID: " + itemId);
                        
                        if (QualityManager.hasQualityInId(itemId)) {
                            //System.out.println("[RomnasQualityCrafting] DEBUG - Item ID already has quality suffix, skipping");
                            continue;
                        }
                        
                        // Check if it's a weapon, armor or tool
                        if (!QualityManager.canHaveQuality(itemAfter)) {
                            //System.out.println("[RomnasQualityCrafting] DEBUG - Item cannot have quality: " + itemId);
                            continue;
                        }
                        
                        // Vérifier si des assets JSON existent pour cet item avant de traiter
                        // Si non, ne pas modifier l'item (évite les crashes avec les flèches, etc.)
                        if (!QualityManager.hasJsonAssetsForBaseItem(itemId)) {
                            //System.out.println("[RomnasQualityCrafting] DEBUG - No JSON assets found for: " + itemId);
                            continue;
                        }
                        
                        //System.out.println("[RomnasQualityCrafting] DEBUG - Processing item: " + itemId);
                        // Process this item
                        processItem(itemAfter, container, slotTransaction.getSlot(), entity);
                    }
                }
            } catch (Exception e) {
                // Ignorer les erreurs silencieusement
            }
            return;
        }
    }
    
    private static void processItem(ItemStack itemStack, ItemContainer container, short slotIndex, LivingEntity entity) {
        String itemId = itemStack.getItemId();
        
        // Apply random quality using config weights
        ItemQuality quality = ItemQuality.randomFromConfig();
        ItemStack modifiedItem = QualityManager.applyQuality(itemStack, quality);
        
        // Si l'item n'a pas été modifié (pas d'assets JSON), ne pas remplacer dans le container
        // Cela évite les problèmes avec les items comme les flèches
        if (modifiedItem == itemStack || modifiedItem.getItemId().equals(itemStack.getItemId())) {
            return;
        }
        
        // Debug: Log item transformation for salvage troubleshooting
        //System.out.println("[RomnasQualityCrafting] Item transformed from " + itemId + " to " + modifiedItem.getItemId() + " (quality: " + quality.getDisplayName() + ")");
        //System.out.println("[RomnasQualityCrafting] Modified item ID check: " + modifiedItem.getItemId() + ", Item.getId(): " + (modifiedItem.getItem() != null ? modifiedItem.getItem().getId() : "null"));
        
        // Replace the item in the container with the modified version
        try {
            container.setItemStackForSlot(slotIndex, modifiedItem);
        } catch (Exception e) {
            //System.out.println("[RomnasQualityCrafting] Error setting item in container: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
