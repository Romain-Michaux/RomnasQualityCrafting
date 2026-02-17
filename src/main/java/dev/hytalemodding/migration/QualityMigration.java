package dev.hytalemodding.migration;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import dev.hytalemodding.quality.ItemQuality;
import dev.hytalemodding.quality.QualityAssigner;
import dev.hytalemodding.quality.QualityRegistry;
import dev.hytalemodding.quality.QualityTierMapper;
import org.bson.BsonDocument;
import org.bson.BsonString;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Handles migration from v1.x quality items to v2.0 format.
 *
 * v1.x stored quality by changing the item ID (e.g. "Weapon_Sword_Copper_Legendary").
 * v2.0 uses quality variant items with correct qualityIndex for client visuals.
 *
 * On player join, scans inventory for v1.x items and:
 * 1. Extracts quality tier from item ID suffix
 * 2. Swaps to quality variant ID (e.g. "Weapon_Sword_Copper__rqc_Legendary")
 * 3. Stores quality as metadata ("rqc_quality" = "LEGENDARY")
 * 4. Stores base ID as metadata ("rqc_base_id" = "Weapon_Sword_Copper")
 * 5. Preserves durability ratio
 *
 * Also upgrades v2.0-metadata-only items (from before variant system) to variants.
 */
public final class QualityMigration {

    private static final String LOG_PREFIX = "[RQC] Migration: ";

    private final Set<UUID> migratedPlayers = new HashSet<>();

    private final QualityRegistry registry;
    private final QualityTierMapper tierMapper;

    private int totalMigrated = 0;
    private int totalReverted = 0;

    public QualityMigration(@Nonnull QualityRegistry registry, @Nonnull QualityTierMapper tierMapper) {
        this.registry = registry;
        this.tierMapper = tierMapper;
    }

    /**
     * Registers the migration handler on player join.
     */
    public void registerEvents(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.registerGlobal(PlayerReadyEvent.class, this::onPlayerReady);
    }

    @SuppressWarnings("removal")
    private void onPlayerReady(@Nonnull PlayerReadyEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;

        UUID uuid = player.getUuid();
        if (migratedPlayers.contains(uuid)) return;

        System.out.println(LOG_PREFIX + "Scanning player " + player.getDisplayName() + " for v1.x items...");

        int migrated = migratePlayer(player);
        migratedPlayers.add(uuid);

        if (migrated > 0) {
            totalMigrated += migrated;

            try {
                player.sendMessage(Message.raw("[RQC] Migrated " + migrated
                        + " quality item(s) from v1.x to v2.0 metadata format.").color("#55ff55"));
            } catch (Exception ignored) {
            }
        }

        System.out.println(LOG_PREFIX + "Player " + player.getDisplayName()
                + ": migrated=" + migrated + " items to metadata format");
    }

    /**
     * Scans all of a player's inventory sections and migrates v1.x quality items.
     * v1.x items have quality in their ID suffix → revert to base ID + add metadata.
     *
     * @return number of items migrated
     */
    private int migratePlayer(@Nonnull Player player) {
        int migrated = 0;

        try {
            Inventory inventory = player.getInventory();
            if (inventory == null) {
                System.out.println(LOG_PREFIX + "  Player inventory is null!");
                return 0;
            }

            // Migrate all inventory sections
            migrated += migrateContainer("hotbar", inventory.getHotbar());
            migrated += migrateContainer("storage", inventory.getStorage());
            migrated += migrateContainer("armor", inventory.getArmor());
            migrated += migrateContainer("utility", inventory.getUtility());
            migrated += migrateContainer("tools", inventory.getTools());

            try {
                migrated += migrateContainer("backpack", inventory.getBackpack());
            } catch (Exception ignored) {
                // Backpack may not exist
            }
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "Error migrating player: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        return migrated;
    }

    /**
     * Migrates v1.x quality items and upgrades v2.0 metadata-only items to variants.
     */
    private int migrateContainer(String sectionName, ItemContainer container) {
        if (container == null) return 0;

        int migrated = 0;
        short capacity = container.getCapacity();

        for (short slot = 0; slot < capacity; slot++) {
            try {
                ItemStack item = container.getItemStack(slot);
                if (item == null || item.isEmpty()) continue;

                String itemId = item.getItemId();
                if (itemId == null) continue;

                boolean isValid = true;
                try { isValid = item.isValid(); } catch (Exception ignored) {}
                boolean hasMeta = QualityAssigner.hasQualityMetadata(item);
                boolean isVariant = tierMapper.isVariant(itemId);
                System.out.println(LOG_PREFIX + "  [" + sectionName + "] slot " + slot
                        + ": id=" + itemId + " qty=" + item.getQuantity()
                        + " valid=" + isValid + " hasMeta=" + hasMeta
                        + " isVariant=" + isVariant);

                // Skip items that are already quality variants with metadata
                if (isVariant && hasMeta) {
                    continue;
                }

                // ── Case 1: v1.x suffixed ID → migrate to variant ──
                ItemQuality quality = ItemQuality.fromItemId(itemId);
                if (quality != null) {
                    String baseId = ItemQuality.extractBaseId(itemId);
                    String targetId = tierMapper.isInitialized()
                            ? tierMapper.getVariantId(baseId, quality) : baseId;
                    System.out.println(LOG_PREFIX + "    -> v1.x suffixed ID! quality=" + quality.name()
                            + " baseId=" + baseId + " targetId=" + targetId + " — migrating...");

                    ItemStack migratedItem = new ItemStack(targetId, item.getQuantity());

                    BsonDocument metadata = item.getMetadata();
                    BsonDocument newMetadata = (metadata != null) ? metadata.clone() : new BsonDocument();
                    newMetadata.put(QualityAssigner.METADATA_KEY, new BsonString(quality.name()));
                    newMetadata.put(QualityAssigner.BASE_ID_KEY, new BsonString(baseId));
                    newMetadata.put("rqc_migrated", new BsonString("v1"));
                    migratedItem = migratedItem.withMetadata(newMetadata);

                    migratedItem = preserveDurability(item, migratedItem);

                    container.setItemStackForSlot(slot, migratedItem);
                    migrated++;
                    totalReverted++;
                    System.out.println(LOG_PREFIX + "    -> migrated v1.x → variant!");
                    continue;
                }

                // ── Case 2: v2.0 metadata-only (no variant) → upgrade to variant ──
                if (hasMeta && !isVariant && tierMapper.isInitialized()) {
                    ItemQuality metaQuality = QualityAssigner.getQualityFromMetadata(item);
                    if (metaQuality != null && metaQuality != ItemQuality.COMMON) {
                        String baseId = QualityAssigner.getBaseItemId(item);
                        String targetId = tierMapper.getVariantId(baseId, metaQuality);
                        System.out.println(LOG_PREFIX + "    -> upgrading metadata-only → variant: "
                                + itemId + " → " + targetId);

                        ItemStack upgraded = new ItemStack(targetId, item.getQuantity());
                        BsonDocument metadata = item.getMetadata();
                        BsonDocument newMetadata = (metadata != null) ? metadata.clone() : new BsonDocument();
                        newMetadata.put(QualityAssigner.BASE_ID_KEY, new BsonString(baseId));
                        upgraded = upgraded.withMetadata(newMetadata);

                        upgraded = preserveDurability(item, upgraded);

                        container.setItemStackForSlot(slot, upgraded);
                        migrated++;
                        System.out.println(LOG_PREFIX + "    -> upgraded to variant!");
                        continue;
                    }
                }
            } catch (Exception e) {
                System.out.println(LOG_PREFIX + "  [" + sectionName + "] slot " + slot
                        + " error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }

        return migrated;
    }

    /**
     * Preserves the durability ratio when swapping items.
     */
    private ItemStack preserveDurability(@Nonnull ItemStack source, @Nonnull ItemStack target) {
        try {
            double srcDurability = source.getDurability();
            double srcMax = source.getMaxDurability();
            double targetMax = target.getMaxDurability();

            if (srcMax > 0 && srcDurability > 0 && targetMax > 0) {
                double ratio = srcDurability / srcMax;
                return target.withMaxDurability(targetMax).withDurability(targetMax * ratio);
            } else if (targetMax > 0) {
                return target.withMaxDurability(targetMax).withDurability(targetMax);
            }
        } catch (Exception ignored) {
        }
        return target;
    }

    // ── Stats ──

    public int getTotalMigrated() { return totalMigrated; }
    public int getTotalReverted() { return totalReverted; }

    /**
     * Force-runs migration on a player's inventory (can be triggered by /rqc migrate).
     * Bypasses the "already migrated this session" check.
     */
    @SuppressWarnings("removal")
    public void forceMigrate(@Nonnull Player player) {
        System.out.println(LOG_PREFIX + "Force migration triggered for " + player.getDisplayName());
        int migrated = migratePlayer(player);
        totalMigrated += migrated;

        try {
            if (migrated > 0) {
                player.sendMessage(Message.raw("[RQC] Force migration: " + migrated + " item(s) converted.").color("#55ff55"));
            } else {
                player.sendMessage(Message.raw("[RQC] Force migration: no v1.x items found to migrate.").color("#ffaa00"));
            }
        } catch (Exception ignored) {
        }

        System.out.println(LOG_PREFIX + "Force migration done: " + migrated + " items migrated");
    }
}
