# RomnasQualityCrafting

**Version:** 1.1.4  
**Author:** Romain Michaux  
**Compatible with:** Hytale Server 1.0-SNAPSHOT

## 📖 Description

RomnasQualityCrafting is a Hytale mod that adds a dynamic quality system to items. Each time an item is crafted, it receives a random quality that affects its statistics (damage, efficiency, durability, resistance).

### Available Qualities
- ⚫ **Poor (Junk)** - Below average quality
- ⚪ **Common** - Standard quality
- 🟢 **Uncommon** - Uncommon quality
- 🔵 **Rare** - Rare quality
- 🟣 **Epic** - Epic quality
- 🟠 **Legendary** - Legendary quality

## ✨ Features

### Separate Multipliers by Equipment Type
- **Weapons**: Controls combat damage
- **Tools**: Controls mining/harvesting efficiency
- **Armor**: Controls resistance and stat bonuses
- **Durability**: Applies to all items

### Flexible Configuration
- Adjust the chances of obtaining each quality
- Customize multipliers for each equipment type
- Exclude certain items or prefixes from quality generation
- External mod support

### Automatic Generation
- Quality variants are automatically generated at startup
- Support for custom textures and models
- Compatible with items from other mods (if enabled)

## 🚀 Installation

1. Download the `RomnasQualityCrafting-x.x.x.jar` file
2. Place it in the `mods/` folder of your Hytale server
3. Start the server
4. The mod will automatically generate configuration files and assets
5. ⚠️⚠️ Restart the server/save to fully load the newly generated JSON items

## ⚙️ Configuration

### Configuration File
The configuration file is located at: `config/RomnasQualityCrafting.json`

### ⚠️ Important: Exclusion Lists
The `ExcludedIdPrefixes` and `ExcludedItems` fields must be **manually added** to the config file after the first generation.

**Quick solution**: Use the provided `fix_config.ps1` script.

For more details, see **CONFIG_INSTRUCTIONS.md**.

## 📊 Configuration Examples

### Balanced Configuration (default)
```json
{
  "QualityWeightCommon": 40,
  "QualityDamageMultiplierLegendary": 2.0,
  "QualityToolEfficiencyMultiplierLegendary": 2.0,
  "QualityArmorMultiplierLegendary": 2.0
}
```

### PvP-Focused Configuration (powerful weapons)
```json
{
  "QualityWeightRare": 15,
  "QualityWeightEpic": 8,
  "QualityDamageMultiplierLegendary": 3.0,
  "QualityToolEfficiencyMultiplierLegendary": 1.5,
  "QualityArmorMultiplierLegendary": 2.5
}
```

### Casual Configuration (less RNG)
```json
{
  "QualityWeightPoor": 10,
  "QualityWeightCommon": 60,
  "QualityWeightUncommon": 20,
  "QualityDamageMultiplierPoor": 0.9,
  "QualityDamageMultiplierLegendary": 1.5
}
```

## 📚 Documentation

- **CHANGELOG.md** - Version history and new features
- **CONFIG_INSTRUCTIONS.md** - Complete configuration guide
- **fix_config.ps1** - PowerShell script to fix the config file

## 🐛 Known Issues

### Exclusion Lists Not Generated
**Problem**: The `ExcludedIdPrefixes` and `ExcludedItems` fields are not automatically generated in the config file due to a limitation in Hytale's CODEC system.

**Solution**: 
1. Use the `fix_config.ps1` script
2. Or add them manually (see CONFIG_INSTRUCTIONS.md)
3. The mod will use default values if these fields are missing

## 🔧 Commands

No commands are required. The mod works automatically.

## 📝 Notes

- Qualities are assigned when crafting or generated in chests
- A server restart is required to apply config changes
- Setting `ForceResetAssets` to `true` forces asset regeneration

## 🤝 Support

To report a bug or request a feature, please join my Discord server: https://discord.gg/PMUuxH2ueW

## 📜 License

All rights reserved

## 🙏 Acknowledgments

Thanks to the Hytale community for the support and feedback!

---

**Developed with ❤️ for the Hytale community**
