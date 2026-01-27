package dev.hytalemodding.rune;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Applies 1.5x movement speed when the player wears legs armor (boots) with the Speed rune.
 * Uses {@link MovementManager} and {@link MovementSettings} like the hermesboots mod:
 * multiplies forwardSprintSpeedMultiplier and climb speeds, then calls manager.update().
 * On unequip we reset via applyDefaultSettings().
 * Set DEBUG_SPEED_RUNE = true temporarily to log when the handler runs and whether speed is applied.
 */
public final class RuneSpeedBootsHandler {

    private static final float SPEED_MULTIPLIER = 1.5f;
    /** Enable to trace speed rune logic in console (disable in release). */
    public static final boolean DEBUG_SPEED_RUNE = false;

    public static void onInventoryChange(@Nonnull LivingEntityInventoryChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player player = (Player) entity;
        UUID uuid = resolveUuid(player);
        if (uuid == null) {
            return;
        }
        PlayerRef playerRef = Universe.get().getPlayer(uuid);
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        if (store == null) {
            return;
        }
        updateSpeedFromLegs(store, ref, player);
    }

    private static UUID resolveUuid(Player player) {
        try {
            var m = player.getClass().getMethod("getUuid");
            Object u = m.invoke(player);
            return u instanceof UUID ? (UUID) u : null;
        } catch (Exception e1) {
            try {
                var m = player.getClass().getMethod("getUniqueId");
                Object u = m.invoke(player);
                return u instanceof UUID ? (UUID) u : null;
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static void updateSpeedFromLegs(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Player player) {

        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        if (manager == null) {
            return;
        }

        // Scan all armor slots: "boots" = legs armor (Armor_*_Legs_*) in this mod; slot index varies by game (often 2=legs, 3=feet)
        boolean shouldHaveSpeed = false;
        var inv = player.getInventory();
        if (inv != null) {
            ItemContainer armor = inv.getArmor();
            if (armor != null) {
                int cap = armor.getCapacity();
                for (int s = 0; s < cap; s++) {
                    ItemStack stack = armor.getItemStack((short) s);
                    if (stack != null && !stack.isEmpty()
                            && RuneManager.isBoots(stack)
                            && RuneManager.RUNE_SPEED.equals(RuneManager.getAppliedRune(stack))) {
                        shouldHaveSpeed = true;
                        if (DEBUG_SPEED_RUNE) {
                            System.out.println("[RuneSpeed] Speed rune found on boots in armor slot " + s + ", item=" + stack.getItemId());
                        }
                        break;
                    }
                }
            }
        }

        if (DEBUG_SPEED_RUNE) {
            System.out.println("[RuneSpeed] shouldHaveSpeed=" + shouldHaveSpeed + ", manager=" + (manager != null) + ", conn=" + (player.getPlayerConnection() != null));
        }

        try {
            // Reset to default first so we never stack multipliers on re-apply
            manager.applyDefaultSettings();

            if (shouldHaveSpeed) {
                MovementSettings settings = manager.getSettings();
                if (settings != null) {
                    settings.forwardSprintSpeedMultiplier *= SPEED_MULTIPLIER;
                    settings.climbUpSprintSpeed *= SPEED_MULTIPLIER;
                    settings.climbDownSprintSpeed *= SPEED_MULTIPLIER;
                    if (DEBUG_SPEED_RUNE) {
                        System.out.println("[RuneSpeed] Applied " + SPEED_MULTIPLIER + "x to forwardSprintSpeedMultiplier and climb speeds");
                    }
                }
            }

            var conn = player.getPlayerConnection();
            if (conn != null) {
                manager.update(conn);
            }
        } catch (Exception e) {
            if (DEBUG_SPEED_RUNE) {
                e.printStackTrace();
            }
            // MovementManager/Settings API may vary by game version
        }
    }

    private RuneSpeedBootsHandler() {}
}
