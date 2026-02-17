package dev.hytalemodding.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Simplified configuration for RomnasQualityCrafting v2.0.
 * 
 * Only contains quality weights and stat multipliers.
 * No more: CustomAssetsPath, ExternalModsCompatEnabled, ForceResetAssets, etc.
 */
public class QualityConfig {

    public static final BuilderCodec<QualityConfig> CODEC = BuilderCodec.builder(
            QualityConfig.class,
            QualityConfig::new)
        // ── Quality weights (probability of each tier) ──
        .append(new KeyedCodec<Integer>("WeightPoor", Codec.INTEGER),
                (c, v) -> c.weightPoor = v, c -> c.weightPoor).add()
        .append(new KeyedCodec<Integer>("WeightCommon", Codec.INTEGER),
                (c, v) -> c.weightCommon = v, c -> c.weightCommon).add()
        .append(new KeyedCodec<Integer>("WeightUncommon", Codec.INTEGER),
                (c, v) -> c.weightUncommon = v, c -> c.weightUncommon).add()
        .append(new KeyedCodec<Integer>("WeightRare", Codec.INTEGER),
                (c, v) -> c.weightRare = v, c -> c.weightRare).add()
        .append(new KeyedCodec<Integer>("WeightEpic", Codec.INTEGER),
                (c, v) -> c.weightEpic = v, c -> c.weightEpic).add()
        .append(new KeyedCodec<Integer>("WeightLegendary", Codec.INTEGER),
                (c, v) -> c.weightLegendary = v, c -> c.weightLegendary).add()
        // ── Weapon damage multipliers ──
        .append(new KeyedCodec<Double>("DamageMultiplierPoor", Codec.DOUBLE),
                (c, v) -> c.damageMultiplierPoor = v, c -> c.damageMultiplierPoor).add()
        .append(new KeyedCodec<Double>("DamageMultiplierCommon", Codec.DOUBLE),
                (c, v) -> c.damageMultiplierCommon = v, c -> c.damageMultiplierCommon).add()
        .append(new KeyedCodec<Double>("DamageMultiplierUncommon", Codec.DOUBLE),
                (c, v) -> c.damageMultiplierUncommon = v, c -> c.damageMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("DamageMultiplierRare", Codec.DOUBLE),
                (c, v) -> c.damageMultiplierRare = v, c -> c.damageMultiplierRare).add()
        .append(new KeyedCodec<Double>("DamageMultiplierEpic", Codec.DOUBLE),
                (c, v) -> c.damageMultiplierEpic = v, c -> c.damageMultiplierEpic).add()
        .append(new KeyedCodec<Double>("DamageMultiplierLegendary", Codec.DOUBLE),
                (c, v) -> c.damageMultiplierLegendary = v, c -> c.damageMultiplierLegendary).add()
        // ── Tool efficiency multipliers ──
        .append(new KeyedCodec<Double>("ToolMultiplierPoor", Codec.DOUBLE),
                (c, v) -> c.toolMultiplierPoor = v, c -> c.toolMultiplierPoor).add()
        .append(new KeyedCodec<Double>("ToolMultiplierCommon", Codec.DOUBLE),
                (c, v) -> c.toolMultiplierCommon = v, c -> c.toolMultiplierCommon).add()
        .append(new KeyedCodec<Double>("ToolMultiplierUncommon", Codec.DOUBLE),
                (c, v) -> c.toolMultiplierUncommon = v, c -> c.toolMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("ToolMultiplierRare", Codec.DOUBLE),
                (c, v) -> c.toolMultiplierRare = v, c -> c.toolMultiplierRare).add()
        .append(new KeyedCodec<Double>("ToolMultiplierEpic", Codec.DOUBLE),
                (c, v) -> c.toolMultiplierEpic = v, c -> c.toolMultiplierEpic).add()
        .append(new KeyedCodec<Double>("ToolMultiplierLegendary", Codec.DOUBLE),
                (c, v) -> c.toolMultiplierLegendary = v, c -> c.toolMultiplierLegendary).add()
        // ── Armor resistance multipliers ──
        .append(new KeyedCodec<Double>("ArmorMultiplierPoor", Codec.DOUBLE),
                (c, v) -> c.armorMultiplierPoor = v, c -> c.armorMultiplierPoor).add()
        .append(new KeyedCodec<Double>("ArmorMultiplierCommon", Codec.DOUBLE),
                (c, v) -> c.armorMultiplierCommon = v, c -> c.armorMultiplierCommon).add()
        .append(new KeyedCodec<Double>("ArmorMultiplierUncommon", Codec.DOUBLE),
                (c, v) -> c.armorMultiplierUncommon = v, c -> c.armorMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("ArmorMultiplierRare", Codec.DOUBLE),
                (c, v) -> c.armorMultiplierRare = v, c -> c.armorMultiplierRare).add()
        .append(new KeyedCodec<Double>("ArmorMultiplierEpic", Codec.DOUBLE),
                (c, v) -> c.armorMultiplierEpic = v, c -> c.armorMultiplierEpic).add()
        .append(new KeyedCodec<Double>("ArmorMultiplierLegendary", Codec.DOUBLE),
                (c, v) -> c.armorMultiplierLegendary = v, c -> c.armorMultiplierLegendary).add()
        // ── Signature Energy multipliers (lower = better, inverted from damage) ──
        .append(new KeyedCodec<Double>("SignatureMultiplierPoor", Codec.DOUBLE),
                (c, v) -> c.signatureMultiplierPoor = v, c -> c.signatureMultiplierPoor).add()
        .append(new KeyedCodec<Double>("SignatureMultiplierCommon", Codec.DOUBLE),
                (c, v) -> c.signatureMultiplierCommon = v, c -> c.signatureMultiplierCommon).add()
        .append(new KeyedCodec<Double>("SignatureMultiplierUncommon", Codec.DOUBLE),
                (c, v) -> c.signatureMultiplierUncommon = v, c -> c.signatureMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("SignatureMultiplierRare", Codec.DOUBLE),
                (c, v) -> c.signatureMultiplierRare = v, c -> c.signatureMultiplierRare).add()
        .append(new KeyedCodec<Double>("SignatureMultiplierEpic", Codec.DOUBLE),
                (c, v) -> c.signatureMultiplierEpic = v, c -> c.signatureMultiplierEpic).add()
        .append(new KeyedCodec<Double>("SignatureMultiplierLegendary", Codec.DOUBLE),
                (c, v) -> c.signatureMultiplierLegendary = v, c -> c.signatureMultiplierLegendary).add()
        // ── Durability multipliers (all item types) ──
        .append(new KeyedCodec<Double>("DurabilityMultiplierPoor", Codec.DOUBLE),
                (c, v) -> c.durabilityMultiplierPoor = v, c -> c.durabilityMultiplierPoor).add()
        .append(new KeyedCodec<Double>("DurabilityMultiplierCommon", Codec.DOUBLE),
                (c, v) -> c.durabilityMultiplierCommon = v, c -> c.durabilityMultiplierCommon).add()
        .append(new KeyedCodec<Double>("DurabilityMultiplierUncommon", Codec.DOUBLE),
                (c, v) -> c.durabilityMultiplierUncommon = v, c -> c.durabilityMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("DurabilityMultiplierRare", Codec.DOUBLE),
                (c, v) -> c.durabilityMultiplierRare = v, c -> c.durabilityMultiplierRare).add()
        .append(new KeyedCodec<Double>("DurabilityMultiplierEpic", Codec.DOUBLE),
                (c, v) -> c.durabilityMultiplierEpic = v, c -> c.durabilityMultiplierEpic).add()
        .append(new KeyedCodec<Double>("DurabilityMultiplierLegendary", Codec.DOUBLE),
                (c, v) -> c.durabilityMultiplierLegendary = v, c -> c.durabilityMultiplierLegendary).add()
        // ── Loot drop quality weights (separate from crafting weights) ──
        .append(new KeyedCodec<Integer>("LootWeightPoor", Codec.INTEGER),
                (c, v) -> c.lootWeightPoor = v, c -> c.lootWeightPoor).add()
        .append(new KeyedCodec<Integer>("LootWeightCommon", Codec.INTEGER),
                (c, v) -> c.lootWeightCommon = v, c -> c.lootWeightCommon).add()
        .append(new KeyedCodec<Integer>("LootWeightUncommon", Codec.INTEGER),
                (c, v) -> c.lootWeightUncommon = v, c -> c.lootWeightUncommon).add()
        .append(new KeyedCodec<Integer>("LootWeightRare", Codec.INTEGER),
                (c, v) -> c.lootWeightRare = v, c -> c.lootWeightRare).add()
        .append(new KeyedCodec<Integer>("LootWeightEpic", Codec.INTEGER),
                (c, v) -> c.lootWeightEpic = v, c -> c.lootWeightEpic).add()
        .append(new KeyedCodec<Integer>("LootWeightLegendary", Codec.INTEGER),
                (c, v) -> c.lootWeightLegendary = v, c -> c.lootWeightLegendary).add()
        // ── Loot quality system toggle ──
        .append(new KeyedCodec<Boolean>("LootQualityEnabled", Codec.BOOLEAN),
                (c, v) -> c.lootQualityEnabled = v, c -> c.lootQualityEnabled).add()
        // ── Ignored item ID prefixes (no quality variants created for these) ──
        .append(new KeyedCodec<String[]>("IgnoredItemPrefixes", Codec.STRING_ARRAY),
                (c, v) -> c.ignoredItemPrefixes = v, c -> c.ignoredItemPrefixes).add()
        .build();

    // ── Quality weights ──
    private int weightPoor = 25;
    private int weightCommon = 40;
    private int weightUncommon = 20;
    private int weightRare = 10;
    private int weightEpic = 4;
    private int weightLegendary = 1;

    // ── Weapon damage multipliers ──
    private double damageMultiplierPoor = 0.7;
    private double damageMultiplierCommon = 1.0;
    private double damageMultiplierUncommon = 1.2;
    private double damageMultiplierRare = 1.4;
    private double damageMultiplierEpic = 1.6;
    private double damageMultiplierLegendary = 2.0;

    // ── Tool efficiency multipliers ──
    private double toolMultiplierPoor = 0.7;
    private double toolMultiplierCommon = 1.0;
    private double toolMultiplierUncommon = 1.2;
    private double toolMultiplierRare = 1.4;
    private double toolMultiplierEpic = 1.6;
    private double toolMultiplierLegendary = 2.0;

    // ── Armor resistance multipliers ──
    private double armorMultiplierPoor = 0.7;
    private double armorMultiplierCommon = 1.0;
    private double armorMultiplierUncommon = 1.2;
    private double armorMultiplierRare = 1.4;
    private double armorMultiplierEpic = 1.6;
    private double armorMultiplierLegendary = 2.0;

    // ── Signature Energy multipliers (lower = better) ──
    private double signatureMultiplierPoor = 1.3;
    private double signatureMultiplierCommon = 1.0;
    private double signatureMultiplierUncommon = 0.85;
    private double signatureMultiplierRare = 0.7;
    private double signatureMultiplierEpic = 0.6;
    private double signatureMultiplierLegendary = 0.5;

    // ── Durability multipliers ──
    private double durabilityMultiplierPoor = 0.7;
    private double durabilityMultiplierCommon = 1.0;
    private double durabilityMultiplierUncommon = 1.15;
    private double durabilityMultiplierRare = 1.3;
    private double durabilityMultiplierEpic = 1.5;
    private double durabilityMultiplierLegendary = 2.0;

    // ── Loot drop quality weights (more rewarding than crafting defaults) ──
    private int lootWeightPoor = 10;
    private int lootWeightCommon = 30;
    private int lootWeightUncommon = 30;
    private int lootWeightRare = 18;
    private int lootWeightEpic = 9;
    private int lootWeightLegendary = 3;

    // ── Loot quality system toggle ──
    private boolean lootQualityEnabled = true;

    // ── Ignored item ID prefixes (consumables, projectiles, etc.) ──
    private String[] ignoredItemPrefixes = new String[] {
            "Weapon_Bomb",
            "Weapon_Arrow",
            "Weapon_Dart",
            "Weapon_Spellbook",
            "Tool_Feedbag",
            "Tool_Watering_Can"
    };

    public QualityConfig() {}

    // ── Weight getters ──
    public int getWeightPoor()      { return weightPoor; }
    public int getWeightCommon()    { return weightCommon; }
    public int getWeightUncommon()  { return weightUncommon; }
    public int getWeightRare()      { return weightRare; }
    public int getWeightEpic()      { return weightEpic; }
    public int getWeightLegendary() { return weightLegendary; }

    // ── Damage multiplier getters ──
    public double getDamageMultiplierPoor()      { return damageMultiplierPoor; }
    public double getDamageMultiplierCommon()    { return damageMultiplierCommon; }
    public double getDamageMultiplierUncommon()  { return damageMultiplierUncommon; }
    public double getDamageMultiplierRare()      { return damageMultiplierRare; }
    public double getDamageMultiplierEpic()      { return damageMultiplierEpic; }
    public double getDamageMultiplierLegendary() { return damageMultiplierLegendary; }

    // ── Tool multiplier getters ──
    public double getToolMultiplierPoor()      { return toolMultiplierPoor; }
    public double getToolMultiplierCommon()    { return toolMultiplierCommon; }
    public double getToolMultiplierUncommon()  { return toolMultiplierUncommon; }
    public double getToolMultiplierRare()      { return toolMultiplierRare; }
    public double getToolMultiplierEpic()      { return toolMultiplierEpic; }
    public double getToolMultiplierLegendary() { return toolMultiplierLegendary; }

    // ── Armor multiplier getters ──
    public double getArmorMultiplierPoor()      { return armorMultiplierPoor; }
    public double getArmorMultiplierCommon()    { return armorMultiplierCommon; }
    public double getArmorMultiplierUncommon()  { return armorMultiplierUncommon; }
    public double getArmorMultiplierRare()      { return armorMultiplierRare; }
    public double getArmorMultiplierEpic()      { return armorMultiplierEpic; }
    public double getArmorMultiplierLegendary() { return armorMultiplierLegendary; }

    // ── Signature Energy multiplier getters ──
    public double getSignatureMultiplierPoor()      { return signatureMultiplierPoor; }
    public double getSignatureMultiplierCommon()    { return signatureMultiplierCommon; }
    public double getSignatureMultiplierUncommon()  { return signatureMultiplierUncommon; }
    public double getSignatureMultiplierRare()      { return signatureMultiplierRare; }
    public double getSignatureMultiplierEpic()      { return signatureMultiplierEpic; }
    public double getSignatureMultiplierLegendary() { return signatureMultiplierLegendary; }

    // ── Durability multiplier getters ──
    public double getDurabilityMultiplierPoor()      { return durabilityMultiplierPoor; }
    public double getDurabilityMultiplierCommon()    { return durabilityMultiplierCommon; }
    public double getDurabilityMultiplierUncommon()  { return durabilityMultiplierUncommon; }
    public double getDurabilityMultiplierRare()      { return durabilityMultiplierRare; }
    public double getDurabilityMultiplierEpic()      { return durabilityMultiplierEpic; }
    public double getDurabilityMultiplierLegendary() { return durabilityMultiplierLegendary; }

    // ── Loot weight getters ──
    public int getLootWeightPoor()      { return lootWeightPoor; }
    public int getLootWeightCommon()    { return lootWeightCommon; }
    public int getLootWeightUncommon()  { return lootWeightUncommon; }
    public int getLootWeightRare()      { return lootWeightRare; }
    public int getLootWeightEpic()      { return lootWeightEpic; }
    public int getLootWeightLegendary() { return lootWeightLegendary; }

    // ── Loot toggle getter ──
    public boolean isLootQualityEnabled() { return lootQualityEnabled; }

    // ── Ignored item prefixes getter ──
    public String[] getIgnoredItemPrefixes() { return ignoredItemPrefixes; }
}
