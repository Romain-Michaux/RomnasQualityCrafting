#!/usr/bin/env python3
"""
Recalcule les Tool.Specs des pioches pour :
- Utiliser exactement les gather types et valeurs de base d'Hytale (0 reste 0).
- Appliquer le multiplicateur de qualité du mod (ItemQuality.damageMultiplier)
  pour que plus la pioche est qualitative, plus elle soit efficace.
"""

import json
import os


# Multiplicateurs par qualité (ItemQuality.damageMultiplier)
QUALITY_MULTIPLIERS = {
    "Junk": 0.7,
    "Common": 1.0,
    "Uncommon": 1.2,
    "Rare": 1.4,
    "Epic": 1.6,
    "Legendary": 2.0,
}

# Specs de base Hytale par matériau (valeurs exactes officielles ; 0 reste 0 à l'échelle)
# Chaque entrée : (Power, GatherType, Quality optionnelle)
def _spec(power, gather_type, quality=None):
    d = {"Power": power, "GatherType": gather_type}
    if quality is not None:
        d["Quality"] = quality
    return d


HYTALE_BASE_SPECS = {
    "Crude": [
        _spec(1, "SoftBlocks"),
        _spec(0.35, "Soils"),
        _spec(0.05, "Woods"),
        _spec(0.25, "Rocks", 1),
        _spec(0.5, "Benches"),
        _spec(0.084, "VolcanicRocks"),
        _spec(0.125, "OreCopper"),
        _spec(0.084, "OreIron"),
        _spec(0.084, "OreSilver"),
        _spec(0.084, "OreGold"),
        _spec(0.063, "OreThorium"),
        _spec(0.063, "OreCobalt"),
        _spec(0.05, "OreAdamantite"),
        _spec(0.042, "OreMithril"),
    ],
    "Copper": [
        _spec(1, "SoftBlocks"),
        _spec(0.5, "Soils"),
        _spec(0.05, "Woods"),
        _spec(0.35, "Rocks", 2),
        _spec(0.5, "Benches"),
        _spec(0.12, "VolcanicRocks"),
        _spec(0.25, "OreCopper"),
        _spec(0.125, "OreIron"),
        _spec(0.125, "OreSilver"),
        _spec(0.125, "OreGold"),
        _spec(0.084, "OreThorium"),
        _spec(0.084, "OreCobalt"),
        _spec(0.063, "OreAdamantite"),
        _spec(0.05, "OreMithril"),
    ],
    "Iron": [
        _spec(1, "SoftBlocks"),
        _spec(0.5, "Soils"),
        _spec(0.05, "Woods"),
        _spec(0.5, "Rocks", 3),
        _spec(0.5, "Benches"),
        _spec(0.17, "VolcanicRocks"),
        _spec(0.5, "OreCopper"),
        _spec(0.25, "OreIron"),
        _spec(0.25, "OreSilver"),
        _spec(0.25, "OreGold"),
        _spec(0.125, "OreThorium"),
        _spec(0.125, "OreCobalt"),
        _spec(0.084, "OreAdamantite"),
        _spec(0.063, "OreMithril"),
    ],
    "Thorium": [
        _spec(1, "SoftBlocks"),
        _spec(0.5, "Soils"),
        _spec(0.05, "Woods"),
        _spec(0.5, "Rocks", 4),
        _spec(0.5, "Benches"),
        _spec(0.17, "VolcanicRocks"),
        _spec(0.5, "OreCopper"),
        _spec(0.5, "OreIron"),
        _spec(0.5, "OreSilver"),
        _spec(0.5, "OreGold"),
        _spec(0.25, "OreThorium"),
        _spec(0.25, "OreCobalt"),
        _spec(0.125, "OreAdamantite", 4),
        _spec(0.084, "OreMithril"),
    ],
    "Cobalt": [
        _spec(1, "SoftBlocks"),
        _spec(0.5, "Soils"),
        _spec(0.05, "Woods"),
        _spec(0.5, "Rocks", 4),
        _spec(0.5, "Benches"),
        _spec(0.17, "VolcanicRocks"),
        _spec(0.5, "OreCopper"),
        _spec(0.5, "OreIron"),
        _spec(0.5, "OreSilver"),
        _spec(0.5, "OreGold"),
        _spec(0.25, "OreThorium"),
        _spec(0.25, "OreCobalt"),
        _spec(0.125, "OreAdamantite", 4),
        _spec(0.084, "OreMithril"),
    ],
    "Adamantite": [
        _spec(1, "SoftBlocks"),
        _spec(1.0, "Soils"),
        _spec(0.05, "Woods"),
        _spec(1, "Rocks", 5),
        _spec(0.5, "Benches"),
        _spec(0.34, "VolcanicRocks"),
        _spec(0.5, "OreCopper"),
        _spec(0.5, "OreIron"),
        _spec(0.5, "OreSilver"),
        _spec(0.5, "OreGold"),
        _spec(0.5, "OreThorium"),
        _spec(0.5, "OreCobalt"),
        _spec(0.25, "OreAdamantite", 4),
        _spec(0.125, "OreMithril"),
    ],
    "Mithril": [
        _spec(1, "SoftBlocks"),
        _spec(1.0, "Soils"),
        _spec(0.05, "Woods"),
        _spec(1.0, "Rocks", 6),
        _spec(0.5, "Benches"),
        _spec(0.34, "VolcanicRocks"),
        _spec(1.0, "OreCopper"),
        _spec(1.0, "OreIron"),
        _spec(1.0, "OreSilver"),
        _spec(1.0, "OreGold"),
        _spec(0.5, "OreThorium"),
        _spec(0.5, "OreCobalt"),
        _spec(0.5, "OreAdamantite", 4),
        _spec(0.25, "OreMithril"),
    ],
}


def material_and_quality_from_id(item_id: str):
    """Tool_Pickaxe_Copper_Common -> ("Copper", "Common")"""
    parts = item_id.split("_")
    if len(parts) >= 4 and parts[0] == "Tool" and parts[1] == "Pickaxe":
        return parts[2], parts[3]
    return None, None


def scale_spec(spec: dict, mult: float) -> dict:
    """Applique le multiplicateur de qualité. Si Power est 0, il reste 0."""
    out = {"GatherType": spec["GatherType"]}
    base_power = spec.get("Power", 0)
    if base_power == 0:
        out["Power"] = 0
    else:
        out["Power"] = round(base_power * mult, 4)
    if "Quality" in spec:
        out["Quality"] = spec["Quality"]  # inchangé (tier de roche/minerai)
    return out


def build_specs_for(material: str, quality: str) -> list:
    if material not in HYTALE_BASE_SPECS or quality not in QUALITY_MULTIPLIERS:
        return None
    base = HYTALE_BASE_SPECS[material]
    mult = QUALITY_MULTIPLIERS[quality]
    return [scale_spec(s, mult) for s in base]


def main():
    items_dir = os.path.join(
        os.path.dirname(__file__), "src", "main", "resources", "Server", "Item", "Items"
    )
    updated = 0
    for name in sorted(os.listdir(items_dir)):
        if not name.startswith("Tool_Pickaxe_") or not name.endswith(".json"):
            continue
        path = os.path.join(items_dir, name)
        with open(path, "r", encoding="utf-8") as f:
            data = json.load(f)
        item_id = data.get("Id", name.replace(".json", ""))
        material, quality = material_and_quality_from_id(item_id)
        if not material or not quality:
            continue
        new_specs = build_specs_for(material, quality)
        if not new_specs or "Tool" not in data or "Specs" not in data["Tool"]:
            continue
        data["Tool"]["Specs"] = new_specs
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print("Updated:", name)
        updated += 1
    print("Done. Updated", updated, "pickaxe(s).")


if __name__ == "__main__":
    main()
