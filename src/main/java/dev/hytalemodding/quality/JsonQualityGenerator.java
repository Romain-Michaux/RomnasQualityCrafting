package dev.hytalemodding.quality;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.JsonSerializer;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonElement;
import java.lang.reflect.Type;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Génère les fichiers JSON pour les variantes de qualité dans le dossier mods de la sauvegarde.
 * Les fichiers sont créés dans : {saveDir}/mods/RQCGeneratedFiles/Server/Item/Items/
 */
public final class JsonQualityGenerator {
    
    private static final String LOG_PREFIX = "[RomnasQualityCrafting] JsonGenerator: ";
    private static final String GENERATED_MOD_NAME = "RomnasQualityCraftingGenerated";
    
    // Map to store the source path of each item found in mods
    private static final Map<String, Path> modItemSourcePaths = new java.util.HashMap<>();
    
    // Map to store RootInteraction references found in items
    private static final java.util.Set<String> requiredRootInteractions = new java.util.HashSet<>();
    
    // Sets to store ParticleSystem, Trail, ModelVFX and Interactions references found in items
    private static final java.util.Set<String> requiredParticleSystems = new java.util.HashSet<>();
    private static final java.util.Set<String> requiredParticleSpawners = new java.util.HashSet<>();
    private static final java.util.Set<String> requiredTrails = new java.util.HashSet<>();
    private static final java.util.Set<String> requiredModelVFX = new java.util.HashSet<>();
    private static final java.util.Set<String> requiredInteractions = new java.util.HashSet<>();
    
    // Configuration for detailed logs (loaded from config file)
    private static boolean verboseLogging = false; // Enabled by default for debugging
    
    // Store mods folder path to be able to find HytaleAssets
    private static Path cachedModsDir = null;
    
    // Store custom assets path from config (if provided)
    private static String customAssetsPath = null;
    
    // Store custom global mods path from config (if provided)
    private static String customGlobalModsPath = null;
    
    // Track attempted paths for logging
    private static final java.util.List<String> attemptedAssetPaths = new java.util.ArrayList<>();
    
    // Track if asset detection failed
    private static boolean assetDetectionFailed = false;
    
    // Cache for Assets.zip path to avoid repeated detection
    private static Path cachedAssetsZipPath = null;
    private static boolean assetsPathInitialized = false;
    
    // Cache for external mods to avoid repeated scans
    private static Map<String, Item> cachedModItems = null;
    private static boolean modScanCompleted = false;
    
    /**
     * Result of JSON generation with detailed status information.
     */
    public static class GenerationResult {
        public final boolean success;
        public final boolean failed;
        public final String failureReason;
        public final int generatedCount;
        public final int errorCount;
        
        public GenerationResult(boolean success, boolean failed, String failureReason, int generatedCount, int errorCount) {
            this.success = success;
            this.failed = failed;
            this.failureReason = failureReason;
            this.generatedCount = generatedCount;
            this.errorCount = errorCount;
        }
        
        public static GenerationResult success(int generatedCount, int errorCount) {
            return new GenerationResult(true, false, null, generatedCount, errorCount);
        }
        
        public static GenerationResult failure(String reason) {
            return new GenerationResult(false, true, reason, 0, 0);
        }
        
        public static GenerationResult skipped() {
            return new GenerationResult(false, false, null, 0, 0);
        }
    }
    
    /**
     * Initializes logging configuration from the config object.
     * @param plugin The plugin instance
     * @param configData The configuration data object
     */
    public static void initializeLogging(@Nonnull JavaPlugin plugin, @Nonnull dev.hytalemodding.config.RomnasQualityCraftingConfig configData) {
        // Note: VerboseLogging has been removed from config, keeping this method for compatibility
        // but it no longer does anything since we only have quality weights now
        
        // Load custom assets path if provided
        if (configData != null) {
            String configPath = configData.getCustomAssetsPath();
            if (configPath != null && !configPath.trim().isEmpty()) {
                customAssetsPath = configPath.trim();
                logEssential("Custom assets path loaded from config: " + customAssetsPath);
            }
            
            // Load custom global mods path if provided
            String modsPath = configData.getCustomGlobalModsPath();
            if (modsPath != null && !modsPath.trim().isEmpty()) {
                customGlobalModsPath = modsPath.trim();
                logEssential("Custom global mods path loaded from config: " + customGlobalModsPath);
            }
        }
    }
    
    /**
     * Log un message essentiel (toujours affiché).
     */
    private static void logEssential(@Nonnull String msg) {
        System.out.println(LOG_PREFIX + msg);
    }
    
    /**
     * Log un message détaillé (seulement si verboseLogging est activé).
     */
    private static void logVerbose(@Nonnull String msg) {
        if (verboseLogging) {
            System.out.println(LOG_PREFIX + msg);
        }
    }
    
    /**
     * Log un message (déprécié, utiliser logEssential ou logVerbose).
     * Par défaut, utilise logVerbose pour maintenir la compatibilité.
     */
    private static void log(@Nonnull String msg) {
        logVerbose(msg);
    }
    
    /**
     * Serializer personnalisé pour les nombres qui convertit les entiers en JSON sans .0
     */
    private static final JsonSerializer<Number> NUMBER_SERIALIZER = new JsonSerializer<Number>() {
        @Override
        public JsonElement serialize(Number src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) {
                return null;
            }
            // Si c'est un nombre entier, le sérialiser comme un entier
            double value = src.doubleValue();
            if (value == (long) value) {
                return new JsonPrimitive((long) value);
            }
            // Sinon, le sérialiser comme un double
            return new JsonPrimitive(value);
        }
    };
    
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .registerTypeAdapter(Number.class, NUMBER_SERIALIZER)
            .setExclusionStrategies(new ExclusionStrategy() {
                @Override
                public boolean shouldSkipField(FieldAttributes f) {
                    // Exclure les champs problématiques qui ne peuvent pas être sérialisés
                    String fieldType = f.getDeclaredClass().getName();
                    String fieldName = f.getName();
                    return fieldType.contains("SoftReference") 
                        || fieldType.contains("WeakReference")
                        || fieldType.contains("Reference")
                        || fieldType.contains("Thread")
                        || fieldType.contains("ClassLoader")
                        || fieldType.contains("Bson")
                        || fieldName.equals("timestamp")
                        || fieldName.equals("clock")
                        || fieldName.equals("ref");
                }
                
                @Override
                public boolean shouldSkipClass(Class<?> clazz) {
                    String className = clazz.getName();
                    return className.contains("SoftReference")
                        || className.contains("WeakReference")
                        || className.contains("Reference")
                        || className.contains("Thread")
                        || className.contains("ClassLoader")
                        || className.contains("Bson")
                        || className.contains("com.hypixel.hytale.codec")
                        || className.contains("com.hypixel.hytale.server.core.asset");
                }
            })
            .disableHtmlEscaping()
            .create();
    
    /**
     * Génère les fichiers JSON pour toutes les variantes de qualité dans le dossier mods de la sauvegarde.
     * @param plugin Le plugin Java pour obtenir le chemin du serveur
     * @param baseItems La map des items de base à traiter
     * @param bootEvent L'événement BootEvent pour obtenir le chemin du monde (peut être null)
     * @return GenerationResult avec le statut et les détails de la génération
     */
    public static GenerationResult generateJsonFiles(@Nonnull JavaPlugin plugin, @Nonnull Map<String, Item> baseItems, @Nullable Object bootEvent) {
        // Initialize logging configuration from plugin's config
        try {
            if (plugin instanceof dev.hytalemodding.RomnasQualityCrafting) {
                dev.hytalemodding.RomnasQualityCrafting rqcPlugin = (dev.hytalemodding.RomnasQualityCrafting) plugin;
                initializeLogging(plugin, rqcPlugin.getConfigData());
            }
        } catch (Exception e) {
            // If config is not available, use default (verboseLogging = false)
            verboseLogging = false;
        }
        
        Path modsDir = getModsDirectory(plugin, bootEvent);
        if (modsDir == null) {
            logEssential("Unable to determine mods directory, skipping JSON generation.");
            return GenerationResult.failure("Unable to determine mods directory");
        }
        
        // Stocker le chemin du dossier mods pour pouvoir trouver HytaleAssets
        cachedModsDir = modsDir;
        
        logEssential("Starting quality variant generation...");
        
        // Scan external mods BEFORE generation to find items and their source files (only if enabled)
        dev.hytalemodding.config.RomnasQualityCraftingConfig configData = null;
        try {
            if (plugin instanceof dev.hytalemodding.RomnasQualityCrafting) {
                configData = ((dev.hytalemodding.RomnasQualityCrafting) plugin).getConfigData();
            }
        } catch (Exception e) {
            // Ignore
        }
        
        boolean externalModsCompatEnabled = configData == null || configData.isExternalModsCompatEnabled();
        if (externalModsCompatEnabled) {
            // Use cached mod items if already scanned
            if (!modScanCompleted) {
                cachedModItems = scanExternalModsForItems(plugin);
                modScanCompleted = true;
            }
            
            if (cachedModItems != null && !cachedModItems.isEmpty()) {
                logEssential("Using " + cachedModItems.size() + " item(s) from external mods (cached).");
                // Add mod items to the base items map
                baseItems.putAll(cachedModItems);
            }
        }
        
        // Find the plugin's mod directory (e.g., RQCGeneratedFiles)
        Path pluginModDir = getPluginModDirectory(plugin, modsDir);
        if (pluginModDir == null) {
            logEssential("Unable to find plugin mod directory in: " + modsDir);
            return GenerationResult.failure("Unable to find plugin mod directory");
        }
        
        Path itemsDir = pluginModDir.resolve("Server").resolve("Item").resolve("Items");
        
        // Check ForceResetAssets flag
        boolean forceResetAssets = configData != null && configData.isForceResetAssets();
        
        // Check if files already exist
        if (Files.exists(itemsDir) && !forceResetAssets) {
            // Check if there are files in the Items directory
            try {
                long fileCount = Files.list(itemsDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .count();
                
                if (fileCount > 0) {
                    logEssential("Generated mod already exists (" + fileCount + " item file(s)). Generation skipped.");
                    return GenerationResult.skipped();
                }
            } catch (IOException e) {
                // Continue with generation on error
            }
        }
        
        try {
            // Create directory if necessary
            Files.createDirectories(itemsDir);
            
            // Always create/update manifest.json to ensure it's valid
            createManifestFile(pluginModDir);
        } catch (IOException e) {
            logEssential("Error creating directory: " + e.getMessage());
            return GenerationResult.failure("Error creating directory: " + e.getMessage());
        }
        
        // Delete all content in the mod directory if ForceResetAssets is enabled
        if (forceResetAssets) {
            try {
                if (Files.exists(pluginModDir)) {
                    // Delete everything except manifest.json and config.json (config file)
                    java.util.List<Path> itemsToDelete = new java.util.ArrayList<>();
                    try (java.util.stream.Stream<Path> stream = Files.walk(pluginModDir)) {
                        stream.forEach(path -> {
                            // Skip the mod directory itself
                            if (path.equals(pluginModDir)) {
                                return;
                            }
                            Path relativePath = pluginModDir.relativize(path);
                            String relativePathStr = relativePath.toString().replace("\\", "/");
                            // Preserve manifest.json and config.json
                            if (relativePathStr.equals("manifest.json") || relativePathStr.endsWith("/manifest.json") ||
                                relativePathStr.equals("config.json") || relativePathStr.endsWith("/config.json")) {
                                return;
                            }
                            itemsToDelete.add(path);
                        });
                    }
                    
                    // Delete in reverse order (files first, then directories)
                    itemsToDelete.sort((a, b) -> {
                        boolean aIsDir = Files.isDirectory(a);
                        boolean bIsDir = Files.isDirectory(b);
                        if (aIsDir && !bIsDir) return 1;
                        if (!aIsDir && bIsDir) return -1;
                        return b.compareTo(a); // Reverse order
                    });
                    
                    int deletedCount = 0;
                    for (Path item : itemsToDelete) {
                        try {
                            if (Files.isDirectory(item)) {
                                deleteDirectoryRecursive(item);
                            } else {
                                Files.delete(item);
                            }
                            deletedCount++;
                        } catch (IOException e) {
                            // Ignore deletion errors
                        }
                    }
                    
                    if (deletedCount > 0) {
                        logEssential("Force reset: Deleted " + deletedCount + " item(s) from mod directory.");
                    }
                }
            } catch (IOException e) {
                // Ignore errors
            }
            
            // Recréer le dossier Items après suppression
            try {
                Files.createDirectories(itemsDir);
                logEssential("Items directory recreated after force reset.");
            } catch (IOException e) {
                logEssential("Error recreating items directory after force reset: " + e.getMessage());
                return GenerationResult.failure("Error recreating items directory after force reset: " + e.getMessage());
            }
        }
        
        logEssential("Generating quality variants for " + baseItems.size() + " base item(s)...");
        int generatedCount = 0;
        int errorCount = 0;
        
        for (Map.Entry<String, Item> entry : baseItems.entrySet()) {
            String baseId = entry.getKey();
            Item baseItem = entry.getValue();
            
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                continue;
            }
            
            for (ItemQuality quality : ItemQuality.values()) {
                String qualityId = DynamicItemAssetCreator.getQualityItemId(baseId, quality);
                Path jsonFile = itemsDir.resolve(qualityId + ".json");
                
                try {
                    Map<String, Object> jsonData = createQualityJson(baseItem, baseId, qualityId, quality);
                    if (jsonData != null && !jsonData.isEmpty()) {
                        // Vérifier que le dossier parent existe avant d'écrire
                        Path parentDir = jsonFile.getParent();
                        if (parentDir != null && !Files.exists(parentDir)) {
                            logEssential("Parent directory doesn't exist, recreating: " + parentDir);
                            Files.createDirectories(parentDir);
                        }
                        writeJsonFile(jsonFile, jsonData);
                        generatedCount++;
                        if (generatedCount % 50 == 0) {
                            logEssential("Progress: " + generatedCount + " files generated...");
                        }
                    } else {
                        errorCount++;
                        logEssential("Failed to create quality JSON for " + baseId + " (" + quality.getDisplayName() + ")");
                    }
                } catch (Exception e) {
                    errorCount++;
                    logEssential("Error generating " + qualityId + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        
        logEssential("Generation completed: " + generatedCount + " JSON file(s) created, " + errorCount + " error(s)");
        
        // If asset detection failed and there are errors, show the warning again
        if (assetDetectionFailed && errorCount > 0) {
            logEssential("");
            logEssential("╔═══════════════════════════════════════════════════════════════════╗");
            logEssential("║                      GENERATION WARNING                           ║");
            logEssential("╚═══════════════════════════════════════════════════════════════════╝");
            logEssential("");
            logEssential("Generation completed with " + errorCount + " error(s).");
            logEssential("Assets.zip was not detected during generation, which likely caused");
            logEssential("these errors. Items may be missing or incomplete.");
            logEssential("");
            logEssential("To fix this issue, please configure the CustomAssetsPath in your");
            logEssential("config file (see instructions above) and restart the server.");
            logEssential("");
            logEssential("═══════════════════════════════════════════════════════════════════");
            logEssential("");
        }
        
        // Copier les dossiers complets depuis les mods sources (only if external mods compat is enabled)
        if (externalModsCompatEnabled) {
            copyAssetDirectoriesFromSourceMods(plugin, pluginModDir);
        }
        
        // Reset ForceResetAssets flag after successful generation
        if (forceResetAssets && configData != null) {
            try {
                configData.setForceResetAssets(false);
                // Save config
                if (plugin instanceof dev.hytalemodding.RomnasQualityCrafting) {
                    try {
                        java.lang.reflect.Method getConfigMethod = plugin.getClass().getMethod("getConfig");
                        Object configObj = getConfigMethod.invoke(plugin);
                        if (configObj != null) {
                            java.lang.reflect.Method saveMethod = configObj.getClass().getMethod("save");
                            saveMethod.invoke(configObj);
                            logEssential("ForceResetAssets flag reset to false.");
                        }
                    } catch (Exception e) {
                        // Ignore if save method doesn't exist
                    }
                }
            } catch (Exception e) {
                // Ignore errors
            }
        }
        
        // Générer les SalvageRecipe pour chaque qualité
        generateSalvageRecipes(plugin, baseItems, pluginModDir);
        
        // Générer les drops modifiés avec les qualités
        generateQualityDrops(plugin, baseItems, pluginModDir);
        
        // Return result based on generation status
        if (assetDetectionFailed && errorCount > generatedCount / 2) {
            // If asset detection failed and we have more errors than half of successes, consider it a failure
            return GenerationResult.failure("Assets.zip not found - items could not be generated properly. See logs for details.");
        } else if (generatedCount > 0) {
            return GenerationResult.success(generatedCount, errorCount);
        } else {
            return GenerationResult.failure("No items were generated");
        }
    }

    /**
     * Crée les données JSON pour une variante de qualité en copiant le JSON original depuis Assets.zip ou un mod et en modifiant les valeurs nécessaires.
     */
    @Nullable
    private static Map<String, Object> createQualityJson(@Nonnull Item baseItem, @Nonnull String baseId, 
                                                         @Nonnull String qualityId, @Nonnull ItemQuality quality) {
        try {
            // Essayer de lire le JSON depuis différentes sources
            Map<String, Object> jsonData = readJsonForItem(baseId);
            
            if (jsonData == null || jsonData.isEmpty()) {
                logEssential("Unable to read JSON for item: " + baseId + " (no JSON data found)");
                return null;
            }
            
            // Note: Ne pas ajouter "Id" car Hytale utilise le nom du fichier comme ID
            // L'ID est déterminé par le nom du fichier (qualityId + ".json")
            
            // Ajouter le champ Quality
            jsonData.put("Quality", quality.getDisplayName());
            
            // Note: Name et Description (TranslationProperties) sont préservés de l'item de base
            // et ne sont pas modifiés pour garder les noms et descriptions originaux
            
            // Modifier MaxDurability si présent
            Object maxDurabilityObj = jsonData.get("MaxDurability");
            if (maxDurabilityObj != null) {
                double baseMaxDurability = maxDurabilityObj instanceof Number 
                    ? ((Number) maxDurabilityObj).doubleValue() 
                    : getMaxDurability(baseItem);
                if (baseMaxDurability > 0) {
                    double newMaxDurability = Math.max(1, baseMaxDurability * quality.getDurabilityMultiplier());
                    jsonData.put("MaxDurability", toJsonNumber(newMaxDurability));
                }
            }
            
            // Modifier les dégâts des armes
            modifyWeaponDamage(jsonData, baseItem, quality.getDamageMultiplier());
            
            // Modifier la puissance des outils (utilise le multiplicateur d'efficacité des outils)
            modifyToolPower(jsonData, baseItem, quality.getToolEfficiencyMultiplier());
            
            // Modifier les stats d'armure (utilise le multiplicateur d'armure)
            modifyArmorStats(jsonData, baseItem, quality.getArmorMultiplier());
            
            removeRecipe(jsonData);
            
            // Collecter les références aux RootInteraction, ParticleSystem, ParticleSpawner, Trail, ModelVFX et Interactions pour les copier plus tard
            collectRootInteractionReferences(jsonData);
            collectParticleSystemReferences(jsonData);
            collectParticleSpawnerReferences(jsonData);
            collectTrailReferences(jsonData);
            collectModelVFXReferences(jsonData);
            collectInteractionReferences(jsonData);

            // Normaliser tous les nombres dans le JSON (convertir les doubles en entiers si approprié)
            normalizeJsonNumbers(jsonData);
            
            return jsonData;
        } catch (Exception e) {
            // Error creating JSON, logged silently
            e.printStackTrace();
            return null;
        }
    }

    private static void removeRecipe(@Nonnull Map<String, Object> jsonData) {
        jsonData.remove("Recipe");
    }
    
    /**
     * Collecte les références aux RootInteraction trouvées dans les items générés.
     * Ces références seront utilisées pour copier les fichiers RootInteraction depuis les mods sources.
     */
    @SuppressWarnings("unchecked")
    private static void collectRootInteractionReferences(@Nonnull Map<String, Object> jsonData) {
        Object interactionsObj = jsonData.get("Interactions");
        if (interactionsObj == null) {
            return;
        }
        
        if (!(interactionsObj instanceof Map)) {
            return;
        }
        
        Map<String, Object> interactions = (Map<String, Object>) interactionsObj;
        
        // Collecter toutes les références RootInteraction
        for (Object value : interactions.values()) {
            if (value instanceof String) {
                String ref = (String) value;
                // Les RootInteraction commencent généralement par "Root_"
                if (ref.startsWith("Root_")) {
                    requiredRootInteractions.add(ref);
                                    // Reference collected silently
                }
            }
        }
    }
    
    /**
     * Collecte les références aux ParticleSystem trouvées dans les items générés.
     */
    @SuppressWarnings("unchecked")
    private static void collectParticleSystemReferences(@Nonnull Map<String, Object> jsonData) {
        // Chercher dans Particles[]
        Object particlesObj = jsonData.get("Particles");
        if (particlesObj instanceof java.util.List) {
            for (Object particle : (java.util.List<?>) particlesObj) {
                if (particle instanceof Map) {
                    Object systemId = ((Map<String, Object>) particle).get("SystemId");
                    if (systemId instanceof String) {
                        String systemIdStr = (String) systemId;
                        requiredParticleSystems.add(systemIdStr);
                        logEssential("Found ParticleSystem reference: " + systemIdStr);
                    }
                }
            }
        }
        
        // Chercher dans ItemAppearanceConditions.*.Particles[]
        Object appearanceConditionsObj = jsonData.get("ItemAppearanceConditions");
        if (appearanceConditionsObj instanceof Map) {
            Map<String, Object> appearanceConditions = (Map<String, Object>) appearanceConditionsObj;
            for (Object conditionList : appearanceConditions.values()) {
                if (conditionList instanceof java.util.List) {
                    for (Object condition : (java.util.List<?>) conditionList) {
                        if (condition instanceof Map) {
                            Object particlesObj2 = ((Map<String, Object>) condition).get("Particles");
                            if (particlesObj2 instanceof java.util.List) {
                                for (Object particle : (java.util.List<?>) particlesObj2) {
                                    if (particle instanceof Map) {
                                        Object systemId = ((Map<String, Object>) particle).get("SystemId");
                                        if (systemId instanceof String) {
                                            requiredParticleSystems.add((String) systemId);
                                            // Reference collected silently
                                        }
                                    }
                                }
                            }
                            Object firstPersonParticlesObj = ((Map<String, Object>) condition).get("FirstPersonParticles");
                            if (firstPersonParticlesObj instanceof java.util.List) {
                                for (Object particle : (java.util.List<?>) firstPersonParticlesObj) {
                                    if (particle instanceof Map) {
                                        Object systemId = ((Map<String, Object>) particle).get("SystemId");
                                        if (systemId instanceof String) {
                                            requiredParticleSystems.add((String) systemId);
                                            // Reference collected silently
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Chercher dans InteractionVars.*.Interactions[].Effects.Particles[]
        Object interactionVarsObj = jsonData.get("InteractionVars");
        if (interactionVarsObj instanceof Map) {
            Map<String, Object> interactionVars = (Map<String, Object>) interactionVarsObj;
            for (Object interactionVar : interactionVars.values()) {
                if (interactionVar instanceof Map) {
                    Object interactionsList = ((Map<String, Object>) interactionVar).get("Interactions");
                    if (interactionsList instanceof java.util.List) {
                        for (Object interaction : (java.util.List<?>) interactionsList) {
                            if (interaction instanceof Map) {
                                Map<String, Object> interactionMap = (Map<String, Object>) interaction;
                                // Chercher dans Effects.Particles[]
                                Object effectsObj = interactionMap.get("Effects");
                                if (effectsObj instanceof Map) {
                                    Object particlesObj3 = ((Map<String, Object>) effectsObj).get("Particles");
                                    if (particlesObj3 instanceof java.util.List) {
                                        for (Object particle : (java.util.List<?>) particlesObj3) {
                                            if (particle instanceof Map) {
                                                Object systemId = ((Map<String, Object>) particle).get("SystemId");
                                                if (systemId instanceof String) {
                                                    requiredParticleSystems.add((String) systemId);
                                                    logVerbose("Found ParticleSystem reference in InteractionVars: " + systemId);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Collecte les références aux ParticleSpawner trouvées dans les items générés.
     */
    @SuppressWarnings("unchecked")
    private static void collectParticleSpawnerReferences(@Nonnull Map<String, Object> jsonData) {
        // Chercher dans Particles[] pour SpawnerId
        Object particlesObj = jsonData.get("Particles");
        if (particlesObj instanceof java.util.List) {
            for (Object particle : (java.util.List<?>) particlesObj) {
                if (particle instanceof Map) {
                    Object spawnerId = ((Map<String, Object>) particle).get("SpawnerId");
                    if (spawnerId instanceof String) {
                        String spawnerIdStr = (String) spawnerId;
                        requiredParticleSpawners.add(spawnerIdStr);
                        logEssential("Found ParticleSpawner reference: " + spawnerIdStr);
                    }
                }
            }
        }
        
        // Chercher dans ItemAppearanceConditions.*.Particles[]
        Object appearanceConditionsObj = jsonData.get("ItemAppearanceConditions");
        if (appearanceConditionsObj instanceof Map) {
            Map<String, Object> appearanceConditions = (Map<String, Object>) appearanceConditionsObj;
            for (Object conditionList : appearanceConditions.values()) {
                if (conditionList instanceof java.util.List) {
                    for (Object condition : (java.util.List<?>) conditionList) {
                        if (condition instanceof Map) {
                            Object particlesObj2 = ((Map<String, Object>) condition).get("Particles");
                            if (particlesObj2 instanceof java.util.List) {
                                for (Object particle : (java.util.List<?>) particlesObj2) {
                                    if (particle instanceof Map) {
                                        Object spawnerId = ((Map<String, Object>) particle).get("SpawnerId");
                                        if (spawnerId instanceof String) {
                                            requiredParticleSpawners.add((String) spawnerId);
                                            // Reference collected silently
                                        }
                                    }
                                }
                            }
                            Object firstPersonParticlesObj = ((Map<String, Object>) condition).get("FirstPersonParticles");
                            if (firstPersonParticlesObj instanceof java.util.List) {
                                for (Object particle : (java.util.List<?>) firstPersonParticlesObj) {
                                    if (particle instanceof Map) {
                                        Object spawnerId = ((Map<String, Object>) particle).get("SpawnerId");
                                        if (spawnerId instanceof String) {
                                            requiredParticleSpawners.add((String) spawnerId);
                                            // Reference collected silently
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Chercher dans InteractionVars.*.Interactions[].Effects.Particles[]
        Object interactionVarsObj = jsonData.get("InteractionVars");
        if (interactionVarsObj instanceof Map) {
            Map<String, Object> interactionVars = (Map<String, Object>) interactionVarsObj;
            for (Object interactionVar : interactionVars.values()) {
                if (interactionVar instanceof Map) {
                    Object interactionsList = ((Map<String, Object>) interactionVar).get("Interactions");
                    if (interactionsList instanceof java.util.List) {
                        for (Object interaction : (java.util.List<?>) interactionsList) {
                            if (interaction instanceof Map) {
                                Map<String, Object> interactionMap = (Map<String, Object>) interaction;
                                // Chercher dans Effects.Particles[]
                                Object effectsObj = interactionMap.get("Effects");
                                if (effectsObj instanceof Map) {
                                    Object particlesObj3 = ((Map<String, Object>) effectsObj).get("Particles");
                                    if (particlesObj3 instanceof java.util.List) {
                                        for (Object particle : (java.util.List<?>) particlesObj3) {
                                            if (particle instanceof Map) {
                                                Object spawnerId = ((Map<String, Object>) particle).get("SpawnerId");
                                                if (spawnerId instanceof String) {
                                                    requiredParticleSpawners.add((String) spawnerId);
                                                    // Reference collected silently
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Collecte les références aux Trail trouvées dans les items générés.
     */
    @SuppressWarnings("unchecked")
    private static void collectTrailReferences(@Nonnull Map<String, Object> jsonData) {
        Object trailsObj = jsonData.get("Trails");
        if (trailsObj instanceof java.util.List) {
            for (Object trail : (java.util.List<?>) trailsObj) {
                if (trail instanceof Map) {
                    Map<String, Object> trailMap = (Map<String, Object>) trail;
                    Object trailId = trailMap.get("TrailId");
                    if (trailId instanceof String) {
                        requiredTrails.add((String) trailId);
                        // Reference collected silently
                    }
                }
            }
        }
    }
    
    /**
     * Collecte les références aux ModelVFX trouvées dans les items générés.
     */
    @SuppressWarnings("unchecked")
    private static void collectModelVFXReferences(@Nonnull Map<String, Object> jsonData) {
        // Chercher dans ItemAppearanceConditions.*.ModelVFXId
        Object appearanceConditionsObj = jsonData.get("ItemAppearanceConditions");
        if (appearanceConditionsObj instanceof Map) {
            Map<String, Object> appearanceConditions = (Map<String, Object>) appearanceConditionsObj;
            for (Object conditionList : appearanceConditions.values()) {
                if (conditionList instanceof java.util.List) {
                    for (Object condition : (java.util.List<?>) conditionList) {
                        if (condition instanceof Map) {
                            Object modelVFXId = ((Map<String, Object>) condition).get("ModelVFXId");
                            if (modelVFXId instanceof String) {
                                requiredModelVFX.add((String) modelVFXId);
                                // Reference collected silently
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Collecte les références aux Interactions trouvées dans les items générés.
     * Les interactions sont référencées via "Parent" dans InteractionVars.
     */
    @SuppressWarnings("unchecked")
    private static void collectInteractionReferences(@Nonnull Map<String, Object> jsonData) {
        Object interactionVarsObj = jsonData.get("InteractionVars");
        if (interactionVarsObj instanceof Map) {
            Map<String, Object> interactionVars = (Map<String, Object>) interactionVarsObj;
            for (Object interactionVar : interactionVars.values()) {
                if (interactionVar instanceof Map) {
                    Object interactionsList = ((Map<String, Object>) interactionVar).get("Interactions");
                    if (interactionsList instanceof java.util.List) {
                        for (Object interaction : (java.util.List<?>) interactionsList) {
                            if (interaction instanceof Map) {
                                Map<String, Object> interactionMap = (Map<String, Object>) interaction;
                                Object parent = interactionMap.get("Parent");
                                if (parent instanceof String) {
                                    String parentId = (String) parent;
                                    // Collecter toutes les interactions référencées
                                    // Les interactions de mods (comme Weapon_Maelstrom_*) seront copiées depuis les mods
                                    // Les interactions de base seront cherchées dans Assets.zip si nécessaire
                                    requiredInteractions.add(parentId);
                                    // Reference collected silently
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Copie tout le contenu des mods sources vers le mod généré (sauf manifest.json).
     * Cette approche garantit qu'on a tous les fichiers nécessaires pour la compatibilité maximale.
     */
    private static void copyAssetDirectoriesFromSourceMods(@Nonnull JavaPlugin plugin, @Nonnull Path pluginModDir) {
        logEssential("Copying assets from source mods...");
        
        // Collecter tous les mods sources uniques
        java.util.Set<Path> sourceMods = new java.util.HashSet<>();
        for (Path sourcePath : modItemSourcePaths.values()) {
            if (sourcePath != null && Files.exists(sourcePath)) {
                // Si c'est un ZIP, on garde le ZIP
                // Si c'est un fichier JSON, on prend le mod parent
                String fileName = sourcePath.getFileName().toString();
                if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
                    sourceMods.add(sourcePath);
                } else {
                    // C'est un fichier JSON, remonter jusqu'au dossier mod
                    Path modDir = sourcePath;
                    // Remonter jusqu'à trouver le dossier mod (qui contient Server/ ou Common/)
                    while (modDir != null && 
                           !Files.exists(modDir.resolve("Server")) && 
                           !Files.exists(modDir.resolve("Common"))) {
                        modDir = modDir.getParent();
                        if (modDir == null || modDir.equals(modDir.getRoot())) {
                            break;
                        }
                    }
                    if (modDir != null && (Files.exists(modDir.resolve("Server")) || Files.exists(modDir.resolve("Common")))) {
                        sourceMods.add(modDir);
                    }
                }
            }
        }
        
        if (sourceMods.isEmpty()) {
            return;
        }
        
        int totalCopied = 0;
        for (Path sourceMod : sourceMods) {
            String modName = sourceMod.getFileName().toString();
            
            if (Files.isDirectory(sourceMod)) {
                // Mod en dossier - copier tout sauf manifest.json
                try {
                    Files.walk(sourceMod).forEach(sourcePath -> {
                        try {
                            Path relativePath = sourceMod.relativize(sourcePath);
                            String relativePathStr = relativePath.toString().replace("\\", "/");
                            
                            // Ignorer le manifest.json
                            if (relativePathStr.equals("manifest.json") || relativePathStr.endsWith("/manifest.json")) {
                                return;
                            }
                            
                            Path targetPath = pluginModDir.resolve(relativePath);
                            
                            if (Files.isDirectory(sourcePath)) {
                                Files.createDirectories(targetPath);
                            } else {
                                Files.createDirectories(targetPath.getParent());
                                Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException e) {
                            // Ignore errors silently
                        }
                    });
                    int fileCount = countFiles(sourceMod);
                    totalCopied += fileCount;
                } catch (IOException e) {
                    // Ignore errors silently
                }
            } else if (modName.endsWith(".zip") || modName.endsWith(".jar")) {
                // Mod en ZIP - copier tout sauf manifest.json
                try (ZipFile zipFile = new ZipFile(sourceMod.toFile())) {
                    java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
                    int filesCopied = 0;
                    
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String entryName = entry.getName();
                        
                        // Ignorer le manifest.json
                        if (entryName.equals("manifest.json") || entryName.endsWith("/manifest.json")) {
                            continue;
                        }
                        
                        // Ignorer les dossiers (ils seront créés automatiquement)
                        if (entry.isDirectory()) {
                            continue;
                        }
                        
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            Path targetFile = pluginModDir.resolve(entryName);
                            Files.createDirectories(targetFile.getParent());
                            Files.copy(is, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            filesCopied++;
                        } catch (Exception e) {
                            // Ignore errors silently
                        }
                    }
                    
                    totalCopied += filesCopied;
                } catch (IOException e) {
                    // Ignore errors silently
                }
            }
        }
        
        if (totalCopied > 0) {
            logEssential("Copied " + totalCopied + " asset file(s) from source mods.");
        }
    }

    /**
     * Supprime récursivement un dossier et son contenu.
     */
    private static void deleteDirectoryRecursive(@Nonnull Path directory) throws IOException {
        if (Files.exists(directory)) {
            Files.walk(directory)
                .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Ignore deletion errors
                    }
                });
        }
    }
    
    /**
     * Compte le nombre de fichiers dans un dossier (récursif).
     */
    private static int countFiles(@Nonnull Path directory) {
        try {
            return (int) Files.walk(directory)
                .filter(Files::isRegularFile)
                .count();
        } catch (IOException e) {
            return 0;
        }
    }

    /**
     * Modifie les dégâts des armes dans le JSON.
     * Structure Hytale : les armes utilisent "InteractionVars" à la racine, pas "Weapon.Interactions"
     */
    @SuppressWarnings("unchecked")
    private static void modifyWeaponDamage(@Nonnull Map<String, Object> jsonData, @Nonnull Item baseItem, float multiplier) {
        // Les armes dans Hytale utilisent "InteractionVars" directement à la racine du JSON
        // Structure: InteractionVars -> { "Swing_Left_Damage": { "Interactions": [...] }, ... }
        
        try {
            Map<String, Object> interactionVars = (Map<String, Object>) jsonData.get("InteractionVars");
            if (interactionVars == null) {
                // Certaines armes peuvent utiliser "Weapon.Interactions" (ancienne structure)
                Map<String, Object> weapon = (Map<String, Object>) jsonData.get("Weapon");
                if (weapon != null) {
                    interactionVars = (Map<String, Object>) weapon.get("Interactions");
                }
                if (interactionVars == null) {
                    return;
                }
            }
            
            // Parcourir toutes les interactions (Swing_Left_Damage, Swing_Right_Damage, etc.)
            int modifiedCount = 0;
            for (Map.Entry<String, Object> interactionEntry : interactionVars.entrySet()) {
                Object interactionValue = interactionEntry.getValue();
                if (interactionValue instanceof Map) {
                    modifyInteractionDamage((Map<String, Object>) interactionValue, multiplier);
                    modifiedCount++;
                }
            }
            
            if (modifiedCount > 0) {
                logVerbose("Damage modification for " + baseItem.getId() + ": " + modifiedCount + " interaction(s) processed");
            }
        } catch (Exception e) {
            logVerbose("Error modifying damage for " + baseItem.getId() + ": " + e.getMessage());
            if (verboseLogging) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Convertit un nombre en entier si c'est un nombre entier, sinon garde le double.
     * Évite d'avoir des nombres comme 5.0 dans le JSON.
     */
    private static Number toJsonNumber(double value) {
        if (value == (long) value) {
            return (long) value;
        }
        return value;
    }
    
    /**
     * Parcourt récursivement une structure JSON et convertit tous les nombres doubles en entiers si approprié.
     * Cette fonction garantit que tous les nombres entiers dans le JSON final sont des entiers et non des doubles.
     */
    @SuppressWarnings("unchecked")
    private static void normalizeJsonNumbers(@Nullable Object obj) {
        if (obj == null) {
            return;
        }
        
        if (obj instanceof Number) {
            // Cette fonction ne modifie pas directement les nombres dans les Maps/Listes
            // car elle est appelée récursivement et les modifications sont faites dans les boucles ci-dessous
            return;
        }
        
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Number) {
                    Number num = (Number) value;
                    double doubleValue = num.doubleValue();
                    if (doubleValue == (long) doubleValue) {
                        entry.setValue((long) doubleValue);
                    }
                } else {
                    normalizeJsonNumbers(value);
                }
            }
        } else if (obj instanceof java.util.List) {
            java.util.List<Object> list = (java.util.List<Object>) obj;
            for (int i = 0; i < list.size(); i++) {
                Object item = list.get(i);
                if (item instanceof Number) {
                    Number num = (Number) item;
                    double doubleValue = num.doubleValue();
                    if (doubleValue == (long) doubleValue) {
                        list.set(i, (long) doubleValue);
                    }
                } else {
                    normalizeJsonNumbers(item);
                }
            }
        }
    }
    
    /**
     * Modifie les dégâts dans une interaction.
     */
    @SuppressWarnings("unchecked")
    private static void modifyInteractionDamage(@Nonnull Map<String, Object> interaction, float multiplier) {
        Object interactionsList = interaction.get("Interactions");
        if (!(interactionsList instanceof java.util.List)) {
            return;
        }
        
        int damageTypesModified = 0;
        for (Object ia : (java.util.List<?>) interactionsList) {
            if (!(ia instanceof Map)) {
                continue;
            }
            
            Map<String, Object> iaMap = (Map<String, Object>) ia;
            Map<String, Object> damageCalculator = (Map<String, Object>) iaMap.get("DamageCalculator");
            if (damageCalculator == null) {
                continue;
            }
            
            Map<String, Object> baseDamage = (Map<String, Object>) damageCalculator.get("BaseDamage");
            if (baseDamage == null) {
                continue;
            }
            
            // Multiplier tous les types de dégâts
            for (Map.Entry<String, Object> entry : baseDamage.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Number) {
                    double oldValue = ((Number) value).doubleValue();
                    double newValue = oldValue * multiplier;
                    entry.setValue(toJsonNumber(newValue));
                    damageTypesModified++;
                }
            }
        }
        
        if (damageTypesModified > 0) {
            // Log détaillé pour debug (peut être commenté en production)
            // log("  -> " + damageTypesModified + " type(s) de dégâts modifié(s) dans cette interaction");
        }
    }
    
    /**
     * Modifie la puissance des outils dans le JSON.
     */
    @SuppressWarnings("unchecked")
    private static void modifyToolPower(@Nonnull Map<String, Object> jsonData, @Nonnull Item baseItem, float multiplier) {
        if (baseItem.getTool() == null) {
            return;
        }
        
        try {
            Map<String, Object> tool = (Map<String, Object>) jsonData.get("Tool");
            if (tool == null) {
                return;
            }
            
            java.util.List<Object> specs = (java.util.List<Object>) tool.get("Specs");
            if (specs == null) {
                return;
            }
            
            for (Object specObj : specs) {
                if (!(specObj instanceof Map)) {
                    continue;
                }
                
                Map<String, Object> spec = (Map<String, Object>) specObj;
                Object power = spec.get("Power");
                if (power instanceof Number) {
                    double newPower = ((Number) power).doubleValue() * multiplier;
                    spec.put("Power", toJsonNumber(newPower));
                }
            }
        } catch (Exception e) {
            // Ignorer les erreurs de modification de la puissance
        }
    }
    
    /**
     * Modifie les stats d'armure dans le JSON.
     *
     */
    @SuppressWarnings("unchecked")
    private static void modifyArmorStats(@Nonnull Map<String, Object> jsonData, @Nonnull Item baseItem, float multiplier) {
        if (baseItem.getArmor() == null) {
            if (verboseLogging) {
                System.out.println("[RQC] Armor is null for item: " + baseItem.getId());
            }
            return;
        }
        
        try {
            Map<String, Object> armor = (Map<String, Object>) jsonData.get("Armor");
            if (armor == null) {
                return;
            }
            
            // Modifier BaseDamageResistance
            Object baseDamageResistance = armor.get("BaseDamageResistance");
            if (baseDamageResistance instanceof Number) {
                double newBaseDamageResistance = ((Number) baseDamageResistance).doubleValue() * multiplier;
                armor.put("BaseDamageResistance", toJsonNumber(newBaseDamageResistance));
            }
            
            // Modifier DamageResistance
            // Structure: DamageResistance est une Map où chaque clé (ex: "Physical", "Projectile") 
            // contient une List d'objets avec "Amount"
            Object damageResistance = armor.get("DamageResistance");
            if (damageResistance instanceof Map) {
                Map<String, Object> damageResistanceMap = (Map<String, Object>) damageResistance;
                for (Map.Entry<String, Object> entry : damageResistanceMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof java.util.List) {
                        java.util.List<Object> resistanceList = (java.util.List<Object>) value;
                        for (Object resistanceObj : resistanceList) {
                            if (resistanceObj instanceof Map) {
                                Map<String, Object> resistanceMap = (Map<String, Object>) resistanceObj;
                                Object amount = resistanceMap.get("Amount");
                                if (amount instanceof Number) {
                                    double newAmount = ((Number) amount).doubleValue() * multiplier;
                                    resistanceMap.put("Amount", toJsonNumber(newAmount));
                                }
                            }
                        }
                    }
                }
            }
            
            // Modifier StatModifiers
            // Structure: StatModifiers est une Map où chaque clé (ex: "Health", "Mana") 
            // contient une List d'objets avec "Amount"
            Object statModifiers = armor.get("StatModifiers");
            if (statModifiers instanceof Map) {
                Map<String, Object> statModifiersMap = (Map<String, Object>) statModifiers;
                for (Map.Entry<String, Object> entry : statModifiersMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof java.util.List) {
                        java.util.List<Object> modifierList = (java.util.List<Object>) value;
                        for (Object modifierObj : modifierList) {
                            if (modifierObj instanceof Map) {
                                Map<String, Object> modifierMap = (Map<String, Object>) modifierObj;
                                Object amount = modifierMap.get("Amount");
                                if (amount instanceof Number) {
                                    double newAmount = ((Number) amount).doubleValue() * multiplier;
                                    modifierMap.put("Amount", toJsonNumber(newAmount));
                                }
                            }
                        }
                    }
                }
            }
            
            // Modifier DamageClassEnhancement
            // Structure similaire : Map où chaque clé contient une List d'objets avec "Amount"
            Object damageClassEnhancement = armor.get("DamageClassEnhancement");
            if (damageClassEnhancement instanceof Map) {
                Map<String, Object> damageClassEnhancementMap = (Map<String, Object>) damageClassEnhancement;
                for (Map.Entry<String, Object> entry : damageClassEnhancementMap.entrySet()) {
                    Object value = entry.getValue();
                    if (value instanceof java.util.List) {
                        java.util.List<Object> enhancementList = (java.util.List<Object>) value;
                        for (Object enhancementObj : enhancementList) {
                            if (enhancementObj instanceof Map) {
                                Map<String, Object> enhancementMap = (Map<String, Object>) enhancementObj;
                                Object amount = enhancementMap.get("Amount");
                                if (amount instanceof Number) {
                                    double newAmount = ((Number) amount).doubleValue() * multiplier;
                                    enhancementMap.put("Amount", toJsonNumber(newAmount));
                                }
                            }
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // Ignorer les erreurs de modification des stats d'armure
        }
    }

    /**
     * Convertit récursivement un BsonDocument en Map<String, Object>.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Object> convertBsonDocumentToMap(@Nonnull Object bsonDoc) {
        try {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            
            // Obtenir la méthode entrySet() ou keySet() pour itérer sur les valeurs
            Method entrySetMethod = null;
            try {
                entrySetMethod = bsonDoc.getClass().getMethod("entrySet");
            } catch (NoSuchMethodException e) {
                try {
                    entrySetMethod = bsonDoc.getClass().getMethod("keySet");
                } catch (NoSuchMethodException e2) {
                    return null;
                }
            }
            
            Object entries = entrySetMethod.invoke(bsonDoc);
            if (entries instanceof java.util.Set) {
                for (Object entry : (java.util.Set<?>) entries) {
                    String key;
                    Object value;
                    
                    if (entry instanceof Map.Entry) {
                        key = String.valueOf(((Map.Entry<?, ?>) entry).getKey());
                        value = ((Map.Entry<?, ?>) entry).getValue();
                    } else {
                        // Essayer d'obtenir la clé et la valeur via réflexion
                        try {
                            Method getKey = entry.getClass().getMethod("getKey");
                            Method getValue = entry.getClass().getMethod("getValue");
                            key = String.valueOf(getKey.invoke(entry));
                            value = getValue.invoke(entry);
                        } catch (Exception e) {
                            continue;
                        }
                    }
                    
                    // Convertir la valeur si c'est un BsonValue
                    Object convertedValue = convertBsonValue(value);
                    result.put(key, convertedValue);
                }
            }
            
            return result;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convertit un BsonValue en type Java standard.
     */
    @Nullable
    private static Object convertBsonValue(@Nullable Object bsonValue) {
        if (bsonValue == null) {
            return null;
        }
        
        String className = bsonValue.getClass().getName();
        
        // Si c'est déjà un type Java standard, le retourner tel quel
        if (bsonValue instanceof String || bsonValue instanceof Number || 
            bsonValue instanceof Boolean || bsonValue instanceof Map || 
            bsonValue instanceof java.util.List) {
            return bsonValue;
        }
        
        // Si c'est un BsonValue, utiliser les méthodes as*() pour obtenir la valeur
        try {
            if (className.contains("BsonString")) {
                Method asString = bsonValue.getClass().getMethod("getValue");
                return asString.invoke(bsonValue);
            } else if (className.contains("BsonInt32") || className.contains("BsonInt64")) {
                Method asInt = bsonValue.getClass().getMethod("getValue");
                return asInt.invoke(bsonValue);
            } else if (className.contains("BsonDouble")) {
                Method asDouble = bsonValue.getClass().getMethod("getValue");
                return asDouble.invoke(bsonValue);
            } else if (className.contains("BsonBoolean")) {
                Method asBoolean = bsonValue.getClass().getMethod("getValue");
                return asBoolean.invoke(bsonValue);
            } else if (className.contains("BsonDocument")) {
                return convertBsonDocumentToMap(bsonValue);
            } else if (className.contains("BsonArray")) {
                java.util.List<Object> list = new java.util.ArrayList<>();
                Method getValues = bsonValue.getClass().getMethod("getValues");
                Object values = getValues.invoke(bsonValue);
                if (values instanceof java.util.List) {
                    for (Object item : (java.util.List<?>) values) {
                        list.add(convertBsonValue(item));
                    }
                }
                return list;
            }
        } catch (Exception e) {
            // Si la conversion échoue, retourner la représentation string
            return bsonValue.toString();
        }
        
        return bsonValue.toString();
    }
    
    /**
     * Obtient la durabilité maximale d'un item.
     */
    private static double getMaxDurability(@Nonnull Item item) {
        try {
            Method m = Item.class.getMethod("getMaxDurability");
            Object v = m.invoke(item);
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
        } catch (Exception ignored) {
        }
        return 0;
    }
    
    /**
     * Écrit un fichier JSON.
     */
    private static void writeJsonFile(@Nonnull Path filePath, @Nonnull Map<String, Object> data) throws IOException {
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            GSON.toJson(data, writer);
        }
    }
    
    /**
     * Creates or updates manifest.json for generated files.
     * Only creates the manifest if it doesn't exist to preserve existing one.
     */
    private static void createManifestFile(@Nonnull Path pluginModDir) {
        try {
            Path manifestPath = pluginModDir.resolve("manifest.json");
            
            // Only create manifest if it doesn't exist (preserve existing one)
            if (!Files.exists(manifestPath)) {
                Map<String, Object> manifest = new java.util.LinkedHashMap<>();
                manifest.put("Group", "dev.hytalemodding");
                manifest.put("Name", "RQCGeneratedFiles");
                manifest.put("Description", "Quality variants system for Hytale");
                manifest.put("Version", "1.1.0");
                manifest.put("ServerVersion", "*");
                manifest.put("IncludesAssetPack", true);
                manifest.put("DisabledByDefault", false);
                
                writeJsonFile(manifestPath, manifest);
                logVerbose("Manifest created: " + manifestPath);
            }
        } catch (Exception e) {
            logVerbose("Error creating manifest: " + e.getMessage());
        }
    }
    
    /**
     * Trouve le chemin vers Assets.zip (fichier ZIP).
     * Structure typique: Hytale/UserData/Mods/{mods} et Hytale/install/release/package/game/latest/Assets.zip
     * On remonte depuis le dossier mods pour trouver le dossier Hytale racine.
     */
    @Nullable
    private static Path findAssetsZipPath() {
        // Return cached result if already initialized
        if (assetsPathInitialized) {
            return cachedAssetsZipPath;
        }
        
        // Clear previous attempts for new search
        attemptedAssetPaths.clear();
        assetDetectionFailed = false;
        
        logEssential("═══════════════════════════════════════════════════════════════════");
        logEssential("║  STARTING ASSETS.ZIP DETECTION                                 ║");
        logEssential("═══════════════════════════════════════════════════════════════════");
        logEssential("");
        
        // Priority 1: Check custom assets path from config
        if (customAssetsPath != null && !customAssetsPath.isEmpty()) {
            try {
                Path customPath = Paths.get(customAssetsPath);
                attemptedAssetPaths.add("[CUSTOM CONFIG] " + customPath.toAbsolutePath());
                
                if (Files.exists(customPath)) {
                    if (Files.isRegularFile(customPath) && customPath.toString().endsWith(".zip")) {
                        logEssential("✓ SUCCESS: Found Assets.zip at custom path: " + customPath.toAbsolutePath());
                        cachedAssetsZipPath = customPath;
                        assetsPathInitialized = true;
                        return customPath;
                    } else if (Files.isDirectory(customPath)) {
                        logEssential("✓ SUCCESS: Found assets directory at custom path: " + customPath.toAbsolutePath());
                        logEssential("  Note: This is a directory, not a ZIP file. Will use directory-based asset loading.");
                        cachedAssetsZipPath = customPath;
                        assetsPathInitialized = true;
                        return customPath;
                    } else {
                        logEssential("✗ FAILED: Custom path exists but is not a ZIP file or directory: " + customPath.toAbsolutePath());
                    }
                } else {
                    logEssential("✗ FAILED: Custom path does not exist: " + customPath.toAbsolutePath());
                }
            } catch (Exception e) {
                logEssential("✗ FAILED: Error accessing custom path: " + e.getMessage());
            }
        } else {
            logEssential("No custom assets path configured (CustomAssetsPath is empty)");
        }
        
        // Priority 2: Try server root directory (parent of mods folder)
        if (cachedModsDir != null) {
            try {
                Path parentDir = cachedModsDir.getParent();
                if (parentDir != null) {
                    Path assetsZipPath = parentDir.resolve("Assets.zip");
                    attemptedAssetPaths.add("[SERVER ROOT] " + assetsZipPath.toAbsolutePath());
                    
                    if (Files.exists(assetsZipPath) && Files.isRegularFile(assetsZipPath)) {
                        logEssential("✓ SUCCESS: Found Assets.zip at server root: " + assetsZipPath.toAbsolutePath());
                        cachedAssetsZipPath = assetsZipPath;
                        assetsPathInitialized = true;
                        return assetsZipPath;
                    } else {
                        logEssential("✗ FAILED: Assets.zip not found at server root: " + assetsZipPath.toAbsolutePath());
                    }
                }
            } catch (Exception e) {
                logEssential("✗ FAILED: Error searching server root: " + e.getMessage());
            }
        }
        
        // Priority 3: Try to find Hytale installation directory
        if (cachedModsDir != null) {
            try {
                Path currentDir = cachedModsDir;
                Path hytaleRootDir = null;
                
                logEssential("Searching for Hytale root directory...");
                
                // Remonter jusqu'à trouver un dossier qui contient à la fois "UserData" et "install"
                for (int i = 0; i < 10 && currentDir != null; i++) {
                    Path userDataDir = currentDir.resolve("UserData");
                    Path installDir = currentDir.resolve("install");
                    
                    attemptedAssetPaths.add("[HYTALE ROOT SEARCH #" + (i+1) + "] " + currentDir.toAbsolutePath());
                    
                    if (Files.exists(userDataDir) && Files.isDirectory(userDataDir) &&
                        Files.exists(installDir) && Files.isDirectory(installDir)) {
                        hytaleRootDir = currentDir;
                        logEssential("✓ Found Hytale root directory at: " + hytaleRootDir.toAbsolutePath());
                        break;
                    } else {
                        logVerbose("  Not Hytale root (missing UserData or install): " + currentDir.toAbsolutePath());
                    }
                    
                    currentDir = currentDir.getParent();
                }
                
                // Si on a trouvé le dossier racine, chercher Assets.zip dans install/release/package/game/latest/
                if (hytaleRootDir != null) {
                    Path assetsZipPath = hytaleRootDir
                        .resolve("install")
                        .resolve("release")
                        .resolve("package")
                        .resolve("game")
                        .resolve("latest")
                        .resolve("Assets.zip");
                    
                    attemptedAssetPaths.add("[HYTALE INSTALL] " + assetsZipPath.toAbsolutePath());
                    
                    if (Files.exists(assetsZipPath) && Files.isRegularFile(assetsZipPath)) {
                        logEssential("✓ SUCCESS: Found Assets.zip in Hytale installation: " + assetsZipPath.toAbsolutePath());
                        cachedAssetsZipPath = assetsZipPath;
                        assetsPathInitialized = true;
                        return assetsZipPath;
                    } else {
                        logEssential("✗ FAILED: Assets.zip not found at expected Hytale location: " + assetsZipPath.toAbsolutePath());
                    }
                } else {
                    logEssential("✗ FAILED: Could not find Hytale root directory (no directory with both UserData and install folders)");
                }
            } catch (Exception e) {
                logEssential("✗ FAILED: Error searching Hytale installation: " + e.getMessage());
            }
        }
        
        // All detection methods failed
        assetDetectionFailed = true;
        logEssential("=== Assets.zip Detection FAILED ===");
        logEssential("");
        logEssential("╔═══════════════════════════════════════════════════════════════════╗");
        logEssential("║                   ASSETS DETECTION FAILED                         ║");
        logEssential("╚═══════════════════════════════════════════════════════════════════╝");
        logEssential("");
        logEssential("The mod could not find the Hytale Assets.zip file or assets directory.");
        logEssential("Attempted paths:");
        for (String path : attemptedAssetPaths) {
            logEssential("  - " + path);
        }
        logEssential("");
        logEssential("╔═══════════════════════════════════════════════════════════════════╗");
        logEssential("║                     HOW TO FIX THIS ISSUE                         ║");
        logEssential("╚═══════════════════════════════════════════════════════════════════╝");
        logEssential("");
        logEssential("SOLUTION 1: Specify custom path in config");
        logEssential("  1. Open: config/config.json");
        logEssential("  2. Find the 'CustomAssetsPath' field");
        logEssential("  3. Set it to your Assets.zip location, for example:");
        logEssential("     Windows: \"CustomAssetsPath\": \"C:/Hytale/install/release/package/game/latest/Assets.zip\"");
        logEssential("     Linux:   \"CustomAssetsPath\": \"/home/user/hytale/install/release/package/game/latest/Assets.zip\"");
        logEssential("  4. OR point to an extracted assets folder:");
        logEssential("     \"CustomAssetsPath\": \"C:/Hytale/HytaleAssets\"");
        logEssential("  5. Save the file and restart the server");
        logEssential("");
        logEssential("SOLUTION 2: Place Assets.zip next to mods folder");
        logEssential("  1. Copy Assets.zip from your Hytale installation");
        logEssential("  2. Place it in your server's root directory (same level as 'mods' folder)");
        logEssential("  3. Restart the server");
        logEssential("");
        logEssential("SOLUTION 3: Extract Assets.zip");
        logEssential("  1. Extract Assets.zip to a folder named 'HytaleAssets'");
        logEssential("  2. Place the 'HytaleAssets' folder next to your 'mods' folder");
        logEssential("  3. Restart the server");
        logEssential("");
        logEssential("Assets.zip typical location:");
        logEssential("  Hytale/install/release/package/game/latest/Assets.zip");
        logEssential("");
        logEssential("═══════════════════════════════════════════════════════════════════");
        logEssential("");
        
        // Mark detection as complete (even though it failed)
        assetsPathInitialized = true;
        cachedAssetsZipPath = null;
        assetDetectionFailed = true;
        
        return null;
    }
    
    /**
     * Trouve le chemin vers HytaleAssets ou Assets.zip (comme dossier) dans le dossier parent du dossier mods.
     * Vérifie d'abord HytaleAssets, puis Assets.zip comme dossier.
     * Utilise également le chemin personnalisé si configuré.
     */
    @Nullable
    private static Path findHytaleAssetsPath() {
        if (cachedModsDir == null) {
            return null;
        }
        
        // Priority 1: Check custom assets path from config
        if (customAssetsPath != null && !customAssetsPath.isEmpty()) {
            try {
                Path customPath = Paths.get(customAssetsPath);
                
                if (Files.exists(customPath) && Files.isDirectory(customPath)) {
                    logVerbose("Using custom assets directory: " + customPath.toAbsolutePath());
                    return customPath;
                }
            } catch (Exception e) {
                logVerbose("Error accessing custom assets path as directory: " + e.getMessage());
            }
        }
        
        try {
            // Le dossier HytaleAssets ou Assets.zip se trouve dans le dossier parent de mods
            Path parentDir = cachedModsDir.getParent();
            if (parentDir != null) {
                // D'abord, essayer HytaleAssets
                Path hytaleAssetsPath = parentDir.resolve("HytaleAssets");
                if (Files.exists(hytaleAssetsPath) && Files.isDirectory(hytaleAssetsPath)) {
                    logVerbose("Found HytaleAssets at: " + hytaleAssetsPath);
                    return hytaleAssetsPath;
                }
                
                // Ensuite, essayer Assets.zip comme dossier
                Path assetsZipDirPath = parentDir.resolve("Assets.zip");
                if (Files.exists(assetsZipDirPath) && Files.isDirectory(assetsZipDirPath)) {
                    logVerbose("Found Assets.zip directory at: " + assetsZipDirPath);
                    return assetsZipDirPath;
                }
            }
        } catch (Exception e) {
            logVerbose("Error searching for HytaleAssets/Assets.zip directory: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Reads a JSON file from Assets.zip.
     * @param itemId The item ID (e.g., "Armor_Adamantite_Chest")
     * @return The parsed JSON content as a Map, or null if the file doesn't exist
     */
    @Nullable
    private static Map<String, Object> readJsonFromAssetsZip(@Nonnull String itemId) {
        Path assetsZipPath = findAssetsZipPath();
        if (assetsZipPath == null || !Files.exists(assetsZipPath)) {
            logVerbose("Assets.zip not found at: " + assetsZipPath);
            return null;
        }
        
        // Essayer d'abord directement dans Server/Item/Items/
        String[] possiblePaths = {
            "Server/Item/Items/" + itemId + ".json",
            "Server/Item/Items/Bench/" + itemId + ".json",
            "Server/Item/Items/Tool/" + itemId + ".json",
            "Server/Item/Items/Armor/" + itemId + ".json",
            "Server/Item/Items/Weapon/" + itemId + ".json"
        };
        
        try (ZipFile zipFile = new ZipFile(assetsZipPath.toFile())) {
            for (String zipEntryPath : possiblePaths) {
                ZipEntry entry = zipFile.getEntry(zipEntryPath);
                if (entry != null) {
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                        
                        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                        Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                        logVerbose("JSON read from Assets.zip: " + zipEntryPath);
                        return jsonData;
                    }
                }
            }
            
            // Si aucun chemin direct ne fonctionne, chercher récursivement
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.endsWith("/" + itemId + ".json") && entryName.contains("Server/Item/Items/")) {
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                        
                        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                        Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                        logVerbose("JSON read from Assets.zip: " + entryName);
                        return jsonData;
                    }
                }
            }
            
            logVerbose("File not found in Assets.zip for item: " + itemId);
            return null;
        } catch (Exception e) {
            logVerbose("Error reading from Assets.zip for " + itemId + ": " + e.getMessage());
            if (verboseLogging) {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    /**
     * Reads a JSON file from HytaleAssets or Assets.zip directory (unzipped version of Assets.zip).
     * Vérifie d'abord HytaleAssets, puis Assets.zip comme dossier dans le parent de mods.
     * @param itemId The item ID (e.g., "Armor_Adamantite_Chest")
     * @return The parsed JSON content as a Map, or null if the file doesn't exist
     */
    @Nullable
    private static Map<String, Object> readJsonFromHytaleAssets(@Nonnull String itemId) {
        Path hytaleAssetsPath = findHytaleAssetsPath();
        if (hytaleAssetsPath == null || !Files.exists(hytaleAssetsPath)) {
            logVerbose("HytaleAssets/Assets.zip directory not found");
            return null;
        }
        
        // Essayer d'abord directement dans Server/Item/Items/
        Path[] possiblePaths = {
            hytaleAssetsPath.resolve("Server").resolve("Item").resolve("Items").resolve(itemId + ".json"),
            hytaleAssetsPath.resolve("Server").resolve("Item").resolve("Items").resolve("Bench").resolve(itemId + ".json"),
            hytaleAssetsPath.resolve("Server").resolve("Item").resolve("Items").resolve("Tool").resolve(itemId + ".json"),
            hytaleAssetsPath.resolve("Server").resolve("Item").resolve("Items").resolve("Armor").resolve(itemId + ".json"),
            hytaleAssetsPath.resolve("Server").resolve("Item").resolve("Items").resolve("Weapon").resolve(itemId + ".json")
        };
        
        for (Path filePath : possiblePaths) {
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                try (java.io.FileReader reader = new java.io.FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
                    TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                    Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                    logVerbose("JSON read from HytaleAssets: " + hytaleAssetsPath.relativize(filePath));
                    return jsonData;
                } catch (Exception e) {
                    logVerbose("Error reading from HytaleAssets for " + itemId + " at " + filePath + ": " + e.getMessage());
                }
            }
        }
        
        // Si aucun chemin direct ne fonctionne, chercher récursivement dans Server/Item/Items/
        try {
            Path itemsDir = hytaleAssetsPath.resolve("Server").resolve("Item").resolve("Items");
            if (Files.exists(itemsDir) && Files.isDirectory(itemsDir)) {
                try (java.util.stream.Stream<Path> stream = Files.walk(itemsDir)) {
                    java.util.List<Path> matchingFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals(itemId + ".json"))
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (!matchingFiles.isEmpty()) {
                        Path filePath = matchingFiles.get(0);
                        try (java.io.FileReader reader = new java.io.FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
                            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                            Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                            logVerbose("JSON read from HytaleAssets: " + hytaleAssetsPath.relativize(filePath));
                            return jsonData;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logVerbose("Error searching recursively in HytaleAssets for " + itemId + ": " + e.getMessage());
        }
        
        logVerbose("File not found in HytaleAssets for item: " + itemId);
        return null;
    }

    /**
     * Génère les SalvageRecipe pour chaque qualité d'item.
     * Les recettes sont copiées depuis Assets.zip et modifiées pour référencer les items avec qualité.
     */
    private static void generateSalvageRecipes(@Nonnull JavaPlugin plugin, @Nonnull Map<String, Item> baseItems, @Nonnull Path modsDir) {
        Path recipesDir = modsDir.resolve("Server").resolve("Item").resolve("Recipes").resolve("Salvage");
        
        try {
            // Créer le dossier si nécessaire
            Files.createDirectories(recipesDir);
            logVerbose("Directory created: " + recipesDir);
        } catch (IOException e) {
            logVerbose("Error creating Recipes/Salvage directory: " + e.getMessage());
            return;
        }
        
        // Supprimer les anciennes recettes de salvage générées
        try {
            if (Files.exists(recipesDir)) {
                java.util.List<Path> filesToDelete = new java.util.ArrayList<>();
                try (java.util.stream.Stream<Path> stream = Files.walk(recipesDir)) {
                    stream.filter(Files::isRegularFile)
                          .filter(p -> p.toString().endsWith(".json"))
                          .forEach(filesToDelete::add);
                }
                for (Path file : filesToDelete) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        logVerbose("Unable to delete " + file + ": " + e.getMessage());
                    }
                }
                if (!filesToDelete.isEmpty()) {
                    logVerbose("Deleted " + filesToDelete.size() + " existing salvage recipe file(s) before regeneration");
                }
            }
        } catch (IOException e) {
            logVerbose("Error deleting existing files: " + e.getMessage());
        }
        
        logVerbose("Starting SalvageRecipe generation...");
        int generatedCount = 0;
        int errorCount = 0;
        
        for (Map.Entry<String, Item> entry : baseItems.entrySet()) {
            String baseId = entry.getKey();
            
            // Chercher la recette de salvage pour cet item
            Map<String, Object> salvageRecipe = readSalvageRecipeFromAssetsZip(baseId);
            if (salvageRecipe == null || salvageRecipe.isEmpty()) {
                continue; // Pas de recette de salvage pour cet item
            }
            
            // Générer une recette pour chaque qualité
            for (ItemQuality quality : ItemQuality.values()) {
                String qualityId = DynamicItemAssetCreator.getQualityItemId(baseId, quality);
                String recipeFileName = "Salvage_" + qualityId + ".json";
                Path recipeFile = recipesDir.resolve(recipeFileName);
                
                try {
                    Map<String, Object> qualityRecipe = createQualitySalvageRecipe(salvageRecipe, baseId, qualityId, quality);
                    if (qualityRecipe != null && !qualityRecipe.isEmpty()) {
                        writeJsonFile(recipeFile, qualityRecipe);
                        generatedCount++;
                    } else {
                        errorCount++;
                        logVerbose("Failed: recipeData is null or empty for " + recipeFileName);
                    }
                } catch (Exception e) {
                    errorCount++;
                    logVerbose("Error generating " + recipeFileName + ": " + e.getMessage());
                    if (verboseLogging) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        logVerbose("SalvageRecipe generation completed: " + generatedCount + " file(s) created, " + errorCount + " error(s)");
    }
    
    /**
     * Lit une recette de salvage depuis HytaleAssets/Assets.zip (dossier) ou Assets.zip (fichier).
     * Vérifie d'abord dans HytaleAssets/Assets.zip comme dossier, puis dans Assets.zip comme fichier.
     * @param itemId L'ID de l'item (ex: "Weapon_Sword_Copper")
     * @return Le contenu JSON parsé en Map, ou null si le fichier n'existe pas
     */
    @Nullable
    private static Map<String, Object> readSalvageRecipeFromAssetsZip(@Nonnull String itemId) {
        // D'abord, essayer depuis HytaleAssets
        Map<String, Object> result = readSalvageRecipeFromHytaleAssets(itemId);
        if (result != null) {
            return result;
        }
        
        // Sinon, essayer depuis Assets.zip
        Path assetsZipPath = findAssetsZipPath();
        if (assetsZipPath == null || !Files.exists(assetsZipPath)) {
            return null;
        }
        
        // Essayer différents noms de fichiers possibles
        String[] possibleNames = {
            "Salvage_" + itemId + ".json",
            itemId + "_Salvage.json",
            itemId + ".json"
        };
        
        try (ZipFile zipFile = new ZipFile(assetsZipPath.toFile())) {
            for (String fileName : possibleNames) {
                String zipEntryPath = "Server/Item/Recipes/Salvage/" + fileName;
                ZipEntry entry = zipFile.getEntry(zipEntryPath);
                if (entry != null) {
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                        
                        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                        Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                        logVerbose("SalvageRecipe read from Assets.zip: " + zipEntryPath);
                        return jsonData;
                    }
                }
            }
            
            // Chercher récursivement dans le dossier Salvage
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.contains("Server/Item/Recipes/Salvage/") && 
                    (entryName.contains(itemId) || entryName.contains("Salvage_" + itemId))) {
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                        
                        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                        Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                        logVerbose("SalvageRecipe read from Assets.zip: " + entryName);
                        return jsonData;
                    }
                }
            }
            
            return null;
        } catch (Exception e) {
            logVerbose("Error reading SalvageRecipe for " + itemId + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Lit une recette de salvage depuis HytaleAssets ou Assets.zip directory.
     * Vérifie d'abord HytaleAssets, puis Assets.zip comme dossier dans le parent de mods.
     * @param itemId L'ID de l'item (ex: "Weapon_Sword_Copper")
     * @return Le contenu JSON parsé en Map, ou null si le fichier n'existe pas
     */
    @Nullable
    private static Map<String, Object> readSalvageRecipeFromHytaleAssets(@Nonnull String itemId) {
        Path hytaleAssetsPath = findHytaleAssetsPath();
        if (hytaleAssetsPath == null || !Files.exists(hytaleAssetsPath)) {
            return null;
        }
        
        // Essayer différents noms de fichiers possibles
        String[] possibleNames = {
            "Salvage_" + itemId + ".json",
            itemId + "_Salvage.json",
            itemId + ".json"
        };
        
        Path salvageDir = hytaleAssetsPath.resolve("Server").resolve("Item").resolve("Recipes").resolve("Salvage");
        if (!Files.exists(salvageDir) || !Files.isDirectory(salvageDir)) {
            return null;
        }
        
        for (String fileName : possibleNames) {
            Path filePath = salvageDir.resolve(fileName);
            if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                try (java.io.FileReader reader = new java.io.FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
                    TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                    Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                    logVerbose("SalvageRecipe read from HytaleAssets: " + hytaleAssetsPath.relativize(filePath));
                    return jsonData;
                } catch (Exception e) {
                    logVerbose("Error reading SalvageRecipe from HytaleAssets for " + itemId + " at " + filePath + ": " + e.getMessage());
                }
            }
        }
        
        // Chercher récursivement dans le dossier Salvage
        try {
            try (java.util.stream.Stream<Path> stream = Files.walk(salvageDir)) {
                java.util.List<Path> matchingFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String fileName = p.getFileName().toString();
                        return (fileName.contains(itemId) || fileName.contains("Salvage_" + itemId)) && fileName.endsWith(".json");
                    })
                    .collect(java.util.stream.Collectors.toList());
                
                if (!matchingFiles.isEmpty()) {
                    Path filePath = matchingFiles.get(0);
                    try (java.io.FileReader reader = new java.io.FileReader(filePath.toFile(), StandardCharsets.UTF_8)) {
                        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                        Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                        logVerbose("SalvageRecipe read from HytaleAssets: " + hytaleAssetsPath.relativize(filePath));
                        return jsonData;
                    }
                }
            }
        } catch (Exception e) {
            logVerbose("Error searching recursively in HytaleAssets Salvage for " + itemId + ": " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Crée une recette de salvage pour une qualité spécifique en copiant la recette originale et en modifiant l'ID de l'item.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Object> createQualitySalvageRecipe(@Nonnull Map<String, Object> originalRecipe, 
                                                                  @Nonnull String baseId, 
                                                                  @Nonnull String qualityId, 
                                                                  @Nonnull ItemQuality quality) {
        try {
            // Créer une copie profonde de la recette
            String json = GSON.toJson(originalRecipe);
            Map<String, Object> recipe = GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
            
            // Modifier l'ID de l'item dans la recette - chercher récursivement dans toutes les structures possibles
            replaceItemIdInRecipe(recipe, baseId, qualityId);
            
            // Note: Ne pas modifier le champ "Id" de la recette car Hytale utilise le nom du fichier comme ID
            // Le nom du fichier est déjà "Salvage_" + qualityId + ".json"
            
            // Normaliser tous les nombres dans le JSON
            normalizeJsonNumbers(recipe);
            
            return recipe;
        } catch (Exception e) {
            logVerbose("Error creating SalvageRecipe for " + qualityId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Remplace récursivement l'ID d'item dans une structure de recette.
     */
    @SuppressWarnings("unchecked")
    private static void replaceItemIdInRecipe(@Nonnull Object obj, @Nonnull String oldItemId, @Nonnull String newItemId) {
        if (obj == null) {
            return;
        }
        
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                // Si c'est un champ ItemId et que la valeur correspond à l'ancien ID, le remplacer
                if (("ItemId".equals(key) || "Id".equals(key)) && value instanceof String) {
                    String itemId = (String) value;
                    if (itemId.equals(oldItemId)) {
                        entry.setValue(newItemId);
                    }
                } else {
                    // Sinon, continuer récursivement
                    replaceItemIdInRecipe(value, oldItemId, newItemId);
                }
            }
        } else if (obj instanceof java.util.List) {
            java.util.List<Object> list = (java.util.List<Object>) obj;
            for (Object item : list) {
                replaceItemIdInRecipe(item, oldItemId, newItemId);
            }
        }
    }
    
    /**
     * Génère les drops modifiés avec les qualités pour chaque item d'arme/armure/outil.
     * Lit tous les drops depuis HytaleAssets/Assets.zip (dossier) ou Assets.zip (fichier) 
     * et remplace les items par des Choice avec les 6 qualités.
     */
    private static void generateQualityDrops(@Nonnull JavaPlugin plugin, @Nonnull Map<String, Item> baseItems, @Nonnull Path modsDir) {
        Path dropsDir = modsDir.resolve("Server").resolve("Drops");
        
        try {
            // Créer le dossier si nécessaire
            Files.createDirectories(dropsDir);
            logVerbose("Directory created: " + dropsDir);
        } catch (IOException e) {
            logVerbose("Error creating Drops directory: " + e.getMessage());
            return;
        }
        
        // Supprimer les anciens drops générés
        try {
            if (Files.exists(dropsDir)) {
                java.util.List<Path> filesToDelete = new java.util.ArrayList<>();
                try (java.util.stream.Stream<Path> stream = Files.walk(dropsDir)) {
                    stream.filter(Files::isRegularFile)
                          .filter(p -> p.toString().endsWith(".json"))
                          .forEach(filesToDelete::add);
                }
                for (Path file : filesToDelete) {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        logVerbose("Unable to delete " + file + ": " + e.getMessage());
                    }
                }
                if (!filesToDelete.isEmpty()) {
                    logVerbose("Deleted " + filesToDelete.size() + " existing drop file(s) before regeneration");
                }
            }
        } catch (IOException e) {
            logVerbose("Error deleting existing files: " + e.getMessage());
        }
        
        logVerbose("Starting drop generation with qualities...");
        int generatedCount = 0;
        int errorCount = 0;
        
        // D'abord, essayer de lire depuis HytaleAssets
        Path hytaleAssetsPath = findHytaleAssetsPath();
        if (hytaleAssetsPath != null && Files.exists(hytaleAssetsPath)) {
            Path dropsSourceDir = hytaleAssetsPath.resolve("Server").resolve("Drops");
            if (Files.exists(dropsSourceDir) && Files.isDirectory(dropsSourceDir)) {
                try {
                    java.util.List<Path> dropFiles;
                    try (java.util.stream.Stream<Path> stream = Files.walk(dropsSourceDir)) {
                        dropFiles = stream
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".json"))
                            .collect(java.util.stream.Collectors.toList());
                    }
                    
                    for (Path dropFile : dropFiles) {
                        try (java.io.FileReader reader = new java.io.FileReader(dropFile.toFile(), StandardCharsets.UTF_8)) {
                            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                            Map<String, Object> dropData = GSON.fromJson(reader, typeToken.getType());
                            
                            // Modifier le drop pour remplacer les items par des Choice avec qualités
                            boolean modified = modifyDropForQualities(dropData, baseItems);
                            
                            if (modified) {
                                // Calculer le chemin relatif depuis Server/Drops/
                                Path relativePath = dropsSourceDir.relativize(dropFile);
                                Path outputFile = dropsDir.resolve(relativePath);
                                
                                // Créer les dossiers parents si nécessaire
                                Files.createDirectories(outputFile.getParent());
                                
                                // Normaliser tous les nombres dans le drop (convertir les doubles en entiers si approprié)
                                normalizeJsonNumbers(dropData);
                                
                                // Sauvegarder le drop modifié
                                writeJsonFile(outputFile, dropData);
                                generatedCount++;
                                
                                if (generatedCount % 10 == 0) {
                                    logVerbose("Progress: " + generatedCount + " drop file(s) created...");
                                }
                            }
                        } catch (Exception e) {
                            errorCount++;
                            logVerbose("Error processing drop " + dropsSourceDir.relativize(dropFile) + ": " + e.getMessage());
                            if (verboseLogging) {
                                e.printStackTrace();
                            }
                        }
                    }
                    
                    if (generatedCount > 0) {
                        logVerbose("Processed " + generatedCount + " drop file(s) from HytaleAssets" + (errorCount > 0 ? " (" + errorCount + " error(s))" : ""));
                        return;
                    }
                    // Si aucun drop n'a été trouvé dans HytaleAssets, continuer avec Assets.zip
                    logVerbose("No drops found in HytaleAssets, trying Assets.zip...");
                } catch (Exception e) {
                    logVerbose("Error reading drops from HytaleAssets: " + e.getMessage());
                    if (verboseLogging) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        // Sinon, essayer depuis Assets.zip
        Path assetsZipPath = findAssetsZipPath();
        if (assetsZipPath == null || !Files.exists(assetsZipPath)) {
            logVerbose("Assets.zip not found, unable to generate drops.");
            return;
        }
        
        try (ZipFile zipFile = new ZipFile(assetsZipPath.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Ne traiter que les fichiers de drops dans Server/Drops/
                if (entryName.startsWith("Server/Drops/") && entryName.endsWith(".json") && !entry.isDirectory()) {
                    try (InputStream inputStream = zipFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                        
                        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                        Map<String, Object> dropData = GSON.fromJson(reader, typeToken.getType());
                        
                        // Modifier le drop pour remplacer les items par des Choice avec qualités
                        boolean modified = modifyDropForQualities(dropData, baseItems);
                        
                        if (modified) {
                            // Calculer le chemin relatif depuis Server/Drops/
                            String relativePath = entryName.substring("Server/Drops/".length());
                            Path outputFile = dropsDir.resolve(relativePath);
                            
                            // Créer les dossiers parents si nécessaire
                            Files.createDirectories(outputFile.getParent());
                            
                            // Normaliser tous les nombres dans le drop (convertir les doubles en entiers si approprié)
                            normalizeJsonNumbers(dropData);
                            
                            // Sauvegarder le drop modifié
                            writeJsonFile(outputFile, dropData);
                            generatedCount++;
                            
                            if (generatedCount % 10 == 0) {
                                logVerbose("Progress: " + generatedCount + " drop file(s) created...");
                            }
                        }
                    } catch (Exception e) {
                        errorCount++;
                        logVerbose("Error processing drop " + entryName + ": " + e.getMessage());
                        if (verboseLogging) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        } catch (Exception e) {
            logVerbose("Error reading drops from Assets.zip: " + e.getMessage());
            if (verboseLogging) {
                e.printStackTrace();
            }
        }
        
        logVerbose("Drop generation completed: " + generatedCount + " file(s) created, " + errorCount + " error(s)");
    }
    
    /**
     * Modifie un drop pour remplacer les items d'armes/armures/outils par des Choice avec les 6 qualités.
     * @param dropData Les données du drop à modifier
     * @param baseItems La map des items de base qui peuvent avoir une qualité
     * @return true si le drop a été modifié, false sinon
     */
    @SuppressWarnings("unchecked")
    private static boolean modifyDropForQualities(@Nonnull Map<String, Object> dropData, @Nonnull Map<String, Item> baseItems) {
        boolean modified = false;
        
        // Parcourir récursivement la structure du drop
        Object container = dropData.get("Container");
        if (container != null) {
            modified = modifyContainerForQualities(container, baseItems) || modified;
        }
        
        return modified;
    }
    
    /**
     * Modifie récursivement un container de drop pour remplacer les items par des Choice avec qualités.
     */
    @SuppressWarnings("unchecked")
    private static boolean modifyContainerForQualities(@Nonnull Object container, @Nonnull Map<String, Item> baseItems) {
        boolean modified = false;
        
        if (container instanceof Map) {
            Map<String, Object> containerMap = (Map<String, Object>) container;
            String type = (String) containerMap.get("Type");
            
            if ("Single".equals(type)) {
                // Si c'est un Single avec un Item, vérifier s'il faut le remplacer
                Object itemObj = containerMap.get("Item");
                if (itemObj instanceof Map) {
                    Map<String, Object> item = (Map<String, Object>) itemObj;
                    Object itemIdObj = item.get("ItemId");
                    if (itemIdObj instanceof String) {
                        String itemId = (String) itemIdObj;
                        
                        // Vérifier si cet item peut avoir une qualité et n'en a pas déjà une
                        if (baseItems.containsKey(itemId) && !QualityManager.hasQualityInId(itemId)) {
                            // Remplacer le Single par un Choice avec les 6 qualités
                            Map<String, Object> choiceContainer = createQualityChoiceContainer(itemId, baseItems);
                            if (choiceContainer != null) {
                                // Copier les propriétés du Single (Weight, etc.) vers le Choice
                                // Ne pas copier "Id" car ce n'est pas utilisé dans les drops
                                Object weight = containerMap.get("Weight");
                                if (weight != null) {
                                    // Normaliser le poids pour éviter les "25.0" au lieu de "25"
                                    if (weight instanceof Number) {
                                        choiceContainer.put("Weight", toJsonNumber(((Number) weight).doubleValue()));
                                    } else {
                                        choiceContainer.put("Weight", weight);
                                    }
                                }
                                
                                // Remplacer le contenu du container
                                containerMap.clear();
                                containerMap.putAll(choiceContainer);
                                modified = true;
                            }
                        }
                    }
                }
            } else if ("Choice".equals(type) || "Multiple".equals(type)) {
                // Parcourir récursivement les Containers
                Object containersObj = containerMap.get("Containers");
                if (containersObj instanceof java.util.List) {
                    java.util.List<Object> containers = (java.util.List<Object>) containersObj;
                    for (int i = 0; i < containers.size(); i++) {
                        Object subContainer = containers.get(i);
                        if (modifyContainerForQualities(subContainer, baseItems)) {
                            modified = true;
                        }
                    }
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Crée un container Choice avec les 6 qualités pour un item de base.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Object> createQualityChoiceContainer(@Nonnull String baseItemId, @Nonnull Map<String, Item> baseItems) {
        if (!baseItems.containsKey(baseItemId)) {
            return null;
        }
        
        Map<String, Object> choiceContainer = new java.util.LinkedHashMap<>();
        choiceContainer.put("Type", "Choice");
        
        java.util.List<Object> containers = new java.util.ArrayList<>();
        
        // Créer un Single pour chaque qualité avec les poids de la configuration
        ItemQuality[] qualities = ItemQuality.values();
        // Ordre de ItemQuality.values() : POOR, COMMON, UNCOMMON, RARE, EPIC, LEGENDARY
        
        // Obtenir les poids depuis la configuration
        dev.hytalemodding.config.RomnasQualityCraftingConfig config = dev.hytalemodding.quality.QualityConfigManager.getConfig();
        double[] weights;
        if (config != null) {
            // Utiliser les poids de la config
            weights = new double[]{
                config.getQualityWeightPoor(),
                config.getQualityWeightCommon(),
                config.getQualityWeightUncommon(),
                config.getQualityWeightRare(),
                config.getQualityWeightEpic(),
                config.getQualityWeightLegendary()
            };
        } else {
            // Fallback aux valeurs par défaut si la config n'est pas disponible
            weights = new double[]{25.0, 40.0, 20.0, 10.0, 4.0, 1.0};
        }
        
        for (int i = 0; i < qualities.length; i++) {
            ItemQuality quality = qualities[i];
            String qualityId = DynamicItemAssetCreator.getQualityItemId(baseItemId, quality);
            
            Map<String, Object> singleContainer = new java.util.LinkedHashMap<>();
            singleContainer.put("Type", "Single");
            // Utiliser toJsonNumber pour éviter les "25.0" au lieu de "25"
            singleContainer.put("Weight", toJsonNumber(weights[i]));
            
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("ItemId", qualityId);
            item.put("QuantityMin", 1);
            item.put("QuantityMax", 1);
            
            singleContainer.put("Item", item);
            containers.add(singleContainer);
        }
        
        choiceContainer.put("Containers", containers);
        return choiceContainer;
    }
    
    /**
     * Obtient le dossier mods de la sauvegarde actuelle.
     * Cherche dans : {UserData}/Saves/{WorldName}/mods/
     */
    @Nullable
    private static Path getModsDirectory(@Nonnull JavaPlugin plugin, @Nullable Object bootEvent) {
        // Méthode 1: Essayer d'obtenir le chemin depuis BootEvent
        if (bootEvent != null) {
            try {
                // Essayer différentes méthodes pour obtenir le chemin du monde depuis l'événement
                String[] methodNames = {"getWorldPath", "getCurrentWorldPath", "getActiveWorldPath", "getWorldDirectory", "getSavePath"};
                for (String methodName : methodNames) {
                    try {
                        Method getWorldPath = bootEvent.getClass().getMethod(methodName);
                        Object worldPath = getWorldPath.invoke(bootEvent);
                        if (worldPath != null) {
                            Path worldPathObj = worldPath instanceof Path ? (Path) worldPath : Paths.get(worldPath.toString());
                            Path modsPath = worldPathObj.resolve("mods");
                            if (Files.exists(modsPath) || Files.createDirectories(modsPath) != null) {
                                // Found silently
                                return modsPath;
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Continuer avec la méthode suivante
                    }
                }
                
                // Essayer d'obtenir le serveur depuis l'événement
                try {
                    Method getServer = bootEvent.getClass().getMethod("getServer");
                    Object server = getServer.invoke(bootEvent);
                    if (server != null) {
                        String[] serverMethodNames = {"getWorldPath", "getCurrentWorldPath", "getActiveWorldPath", "getWorldDirectory"};
                        for (String methodName : serverMethodNames) {
                            try {
                                Method getWorldPath = server.getClass().getMethod(methodName);
                                Object worldPath = getWorldPath.invoke(server);
                                if (worldPath != null) {
                                    Path worldPathObj = worldPath instanceof Path ? (Path) worldPath : Paths.get(worldPath.toString());
                                    Path modsPath = worldPathObj.resolve("mods");
                                    if (Files.exists(modsPath) || Files.createDirectories(modsPath) != null) {
                                        // Found silently
                                        return modsPath;
                                    }
                                }
                            } catch (NoSuchMethodException ignored) {
                                // Continuer
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Continuer
                }
            } catch (Exception ignored) {
                // Méthode non disponible, continuer
            }
        }
        
        // Méthode 2: Utiliser getDataFolder() qui devrait pointer vers le monde actuel
        try {
            Method getDataFolder = JavaPlugin.class.getMethod("getDataFolder");
            File dataFolder = (File) getDataFolder.invoke(plugin);
            if (dataFolder != null) {
                // Le dataFolder est généralement dans le dossier du monde (plugins/ ou data/)
                // Remonter jusqu'au dossier du monde puis aller dans mods
                File worldDir = dataFolder.getParentFile();
                if (worldDir != null) {
                    // Vérifier si on est dans un sous-dossier (plugins/, data/, etc.)
                    String worldDirName = worldDir.getName();
                    if (worldDirName.equals("plugins") || worldDirName.equals("data") || worldDirName.equals("mods")) {
                        worldDir = worldDir.getParentFile();
                    }
                    
                    if (worldDir != null) {
                        File modsDir = new File(worldDir, "mods");
                        Path modsPath = modsDir.toPath();
                        if (modsDir.exists() || modsDir.mkdirs()) {
                            // Found silently
                            return modsPath;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Méthode non disponible, continuer
        }
        
        // Méthode 3: Obtenir le chemin du monde depuis le serveur
        try {
            Method getServer = plugin.getClass().getMethod("getServer");
            Object server = getServer.invoke(plugin);
            if (server != null) {
                // Essayer différentes méthodes pour obtenir le chemin du monde
                String[] methodNames = {"getWorldPath", "getCurrentWorldPath", "getActiveWorldPath", "getWorldDirectory"};
                for (String methodName : methodNames) {
                    try {
                        Method getWorldPath = server.getClass().getMethod(methodName);
                        Object worldPath = getWorldPath.invoke(server);
                        if (worldPath != null) {
                            Path worldPathObj = worldPath instanceof Path ? (Path) worldPath : Paths.get(worldPath.toString());
                            Path modsPath = worldPathObj.resolve("mods");
                            if (Files.exists(modsPath) || Files.createDirectories(modsPath) != null) {
                                // Found silently
                                return modsPath;
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Continuer avec la méthode suivante
                    }
                }
            }
        } catch (Exception e) {
            logVerbose("Error with getServer: " + e.getMessage());
        }
        
        // Méthode 4: Chercher dans les champs du plugin pour trouver le chemin du monde
        try {
            Field[] fields = plugin.getClass().getDeclaredFields();
            for (Field field : fields) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(plugin);
                    if (value != null && (value instanceof Path || value instanceof File || value instanceof String)) {
                        Path testPath = null;
                        if (value instanceof Path) {
                            testPath = (Path) value;
                        } else if (value instanceof File) {
                            testPath = ((File) value).toPath();
                        } else if (value instanceof String) {
                            testPath = Paths.get((String) value);
                        }
                        
                        if (testPath != null && Files.exists(testPath)) {
                            // Vérifier si c'est un dossier de sauvegarde (contient mods/ ou config.json)
                            Path modsPath = testPath.resolve("mods");
                            if (Files.exists(modsPath) || Files.exists(testPath.resolve("config.json"))) {
                                if (Files.exists(modsPath) || Files.createDirectories(modsPath) != null) {
                                    // Found silently
                                    return modsPath;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // Continuer avec le champ suivant
                }
            }
        } catch (Exception ignored) {
            // Erreur lors de la recherche, continuer
        }
        
        // Méthode 5: Chercher dans les propriétés système
        try {
            String userData = System.getProperty("hytale.userData");
            if (userData == null) {
                userData = System.getenv("HYTALE_USER_DATA");
            }
            if (userData != null) {
                Path savesDir = Paths.get(userData).resolve("Saves");
                if (Files.exists(savesDir)) {
                    // Chercher le dossier de sauvegarde le plus récemment modifié
                    Path latestSave = null;
                    long latestTime = 0;
                    try (java.util.stream.Stream<Path> stream = Files.list(savesDir)) {
                        for (Path saveDir : stream.filter(Files::isDirectory).collect(java.util.stream.Collectors.toList())) {
                            Path configFile = saveDir.resolve("config.json");
                            if (Files.exists(configFile)) {
                                long modTime = Files.getLastModifiedTime(configFile).toMillis();
                                if (modTime > latestTime) {
                                    latestTime = modTime;
                                    latestSave = saveDir;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    
                    if (latestSave != null) {
                        Path modsPath = latestSave.resolve("mods");
                        if (Files.exists(modsPath) || Files.createDirectories(modsPath) != null) {
                            // Found silently
                            return modsPath;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Erreur lors de la recherche, continuer
        }
        
        // Méthode 6: Utiliser le chemin par défaut de Hytale (Windows)
        try {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                Path defaultUserData = Paths.get(userHome).resolve("AppData").resolve("Roaming").resolve("Hytale").resolve("UserData");
                Path savesDir = defaultUserData.resolve("Saves");
                if (Files.exists(savesDir)) {
                    // Chercher le dossier de sauvegarde le plus récemment modifié
                    Path latestSave = null;
                    long latestTime = 0;
                    try (java.util.stream.Stream<Path> stream = Files.list(savesDir)) {
                        for (Path saveDir : stream.filter(Files::isDirectory).collect(java.util.stream.Collectors.toList())) {
                            Path configFile = saveDir.resolve("config.json");
                            if (Files.exists(configFile)) {
                                long modTime = Files.getLastModifiedTime(configFile).toMillis();
                                if (modTime > latestTime) {
                                    latestTime = modTime;
                                    latestSave = saveDir;
                                }
                            }
                        }
                    } catch (Exception ignored) {
                        // Erreur lors de la liste, continuer
                    }
                    
                    if (latestSave != null) {
                        Path modsPath = latestSave.resolve("mods");
                        if (Files.exists(modsPath) || Files.createDirectories(modsPath) != null) {
                            // Found silently
                            return modsPath;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Erreur lors de la recherche, continuer
        }
        
        // Méthode 7: Utiliser le répertoire de travail actuel
        try {
            String userDir = System.getProperty("user.dir");
            if (userDir != null) {
                Path currentDir = Paths.get(userDir);
                
                // Vérifier si on est directement dans un dossier de sauvegarde
                Path modsPath = currentDir.resolve("mods");
                Path configFile = currentDir.resolve("config.json");
                if (Files.exists(configFile) && (Files.exists(modsPath) || Files.createDirectories(modsPath) != null)) {
                    logVerbose("Mods directory found: " + modsPath);
                    return modsPath;
                }
                
                // Vérifier si on est dans un sous-dossier d'une sauvegarde
                Path parent = currentDir.getParent();
                if (parent != null) {
                    Path parentMods = parent.resolve("mods");
                    Path parentConfig = parent.resolve("config.json");
                    if (Files.exists(parentConfig) && (Files.exists(parentMods) || Files.createDirectories(parentMods) != null)) {
                        logVerbose("Mods directory found: " + parentMods);
                        return parentMods;
                    }
                }
            }
        } catch (Exception ignored) {
            // Erreur lors de la recherche, continuer
        }
        
        // If we get here, we couldn't determine the current world
        logEssential("ERROR: Unable to determine the current world's mods directory. Generation will be skipped.");
        return null;
    }
    
    /**
     * Gets the plugin's mod directory within the mods folder.
     * Looks for directories matching patterns like "RQCGeneratedFiles".
     * @param plugin The plugin instance
     * @param modsDir The mods directory of the save
     * @return The plugin's mod directory, or null if not found
     */
    @Nullable
    private static Path getPluginModDirectory(@Nonnull JavaPlugin plugin, @Nonnull Path modsDir) {
        // Method 1: Try to get the plugin's data folder and work backwards
        try {
            Method getDataFolder = JavaPlugin.class.getMethod("getDataFolder");
            File dataFolder = (File) getDataFolder.invoke(plugin);
            if (dataFolder != null && dataFolder.exists()) {
                // The data folder is typically inside the mod's directory
                // Try to find the mod directory by going up from data folder
                File current = dataFolder;
                while (current != null) {
                    Path currentPath = current.toPath();
                    // Check if this directory is in the mods folder
                    if (currentPath.startsWith(modsDir)) {
                        // This is likely the mod directory
                        // Check if it contains a manifest.json or Server folder
                        if (Files.exists(currentPath.resolve("manifest.json")) || 
                            Files.exists(currentPath.resolve("Server")) ||
                            Files.exists(currentPath.resolve("config.json"))) {
                            // Found silently
                            return currentPath;
                        }
                    }
                    current = current.getParentFile();
                }
            }
        } catch (Exception ignored) {
            // Method not available, continue
        }
        
        // Method 2: Search for directories matching common naming patterns
        // First, try the new name RQCGeneratedFiles
        try {
            Path rqcGeneratedPath = modsDir.resolve("RQCGeneratedFiles");
            if (Files.exists(rqcGeneratedPath) && Files.isDirectory(rqcGeneratedPath)) {
                // Check if it looks like our mod directory (has config file or Server folder)
                if (Files.exists(rqcGeneratedPath.resolve("config.json")) ||
                    Files.exists(rqcGeneratedPath.resolve("manifest.json")) ||
                    Files.exists(rqcGeneratedPath.resolve("Server"))) {
                    // Found silently
                    return rqcGeneratedPath;
                }
            }
        } catch (Exception e) {
            logVerbose("Error checking RQCGeneratedFiles directory: " + e.getMessage());
        }
        
        // Then, try old names for backward compatibility (migration support)
        try {
            String[] possibleNames = {
                "dev.hytalemodding_RomnasQualityCrafting",
                "RomnasQualityCrafting",
                "RomnasQualityCrafting"
            };
            
            for (String name : possibleNames) {
                Path testPath = modsDir.resolve(name);
                if (Files.exists(testPath) && Files.isDirectory(testPath)) {
                    // Check if it looks like our mod directory (has config file or Server folder)
                    if (Files.exists(testPath.resolve("config.json")) ||
                        Files.exists(testPath.resolve("manifest.json")) ||
                        Files.exists(testPath.resolve("Server"))) {
                        // Found silently
                        return testPath;
                    }
                }
            }
            
            // Method 3: Search all directories in modsDir for one containing our config file
            // Prioritize RQCGeneratedFiles if it exists
            Path rqcGeneratedPath = modsDir.resolve("RQCGeneratedFiles");
            if (Files.exists(rqcGeneratedPath) && Files.isDirectory(rqcGeneratedPath)) {
                if (Files.exists(rqcGeneratedPath.resolve("config.json"))) {
                    // Found silently
                    return rqcGeneratedPath;
                }
            }
            
            try (java.util.stream.Stream<Path> stream = Files.list(modsDir)) {
                for (Path modPath : stream.filter(Files::isDirectory).collect(java.util.stream.Collectors.toList())) {
                    String modName = modPath.getFileName().toString();
                    // Skip RQCGeneratedFiles as we already checked it
                    if (modName.equals("RQCGeneratedFiles")) {
                        continue;
                    }
                    // Check if this directory contains our config file
                    if (Files.exists(modPath.resolve("config.json"))) {
                        // Found silently
                        return modPath;
                    }
                    // Also check if it contains "RomnasQualityCrafting" or "RQCGeneratedFiles" in the name
                    if (modName.contains("RomnasQualityCrafting") || modName.contains("Romnas") || modName.contains("RQCGeneratedFiles")) {
                        if (Files.exists(modPath.resolve("manifest.json")) || 
                            Files.exists(modPath.resolve("Server"))) {
                            // Found silently
                            return modPath;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logVerbose("Error searching for plugin mod directory: " + e.getMessage());
        }
        
        // Method 4: If not found, create it with the most likely name
        Path fallbackPath = modsDir.resolve("RQCGeneratedFiles");
        try {
            Files.createDirectories(fallbackPath);
            logVerbose("Created plugin mod directory: " + fallbackPath);
            return fallbackPath;
        } catch (Exception e) {
            logEssential("Unable to create plugin mod directory: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Scanne les mods externes chargés pour trouver des items (armes, armures, outils) et les ajouter à la génération.
     * @param plugin Le plugin Java
     * @return Une map des items trouvés dans les mods externes (ID -> Item)
     */
    @Nonnull
    private static Map<String, Item> scanExternalModsForItems(@Nonnull JavaPlugin plugin) {
        Map<String, Item> modItems = new java.util.LinkedHashMap<>();
        
        try {
            logEssential("═══════════════════════════════════════════════════════════════════");
            logEssential("║  SCANNING EXTERNAL MODS FOR ITEMS                              ║");
            logEssential("═══════════════════════════════════════════════════════════════════");
            logEssential("");
            
            // Get the list of actually loaded mods
            java.util.Set<String> loadedModNames = getLoadedModNames(plugin);
            if (!loadedModNames.isEmpty()) {
                logEssential("Detected " + loadedModNames.size() + " loaded mod(s): " + loadedModNames);
            } else {
                logEssential("No loaded mods detected via reflection (normal if no mods are loaded)");
            }
            logEssential("");
            
            // Get the global Mods directory (not the save's mods)
            Path globalModsDir = getGlobalModsDirectory();
            if (globalModsDir == null || !Files.exists(globalModsDir)) {
                logEssential("No global mods directory found - external mod compatibility disabled");
                logEssential("═══════════════════════════════════════════════════════════════════");
                logEssential("");
                return modItems;
            }
            
            logEssential("Scanning global mods directory: " + globalModsDir.toAbsolutePath());
            logEssential("");
            
            // Parcourir les mods (peuvent être des dossiers ou des fichiers ZIP/JAR)
            int modsScanned = 0;
            int modsSkipped = 0;
            int modsProcessed = 0;
            int totalItemsFound = 0;
            
            try (java.util.stream.Stream<Path> modPaths = Files.list(globalModsDir)) {
                java.util.List<Path> modPathsList = modPaths.collect(java.util.stream.Collectors.toList());
                logEssential("Found " + modPathsList.size() + " element(s) in global mods directory");
                logEssential("Found " + modPathsList.size() + " element(s) in global mods directory");
                logEssential("");
                
                // List all elements for debug
                for (Path path : modPathsList) {
                    String filename = path.getFileName().toString();
                    boolean isDir = Files.isDirectory(path);
                    boolean isFile = Files.isRegularFile(path);
                    logVerbose("  Element: " + filename + " (dir: " + isDir + ", file: " + isFile + ")");
                }
                
                for (Path modPath : modPathsList) {
                    String modName = modPath.getFileName().toString();
                    modsScanned++;
                    
                    // Mods can be directories or ZIP/JAR files
                    if (Files.isDirectory(modPath)) {
                        logEssential("Processing directory mod: " + modName);
                        int itemsBefore = modItems.size();
                        // It's a directory, we can scan directly
                        Path modDir = modPath;
                        processModDirectory(modDir, modName, loadedModNames, modItems);
                        int itemsAdded = modItems.size() - itemsBefore;
                        
                        if (itemsAdded > 0) {
                            logEssential("  → Added " + itemsAdded + " item(s) from this mod");
                            modsProcessed++;
                            totalItemsFound += itemsAdded;
                        } else {
                            logEssential("  → No items found or mod not loaded");
                            modsSkipped++;
                        }
                    } else if (modName.endsWith(".zip") || modName.endsWith(".jar")) {
                        logEssential("Processing ZIP/JAR mod: " + modName);
                        int itemsBefore = modItems.size();
                        // It's a ZIP/JAR file, we need to extract or scan it as a ZIP
                        processModZipFile(modPath, modName, loadedModNames, modItems);
                        int itemsAdded = modItems.size() - itemsBefore;
                        
                        if (itemsAdded > 0) {
                            logEssential("  → Added " + itemsAdded + " item(s) from this mod");
                            modsProcessed++;
                            totalItemsFound += itemsAdded;
                        } else {
                            logEssential("  → No items found or mod not loaded");
                            modsSkipped++;
                        }
                    } else {
                        logVerbose("  Skipped (unsupported format): " + modName);
                        modsSkipped++;
                    }
                }
            } catch (Exception e) {
                logEssential("Error traversing mods: " + e.getMessage());
                if (verboseLogging) {
                    e.printStackTrace();
                }
            }
            
            // Final statistics
            logEssential("");
            logEssential("═══════════════════════════════════════════════════════════════════");
            logEssential("║  EXTERNAL MODS SCAN COMPLETE                                   ║");
            logEssential("═══════════════════════════════════════════════════════════════════");
            logEssential("Mods scanned:      " + modsScanned);
            logEssential("Mods processed:    " + modsProcessed);
            logEssential("Mods skipped:      " + modsSkipped);
            logEssential("Total items found: " + totalItemsFound);
            logEssential("Unique items:      " + modItems.size());
            logEssential("═══════════════════════════════════════════════════════════════════");
            logEssential("");
        } catch (Exception e) {
            logEssential("Error scanning external mods: " + e.getMessage());
        }
        
        return modItems;
    }
    
    /**
     * Traite un mod qui est un dossier.
     * Avec validation de structure et logging détaillé.
     */
    private static void processModDirectory(@Nonnull Path modDir, @Nonnull String modName, 
                                           @Nonnull java.util.Set<String> loadedModNames, 
                                           @Nonnull Map<String, Item> modItems) {
        // Ignorer notre propre mod source et le mod généré (mais pas le dossier mods lui-même où on génère)
        if (modName.contains("RomnasQualityCrafting") || modName.contains("RQCGeneratedFiles")) {
            logVerbose("    Skipping (own mod): " + modName);
            return;
        }
        
        // Si on a une liste de mods chargés, vérifier si ce mod est chargé
        if (!loadedModNames.isEmpty() && !isModLoaded(modName, loadedModNames)) {
            logVerbose("    Skipping (not loaded): " + modName);
            return;
        }
        
        // Validation de structure: vérifier que le mod a une structure valide
        Path modItemsDir = modDir.resolve("Server").resolve("Item").resolve("Items");
        if (!Files.exists(modItemsDir) || !Files.isDirectory(modItemsDir)) {
            logVerbose("    Invalid mod structure (no Server/Item/Items/ directory): " + modName);
            return;
        }
        
        // Chercher les items dans ce mod
        logVerbose("    Scanning items in: " + modItemsDir.toAbsolutePath());
        int itemsBefore = modItems.size();
        scanModDirectoryForItems(modItemsDir, modItems, modName);
        int itemsAdded = modItems.size() - itemsBefore;
        logVerbose("    Found " + itemsAdded + " item(s) in directory mod: " + modName);
    }
    
    /**
     * Traite un mod qui est un fichier ZIP/JAR.
     * Avec validation de structure et logging détaillé.
     */
    private static void processModZipFile(@Nonnull Path zipPath, @Nonnull String modName,
                                         @Nonnull java.util.Set<String> loadedModNames,
                                         @Nonnull Map<String, Item> modItems) {
        // Ignorer notre propre mod et le mod généré
        if (modName.contains("RomnasQualityCrafting") || modName.contains("RQCGeneratedFiles")) {
            logVerbose("    Skipping (own mod): " + modName);
            return;
        }
        
        // Si on a une liste de mods chargés, vérifier si ce mod est chargé
        String baseModName = extractBaseModName(modName.replace(".zip", "").replace(".jar", ""));
        if (!loadedModNames.isEmpty() && !isModLoaded(baseModName, loadedModNames)) {
            logVerbose("    Skipping (not loaded): " + modName);
            return;
        }
        
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            // Vérifier que le ZIP contient une structure de mod valide
            boolean hasValidStructure = false;
            java.util.Enumeration<? extends ZipEntry> preCheck = zipFile.entries();
            while (preCheck.hasMoreElements()) {
                ZipEntry entry = preCheck.nextElement();
                if (entry.getName().startsWith("Server/Item/Items/")) {
                    hasValidStructure = true;
                    break;
                }
            }
            
            if (!hasValidStructure) {
                logVerbose("    Invalid mod structure (no Server/Item/Items/ in ZIP): " + modName);
                return;
            }
            
            logVerbose("    Scanning ZIP/JAR contents: " + modName);
            
            // Parcourir toutes les entrées du ZIP
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            int itemsFound = 0;
            int filesScanned = 0;
            
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Chercher les fichiers JSON dans Server/Item/Items/
                if (entryName.startsWith("Server/Item/Items/") && entryName.endsWith(".json") && !entry.isDirectory()) {
                    filesScanned++;
                    
                    try {
                        // Extraire l'ID de l'item (nom du fichier sans extension)
                        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                        String itemId = fileName.substring(0, fileName.length() - 5); // Enlever ".json"
                        
                        // Lire le JSON depuis le ZIP
                        try (InputStream is = zipFile.getInputStream(entry);
                             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                            
                            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                            Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                            
                            if (jsonData != null && !jsonData.isEmpty()) {
                                // Vérifier si c'est une arme, armure ou outil
                                if (isWeaponArmorOrTool(jsonData)) {
                                    // Chercher l'item dans la map Item globale
                                    Item item = findItemById(itemId);
                                    if (item != null && item != Item.UNKNOWN) {
                                        modItems.put(itemId, item);
                                        // Stocker le chemin source (on va créer un Path temporaire ou utiliser le ZIP)
                                        // Pour l'instant, on stocke juste l'ID et on lira depuis le ZIP plus tard
                                        modItemSourcePaths.put(itemId, zipPath); // On stocke le chemin du ZIP
                                        itemsFound++;
                                        logVerbose("      Found item: " + itemId);
                                    } else {
                                        logVerbose("      Item not in global registry: " + itemId);
                                    }
                                } else {
                                    logVerbose("      Skipped (not weapon/armor/tool): " + itemId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logVerbose("      Error reading " + entryName + ": " + e.getMessage());
                    }
                }
            }
            
            logVerbose("    Scanned " + filesScanned + " file(s), found " + itemsFound + " valid item(s) in: " + modName);
            
        } catch (Exception e) {
            logEssential("Error scanning mod ZIP file " + zipPath.getFileName() + ": " + e.getMessage());
            if (verboseLogging) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Lit le JSON d'un item depuis un fichier ZIP de mod.
     */
    @Nullable
    private static Map<String, Object> readJsonFromModZip(@Nonnull Path zipPath, @Nonnull String itemId) {
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            // Chercher le fichier JSON dans le ZIP avec des chemins possibles
            String[] possiblePaths = {
                "Server/Item/Items/" + itemId + ".json",
                "Server/Item/Items/Weapon/" + itemId + ".json",
                "Server/Item/Items/Armor/" + itemId + ".json",
                "Server/Item/Items/Tool/" + itemId + ".json",
                "Server/Item/Items/Bench/" + itemId + ".json"
            };
            
            // D'abord, essayer les chemins directs
            for (String zipEntryPath : possiblePaths) {
                ZipEntry entry = zipFile.getEntry(zipEntryPath);
                if (entry != null && !entry.isDirectory()) {
                    try (InputStream is = zipFile.getInputStream(entry);
                         InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        
                        TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                        Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                        logVerbose("JSON read from mod ZIP (" + zipPath.getFileName() + "): " + zipEntryPath);
                        return jsonData;
                    } catch (Exception e) {
                        logVerbose("Error reading " + zipEntryPath + " from " + zipPath.getFileName() + ": " + e.getMessage());
                    }
                }
            }
            
            // Si aucun chemin direct ne fonctionne, chercher récursivement dans Server/Item/Items/
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();
                
                // Chercher un fichier qui se termine par "{itemId}.json" ou "/{itemId}.json" dans Server/Item/Items/
                if (entryName.contains("Server/Item/Items/") && 
                    (entryName.endsWith("/" + itemId + ".json") || entryName.endsWith(itemId + ".json")) &&
                    !entry.isDirectory()) {
                    // Vérifier que c'est bien le bon fichier (pas un fichier qui contient juste l'ID dans le chemin)
                    String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                    if (fileName.equals(itemId + ".json")) {
                        try (InputStream is = zipFile.getInputStream(entry);
                             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                            
                            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                            Map<String, Object> jsonData = GSON.fromJson(reader, typeToken.getType());
                            logVerbose("JSON read from mod ZIP (" + zipPath.getFileName() + "): " + entryName);
                            return jsonData;
                        } catch (Exception e) {
                            logVerbose("Error reading " + entryName + " from " + zipPath.getFileName() + ": " + e.getMessage());
                        }
                    }
                }
            }
            
            logVerbose("JSON file not found in mod ZIP (" + zipPath.getFileName() + ") for item: " + itemId);
        } catch (Exception e) {
            logEssential("Error opening mod ZIP " + zipPath.getFileName() + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Obtient la liste des noms de mods réellement chargés pour cette sauvegarde.
     */
    @Nonnull
    private static java.util.Set<String> getLoadedModNames(@Nonnull JavaPlugin plugin) {
        java.util.Set<String> loadedModNames = new java.util.HashSet<>();
        
        try {
            // Méthode 1: Essayer d'obtenir le PluginManager depuis le plugin
            try {
                Method getPluginManager = plugin.getClass().getMethod("getPluginManager");
                Object pluginManager = getPluginManager.invoke(plugin);
                if (pluginManager != null) {
                    // Essayer différentes méthodes pour obtenir les plugins chargés
                    String[] methodNames = {"getPlugins", "getLoadedPlugins", "getAllPlugins"};
                    for (String methodName : methodNames) {
                        try {
                            Method getPlugins = pluginManager.getClass().getMethod(methodName);
                            Object plugins = getPlugins.invoke(pluginManager);
                            if (plugins instanceof java.util.Collection) {
                                for (Object loadedPlugin : (java.util.Collection<?>) plugins) {
                                    String pluginName = extractPluginName(loadedPlugin);
                                    if (pluginName != null) {
                                        loadedModNames.add(pluginName);
                                    }
                                }
                                if (!loadedModNames.isEmpty()) {
                                    break;
                                }
                            }
                        } catch (NoSuchMethodException ignored) {
                            // Continuer avec la méthode suivante
                        }
                    }
                }
            } catch (Exception ignored) {
                // Méthode non disponible, continuer
            }
            
            // Méthode 2: Essayer d'obtenir depuis le serveur
            try {
                Method getServer = plugin.getClass().getMethod("getServer");
                Object server = getServer.invoke(plugin);
                if (server != null) {
                    // Essayer d'obtenir le PluginManager depuis le serveur
                    try {
                        Method getPluginManager = server.getClass().getMethod("getPluginManager");
                        Object pluginManager = getPluginManager.invoke(server);
                        if (pluginManager != null) {
                            String[] methodNames = {"getPlugins", "getLoadedPlugins", "getAllPlugins"};
                            for (String methodName : methodNames) {
                                try {
                                    Method getPlugins = pluginManager.getClass().getMethod(methodName);
                                    Object plugins = getPlugins.invoke(pluginManager);
                                    if (plugins instanceof java.util.Collection) {
                                        for (Object loadedPlugin : (java.util.Collection<?>) plugins) {
                                            String pluginName = extractPluginName(loadedPlugin);
                                            if (pluginName != null) {
                                                loadedModNames.add(pluginName);
                                            }
                                        }
                                        if (!loadedModNames.isEmpty()) {
                                            break;
                                        }
                                    }
                                } catch (NoSuchMethodException ignored) {
                                    // Continuer
                                }
                            }
                        }
                    } catch (NoSuchMethodException ignored) {
                        // Continuer
                    }
                }
            } catch (Exception ignored) {
                // Méthode non disponible, continuer
            }
            
            // Méthode 3: Lire depuis le fichier de configuration de la sauvegarde
            try {
                Path modsDir = getModsDirectory(plugin, null);
                if (modsDir != null) {
                    Path worldDir = modsDir.getParent();
                    if (worldDir != null) {
                        Path configFile = worldDir.resolve("config.json");
                        if (Files.exists(configFile)) {
                            Map<String, Object> config = readJsonFromFile(configFile);
                            if (config != null) {
                                // Chercher les mods activés dans la config
                                Object modsObj = config.get("mods");
                                if (modsObj instanceof java.util.List) {
                                    for (Object modObj : (java.util.List<?>) modsObj) {
                                        if (modObj instanceof String) {
                                            loadedModNames.add((String) modObj);
                                        } else if (modObj instanceof Map) {
                                            @SuppressWarnings("unchecked")
                                            Map<String, Object> modMap = (Map<String, Object>) modObj;
                                            Object nameObj = modMap.get("name");
                                            if (nameObj instanceof String) {
                                                loadedModNames.add((String) nameObj);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Erreur silencieuse
            }
            
        } catch (Exception e) {
            logVerbose("Error retrieving loaded mods: " + e.getMessage());
        }
        
        return loadedModNames;
    }
    
    /**
     * Extrait le nom d'un plugin depuis l'objet plugin.
     */
    @Nullable
    private static String extractPluginName(@Nonnull Object plugin) {
        try {
            // Essayer différentes méthodes pour obtenir le nom
            String[] methodNames = {"getName", "getPluginName", "getId"};
            for (String methodName : methodNames) {
                try {
                    Method getName = plugin.getClass().getMethod(methodName);
                    Object name = getName.invoke(plugin);
                    if (name instanceof String) {
                        return (String) name;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Continuer
                }
            }
            
            // Essayer d'obtenir depuis les champs
            try {
                Field[] fields = plugin.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (field.getType() == String.class && 
                        (field.getName().equals("name") || field.getName().equals("pluginName") || field.getName().equals("id"))) {
                        field.setAccessible(true);
                        Object value = field.get(plugin);
                        if (value instanceof String) {
                            return (String) value;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Ignorer
            }
            
        } catch (Exception ignored) {
            // Ignorer
        }
        
        return null;
    }
    
    /**
     * Vérifie si un mod est chargé en comparant son nom avec la liste des mods chargés.
     * Gère les variations de nom (avec/sans version, etc.).
     */
    private static boolean isModLoaded(@Nonnull String modDirName, @Nonnull java.util.Set<String> loadedModNames) {
        // Vérification exacte
        if (loadedModNames.contains(modDirName)) {
            return true;
        }
        
        // Vérification partielle (le nom du dossier peut contenir la version, ex: "ModName1.0.6")
        for (String loadedName : loadedModNames) {
            // Si le nom chargé est contenu dans le nom du dossier ou vice versa
            if (modDirName.contains(loadedName) || loadedName.contains(modDirName)) {
                return true;
            }
            
            // Extraire le nom de base (sans version) pour comparaison
            String baseModName = extractBaseModName(modDirName);
            String baseLoadedName = extractBaseModName(loadedName);
            if (baseModName.equals(baseLoadedName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extrait le nom de base d'un mod (sans version).
     * Améliore la reconnaissance des patterns: "Mod_v1.2.3", "Mod-1.2.3", "Mod1.2.3", "Mod_1.2.3"
     */
    @Nonnull
    private static String extractBaseModName(@Nonnull String modName) {
        // Nettoyer les extensions
        String cleaned = modName.replaceAll("\\.(zip|jar)$", "");
        
        // Pattern 1: Version avec préfixe "v" ou "V" (ex: "Mod_v1.2.3", "ModV1.0")
        cleaned = cleaned.replaceAll("[_-]?[vV]\\d+\\.\\d+(\\.\\d+)?$", "");
        
        // Pattern 2: Version avec séparateurs (ex: "Mod_1.2.3", "Mod-1.0.6")
        cleaned = cleaned.replaceAll("[_-]\\d+\\.\\d+(\\.\\d+)?$", "");
        
        // Pattern 3: Version collée (ex: "Mod1.2.3", "Mod1.0")
        cleaned = cleaned.replaceAll("\\d+\\.\\d+(\\.\\d+)?$", "");
        
        // Pattern 4: Version sans points (ex: "Mod123", "Mod10")
        // Attention: on garde seulement si 2+ chiffres pour éviter de retirer des noms comme "Mod1"
        // cleaned = cleaned.replaceAll("\\d{2,}$", "");
        
        // Nettoyer les tirets/underscores finaux
        cleaned = cleaned.replaceAll("[_-]+$", "");
        
        return cleaned.trim();
    }
    
    /**
     * Obtient le dossier Mods global avec détection multi-plateforme et logging détaillé.
     * Priority: CustomGlobalModsPath > Windows AppData > Linux/Mac paths
     */
    @Nullable
    private static Path getGlobalModsDirectory() {
        java.util.List<String> attemptedPaths = new java.util.ArrayList<>();
        
        logEssential("=== Starting Global Mods Directory Detection ===");
        
        // Priority 1: Custom path from config
        if (customGlobalModsPath != null && !customGlobalModsPath.isEmpty()) {
            try {
                Path customPath = Paths.get(customGlobalModsPath);
                attemptedPaths.add("[CUSTOM CONFIG] " + customPath.toAbsolutePath());
                
                if (Files.exists(customPath) && Files.isDirectory(customPath)) {
                    logEssential("✓ SUCCESS: Found global mods directory at custom path: " + customPath.toAbsolutePath());
                    return customPath;
                } else {
                    logEssential("✗ FAILED: Custom path does not exist or is not a directory: " + customPath.toAbsolutePath());
                }
            } catch (Exception e) {
                logEssential("✗ FAILED: Error accessing custom global mods path: " + e.getMessage());
            }
        } else {
            logEssential("No custom global mods path configured (CustomGlobalModsPath is empty)");
        }
        
        // Priority 2: Platform-specific default paths
        String userHome = System.getProperty("user.home");
        String osName = System.getProperty("os.name").toLowerCase();
        
        if (userHome != null) {
            java.util.List<Path> pathsToTry = new java.util.ArrayList<>();
            
            if (osName.contains("win")) {
                // Windows paths
                pathsToTry.add(Paths.get(userHome, "AppData", "Roaming", "Hytale", "UserData", "Mods"));
                pathsToTry.add(Paths.get(userHome, "AppData", "Local", "Hytale", "UserData", "Mods"));
                attemptedPaths.add("[WINDOWS ROAMING] " + pathsToTry.get(0).toAbsolutePath());
                attemptedPaths.add("[WINDOWS LOCAL] " + pathsToTry.get(1).toAbsolutePath());
            } else if (osName.contains("mac") || osName.contains("darwin")) {
                // macOS paths
                pathsToTry.add(Paths.get(userHome, "Library", "Application Support", "Hytale", "UserData", "Mods"));
                pathsToTry.add(Paths.get(userHome, ".hytale", "UserData", "Mods"));
                attemptedPaths.add("[MACOS APP SUPPORT] " + pathsToTry.get(0).toAbsolutePath());
                attemptedPaths.add("[MACOS HIDDEN] " + pathsToTry.get(1).toAbsolutePath());
            } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
                // Linux paths
                pathsToTry.add(Paths.get(userHome, ".local", "share", "Hytale", "UserData", "Mods"));
                pathsToTry.add(Paths.get(userHome, ".hytale", "UserData", "Mods"));
                attemptedPaths.add("[LINUX LOCAL SHARE] " + pathsToTry.get(0).toAbsolutePath());
                attemptedPaths.add("[LINUX HIDDEN] " + pathsToTry.get(1).toAbsolutePath());
            }
            
            // Try all platform-specific paths
            for (Path path : pathsToTry) {
                try {
                    if (Files.exists(path) && Files.isDirectory(path)) {
                        logEssential("✓ SUCCESS: Found global mods directory at: " + path.toAbsolutePath());
                        return path;
                    } else {
                        logVerbose("✗ Path does not exist: " + path.toAbsolutePath());
                    }
                } catch (Exception e) {
                    logVerbose("✗ Error checking path: " + path.toAbsolutePath() + " - " + e.getMessage());
                }
            }
        }
        
        // Priority 3: Try relative to current directory (for dedicated servers)
        try {
            Path currentDir = Paths.get(System.getProperty("user.dir"));
            Path[] relativePaths = {
                currentDir.resolve("Mods"),
                currentDir.resolve("mods"),
                currentDir.resolve("UserData").resolve("Mods")
            };
            
            for (Path path : relativePaths) {
                attemptedPaths.add("[RELATIVE] " + path.toAbsolutePath());
                if (Files.exists(path) && Files.isDirectory(path)) {
                    logEssential("✓ SUCCESS: Found global mods directory (relative): " + path.toAbsolutePath());
                    return path;
                }
            }
        } catch (Exception e) {
            logVerbose("Error trying relative paths: " + e.getMessage());
        }
        
        // All detection methods failed
        logEssential("=== Global Mods Directory Detection FAILED ===");
        logEssential("");
        logEssential("Could not find the global Mods directory for external mod scanning.");
        logEssential("Attempted paths:");
        for (String path : attemptedPaths) {
            logEssential("  - " + path);
        }
        logEssential("");
        logEssential("To fix this, set 'CustomGlobalModsPath' in your config:");
        logEssential("  Windows: \"CustomGlobalModsPath\": \"C:/Users/YourName/AppData/Roaming/Hytale/UserData/Mods\"");
        logEssential("  Linux:   \"CustomGlobalModsPath\": \"/home/username/.local/share/Hytale/UserData/Mods\"");
        logEssential("  macOS:   \"CustomGlobalModsPath\": \"/Users/username/Library/Application Support/Hytale/UserData/Mods\"");
        logEssential("");
        logEssential("Note: External mod compatibility will be disabled without this directory.");
        logEssential("═══════════════════════════════════════════════════════════════════");
        logEssential("");
        
        return null;
    }
    
    /**
     * Scanne un dossier de mod pour trouver des items (armes, armures, outils).
     * @param itemsDir Le dossier Server/Item/Items du mod
     * @param modItems La map où ajouter les items trouvés
     * @param modName Le nom du mod (pour les logs)
     * @return Le nombre d'items trouvés
     */
    private static int scanModDirectoryForItems(@Nonnull Path itemsDir, @Nonnull Map<String, Item> modItems, @Nonnull String modName) {
        int count = 0;
        int scannedFiles = 0;
        
        try {
            // Parcourir récursivement tous les fichiers JSON
            try (java.util.stream.Stream<Path> files = Files.walk(itemsDir)) {
                for (Path jsonFile : files.filter(Files::isRegularFile)
                                         .filter(p -> p.toString().endsWith(".json"))
                                         .collect(java.util.stream.Collectors.toList())) {
                    
                    scannedFiles++;
                    try {
                        // Lire le JSON
                        Map<String, Object> jsonData = readJsonFromFile(jsonFile);
                        if (jsonData == null || jsonData.isEmpty()) {
                            continue;
                        }
                        
                        // Extraire l'ID de l'item (nom du fichier sans extension)
                        String fileName = jsonFile.getFileName().toString();
                        String itemId = fileName.substring(0, fileName.length() - 5); // Enlever ".json"
                        
                        // Vérifier si c'est une arme, armure ou outil
                        if (isWeaponArmorOrTool(jsonData)) {
                            // Chercher l'item dans la map Item globale
                            Item item = findItemById(itemId);
                            if (item != null && item != Item.UNKNOWN) {
                                modItems.put(itemId, item);
                                // Stocker le chemin source pour pouvoir le relire plus tard
                                modItemSourcePaths.put(itemId, jsonFile);
                                count++;
                            }
                        }
                    } catch (Exception e) {
                        // Ignore errors silently
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors silently
        }
        
        return count;
    }
    
    /**
     * Lit un fichier JSON depuis le système de fichiers.
     */
    @Nullable
    private static Map<String, Object> readJsonFromFile(@Nonnull Path jsonFile) {
        try {
            String content = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
            return GSON.fromJson(content, typeToken.getType());
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Lit le JSON d'un item depuis différentes sources (mods externes, HytaleAssets/Assets.zip comme dossier, ou Assets.zip comme fichier).
     */
    @Nullable
    private static Map<String, Object> readJsonForItem(@Nonnull String itemId) {
        // D'abord, essayer de lire depuis un mod externe si on a le chemin source
        Path modSourcePath = modItemSourcePaths.get(itemId);
        if (modSourcePath != null && Files.exists(modSourcePath)) {
            // Vérifier si c'est un fichier ZIP ou un fichier JSON normal
            String fileName = modSourcePath.getFileName().toString();
            if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
                // C'est un fichier ZIP, lire depuis le ZIP
                Map<String, Object> jsonData = readJsonFromModZip(modSourcePath, itemId);
                if (jsonData != null && !jsonData.isEmpty()) {
                    return jsonData;
                }
            } else {
                // C'est un fichier JSON normal
                Map<String, Object> jsonData = readJsonFromFile(modSourcePath);
                if (jsonData != null && !jsonData.isEmpty()) {
                    return jsonData;
                }
            }
        } else if (modSourcePath != null) {
            logEssential("Source path stored but file not found for " + itemId + ": " + modSourcePath);
        }
        
        // Ensuite, essayer depuis HytaleAssets (dossier parent de mods)
        Map<String, Object> hytaleAssetsResult = readJsonFromHytaleAssets(itemId);
        if (hytaleAssetsResult != null) {
            return hytaleAssetsResult;
        }
        
        // Sinon, essayer depuis Assets.zip
        return readJsonFromAssetsZip(itemId);
    }
    
    /**
     * Vérifie si un JSON représente une arme, armure ou outil.
     */
    private static boolean isWeaponArmorOrTool(@Nonnull Map<String, Object> jsonData) {
        // Vérifier la présence de champs caractéristiques
        if (jsonData.containsKey("Weapon") || jsonData.containsKey("InteractionVars")) {
            return true; // Arme
        }
        if (jsonData.containsKey("Armor")) {
            return true; // Armure
        }
        if (jsonData.containsKey("Tool")) {
            return true; // Outil (pickaxes, hatchets, etc.)
        }
        if (jsonData.containsKey("BlockSelectorTool")) {
            return true; // Outil avec BlockSelectorTool (hammers, shovels, multitools, etc.)
        }
        return false;
    }
    
    /**
     * Trouve un Item par son ID dans la map globale des items.
     */
    @Nullable
    private static Item findItemById(@Nonnull String itemId) {
        try {
            Object assetMap = QualityVariantBootstrap.getItemAssetMapRaw();
            if (assetMap == null) {
                return null;
            }
            
            Map<String, Item> itemMap = QualityVariantBootstrap.resolveMutableMap(assetMap);
            if (itemMap == null) {
                return null;
            }
            
            return itemMap.get(itemId);
        } catch (Exception e) {
            return null;
        }
    }
    
}
