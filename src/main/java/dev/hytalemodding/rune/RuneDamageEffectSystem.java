package dev.hytalemodding.rune;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Applique les effets des runes Burn et Poison après que les dégâts soient appliqués.
 * Inspiré du mod SimpleEnchantments (EnchantmentBurnSystem).
 */
public class RuneDamageEffectSystem extends DamageEventSystem {

    @Nonnull
    @Override
    public com.hypixel.hytale.component.query.Query<EntityStore> getQuery() {
        return com.hypixel.hytale.component.Archetype.empty();
    }

    @Nonnull
    @Override
    public Set<com.hypixel.hytale.component.dependency.Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency(Order.AFTER, DamageSystems.ApplyDamage.class));
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {

        if (damage.getAmount() <= 0 || damage.isCancelled()) {
            return;
        }

        Damage.Source source = damage.getSource();
        Ref<EntityStore> attackerRef = null;

        if (source instanceof Damage.EntitySource entitySource) {
            attackerRef = entitySource.getRef();
        } else if (source instanceof Damage.ProjectileSource projectileSource) {
            attackerRef = projectileSource.getRef();
        }

        if (attackerRef == null || !attackerRef.isValid()) {
            return;
        }

        Player player = store.getComponent(attackerRef, Player.getComponentType());
        if (player == null) {
            return;
        }

        var inv = player.getInventory();
        if (inv == null) {
            return;
        }

        ItemStack weapon = inv.getItemInHand();
        if (weapon == null || weapon.isEmpty()) {
            return;
        }

        String rune = RuneManager.getAppliedRune(weapon);
        if (rune == null) {
            return;
        }

        Ref<EntityStore> targetRef = chunk.getReferenceTo(index);
        if (targetRef == null || !targetRef.isValid()) {
            return;
        }

        EffectControllerComponent effectController = store.getComponent(targetRef, EffectControllerComponent.getComponentType());
        if (effectController == null) {
            return;
        }

        String effectId = null;
        switch (rune) {
            case RuneManager.RUNE_BURN:
                effectId = "Burn";
                break;
            case RuneManager.RUNE_POISON:
                effectId = "Poison";
                break;
            default:
                return;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId);
        if (effect == null) {
            effect = EntityEffect.getAssetMap().getAsset(effectId.toLowerCase());
        }
        if (effect != null) {
            effectController.addEffect(targetRef, effect, commandBuffer);
        }
    }
}
