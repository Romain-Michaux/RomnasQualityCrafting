package dev.hytalemodding.quality;

import dev.hytalemodding.config.RomnasQualityCraftingConfig;

import javax.annotation.Nullable;

public enum ItemQuality {
    POOR("Junk", 0.7f, 0.7f),
    COMMON("Common", 1.0f, 1.0f),
    UNCOMMON("Uncommon", 1.2f, 1.15f),
    RARE("Rare", 1.4f, 1.3f),
    EPIC("Epic", 1.6f, 1.5f),
    LEGENDARY("Legendary", 2.0f, 2.0f);

    private final String displayName;
    private final float damageMultiplier;
    private final float durabilityMultiplier;

    ItemQuality(String displayName, float damageMultiplier, float durabilityMultiplier) {
        this.displayName = displayName;
        this.damageMultiplier = damageMultiplier;
        this.durabilityMultiplier = durabilityMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public float getDurabilityMultiplier() {
        return durabilityMultiplier;
    }

    /** 
     * Poids par défaut (40% Common, 25% Poor, 20% Uncommon, 10% Rare, 4% Epic, 1% Legendary).
     * Utilisé comme fallback si la config n'est pas disponible.
     */
    public static ItemQuality random() {
        double rand = Math.random();
        if (rand < 0.40) return COMMON;      // 40%
        if (rand < 0.65) return POOR;        // 25%
        if (rand < 0.85) return UNCOMMON;    // 20%
        if (rand < 0.95) return RARE;        // 10%
        if (rand < 0.99) return EPIC;        // 4%
        return LEGENDARY;                     // 1%
    }

    /** 
     * Génère une qualité aléatoire en utilisant les poids de la configuration.
     * Utilise QualityConfigManager pour obtenir la config.
     * @return Une qualité aléatoire basée sur les poids de la config, ou random() si la config n'est pas disponible
     */
    public static ItemQuality randomFromConfig() {
        RomnasQualityCraftingConfig config = QualityConfigManager.getConfig();
        return random(config);
    }
    
    /** 
     * Génère une qualité aléatoire en utilisant les poids de la configuration.
     * @param config La configuration contenant les poids des qualités
     * @return Une qualité aléatoire basée sur les poids de la config, ou random() si la config est null
     */
    public static ItemQuality random(@Nullable RomnasQualityCraftingConfig config) {
        if (config == null) {
            return random(); // Fallback to default if config is null
        }
        
        // Récupérer les poids depuis la config
        int weightPoor = config.getQualityWeightPoor();
        int weightCommon = config.getQualityWeightCommon();
        int weightUncommon = config.getQualityWeightUncommon();
        int weightRare = config.getQualityWeightRare();
        int weightEpic = config.getQualityWeightEpic();
        int weightLegendary = config.getQualityWeightLegendary();
        
        // Calculer le total des poids
        int totalWeight = weightPoor + weightCommon + weightUncommon + weightRare + weightEpic + weightLegendary;
        
        if (totalWeight <= 0) {
            // Si tous les poids sont à 0 ou négatifs, utiliser les valeurs par défaut
            return random();
        }
        
        // Générer un nombre aléatoire entre 0 et totalWeight
        double rand = Math.random() * totalWeight;
        
        // Déterminer quelle qualité choisir en fonction des poids
        int cumulative = 0;
        
        cumulative += weightPoor;
        if (rand < cumulative) return POOR;
        
        cumulative += weightCommon;
        if (rand < cumulative) return COMMON;
        
        cumulative += weightUncommon;
        if (rand < cumulative) return UNCOMMON;
        
        cumulative += weightRare;
        if (rand < cumulative) return RARE;
        
        cumulative += weightEpic;
        if (rand < cumulative) return EPIC;
        
        return LEGENDARY;
    }
}
