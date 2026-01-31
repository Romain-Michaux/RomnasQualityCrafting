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
    
    private static final String FAILURE_MESSAGE = "[RQC] ERROR: Quality item generation FAILED. " +
            "Items will not be available until this issue is resolved. " +
            "Reason: %s";
    
    private static final String FAILURE_INSTRUCTION = "[RQC] To fix this issue:\n" +
            "1. Check the server console for detailed error messages\n" +
            "2. Configure 'CustomAssetsPath' in GeneratedModFolder/config.json\n" +
            "3. Point it to your Assets.zip file or extracted assets folder\n" +
            "4. Restart the server";
    
    /**
     * Called when a player is ready.
     * Checks if generation happened and sends appropriate message.
     */
    public static void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        // Get the Player from the event
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        
        // Check if generation failed
        if (QualityVariantBootstrap.didGenerationFail()) {
            String reason = QualityVariantBootstrap.getGenerationFailureReason();
            if (reason == null) {
                reason = "Unknown error";
            }
            
            try {
                // Send error message in red
                player.sendMessage(Message.raw(String.format(FAILURE_MESSAGE, reason)).color("#ff0000"));
                player.sendMessage(Message.raw(FAILURE_INSTRUCTION).color("#ffaa00"));
            } catch (Exception e) {
                // Ignore message sending errors
            }
            return;
        }
        
        // Check if generation succeeded
        if (QualityVariantBootstrap.wasGenerationPerformed()) {
            try {
                int generatedCount = QualityVariantBootstrap.getGeneratedFileCount();
                int errorCount = QualityVariantBootstrap.getErrorFileCount();
                
                // Build status message
                String statusMsg = String.format("[RQC] Generated %d item variant(s) with %d error(s). ", 
                                                generatedCount, errorCount);
                
                // Send status in yellow
                player.sendMessage(Message.raw(statusMsg).color("#ffaa00"));
                
                // Send restart warning in orange
                player.sendMessage(Message.raw(RESTART_MESSAGE).color("#ffaa00"));
            } catch (Exception e) {
                // Fallback to simple message
                try {
                    player.sendMessage(Message.raw(RESTART_MESSAGE).color("#ffaa00"));
                } catch (Exception ignored) {
                    // Ignore message sending errors
                }
            }
        }
    }
}
