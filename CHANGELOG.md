# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.3] - 2026-01-25

### 🔧 Fixed
- Pioches qui ne minaient plus de minerais

### ✨ Added
- Traductions françaises (fr-FR)

## [1.0.2] - 2026-01-24

### ✨ Added
- Reforge kits for upgrading item quality
- New interface for reforging and item stats (only accessible through creative mode)

### 📝 Changed
- All loot tables have been modified: higher tier chests now loot higher quality items
- Increased legendary item chances in higher tier chests

## [1.0.1] - 2024-12-XX

### 🔧 Fixed
- Bug fixes and stability improvements

### 📝 Changed
- Code cleanup and optimizations

## [1.0.0] - 2024-12-XX

### ✨ Added
- Random quality system for items (weapons, armor, tools)
- 6 quality tiers: Poor, Common, Uncommon, Rare, Epic, Legendary
- Automatic quality application during crafting
- Automatic quality application during looting
- Stat multipliers for each quality tier:
  - Poor: -30% damage/protection, -30% durability
  - Common: Base statistics (reference)
  - Uncommon: +20% damage/protection, +15% durability
  - Rare: +40% damage/protection, +30% durability
  - Epic: +60% damage/protection, +50% durability
  - Legendary: +100% damage/protection, +100% durability
- Balanced probability system for quality distribution
- In-game notifications when quality is assigned
- Full support for all weapons (swords, axes, etc.)
- Full support for all armor pieces
- Full support for all tools (pickaxes, hatchets, etc.)
- Over 500 pre-generated JSON files for all item+quality combinations
- Metadata management system for storing qualities
- Packet interceptor for dynamic item modification
- Event handler for detecting crafts and loots
- Automatic JSON asset verification before quality application
- Protection against infinite loops when modifying items
- Informative messages with detailed statistics for each quality

### 🔧 Technical
- Modular architecture with separation of concerns
- Java reflection usage for packet interception
- ThreadLocal system for managing ItemStack during serialization
- Quality cache per ItemStack to avoid recalculations
- Robust error handling with detailed logs
- Support for complex inventory transactions (SlotTransaction, ListTransaction)

### 📝 Documentation
- Complete README.md with installation and usage instructions
- CHANGELOG.md for version tracking
- Code source comments
- Documentation of probabilities and multipliers

### 🌐 Localization
- English translation files (en-US)
- Support for future extension to other languages
- Localization keys for all qualities and items

### 🎨 Assets
- UI textures for quality tooltips
- UI textures for inventory slots by quality
- Particle configuration for item entities
- Custom colors for each quality

---

## Version Format

- **MAJOR** : Incompatible changes with previous versions
- **MINOR** : New backward-compatible features
- **PATCH** : Backward-compatible bug fixes

---

## Notes

- This initial version (1.0.0) represents the first stable release of the mod
- All core features are implemented and tested
- The mod is ready for public distribution
