#!/usr/bin/env python3
"""
Script pour modifier les fichiers JSON de drops pour utiliser les variantes avec qualité.
Remplace les items Weapon_*, Armor_*, Tool_* par des Choice avec les 6 qualités.
"""

import json
import os
import sys
from pathlib import Path

# Probabilités des qualités (poids)
QUALITY_WEIGHTS = {
    "Common": 40,
    "Poor": 25,
    "Uncommon": 20,
    "Rare": 10,
    "Epic": 4,
    "Legendary": 1
}

QUALITIES = ["Common", "Poor", "Uncommon", "Rare", "Epic", "Legendary"]

# Chemin vers les assets JSON du projet pour vérifier si les items existent
PROJECT_ITEMS_PATH = Path("src/main/resources/Server/Item/Items")

# Chemin vers les fichiers de drops à modifier
DROPS_PATH = Path(r"C:\Users\micha\Desktop\Hytale Base Server JSON\Server\Drops")

def has_quality_in_id(item_id):
    """Vérifie si un ItemId contient déjà une qualité."""
    for quality in QUALITIES:
        if item_id.endswith(f"_{quality}"):
            return True
    return False

def item_has_assets(base_item_id):
    """Vérifie si des assets JSON existent pour cet item."""
    if not PROJECT_ITEMS_PATH.exists():
        # Si le chemin n'existe pas, on assume que tous les items ont des assets
        # (pour éviter de skip des items valides)
        return True
    
    # Vérifier si au moins une qualité existe
    for quality in QUALITIES:
        quality_item_id = f"{base_item_id}_{quality}"
        quality_file = PROJECT_ITEMS_PATH / f"{quality_item_id}.json"
        if quality_file.exists():
            return True
    return False

def is_weapon_armor_tool(item_id):
    """Vérifie si un ItemId est une arme, armure ou outil."""
    if not item_id:
        return False
    return item_id.startswith("Weapon_") or item_id.startswith("Armor_") or item_id.startswith("Tool_")

def should_skip_item(item_id):
    """Détermine si un item doit être ignoré."""
    # Items qui ne devraient pas avoir de qualité (consommables, etc.)
    skip_items = [
        "Weapon_Arrow",  # Les flèches sont des consommables
        "Tool_Repair_Kit",  # Les kits de réparation ne devraient peut-être pas avoir de qualité
    ]
    
    for skip in skip_items:
        if item_id.startswith(skip):
            return True
    return False

def create_quality_choice(original_item):
    """Crée un Choice avec les 6 variantes de qualité."""
    base_item_id = original_item["ItemId"]
    
    # Créer les 6 variantes
    containers = []
    for quality in QUALITIES:
        quality_item_id = f"{base_item_id}_{quality}"
        weight = QUALITY_WEIGHTS[quality]
        
        # Copier les propriétés de l'item original (QuantityMin, QuantityMax, etc.)
        quality_item = {
            "ItemId": quality_item_id
        }
        
        # Copier les autres propriétés si elles existent
        if "QuantityMin" in original_item:
            quality_item["QuantityMin"] = original_item["QuantityMin"]
        if "QuantityMax" in original_item:
            quality_item["QuantityMax"] = original_item["QuantityMax"]
        
        containers.append({
            "Type": "Single",
            "Weight": weight,
            "Item": quality_item
        })
    
    return {
        "Type": "Choice",
        "Containers": containers
    }

def process_container(container, modified=False):
    """Traite récursivement un container et ses enfants."""
    if not isinstance(container, dict):
        return container, modified
    
    container_type = container.get("Type")
    
    if container_type == "Single":
        # Vérifier si c'est un item à modifier
        item = container.get("Item")
        if item and isinstance(item, dict):
            item_id = item.get("ItemId")
            if item_id and is_weapon_armor_tool(item_id):
                if not has_quality_in_id(item_id) and not should_skip_item(item_id):
                    if item_has_assets(item_id):
                        # Remplacer par un Choice avec les qualités
                        new_choice = create_quality_choice(item)
                        # Préserver le Weight du container original s'il existe
                        if "Weight" in container:
                            new_choice["Weight"] = container["Weight"]
                        return new_choice, True
    
    elif container_type in ["Multiple", "Choice"]:
        # Traiter les containers enfants
        containers = container.get("Containers", [])
        if containers:
            new_containers = []
            for child in containers:
                processed_child, child_modified = process_container(child, modified)
                new_containers.append(processed_child)
                if child_modified:
                    modified = True
            
            container["Containers"] = new_containers
    
    return container, modified

def process_drop_file(file_path):
    """Traite un fichier de drop."""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        # Traiter le container racine
        if "Container" in data:
            processed_container, modified = process_container(data["Container"])
            data["Container"] = processed_container
            
            if modified:
                # Sauvegarder le fichier modifié
                with open(file_path, 'w', encoding='utf-8') as f:
                    json.dump(data, f, indent=2, ensure_ascii=False)
                return True
        
        return False
    except Exception as e:
        print(f"Erreur lors du traitement de {file_path}: {e}")
        return False

def main():
    """Fonction principale."""
    if not DROPS_PATH.exists():
        print(f"Erreur: Le chemin des drops n'existe pas: {DROPS_PATH}")
        sys.exit(1)
    
    # Trouver tous les fichiers JSON dans le dossier Drops
    json_files = list(DROPS_PATH.rglob("*.json"))
    
    print(f"Trouvé {len(json_files)} fichiers JSON à traiter...")
    
    modified_count = 0
    for json_file in json_files:
        if process_drop_file(json_file):
            modified_count += 1
            print(f"[OK] Modifie: {json_file.relative_to(DROPS_PATH)}")
    
    print(f"\nTermine! {modified_count} fichiers modifies sur {len(json_files)}.")

if __name__ == "__main__":
    main()
