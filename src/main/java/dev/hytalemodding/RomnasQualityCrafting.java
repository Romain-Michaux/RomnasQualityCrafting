package dev.hytalemodding;

import com.hypixel.hytale.server.core.event.events.BootEvent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import dev.hytalemodding.config.RomnasQualityCraftingConfig;
import dev.hytalemodding.events.InventoryChangeHandler;
import dev.hytalemodding.events.PlayerJoinHandler;
import dev.hytalemodding.quality.QualityVariantBootstrap;
import dev.hytalemodding.reforge.OpenReforgePageInteraction;
import dev.hytalemodding.rune.OpenApplyRunePageInteraction;

import javax.annotation.Nonnull;

public class RomnasQualityCrafting extends JavaPlugin {

    private final Object config; // Config<RomnasQualityCraftingConfig> - using Object to avoid import issues

    public RomnasQualityCrafting(@Nonnull JavaPluginInit init) {
        super(init);
        // Load configuration in constructor (must be done before setup)
        config = this.withConfig("RomnasQualityCrafting", RomnasQualityCraftingConfig.CODEC);
    }

    @Override
    protected void setup() {
        // Save config to ensure it's created if it doesn't exist
        try {
            java.lang.reflect.Method saveMethod = config.getClass().getMethod("save");
            saveMethod.invoke(config);
        } catch (Exception e) {
            // Ignore if save method doesn't exist
        }
        
        // Initialize config managers
        try {
            java.lang.reflect.Method getMethod = config.getClass().getMethod("get");
            RomnasQualityCraftingConfig configData = (RomnasQualityCraftingConfig) getMethod.invoke(config);
            // Initialize logging
            dev.hytalemodding.quality.JsonQualityGenerator.initializeLogging(this, configData);
            // Initialize quality config manager for static access
            dev.hytalemodding.quality.QualityConfigManager.initialize(configData);
        } catch (Exception e) {
            // If config is not available, use default
        }
        // Génère les fichiers JSON pour les 6 variantes de qualité (Poor…Legendary) pour chaque arme/outil/armure au lancement
        this.getEventRegistry().register(BootEvent.class, event -> QualityVariantBootstrap.run(this, event));

        // Register inventory change handler (craft/loot → apply quality; respects AutoApplyQuality)
        this.getEventRegistry().registerGlobal(
            LivingEntityInventoryChangeEvent.class,
            InventoryChangeHandler::onInventoryChange
        );
        
        // Register player ready handler (warns players if generation happened)
        this.getEventRegistry().registerGlobal(
            PlayerReadyEvent.class,
            PlayerJoinHandler::onPlayerReady
        );

            this.getCodecRegistry(Interaction.CODEC).register(
                "OpenReforgePage",
                OpenReforgePageInteraction.class,
                OpenReforgePageInteraction.CODEC
            );
            this.getCodecRegistry(Interaction.CODEC).register(
                "ReforgeFromViewerPage",
                dev.hytalemodding.qualityviewer.ReforgeFromViewerPageInteraction.class,
                dev.hytalemodding.qualityviewer.ReforgeFromViewerPageInteraction.CODEC
            );
    

            this.getCodecRegistry(Interaction.CODEC).register(
                "OpenQualityViewerPage",
                dev.hytalemodding.qualityviewer.OpenQualityViewerPageInteraction.class,
                dev.hytalemodding.qualityviewer.OpenQualityViewerPageInteraction.CODEC
            );
            this.getCodecRegistry(Interaction.CODEC).register(
                "ApplyRuneFromViewerPage",
                dev.hytalemodding.qualityviewer.ApplyRuneFromViewerPageInteraction.class,
                dev.hytalemodding.qualityviewer.ApplyRuneFromViewerPageInteraction.CODEC
            );

        this.getCodecRegistry(Interaction.CODEC).register(
            "OpenApplyRunePage",
            OpenApplyRunePageInteraction.class,
            OpenApplyRunePageInteraction.CODEC
        );

        this.getEntityStoreRegistry().registerSystem(new dev.hytalemodding.rune.RuneDamageEffectSystem());
        this.getEntityStoreRegistry().registerSystem(new dev.hytalemodding.rune.RuneLuckMiningSystem());
        // Rune Speed is applied in InventoryChangeHandler via RuneSpeedBootsHandler; no separate system to register
    }
    
    /**
     * Gets the configuration instance.
     * @return The configuration for this plugin
     */
    public Object getConfig() {
        return config;
    }
    
    /**
     * Gets the configuration data.
     * @return The configuration data object
     */
    public RomnasQualityCraftingConfig getConfigData() {
        try {
            java.lang.reflect.Method getMethod = config.getClass().getMethod("get");
            return (RomnasQualityCraftingConfig) getMethod.invoke(config);
        } catch (Exception e) {
            return new RomnasQualityCraftingConfig(); // Return default config on error
        }
    }
}