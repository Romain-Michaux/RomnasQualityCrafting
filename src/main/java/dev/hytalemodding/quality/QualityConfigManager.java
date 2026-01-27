package dev.hytalemodding.quality;

import dev.hytalemodding.config.RomnasQualityCraftingConfig;

import javax.annotation.Nullable;

/**
 * Manages access to the quality configuration from static contexts.
 */
public class QualityConfigManager {
    
    private static RomnasQualityCraftingConfig config = null;
    
    /**
     * Initializes the configuration manager with the config data.
     * Should be called from the plugin's setup() method.
     * @param configData The configuration data
     */
    public static void initialize(@Nullable RomnasQualityCraftingConfig configData) {
        config = configData;
    }
    
    /**
     * Gets the current configuration.
     * @return The configuration, or null if not initialized
     */
    @Nullable
    public static RomnasQualityCraftingConfig getConfig() {
        return config;
    }
}
