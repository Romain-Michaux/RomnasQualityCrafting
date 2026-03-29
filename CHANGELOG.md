# Changelog

## v2.0.8 — Hytale March Update Compatibility

### 🔧 Fixed
- **Crash on startup with Hytale 2026.03.26** — `LivingEntityInventoryChangeEvent` was removed from the server; migrated `QualityAssigner` from a global event handler to an ECS `EntityEventSystem<EntityStore, InventoryChangeEvent>`, matching the new inventory event API
- **NullPointerException on shutdown after failed setup** — `shutdown()` now null-checks `migration` before accessing it, so a setup failure no longer causes a secondary crash

### 🔨 Changed
- **QualityAssigner is now an ECS system** — registered via `getEntityStoreRegistry().registerSystem()` instead of `getEventRegistry().registerGlobal()`; uses `Query.any()` to receive events from all entity archetypes
- **Updated `ServerVersion`** — `2026.02.19-1a311a592` → `2026.03.26-89796e57b`
- **Expanded ignore list** — added `Tool_Fertilizer` and `Template_` to the default ignored item prefixes

---

## v2.0.7 — Recipe Cloning Fix

### 🔧 Fixed
- **Duplicate recipes for non-quality items** — `cloneRecipesForVariants()` was cloning every recipe that referenced an eligible item as input, not just salvage recipes; this created 2,000+ spurious recipe clones for unrelated outputs (e.g. `ZC_Composter`, armor recoloring, ore processing). Now only recipes whose ID starts with `Salvage_` are cloned, so quality variants work on salvage benches without polluting other workstations

---

## v2.0.6 — Watering Can State Fix

### 🔧 Fixed
- **Watering can gets quality on fill** — Hytale prefixes state-variant item IDs with `*` (e.g. `*Tool_Watering_Can_State_Filled_Water`); the `startsWith("Tool_Watering_Can")` ignore-list check failed because the actual ID started with `*`. `isIgnored()` now strips the leading `*` before prefix matching
- **Stale server config losing new ignore defaults** — `QualityItemFactory.initIgnoreList()` now merges hardcoded defaults with config values instead of replacing, so newly added default prefixes are always active even on servers with older config files
- **Belt-and-suspenders ignore check** — `QualityRegistry.isEligible()` now re-checks the ignore list at runtime, preventing any ignored item from being treated as eligible regardless of how it entered the set

### 🔨 Changed
- **Removed confusing `isEligibleForQuality` overload** — the single-parameter `isEligibleForQuality(Item)` was public and skipped the ignore list, making it easy to accidentally bypass; renamed to `private isTypeEligible(Item)` so only the full check `isEligibleForQuality(String, Item)` is exposed (credit: QuickBASIC)

---

## v2.0.5 — SimpleEnchantments Compatibility

### ✨ New
- **SimpleEnchantments compatibility bridge** — automatically detects SimpleEnchantments at startup and registers all quality variant items with SE's `EnchantmentApi`, so enchanting scrolls correctly recognize variant weapons/armor/tools as enchantable
- **Metadata preservation** — quality assignment, crafting, and v1.x migration now carry over all existing `ItemStack` metadata (including enchantments from SimpleEnchantments and any other mod-stored data) instead of creating blank items

### 🔧 Fixed
- **Enchanting scrolls rejecting quality items** — SE's `ItemCategoryManager` built its category cache before RQC created variant items, causing all variants to be categorized as `UNKNOWN`; the new bridge re-registers them after variant creation
- **Enchantments lost on quality assignment** — swapping a base item to its quality variant previously created a new `ItemStack` with empty metadata, destroying any enchantments; now uses the 3-arg `ItemStack` constructor to preserve the original `BsonDocument`
- **Server version mismatch** — updated `ServerVersion` from `2026.02.18-f3b8fff95` to `2026.02.19-1a311a592`

---

## v2.0.4 — Ground Drop Glow & Quality Matching Fix

### ✨ New
- **Ground drop glow colors** — items now display quality-appropriate particle effects (`Drop_Common`, `Drop_Uncommon`, `Drop_Rare`, `Drop_Epic`, `Drop_Legendary`) when dropped, making legendary items easily distinguishable from common ones on the ground

### 🔧 Fixed
- **False positive quality suffix matching** — items like `Furniture_Dungeon_Chest_Epic` that coincidentally end with a quality tier name are no longer mistakenly treated as v1.x quality items; added eligibility validation to ensure suffix matching only applies to actual weapons/armor/tools
- **Quality migration validation** — `QualityMigration` now validates base item eligibility before attempting v1.x migration, preventing crashes when processing items with quality-tier-named suffixes

## v2.0.3 — (Previous release)

### 🔧 Fixed
- **Crafted items now show correct rarity immediately** — Update 3 moved crafting to a new `PlayerCraftEvent` that no longer triggers `LivingEntityInventoryChangeEvent`; added a dedicated `PlayerCraftEvent` handler that scans the player's inventory post-craft and assigns quality to the output items
- **Durability no longer doubled on crafted items** — the durability multiplier was being applied twice (once baked into the variant Item asset, then again at runtime in `QualityAssigner`); removed the redundant runtime application so items match the creative menu values

## v2.0.2 — Crafting & Durability Fix

### 🔧 Fixed
- **Crafted items now show correct rarity immediately** — Update 3 moved crafting to a new `PlayerCraftEvent` that no longer triggers `LivingEntityInventoryChangeEvent`; added a dedicated `PlayerCraftEvent` handler that scans the player's inventory post-craft and assigns quality to the output items
- **Durability no longer doubled on crafted items** — the durability multiplier was being applied twice (once baked into the variant Item asset, then again at runtime in `QualityAssigner`); removed the redundant runtime application so items match the creative menu values

## v2.0.1 — Compatibility Update

### 🔧 Fixed
- Updated `ServerVersion` to `2026.02.17-255364b8e` for Hytale Update 3 compatibility
- Added watering can to the base ignore list

## v2.0.0 — Full Rewrite

Complete architecture rewrite. Quality is now baked into variant items at startup instead of applied at runtime.

### ✨ New
- **In-memory variant system** — creates 2,500+ variant items (6 tiers × 419 items) at startup with correct stats
- **Hytale-native quality colors** — items show colored names using Hytale's built-in quality tier system
- **Weapon damage baking** — damage values are cloned into variant interaction chains (DamageEntityInteraction)
- **Tool efficiency scaling** — pickaxe/axe/shovel speed and power scale with quality tier
- **Armor stat scaling** — damage resistance, knockback, stat modifiers all scale correctly
- **Signature Energy scaling** — inverted multiplier (better quality = lower cost)
- **Salvage recipe cloning** — quality variants work on salvage benches automatically
- **Loot drop quality** — drop tables modified at startup with separate configurable weights
- **Ignore list** — configurable item prefix filter to exclude consumables (arrows, bombs, darts, spellbooks, feedbags)
- **v1.x auto-migration** — old quality items are seamlessly upgraded on player join
- **Old file cleanup** — v1.x `RQCGeneratedFiles/` folder auto-deleted on startup
- **French localization** — `fr-FR` language file included

### 🔧 Fixed (from v1.x)
- Unmodifiable asset map crash when injecting variant items
- Zero durability on cloned items (copy constructor missing fields)
- Shared object references between variants causing stat bleed
- Armor tooltip showing base stats instead of quality-adjusted values
- Tool efficiency not scaling with quality
- Weapon damage not reflecting quality tier in combat

### 🗑️ Removed
- On-disk JSON file generation (replaced by in-memory variants)
- Runtime ECS damage system (was no-op — all stats baked into variants)
- Admin commands (`/rqc`)
- Per-item verbose debug logging (cleaned for release)

---

## v1.x — Initial Release

- Quality assigned via item ID suffix (e.g. `Weapon_Sword_Copper_Legendary`)
- Stats applied at runtime via JSON asset files generated on disk
- Basic crafting weight configuration
