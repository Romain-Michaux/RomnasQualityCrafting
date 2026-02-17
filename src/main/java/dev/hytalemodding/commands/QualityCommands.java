package dev.hytalemodding.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandRegistry;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import dev.hytalemodding.quality.ItemQuality;
import dev.hytalemodding.quality.QualityAssigner;
import dev.hytalemodding.quality.QualityRegistry;
import dev.hytalemodding.migration.QualityMigration;

import javax.annotation.Nonnull;

/**
 * Admin commands for RomnasQualityCrafting.
 *
 * Commands:
 *   /rqc info       — Show quality info of the held item
 *   /rqc stats      — Show registration and migration statistics
 */
public final class QualityCommands {

    private final QualityRegistry registry;
    private final QualityMigration migration;

    public QualityCommands(@Nonnull QualityRegistry registry, @Nonnull QualityMigration migration) {
        this.registry = registry;
        this.migration = migration;
    }

    /**
     * Registers all commands with the command registry.
     */
    public void registerCommands(@Nonnull CommandRegistry commandRegistry) {
        RqcCommand root = new RqcCommand();
        root.addSubCommand(new InfoCommand());
        root.addSubCommand(new StatsCommand());
        root.addSubCommand(new MigrateCommand());
        commandRegistry.registerCommand(root);
    }

    // ── /rqc (root command) ──

    private class RqcCommand extends AbstractCommandCollection {
        RqcCommand() {
            super("rqc", "RomnasQualityCrafting commands");
        }
    }

    // ── /rqc info ──

    private class InfoCommand extends CommandBase {
        InfoCommand() {
            super("info", "Show quality of held item");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            if (!ctx.isPlayer()) {
                ctx.sendMessage(Message.raw("[RQC] This command can only be used by a player.").color("#ff5555"));
                return;
            }

            try {
                Player player = ctx.senderAs(Player.class);
                ItemStack heldItem = getHeldItem(player);
                if (heldItem == null || heldItem.isEmpty()) {
                    ctx.sendMessage(Message.raw("[RQC] You are not holding any item.").color("#ffaa00"));
                    return;
                }

                String itemId = heldItem.getItemId();
                String baseId = QualityAssigner.getBaseItemId(heldItem);
                ItemQuality quality = QualityAssigner.getQualityFromItem(heldItem);

                ctx.sendMessage(Message.raw("[RQC] Item Info:").color("#55ff55"));
                ctx.sendMessage(Message.raw("  Item ID: " + itemId).color("#aaaaaa"));
                if (!baseId.equals(itemId)) {
                    ctx.sendMessage(Message.raw("  Base ID: " + baseId).color("#aaaaaa"));
                }

                if (quality != null) {
                    ctx.sendMessage(Message.raw("  Quality: " + quality.getDisplayName() + " (" + quality.name() + ")").color("#aaaaaa"));
                    ctx.sendMessage(Message.raw("  Damage multiplier: " + quality.getDamageMultiplier(null) + "x").color("#aaaaaa"));
                    ctx.sendMessage(Message.raw("  Armor multiplier: " + quality.getArmorMultiplier(null) + "x").color("#aaaaaa"));
                    ctx.sendMessage(Message.raw("  Durability multiplier: " + quality.getDurabilityMultiplier(null) + "x").color("#aaaaaa"));

                    boolean fromMeta = QualityAssigner.hasQualityMetadata(heldItem);
                    ctx.sendMessage(Message.raw("  Source: " + (fromMeta ? "metadata (v2.0)" : "ID suffix (v1.x legacy)")).color("#aaaaaa"));
                } else {
                    ctx.sendMessage(Message.raw("  Quality: None").color("#aaaaaa"));

                    boolean eligible = registry.isEligible(baseId);
                    ctx.sendMessage(Message.raw("  Quality eligible: " + (eligible ? "Yes" : "No")).color("#aaaaaa"));
                }

                ctx.sendMessage(Message.raw("  Durability: " + heldItem.getDurability() + " / " + heldItem.getMaxDurability()).color("#aaaaaa"));
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("[RQC] Error reading item: " + e.getMessage()).color("#ff5555"));
            }
        }
    }

    // ── /rqc stats ──

    private class StatsCommand extends CommandBase {
        StatsCommand() {
            super("stats", "Show quality system statistics");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            ctx.sendMessage(Message.raw("[RQC] Quality System Statistics:").color("#55ff55"));
            ctx.sendMessage(Message.raw("  Items scanned: " + registry.getTotalScanned()).color("#aaaaaa"));
            ctx.sendMessage(Message.raw("  Eligible items: " + registry.getTotalEligible()).color("#aaaaaa"));
            ctx.sendMessage(Message.raw("  Mode: quality variants (per-stack visuals)").color("#aaaaaa"));

            ctx.sendMessage(Message.raw("  Migration — migrated: " + migration.getTotalMigrated()
                    + ", reverted: " + migration.getTotalReverted()).color("#aaaaaa"));

            if (ctx.isPlayer()) {
                try {
                    Player player = ctx.senderAs(Player.class);
                    ItemStack held = getHeldItem(player);
                    if (held != null && !held.isEmpty()) {
                        String itemId = held.getItemId();
                        String baseId = QualityAssigner.getBaseItemId(held);
                        ItemQuality quality = QualityAssigner.getQualityFromItem(held);
                        String qualityStr = (quality != null)
                                ? quality.getDisplayName() + " (" + quality.name() + ")"
                                : "None";
                        boolean eligible = registry.isEligible(baseId);

                        ctx.sendMessage(Message.raw("  ── Held Item ──").color("#55ff55"));
                        ctx.sendMessage(Message.raw("    ID: " + itemId).color("#aaaaaa"));
                        if (!baseId.equals(itemId)) {
                            ctx.sendMessage(Message.raw("    Base ID: " + baseId).color("#aaaaaa"));
                        }
                        ctx.sendMessage(Message.raw("    Quality: " + qualityStr).color("#aaaaaa"));
                        ctx.sendMessage(Message.raw("    Eligible: " + (eligible ? "Yes" : "No")).color("#aaaaaa"));
                        ctx.sendMessage(Message.raw("    Durability: " + held.getDurability()
                                + " / " + held.getMaxDurability()).color("#aaaaaa"));
                    } else {
                        ctx.sendMessage(Message.raw("  ── Held Item: none ──").color("#aaaaaa"));
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    // ── /rqc migrate ──

    private class MigrateCommand extends CommandBase {
        MigrateCommand() {
            super("migrate", "Force re-run v1.x migration on your inventory");
        }

        @Override
        protected void executeSync(@Nonnull CommandContext ctx) {
            if (!ctx.isPlayer()) {
                ctx.sendMessage(Message.raw("[RQC] This command can only be used by a player.").color("#ff5555"));
                return;
            }

            try {
                Player player = ctx.senderAs(Player.class);
                migration.forceMigrate(player);
                ctx.sendMessage(Message.raw("[RQC] Migration re-run complete. Check server logs for details.").color("#55ff55"));
            } catch (Exception e) {
                ctx.sendMessage(Message.raw("[RQC] Error: " + e.getMessage()).color("#ff5555"));
            }
        }
    }

    // ── Helpers ──

    private static ItemStack getHeldItem(@Nonnull Player player) {
        try {
            com.hypixel.hytale.server.core.inventory.Inventory inv = player.getInventory();
            if (inv != null) {
                return inv.getItemInHand();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
