package dev.hytalemodding.quality;

import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import dev.hytalemodding.config.QualityConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Maps our quality tiers to Hytale's built-in ItemQuality system.
 *
 * At startup, enumerates Hytale's built-in quality tiers and finds their
 * indices. Then creates a mapping from our ItemQuality enum to Hytale quality
 * indices so that items show the correct colors and tooltips in the client.
 *
 * Approach for per-stack quality visuals:
 * Since quality color is per-Item-asset (via qualityIndex), not per-ItemStack,
 * we create variant Item clones for each eligible item × quality tier. Each
 * variant has a different qualityIndex. When quality is assigned to an ItemStack,
 * the item ID is swapped to the variant's ID.
 *
 * Variant naming convention: {baseId}_{qualityDisplayName}
 * Example: "Weapon_Sword_Copper_Legendary"
 */
public final class QualityTierMapper {

    private static final String LOG_PREFIX = "[RQC] TierMapper: ";

    // Mapping from our quality enum → Hytale qualityIndex
    private final Map<ItemQuality, Integer> qualityToIndex = new EnumMap<>(ItemQuality.class);

    // Mapping from our quality enum → Hytale quality ID string
    private final Map<ItemQuality, String> qualityToHytaleId = new EnumMap<>(ItemQuality.class);

    // Set of all variant item IDs we've created
    private final Set<String> variantItemIds = new HashSet<>();

    // Map from variant ID → base ID for reverse lookup
    private final Map<String, String> variantToBase = new HashMap<>();

    // Map from variant ID → quality for reverse lookup
    private final Map<String, ItemQuality> variantToQuality = new HashMap<>();

    // Mapping from our quality enum → Hytale quality tier's ItemEntityConfig
    // (contains particleSystemId for ground drop glow per rarity)
    private final Map<ItemQuality, Object> qualityToItemEntityConfig = new EnumMap<>(ItemQuality.class);

    private boolean initialized = false;
    private int variantsCreated = 0;

    /**
     * Initialize by discovering Hytale's built-in quality tiers and mapping
     * our tiers to them. Must be called after assets are loaded.
     */
    public void initialize() {
        discoverHytaleTiers();
        collectQualityItemEntityConfigs();
        initialized = true;
    }

    /**
     * Creates variant Item clones for all eligible items, each with the
     * appropriate qualityIndex. Registers them in the Item asset map.
     * Also applies quality multipliers to durability and armor stats on the
     * Item asset so that client tooltips display the correct values.
     */
    public void createVariants(@Nonnull QualityRegistry registry, @Nonnull QualityConfig config) {
        if (!initialized) {
            System.out.println(LOG_PREFIX + "ERROR: Not initialized! Cannot create variants.");
            return;
        }

        long startTime = System.currentTimeMillis();

        Map<String, Item> itemMap = getItemAssetMap();
        if (itemMap == null) {
            System.out.println(LOG_PREFIX + "ERROR: Cannot access item asset map for variant creation!");
            return;
        }

        int created = 0;
        int failed = 0;
        Set<String> eligibleIds = registry.getEligibleItemIds();

        for (String baseId : eligibleIds) {
            Item baseItem = itemMap.get(baseId);
            if (baseItem == null) continue;

            // Create a variant for each quality tier (including COMMON)
            for (ItemQuality quality : ItemQuality.values()) {
                Integer hytaleIdx = qualityToIndex.get(quality);
                if (hytaleIdx == null) continue;

                String variantId = ItemQuality.qualityItemId(baseId, quality);

                try {
                    // Use copy constructor to clone the item
                    Item variant = new Item(baseItem);

                    // ── Fix: copy fields the copy constructor misses ──
                    copyMissingFields(variant, baseItem);

                    // Set the variant's ID
                    setFieldValue(variant, "id", variantId);

                    // Set the variant's qualityIndex to match our tier
                    setFieldValue(variant, "qualityIndex", hytaleIdx);

                    // Set the qualityId
                    String hytaleQualityId = qualityToHytaleId.get(quality);
                    if (hytaleQualityId != null) {
                        setFieldValue(variant, "qualityId", hytaleQualityId);
                    }

                    // ── Apply quality multipliers to Item-level stats ──
                    // Durability: scale maxDurability on the Item asset so new
                    // ItemStacks created from this variant get the right durability
                    applyDurabilityMultiplier(variant, quality, config);

                    // Armor: scale baseDamageResistance so the tooltip shows
                    // the quality-adjusted armor value
                    applyArmorMultiplier(variant, quality, config);

                    // Tools: scale speed and power so pickaxe/axe/shovel
                    // efficiency reflects quality tier
                    applyToolMultiplier(variant, quality, config);

                    // Weapons: scale stat modifier amounts (damage) so the
                    // tooltip and combat reflect quality tier
                    applyWeaponMultiplier(variant, quality, config);

                    // Ground drop glow: set the variant's itemEntityConfig
                    // so the correct particle system plays when dropped
                    applyDropGlow(variant, baseItem, quality);

                    // Clear the cached packet so it regenerates with new stats
                    try {
                        setFieldValue(variant, "cachedPacket", null);
                    } catch (Exception ignored) {}

                    // Register in the mutable backing asset map
                    itemMap.put(variantId, variant);

                    variantItemIds.add(variantId);
                    variantToBase.put(variantId, baseId);
                    variantToQuality.put(variantId, quality);
                    created++;


                } catch (Exception e) {
                    failed++;
                }
            }
        }

        variantsCreated = created;
        long elapsed = System.currentTimeMillis() - startTime;
        if (failed > 0) {
            System.out.println(LOG_PREFIX + "WARNING: " + failed + " variant(s) failed to create");
        }

        // Register all pending cloned interactions in their asset stores
        registerPendingInteractions();

        // Clone salvage/crafting recipes for all variants so they work in
        // workstations (e.g. salvage bench) without needing JSON files
        cloneRecipesForVariants(eligibleIds);
    }

    /**
     * Clones all CraftingRecipes (salvage, crafting, etc.) that reference a base
     * item as input and creates matching recipes for each quality variant.
     *
     * For each existing recipe whose input ItemId matches a base eligible item,
     * we create 6 cloned recipes (one per quality tier) with the input ItemId
     * swapped to the variant ID. The output stays the same — salvaging a
     * Legendary sword gives the same materials as salvaging the base sword.
     *
     * Recipes are registered via CraftingRecipe.getAssetStore().loadAssets(),
     * which uses DefaultAssetMap (not indexed) so it's safe to add dynamically.
     * The CraftingPlugin's onRecipeLoad listener auto-registers them with benches.
     */
    private void cloneRecipesForVariants(Set<String> eligibleIds) {
        try {
            Map<String, CraftingRecipe> recipeMap = CraftingRecipe.getAssetMap().getAssetMap();
            if (recipeMap == null || recipeMap.isEmpty()) {
                return;
            }

            // Build a map of baseItemId → list of SALVAGE recipes that use it as input.
            // We only clone salvage recipes (ID starts with "Salvage_") so that quality
            // variants work on salvage benches. Crafting recipes that happen to use an
            // eligible item as one of their ingredients should NOT be cloned — doing so
            // creates duplicate recipes for unrelated outputs (e.g. ZC_Composter,
            // armor recoloring recipes, ore processing, etc.).
            Map<String, List<CraftingRecipe>> baseToRecipes = new HashMap<>();
            for (CraftingRecipe recipe : recipeMap.values()) {
                MaterialQuantity[] inputs = recipe.getInput();
                if (inputs == null || inputs.length == 0) continue;

                // Only clone salvage recipes — skip all other recipe types
                String recipeId = recipe.getId();
                if (recipeId == null || !recipeId.startsWith("Salvage_")) continue;

                for (MaterialQuantity input : inputs) {
                    String inputItemId = input.getItemId();
                    if (inputItemId != null && eligibleIds.contains(inputItemId)) {
                        baseToRecipes.computeIfAbsent(inputItemId, k -> new ArrayList<>()).add(recipe);
                    }
                }
            }

            if (baseToRecipes.isEmpty()) {
                return;
            }

            List<CraftingRecipe> pendingRecipes = new ArrayList<>();
            int clonedCount = 0;

            for (Map.Entry<String, List<CraftingRecipe>> entry : baseToRecipes.entrySet()) {
                String baseId = entry.getKey();
                List<CraftingRecipe> recipes = entry.getValue();

                for (CraftingRecipe originalRecipe : recipes) {
                    String originalId = originalRecipe.getId();

                    for (ItemQuality quality : ItemQuality.values()) {
                        String variantId = ItemQuality.qualityItemId(baseId, quality);
                        if (!variantItemIds.contains(variantId)) continue;

                        try {
                            // Clone via copy constructor
                            CraftingRecipe cloned = new CraftingRecipe(originalRecipe);

                            // Generate a unique recipe ID
                            String clonedRecipeId = originalId + "_" + quality.getDisplayName();
                            setFieldValue(cloned, "id", clonedRecipeId);

                            // Clone input array, replacing the base item ID with the variant
                            MaterialQuantity[] origInputs = originalRecipe.getInput();
                            MaterialQuantity[] newInputs = new MaterialQuantity[origInputs.length];
                            for (int i = 0; i < origInputs.length; i++) {
                                MaterialQuantity mq = origInputs[i];
                                if (baseId.equals(mq.getItemId())) {
                                    // Clone the MaterialQuantity with the variant's item ID
                                    newInputs[i] = new MaterialQuantity(
                                            variantId,
                                            mq.getResourceTypeId(),
                                            null,  // tag
                                            mq.getQuantity(),
                                            mq.getMetadata()
                                    );
                                } else {
                                    newInputs[i] = mq;
                                }
                            }
                            setFieldValue(cloned, "input", newInputs);

                            pendingRecipes.add(cloned);
                            clonedCount++;

                        } catch (Exception e) {
                        }
                    }
                }
            }

            // Batch-register all cloned recipes
            if (!pendingRecipes.isEmpty()) {
                CraftingRecipe.getAssetStore().loadAssets("RomnasQualityCrafting", pendingRecipes);
            }
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "WARNING: Failed to clone recipes for variants: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets the variant item ID for a base item at a given quality.
     */
    @Nonnull
    public String getVariantId(@Nonnull String baseId, @Nonnull ItemQuality quality) {
        return ItemQuality.qualityItemId(baseId, quality);
    }

    /**
     * Gets the base item ID from a variant ID.
     * If the ID is not a variant, returns the ID unchanged.
     */
    @Nonnull
    public String getBaseId(@Nonnull String itemId) {
        String base = variantToBase.get(itemId);
        if (base != null) return base;

        // Parse quality suffix if present
        return ItemQuality.extractBaseId(itemId);
    }

    /**
     * Checks if an item ID is one of our quality variants.
     */
    public boolean isVariant(@Nonnull String itemId) {
        return variantItemIds.contains(itemId);
    }

    /**
     * Gets the quality from a variant item ID.
     */
    @Nullable
    public ItemQuality getQualityFromVariantId(@Nonnull String itemId) {
        ItemQuality q = variantToQuality.get(itemId);
        if (q != null) return q;

        // Fall back to parsing suffix
        return ItemQuality.fromItemId(itemId);
    }

    /**
     * Gets the Hytale qualityIndex for our quality tier.
     */
    public int getHytaleQualityIndex(@Nonnull ItemQuality quality) {
        Integer idx = qualityToIndex.get(quality);
        return (idx != null) ? idx : 0;
    }

    public boolean isInitialized() { return initialized; }
    public int getVariantsCreated() { return variantsCreated; }

    /** Returns an unmodifiable view of the variant-ID → base-ID map. */
    @Nonnull
    public Map<String, String> getVariantToBaseMap() {
        return Collections.unmodifiableMap(variantToBase);
    }

    // ── Private helpers ──

    /**
     * Discovers Hytale's built-in quality tiers by enumerating the ItemQuality asset map.
     * Maps our tiers to Hytale tiers by name matching or by quality value ordering.
     */
    private void discoverHytaleTiers() {
        try {
            // Access Hytale's ItemQuality asset map
            Class<?> hytaleQualityClass = com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality.class;
            Method getAssetMapMethod = hytaleQualityClass.getMethod("getAssetMap");
            Object assetMapObj = getAssetMapMethod.invoke(null);

            if (assetMapObj == null) {
                System.out.println(LOG_PREFIX + "WARNING: ItemQuality asset map is null, using fallback indices");
                setFallbackMapping();
                return;
            }

            // Get the underlying map
            Method getMapMethod = assetMapObj.getClass().getMethod("getAssetMap");
            @SuppressWarnings("unchecked")
            Map<String, ?> qualityMap = (Map<String, ?>) getMapMethod.invoke(assetMapObj);

            if (qualityMap == null || qualityMap.isEmpty()) {
                System.out.println(LOG_PREFIX + "WARNING: ItemQuality map is empty, using fallback");
                setFallbackMapping();
                return;
            }

            // Collect all tiers with their info
            List<HytaleTierInfo> tiers = new ArrayList<>();

            // Get the getIndex method for looking up indices
            Method getIndexMethod = assetMapObj.getClass().getMethod("getIndex", Object.class);

            for (Map.Entry<String, ?> entry : qualityMap.entrySet()) {
                String id = entry.getKey();
                Object qualityObj = entry.getValue();

                int index = -1;
                try {
                    index = (int) getIndexMethod.invoke(assetMapObj, id);
                } catch (Exception ignored) {}

                int qualityValue = -1;
                try {
                    Method getQualityValue = qualityObj.getClass().getMethod("getQualityValue");
                    qualityValue = (int) getQualityValue.invoke(qualityObj);
                } catch (Exception ignored) {}

                String textColorStr = "?";
                try {
                    Method getTextColor = qualityObj.getClass().getMethod("getTextColor");
                    Object color = getTextColor.invoke(qualityObj);
                    if (color != null) {
                        Color c = (Color) color;
                        textColorStr = "(" + (c.red & 0xFF) + "," + (c.green & 0xFF) + "," + (c.blue & 0xFF) + ")";
                    }
                } catch (Exception ignored) {}

                tiers.add(new HytaleTierInfo(id, index, qualityValue));
            }

            // Sort by qualityValue (ascending)
            tiers.sort(Comparator.comparingInt(t -> t.qualityValue));

            // Map our tiers to Hytale tiers by matching names first,
            // then by quality value ordering
            mapByNameOrOrder(tiers);

        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "ERROR discovering quality tiers: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
            setFallbackMapping();
        }
    }

    private void mapByNameOrOrder(List<HytaleTierInfo> sortedTiers) {
        // Try name matching first (case-insensitive)
        Map<String, HytaleTierInfo> byName = new HashMap<>();
        for (HytaleTierInfo tier : sortedTiers) {
            byName.put(tier.id.toLowerCase(Locale.ROOT), tier);
        }

        // Try to match each of our tiers by common names
        String[][] nameMatches = {
            {"POOR",      "poor", "junk", "trash", "broken"},
            {"COMMON",    "common", "normal", "standard", "basic", "default"},
            {"UNCOMMON",  "uncommon", "fine", "improved"},
            {"RARE",      "rare", "superior"},
            {"EPIC",      "epic", "exceptional", "magnificent"},
            {"LEGENDARY", "legendary", "mythic", "divine", "relic"},
        };

        Set<HytaleTierInfo> used = new HashSet<>();

        for (String[] nameMatch : nameMatches) {
            ItemQuality ourTier = ItemQuality.valueOf(nameMatch[0]);
            boolean found = false;

            for (int i = 1; i < nameMatch.length; i++) {
                HytaleTierInfo match = byName.get(nameMatch[i]);
                if (match != null && !used.contains(match)) {
                    qualityToIndex.put(ourTier, match.index);
                    qualityToHytaleId.put(ourTier, match.id);
                    used.add(match);
                    found = true;
                    break;
                }
            }

            if (!found) {
                // Fall back to ordering: distribute across available tiers
                // We have 6 tiers, Hytale has ~12. Space them out.
            }
        }

        // For any unmapped tiers, distribute by quality value
        List<HytaleTierInfo> unused = new ArrayList<>();
        for (HytaleTierInfo tier : sortedTiers) {
            if (!used.contains(tier)) unused.add(tier);
        }

        for (ItemQuality ourTier : ItemQuality.values()) {
            if (qualityToIndex.containsKey(ourTier)) continue;

            if (!unused.isEmpty()) {
                // Pick the one whose qualityValue is closest to our tier's ordinal
                int targetOrdinal = ourTier.ordinal();
                // Map 0..5 onto 0..(unused.size-1)
                int pickIdx = Math.min(targetOrdinal * unused.size() / 6, unused.size() - 1);
                HytaleTierInfo pick = unused.get(pickIdx);
                qualityToIndex.put(ourTier, pick.index);
                qualityToHytaleId.put(ourTier, pick.id);
                used.add(pick);
                unused.remove(pickIdx);
            } else {
                // Last resort: use the ordinal as index
                qualityToIndex.put(ourTier, ourTier.ordinal());
                qualityToHytaleId.put(ourTier, ourTier.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    private void setFallbackMapping() {
        for (ItemQuality q : ItemQuality.values()) {
            qualityToIndex.put(q, q.ordinal());
            qualityToHytaleId.put(q, q.name().toLowerCase(Locale.ROOT));
        }
    }

    /**
     * After tier mapping is complete, looks up the ItemEntityConfig from each
     * Hytale quality tier and stores it for later use during variant creation.
     * The ItemEntityConfig contains the particleSystemId (e.g. "Drop_Legendary")
     * that controls the ground drop glow effect per rarity.
     */
    private void collectQualityItemEntityConfigs() {
        try {
            Class<?> hqClass = com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality.class;
            Method getAssetMapMethod = hqClass.getMethod("getAssetMap");
            Object assetMapObj = getAssetMapMethod.invoke(null);
            if (assetMapObj == null) return;

            Method getMapMethod = assetMapObj.getClass().getMethod("getAssetMap");
            @SuppressWarnings("unchecked")
            Map<String, ?> qualityMap = (Map<String, ?>) getMapMethod.invoke(assetMapObj);
            if (qualityMap == null || qualityMap.isEmpty()) return;

            for (ItemQuality ourTier : ItemQuality.values()) {
                String hytaleId = qualityToHytaleId.get(ourTier);
                if (hytaleId == null) continue;

                Object qualityObj = qualityMap.get(hytaleId);
                if (qualityObj == null) continue;

                try {
                    Method getIec = qualityObj.getClass().getMethod("getItemEntityConfig");
                    Object iec = getIec.invoke(qualityObj);
                    if (iec != null) {
                        qualityToItemEntityConfig.put(ourTier, iec);
                    }
                } catch (Exception ignored) {}
            }

            System.out.println(LOG_PREFIX + "Collected ItemEntityConfig for "
                    + qualityToItemEntityConfig.size() + " quality tiers (drop glow)");

        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "WARNING: Could not collect quality ItemEntityConfigs: " + e.getMessage());
        }
    }

    /**
     * Sets the variant's itemEntityConfig so dropped items on the ground show
     * the correct glow/particle effect for their quality tier.
     *
     * The particleSystemId field (e.g. "Drop_Common", "Drop_Legendary") on
     * ItemEntityConfig controls which particle system the client renders when
     * the item entity is on the ground. Without this, all variants inherit the
     * base item's original glow regardless of quality.
     *
     * Strategy: clone the base item's existing itemEntityConfig (to preserve
     * custom physics/pickup values), then override the particleSystemId with the
     * value from the matching Hytale quality tier.
     */
    private void applyDropGlow(Item variant, Item baseItem, ItemQuality quality) {
        try {
            Object tierIec = qualityToItemEntityConfig.get(quality);

            // Get the particle system ID for this quality tier
            String tierParticleSystemId = null;
            if (tierIec != null) {
                try {
                    tierParticleSystemId = (String) getFieldValue(tierIec, "particleSystemId");
                } catch (Exception ignored) {}
            }

            Object baseIec = getFieldValue(baseItem, "itemEntityConfig");

            if (baseIec != null) {
                // Clone the base item's config to preserve physics/pickup/ttl values
                Object clonedIec = cloneObjectShallow(baseIec);

                // Override particleSystemId to match quality tier's glow
                setFieldValue(clonedIec, "particleSystemId", tierParticleSystemId);

                // Also clone showItemParticles from tier config if available
                if (tierIec != null) {
                    try {
                        boolean showParticles = (boolean) getFieldValue(tierIec, "showItemParticles");
                        setFieldValue(clonedIec, "showItemParticles", showParticles);
                    } catch (Exception ignored) {}
                }

                setFieldValue(variant, "itemEntityConfig", clonedIec);
            } else if (tierIec != null) {
                // Base item has no config but quality tier does — use tier's config directly
                Object clonedIec = cloneObjectShallow(tierIec);
                setFieldValue(variant, "itemEntityConfig", clonedIec);
            }
            // If neither exists, leave the variant with no itemEntityConfig (no glow)
        } catch (Exception e) {
            // Non-critical — variant will just use inherited glow
        }
    }

    // ── Fields the Item copy constructor does NOT copy ──
    // The Item(Item) copy constructor misses several fields, defaulting them
    // to 0/null. We must copy them manually via reflection.
    private static final String[] MISSING_COPY_FIELDS = {
        "maxDurability",
        "durabilityLossOnHit",
        "fuelQuality",
        "glider",
        "blockToState",
        "itemAppearanceConditions",
        "rawDisplayEntityStatsHUD",
        "itemStackContainerConfig",
    };

    /**
     * Copies fields that the Item copy constructor misses.
     */
    private static void copyMissingFields(Item variant, Item baseItem) {
        for (String fieldName : MISSING_COPY_FIELDS) {
            try {
                Object value = getFieldValue(baseItem, fieldName);
                if (value != null) {
                    setFieldValue(variant, fieldName, value);
                }
            } catch (Exception ignored) {
                // Field might not exist in this version — skip silently
            }
        }
    }

    /**
     * Applies the quality durability multiplier to the variant Item's maxDurability.
     * This ensures that ItemStacks created from this variant get the correct
     * maxDurability from the start, and the tooltip shows the adjusted value.
     */
    private static void applyDurabilityMultiplier(Item variant, ItemQuality quality, QualityConfig config) {
        try {
            double baseDur = variant.getMaxDurability();
            if (baseDur > 0) {
                float multiplier = quality.getDurabilityMultiplier(config);
                double newDur = baseDur * multiplier;
                setFieldValue(variant, "maxDurability", newDur);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Applies the quality armor multiplier to the variant Item's armor stats.
     * Clones the armor object first (since it's shared by reference from the
     * copy constructor), then scales ALL armor-related stats:
     *   - baseDamageResistance (flat armor value)
     *   - damageResistanceValues (per-cause resistance modifiers)
     *   - damageEnhancementValues (attack/damage bonus modifiers)
     *   - damageClassEnhancement (melee/ranged/magic enhancement)
     *   - statModifiers (mana, health, oxygen, etc.)
     *   - knockbackResistances / knockbackEnhancements (per-cause)
     *   - interactionModifiers (per-interaction type modifiers)
     */
    private static void applyArmorMultiplier(Item variant, ItemQuality quality, QualityConfig config) {
        try {
            Object armor = variant.getArmor();
            if (armor == null) return;

            float multiplier = quality.getArmorMultiplier(config);
            if (multiplier == 1.0f) return;

            // Clone the armor object to avoid modifying the base item's shared reference.
            Object clonedArmor = cloneObjectShallow(armor);

            // ── Scale baseDamageResistance (flat armor value) ──
            double baseResistance = (double) getFieldValue(clonedArmor, "baseDamageResistance");
            if (baseResistance > 0) {
                setFieldValue(clonedArmor, "baseDamageResistance", baseResistance * multiplier);
            }

            // ── Scale per-cause damage resistance modifiers ──
            scaleStatModifierMap(clonedArmor, "damageResistanceValues", multiplier);
            scaleStatModifierMap(clonedArmor, "damageResistanceValuesRaw", multiplier);

            // ── Scale per-cause damage enhancement modifiers (attack bonuses) ──
            scaleStatModifierMap(clonedArmor, "damageEnhancementValues", multiplier);
            scaleStatModifierMap(clonedArmor, "damageEnhancementValuesRaw", multiplier);

            // ── Scale damage class enhancement (melee/ranged/magic) ──
            scaleStatModifierMapSingleModifier(clonedArmor, "damageClassEnhancement", multiplier);

            // ── Scale stat modifiers (mana, health, oxygen, etc.) ──
            scaleInt2ObjectModifierMap(clonedArmor, "statModifiers", multiplier);
            scaleStatModifierMap(clonedArmor, "rawStatModifiers", multiplier);

            // ── Scale knockback resistances (Map<DamageCause, Float>) ──
            scaleFloatValueMap(clonedArmor, "knockbackResistances", multiplier);
            scaleFloatValueMap(clonedArmor, "knockbackResistancesRaw", multiplier);

            // ── Scale knockback enhancements (Map<DamageCause, Float>) ──
            scaleFloatValueMap(clonedArmor, "knockbackEnhancements", multiplier);
            scaleFloatValueMap(clonedArmor, "knockbackEnhancementsRaw", multiplier);

            // ── Scale interaction modifiers ──
            scaleInteractionModifiers(clonedArmor, "interactionModifiers", multiplier);
            scaleInteractionModifiersRaw(clonedArmor, "interactionModifiersRaw", multiplier);

            // Replace the variant's armor with our scaled clone
            setFieldValue(variant, "armor", clonedArmor);
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "WARNING: Failed to apply armor multiplier: " + e.getMessage());
        }
    }

    /**
     * Applies the quality tool multiplier to the variant Item's tool stats.
     * Clones the tool + each ItemToolSpec (shared by reference), then scales:
     *   - ItemTool.speed (mining speed multiplier)
     *   - Each ItemToolSpec.power (how fast this tool breaks blocks of its type)
     */
    private static void applyToolMultiplier(Item variant, ItemQuality quality, QualityConfig config) {
        try {
            Object tool = getFieldValue(variant, "tool");
            if (tool == null) return;

            float multiplier = quality.getToolMultiplier(config);
            if (multiplier == 1.0f) return;

            // Clone the tool object (shared reference from copy constructor)
            Object clonedTool = cloneObjectShallow(tool);

            // Scale tool speed
            float speed = (float) getFieldValue(clonedTool, "speed");
            float newSpeed = speed * multiplier;
            setFieldValue(clonedTool, "speed", newSpeed);

            // Clone and scale each ItemToolSpec in the specs array
            Object specsObj = getFieldValue(clonedTool, "specs");
            if (specsObj != null && specsObj.getClass().isArray()) {
                Object[] specs = (Object[]) specsObj;
                // Create a typed array of the same component type
                Object[] clonedSpecs = (Object[]) java.lang.reflect.Array.newInstance(
                        specsObj.getClass().getComponentType(), specs.length);

                for (int i = 0; i < specs.length; i++) {
                    if (specs[i] == null) {
                        clonedSpecs[i] = null;
                        continue;
                    }
                    Object clonedSpec = cloneObjectShallow(specs[i]);

                    // Scale power
                    float power = (float) getFieldValue(clonedSpec, "power");
                    float newPower = power * multiplier;
                    setFieldValue(clonedSpec, "power", newPower);

                    // Clear cached packet on the spec so it regenerates
                    try { setFieldValue(clonedSpec, "cachedPacket", null); } catch (Exception ignored) {}

                    clonedSpecs[i] = clonedSpec;
                }

                setFieldValue(clonedTool, "specs", clonedSpecs);
            }

            // Replace the variant's tool with our scaled clone
            setFieldValue(variant, "tool", clonedTool);


        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "WARNING: Failed to apply tool multiplier: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Applies the quality damage multiplier to the variant Item's weapon interactions.
     *
     * Weapon damage in Hytale is NOT stored in ItemWeapon.statModifiers (those are
     * empty for most weapons). Instead, damage values live in the InteractionVars
     * system: the Item JSON defines per-item interaction overrides (with
     * DamageCalculator.BaseDamage) that get resolved into shared Interaction assets
     * in the global Interaction asset store.
     *
     * Weapon damage is baked by cloning the RootInteraction assets referenced by
     * each variant's interactionVars, cloning the DamageEntityInteraction sub-interactions
     * with scaled baseDamage values, and registering the clones in the asset stores.
     *
     * Signature Energy (statModifiers) uses a separate inverted multiplier.
     */
    private void applyWeaponMultiplier(Item variant, ItemQuality quality, QualityConfig config) {
        try {
            Object weapon = getFieldValue(variant, "weapon");
            if (weapon == null) return;

            // Signature Energy uses its own multiplier (inverted: lower = better)
            float sigMultiplier = quality.getSignatureMultiplier(config);

            boolean hasSigScaling = sigMultiplier != 1.0f;

            if (hasSigScaling) {
                // Clone the weapon object (shared reference from copy constructor)
                Object clonedWeapon = cloneObjectShallow(weapon);

                // Scale statModifiers with SIGNATURE multiplier (lower = better)
                Object statMods = getFieldValue(clonedWeapon, "statModifiers");
                if (statMods != null) {
                    scaleInt2ObjectModifierMap(clonedWeapon, "statModifiers", sigMultiplier);
                }

                // Also scale raw stat modifiers with signature multiplier
                Object rawStatMods = getFieldValue(clonedWeapon, "rawStatModifiers");
                if (rawStatMods != null) {
                    scaleStatModifierMap(clonedWeapon, "rawStatModifiers", sigMultiplier);
                }

                // Replace the variant's weapon with our scaled clone
                setFieldValue(variant, "weapon", clonedWeapon);
            }

            // Weapon DAMAGE: scale DamageCalculator.baseDamage in the interaction chain
            float damageMultiplier = quality.getDamageMultiplier(config);
            if (damageMultiplier != 1.0f) {
                applyWeaponDamageBaked(variant, quality, damageMultiplier);
            }


        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "WARNING: Failed to apply weapon multiplier: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Pending cloned RootInteractions and Interactions to register in batch
     * after all variants are created.
     */
    private final List<RootInteraction> pendingRootInteractions = new ArrayList<>();
    private final List<Interaction> pendingInteractions = new ArrayList<>();

    /**
     * Bakes weapon damage into a variant Item by cloning its interaction chain.
     *
     * Flow:
     * 1. Item.interactionVars: Map<String, String> maps var names → RootInteraction IDs
     * 2. RootInteraction.interactionIds[] → sub-Interaction IDs (includes DamageEntityInteraction)
     * 3. DamageEntityInteraction.damageCalculator.baseDamageRaw has the damage values
     *
     * For each interactionVar that points to a damage-related RootInteraction:
     *   - Clone each DamageEntityInteraction in the chain with scaled baseDamageRaw
     *   - Clone the RootInteraction pointing to the cloned sub-interactions
     *   - Register both in their respective asset stores
     *   - Update the variant's interactionVars map to point to the new RootInteraction ID
     */
    private void applyWeaponDamageBaked(Item variant, ItemQuality quality, float damageMultiplier) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> interactionVars = (Map<String, String>) getFieldValue(variant, "interactionVars");
            if (interactionVars == null || interactionVars.isEmpty()) return;

            String variantId = (String) getFieldValue(variant, "id");

            // Create a mutable copy of the interactionVars map since it may be unmodifiable/shared
            Map<String, String> newVars = new HashMap<>(interactionVars);
            boolean anyChanged = false;

            for (Map.Entry<String, String> entry : interactionVars.entrySet()) {
                String varName = entry.getKey();
                String rootInteractionId = entry.getValue();

                // Only process damage-related vars (name contains "Damage")
                if (!varName.contains("Damage")) continue;

                try {
                    // Look up the RootInteraction
                    RootInteraction rootInteraction = RootInteraction.getAssetMap().getAsset(rootInteractionId);
                    if (rootInteraction == null) continue;

                    // Get the sub-interaction IDs
                    String[] subIds = rootInteraction.getInteractionIds();
                    if (subIds == null || subIds.length == 0) continue;

                    // Check if any sub-interaction is a DamageEntityInteraction
                    boolean hasDamage = false;
                    String[] clonedSubIds = new String[subIds.length];

                    for (int i = 0; i < subIds.length; i++) {
                        Interaction subInteraction = Interaction.getAssetMap().getAsset(subIds[i]);
                        if (subInteraction == null) {
                            clonedSubIds[i] = subIds[i];
                            continue;
                        }

                        if (isDamageEntityInteraction(subInteraction)) {
                            // Clone this DamageEntityInteraction with scaled damage
                            String clonedSubId = subIds[i] + "_RQC_" + quality.getDisplayName();
                            Interaction clonedSub = cloneDamageInteraction(subInteraction, clonedSubId, damageMultiplier);
                            if (clonedSub != null) {
                                clonedSubIds[i] = clonedSubId;
                                pendingInteractions.add(clonedSub);
                                hasDamage = true;
                            } else {
                                clonedSubIds[i] = subIds[i];
                            }
                        } else {
                            clonedSubIds[i] = subIds[i];
                        }
                    }

                    if (hasDamage) {
                        // Clone the RootInteraction pointing to our cloned sub-interactions
                        String clonedRootId = rootInteractionId + "_RQC_" + quality.getDisplayName();
                        RootInteraction clonedRoot = cloneRootInteraction(rootInteraction, clonedRootId, clonedSubIds);
                        if (clonedRoot != null) {
                            pendingRootInteractions.add(clonedRoot);
                            newVars.put(varName, clonedRootId);
                            anyChanged = true;
                        }
                    }
                } catch (Exception e) {
                    // Interaction clone failed — variant uses base interaction
                }
            }

            if (anyChanged) {
                setFieldValue(variant, "interactionVars", newVars);
                // Clear cached packet so it regenerates with new interactionVars
                try { setFieldValue(variant, "cachedPacket", null); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "WARNING: Failed to bake weapon damage: " + e.getMessage());
        }
    }


    /**
     * Checks if an Interaction is a DamageEntityInteraction by class name.
     */
    private static boolean isDamageEntityInteraction(Interaction interaction) {
        return interaction.getClass().getSimpleName().equals("DamageEntityInteraction");
    }

    /**
     * Clones a DamageEntityInteraction with a new ID and scaled baseDamage values.
     */
    private static Interaction cloneDamageInteraction(Interaction original, String newId, float multiplier) {
        try {
            // Shallow-clone the entire object
            Interaction cloned = (Interaction) cloneObjectShallow(original);

            // Set the new ID
            setFieldValue(cloned, "id", newId);

            // Clone and scale the DamageCalculator
            Object damageCalc = getFieldValue(cloned, "damageCalculator");
            if (damageCalc != null) {
                Object clonedCalc = cloneObjectShallow(damageCalc);

                // Scale baseDamageRaw: Object2FloatMap<String> — the raw damage values from JSON
                scaleDamageMap(clonedCalc, "baseDamageRaw", multiplier);

                // Scale baseDamage: Int2FloatMap (transient, resolved from baseDamageRaw)
                scaleInt2FloatDamageMap(clonedCalc, "baseDamage", multiplier);

                setFieldValue(cloned, "damageCalculator", clonedCalc);
            }

            // Also check for angledDamage array — each entry has its own damage calc
            Object angledDamage = getFieldValue(cloned, "angledDamage");
            if (angledDamage != null && angledDamage.getClass().isArray()) {
                Object[] arr = (Object[]) angledDamage;
                Object[] clonedArr = java.util.Arrays.copyOf(arr, arr.length);
                for (int i = 0; i < clonedArr.length; i++) {
                    if (clonedArr[i] == null) continue;
                    Object clonedAngled = cloneObjectShallow(clonedArr[i]);

                    Object angledCalc = getFieldValue(clonedAngled, "damageCalculator");
                    if (angledCalc != null) {
                        Object clonedAngledCalc = cloneObjectShallow(angledCalc);
                        scaleDamageMap(clonedAngledCalc, "baseDamageRaw", multiplier);
                        scaleInt2FloatDamageMap(clonedAngledCalc, "baseDamage", multiplier);
                        setFieldValue(clonedAngled, "damageCalculator", clonedAngledCalc);
                    }
                    clonedArr[i] = clonedAngled;
                }
                setFieldValue(cloned, "angledDamage", clonedArr);
            }

            // Clear cached packet
            try { setFieldValue(cloned, "cachedPacket", null); } catch (Exception ignored) {}

            // Clear the AssetExtraInfo data so it doesn't conflict
            try {
                Object data = getFieldValue(original, "data");
                if (data != null) {
                    Object clonedData = cloneObjectShallow(data);
                    setFieldValue(clonedData, "key", newId);
                    setFieldValue(cloned, "data", clonedData);
                }
            } catch (Exception ignored) {}

            return cloned;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Clones a RootInteraction with a new ID and replaced sub-interaction IDs.
     */
    private static RootInteraction cloneRootInteraction(RootInteraction original, String newId, String[] newSubIds) {
        try {
            RootInteraction cloned = (RootInteraction) cloneObjectShallow(original);
            setFieldValue(cloned, "id", newId);
            setFieldValue(cloned, "interactionIds", newSubIds);

            // Clear operations so build() regenerates them
            setFieldValue(cloned, "operations", null);

            // Update AssetExtraInfo data
            try {
                Object data = getFieldValue(original, "data");
                if (data != null) {
                    Object clonedData = cloneObjectShallow(data);
                    setFieldValue(clonedData, "key", newId);
                    setFieldValue(cloned, "data", clonedData);
                }
            } catch (Exception ignored) {}

            return cloned;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Scales float values in an Object2FloatMap (e.g., baseDamageRaw: Object2FloatMap<String>).
     * Creates a new map to avoid modifying shared originals.
     */
    @SuppressWarnings("unchecked")
    private static void scaleDamageMap(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return;

            if (mapObj instanceof it.unimi.dsi.fastutil.objects.Object2FloatMap<?> floatMap) {
                var newMap = new it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap<>();
                for (var entry : floatMap.object2FloatEntrySet()) {
                    newMap.put(entry.getKey(), entry.getFloatValue() * multiplier);
                }
                setFieldValue(owner, fieldName, newMap);
            } else if (mapObj instanceof Map<?, ?> map) {
                Map<Object, Object> newMap = new HashMap<>();
                for (Map.Entry<?, ?> e : map.entrySet()) {
                    Object val = e.getValue();
                    if (val instanceof Number n) {
                        newMap.put(e.getKey(), n.floatValue() * multiplier);
                    } else {
                        newMap.put(e.getKey(), val);
                    }
                }
                setFieldValue(owner, fieldName, newMap);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Scales float values in an Int2FloatMap (e.g., baseDamage: Int2FloatMap).
     * Creates a new map to avoid modifying shared originals.
     */
    private static void scaleInt2FloatDamageMap(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return; // transient field may be null

            if (mapObj instanceof it.unimi.dsi.fastutil.ints.Int2FloatMap map) {
                var newMap = new it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap();
                for (var e : map.int2FloatEntrySet()) {
                    newMap.put(e.getIntKey(), e.getFloatValue() * multiplier);
                }
                setFieldValue(owner, fieldName, newMap);
            }
        } catch (Exception ignored) {}
    }

    // ── Shared helper: shallow-clone any object via its no-arg constructor ──

    /**
     * Registers all pending cloned Interaction and RootInteraction assets in their
     * respective asset stores via loadAssets(). This is called once after all
     * variant Items are created with their interactionVars pointing to the new IDs.
     *
     * Both Interaction and RootInteraction use IndexedLookupTableAssetMap, which
     * assigns integer indices. As long as the new entries are registered BEFORE the
     * Item assets are synced to the client (via toPacket()), the indices will be known.
     */
    private void registerPendingInteractions() {
        if (pendingInteractions.isEmpty() && pendingRootInteractions.isEmpty()) {
            return;
        }

        // Register cloned sub-Interactions first (DamageEntityInteraction etc.)
        if (!pendingInteractions.isEmpty()) {
            try {
                Interaction.getAssetStore().loadAssets("RomnasQualityCrafting", pendingInteractions);
            } catch (Exception e) {
                System.out.println(LOG_PREFIX + "ERROR registering Interactions: " + e.getMessage());
            }
        }

        // Then register cloned RootInteractions (which reference the sub-Interactions)
        if (!pendingRootInteractions.isEmpty()) {
            try {
                RootInteraction.getAssetStore().loadAssets("RomnasQualityCrafting", pendingRootInteractions);

                // Rebuild each RootInteraction's operations (they were cleared during cloning)
                for (RootInteraction ri : pendingRootInteractions) {
                    try {
                        ri.build();
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                System.out.println(LOG_PREFIX + "ERROR registering RootInteractions: " + e.getMessage());
            }
        }

        // Clear pending lists
        pendingInteractions.clear();
        pendingRootInteractions.clear();
    }

    private static Object cloneObjectShallow(Object original) throws Exception {
        Class<?> clazz = original.getClass();
        java.lang.reflect.Constructor<?> ctor = clazz.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object clone = ctor.newInstance();

        // Copy all instance fields (including inherited)
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                f.set(clone, f.get(original));
            }
            current = current.getSuperclass();
        }
        return clone;
    }

    // ── Shared helper: scale StaticModifier.amount in a Map<?, StaticModifier[]> ──

    @SuppressWarnings("unchecked")
    private static void scaleStatModifierMap(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return;

            if (mapObj instanceof Map<?, ?> map) {
                // Create a new map with scaled modifiers
                Map<Object, Object> newMap = new java.util.HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object val = entry.getValue();
                    if (val != null && val.getClass().isArray()) {
                        Object[] arr = (Object[]) val;
                        Object[] scaled = scaleModifierArray(arr, multiplier);
                        newMap.put(entry.getKey(), scaled);
                    } else {
                        newMap.put(entry.getKey(), val);
                    }
                }
                setFieldValue(owner, fieldName, newMap);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Scale StaticModifier.amount in an Int2ObjectMap (fastutil) used by weapon/armor.
     * Creates a NEW map instance to avoid modifying the shared original.
     */
    @SuppressWarnings("unchecked")
    private static void scaleInt2ObjectModifierMap(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return;

            if (mapObj instanceof Map<?, ?> map) {
                // Create a new map of the same type (fastutil Int2ObjectOpenHashMap)
                Map<Object, Object> newMap;
                try {
                    var ctor = mapObj.getClass().getDeclaredConstructor();
                    ctor.setAccessible(true);
                    newMap = (Map<Object, Object>) ctor.newInstance();
                } catch (Exception e) {
                    newMap = new java.util.HashMap<>();
                }

                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object val = entry.getValue();
                    if (val != null && val.getClass().isArray()) {
                        Object[] arr = (Object[]) val;
                        Object[] scaled = scaleModifierArray(arr, multiplier);
                        newMap.put(entry.getKey(), scaled);
                    } else {
                        newMap.put(entry.getKey(), val);
                    }
                }
                setFieldValue(owner, fieldName, newMap);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Clones an array of StaticModifier objects, scaling each one's 'amount' field.
     */
    private static Object[] scaleModifierArray(Object[] modifiers, float multiplier) {
        Object[] scaled = java.util.Arrays.copyOf(modifiers, modifiers.length);
        for (int i = 0; i < scaled.length; i++) {
            if (scaled[i] == null) continue;
            try {
                Object cloned = cloneObjectShallow(scaled[i]);
                float amount = (float) getFieldValue(cloned, "amount");
                setFieldValue(cloned, "amount", amount * multiplier);
                scaled[i] = cloned;
            } catch (Exception ignored) {}
        }
        return scaled;
    }

    /**
     * Scales Float values in a Map (e.g., knockbackResistances: Map<DamageCause, Float>).
     */
    @SuppressWarnings("unchecked")
    private static void scaleFloatValueMap(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return;

            if (mapObj instanceof Map<?, ?> map) {
                Map<Object, Object> newMap = new java.util.HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object val = entry.getValue();
                    if (val instanceof Float f) {
                        newMap.put(entry.getKey(), f * multiplier);
                    } else if (val instanceof Number n) {
                        newMap.put(entry.getKey(), (float) (n.floatValue() * multiplier));
                    } else {
                        newMap.put(entry.getKey(), val);
                    }
                }
                setFieldValue(owner, fieldName, newMap);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Scales StaticModifier values in a Map whose values are single StaticModifier
     * (not arrays), e.g. damageClassEnhancement: Map<DamageClass, StaticModifier[]>.
     * Actually, damageClassEnhancement uses StaticModifier[], so this handles both patterns.
     */
    @SuppressWarnings("unchecked")
    private static void scaleStatModifierMapSingleModifier(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return;

            if (mapObj instanceof Map<?, ?> map) {
                Map<Object, Object> newMap = new java.util.HashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    Object val = entry.getValue();
                    if (val != null && val.getClass().isArray()) {
                        Object[] arr = (Object[]) val;
                        Object[] scaled = scaleModifierArray(arr, multiplier);
                        newMap.put(entry.getKey(), scaled);
                    } else if (val != null) {
                        // Single StaticModifier object
                        try {
                            Object cloned = cloneObjectShallow(val);
                            float amount = (float) getFieldValue(cloned, "amount");
                            setFieldValue(cloned, "amount", amount * multiplier);
                            newMap.put(entry.getKey(), cloned);
                        } catch (Exception e) {
                            newMap.put(entry.getKey(), val);
                        }
                    } else {
                        newMap.put(entry.getKey(), val);
                    }
                }
                setFieldValue(owner, fieldName, newMap);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Scales interaction modifiers: Map<String, Int2ObjectMap<StaticModifier>>
     * The resolved form uses Int2ObjectMap with single StaticModifier values (not arrays).
     * Creates NEW map instances to avoid modifying the shared originals.
     */
    @SuppressWarnings("unchecked")
    private static void scaleInteractionModifiers(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return;

            if (mapObj instanceof Map<?, ?> outerMap) {
                Map<Object, Object> newOuter = new java.util.HashMap<>();
                for (Map.Entry<?, ?> outerEntry : outerMap.entrySet()) {
                    Object innerMapObj = outerEntry.getValue();
                    if (innerMapObj instanceof Map<?, ?> innerMap) {
                        // Create a new inner map (fastutil Int2ObjectMap)
                        Map<Object, Object> newInner;
                        try {
                            var ctor = innerMapObj.getClass().getDeclaredConstructor();
                            ctor.setAccessible(true);
                            newInner = (Map<Object, Object>) ctor.newInstance();
                        } catch (Exception e) {
                            newInner = new java.util.HashMap<>();
                        }

                        for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                            Object mod = innerEntry.getValue();
                            if (mod != null) {
                                try {
                                    Object cloned = cloneObjectShallow(mod);
                                    float amount = (float) getFieldValue(cloned, "amount");
                                    setFieldValue(cloned, "amount", amount * multiplier);
                                    newInner.put(innerEntry.getKey(), cloned);
                                } catch (Exception e) {
                                    newInner.put(innerEntry.getKey(), mod);
                                }
                            } else {
                                newInner.put(innerEntry.getKey(), mod);
                            }
                        }
                        newOuter.put(outerEntry.getKey(), newInner);
                    } else {
                        newOuter.put(outerEntry.getKey(), innerMapObj);
                    }
                }
                setFieldValue(owner, fieldName, newOuter);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Scales raw interaction modifiers: Map<String, Map<String, StaticModifier>>
     */
    @SuppressWarnings("unchecked")
    private static void scaleInteractionModifiersRaw(Object owner, String fieldName, float multiplier) {
        try {
            Object mapObj = getFieldValue(owner, fieldName);
            if (mapObj == null) return;

            if (mapObj instanceof Map<?, ?> outerMap) {
                Map<Object, Object> newOuter = new java.util.HashMap<>();
                for (Map.Entry<?, ?> outerEntry : outerMap.entrySet()) {
                    Object innerMapObj = outerEntry.getValue();
                    if (innerMapObj instanceof Map<?, ?> innerMap) {
                        Map<Object, Object> newInner = new java.util.HashMap<>();
                        for (Map.Entry<?, ?> innerEntry : innerMap.entrySet()) {
                            Object mod = innerEntry.getValue();
                            if (mod != null) {
                                try {
                                    Object cloned = cloneObjectShallow(mod);
                                    float amount = (float) getFieldValue(cloned, "amount");
                                    setFieldValue(cloned, "amount", amount * multiplier);
                                    newInner.put(innerEntry.getKey(), cloned);
                                } catch (Exception e) {
                                    newInner.put(innerEntry.getKey(), mod);
                                }
                            } else {
                                newInner.put(innerEntry.getKey(), mod);
                            }
                        }
                        newOuter.put(outerEntry.getKey(), newInner);
                    } else {
                        newOuter.put(outerEntry.getKey(), innerMapObj);
                    }
                }
                setFieldValue(owner, fieldName, newOuter);
            }
        } catch (Exception ignored) {}
    }

    private static Object getFieldValue(Object obj, String fieldName) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + obj.getClass().getName());
    }

    private static void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException("Field '" + fieldName + "' not found in " + obj.getClass().getName());
    }

    /**
     * Gets the MUTABLE backing map from the Item asset map.
     * <p>
     * DefaultAssetMap.getAssetMap() returns an unmodifiable view,
     * so we must access the protected 'assetMap' field directly via reflection
     * to be able to inject our variant Items.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Item> getItemAssetMap() {
        try {
            Method getAssetMap = Item.class.getMethod("getAssetMap");
            Object assetMapObj = getAssetMap.invoke(null);
            if (assetMapObj == null) return null;

            // Access the protected 'assetMap' field directly (the mutable backing map).
            // Do NOT call getAssetMap() on the DefaultAssetMap — that returns
            // an unmodifiable wrapper which throws UnsupportedOperationException on put().
            Class<?> current = assetMapObj.getClass();
            while (current != null && current != Object.class) {
                try {
                    Field f = current.getDeclaredField("assetMap");
                    f.setAccessible(true);
                    Object val = f.get(assetMapObj);
                    if (val instanceof Map) {
                        return (Map<String, Item>) val;
                    }
                } catch (NoSuchFieldException ignored) {}
                current = current.getSuperclass();
            }

            System.out.println(LOG_PREFIX + "WARNING: Could not find 'assetMap' field on "
                    + assetMapObj.getClass().getName());
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "Cannot access item asset map: " + e.getMessage());
        }
        return null;
    }

    private record HytaleTierInfo(String id, int index, int qualityValue) {}
}
