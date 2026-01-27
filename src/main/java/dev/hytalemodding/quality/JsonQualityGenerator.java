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
 * Les fichiers sont créés dans : {saveDir}/mods/dev.hytalemodding_RomnasQualityCrafting/Server/Item/Items/
 */
public final class JsonQualityGenerator {
    
    private static final String LOG_PREFIX = "[RomnasQualityCrafting] JsonGenerator: ";
    private static final String GENERATED_MOD_NAME = "RomnasQualityCraftingGenerated";
    
    // Map pour stocker le chemin source de chaque item trouvé dans les mods
    private static final Map<String, Path> modItemSourcePaths = new java.util.HashMap<>();
    
    // Configuration pour les logs détaillés (chargée depuis le fichier de config)
    private static boolean verboseLogging = false;
    
    /**
     * Initializes logging configuration from the config object.
     * @param plugin The plugin instance
     * @param configData The configuration data object
     */
    public static void initializeLogging(@Nonnull JavaPlugin plugin, @Nonnull dev.hytalemodding.config.RomnasQualityCraftingConfig configData) {
        // Note: VerboseLogging has been removed from config, keeping this method for compatibility
        // but it no longer does anything since we only have quality weights now
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
     * @return true si la génération a eu lieu, false si le mod existait déjà
     */
    public static boolean generateJsonFiles(@Nonnull JavaPlugin plugin, @Nonnull Map<String, Item> baseItems, @Nullable Object bootEvent) {
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
            return false;
        }
        
        logEssential("Starting quality variant generation...");
        
        // Scan external mods BEFORE generation to find items and their source files
        Map<String, Item> modItems = scanExternalModsForItems(plugin);
        if (!modItems.isEmpty()) {
            logVerbose("Found " + modItems.size() + " item(s) in external mods, adding to generation list.");
            logVerbose("modItemSourcePaths contains " + modItemSourcePaths.size() + " source path(s).");
            // Add mod items to the base items map
            baseItems.putAll(modItems);
        }
        
        // Find the plugin's mod directory (e.g., dev.hytalemodding_RomnasQualityCrafting)
        Path pluginModDir = getPluginModDirectory(plugin, modsDir);
        if (pluginModDir == null) {
            logEssential("Unable to find plugin mod directory in: " + modsDir);
            return false;
        }
        
        Path itemsDir = pluginModDir.resolve("Server").resolve("Item").resolve("Items");
        
        // Check if files already exist
        if (Files.exists(itemsDir)) {
            // Check if there are files in the Items directory
            try {
                long fileCount = Files.list(itemsDir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .count();
                
                if (fileCount > 0) {
                    logEssential("Generated mod already exists (" + fileCount + " item file(s)). Generation skipped.");
                    return false;
                }
            } catch (IOException e) {
                logVerbose("Error checking existing files: " + e.getMessage());
                // Continue with generation on error
            }
        }
        
        try {
            // Create directory if necessary
            Files.createDirectories(itemsDir);
            logVerbose("Directory created: " + itemsDir);
            
            // Always create/update manifest.json to ensure it's valid
            createManifestFile(pluginModDir);
        } catch (IOException e) {
            logEssential("Error creating directory: " + e.getMessage());
            return false;
        }
        
        // Delete all existing JSON files to regenerate on each startup
        try {
            if (Files.exists(itemsDir)) {
                java.util.List<Path> filesToDelete = new java.util.ArrayList<>();
                try (java.util.stream.Stream<Path> stream = Files.walk(itemsDir)) {
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
                    logVerbose("Deleted " + filesToDelete.size() + " existing JSON file(s) before regeneration");
                }
            }
        } catch (IOException e) {
            logVerbose("Error deleting existing files: " + e.getMessage());
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
                        writeJsonFile(jsonFile, jsonData);
                        generatedCount++;
                        if (generatedCount % 50 == 0) {
                            logVerbose("Progress: " + generatedCount + " file(s) created...");
                        }
                    } else {
                        errorCount++;
                        logVerbose("Failed: jsonData is null or empty for " + qualityId);
                    }
                } catch (Exception e) {
                    errorCount++;
                    logVerbose("Error generating " + qualityId + ": " + e.getMessage());
                    if (verboseLogging) {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        logEssential("Generation completed: " + generatedCount + " JSON file(s) created, " + errorCount + " error(s)");
        
        // Générer les SalvageRecipe pour chaque qualité
        generateSalvageRecipes(plugin, baseItems, pluginModDir);
        
        // Générer les drops modifiés avec les qualités
        generateQualityDrops(plugin, baseItems, pluginModDir);
        
        return true;
    }
    
    /**
     * Génère les variantes de qualité pour une liste spécifique d'items.
     */
    private static void generateQualityVariantsForItems(@Nonnull Map<String, Item> allItems, @Nonnull Path itemsDir, @Nonnull java.util.Set<String> itemIds) {
        int generatedCount = 0;
        int errorCount = 0;
        
        for (String baseId : itemIds) {
            Item baseItem = allItems.get(baseId);
            if (baseItem == null || baseItem == Item.UNKNOWN) {
                continue;
            }
            
            // Générer les 6 variantes de qualité
            for (ItemQuality quality : ItemQuality.values()) {
                String qualityId = baseId + "_" + quality.name();
                try {
                    Map<String, Object> jsonData = createQualityJson(baseItem, baseId, qualityId, quality);
                    if (jsonData != null && !jsonData.isEmpty()) {
                        Path qualityFilePath = itemsDir.resolve(qualityId + ".json");
                        writeJsonFile(qualityFilePath, jsonData);
                        generatedCount++;
                    } else {
                        errorCount++;
                    }
                } catch (Exception e) {
                    errorCount++;
                    logVerbose("Error generating " + qualityId + ": " + e.getMessage());
                }
            }
        }
        
        if (generatedCount > 0 || errorCount > 0) {
            logVerbose("External mods generation: " + generatedCount + " JSON file(s) created, " + errorCount + " error(s)");
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
                logVerbose("Unable to read JSON for " + baseId + ", skipping.");
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
            modifyWeaponDagimage(jsonData, baseItem, quality.getDamageMultiplier());
            
            // Modifier la puissance des outils
            modifyToolPower(jsonData, baseItem, quality.getDamageMultiplier());
            
            // Modifier les stats d'armure
            modifyArmorStats(jsonData, baseItem, quality.getDamageMultiplier());
            
            removeRecipe(jsonData);

            // Normaliser tous les nombres dans le JSON (convertir les doubles en entiers si approprié)
            normalizeJsonNumbers(jsonData);
            
            return jsonData;
        } catch (Exception e) {
            logVerbose("Error creating JSON for " + qualityId + ": " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static void removeRecipe(@Nonnull Map<String, Object> jsonData) {
        jsonData.remove("Recipe");
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
     * Crée le JSON via le codec d'Hytale (méthode préférée).
     */
    @Nullable
    private static Map<String, Object> createQualityJsonViaCodec(@Nonnull Item baseItem, @Nonnull String baseId) {
        try {
            Object encoded = encodeItemViaCodec(baseItem);
            if (encoded == null) {
                return null;
            }
            
            // Convertir l'objet encodé en Map
            return convertEncodedToMap(encoded);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Construit le JSON manuellement en utilisant la réflexion pour lire les champs de l'item.
     * Cette méthode évite les problèmes de sérialisation avec Gson.
     */
    @Nullable
    private static Map<String, Object> buildJsonManually(@Nonnull Item baseItem, @Nonnull String baseId) {
        Map<String, Object> jsonData = new java.util.LinkedHashMap<>();
        
        try {
            // Lire les champs publics de l'item via réflexion
            Class<?> itemClass = baseItem.getClass();
            
            // Lire tous les champs déclarés
            for (Field field : itemClass.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(baseItem);
                    
                    // Ignorer les valeurs null et les types problématiques
                    if (value == null) continue;
                    
                    String fieldName = field.getName();
                    String fieldType = field.getType().getName();
                    
                    // Ignorer les champs internes et problématiques
                    if (fieldType.contains("SoftReference") || fieldType.contains("WeakReference") ||
                        fieldType.contains("Reference") || fieldType.contains("Thread") ||
                        fieldType.contains("ClassLoader") || fieldType.contains("Bson") ||
                        fieldName.equals("timestamp") || fieldName.equals("clock") ||
                        fieldName.equals("ref") || fieldName.startsWith("$") ||
                        fieldName.equals("serialVersionUID")) {
                        continue;
                    }
                    
                    // Convertir la valeur en type JSON-compatible
                    Object jsonValue = convertToJsonValue(value);
                    if (jsonValue != null) {
                        // Utiliser le nom du champ avec la première lettre en majuscule (convention JSON Hytale)
                        String jsonKey = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
                        jsonData.put(jsonKey, jsonValue);
                    }
                } catch (Exception e) {
                    // Ignorer les champs qui ne peuvent pas être lus
                    continue;
                }
            }
            
            // Lire aussi les méthodes getter publiques
            for (Method method : itemClass.getMethods()) {
                try {
                    String methodName = method.getName();
                    if (methodName.startsWith("get") && method.getParameterCount() == 0 && 
                        methodName.length() > 3 && !methodName.equals("getClass")) {
                        
                        Object value = method.invoke(baseItem);
                        if (value == null) continue;
                        
                        String fieldName = methodName.substring(3);
                        String returnType = method.getReturnType().getName();
                        
                        // Ignorer les types problématiques
                        if (returnType.contains("SoftReference") || returnType.contains("WeakReference") ||
                            returnType.contains("Reference") || returnType.contains("Thread") ||
                            returnType.contains("ClassLoader") || returnType.contains("Bson")) {
                            continue;
                        }
                        
                        Object jsonValue = convertToJsonValue(value);
                        if (jsonValue != null) {
                            jsonData.put(fieldName, jsonValue);
                        }
                    }
                } catch (Exception e) {
                    // Ignorer les méthodes qui ne peuvent pas être appelées
                    continue;
                }
            }
            
            return jsonData.isEmpty() ? null : jsonData;
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convertit une valeur Java en valeur JSON-compatible.
     */
    @Nullable
    private static Object convertToJsonValue(@Nullable Object value) {
        if (value == null) return null;
        
        // Types primitifs et String
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        
        // Collections
        if (value instanceof java.util.List) {
            java.util.List<Object> result = new java.util.ArrayList<>();
            for (Object item : (java.util.List<?>) value) {
                Object converted = convertToJsonValue(item);
                if (converted != null) {
                    result.add(converted);
                }
            }
            return result.isEmpty() ? null : result;
        }
        
        if (value instanceof java.util.Map) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) value).entrySet()) {
                Object key = entry.getKey();
                Object val = entry.getValue();
                if (key != null && val != null) {
                    Object convertedVal = convertToJsonValue(val);
                    if (convertedVal != null) {
                        result.put(key.toString(), convertedVal);
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }
        
        // Essayer de sérialiser avec Gson pour les objets complexes
        try {
            String json = GSON.toJson(value);
            return GSON.fromJson(json, Object.class);
        } catch (Exception e) {
            // Si la sérialisation échoue, retourner null
            return null;
        }
    }
    
    /**
     * Encode un item en utilisant le codec d'Hytale.
     */
    @Nullable
    private static Object encodeItemViaCodec(@Nonnull Item item) {
        try {
            Field codecField = null;
            for (Field f : Item.class.getDeclaredFields()) {
                String n = f.getName().toUpperCase();
                if ((n.equals("CODEC") || n.equals("ABSTRACT_CODEC")) && f.getType().getName().contains("Codec")) {
                    codecField = f;
                    break;
                }
            }
            if (codecField == null) return null;
            codecField.setAccessible(true);
            Object codec = codecField.get(null);
            if (codec == null) return null;
            
            // Chercher la méthode encode
            Method encodeMethod = null;
            for (Method m : codec.getClass().getMethods()) {
                if (m.getName().equals("encode") && m.getParameterCount() == 1) {
                    encodeMethod = m;
                    break;
                }
            }
            if (encodeMethod == null) return null;
            
            return encodeMethod.invoke(codec, item);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convertit un objet encodé (BsonDocument, Map, etc.) en Map<String, Object> pour modification.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Object> convertEncodedToMap(@Nonnull Object encoded) {
        // Si c'est déjà une Map
        if (encoded instanceof Map) {
            return (Map<String, Object>) encoded;
        }
        
        // Si c'est un BsonDocument, convertir récursivement en Map
        if (encoded.getClass().getName().contains("BsonDocument")) {
            try {
                return convertBsonDocumentToMap(encoded);
            } catch (Exception e) {
                // Si la conversion récursive échoue, essayer avec toJson()
                try {
                    Method toJsonMethod = encoded.getClass().getMethod("toJson");
                    Object jsonResult = toJsonMethod.invoke(encoded);
                    String json = jsonResult != null ? jsonResult.toString() : encoded.toString();
                    return GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        
        // Essayer de sérialiser avec Gson puis désérialiser en Map
        try {
            String json = GSON.toJson(encoded);
            return GSON.fromJson(json, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            return null;
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
                manifest.put("Name", "RomnasQualityCrafting");
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
     * Trouve le chemin vers Assets.zip dans le dossier d'installation de Hytale.
     */
    @Nullable
    private static Path findAssetsZipPath() {
        try {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                // Chemin Windows typique : C:\Users\{user}\AppData\Roaming\Hytale\install\release\package\game\latest\Assets.zip
                Path assetsZipPath = Paths.get(userHome, "AppData", "Roaming", "Hytale", "install", "release", "package", "game", "latest", "Assets.zip");
                if (Files.exists(assetsZipPath)) {
                    return assetsZipPath;
                }
            }
        } catch (Exception e) {
            logVerbose("Error searching for Assets.zip: " + e.getMessage());
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
     * Lit une recette de salvage depuis Assets.zip.
     * @param itemId L'ID de l'item (ex: "Weapon_Sword_Copper")
     * @return Le contenu JSON parsé en Map, ou null si le fichier n'existe pas
     */
    @Nullable
    private static Map<String, Object> readSalvageRecipeFromAssetsZip(@Nonnull String itemId) {
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
     * Lit tous les drops depuis Assets.zip et remplace les items par des Choice avec les 6 qualités.
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
        
        // Read all drops from Assets.zip
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
                                logVerbose("Mods directory found: " + modsPath);
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
                                        logVerbose("Mods directory found: " + modsPath);
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
                            logVerbose("Mods directory found via getDataFolder: " + modsPath);
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
                                logVerbose("Mods directory found: " + modsPath);
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
                                    logVerbose("Mods directory found: " + modsPath);
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
                            logVerbose("Mods directory found via system property (most recent save): " + modsPath);
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
                            logVerbose("Mods directory found: " + modsPath);
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
     * Looks for directories matching patterns like "dev.hytalemodding_RomnasQualityCrafting".
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
                            Files.exists(currentPath.resolve("RomnasQualityCrafting.json"))) {
                            logVerbose("Found plugin mod directory via getDataFolder: " + currentPath);
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
        try {
            String[] possibleNames = {
                "dev.hytalemodding_RomnasQualityCrafting",
                "dev.hytalemodding.RomnasQualityCrafting",
                "RomnasQualityCrafting"
            };
            
            for (String name : possibleNames) {
                Path testPath = modsDir.resolve(name);
                if (Files.exists(testPath) && Files.isDirectory(testPath)) {
                    // Check if it looks like our mod directory (has config file or Server folder)
                    if (Files.exists(testPath.resolve("RomnasQualityCrafting.json")) ||
                        Files.exists(testPath.resolve("manifest.json")) ||
                        Files.exists(testPath.resolve("Server"))) {
                        logVerbose("Found plugin mod directory by name: " + testPath);
                        return testPath;
                    }
                }
            }
            
            // Method 3: Search all directories in modsDir for one containing our config file
            try (java.util.stream.Stream<Path> stream = Files.list(modsDir)) {
                for (Path modPath : stream.filter(Files::isDirectory).collect(java.util.stream.Collectors.toList())) {
                    String modName = modPath.getFileName().toString();
                    // Check if this directory contains our config file
                    if (Files.exists(modPath.resolve("RomnasQualityCrafting.json"))) {
                        logVerbose("Found plugin mod directory by config file: " + modPath);
                        return modPath;
                    }
                    // Also check if it contains "RomnasQualityCrafting" in the name
                    if (modName.contains("RomnasQualityCrafting") || modName.contains("Romnas")) {
                        if (Files.exists(modPath.resolve("manifest.json")) || 
                            Files.exists(modPath.resolve("Server"))) {
                            logVerbose("Found plugin mod directory by name pattern: " + modPath);
                            return modPath;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logVerbose("Error searching for plugin mod directory: " + e.getMessage());
        }
        
        // Method 4: If not found, create it with the most likely name
        Path fallbackPath = modsDir.resolve("dev.hytalemodding_RomnasQualityCrafting");
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
        
        logVerbose("=== Starting external mods scan ===");
        
        try {
            // Get the list of actually loaded mods
            java.util.Set<String> loadedModNames = getLoadedModNames(plugin);
            logVerbose("Loaded mods detected: " + loadedModNames.size() + " mod(s)");
            if (!loadedModNames.isEmpty()) {
                logVerbose("List of loaded mods: " + String.join(", ", loadedModNames));
            } else {
                logVerbose("No loaded mods detected via detection methods.");
            }
            
            // Get the global Mods directory (not the save's mods)
            Path globalModsDir = getGlobalModsDirectory();
            if (globalModsDir == null || !Files.exists(globalModsDir)) {
                logVerbose("Global Mods directory not found, skipping external mods scan.");
                logVerbose("Expected path: " + (globalModsDir != null ? globalModsDir.toString() : "null"));
                return modItems;
            }
            
            logVerbose("Global Mods directory found: " + globalModsDir);
            
            if (!loadedModNames.isEmpty()) {
                logVerbose("Scanning loaded external mods (" + loadedModNames.size() + " mod(s)) in: " + globalModsDir);
            } else {
                logVerbose("No loaded mods detected, scanning all available mods in: " + globalModsDir);
            }
            
            // Parcourir les mods (peuvent être des dossiers ou des fichiers ZIP/JAR)
            int modsScanned = 0;
            try (java.util.stream.Stream<Path> modPaths = Files.list(globalModsDir)) {
                java.util.List<Path> modPathsList = modPaths.collect(java.util.stream.Collectors.toList());
                logVerbose("Number of elements found in Mods directory: " + modPathsList.size());
                
                // List all elements for debug
                for (Path path : modPathsList) {
                    logVerbose("  Element found: " + path.getFileName() + " (directory: " + Files.isDirectory(path) + ", file: " + Files.isRegularFile(path) + ")");
                }
                
                for (Path modPath : modPathsList) {
                    String modName = modPath.getFileName().toString();
                    modsScanned++;
                    
                    // Mods can be directories or ZIP/JAR files
                    if (Files.isDirectory(modPath)) {
                        // It's a directory, we can scan directly
                        Path modDir = modPath;
                        processModDirectory(modDir, modName, loadedModNames, modItems);
                    } else if (modName.endsWith(".zip") || modName.endsWith(".jar")) {
                        // It's a ZIP/JAR file, we need to extract or scan it as a ZIP
                        logVerbose("Processing ZIP/JAR mod: " + modName);
                        processModZipFile(modPath, modName, loadedModNames, modItems);
                    } else {
                        logVerbose("Element ignored (not a directory/ZIP/JAR): " + modName);
                    }
                }
            } catch (Exception e) {
                logVerbose("Error traversing mods: " + e.getMessage());
                if (verboseLogging) {
                    e.printStackTrace();
                }
            }
            logVerbose("Scan completed: " + modsScanned + " mod(s) examined, " + modItems.size() + " item(s) found total");
        } catch (Exception e) {
            logVerbose("Error scanning external mods: " + e.getMessage());
        }
        
        return modItems;
    }
    
    /**
     * Traite un mod qui est un dossier.
     */
    private static void processModDirectory(@Nonnull Path modDir, @Nonnull String modName, 
                                           @Nonnull java.util.Set<String> loadedModNames, 
                                           @Nonnull Map<String, Item> modItems) {
        // Ignorer notre propre mod source (mais pas le dossier mods lui-même où on génère)
        if (modName.contains("RomnasQualityCrafting")) {
            logVerbose("  Mod '" + modName + "' ignored (source mod)");
            return;
        }
        
        // Si on a une liste de mods chargés, vérifier si ce mod est chargé
        if (!loadedModNames.isEmpty() && !isModLoaded(modName, loadedModNames)) {
            logVerbose("  Mod '" + modName + "' ignored (not loaded according to detection)");
            return;
        }
        
        // Chercher les items dans ce mod
        Path modItemsDir = modDir.resolve("Server").resolve("Item").resolve("Items");
        logVerbose("  Mod items path: " + modItemsDir + " (exists: " + Files.exists(modItemsDir) + ")");
        if (Files.exists(modItemsDir)) {
            logVerbose("  Scanning mod '" + modName + "'...");
            int itemsFound = scanModDirectoryForItems(modItemsDir, modItems, modName);
            if (itemsFound > 0) {
                logVerbose("  Mod '" + modName + "': " + itemsFound + " item(s) found");
            } else {
                logVerbose("  Mod '" + modName + "': no eligible items found");
            }
        } else {
            logVerbose("  Mod '" + modName + "': Server/Item/Items directory not found");
        }
    }
    
    /**
     * Traite un mod qui est un fichier ZIP/JAR.
     */
    private static void processModZipFile(@Nonnull Path zipPath, @Nonnull String modName,
                                         @Nonnull java.util.Set<String> loadedModNames,
                                         @Nonnull Map<String, Item> modItems) {
        // Ignorer notre propre mod
        if (modName.contains("RomnasQualityCrafting")) {
            logVerbose("  ZIP mod '" + modName + "' ignored (source mod)");
            return;
        }
        
        // Si on a une liste de mods chargés, vérifier si ce mod est chargé
        String baseModName = extractBaseModName(modName.replace(".zip", "").replace(".jar", ""));
        if (!loadedModNames.isEmpty() && !isModLoaded(baseModName, loadedModNames)) {
            logVerbose("  ZIP mod '" + modName + "' ignored (not loaded according to detection)");
            return;
        }
        
        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            logVerbose("  Scanning ZIP mod '" + modName + "'...");
            
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
                                        logVerbose("    Item found: " + itemId + " (file: " + fileName + ")");
                                    } else {
                                        logVerbose("    Item not found in Item map: " + itemId + " (file: " + fileName + ")");
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logVerbose("    Error reading " + entryName + ": " + e.getMessage());
                    }
                }
            }
            
            if (filesScanned > 0) {
                logVerbose("  ZIP mod '" + modName + "': " + filesScanned + " JSON file(s) scanned, " + itemsFound + " eligible item(s) found");
            }
        } catch (Exception e) {
            logVerbose("Error scanning ZIP mod '" + modName + "': " + e.getMessage());
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
            logVerbose("Error opening mod ZIP " + zipPath.getFileName() + ": " + e.getMessage());
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
     * Ex: "Wans_Wonder_Weapon1.0.6" -> "Wans_Wonder_Weapon"
     */
    @Nonnull
    private static String extractBaseModName(@Nonnull String modName) {
        // Enlever les numéros de version à la fin (ex: "1.0.6", "1.0", etc.)
        return modName.replaceAll("\\d+\\.\\d+(\\.\\d+)?$", "").trim();
    }
    
    /**
     * Obtient le dossier Mods global (C:\Users\{user}\AppData\Roaming\Hytale\UserData\Mods).
     */
    @Nullable
    private static Path getGlobalModsDirectory() {
        try {
            String userHome = System.getProperty("user.home");
            if (userHome != null) {
                Path globalModsDir = Paths.get(userHome).resolve("AppData").resolve("Roaming").resolve("Hytale").resolve("UserData").resolve("Mods");
                if (Files.exists(globalModsDir)) {
                    return globalModsDir;
                }
            }
        } catch (Exception ignored) {
            // Erreur silencieuse
        }
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
                                logVerbose("  Item found: " + itemId + " (file: " + fileName + ")");
                                    } else {
                                logVerbose("  Item not found in Item map: " + itemId + " (file: " + fileName + ")");
                            }
                        }
                    } catch (Exception e) {
                        logVerbose("  Error reading " + jsonFile.getFileName() + ": " + e.getMessage());
                    }
                }
            }
            
            if (scannedFiles > 0) {
                logVerbose("Mod '" + modName + "': " + scannedFiles + " JSON file(s) scanned, " + count + " eligible item(s) found");
            }
        } catch (Exception e) {
            logVerbose("Error scanning mod '" + modName + "': " + e.getMessage());
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
     * Lit le JSON d'un item depuis différentes sources (Assets.zip ou mods externes).
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
            logVerbose("Source path exists but file not found for " + itemId + ": " + modSourcePath);
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
            return true; // Outil
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
