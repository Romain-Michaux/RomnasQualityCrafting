package dev.hytalemodding.events;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import dev.hytalemodding.quality.QualityVariantBootstrap;

import javax.annotation.Nonnull;

/**
 * Handles player connection events to warn players if mod generation occurred.
 */
public class PlayerJoinHandler {
    
    private static final String RESTART_MESSAGE = "[RQC] WARNING: The mod has generated new JSON files. " +
            "A server restart is required for the new items to be available. " +
            "Please restart the server as soon as possible.";
    
    /**
     * Called when a player is ready.
     * Checks if generation happened and sends a warning message.
     */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        // Check if generation happened
        if (!QualityVariantBootstrap.wasGenerationPerformed()) {
            return;
        }
        
        // Get the Player from the event
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        
        // Send the warning message
        try {
            player.sendMessage(Message.raw(RESTART_MESSAGE).color("#ffaa00"));
        } catch (Exception e) {
            // Ignore message sending errors
        }
    }
}
