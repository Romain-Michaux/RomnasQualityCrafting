#!/usr/bin/env python3
"""
Script pour copier les fichiers JSON de drops modifiés dans les resources du mod.
"""

import os
import shutil
from pathlib import Path

# Fichiers modifiés (relatifs à Drops/)
MODIFIED_FILES = [
    "Prefabs/Zone1_Encounters_Tier1.json",
    "Prefabs/Zone1_Encounters_Tier2.json",
    "Prefabs/Zone1_Encounters_Tier3.json",
    "Prefabs/Zone1_Encounters_Tier4.json",
    "Prefabs/Zone2_Encounters_Tier1.json",
    "Prefabs/Zone2_Encounters_Tier2.json",
    "Prefabs/Zone2_Encounters_Tier3.json",
    "Prefabs/Zone2_Encounters_Tier4.json",
    "Prefabs/Zone3_Encounters_Tier1.json",
    "Prefabs/Zone3_Encounters_Tier2.json",
    "Prefabs/Zone3_Encounters_Tier3.json",
    "Prefabs/Zone3_Encounters_Tier4.json",
    "Prefabs/Zone4_Encounters_Tier1.json",
    "Prefabs/Zone4_Encounters_Tier2.json",
    "Prefabs/Zone4_Encounters_Tier3.json",
    "Prefabs/Zone4_Encounters_Tier4.json",
    "NPCs/Loadouts/Test_Random_Hotbar.json",
    "NPCs/Undead/Drop_Skeleton_Sand_Ranger.json",
    "NPCs/Undead/Drop_Skeleton_Scout.json",
    "NPCs/Intelligent/Outlander/Drop_Outlander_Stalker.json",
]

# Chemins
SOURCE_DROPS_PATH = Path(r"C:\Users\micha\Desktop\Hytale Base Server JSON\Server\Drops")
TARGET_DROPS_PATH = Path("src/main/resources/Server/Drops")

def main():
    """Fonction principale."""
    if not SOURCE_DROPS_PATH.exists():
        print(f"Erreur: Le chemin source n'existe pas: {SOURCE_DROPS_PATH}")
        return
    
    # Créer le dossier de destination s'il n'existe pas
    TARGET_DROPS_PATH.mkdir(parents=True, exist_ok=True)
    
    copied_count = 0
    for file_path in MODIFIED_FILES:
        source_file = SOURCE_DROPS_PATH / file_path
        target_file = TARGET_DROPS_PATH / file_path
        
        if not source_file.exists():
            print(f"Attention: Fichier source introuvable: {source_file}")
            continue
        
        # Créer les dossiers parents si nécessaire
        target_file.parent.mkdir(parents=True, exist_ok=True)
        
        # Copier le fichier
        try:
            shutil.copy2(source_file, target_file)
            copied_count += 1
            print(f"[OK] Copie: {file_path}")
        except Exception as e:
            print(f"Erreur lors de la copie de {file_path}: {e}")
    
    print(f"\nTermine! {copied_count} fichiers copies sur {len(MODIFIED_FILES)}.")

if __name__ == "__main__":
    main()
