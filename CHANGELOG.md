# Changelog

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
