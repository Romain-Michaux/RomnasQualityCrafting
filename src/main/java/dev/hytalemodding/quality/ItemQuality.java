package dev.hytalemodding.quality;

import dev.hytalemodding.config.QualityConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Represents the quality tiers an item can have.
 * Each quality modifies weapon damage, tool efficiency, armor resistance, and durability.
 */
public enum ItemQuality {
    POOR("Junk", 0.7f, 0.7f, 0.7f, 0.7f, 1.3f),
    COMMON("Common", 1.0f, 1.0f, 1.0f, 1.0f, 1.0f),
    UNCOMMON("Uncommon", 1.2f, 1.2f, 1.2f, 1.15f, 0.85f),
    RARE("Rare", 1.4f, 1.4f, 1.4f, 1.3f, 0.7f),
    EPIC("Epic", 1.6f, 1.6f, 1.6f, 1.5f, 0.6f),
    LEGENDARY("Legendary", 2.0f, 2.0f, 2.0f, 2.0f, 0.5f);

    private final String displayName;
    private final float defaultDamageMultiplier;
    private final float defaultToolMultiplier;
    private final float defaultArmorMultiplier;
    private final float defaultDurabilityMultiplier;
    private final float defaultSignatureMultiplier;

    ItemQuality(String displayName, float damage, float tool, float armor, float durability, float signature) {
        this.displayName = displayName;
        this.defaultDamageMultiplier = damage;
        this.defaultToolMultiplier = tool;
        this.defaultArmorMultiplier = armor;
        this.defaultDurabilityMultiplier = durability;
        this.defaultSignatureMultiplier = signature;
    }

    /** Display name used in item IDs and UI (e.g. "Legendary", "Junk"). */
    @Nonnull
    public String getDisplayName() {
        return displayName;
    }

    // ── Multiplier accessors (config-aware with defaults) ──

    public float getDamageMultiplier(@Nullable QualityConfig config) {
        if (config == null) return defaultDamageMultiplier;
        switch (this) {
            case POOR:      return (float) config.getDamageMultiplierPoor();
            case COMMON:    return (float) config.getDamageMultiplierCommon();
            case UNCOMMON:  return (float) config.getDamageMultiplierUncommon();
            case RARE:      return (float) config.getDamageMultiplierRare();
            case EPIC:      return (float) config.getDamageMultiplierEpic();
            case LEGENDARY: return (float) config.getDamageMultiplierLegendary();
            default:        return defaultDamageMultiplier;
        }
    }

    public float getToolMultiplier(@Nullable QualityConfig config) {
        if (config == null) return defaultToolMultiplier;
        switch (this) {
            case POOR:      return (float) config.getToolMultiplierPoor();
            case COMMON:    return (float) config.getToolMultiplierCommon();
            case UNCOMMON:  return (float) config.getToolMultiplierUncommon();
            case RARE:      return (float) config.getToolMultiplierRare();
            case EPIC:      return (float) config.getToolMultiplierEpic();
            case LEGENDARY: return (float) config.getToolMultiplierLegendary();
            default:        return defaultToolMultiplier;
        }
    }

    public float getArmorMultiplier(@Nullable QualityConfig config) {
        if (config == null) return defaultArmorMultiplier;
        switch (this) {
            case POOR:      return (float) config.getArmorMultiplierPoor();
            case COMMON:    return (float) config.getArmorMultiplierCommon();
            case UNCOMMON:  return (float) config.getArmorMultiplierUncommon();
            case RARE:      return (float) config.getArmorMultiplierRare();
            case EPIC:      return (float) config.getArmorMultiplierEpic();
            case LEGENDARY: return (float) config.getArmorMultiplierLegendary();
            default:        return defaultArmorMultiplier;
        }
    }

    public float getDurabilityMultiplier(@Nullable QualityConfig config) {
        if (config == null) return defaultDurabilityMultiplier;
        switch (this) {
            case POOR:      return (float) config.getDurabilityMultiplierPoor();
            case COMMON:    return (float) config.getDurabilityMultiplierCommon();
            case UNCOMMON:  return (float) config.getDurabilityMultiplierUncommon();
            case RARE:      return (float) config.getDurabilityMultiplierRare();
            case EPIC:      return (float) config.getDurabilityMultiplierEpic();
            case LEGENDARY: return (float) config.getDurabilityMultiplierLegendary();
            default:        return defaultDurabilityMultiplier;
        }
    }

    public float getSignatureMultiplier(@Nullable QualityConfig config) {
        if (config == null) return defaultSignatureMultiplier;
        switch (this) {
            case POOR:      return (float) config.getSignatureMultiplierPoor();
            case COMMON:    return (float) config.getSignatureMultiplierCommon();
            case UNCOMMON:  return (float) config.getSignatureMultiplierUncommon();
            case RARE:      return (float) config.getSignatureMultiplierRare();
            case EPIC:      return (float) config.getSignatureMultiplierEpic();
            case LEGENDARY: return (float) config.getSignatureMultiplierLegendary();
            default:        return defaultSignatureMultiplier;
        }
    }

    // ── Weighted random quality selection ──

    /**
     * Rolls a random quality using config weights (crafting context).
     * Falls back to hardcoded defaults if config is null.
     */
    @Nonnull
    public static ItemQuality random(@Nullable QualityConfig config) {
        int wPoor, wCommon, wUncommon, wRare, wEpic, wLegendary;

        if (config != null) {
            wPoor      = config.getWeightPoor();
            wCommon    = config.getWeightCommon();
            wUncommon  = config.getWeightUncommon();
            wRare      = config.getWeightRare();
            wEpic      = config.getWeightEpic();
            wLegendary = config.getWeightLegendary();
        } else {
            wPoor = 25; wCommon = 40; wUncommon = 20; wRare = 10; wEpic = 4; wLegendary = 1;
        }

        return rollFromWeights(wPoor, wCommon, wUncommon, wRare, wEpic, wLegendary);
    }

    /**
     * Rolls a random quality using loot-specific weights (drop context).
     * Loot weights are typically more generous than crafting weights.
     * Falls back to hardcoded loot defaults if config is null.
     */
    @Nonnull
    public static ItemQuality randomLoot(@Nullable QualityConfig config) {
        int wPoor, wCommon, wUncommon, wRare, wEpic, wLegendary;

        if (config != null) {
            wPoor      = config.getLootWeightPoor();
            wCommon    = config.getLootWeightCommon();
            wUncommon  = config.getLootWeightUncommon();
            wRare      = config.getLootWeightRare();
            wEpic      = config.getLootWeightEpic();
            wLegendary = config.getLootWeightLegendary();
        } else {
            wPoor = 10; wCommon = 30; wUncommon = 30; wRare = 18; wEpic = 9; wLegendary = 3;
        }

        return rollFromWeights(wPoor, wCommon, wUncommon, wRare, wEpic, wLegendary);
    }

    /**
     * Returns the loot weight for this quality tier from config.
     * Used by LootDropModifier to set ChoiceItemDropContainer weights.
     */
    public double getLootWeight(@Nullable QualityConfig config) {
        if (config == null) {
            switch (this) {
                case POOR:      return 10;
                case COMMON:    return 30;
                case UNCOMMON:  return 30;
                case RARE:      return 18;
                case EPIC:      return 9;
                case LEGENDARY: return 3;
                default:        return 30;
            }
        }
        switch (this) {
            case POOR:      return config.getLootWeightPoor();
            case COMMON:    return config.getLootWeightCommon();
            case UNCOMMON:  return config.getLootWeightUncommon();
            case RARE:      return config.getLootWeightRare();
            case EPIC:      return config.getLootWeightEpic();
            case LEGENDARY: return config.getLootWeightLegendary();
            default:        return config.getLootWeightCommon();
        }
    }

    /**
     * Internal helper: rolls from explicit weights.
     */
    @Nonnull
    private static ItemQuality rollFromWeights(int wPoor, int wCommon, int wUncommon,
                                                int wRare, int wEpic, int wLegendary) {
        int total = wPoor + wCommon + wUncommon + wRare + wEpic + wLegendary;
        if (total <= 0) return COMMON;

        double rand = Math.random() * total;
        int cumulative = 0;

        cumulative += wPoor;
        if (rand < cumulative) return POOR;

        cumulative += wCommon;
        if (rand < cumulative) return COMMON;

        cumulative += wUncommon;
        if (rand < cumulative) return UNCOMMON;

        cumulative += wRare;
        if (rand < cumulative) return RARE;

        cumulative += wEpic;
        if (rand < cumulative) return EPIC;

        return LEGENDARY;
    }

    // ── Utility methods ──

    /**
     * Checks if a given item ID already has a quality suffix.
     * Example: "Weapon_Sword_Copper_Legendary" → true
     */
    public static boolean hasQualitySuffix(@Nonnull String itemId) {
        for (ItemQuality q : values()) {
            if (itemId.endsWith("_" + q.displayName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts the base item ID from a quality item ID.
     * Example: "Weapon_Sword_Copper_Legendary" → "Weapon_Sword_Copper"
     * Returns the original ID if no quality suffix is found.
     */
    @Nonnull
    public static String extractBaseId(@Nonnull String itemId) {
        for (ItemQuality q : values()) {
            String suffix = "_" + q.displayName;
            if (itemId.endsWith(suffix)) {
                return itemId.substring(0, itemId.length() - suffix.length());
            }
        }
        return itemId;
    }

    /**
     * Attempts to parse a quality from a quality item ID suffix.
     * Returns null if no quality suffix is found.
     */
    @Nullable
    public static ItemQuality fromItemId(@Nonnull String itemId) {
        for (ItemQuality q : values()) {
            if (itemId.endsWith("_" + q.displayName)) {
                return q;
            }
        }
        return null;
    }

    /**
     * Builds the quality variant ID for a given base item and quality.
     * Example: ("Weapon_Sword_Copper", LEGENDARY) → "Weapon_Sword_Copper_Legendary"
     */
    @Nonnull
    public static String qualityItemId(@Nonnull String baseId, @Nonnull ItemQuality quality) {
        return baseId + "_" + quality.displayName;
    }
}
