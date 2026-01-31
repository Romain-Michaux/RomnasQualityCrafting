# Changelog - RomnasQualityCrafting

## Version 1.1.4 (January 29, 2026)

### 🆕 New Features

#### Separate Multipliers by Equipment Type

Multipliers are now separated for each equipment type, allowing finer balance tuning:

1. **Weapon Damage Multipliers** (`QualityDamageMultiplier*`)
   - Applies only to weapon damage
   - Default values: Poor 0.7, Common 1.0, Uncommon 1.2, Rare 1.4, Epic 1.6, Legendary 2.0

2. **Tool Efficiency Multipliers** (`QualityToolEfficiencyMultiplier*`) - **NEW**
   - Applies to mining/harvesting speed of tools (pickaxes, axes, shovels, etc.)
   - Independent from weapon damage
   - Default values: Poor 0.7, Common 1.0, Uncommon 1.2, Rare 1.4, Epic 1.6, Legendary 2.0

3. **Armor Multipliers** (`QualityArmorMultiplier*`) - **NEW**
   - Applies to damage resistance and stat bonuses of armor
   - Independent from weapon damage and tool efficiency
   - Default values: Poor 0.7, Common 1.0, Uncommon 1.2, Rare 1.4, Epic 1.6, Legendary 2.0

4. **Durability Multipliers** (`QualityDurabilityMultiplier*`)
   - Continues to apply to all item types (unchanged)
   - Default values: Poor 0.7, Common 1.0, Uncommon 1.15, Rare 1.3, Epic 1.5, Legendary 2.0

### 📝 Documentation

- Added `CONFIG_INSTRUCTIONS.md`: Complete configuration guide
- Added `fix_config.ps1`: PowerShell script to automatically add exclusion lists

### 🐛 Known Issue: Exclusion Lists

**Important**: Due to a limitation in Hytale's CODEC system, the `ExcludedIdPrefixes` and `ExcludedItems` fields are **not automatically generated** in the `config/RomnasQualityCrafting.json` file.

**Solutions**:
1. Use the `fix_config.ps1` script to automatically add these lists
2. Or add them manually (see `CONFIG_INSTRUCTIONS.md`)

The mod will use default values if these fields are missing.

### 🎮 Gameplay Impact

With this update, you can now:
- Have highly efficient tools that don't deal much damage in combat
- Have very resistant armor without affecting weapons
- Balance each equipment type separately according to your preferences

**Custom configuration example**:
```json
{
  "QualityDamageMultiplierLegendary": 3.0,        // Legendary weapons: +200% damage
  "QualityToolEfficiencyMultiplierLegendary": 1.5, // Legendary tools: +50% efficiency
  "QualityArmorMultiplierLegendary": 2.5          // Legendary armor: +150% resistance
}
```

---

## Version 1.1.3 (previous versions)

- Added external mod support
- Improved asset generation
- Various bug fixes

