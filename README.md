# ⚔️ Romna's Quality Crafting

**RPG-style quality tiers for Hytale — just drop in the JAR and play.**

Every weapon, armor piece, and tool you craft or loot rolls a random quality tier that affects its stats. Higher quality = better damage, armor, durability, and efficiency.

---

## 🎲 Quality Tiers

| Tier | Color | Damage | Armor | Tool Speed | Durability | Sig. Energy |
|------|-------|--------|-------|------------|------------|-------------|
| **Poor** | Gray | ×0.7 | ×0.7 | ×0.7 | ×0.7 | ×1.3 *(worse)* |
| **Common** | White | ×1.0 | ×1.0 | ×1.0 | ×1.0 | ×1.0 |
| **Uncommon** | Green | ×1.2 | ×1.2 | ×1.2 | ×1.15 | ×0.85 |
| **Rare** | Blue | ×1.4 | ×1.4 | ×1.4 | ×1.3 | ×0.7 |
| **Epic** | Purple | ×1.6 | ×1.6 | ×1.6 | ×1.5 | ×0.6 |
| **Legendary** | Gold | ×2.0 | ×2.0 | ×2.0 | ×2.0 | ×0.5 |

> Signature Energy uses an **inverted** multiplier — better quality = lower energy cost.

---

## 📦 Installation

1. Download `RomnasQualityCrafting-2.0.0.jar`
2. Place it in your Hytale server's `Mods/` folder
3. Start the server — a `config.json` is generated automatically
4. Done! Quality tiers are applied to all eligible items

---

## ⚙️ Configuration

The config file is auto-generated on first run. All values are fully customizable.

### Crafting Roll Weights
Control how often each tier appears when crafting:

| Setting | Default | Description |
|---------|---------|-------------|
| `WeightPoor` | 25 | Weight for Poor quality |
| `WeightCommon` | 40 | Weight for Common quality |
| `WeightUncommon` | 20 | Weight for Uncommon quality |
| `WeightRare` | 10 | Weight for Rare quality |
| `WeightEpic` | 4 | Weight for Epic quality |
| `WeightLegendary` | 1 | Weight for Legendary quality |

### Loot Drop Weights
Separate weights for loot drops (more rewarding by default):

| Setting | Default |
|---------|---------|
| `LootWeightPoor` | 10 |
| `LootWeightCommon` | 30 |
| `LootWeightUncommon` | 30 |
| `LootWeightRare` | 18 |
| `LootWeightEpic` | 9 |
| `LootWeightLegendary` | 3 |

### Stat Multipliers
Each stat category has per-tier multipliers (e.g. `DamageMultiplierLegendary`, `ArmorMultiplierPoor`).
See `config.json` for the full list.

### Other Settings

| Setting | Default | Description |
|---------|---------|-------------|
| `LootQualityEnabled` | `true` | Enable quality on loot drops |
| `IgnoredItemPrefixes` | `["Weapon_Bomb", "Weapon_Arrow", ...]` | Item ID prefixes to exclude (consumables, ammo) |

---

## 🔧 Features

- **Zero setup** — works out of the box with any items from any mod
- **Client-side colors** — quality tier shows as colored item name (uses Hytale's built-in quality system)
- **All stats baked** — damage, armor, tool speed, durability, and signature energy are baked into variant items
- **Salvage recipes** — quality variants work correctly on salvage benches
- **Loot drops** — drop tables automatically include quality variants with separate configurable weights
- **Ignore list** — exclude consumables (arrows, bombs, darts, etc.) via config
- **v1.x migration** — existing saves with v1.x quality items are automatically upgraded on player join
- **Old file cleanup** — v1.x generated JSON files are auto-deleted on startup

---

##  Compatibility

- Works alongside other mods — any new weapons/armor/tools are automatically included

---

##  Support and questions

- Comments disabled because it's way harder to track than with Discord.
- **Discord Server:** https://discord.gg/Y7e6hrjqVz

---

*Made by Romna* ❤️
