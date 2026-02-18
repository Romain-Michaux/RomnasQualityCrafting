package dev.hytalemodding;

import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import dev.hytalemodding.config.QualityConfig;
import dev.hytalemodding.migration.QualityMigration;
import dev.hytalemodding.quality.CraftQualitySystem;
import dev.hytalemodding.quality.LootDropModifier;
import dev.hytalemodding.quality.QualityAssigner;
import dev.hytalemodding.quality.QualityItemFactory;
import dev.hytalemodding.quality.QualityRegistry;
import dev.hytalemodding.quality.QualityTierMapper;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * RomnasQualityCrafting v2.0 — Zero-setup quality system for Hytale.
 *
 * Adds RPG-style quality tiers (Poor → Legendary) to weapons, armor, and tools.
 * Quality is assigned randomly on craft/loot with configurable weights.
 *
 * Architecture (v2.0 — quality variants):
 *   - Quality determined entirely by item ID (variant naming convention)
 *   - Item ID swapped to a quality variant for correct client visuals (colors, tooltips)
 *   - Variant items cloned from base items with correct Hytale qualityIndex
 *   - All stat multipliers (damage, armor, tools, durability) baked into variants
 *   - Durability multiplier applied on item acquisition
 *   - No metadata (BsonDocument) used — keeps items salvageable
 *   - Migration from v1.x (ID suffix) to variant system
 *
 * Just install the JAR and go.
 */
public class RomnasQualityCrafting extends JavaPlugin {

    private static final String LOG_PREFIX = "[RQC] ";

    private final Object configHandle;
    private QualityConfig config;
    private QualityRegistry registry;
    private QualityAssigner assigner;
    private CraftQualitySystem craftSystem;
    private QualityTierMapper tierMapper;
    private QualityMigration migration;
    private LootDropModifier lootDropModifier;

    public RomnasQualityCrafting(@Nonnull JavaPluginInit init) {
        super(init);
        // Load config via Hytale's CODEC system (must be in constructor)
        configHandle = this.withConfig("RomnasQualityCrafting", QualityConfig.CODEC);
    }

    @Override
    protected void setup() {
        long startTime = System.currentTimeMillis();

        // ── 0. Clean up old v1.x generated files (RQCGeneratedFiles folder) ──
        // Must run BEFORE assets are loaded so the engine doesn't pick up stale JSONs.
        cleanupOldGeneratedFiles();

        // ── 1. Load configuration ──
        config = loadConfig();
        System.out.println(LOG_PREFIX + "Configuration loaded.");

        // ── 2. Save config to disk (creates file if missing) ──
        saveConfig();

        // ── 3. Create registry (will scan items when assets are loaded) ──
        registry = new QualityRegistry();

        // ── 4. Create tier mapper (maps our quality tiers to Hytale's built-in tiers) ──
        tierMapper = new QualityTierMapper();

        // ── 5. Set up quality assignment via inventory change events ──
        assigner = new QualityAssigner(registry, config, tierMapper);
        assigner.registerEvents(this.getEventRegistry());

        // ── 5b. Set up ECS crafting handler (CraftRecipeEvent.Post) ──
        craftSystem = new CraftQualitySystem(registry, config, tierMapper);
        this.getEntityStoreRegistry().registerSystem(craftSystem);
        System.out.println(LOG_PREFIX + "Quality assignment events registered (inventory change + ECS craft).");

        // ── 6. Set up v1.x → v2.0 migration on player join ──
        migration = new QualityMigration(registry, tierMapper);
        migration.registerEvents(this.getEventRegistry());
        System.out.println(LOG_PREFIX + "Migration handler registered.");

        // ── 7. Defer item scanning until assets are fully loaded ──
        this.getEventRegistry().register(
            LoadAssetEvent.PRIORITY_LOAD_LATE,
            LoadAssetEvent.class,
            this::onAssetsLoaded
        );
        System.out.println(LOG_PREFIX + "Waiting for assets to load before scanning items...");

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println(LOG_PREFIX + "=== RomnasQualityCrafting v2.0 setup() done in " + elapsed + "ms ===");
    }

    /**
     * Called when all game assets (items, blocks, etc.) are fully loaded into memory.
     * Scans the Item asset map to identify which items are eligible for quality.
     * No items are injected — we only build a lookup table of eligible IDs.
     */
    private void onAssetsLoaded(LoadAssetEvent event) {
        System.out.println(LOG_PREFIX + "Assets loaded — scanning eligible items...");
        long startTime = System.currentTimeMillis();

        // Initialize the ignore list before scanning
        QualityItemFactory.initIgnoreList(config);

        registry.scanEligibleItems();

        System.out.println(LOG_PREFIX + "  Eligible items: " + registry.getTotalEligible()
                + " (of " + registry.getTotalScanned() + " scanned)");

        // Initialize quality tier mapping (discover Hytale's built-in quality tiers)
        tierMapper.initialize();

        // Create quality variant items in the asset map
        tierMapper.createVariants(registry, config);

        // Modify loot drop tables so eligible items drop as quality variants
        lootDropModifier = new LootDropModifier(config, tierMapper, registry);
        lootDropModifier.modifyDropLists();

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println(LOG_PREFIX + "=== Item scan + variant creation complete in " + elapsed + "ms ===");
        System.out.println(LOG_PREFIX + "  Quality variants created: " + tierMapper.getVariantsCreated());
        System.out.println(LOG_PREFIX + "  Loot drop lists modified: " + lootDropModifier.getDropListsModified()
                + " (" + lootDropModifier.getDropsReplaced() + " drops replaced)");
    }

    @Override
    public void shutdown() {
        System.out.println(LOG_PREFIX + "Shutting down. Migration stats: "
                + migration.getTotalMigrated() + " migrated, "
                + migration.getTotalReverted() + " reverted.");
    }

    // ── Config helpers ──

    private QualityConfig loadConfig() {
        try {
            Method getMethod = configHandle.getClass().getMethod("get");
            QualityConfig loaded = (QualityConfig) getMethod.invoke(configHandle);
            if (loaded != null) return loaded;
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "Warning: Could not load config, using defaults: " + e.getMessage());
        }
        return new QualityConfig();
    }

    private void saveConfig() {
        try {
            Method saveMethod = configHandle.getClass().getMethod("save");
            saveMethod.invoke(configHandle);
        } catch (Exception ignored) {
            // Config save not available — that's fine
        }
    }

    /** Exposes the config for other components. */
    public QualityConfig getQualityConfig() {
        return config;
    }

    /** Exposes the registry for other components. */
    public QualityRegistry getQualityRegistry() {
        return registry;
    }

    // ── Old v1.x generated files cleanup ──

    /** Name of the old generated mod folder from v1.x. */
    private static final String OLD_GENERATED_FOLDER = "RQCGeneratedFiles";

    /**
     * Deletes the old RQCGeneratedFiles folder left over from v1.x.
     *
     * In v1.x, quality variant items were generated as JSON files on disk inside
     * a fake mod folder ({saveDir}/mods/RQCGeneratedFiles/). In v2.0, variants
     * are created in memory at startup — the old JSON files are no longer needed
     * and would conflict with the new system (duplicate IDs, stale stats).
     *
     * This method finds the RQCGeneratedFiles folder by going up from our plugin's
     * data directory to the mods/ folder, then recursively deletes it.
     * It runs early in setup() BEFORE assets are loaded.
     */
    private void cleanupOldGeneratedFiles() {
        try {
            Path dataDir = this.getDataDirectory();
            if (dataDir == null) {
                System.out.println(LOG_PREFIX + "Could not resolve data directory for cleanup.");
                return;
            }

            // dataDir = {saveDir}/mods/{pluginId}/ → parent = {saveDir}/mods/
            Path modsDir = dataDir.getParent();
            if (modsDir == null) return;

            Path oldGenerated = modsDir.resolve(OLD_GENERATED_FOLDER);
            if (!Files.exists(oldGenerated) || !Files.isDirectory(oldGenerated)) {
                // No old generated folder — nothing to clean up
                return;
            }

            System.out.println(LOG_PREFIX + "Found old v1.x generated folder: " + oldGenerated);
            System.out.println(LOG_PREFIX + "Deleting to prevent conflicts with v2.0 in-memory variants...");

            // Count files for logging
            long[] count = {0};
            Files.walkFileTree(oldGenerated, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    count[0]++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });

            System.out.println(LOG_PREFIX + "Deleted old RQCGeneratedFiles folder (" + count[0] + " files removed).");
            System.out.println(LOG_PREFIX + "v2.0 now generates quality variants in memory — no disk files needed.");

        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "Warning: Failed to clean up old generated files: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            System.out.println(LOG_PREFIX + "You can manually delete the 'RQCGeneratedFiles' folder from your save's mods directory.");
        }
    }
}
