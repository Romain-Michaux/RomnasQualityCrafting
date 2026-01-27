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
        .build();
    
    // Quality weights (default values: Poor=25, Common=40, Uncommon=20, Rare=10, Epic=4, Legendary=1)
    private int qualityWeightPoor = 25;
    private int qualityWeightCommon = 40;
    private int qualityWeightUncommon = 20;
    private int qualityWeightRare = 10;
    private int qualityWeightEpic = 4;
    private int qualityWeightLegendary = 1;
    
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
}
