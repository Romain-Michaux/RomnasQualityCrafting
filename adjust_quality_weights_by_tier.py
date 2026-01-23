#!/usr/bin/env python3
"""
Script pour ajuster les poids des qualités selon le tier dans les fichiers de drops.
Plus le tier est élevé, plus les qualités élevées ont de chances d'apparaître.
"""

import json
import os
import re
from pathlib import Path

# Définition des poids par tier
# Format: {tier: {quality: weight}}
TIER_QUALITY_WEIGHTS = {
    1: {
        "Poor": 30,
        "Common": 40,
        "Uncommon": 20,
        "Rare": 8,
        "Epic": 2,
        "Legendary": 0.5
    },
    2: {
        "Poor": 20,
        "Common": 35,
        "Uncommon": 25,
        "Rare": 15,
        "Epic": 4,
        "Legendary": 1
    },
    3: {
        "Poor": 10,
        "Common": 25,
        "Uncommon": 30,
        "Rare": 25,
        "Epic": 8,
        "Legendary": 2
    },
    4: {
        "Poor": 5,
        "Common": 15,
        "Uncommon": 25,
        "Rare": 30,
        "Epic": 20,
        "Legendary": 5
    }
}

QUALITIES = ["Poor", "Common", "Uncommon", "Rare", "Epic", "Legendary"]

# Chemin vers les fichiers de drops
DROPS_PATH = Path("src/main/resources/Server/Drops/Prefabs")

def extract_tier_from_filename(filename):
    """Extrait le numéro de tier du nom de fichier."""
    match = re.search(r'Tier(\d+)', filename)
    if match:
        return int(match.group(1))
    return None

def is_quality_item(item_id):
    """Vérifie si un ItemId contient une qualité."""
    for quality in QUALITIES:
        if item_id.endswith(f"_{quality}"):
            return quality
    return None

def update_quality_weights(container, tier):
    """Met à jour récursivement les poids des qualités dans un container."""
    if not isinstance(container, dict):
        return
    
    container_type = container.get("Type")
    
    if container_type == "Single":
        # Vérifier si c'est un item avec qualité
        item = container.get("Item")
        if item and isinstance(item, dict):
            item_id = item.get("ItemId")
            if item_id:
                quality = is_quality_item(item_id)
                if quality and tier in TIER_QUALITY_WEIGHTS:
                    # Mettre à jour le poids selon le tier
                    new_weight = TIER_QUALITY_WEIGHTS[tier][quality]
                    container["Weight"] = new_weight
    
    elif container_type in ["Multiple", "Choice"]:
        # Traiter les containers enfants
        containers = container.get("Containers", [])
        if containers:
            for child in containers:
                update_quality_weights(child, tier)

def process_drop_file(file_path):
    """Traite un fichier de drop et ajuste les poids des qualités."""
    try:
        # Extraire le tier du nom de fichier
        tier = extract_tier_from_filename(file_path.name)
        if tier is None:
            print(f"[SKIP] Impossible d'extraire le tier de: {file_path.name}")
            return False
        
        if tier not in TIER_QUALITY_WEIGHTS:
            print(f"[SKIP] Tier {tier} non géré: {file_path.name}")
            return False
        
        # Lire le fichier
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # Traiter le container racine
        if "Container" in data:
            update_quality_weights(data["Container"], tier)
            
            # Sauvegarder le fichier modifié
            with open(file_path, 'w', encoding='utf-8') as f:
                json.dump(data, f, indent=2, ensure_ascii=False)
            return True
        
        return False
    except Exception as e:
        print(f"Erreur lors du traitement de {file_path}: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """Fonction principale."""
    if not DROPS_PATH.exists():
        print(f"Erreur: Le chemin des drops n'existe pas: {DROPS_PATH}")
        return
    
    # Trouver tous les fichiers Tier dans le dossier Prefabs
    tier_files = [f for f in DROPS_PATH.glob("*Encounters_Tier*.json")]
    
    print(f"Trouvé {len(tier_files)} fichiers Tier à traiter...")
    print("\nPoids par tier:")
    for tier, weights in TIER_QUALITY_WEIGHTS.items():
        print(f"  Tier {tier}: {weights}")
    print()
    
    modified_count = 0
    for tier_file in tier_files:
        if process_drop_file(tier_file):
            modified_count += 1
            tier = extract_tier_from_filename(tier_file.name)
            print(f"[OK] Tier {tier}: {tier_file.name}")
    
    print(f"\nTerminé! {modified_count} fichiers modifiés sur {len(tier_files)}.")

if __name__ == "__main__":
    main()
