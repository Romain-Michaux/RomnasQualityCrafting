package dev.hytalemodding.quality;

public enum ItemQuality {
    POOR("Poor", 0.7f, 0.7f),
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

    // Weighted random selection
    public static ItemQuality random() {
        double rand = Math.random();
        if (rand < 0.40) return COMMON;      // 40%
        if (rand < 0.65) return POOR;        // 25%
        if (rand < 0.85) return UNCOMMON;    // 20%
        if (rand < 0.95) return RARE;        // 10%
        if (rand < 0.99) return EPIC;        // 4%
        return LEGENDARY;                     // 1%
    }
}
