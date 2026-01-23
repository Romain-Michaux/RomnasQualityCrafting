package dev.hytalemodding;

import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import dev.hytalemodding.events.InventoryChangeHandler;
import dev.hytalemodding.reforge.OpenReforgePageInteraction;

import javax.annotation.Nonnull;

public class RomnasQualityCrafting extends JavaPlugin {

    public RomnasQualityCrafting(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        // Register inventory change handler (handles both crafting and looting!)
        this.getEventRegistry().registerGlobal(
            LivingEntityInventoryChangeEvent.class,
            InventoryChangeHandler::onInventoryChange
        );
        
        // Register reforge page interaction
        this.getCodecRegistry(Interaction.CODEC).register(
            "OpenReforgePage",
            OpenReforgePageInteraction.class,
            OpenReforgePageInteraction.CODEC
        );
    }
}