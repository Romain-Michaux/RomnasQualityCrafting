package dev.hytalemodding.compat;

import dev.hytalemodding.quality.ItemQuality;
import dev.hytalemodding.quality.QualityTierMapper;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * Optional compatibility bridge for SimpleEnchantments.
 *
 * SimpleEnchantments builds its item category cache at startup from the Item
 * asset map. Our quality variant items are created AFTER that, so SE never
 * categorizes them — making them un-enchantable.
 *
 * This bridge uses reflection to call SE's public API (EnchantmentApi) to
 * register each quality variant item with the same category as its base item.
 * All access is via reflection so there is no compile-time dependency on SE.
 *
 * If SE is not installed, initialization is silently skipped.
 */
public final class SimpleEnchantmentsBridge {

    private static final String LOG_PREFIX = "[RQC] SE-Compat: ";

    // SE API class names
    private static final String API_PROVIDER_CLASS = "org.herolias.plugin.api.EnchantmentApiProvider";
    private static final String CATEGORY_MANAGER_CLASS = "org.herolias.plugin.enchantment.ItemCategoryManager";

    private SimpleEnchantmentsBridge() {}

    /**
     * Attempts to register all quality variant items with SimpleEnchantments'
     * item category system. Each variant inherits the category of its base item.
     *
     * @param tierMapper the fully initialized tier mapper with all variants
     * @return the number of variants successfully registered, or -1 if SE is not present
     */
    public static int registerVariants(QualityTierMapper tierMapper) {
        try {
            // 1. Check if SE is loaded by trying to access its API provider
            Class<?> apiProviderClass = Class.forName(API_PROVIDER_CLASS);
            Method getApiMethod = apiProviderClass.getMethod("get");
            Object api = getApiMethod.invoke(null);

            if (api == null) {
                System.out.println(LOG_PREFIX + "SimpleEnchantments API not initialized yet, skipping.");
                return -1;
            }

            // 2. Get the registerItemToCategory method
            Method registerMethod = api.getClass().getMethod("registerItemToCategory", String.class, String.class);

            // 3. Get the ItemCategoryManager to look up base item categories
            Class<?> categoryManagerClass = Class.forName(CATEGORY_MANAGER_CLASS);
            Method getInstanceMethod = categoryManagerClass.getMethod("getInstance");
            Object categoryManager = getInstanceMethod.invoke(null);

            Method categorizeMethod = categoryManagerClass.getMethod("categorizeItem", String.class);

            // 4. Get the getId() method from ItemCategory
            Method getIdMethod = null;

            // 5. Iterate all variant-to-base mappings and register each variant
            Map<String, String> variantToBase = tierMapper.getVariantToBaseMap();
            int registered = 0;
            int skipped = 0;

            for (Map.Entry<String, String> entry : variantToBase.entrySet()) {
                String variantId = entry.getKey();
                String baseId = entry.getValue();

                try {
                    // Get the category of the base item
                    Object category = categorizeMethod.invoke(categoryManager, baseId);
                    if (category == null) {
                        skipped++;
                        continue;
                    }

                    // Lazily resolve getId() method
                    if (getIdMethod == null) {
                        getIdMethod = category.getClass().getMethod("getId");
                    }

                    // Check if category is UNKNOWN (not enchantable)
                    String categoryId = (String) getIdMethod.invoke(category);
                    if (categoryId == null || "unknown".equalsIgnoreCase(categoryId)) {
                        skipped++;
                        continue;
                    }

                    // Register the variant with the same category as base
                    registerMethod.invoke(api, variantId, categoryId);
                    registered++;

                } catch (Exception e) {
                    skipped++;
                }
            }

            System.out.println(LOG_PREFIX + "Registered " + registered
                    + " variant(s) with SimpleEnchantments (" + skipped + " skipped)");
            return registered;

        } catch (ClassNotFoundException e) {
            // SE not installed — perfectly fine, skip silently
            return -1;
        } catch (Exception e) {
            System.out.println(LOG_PREFIX + "Failed to integrate with SimpleEnchantments: "
                    + e.getClass().getSimpleName() + " - " + e.getMessage());
            return -1;
        }
    }
}
