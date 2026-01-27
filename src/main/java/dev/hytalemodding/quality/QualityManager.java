package dev.hytalemodding.quality;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

public class QualityManager {
    
    private static final String METADATA_QUALITY = "quality"; // Kept for getQualityFromItem() compatibility
    
    public static boolean isWeapon(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (item == null) {
            return false;
        }
        return item.getWeapon() != null;
    }
    
    public static boolean isArmor(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (item == null) {
            return false;
        }
        return item.getArmor() != null;
    }
    
    public static boolean isTool(ItemStack itemStack) {
        Item item = itemStack.getItem();
        if (item == null) {
            return false;
        }
        
        // Check if item has Tool property (for pickaxes, hatchets, etc.)
        if (item.getTool() != null) {
            return true;
        }
        
        // Check if item has BlockSelectorTool property (for hammers, shovels, etc.)
        // Use reflection to check for BlockSelectorTool
        try {
            java.lang.reflect.Method getBlockSelectorToolMethod = Item.class.getMethod("getBlockSelectorTool");
            Object blockSelectorTool = getBlockSelectorToolMethod.invoke(item);
            if (blockSelectorTool != null) {
                return true;
            }
        } catch (Exception e) {
            // Method doesn't exist or failed, continue to next check
        }
        
        // Check if item ID starts with "Tool_" (fallback for items that might not have Tool property)
        String itemId = item.getId();
        if (itemId != null && itemId.startsWith("Tool_")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if the item is a weapon, armor, or tool that can have a quality
     */
    public static boolean canHaveQuality(ItemStack itemStack) {
        return isWeapon(itemStack) || isArmor(itemStack) || isTool(itemStack);
    }

    /**
     * Returns true if the item is considered Weapon or Armor for quality compat:
     * - Tags.Type contains "Weapon" or "Armor", or
     * - Categories contains a string with "Weapon" or "Armor" (e.g. "Items.Weapons").
     * Used by quality compat to avoid duplicating consumables, runes, etc.
     */
    public static boolean hasWeaponOrArmorTag(Item item) {
        if (item == null) {
            return false;
        }
        if (hasWeaponOrArmorInTags(item)) {
            return true;
        }
        return hasWeaponOrArmorInCategories(item);
    }

    private static boolean hasWeaponOrArmorInTags(Item item) {
        try {
            Method getTags = Item.class.getMethod("getTags");
            Object tags = getTags.invoke(item);
            if (tags == null) {
                return false;
            }
            Object typeVal = null;
            if (tags instanceof Map) {
                typeVal = ((Map<?, ?>) tags).get("Type");
            } else {
                try {
                    Method getType = tags.getClass().getMethod("getType");
                    typeVal = getType.invoke(tags);
                } catch (NoSuchMethodException ignored) {
                }
                if (typeVal == null) {
                    try {
                        Method m = tags.getClass().getMethod("get", Object.class);
                        typeVal = m.invoke(tags, "Type");
                    } catch (Exception ignored) {
                    }
                }
            }
            if (typeVal == null) {
                return false;
            }
            Collection<?> typeList = null;
            if (typeVal instanceof Collection) {
                typeList = (Collection<?>) typeVal;
            } else if (typeVal instanceof Iterable && !(typeVal instanceof String)) {
                java.util.List<Object> tmp = new java.util.ArrayList<>();
                for (Object o : (Iterable<?>) typeVal) {
                    tmp.add(o);
                }
                typeList = tmp;
            } else if (typeVal.getClass().isArray()) {
                java.util.List<Object> tmp = new java.util.ArrayList<>();
                int len = java.lang.reflect.Array.getLength(typeVal);
                for (int i = 0; i < len; i++) {
                    tmp.add(java.lang.reflect.Array.get(typeVal, i));
                }
                typeList = tmp;
            } else if (typeVal instanceof String) {
                String s = (String) typeVal;
                return "Weapon".equalsIgnoreCase(s) || "Armor".equalsIgnoreCase(s);
            }
            if (typeList != null) {
                for (Object o : typeList) {
                    String s = o == null ? null : String.valueOf(o).trim();
                    if ("Weapon".equalsIgnoreCase(s) || "Armor".equalsIgnoreCase(s)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // getTags or Tags.Type not available
        }
        return false;
    }

    private static boolean hasWeaponOrArmorInCategories(Item item) {
        try {
            Method getCategories = Item.class.getMethod("getCategories");
            Object categories = getCategories.invoke(item);
            if (categories == null) {
                return false;
            }
            java.util.List<String> toCheck = new java.util.ArrayList<>();
            if (categories instanceof Collection) {
                for (Object o : (Collection<?>) categories) {
                    toCheck.add(o == null ? "" : String.valueOf(o));
                }
            } else if (categories instanceof Iterable && !(categories instanceof String)) {
                for (Object o : (Iterable<?>) categories) {
                    toCheck.add(o == null ? "" : String.valueOf(o));
                }
            } else if (categories.getClass().isArray()) {
                int len = java.lang.reflect.Array.getLength(categories);
                for (int i = 0; i < len; i++) {
                    toCheck.add(String.valueOf(java.lang.reflect.Array.get(categories, i)));
                }
            }
            for (String s : toCheck) {
                if (s == null) continue;
                String lower = s.toLowerCase();
                if (lower.contains("weapon") || lower.contains("armor")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // getCategories not available
        }
        return false;
    }

    /**
     * Checks if an Item (asset) is a weapon, armor, or tool that can have a quality.
     * Used by QualityCompatLoader to detect which items need auto-generated quality variants.
     */
    public static boolean canHaveQuality(Item item) {
        if (item == null) {
            return false;
        }
        if (item.getWeapon() != null || item.getArmor() != null) {
            return true;
        }
        if (item.getTool() != null) {
            return true;
        }
        try {
            java.lang.reflect.Method m = Item.class.getMethod("getBlockSelectorTool");
            if (m.invoke(item) != null) {
                return true;
            }
        } catch (Exception ignored) {
        }
        String id = item.getId();
        return id != null && id.startsWith("Tool_");
    }
    
    /**
     * Checks if an item ID already ends with a quality name
     * Examples: "Weapon_Sword_Copper_Common" -> true, "Weapon_Sword_Copper" -> false
     */
    public static boolean hasQualityInId(String itemId) {
        for (ItemQuality quality : ItemQuality.values()) {
            String qualitySuffix = "_" + quality.getDisplayName();
            if (itemId.endsWith(qualitySuffix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Checks if JSON assets exist for a base item
     * Returns true if at least one quality exists in JSON assets
     */
    public static boolean hasJsonAssetsForBaseItem(String baseItemId) {
        String[] qualities = {"Junk", "Common", "Uncommon", "Rare", "Epic", "Legendary"};
        for (String quality : qualities) {
            String qualityId = baseItemId + "_" + quality;
            Item item = Item.getAssetMap().getAsset(qualityId);
            if (item != null && item != Item.UNKNOWN) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Applies a quality to an item by replacing it with the quality version from JSON
     * Stats are already defined in JSON files, so we just swap the item
     */
    public static ItemStack applyQuality(ItemStack itemStack, ItemQuality quality) {
        Item item = itemStack.getItem();
        if (item == null) {
            return itemStack;
        }
        
        // Try both methods to get the item ID
        String baseItemId = item.getId();
        String itemStackId = itemStack.getItemId();
        // Use item.getId() as it's more reliable
        if (baseItemId == null || baseItemId.isEmpty()) {
            baseItemId = itemStackId;
        }
        
        // Check if item already has a quality in its ID
        if (hasQualityInId(baseItemId)) {
            return itemStack;
        }
        
        // Check if JSON assets exist for this base item
        if (!hasJsonAssetsForBaseItem(baseItemId)) {
            return itemStack;
        }
        
        // Get the quality item ID
        String qualityItemId = DynamicItemAssetCreator.getQualityItemId(baseItemId, quality);
        
        // Check if the quality asset exists (loaded from JSON files)
        Item qualityItem = Item.getAssetMap().getAsset(qualityItemId);
        if (qualityItem == null || qualityItem == Item.UNKNOWN) {
            return itemStack;
        }
        
        // Verify that the quality item has the same type as the base item (weapon/armor/tool)
        // This ensures the JSON is complete and valid
        boolean baseIsWeapon = item.getWeapon() != null;
        boolean baseIsArmor = item.getArmor() != null;
        boolean baseIsTool = item.getTool() != null;
        
        // Check for BlockSelectorTool for items like hammers and shovels
        boolean baseHasBlockSelectorTool = false;
        try {
            java.lang.reflect.Method getBlockSelectorToolMethod = Item.class.getMethod("getBlockSelectorTool");
            Object blockSelectorTool = getBlockSelectorToolMethod.invoke(item);
            baseHasBlockSelectorTool = (blockSelectorTool != null);
        } catch (Exception e) {
            // Method doesn't exist or failed
        }
        
        boolean qualityIsWeapon = qualityItem.getWeapon() != null;
        boolean qualityIsArmor = qualityItem.getArmor() != null;
        boolean qualityIsTool = qualityItem.getTool() != null;
        
        // Check for BlockSelectorTool in quality item
        boolean qualityHasBlockSelectorTool = false;
        try {
            java.lang.reflect.Method getBlockSelectorToolMethod = Item.class.getMethod("getBlockSelectorTool");
            Object blockSelectorTool = getBlockSelectorToolMethod.invoke(qualityItem);
            qualityHasBlockSelectorTool = (blockSelectorTool != null);
        } catch (Exception e) {
            // Method doesn't exist or failed
        }
        
        // Check if the quality item has the same type as the base item
        // If types don't match, the JSON is likely incomplete (e.g., missing interactions)
        // For tools with BlockSelectorTool, we need to check both Tool and BlockSelectorTool
        boolean typeMatches = false;
        if (baseIsWeapon && qualityIsWeapon) {
            typeMatches = true;
        } else if (baseIsArmor && qualityIsArmor) {
            typeMatches = true;
        } else if (baseIsTool && qualityIsTool) {
            typeMatches = true;
        } else if (baseHasBlockSelectorTool && qualityHasBlockSelectorTool) {
            // Both have BlockSelectorTool (hammers, shovels)
            typeMatches = true;
        }
        
        if (!typeMatches) {
            //System.out.println("[RomnasQualityCrafting] Quality asset incomplete or invalid: " + qualityItemId);
            //System.out.println("[RomnasQualityCrafting] Base item type mismatch - returning original item");
            return itemStack;
        }
        
        // Additional check: verify the item has an ID set (ensures it's fully loaded)
        if (qualityItem.getId() == null || qualityItem.getId().isEmpty() || 
            !qualityItem.getId().equals(qualityItemId)) {
            //System.out.println("[RomnasQualityCrafting] Quality asset ID mismatch or incomplete: " + qualityItemId);
            //System.out.println("[RomnasQualityCrafting] Returning original item without quality modification");
            return itemStack;
        }
        
        // Create a new ItemStack with the quality item ID
        // Stats (durability and damage) are already defined in JSON files
        // Preserve durability ratio if item already had durability
        ItemStack modified = new ItemStack(qualityItemId, itemStack.getQuantity());
        
        // Debug: Verify the ItemStack was created with the correct ID
        //System.out.println("[RomnasQualityCrafting] Created ItemStack with ID: " + modified.getItemId() + " (expected: " + qualityItemId + ")");
        if (modified.getItem() != null) {
            //System.out.println("[RomnasQualityCrafting] Item.getId(): " + modified.getItem().getId());
        }
        
        // Preserve durability ratio if item already had durability
        double currentDurability = itemStack.getDurability();
        double currentMaxDurability = itemStack.getMaxDurability();
        double newMaxDurability = qualityItem.getMaxDurability();
        
        if (currentMaxDurability > 0 && currentDurability > 0) {
            // Preserve durability ratio
            double durabilityRatio = currentDurability / currentMaxDurability;
            double newDurability = newMaxDurability * durabilityRatio;
            modified = modified
                .withMaxDurability(newMaxDurability)
                .withDurability(newDurability);
        } else {
            // If no current durability, use max durability from new asset
            modified = modified
                .withMaxDurability(newMaxDurability)
                .withDurability(newMaxDurability);
        }
        
        // Don't add metadata - it causes issues with recipe matching (isEquivalentType compares metadata)
        // The quality is already encoded in the item ID (e.g., Weapon_Sword_Thorium_Uncommon)
        
        return modified;
    }
    
    /**
     * Gets the quality from item metadata
     */
    @SuppressWarnings("deprecation")
    @Nullable
    public static ItemQuality getQualityFromItem(ItemStack itemStack) {
        BsonDocument metadata = itemStack.getMetadata();
        if (metadata == null) {
            return null;
        }
        
        org.bson.BsonValue qualityValue = metadata.get(METADATA_QUALITY);
        if (qualityValue == null || !qualityValue.isString()) {
            return null;
        }
        
        try {
            return ItemQuality.valueOf(qualityValue.asString().getValue());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * Reforges an item by changing its quality to a new random quality
     * @param itemStack The item to reforge (must already have a quality)
     * @param newQuality The new quality to apply
     * @return The reforged item, or null if reforge failed
     */
    @Nullable
    public static ItemStack reforgeItem(ItemStack itemStack, ItemQuality newQuality) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        
        Item item = itemStack.getItem();
        if (item == null) {
            return null;
        }
        
        String currentItemId = item.getId();
        if (currentItemId == null || currentItemId.isEmpty()) {
            currentItemId = itemStack.getItemId();
        }
        
        // Vérifier que l'item a déjà une qualité
        if (!hasQualityInId(currentItemId)) {
            //System.out.println("[RomnasQualityCrafting] Cannot reforge item without quality: " + currentItemId);
            return null;
        }
        
        // Obtenir l'ID de base (sans la qualité)
        String baseItemId = getBaseItemId(currentItemId);
        
        // Vérifier si des assets JSON existent pour cet item
        if (!hasJsonAssetsForBaseItem(baseItemId)) {
            //System.out.println("[RomnasQualityCrafting] No JSON assets found for base item: " + baseItemId);
            return null;
        }
        
        // Obtenir le nouvel ID avec la nouvelle qualité
        String newItemId = DynamicItemAssetCreator.getQualityItemId(baseItemId, newQuality);
        
        // Vérifier que le nouvel item existe
        Item newItem = Item.getAssetMap().getAsset(newItemId);
        if (newItem == null || newItem == Item.UNKNOWN) {
            //System.out.println("[RomnasQualityCrafting] New quality item not found: " + newItemId);
            return null;
        }
        
        // Créer le nouvel ItemStack avec la nouvelle qualité
        ItemStack reforgedItem = new ItemStack(newItemId, itemStack.getQuantity());
        
        // Préserver la durabilité (ratio)
        double currentDurability = itemStack.getDurability();
        double currentMaxDurability = itemStack.getMaxDurability();
        double newMaxDurability = newItem.getMaxDurability();
        
        if (currentMaxDurability > 0 && currentDurability > 0 && newMaxDurability > 0) {
            double durabilityRatio = currentDurability / currentMaxDurability;
            double newDurability = newMaxDurability * durabilityRatio;
            reforgedItem = reforgedItem
                .withMaxDurability(newMaxDurability)
                .withDurability(newDurability);
        } else {
            reforgedItem = reforgedItem
                .withMaxDurability(newMaxDurability)
                .withDurability(newMaxDurability);
        }
        
        return reforgedItem;
    }
    
    /**
     * Extracts the base item ID from a quality item ID
     * Example: "Weapon_Sword_Copper_Common" -> "Weapon_Sword_Copper"
     */
    @Nonnull
    public static String getBaseItemId(@Nonnull String itemId) {
        for (ItemQuality quality : ItemQuality.values()) {
            String suffix = "_" + quality.getDisplayName();
            if (itemId.endsWith(suffix)) {
                return itemId.substring(0, itemId.length() - suffix.length());
            }
        }
        return itemId;
    }
}
