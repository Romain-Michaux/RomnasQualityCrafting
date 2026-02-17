package dev.hytalemodding.quality;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import dev.hytalemodding.config.QualityConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for item quality eligibility checks.
 *
 * Provides eligibility checks used by QualityRegistry and QualityAssigner.
 */
public final class QualityItemFactory {

    private QualityItemFactory() {} // Utility class, no instantiation

    /** Hardcoded default ignored prefixes (consumables, projectiles, etc.). */
    private static final String[] DEFAULT_IGNORED_PREFIXES = {
            "Weapon_Bomb",
            "Weapon_Arrow",
            "Weapon_Dart",
            "Weapon_Spellbook",
            "Tool_Feedbag"
    };

    /** Cached merged ignore set (built once from config + defaults). */
    private static Set<String> cachedIgnorePrefixes = null;

    /**
     * Initializes the ignore list from config. Call once at startup after
     * config is loaded, before scanning items.
     */
    public static void initIgnoreList(@Nullable QualityConfig config) {
        Set<String> prefixes = new HashSet<>(Arrays.asList(DEFAULT_IGNORED_PREFIXES));
        if (config != null) {
            String[] configPrefixes = config.getIgnoredItemPrefixes();
            if (configPrefixes != null) {
                // Config replaces defaults entirely (allows full user control)
                prefixes.clear();
                Collections.addAll(prefixes, configPrefixes);
            }
        }
        cachedIgnorePrefixes = prefixes;
    }

    /**
     * Checks if an item ID is on the ignore list (matches any prefix).
     */
    public static boolean isIgnored(@Nonnull String itemId) {
        Set<String> prefixes = cachedIgnorePrefixes;
        if (prefixes == null) {
            // Fallback if not initialized — use hardcoded defaults
            prefixes = new HashSet<>(Arrays.asList(DEFAULT_IGNORED_PREFIXES));
        }
        for (String prefix : prefixes) {
            if (itemId.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Checks if an item is eligible for quality variants.
     * An item qualifies if it's a weapon, armor, or tool AND not on the ignore list.
     */
    public static boolean isEligibleForQuality(@Nonnull Item item) {
        if (item.getWeapon() != null) return true;
        if (item.getArmor() != null) return true;
        if (item.getTool() != null) return true;
        if (item.getBlockSelectorToolData() != null) return true;

        return false;
    }

    /**
     * Full eligibility check combining type check and ignore list.
     * Use this in QualityRegistry when scanning items.
     */
    public static boolean isEligibleForQuality(@Nonnull String itemId, @Nonnull Item item) {
        if (isIgnored(itemId)) return false;
        return isEligibleForQuality(item);
    }
}
