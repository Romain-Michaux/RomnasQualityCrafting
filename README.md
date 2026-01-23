# Romna's Quality Crafting

[![Version](https://img.shields.io/badge/version-1.0.1-blue.svg)](https://github.com/yourusername/RomnasQualityCrafting)
[![Hytale](https://img.shields.io/badge/Hytale-Server%20Mod-green.svg)](https://hytale.com)

A Hytale server mod that adds a random quality system to items when crafting or looting. Each weapon, armor, or tool can now have a quality tier that affects its statistics, making every craft unique and exciting!

## 📋 Description

**Romna's Quality Crafting** transforms your crafting and looting experience by adding 6 different quality tiers for weapons, armor, and tools. Each quality provides bonuses or penalties to item statistics, ensuring that no two crafts are ever the same!

This mod introduces a dynamic quality system where every crafted or looted item has a chance to receive one of six quality levels, each with unique stat multipliers. From poorly crafted items to legendary masterpieces, every item tells a story of its creation.

## ✨ Features

### Current Features (v1.0.1)

- **6 Quality Tiers** : Poor, Common, Uncommon, Rare, Epic, Legendary
- **Balanced Probability System** : 
  - Common : 40%
  - Poor : 25%
  - Uncommon : 20%
  - Rare : 10%
  - Epic : 4%
  - Legendary : 1%
- **Automatic Application** when crafting or looting
- **Stat Multipliers** :
  - **Poor** : -30% damage/protection, -30% durability
  - **Common** : Base statistics (reference)
  - **Uncommon** : +20% damage/protection, +15% durability
  - **Rare** : +40% damage/protection, +30% durability
  - **Epic** : +60% damage/protection, +50% durability
  - **Legendary** : +100% damage/protection, +100% durability
- **Full Support** for :
  - All weapons (swords, axes, etc.)
  - All armor pieces
  - All tools (pickaxes, hatchets, etc.)
- **In-Game Notifications** : Players receive a message when a quality is assigned to their crafted item
- **Pre-generated JSON Assets** : Over 500 JSON files for all item+quality combinations

## 🚀 Installation

1. Download the latest version of the mod from the [releases page](https://github.com/yourusername/RomnasQualityCrafting/releases)
2. Place the `.jar` file in your Hytale server's `plugins` folder
3. Restart your server
4. The mod is now active!

## 📖 Usage

The mod works automatically! No configuration is required.

- **When Crafting** : Each crafted item will automatically receive a random quality
- **When Looting** : Looted items can also receive a random quality
- **Notification** : A message appears in chat to inform you of the assigned quality and bonuses received

### Example In-Game Message

```
[RomnasQualityCrafting] A quality has been assigned: Weapon_Sword_Copper (Epic)
Damage: +60% | Durability: +50%
```

## 🗺️ Roadmap

### Upcoming Features

#### 🔨 Item Reforging (Planned)
- **Reforge existing items** to change their quality tier
- Consume materials and resources to attempt reforging
- Risk/reward system where reforging might improve or degrade quality
- Special reforging stations or anvils
- Preserve item enchantments and custom properties during reforging

#### 🎲 Random Stats (Planned)
- **Additional random stat rolls** beyond quality tiers
- Secondary stats like:
  - Critical hit chance
  - Attack speed modifiers
  - Movement speed bonuses
  - Resource gathering efficiency
  - Special effects and procs
- Stat ranges vary by quality tier
- Multiple stat rolls per item for truly unique gear

#### ✨ Runeforging (Planned)
- **Advanced crafting system** using special runes
- Apply runes to items for permanent stat enhancements
- Different rune types for different stat categories
- Rune combinations create powerful synergies
- Rare and legendary runes with unique effects
- Runeforging recipes and progression system

### Future Considerations

- Quality-specific visual effects and particles
- Quality-based item naming system
- Integration with economy systems
- Quality trading and marketplace features
- Custom quality tiers for special events
- API for other mods to interact with the quality system

## 🎮 Compatibility

- **Hytale Version** : Compatible with all Hytale server versions
- **Other Mods** : Compatible with most other server mods
- **Performance** : Optimized to not impact server performance

## 📁 Project Structure

```
RomnasQualityCrafting/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── dev/hytalemodding/
│   │   │       ├── ExamplePlugin.java          # Main entry point
│   │   │       ├── commands/                   # Mod commands
│   │   │       ├── events/                     # Event handlers
│   │   │       └── quality/                   # Quality system
│   │   └── resources/
│   │       ├── manifest.json                   # Mod manifest
│   │       └── Server/
│   │           ├── Item/
│   │           │   ├── Items/                  # Item JSON assets
│   │           │   └── Qualities/              # Quality configurations
│   │           └── Languages/
│   │               └── en-US/
│   │                   └── server.lang         # Translations
├── pom.xml                                     # Maven configuration
├── README.md                                   # This file
└── CHANGELOG.md                                # Version history
```

## 🔧 Development

### Prerequisites

- Java 25+
- Maven 3.6+
- Hytale Server SDK

### Compilation

```bash
mvn clean package
```

The `.jar` file will be generated in the `target/` folder.

### Contributing

Contributions are welcome! Feel free to open an issue or pull request.

## 📝 License

This project is licensed under [MIT](LICENSE) (or specify your license).

## 👥 Authors

- **Romna** - Initial development

## 🙏 Acknowledgments

- Hypixel team for the Hytale SDK
- Hytale community for support

## 📞 Support

For any questions or issues:
- Open an [issue](https://github.com/yourusername/RomnasQualityCrafting/issues) on GitHub
- Contact us on [Discord](https://discord.gg/your-server) (if applicable)

## 🔗 Links

- [GitHub](https://github.com/yourusername/RomnasQualityCrafting)
- [Wiki](https://github.com/yourusername/RomnasQualityCrafting/wiki) (if applicable)
- [Hytale Website](https://hytale.com)

---

## 📋 Changelog

### [1.0.1] - 2024-12-XX

#### 🔧 Fixed
- Bug fixes and stability improvements

#### 📝 Changed
- Code cleanup and optimizations

### [1.0.0] - 2024-12-XX

#### ✨ Added
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

#### 🔧 Technical
- Modular architecture with separation of concerns
- Java reflection usage for packet interception
- ThreadLocal system for managing ItemStack during serialization
- Quality cache per ItemStack to avoid recalculations
- Robust error handling with detailed logs
- Support for complex inventory transactions (SlotTransaction, ListTransaction)

#### 📝 Documentation
- Complete README.md with installation and usage instructions
- CHANGELOG.md for version tracking
- Code source comments
- Documentation of probabilities and multipliers

#### 🌐 Localization
- English translation files (en-US)
- Support for future extension to other languages
- Localization keys for all qualities and items

#### 🎨 Assets
- UI textures for quality tooltips
- UI textures for inventory slots by quality
- Particle configuration for item entities
- Custom colors for each quality

---

**Note** : This mod is a community project and is not officially supported by Hypixel Studios.
