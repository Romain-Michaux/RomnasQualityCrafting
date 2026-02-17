package dev.hytalemodding.quality;

import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.LivingEntity;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import dev.hytalemodding.config.QualityConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;

/**
 * ECS damage event system that applies quality-based stat modifiers at runtime.
 *
 * When a Damage event fires on an entity:
 * 1. Check the attacker's held weapon for quality metadata → multiply damage
 * 2. Check the target's armor for quality metadata → reduce damage
 *
 * Quality is stored as BsonDocument metadata on ItemStack (key: "rqc_quality").
 * This system reads that metadata and adjusts the Damage amount accordingly.
 *
 * Entity lookup strategy:
 *   - Target: we already have the ArchetypeChunk + entityIndex from the handle() call,
 *     so we scan the chunk's protected {@code components[][]} array for a LivingEntity.
 *   - Attacker: we get a {@code Ref<EntityStore>} from {@code Damage.EntitySource},
 *     then use {@code Store.forEachChunk()} to find the chunk containing that Ref
 *     and extract the LivingEntity the same way.
 */
public final class QualityDamageSystem extends DamageEventSystem {

    private static final String LOG_PREFIX = "[RQC] DamageSystem: ";

    private final QualityConfig config;

    /** Cached reflective access to ArchetypeChunk.components (protected field). */
    private static volatile Field componentsField;

    public QualityDamageSystem(@Nonnull QualityConfig config) {
        this.config = config;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    private static int damageLogCount = 0;

    @Override
    public void handle(int entityIndex,
                       ArchetypeChunk<EntityStore> chunk,
                       Store<EntityStore> store,
                       CommandBuffer<EntityStore> commandBuffer,
                       Damage damage) {

        // ALL quality stat multipliers are now baked into variant Item assets at startup
        // by QualityTierMapper, including weapon damage (via cloned interaction chains
        // with scaled DamageCalculator.baseDamage values).
        //
        // This system is kept as a no-op placeholder in case we need runtime scaling
        // in the future (e.g., for buffs or temporary effects).
    }

    // ───────────────────────────── Attacker (weapon) ─────────────────────────

    private float getAttackerDamageMultiplier(Damage damage, Store<EntityStore> store) {
        try {
            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource entitySource)) return 1.0f;

            Ref<EntityStore> attackerRef = entitySource.getRef();
            if (attackerRef == null || !attackerRef.isValid()) return 1.0f;

            LivingEntity attacker = findLivingEntityByRef(attackerRef, store);
            if (attacker == null) return 1.0f;

            Inventory inv = attacker.getInventory();
            if (inv == null) return 1.0f;

            ItemStack heldItem = inv.getItemInHand();
            if (heldItem == null || heldItem.isEmpty()) return 1.0f;

            ItemQuality quality = QualityAssigner.getQualityFromItem(heldItem);
            if (quality == null) return 1.0f;

            return quality.getDamageMultiplier(config);
        } catch (Exception e) {
            return 1.0f;
        }
    }

    // ───────────────────── Entity lookup from ECS data ──────────────────────

    /**
     * Finds a LivingEntity component directly from the chunk's components array.
     * Used for the TARGET entity where we already know chunk + entityIndex.
     *
     * ArchetypeChunk stores components as {@code Component[columnCount][rowCount]}.
     * Each column corresponds to a ComponentType; each row to an entity index.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static LivingEntity findLivingEntityInChunk(ArchetypeChunk<EntityStore> chunk, int entityIndex) {
        try {
            Component<EntityStore>[][] comps = getComponentsArray(chunk);
            if (comps == null) return null;

            for (Component<EntityStore>[] column : comps) {
                if (column != null && entityIndex < column.length) {
                    Component<EntityStore> comp = column[entityIndex];
                    if (comp instanceof LivingEntity le) {
                        return le;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Finds a LivingEntity by its Ref, scanning the Store's chunks.
     * Used for the ATTACKER entity where we only have a Ref from Damage.Source.
     *
     * Strategy: iterate all chunks via Store.forEachChunk(), find the one containing
     * our Ref, then extract the LivingEntity from that chunk.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    private static LivingEntity findLivingEntityByRef(Ref<EntityStore> ref, Store<EntityStore> store) {
        try {
            // Use store.getArchetype(ref) to verify the ref is valid
            Archetype<EntityStore> archetype = store.getArchetype(ref);
            if (archetype == null) return null;

            // Ref.getIndex() is the index within its ArchetypeChunk
            int refIndex = ref.getIndex();

            // Use forEachChunk to find the chunk containing our ref
            final LivingEntity[] result = {null};

            store.forEachChunk((ArchetypeChunk<EntityStore> chunk, CommandBuffer<EntityStore> cb) -> {
                if (result[0] != null) return; // Already found

                // Check if this chunk's archetype matches
                if (!chunk.getArchetype().equals(archetype)) return;

                // Scan refs in this chunk to find our entity
                for (int i = 0; i < chunk.size(); i++) {
                    Ref<EntityStore> chunkRef = chunk.getReferenceTo(i);
                    if (chunkRef != null && chunkRef.equals(ref)) {
                        result[0] = findLivingEntityInChunk(chunk, i);
                        return;
                    }
                }
            });

            return result[0];
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Gets the internal {@code Component[][]} array from an ArchetypeChunk via
     * cached reflective field access. The field is {@code protected}, so we need
     * setAccessible(true) once.
     */
    @SuppressWarnings("unchecked")
    private static Component<EntityStore>[][] getComponentsArray(ArchetypeChunk<EntityStore> chunk) {
        try {
            Field f = componentsField;
            if (f == null) {
                f = ArchetypeChunk.class.getDeclaredField("components");
                f.setAccessible(true);
                componentsField = f;
            }
            return (Component<EntityStore>[][]) f.get(chunk);
        } catch (Exception e) {
            return null;
        }
    }
}
