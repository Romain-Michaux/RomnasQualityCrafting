# Configuration — RomnasQualityCrafting

Le mod lit un fichier **`RomnasQualityCrafting.json`** au démarrage du serveur.  
S’il est absent, il est créé automatiquement dans le répertoire de config du plugin (ou dans `config/` à la racine du serveur).

Toutes les options ci‑dessous sont modifiables par les gérants de serveur.

---

## Emplacement du fichier

- Le plugin cherche d’abord un répertoire fourni par l’API Hytale (`getConfigDirectory`, `getDataFolder`, `getConfigPath`).
- Si rien n’est disponible, le fichier utilisé est : **`config/RomnasQualityCrafting.json`** (répertoire de travail du serveur).

Après le premier lancement avec le mod, le fichier est créé avec les valeurs par défaut si besoin.

---

## Options disponibles

### Qualité et compatibilité

| Clé | Type | Défaut | Description |
|-----|------|--------|-------------|
| **QualityCompatEnabled** | `boolean` | `true` | Active la création automatique des 6 qualités (Poor → Legendary) pour les armes/outils/armures des autres mods qui n’en ont pas. |
| **QualityCompatRegisterSalvage** | `boolean` | `true` | Lorsque la compat qualité est activée, enregistre aussi des recettes de salvage pour chaque variante de qualité créée (à partir des recettes de salvage de l’item de base). |
| **AutoApplyQuality** | `boolean` | `true` | Applique une qualité aléatoire (selon les poids) aux objets obtenus par craft ou loot. Désactiver pour ne plus attribuer de qualité automatiquement. |

### Poids des qualités

Probabilités relatives pour le tirage d’une qualité (craft, loot, reforge). Plus la valeur est grande, plus la qualité a de chances d’être tirée. Idéalement, la somme fait 100 (comme des pourcentages).

| Clé | Type | Défaut | Description |
|-----|------|--------|-------------|
| **QualityWeightPoor**   | `int` | `25` | Poids pour *Poor*   |
| **QualityWeightCommon** | `int` | `40` | Poids pour *Common* |
| **QualityWeightUncommon** | `int` | `20` | Poids pour *Uncommon* |
| **QualityWeightRare**   | `int` | `10` | Poids pour *Rare*   |
| **QualityWeightEpic**   | `int` | `4`  | Poids pour *Epic*   |
| **QualityWeightLegendary** | `int` | `1` | Poids pour *Legendary* |

Si la somme des poids est ≤ 0, le mod utilise un tirage équiprobable entre les six qualités.

### Reforge

| Clé | Type | Défaut | Description |
|-----|------|--------|-------------|
| **ReforgeEnabled** | `boolean` | `true` | Active les interactions et l’interface de reforge (reforge d’équipement). |

### Quality Viewer

| Clé | Type | Défaut | Description |
|-----|------|--------|-------------|
| **QualityViewerEnabled** | `boolean` | `true` | Active la “Quality Viewer” (outil et page pour voir la qualité / reforge / appliquer une rune depuis l’interface). |

### Runecrafting

| Clé | Type | Défaut | Description |
|-----|------|--------|-------------|
| **RunecraftingEnabled** | `boolean` | `true` | Active la pose de runes sur les équipements (ouverture de la page “Apply Rune”, etc.). |

### Effets des runes

| Clé | Type | Défaut | Description |
|-----|------|--------|-------------|
| **RuneEffectBurnPoison** | `boolean` | `true` | Active les effets des runes de type Brûlure / Poison (dégâts over time). |
| **RuneEffectLuckMining** | `boolean` | `true` | Active l’effet de la rune de chance (minage, etc.). |
| **RuneEffectSpeedBoots** | `boolean` | `true` | Active l’effet de vitesse sur les bottes (rune Speed). |

---

## Exemple de fichier complet

```json
{
  "QualityCompatEnabled": true,
  "QualityCompatRegisterSalvage": true,
  "AutoApplyQuality": true,
  "QualityWeightPoor": 25,
  "QualityWeightCommon": 40,
  "QualityWeightUncommon": 20,
  "QualityWeightRare": 10,
  "QualityWeightEpic": 4,
  "QualityWeightLegendary": 1,
  "ReforgeEnabled": true,
  "QualityViewerEnabled": true,
  "RunecraftingEnabled": true,
  "RuneEffectBurnPoison": true,
  "RuneEffectLuckMining": true,
  "RuneEffectSpeedBoots": true
}
```

Les clés peuvent être réorganisées ou omises : les valeurs manquantes sont remplacées par les défauts du tableau ci‑dessus.

---

## Prise en compte des changements

La configuration est lue **au démarrage du serveur**. Pour appliquer une modification, il faut redémarrer le serveur (ou recharger le plugin si votre environnement le permet).
