# RomnasQualityCrafting

**Version:** 2.0.0  
**Author:** Romain Michaux  
**Compatible with:** Hytale Server 1.0-SNAPSHOT

## 📖 Description

RomnasQualityCrafting adds an RPG-style quality system to Hytale. Every time a weapon, armor piece, or tool is crafted or looted, it receives a random quality tier that modifies its stats.

### Quality Tiers
| Tier | Color | Damage | Tool Efficiency | Armor | Durability |
|------|-------|--------|----------------|-------|------------|
| ⚫ **Poor (Junk)** | Gray | 0.7× | 0.7× | 0.7× | 0.7× |
| ⚪ **Common** | White | 1.0× | 1.0× | 1.0× | 1.0× |
| 🟢 **Uncommon** | Green | 1.2× | 1.2× | 1.2× | 1.15× |
| 🔵 **Rare** | Blue | 1.4× | 1.4× | 1.4× | 1.3× |
| 🟣 **Epic** | Purple | 1.6× | 1.6× | 1.6× | 1.5× |
| 🟠 **Legendary** | Orange | 2.0× | 2.0× | 2.0× | 2.0× |

## 🚀 Installation

1. Download `RomnasQualityCrafting-2.0.0.jar`
2. Place it in your server's `mods/` folder
3. Start the server
4. **Done!** Quality variants are generated in memory — no restart needed.

That's it. No configuration required. No extra setup steps.

## ⚙️ Configuration (Optional)

A config file is auto-generated at `config/config.json`. You can customize:

### Quality Weights (Drop Chances)
```json
{
  "WeightPoor": 25,
  "WeightCommon": 40,
  "WeightUncommon": 20,
  "WeightRare": 10,
  "WeightEpic": 4,
  "WeightLegendary": 1
}
```

### Stat Multipliers
Each equipment type has independent multipliers:

- **`DamageMultiplier*`** — Weapon damage scaling
- **`ToolMultiplier*`** — Mining/harvesting efficiency scaling
- **`ArmorMultiplier*`** — Damage resistance scaling
- **`DurabilityMultiplier*`** — Durability scaling (all item types)

Example: Make Legendary weapons extremely powerful but tools only slightly better:
```json
{
  "DamageMultiplierLegendary": 3.0,
  "ToolMultiplierLegendary": 1.5,
  "ArmorMultiplierLegendary": 2.5,
  "DurabilityMultiplierLegendary": 2.0
}
```

**Note:** Config changes require a server restart to take effect.

## 📋 Commands

| Command | Description |
|---------|-------------|
| `/rqc info` | Show quality info of held item |
| `/rqc stats` | Show registration and migration statistics |

## 🔄 Upgrading from v1.x

v2.0 is a complete rewrite. Key changes:

- **No more Assets.zip scanning** — quality variants are generated from the loaded item registry
- **No more JSON file generation** — everything happens in memory
- **No more server restart required** after first install
- **Automatic migration** — old v1.x quality items are converted on player join

### What happens to existing quality items?
When a player with v1.x quality items joins the server:
1. Items with matching v2.0 variants → automatically migrated with metadata
2. Items without matching variants → reverted to base item (safe fallback)
3. Durability ratio is preserved during migration

### Removed config fields
These v1.x config fields are no longer needed and are silently ignored:
- `CustomAssetsPath`
- `CustomGlobalModsPath`
- `ExternalModsCompatEnabled`
- `ForceResetAssets`
- `ExcludedIdPrefixes`
- `ExcludedItems`

### Removed files
You can safely delete:
- `RQCGeneratedFiles/` folder in your save directory
- `ASSETS_DETECTION_GUIDE.md`
- `EXTERNAL_MODS_GUIDE.md`
- `CONFIG_INSTRUCTIONS.md`
- `fix_config.ps1`
- `cleanup_script.ps1`

## 🏗️ Architecture (for developers)

```
RomnasQualityCrafting.java     — Main plugin entry point
├── config/
│   └── QualityConfig.java     — CODEC-based config (weights + multipliers)
├── quality/
│   ├── ItemQuality.java       — Quality enum with multiplier logic
│   ├── QualityItemFactory.java — In-memory item cloning + stat modification
│   ├── QualityRegistry.java   — Startup variant registration in asset map
│   └── QualityAssigner.java   — ECS event handlers for runtime assignment
├── migration/
│   └── QualityMigration.java  — v1.x → v2.0 automatic migration
└── commands/
    └── QualityCommands.java   — /rqc admin commands
```

Total: ~800 lines of Java (down from ~5,000+ in v1.x)

## 📄 License

MIT License
