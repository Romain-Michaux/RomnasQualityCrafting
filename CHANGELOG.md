# Changelog — RomnasQualityCrafting

## Version 2.0.0 (February 15, 2026)

### 🚀 Complete Rewrite — Zero-Setup Architecture

v2.0 is a ground-up rewrite that eliminates all setup complexity. The mod now generates quality variants entirely in memory at startup, with no file I/O, no Assets.zip scanning, and no server restart required.

### ✨ What's New

#### Zero-Setup Installation
- **Install and play** — drop the JAR in your mods folder, start the server, done
- No more `Assets.zip` path configuration
- No more `fix_config.ps1` scripts
- No more server restart after first install
- No more `RQCGeneratedFiles` folder

#### In-Memory Quality Registration
- Quality variants are generated from the loaded item registry at startup
- Uses Hytale's Asset Registry pattern for proper item registration
- All weapons, armor, and tools automatically get 6 quality variants
- No JSON files written to disk

#### Metadata-Based Quality Tracking
- Quality is stored as item metadata (`rqc_quality`) for reliable tracking
- Quality also encoded in item ID for visual color display
- Dual-source lookup (metadata first, ID fallback) for maximum compatibility

#### ECS Event System
- Uses modern Hytale ECS events (`LivingEntityInventoryChangeEvent`)
- Handles crafting, looting, trading, and manual pickup
- Replaces deprecated event handlers from v1.x

#### Automatic v1.x Migration
- Old quality items are automatically converted on player join
- Durability ratio preserved during migration
- Items without matching v2.0 variants safely revert to base item
- Migration status shown to player in chat

#### Admin Commands
- `/rqc info` — Show quality info of held item
- `/rqc stats` — Show registration and migration statistics

### 📐 Simplified Configuration

Config fields renamed for clarity:
| v1.x Field | v2.0 Field |
|-----------|-----------|
| `QualityWeightPoor` | `WeightPoor` |
| `QualityDamageMultiplierPoor` | `DamageMultiplierPoor` |
| `QualityToolEfficiencyMultiplierPoor` | `ToolMultiplierPoor` |
| `QualityArmorMultiplierPoor` | `ArmorMultiplierPoor` |
| `QualityDurabilityMultiplierPoor` | `DurabilityMultiplierPoor` |

Removed fields (no longer needed):
- `CustomAssetsPath`
- `CustomGlobalModsPath`
- `ExternalModsCompatEnabled`
- `ForceResetAssets`

### 📉 Code Reduction

| Component | v1.x | v2.0 |
|-----------|------|------|
| Total Java lines | ~5,000+ | ~800 |
| Config fields | 30+ | 24 |
| Documentation files | 5 | 1 (README) |
| Required setup steps | 5 | 1 (install) |
| External scripts | 2 | 0 |

### ⚠️ Breaking Changes

- Config field names changed (see table above). Old fields are silently ignored.
- `RQCGeneratedFiles` folder is no longer created or used. Can be safely deleted.
- Old quality items are automatically migrated on first player join.

---

## Version 1.2.0 (January 31, 2026)

- Fixed external mod detection (only process items from loaded mods)
- Improved asset detection and generation
- Simplified configuration and logging

## Version 1.1.4 (January 29, 2026)

- Added separate multipliers per equipment type (weapon/tool/armor/durability)
- Added `CONFIG_INSTRUCTIONS.md` and `fix_config.ps1`

## Version 1.1.3 and earlier

- Added external mod support
- Improved asset generation
- Various bug fixes
