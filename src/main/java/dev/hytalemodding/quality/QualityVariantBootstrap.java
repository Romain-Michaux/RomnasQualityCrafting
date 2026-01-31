package dev.hytalemodding.quality;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Au lancement du serveur, enregistre 6 variantes de qualité (Poor, Common, Uncommon, Rare, Epic, Legendary)
 * pour chaque arme, outil et armure chargée (jeu de base + autres mods), en les injectant dans la map
 * d’assets Item. Filtre : uniquement les items pour lesquels {@link QualityManager#canHaveQuality(Item)}
 * et dont l’id n’a pas déjà un suffixe de qualité.
 */
public final class QualityVariantBootstrap {

    private static final String LOG_PREFIX = "[RomnasQualityCrafting] QualityBootstrap: ";

    /** ID prefixes to ignore (benches, mod tools, etc.). */
    // Excluded lists are now loaded from config, with defaults as fallback

    private static boolean alreadyRun = false;
    private static boolean generationHappened = false;
    private static boolean generationFailed = false;
    private static String generationFailureReason = null;
    private static int generatedFileCount = 0;
    private static int errorFileCount = 0;

    /**
     * À appeler quand les assets Item sont chargés (ex. BootEvent). Pour chaque arme/outil/armure
     * sans variantes qualité déjà présentes, crée et enregistre 6 variantes dans la map Item.
     */
    public static void run(@Nullable JavaPlugin plugin) {
        run(plugin, null);
    }
    
    /**
     * Version avec BootEvent pour obtenir le chemin du monde.
     */
    public static void run(@Nullable JavaPlugin plugin, @Nullable Object bootEvent) {
        if (alreadyRun) {
            return;
        }
        // Don't set alreadyRun = true yet - only after successful generation or if files already exist
        

        Object assetMap = getItemAssetMapRaw();
        if (assetMap == null) {
            log("Impossible d’obtenir la map d’assets Item, abandon.");            generationFailed = true;
            generationFailureReason = "Unable to obtain Item asset map";            return;
        }

        Map<String, Item> mutableMap = resolveMutableMap(assetMap);
        if (mutableMap == null) {
            log("La map d’assets Item n’est pas modifiable, abandon.");            generationFailed = true;
            generationFailureReason = "Item asset map is not mutable";            return;
        }

        // Snapshot des ids uniquement : on n'itère pas sur la map pendant qu'on y écrit.
        List<String> baseIds = collectBaseIdsSnapshot(mutableMap);
        if (baseIds.isEmpty()) {
            alreadyRun = true; // Mark as run since there's nothing to generate
            return;
        }


        // Collecter les items de base pour la génération JSON
        Map<String, Item> baseItemsForJson = new LinkedHashMap<>();
        for (String baseId : baseIds) {
            Item base = mutableMap.get(baseId);
            if (base != null && base != Item.UNKNOWN) {
                baseItemsForJson.put(baseId, base);
            }
        }

        // Générer les fichiers JSON dans le dossier mods
        if (plugin != null && !baseItemsForJson.isEmpty()) {
            JsonQualityGenerator.GenerationResult result = JsonQualityGenerator.generateJsonFiles(plugin, baseItemsForJson, bootEvent);
            generationHappened = result.success;
            generationFailed = result.failed;
            generationFailureReason = result.failureReason;
            generatedFileCount = result.generatedCount;
            errorFileCount = result.errorCount;
            
            // Only mark as already run if generation succeeded or was skipped (files already exist)
            // If it failed, allow retry on next server start
            if (result.success || (!result.failed && !result.success)) {
                // Success or skipped (already exists)
                alreadyRun = true;
            } else {
                // Failed - don't mark as run so it retries next time
                alreadyRun = false;
            }
        }

        if (generationHappened) {
            System.out.println("[RomnasQualityCrafting] Quality variant generation completed. Files created in save's mods directory.");
            System.out.println("[RomnasQualityCrafting] WARNING: A server restart is required to load the new JSON files.");
        } else if (generationFailed) {
            System.out.println("[RomnasQualityCrafting] Quality variant generation FAILED.");
            if (generationFailureReason != null) {
                System.out.println("[RomnasQualityCrafting] Reason: " + generationFailureReason);
            }
            System.out.println("[RomnasQualityCrafting] Generation will retry on next server restart.");
        }
    }
    
    /**
     * Retourne true si une génération a eu lieu lors de ce démarrage.
     */
    public static boolean wasGenerationPerformed() {
        return generationHappened;
    }
    
    /**
     * Retourne true si une génération a échoué lors de ce démarrage.
     */
    public static boolean didGenerationFail() {
        return generationFailed;
    }
    
    /**
     * Retourne la raison de l'échec de génération, ou null si pas d'échec.
     */
    @Nullable
    public static String getGenerationFailureReason() {
        return generationFailureReason;
    }
    
    /**
     * Retourne le nombre de fichiers générés avec succès.
     */
    public static int getGeneratedFileCount() {
        return generatedFileCount;
    }
    
    /**
     * Retourne le nombre d'erreurs lors de la génération.
     */
    public static int getErrorFileCount() {
        return errorFileCount;
    }

    /**
     * Obtient la map brute des assets Item (pour usage externe).
     */
    public static Object getItemAssetMapRaw() {
        try {
            Method getAssetMap = Item.class.getMethod("getAssetMap");
            return getAssetMap.invoke(null);
        } catch (Exception e) {
            // Silent failure - this is expected in some scenarios
            return null;
        }
    }

    private static final String SENTINEL_KEY = "__RomnasQualityCrafting_MUTABLE_TEST__";

    /**
     * Résout une map mutable depuis la map d'assets (pour usage externe).
     */
    public static Map<String, Item> resolveMutableMap(Object assetMap) {
        Map<String, Item> candidate = extractMap(assetMap);
        if (candidate != null) {
            Map<String, Item> mutable = ensureMutable(candidate);
            if (mutable != null) return mutable;
        }
        return findMutableMapDeep(assetMap, 4, new IdentityHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Item> extractMap(Object assetMap) {
        try {
            Method getAssetMap = assetMap.getClass().getMethod("getAssetMap");
            Object inner = getAssetMap.invoke(assetMap);
            if (inner instanceof Map) return (Map<String, Item>) inner;
        } catch (Exception ignored) {
        }
        if (assetMap instanceof Map) return (Map<String, Item>) assetMap;
        try {
            for (Method m : assetMap.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && Map.class.isAssignableFrom(m.getReturnType())) {
                    Object inner = m.invoke(assetMap);
                    if (inner instanceof Map) return (Map<String, Item>) inner;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Map<String, Item> ensureMutable(Map<String, Item> map) {
        if (map == null) return null;
        if (testPut(map)) return map;
        Map<String, Item> next = findBackingMap(map);
        if (next != null && next != map) return ensureMutable(next);
        return null;
    }

    private static boolean testPut(Map<String, Item> map) {
        try {
            if (map.isEmpty()) {
                map.put(SENTINEL_KEY, null);
                map.remove(SENTINEL_KEY);
            } else {
                Item any = map.values().iterator().next();
                map.put(SENTINEL_KEY, any);
                map.remove(SENTINEL_KEY);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Item> findBackingMap(Map<String, Item> map) {
        if (map == null) return null;
        Class<?> c = map.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object backing = f.get(map);
                    if (backing instanceof Map && backing != map) {
                        return (Map<String, Item>) backing;
                    }
                } catch (Exception ignored) {
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Map<String, Item> findMutableMapDeep(@Nullable Object root, int depth, @Nonnull IdentityHashMap<Object, Boolean> visited) {
        if (root == null || depth <= 0 || visited.containsKey(root)) return null;
        visited.put(root, Boolean.TRUE);
        try {
            if (root instanceof Map) {
                if (testPut((Map<String, Item>) root)) return (Map<String, Item>) root;
            }
            Class<?> c = root.getClass();
            while (c != null && c != Object.class) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().isPrimitive() || f.getType() == String.class) continue;
                    try {
                        f.setAccessible(true);
                        Object v = f.get(root);
                        if (v == null) continue;
                        if (v instanceof Map && testPut((Map<String, Item>) v)) {
                            return (Map<String, Item>) v;
                        }
                        if (depth > 1) {
                            Map<String, Item> found = findMutableMapDeep(v, depth - 1, visited);
                            if (found != null) return found;
                        }
                    } catch (Exception ignored) {
                    }
                }
                c = c.getSuperclass();
            }
        } finally {
            visited.remove(root);
        }
        return null;
    }

    /**
     * Construit une liste d'ids à traiter sans garder de référence aux entries de la map,
     * pour éviter tout décalage ou double traitement quand on ajoute des variantes.
     * Filtre strict : armes/armures (via tags ou catégories), vrais outils (Tool + getTool/BlockSelectorTool),
     * et exclusion des bancs, etc.
     */
    @Nonnull
    private static List<String> collectBaseIdsSnapshot(Map<String, Item> map) {
        List<String> out = new ArrayList<>();
        List<String> keys = new ArrayList<>(map.keySet());
        for (String id : keys) {
            Item item = map.get(id);
            if (item == null || item == Item.UNKNOWN) continue;
            if (QualityManager.hasQualityInId(id)) continue;
            if (isExcludedId(id)) continue;
            if (!isEligibleForQualityVariants(id, item)) continue;
            out.add(id);
        }
        return out;
    }

    private static boolean isExcludedId(String id) {
        if (id == null) return true;
        
        // Get excluded lists from QualityConfigManager (which loads from JSON)
        java.util.List<String> excludedPrefixes = dev.hytalemodding.quality.QualityConfigManager.getExcludedIdPrefixes();
        java.util.List<String> excludedItems = dev.hytalemodding.quality.QualityConfigManager.getExcludedItems();
        
        for (String prefix : excludedPrefixes) {
            if (id.startsWith(prefix)) return true;
        }
        for (String item : excludedItems) {
            if (id.equals(item)) return true;
        }
        return false;
    }

    /**
     * Vrai seulement pour des équipements qu’on veut dupliquer en qualités :
     * - arme/armure avec tag ou catégorie Weapon/Armor, ou
     * - outil avec getTool() ou getBlockSelectorTool() (on exclut les id "Tool_*" sans vraie config outil).
     */
    private static boolean isEligibleForQualityVariants(String id, Item item) {
        if (item == null) return false;
        if (item.getWeapon() != null || item.getArmor() != null) {
            return QualityManager.hasWeaponOrArmorTag(item);
        }
        if (item.getTool() != null) return true;
        try {
            if (Item.class.getMethod("getBlockSelectorTool").invoke(item) != null) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static void log(String msg) {
        System.out.println(LOG_PREFIX + msg);
    }
}


