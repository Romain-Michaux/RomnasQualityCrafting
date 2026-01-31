package dev.hytalemodding.quality;

import dev.hytalemodding.config.RomnasQualityCraftingConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages access to the quality configuration from static contexts.
 */
public class QualityConfigManager {
    
    private static RomnasQualityCraftingConfig config = null;
    private static List<String> excludedIdPrefixes = null;
    private static List<String> excludedItems = null;
    private static JavaPlugin plugin = null;
    
    /**
     * Initializes the configuration manager with the config data.
     * Should be called from the plugin's setup() method.
     * @param configData The configuration data
     * @param pluginInstance The plugin instance (for finding config file path)
     */
    public static void initialize(@Nullable RomnasQualityCraftingConfig configData, @Nullable JavaPlugin pluginInstance) {
        config = configData;
        plugin = pluginInstance;
        // Load excluded lists from JSON file
        loadExcludedListsFromJson();
    }
    
    /**
     * Initializes the configuration manager with the config data (backward compatibility).
     * Should be called from the plugin's setup() method.
     * @param configData The configuration data
     */
    public static void initialize(@Nullable RomnasQualityCraftingConfig configData) {
        initialize(configData, null);
    }
    
    /**
     * Loads excluded lists from the JSON config file.
     */
    private static void loadExcludedListsFromJson() {
        try {
            // Try to find the config file
            Path configPath = findConfigFile();
            if (configPath == null || !Files.exists(configPath)) {
                // Use defaults if file not found
                excludedIdPrefixes = getDefaultExcludedIdPrefixes();
                excludedItems = getDefaultExcludedItems();
                return;
            }
            
            Gson gson = new Gson();
            JsonObject json = gson.fromJson(new FileReader(configPath.toFile()), JsonObject.class);
            
            // Load ExcludedIdPrefixes
            if (json.has("ExcludedIdPrefixes") && json.get("ExcludedIdPrefixes").isJsonArray()) {
                JsonArray array = json.getAsJsonArray("ExcludedIdPrefixes");
                excludedIdPrefixes = new ArrayList<>();
                for (JsonElement element : array) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        excludedIdPrefixes.add(element.getAsString());
                    }
                }
            } else {
                excludedIdPrefixes = getDefaultExcludedIdPrefixes();
            }
            
            // Load ExcludedItems
            if (json.has("ExcludedItems") && json.get("ExcludedItems").isJsonArray()) {
                JsonArray array = json.getAsJsonArray("ExcludedItems");
                excludedItems = new ArrayList<>();
                for (JsonElement element : array) {
                    if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        excludedItems.add(element.getAsString());
                    }
                }
            } else {
                excludedItems = getDefaultExcludedItems();
            }
        } catch (Exception e) {
            // On error, use defaults
            excludedIdPrefixes = getDefaultExcludedIdPrefixes();
            excludedItems = getDefaultExcludedItems();
        }
    }
    
    /**
     * Tries to find the config file in various locations.
     */
    @Nullable
    private static Path findConfigFile() {
        // First, try to get the config file from the plugin's data folder
        if (plugin != null) {
            try {
                java.lang.reflect.Method getDataFolder = plugin.getClass().getMethod("getDataFolder");
                File dataFolder = (File) getDataFolder.invoke(plugin);
                if (dataFolder != null) {
                    File configFile = new File(dataFolder, "RomnasQualityCrafting.json");
                    if (configFile.exists()) {
                        return configFile.toPath();
                    }
                }
            } catch (Exception e) {
                // Ignore and try other methods
            }
        }
        
        // Try common config locations
        String[] possiblePaths = {
            "config/RomnasQualityCrafting.json",
            "RomnasQualityCrafting.json",
            System.getProperty("user.dir") + "/config/RomnasQualityCrafting.json"
        };
        
        for (String pathStr : possiblePaths) {
            Path path = Paths.get(pathStr);
            if (Files.exists(path)) {
                return path;
            }
        }
        
        return null;
    }
    
    /**
     * Gets the current configuration.
     * @return The configuration, or null if not initialized
     */
    @Nullable
    public static RomnasQualityCraftingConfig getConfig() {
        return config;
    }
    
    /**
     * Gets the excluded ID prefixes list.
     * @return The list of excluded ID prefixes
     */
    @Nonnull
    public static List<String> getExcludedIdPrefixes() {
        return excludedIdPrefixes != null ? excludedIdPrefixes : getDefaultExcludedIdPrefixes();
    }
    
    /**
     * Gets the excluded items list.
     * @return The list of excluded items
     */
    @Nonnull
    public static List<String> getExcludedItems() {
        return excludedItems != null ? excludedItems : getDefaultExcludedItems();
    }
    
    // Default excluded ID prefixes
    private static List<String> getDefaultExcludedIdPrefixes() {
        List<String> defaults = new ArrayList<>();
        defaults.add("Bench_");
        defaults.add("Weapon_Arrow_");
        defaults.add("Weapon_Bomb_");
        defaults.add("Weapon_Dart_");
        defaults.add("Weapon_Grenade_");
        defaults.add("Weapon_Kunai_");
        defaults.add("Debug_");
        defaults.add("Test_");
        defaults.add("Template_");
        return defaults;
    }
    
    // Default excluded items
    private static List<String> getDefaultExcludedItems() {
        List<String> defaults = new ArrayList<>();
        defaults.add("Weapon_Bomb");
        defaults.add("Farming_Collar");
        defaults.add("Halloween_Broomstick");
        defaults.add("Tool_Feedbag");
        defaults.add("Tool_Fertilizer");
        defaults.add("Tool_Map");
        return defaults;
    }
}
