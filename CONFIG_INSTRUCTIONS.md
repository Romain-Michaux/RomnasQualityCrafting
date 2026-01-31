# Configuration Instructions - RomnasQualityCrafting

## Configuration File Location

When the server starts, Hytale automatically generates a configuration file at:
```
mods/RomnasQualityCrafting/config.json
```

## Important: ExcludedIdPrefixes and ExcludedItems

⚠️ **Known Issue**: Due to Hytale's CODEC system not supporting arrays/lists, the `ExcludedIdPrefixes` and `ExcludedItems` fields are **NOT automatically generated** in the config file.

### Solution

You need to **manually add** these fields to your config file after the first server start.

### Example Configuration

After the server generates the initial config file, open `config/config.json` and add these fields at the end (before the closing brace):

```json
{
  "QualityWeightPoor": 25,
  "QualityWeightCommon": 40,
  ...
  "QualityArmorMultiplierLegendary": 2.0,
  
  "ExcludedIdPrefixes": [
    "Bench_",
    "Weapon_Arrow_",
    "Weapon_Bomb_",
    "Weapon_Dart_",
    "Weapon_Grenade_",
    "Weapon_Kunai_",
    "Debug_",
    "Test_",
    "Template_"
  ],
  "ExcludedItems": [
    "Weapon_Bomb",
    "Farming_Collar",
    "Halloween_Broomstick",
    "Tool_Feedbag",
    "Tool_Fertilizer",
    "Tool_Map"
  ]
}
```

### What These Fields Do

- **ExcludedIdPrefixes**: Items with IDs starting with these prefixes will NOT have quality variants generated
- **ExcludedItems**: Specific items by exact ID that will NOT have quality variants generated

### Default Values

If these fields are missing from your config file, the mod will use the default values shown above.

## Configuration Parameters

### Quality Weights (Crafting Chances)
- `QualityWeightPoor`: Weight for Poor quality (default: 25)
- `QualityWeightCommon`: Weight for Common quality (default: 40)
- `QualityWeightUncommon`: Weight for Uncommon quality (default: 20)
- `QualityWeightRare`: Weight for Rare quality (default: 10)
- `QualityWeightEpic`: Weight for Epic quality (default: 4)
- `QualityWeightLegendary`: Weight for Legendary quality (default: 1)

### Damage Multipliers (Weapons)
Controls weapon damage for each quality level:
- `QualityDamageMultiplierPoor`: 0.7 (30% less damage)
- `QualityDamageMultiplierCommon`: 1.0 (normal damage)
- `QualityDamageMultiplierUncommon`: 1.2 (20% more damage)
- `QualityDamageMultiplierRare`: 1.4 (40% more damage)
- `QualityDamageMultiplierEpic`: 1.6 (60% more damage)
- `QualityDamageMultiplierLegendary`: 2.0 (100% more damage)

### Tool Efficiency Multipliers (Separate from Weapons)
Controls tool mining/harvesting speed for each quality level:
- `QualityToolEfficiencyMultiplierPoor`: 0.7
- `QualityToolEfficiencyMultiplierCommon`: 1.0
- `QualityToolEfficiencyMultiplierUncommon`: 1.2
- `QualityToolEfficiencyMultiplierRare`: 1.4
- `QualityToolEfficiencyMultiplierEpic`: 1.6
- `QualityToolEfficiencyMultiplierLegendary`: 2.0

### Armor Multipliers (Separate from Weapons)
Controls armor resistance and stat bonuses for each quality level:
- `QualityArmorMultiplierPoor`: 0.7
- `QualityArmorMultiplierCommon`: 1.0
- `QualityArmorMultiplierUncommon`: 1.2
- `QualityArmorMultiplierRare`: 1.4
- `QualityArmorMultiplierEpic`: 1.6
- `QualityArmorMultiplierLegendary`: 2.0

### Durability Multipliers (All Items)
Controls item durability for each quality level:
- `QualityDurabilityMultiplierPoor`: 0.7
- `QualityDurabilityMultiplierCommon`: 1.0
- `QualityDurabilityMultiplierUncommon`: 1.15
- `QualityDurabilityMultiplierRare`: 1.3
- `QualityDurabilityMultiplierEpic`: 1.5
- `QualityDurabilityMultiplierLegendary`: 2.0

### Other Options
- `ForceResetAssets`: Set to `true` to force regeneration of all quality items (resets to `false` after)
- `ExternalModsCompatEnabled`: Set to `true` to scan and create quality variants for items from other mods
- `CustomAssetsPath`: **NEW in 1.2.0** - Specify custom path to Assets.zip or extracted assets folder (empty by default)

### Assets Detection (NEW in 1.2.0)

The `CustomAssetsPath` field allows you to manually specify where the mod should look for Hytale's asset files:

```json
{
  "CustomAssetsPath": ""
}
```

**When to use this:**
- Your Hytale installation is in a non-standard location
- Items are not being generated
- You see "Assets.zip not found" errors

**Examples:**

Windows (ZIP file):
```json
"CustomAssetsPath": "C:/Hytale/install/release/package/game/latest/Assets.zip"
```

Windows (extracted folder):
```json
"CustomAssetsPath": "C:/Hytale/HytaleAssets"
```

Linux:
```json
"CustomAssetsPath": "/home/user/hytale/install/release/package/game/latest/Assets.zip"
```

**Important Notes:**
- Use forward slashes (`/`) even on Windows
- Leave empty for automatic detection
- See `ASSETS_DETECTION_GUIDE.md` for more details

## Example: Custom Configuration

Here's an example of a custom configuration with different values:

```json
{
  "QualityWeightPoor": 10,
  "QualityWeightCommon": 50,
  "QualityWeightUncommon": 25,
  "QualityWeightRare": 10,
  "QualityWeightEpic": 4,
  "QualityWeightLegendary": 1,
  
  "QualityDamageMultiplierPoor": 0.5,
  "QualityDamageMultiplierLegendary": 3.0,
  
  "QualityToolEfficiencyMultiplierPoor": 0.8,
  "QualityToolEfficiencyMultiplierLegendary": 1.5,
  
  "QualityArmorMultiplierPoor": 0.6,
  "QualityArmorMultiplierLegendary": 2.5,
  
  "CustomAssetsPath": "",
  "ExternalModsCompatEnabled": true,
  
  "ExcludedIdPrefixes": [
    "Bench_",
    "Debug_"
  ],
  "ExcludedItems": [
    "Tool_Map"
  ]
}
```

## Notes

- The mod reads these values when the server starts
- Changes require a server restart to take effect
- Setting `ForceResetAssets` to `true` will regenerate all quality item files on next startup
