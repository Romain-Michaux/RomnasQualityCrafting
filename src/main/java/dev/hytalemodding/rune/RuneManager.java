package dev.hytalemodding.rune;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.hytalemodding.quality.QualityManager;
import org.bson.BsonDocument;
import org.bson.BsonString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Manages rune application and storage on items.
 * Runes are stored in item metadata so the same item type can have different runes.
 */
public final class RuneManager {

    public static final String METADATA_RUNE = "AppliedRune";

    /** Rune IDs used for weapons/bows/daggers: set enemies on fire */
    public static final String RUNE_BURN = "Burn";
    /** Rune IDs for weapons/bows/daggers: poison enemies */
    public static final String RUNE_POISON = "Poison";
    /** Rune ID for pickaxes: double ore yields from ore blocks */
    public static final String RUNE_LUCK = "Luck";
    /** Rune ID for boots (legs armor): doubles walk speed */
    public static final String RUNE_SPEED = "Speed";

    private RuneManager() {}

    @Nullable
    public static String getAppliedRune(@Nonnull ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return null;
        }
        BsonDocument meta = itemStack.getMetadata();
        if (meta == null) return null;
        var v = meta.get(METADATA_RUNE);
        return (v != null && v.isString()) ? v.asString().getValue() : null;
    }

    /**
     * Returns an ItemStack with the given rune set in metadata, or the same stack if unchanged.
     */
    @Nonnull
    public static ItemStack withAppliedRune(@Nonnull ItemStack itemStack, @Nonnull String runeId) {
        BsonDocument meta = itemStack.getMetadata() != null
            ? itemStack.getMetadata().clone()
            : new BsonDocument();
        meta.put(METADATA_RUNE, new BsonString(runeId));
        return itemStack.withMetadata(meta);
    }

    /**
     * Returns an ItemStack with rune metadata removed.
     */
    @Nonnull
    public static ItemStack clearRune(@Nonnull ItemStack itemStack) {
        BsonDocument meta = itemStack.getMetadata();
        if (meta == null || !meta.containsKey(METADATA_RUNE)) {
            return itemStack;
        }
        BsonDocument next = meta.clone();
        next.remove(METADATA_RUNE);
        return itemStack.withMetadata(next);
    }

    public static boolean hasRune(@Nonnull ItemStack itemStack) {
        return getAppliedRune(itemStack) != null;
    }

    /**
     * Whether the rune can be applied to the given target item.
     * Burn and Poison apply to weapons (swords, daggers, bows, etc.), but not arrows.
     * Luck applies to pickaxes only. Speed applies to boots (legs armor) only.
     */
    public static boolean canApplyRuneTo(@Nonnull String runeId, @Nonnull ItemStack target) {
        if (target == null || target.isEmpty()) return false;
        if (isArrow(target)) return false;
        switch (runeId) {
            case RUNE_BURN:
            case RUNE_POISON:
                return isWeaponOrRanged(target);
            case RUNE_LUCK:
                return isPickaxe(target);
            case RUNE_SPEED:
                return isBoots(target);
            default:
                return false;
        }
    }

    /** Boots are legs armor in this mod (e.g. Armor_Copper_Legs_Common). */
    public static boolean isBoots(@Nonnull ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        String id = itemStack.getItemId();
        return id != null && id.contains("_Legs_");
    }

    /** Arrows (e.g. Weapon_Arrow_Crude) cannot have runes applied. */
    public static boolean isArrow(@Nonnull ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) return false;
        String id = itemStack.getItemId();
        return id != null && (id.contains("_Arrow_") || id.startsWith("Weapon_Arrow"));
    }

    public static boolean isWeaponOrRanged(@Nonnull ItemStack itemStack) {
        if (isArrow(itemStack)) return false;
        Item item = itemStack.getItem();
        if (item == null) return false;
        if (item.getWeapon() != null) return true;
        String id = item.getId();
        if (id == null) id = itemStack.getItemId();
        return id != null && (id.startsWith("Weapon_") || id.contains("_Bow_") || id.contains("_Dagger_"));
    }

    public static boolean isPickaxe(@Nonnull ItemStack itemStack) {
        return QualityManager.isTool(itemStack) && itemStack.getItemId().contains("Pickaxe");
    }

    /** Resolve rune id from rune item id, e.g. Rune_Burn -> Burn */
    @Nullable
    public static String runeIdFromItemId(@Nonnull String itemId) {
        if (itemId.startsWith("Rune_")) {
            return itemId.substring("Rune_".length());
        }
        return null;
    }
}
