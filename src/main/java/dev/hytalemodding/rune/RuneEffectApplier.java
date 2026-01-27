package dev.hytalemodding.rune;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nullable;

/**
 * Helper to apply Burn/Poison rune effects after damage.
 * Called from a DamageEventSystem in the Inspect group when you have:
 * - targetRef = chunk.getReferenceTo(index)
 * - damage.getSource() instanceof Damage.EntitySource
 * - attacker = store.getComponent(attackerRef, Player.getComponentType())
 * Then call applyIfRuneWeapon(attacker, targetRef, store).
 */
public final class RuneEffectApplier {

    public static final float BURN_DURATION_SEC = 4.0f;
    public static final float POISON_DURATION_SEC = 5.0f;

    private RuneEffectApplier() {}

    /**
     * If the player's held weapon has a Burn or Poison rune, apply that effect to the target.
     * Returns true if an effect was applied.
     */
    public static boolean applyIfRuneWeapon(
            @Nullable Player player,
            @Nullable Ref<EntityStore> targetRef,
            @Nullable Store<EntityStore> store) {
        if (player == null || targetRef == null || store == null) {
            return false;
        }
        ItemStack weapon = getHeldWeapon(player);
        if (weapon == null || weapon.isEmpty()) {
            return false;
        }
        String rune = RuneManager.getAppliedRune(weapon);
        if (rune == null) {
            return false;
        }
        EffectControllerComponent effectController = store.getComponent(targetRef, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            return false;
        }
        EntityEffect effect = null;
        float duration = 0f;
        switch (rune) {
            case RuneManager.RUNE_BURN:
                effect = EntityEffect.getAssetMap().getAsset("Burning");
                if (effect == null) {
                    effect = EntityEffect.getAssetMap().getAsset("Fire");
                }
                duration = BURN_DURATION_SEC;
                break;
            case RuneManager.RUNE_POISON:
                effect = EntityEffect.getAssetMap().getAsset("Poison");
                if (effect == null) {
                    effect = EntityEffect.getAssetMap().getAsset("poison");
                }
                duration = POISON_DURATION_SEC;
                break;
            default:
                return false;
        }
        if (effect != null && duration > 0) {
            effectController.addEffect(targetRef, effect, duration, OverlapBehavior.EXTEND, store);
            return true;
        }
        return false;
    }

    @Nullable
    public static ItemStack getHeldWeapon(Player player) {
        ItemContainer storage = player.getInventory().getStorage();
        ItemContainer hotbar = player.getInventory().getHotbar();
        int activeSlot = player.getInventory().getActiveSlot(0);
        if (hotbar != null && activeSlot >= 0 && activeSlot < hotbar.getCapacity()) {
            ItemStack stack = hotbar.getItemStack((short) activeSlot);
            if (stack != null && !stack.isEmpty() && RuneManager.isWeaponOrRanged(stack)) {
                return stack;
            }
        }
        if (storage != null && hotbar != null) {
            int hotbarSize = hotbar.getCapacity();
            int invActive = activeSlot + hotbarSize;
            if (invActive >= 0 && invActive < storage.getCapacity()) {
                ItemStack stack = storage.getItemStack((short) invActive);
                if (stack != null && !stack.isEmpty() && RuneManager.isWeaponOrRanged(stack)) {
                    return stack;
                }
            }
        }
        return null;
    }
}
