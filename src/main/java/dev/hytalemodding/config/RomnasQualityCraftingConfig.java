package dev.hytalemodding.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * Configuration class for RomnasQualityCrafting mod.
 * Contains only quality weights for crafting chances.
 * All keys must be capitalized as per Hytale's requirements.
 */
public class RomnasQualityCraftingConfig {
    
    public static final BuilderCodec<RomnasQualityCraftingConfig> CODEC = BuilderCodec.builder(
            RomnasQualityCraftingConfig.class, 
            RomnasQualityCraftingConfig::new)
        // Quality weights for crafting chances
        .append(new KeyedCodec<Integer>("QualityWeightPoor", Codec.INTEGER),
                (config, value) -> config.qualityWeightPoor = value,
                (config) -> config.qualityWeightPoor).add()
        .append(new KeyedCodec<Integer>("QualityWeightCommon", Codec.INTEGER),
                (config, value) -> config.qualityWeightCommon = value,
                (config) -> config.qualityWeightCommon).add()
        .append(new KeyedCodec<Integer>("QualityWeightUncommon", Codec.INTEGER),
                (config, value) -> config.qualityWeightUncommon = value,
                (config) -> config.qualityWeightUncommon).add()
        .append(new KeyedCodec<Integer>("QualityWeightRare", Codec.INTEGER),
                (config, value) -> config.qualityWeightRare = value,
                (config) -> config.qualityWeightRare).add()
        .append(new KeyedCodec<Integer>("QualityWeightEpic", Codec.INTEGER),
                (config, value) -> config.qualityWeightEpic = value,
                (config) -> config.qualityWeightEpic).add()
        .append(new KeyedCodec<Integer>("QualityWeightLegendary", Codec.INTEGER),
                (config, value) -> config.qualityWeightLegendary = value,
                (config) -> config.qualityWeightLegendary).add()
        // Force reset assets flag (resets to false after regeneration)
        .append(new KeyedCodec<Boolean>("ForceResetAssets", Codec.BOOLEAN),
                (config, value) -> config.forceResetAssets = value,
                (config) -> config.forceResetAssets).add()
        // External mods compatibility flag
        .append(new KeyedCodec<Boolean>("ExternalModsCompatEnabled", Codec.BOOLEAN),
                (config, value) -> config.externalModsCompatEnabled = value,
                (config) -> config.externalModsCompatEnabled).add()
        // Quality multipliers for damage
        .append(new KeyedCodec<Double>("QualityDamageMultiplierPoor", Codec.DOUBLE),
                (config, value) -> config.qualityDamageMultiplierPoor = value,
                (config) -> config.qualityDamageMultiplierPoor).add()
        .append(new KeyedCodec<Double>("QualityDamageMultiplierCommon", Codec.DOUBLE),
                (config, value) -> config.qualityDamageMultiplierCommon = value,
                (config) -> config.qualityDamageMultiplierCommon).add()
        .append(new KeyedCodec<Double>("QualityDamageMultiplierUncommon", Codec.DOUBLE),
                (config, value) -> config.qualityDamageMultiplierUncommon = value,
                (config) -> config.qualityDamageMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("QualityDamageMultiplierRare", Codec.DOUBLE),
                (config, value) -> config.qualityDamageMultiplierRare = value,
                (config) -> config.qualityDamageMultiplierRare).add()
        .append(new KeyedCodec<Double>("QualityDamageMultiplierEpic", Codec.DOUBLE),
                (config, value) -> config.qualityDamageMultiplierEpic = value,
                (config) -> config.qualityDamageMultiplierEpic).add()
        .append(new KeyedCodec<Double>("QualityDamageMultiplierLegendary", Codec.DOUBLE),
                (config, value) -> config.qualityDamageMultiplierLegendary = value,
                (config) -> config.qualityDamageMultiplierLegendary).add()
        // Quality multipliers for durability
        .append(new KeyedCodec<Double>("QualityDurabilityMultiplierPoor", Codec.DOUBLE),
                (config, value) -> config.qualityDurabilityMultiplierPoor = value,
                (config) -> config.qualityDurabilityMultiplierPoor).add()
        .append(new KeyedCodec<Double>("QualityDurabilityMultiplierCommon", Codec.DOUBLE),
                (config, value) -> config.qualityDurabilityMultiplierCommon = value,
                (config) -> config.qualityDurabilityMultiplierCommon).add()
        .append(new KeyedCodec<Double>("QualityDurabilityMultiplierUncommon", Codec.DOUBLE),
                (config, value) -> config.qualityDurabilityMultiplierUncommon = value,
                (config) -> config.qualityDurabilityMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("QualityDurabilityMultiplierRare", Codec.DOUBLE),
                (config, value) -> config.qualityDurabilityMultiplierRare = value,
                (config) -> config.qualityDurabilityMultiplierRare).add()
        .append(new KeyedCodec<Double>("QualityDurabilityMultiplierEpic", Codec.DOUBLE),
                (config, value) -> config.qualityDurabilityMultiplierEpic = value,
                (config) -> config.qualityDurabilityMultiplierEpic).add()
        .append(new KeyedCodec<Double>("QualityDurabilityMultiplierLegendary", Codec.DOUBLE),
                (config, value) -> config.qualityDurabilityMultiplierLegendary = value,
                (config) -> config.qualityDurabilityMultiplierLegendary).add()
        // Quality multipliers for tool efficiency
        .append(new KeyedCodec<Double>("QualityToolEfficiencyMultiplierPoor", Codec.DOUBLE),
                (config, value) -> config.qualityToolEfficiencyMultiplierPoor = value,
                (config) -> config.qualityToolEfficiencyMultiplierPoor).add()
        .append(new KeyedCodec<Double>("QualityToolEfficiencyMultiplierCommon", Codec.DOUBLE),
                (config, value) -> config.qualityToolEfficiencyMultiplierCommon = value,
                (config) -> config.qualityToolEfficiencyMultiplierCommon).add()
        .append(new KeyedCodec<Double>("QualityToolEfficiencyMultiplierUncommon", Codec.DOUBLE),
                (config, value) -> config.qualityToolEfficiencyMultiplierUncommon = value,
                (config) -> config.qualityToolEfficiencyMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("QualityToolEfficiencyMultiplierRare", Codec.DOUBLE),
                (config, value) -> config.qualityToolEfficiencyMultiplierRare = value,
                (config) -> config.qualityToolEfficiencyMultiplierRare).add()
        .append(new KeyedCodec<Double>("QualityToolEfficiencyMultiplierEpic", Codec.DOUBLE),
                (config, value) -> config.qualityToolEfficiencyMultiplierEpic = value,
                (config) -> config.qualityToolEfficiencyMultiplierEpic).add()
        .append(new KeyedCodec<Double>("QualityToolEfficiencyMultiplierLegendary", Codec.DOUBLE),
                (config, value) -> config.qualityToolEfficiencyMultiplierLegendary = value,
                (config) -> config.qualityToolEfficiencyMultiplierLegendary).add()
        // Quality multipliers for armor stats
        .append(new KeyedCodec<Double>("QualityArmorMultiplierPoor", Codec.DOUBLE),
                (config, value) -> config.qualityArmorMultiplierPoor = value,
                (config) -> config.qualityArmorMultiplierPoor).add()
        .append(new KeyedCodec<Double>("QualityArmorMultiplierCommon", Codec.DOUBLE),
                (config, value) -> config.qualityArmorMultiplierCommon = value,
                (config) -> config.qualityArmorMultiplierCommon).add()
        .append(new KeyedCodec<Double>("QualityArmorMultiplierUncommon", Codec.DOUBLE),
                (config, value) -> config.qualityArmorMultiplierUncommon = value,
                (config) -> config.qualityArmorMultiplierUncommon).add()
        .append(new KeyedCodec<Double>("QualityArmorMultiplierRare", Codec.DOUBLE),
                (config, value) -> config.qualityArmorMultiplierRare = value,
                (config) -> config.qualityArmorMultiplierRare).add()
        .append(new KeyedCodec<Double>("QualityArmorMultiplierEpic", Codec.DOUBLE),
                (config, value) -> config.qualityArmorMultiplierEpic = value,
                (config) -> config.qualityArmorMultiplierEpic).add()
        .append(new KeyedCodec<Double>("QualityArmorMultiplierLegendary", Codec.DOUBLE),
                (config, value) -> config.qualityArmorMultiplierLegendary = value,
                (config) -> config.qualityArmorMultiplierLegendary).add()
        // Custom path to Assets.zip or assets folder (optional, empty by default)
        .append(new KeyedCodec<String>("CustomAssetsPath", Codec.STRING),
                (config, value) -> config.customAssetsPath = value,
                (config) -> config.customAssetsPath).add()
        // Custom path to global Mods directory (optional, empty by default)
        .append(new KeyedCodec<String>("CustomGlobalModsPath", Codec.STRING),
                (config, value) -> config.customGlobalModsPath = value,
                (config) -> config.customGlobalModsPath).add()
        .build();
    
    // Quality weights (default values: Poor=25, Common=40, Uncommon=20, Rare=10, Epic=4, Legendary=1)
    private int qualityWeightPoor = 25;
    private int qualityWeightCommon = 40;
    private int qualityWeightUncommon = 20;
    private int qualityWeightRare = 10;
    private int qualityWeightEpic = 4;
    private int qualityWeightLegendary = 1;
    
    // Force reset assets flag (resets to false after regeneration)
    private boolean forceResetAssets = false;
    
    // External mods compatibility flag
    private boolean externalModsCompatEnabled = true;
    
    // Quality damage multipliers (default values matching ItemQuality enum)
    private double qualityDamageMultiplierPoor = 0.7;
    private double qualityDamageMultiplierCommon = 1.0;
    private double qualityDamageMultiplierUncommon = 1.2;
    private double qualityDamageMultiplierRare = 1.4;
    private double qualityDamageMultiplierEpic = 1.6;
    private double qualityDamageMultiplierLegendary = 2.0;
    
    // Quality durability multipliers (default values matching ItemQuality enum)
    private double qualityDurabilityMultiplierPoor = 0.7;
    private double qualityDurabilityMultiplierCommon = 1.0;
    private double qualityDurabilityMultiplierUncommon = 1.15;
    private double qualityDurabilityMultiplierRare = 1.3;
    private double qualityDurabilityMultiplierEpic = 1.5;
    private double qualityDurabilityMultiplierLegendary = 2.0;
    
    // Quality tool efficiency multipliers (default values matching ItemQuality enum)
    private double qualityToolEfficiencyMultiplierPoor = 0.7;
    private double qualityToolEfficiencyMultiplierCommon = 1.0;
    private double qualityToolEfficiencyMultiplierUncommon = 1.2;
    private double qualityToolEfficiencyMultiplierRare = 1.4;
    private double qualityToolEfficiencyMultiplierEpic = 1.6;
    private double qualityToolEfficiencyMultiplierLegendary = 2.0;
    
    // Quality armor multipliers (default values matching ItemQuality enum)
    private double qualityArmorMultiplierPoor = 0.7;
    private double qualityArmorMultiplierCommon = 1.0;
    private double qualityArmorMultiplierUncommon = 1.2;
    private double qualityArmorMultiplierRare = 1.4;
    private double qualityArmorMultiplierEpic = 1.6;
    private double qualityArmorMultiplierLegendary = 2.0;
    
    // Custom path to Assets.zip or assets folder (empty by default)
    private String customAssetsPath = "";
    
    // Custom path to global Mods directory (empty by default)
    private String customGlobalModsPath = "";
    
    // Note: ExcludedIdPrefixes and ExcludedItems are loaded directly from JSON
    // via QualityConfigManager, not through the codec (as list codecs are not available)
    
    public RomnasQualityCraftingConfig() {
    }
    
    // Getters for quality weights
    public int getQualityWeightPoor() {
        return qualityWeightPoor;
    }
    
    public int getQualityWeightCommon() {
        return qualityWeightCommon;
    }
    
    public int getQualityWeightUncommon() {
        return qualityWeightUncommon;
    }
    
    public int getQualityWeightRare() {
        return qualityWeightRare;
    }
    
    public int getQualityWeightEpic() {
        return qualityWeightEpic;
    }
    
    public int getQualityWeightLegendary() {
        return qualityWeightLegendary;
    }
    
    // Setters for quality weights
    public void setQualityWeightPoor(int qualityWeightPoor) {
        this.qualityWeightPoor = qualityWeightPoor;
    }
    
    public void setQualityWeightCommon(int qualityWeightCommon) {
        this.qualityWeightCommon = qualityWeightCommon;
    }
    
    public void setQualityWeightUncommon(int qualityWeightUncommon) {
        this.qualityWeightUncommon = qualityWeightUncommon;
    }
    
    public void setQualityWeightRare(int qualityWeightRare) {
        this.qualityWeightRare = qualityWeightRare;
    }
    
    public void setQualityWeightEpic(int qualityWeightEpic) {
        this.qualityWeightEpic = qualityWeightEpic;
    }
    
    public void setQualityWeightLegendary(int qualityWeightLegendary) {
        this.qualityWeightLegendary = qualityWeightLegendary;
    }
    
    // Getters and setters for new flags
    public boolean isForceResetAssets() {
        return forceResetAssets;
    }
    
    public void setForceResetAssets(boolean forceResetAssets) {
        this.forceResetAssets = forceResetAssets;
    }
    
    public boolean isExternalModsCompatEnabled() {
        return externalModsCompatEnabled;
    }
    
    public void setExternalModsCompatEnabled(boolean externalModsCompatEnabled) {
        this.externalModsCompatEnabled = externalModsCompatEnabled;
    }
    
    // Getters for quality damage multipliers
    public double getQualityDamageMultiplierPoor() {
        return qualityDamageMultiplierPoor;
    }
    
    public double getQualityDamageMultiplierCommon() {
        return qualityDamageMultiplierCommon;
    }
    
    public double getQualityDamageMultiplierUncommon() {
        return qualityDamageMultiplierUncommon;
    }
    
    public double getQualityDamageMultiplierRare() {
        return qualityDamageMultiplierRare;
    }
    
    public double getQualityDamageMultiplierEpic() {
        return qualityDamageMultiplierEpic;
    }
    
    public double getQualityDamageMultiplierLegendary() {
        return qualityDamageMultiplierLegendary;
    }
    
    // Setters for quality damage multipliers
    public void setQualityDamageMultiplierPoor(double qualityDamageMultiplierPoor) {
        this.qualityDamageMultiplierPoor = qualityDamageMultiplierPoor;
    }
    
    public void setQualityDamageMultiplierCommon(double qualityDamageMultiplierCommon) {
        this.qualityDamageMultiplierCommon = qualityDamageMultiplierCommon;
    }
    
    public void setQualityDamageMultiplierUncommon(double qualityDamageMultiplierUncommon) {
        this.qualityDamageMultiplierUncommon = qualityDamageMultiplierUncommon;
    }
    
    public void setQualityDamageMultiplierRare(double qualityDamageMultiplierRare) {
        this.qualityDamageMultiplierRare = qualityDamageMultiplierRare;
    }
    
    public void setQualityDamageMultiplierEpic(double qualityDamageMultiplierEpic) {
        this.qualityDamageMultiplierEpic = qualityDamageMultiplierEpic;
    }
    
    public void setQualityDamageMultiplierLegendary(double qualityDamageMultiplierLegendary) {
        this.qualityDamageMultiplierLegendary = qualityDamageMultiplierLegendary;
    }
    
    // Getters for quality durability multipliers
    public double getQualityDurabilityMultiplierPoor() {
        return qualityDurabilityMultiplierPoor;
    }
    
    public double getQualityDurabilityMultiplierCommon() {
        return qualityDurabilityMultiplierCommon;
    }
    
    public double getQualityDurabilityMultiplierUncommon() {
        return qualityDurabilityMultiplierUncommon;
    }
    
    public double getQualityDurabilityMultiplierRare() {
        return qualityDurabilityMultiplierRare;
    }
    
    public double getQualityDurabilityMultiplierEpic() {
        return qualityDurabilityMultiplierEpic;
    }
    
    public double getQualityDurabilityMultiplierLegendary() {
        return qualityDurabilityMultiplierLegendary;
    }
    
    // Setters for quality durability multipliers
    public void setQualityDurabilityMultiplierPoor(double qualityDurabilityMultiplierPoor) {
        this.qualityDurabilityMultiplierPoor = qualityDurabilityMultiplierPoor;
    }
    
    public void setQualityDurabilityMultiplierCommon(double qualityDurabilityMultiplierCommon) {
        this.qualityDurabilityMultiplierCommon = qualityDurabilityMultiplierCommon;
    }
    
    public void setQualityDurabilityMultiplierUncommon(double qualityDurabilityMultiplierUncommon) {
        this.qualityDurabilityMultiplierUncommon = qualityDurabilityMultiplierUncommon;
    }
    
    public void setQualityDurabilityMultiplierRare(double qualityDurabilityMultiplierRare) {
        this.qualityDurabilityMultiplierRare = qualityDurabilityMultiplierRare;
    }
    
    public void setQualityDurabilityMultiplierEpic(double qualityDurabilityMultiplierEpic) {
        this.qualityDurabilityMultiplierEpic = qualityDurabilityMultiplierEpic;
    }
    
    public void setQualityDurabilityMultiplierLegendary(double qualityDurabilityMultiplierLegendary) {
        this.qualityDurabilityMultiplierLegendary = qualityDurabilityMultiplierLegendary;
    }
    
    // Getters for quality tool efficiency multipliers
    public double getQualityToolEfficiencyMultiplierPoor() {
        return qualityToolEfficiencyMultiplierPoor;
    }
    
    public double getQualityToolEfficiencyMultiplierCommon() {
        return qualityToolEfficiencyMultiplierCommon;
    }
    
    public double getQualityToolEfficiencyMultiplierUncommon() {
        return qualityToolEfficiencyMultiplierUncommon;
    }
    
    public double getQualityToolEfficiencyMultiplierRare() {
        return qualityToolEfficiencyMultiplierRare;
    }
    
    public double getQualityToolEfficiencyMultiplierEpic() {
        return qualityToolEfficiencyMultiplierEpic;
    }
    
    public double getQualityToolEfficiencyMultiplierLegendary() {
        return qualityToolEfficiencyMultiplierLegendary;
    }
    
    // Setters for quality tool efficiency multipliers
    public void setQualityToolEfficiencyMultiplierPoor(double qualityToolEfficiencyMultiplierPoor) {
        this.qualityToolEfficiencyMultiplierPoor = qualityToolEfficiencyMultiplierPoor;
    }
    
    public void setQualityToolEfficiencyMultiplierCommon(double qualityToolEfficiencyMultiplierCommon) {
        this.qualityToolEfficiencyMultiplierCommon = qualityToolEfficiencyMultiplierCommon;
    }
    
    public void setQualityToolEfficiencyMultiplierUncommon(double qualityToolEfficiencyMultiplierUncommon) {
        this.qualityToolEfficiencyMultiplierUncommon = qualityToolEfficiencyMultiplierUncommon;
    }
    
    public void setQualityToolEfficiencyMultiplierRare(double qualityToolEfficiencyMultiplierRare) {
        this.qualityToolEfficiencyMultiplierRare = qualityToolEfficiencyMultiplierRare;
    }
    
    public void setQualityToolEfficiencyMultiplierEpic(double qualityToolEfficiencyMultiplierEpic) {
        this.qualityToolEfficiencyMultiplierEpic = qualityToolEfficiencyMultiplierEpic;
    }
    
    public void setQualityToolEfficiencyMultiplierLegendary(double qualityToolEfficiencyMultiplierLegendary) {
        this.qualityToolEfficiencyMultiplierLegendary = qualityToolEfficiencyMultiplierLegendary;
    }
    
    // Getters for quality armor multipliers
    public double getQualityArmorMultiplierPoor() {
        return qualityArmorMultiplierPoor;
    }
    
    public double getQualityArmorMultiplierCommon() {
        return qualityArmorMultiplierCommon;
    }
    
    public double getQualityArmorMultiplierUncommon() {
        return qualityArmorMultiplierUncommon;
    }
    
    public double getQualityArmorMultiplierRare() {
        return qualityArmorMultiplierRare;
    }
    
    public double getQualityArmorMultiplierEpic() {
        return qualityArmorMultiplierEpic;
    }
    
    public double getQualityArmorMultiplierLegendary() {
        return qualityArmorMultiplierLegendary;
    }
    
    // Setters for quality armor multipliers
    public void setQualityArmorMultiplierPoor(double qualityArmorMultiplierPoor) {
        this.qualityArmorMultiplierPoor = qualityArmorMultiplierPoor;
    }
    
    public void setQualityArmorMultiplierCommon(double qualityArmorMultiplierCommon) {
        this.qualityArmorMultiplierCommon = qualityArmorMultiplierCommon;
    }
    
    public void setQualityArmorMultiplierUncommon(double qualityArmorMultiplierUncommon) {
        this.qualityArmorMultiplierUncommon = qualityArmorMultiplierUncommon;
    }
    
    public void setQualityArmorMultiplierRare(double qualityArmorMultiplierRare) {
        this.qualityArmorMultiplierRare = qualityArmorMultiplierRare;
    }
    
    public void setQualityArmorMultiplierEpic(double qualityArmorMultiplierEpic) {
        this.qualityArmorMultiplierEpic = qualityArmorMultiplierEpic;
    }
    
    public void setQualityArmorMultiplierLegendary(double qualityArmorMultiplierLegendary) {
        this.qualityArmorMultiplierLegendary = qualityArmorMultiplierLegendary;
    }
    
    // Getter and setter for custom assets path
    public String getCustomAssetsPath() {
        return customAssetsPath;
    }
    
    public void setCustomAssetsPath(String customAssetsPath) {
        this.customAssetsPath = customAssetsPath;
    }
    
    // Getter and setter for custom global mods path
    public String getCustomGlobalModsPath() {
        return customGlobalModsPath;
    }
    
    public void setCustomGlobalModsPath(String customGlobalModsPath) {
        this.customGlobalModsPath = customGlobalModsPath;
    }
    
    // Note: Excluded lists are accessed via QualityConfigManager.getExcludedIdPrefixes() 
    // and QualityConfigManager.getExcludedItems() which load directly from JSON
}
