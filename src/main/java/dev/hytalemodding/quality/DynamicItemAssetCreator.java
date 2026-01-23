package dev.hytalemodding.quality;

import javax.annotation.Nonnull;

/**
 * Utility class for generating quality item IDs
 */
public class DynamicItemAssetCreator {
    
    /**
     * Generates the item ID for a given quality
     * Uses displayName to match JSON files (e.g., "Common" instead of "COMMON")
     * Example: "CrudeSword" + "Legendary" -> "CrudeSword_Legendary"
     */
    @Nonnull
    public static String getQualityItemId(@Nonnull String baseItemId, @Nonnull ItemQuality quality) {
        return baseItemId + "_" + quality.getDisplayName();
    }
}
