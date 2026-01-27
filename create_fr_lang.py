#!/usr/bin/env python3
"""Génère server.lang en français (fr-FR) à partir de en-US via substitutions."""

import os
import re

# Substitutions (ordre: plus long en premier pour éviter under-match)
# Qualités
QUALITIES = {
    "Junk": "Piètre",
    "Common": "Commun",
    "Uncommon": "Peu commun",
    "Rare": "Rare",
    "Epic": "Épique",
    "Legendary": "Légendaire",
}

# Armes / outils / armures (termes récurrents dans les noms)
TERMS = {
    "Crude Sword": "Épée brute",
    "Copper Sword": "Épée en cuivre",
    "Iron Sword": "Épée en fer",
    "Cobalt Sword": "Épée en cobalt",
    "Thorium Sword": "Épée en thorium",
    "Mithril Sword": "Épée en mithril",
    "Adamantite Sword": "Épée en adamantite",
    "Crude Daggers": "Dagues brutes",
    "Copper Daggers": "Dagues en cuivre",
    "Iron Daggers": "Dagues en fer",
    "Cobalt Daggers": "Dagues en cobalt",
    "Thorium Daggers": "Dagues en thorium",
    "Mithril Daggers": "Dagues en mithril",
    "Adamantite Daggers": "Dagues en adamantite",
    "Crude Axe": "Hache brute",
    "Copper Axe": "Hache en cuivre",
    "Iron Axe": "Hache en fer",
    "Cobalt Axe": "Hache en cobalt",
    "Thorium Axe": "Hache en thorium",
    "Mithril Axe": "Hache en mithril",
    "Adamantite Axe": "Hache en adamantite",
    "Crude Battleaxe": "Hache de guerre brute",
    "Copper Battleaxe": "Hache de guerre en cuivre",
    "Iron Battleaxe": "Hache de guerre en fer",
    "Cobalt Battleaxe": "Hache de guerre en cobalt",
    "Thorium Battleaxe": "Hache de guerre en thorium",
    "Mithril Battleaxe": "Hache de guerre en mithril",
    "Adamantite Battleaxe": "Hache de guerre en adamantite",
    "Crude Mace": "Massue brute",
    "Copper Mace": "Massue en cuivre",
    "Iron Mace": "Massue en fer",
    "Cobalt Mace": "Massue en cobalt",
    "Thorium Mace": "Massue en thorium",
    "Mithril Mace": "Massue en mithril",
    "Adamantite Mace": "Massue en adamantite",
    "Crude Shield": "Bouclier brut",
    "Copper Shield": "Bouclier en cuivre",
    "Iron Shield": "Bouclier en fer",
    "Cobalt Shield": "Bouclier en cobalt",
    "Thorium Shield": "Bouclier en thorium",
    "Mithril Shield": "Bouclier en mithril",
    "Adamantite Shield": "Bouclier en adamantite",
    "Crude Shortbow": "Arc court brut",
    "Copper Shortbow": "Arc court en cuivre",
    "Iron Shortbow": "Arc court en fer",
    "Cobalt Shortbow": "Arc court en cobalt",
    "Thorium Shortbow": "Arc court en thorium",
    "Mithril Shortbow": "Arc court en mithril",
    "Adamantite Shortbow": "Arc court en adamantite",
    "Iron Crossbow": "Arbalète en fer",
    "Adamantite Chest": "Plastron en adamantite",
    "Adamantite Hands": "Gants en adamantite",
    "Adamantite Head": "Casque en adamantite",
    "Adamantite Legs": "Jambières en adamantite",
    "Cobalt Chest": "Plastron en cobalt",
    "Cobalt Hands": "Gants en cobalt",
    "Cobalt Head": "Casque en cobalt",
    "Cobalt Legs": "Jambières en cobalt",
    "Copper Chest": "Plastron en cuivre",
    "Copper Hands": "Gants en cuivre",
    "Copper Head": "Casque en cuivre",
    "Copper Legs": "Jambières en cuivre",
    "Iron Chest": "Plastron en fer",
    "Iron Hands": "Gants en fer",
    "Iron Head": "Casque en fer",
    "Iron Legs": "Jambières en fer",
    "Mithril Chest": "Plastron en mithril",
    "Mithril Hands": "Gants en mithril",
    "Mithril Head": "Casque en mithril",
    "Mithril Legs": "Jambières en mithril",
    "Thorium Chest": "Plastron en thorium",
    "Thorium Hands": "Gants en thorium",
    "Thorium Head": "Casque en thorium",
    "Thorium Legs": "Jambières en thorium",
    "Crude Pickaxe": "Pioche brute",
    "Copper Pickaxe": "Pioche en cuivre",
    "Iron Pickaxe": "Pioche en fer",
    "Cobalt Pickaxe": "Pioche en cobalt",
    "Thorium Pickaxe": "Pioche en thorium",
    "Mithril Pickaxe": "Pioche en mithril",
    "Adamantite Pickaxe": "Pioche en adamantite",
    "Crude Hammer": "Marteau brut",
    "Iron Hammer": "Marteau en fer",
    "Crude Hatchet": "Hachette brute",
    "Copper Hatchet": "Hachette en cuivre",
    "Iron Hatchet": "Hachette en fer",
    "Cobalt Hatchet": "Hachette en cobalt",
    "Thorium Hatchet": "Hachette en thorium",
    "Mithril Hatchet": "Hachette en mithril",
    "Adamantite Hatchet": "Hachette en adamantite",
    "Crude Reforge Kit": "Kit de reforge brut",
    "Copper Reforge Kit": "Kit de reforge en cuivre",
    "Iron Reforge Kit": "Kit de reforge en fer",
    "Cobalt Reforge Kit": "Kit de reforge en cobalt",
    "Thorium Reforge Kit": "Kit de reforge en thorium",
    "Mithril Reforge Kit": "Kit de reforge en mithril",
    "Adamantite Reforge Kit": "Kit de reforge en adamantite",
    "Quality Viewer": "Visionneuse en qualité",
}

# Phrases pour descriptions (snippets anglais -> français) — les plus longues en premier
PHRASES = [
    ("Quality Items Viewer", "Visionneuse d'objets de qualité"),
    ("No compatibles items found in inventory", "Aucun objet compatible dans l'inventaire"),
    ("No quality items found in inventory", "Aucun objet de qualité dans l'inventaire"),
    ("Deals 10% less damage and has 50% less durability than common daggers.", "Inflige 10% de dégâts en moins et a 50% de durabilité en moins que des dagues communes."),
    ("Deals 5% more damage and has 25% more durability than common daggers.", "Inflige 5% de dégâts en plus et a 25% de durabilité en plus que des dagues communes."),
    ("Deals 10% more damage and has 50% more durability than common daggers.", "Inflige 10% de dégâts en plus et a 50% de durabilité en plus que des dagues communes."),
    ("Deals 15% more damage and has 75% more durability than common daggers.", "Inflige 15% de dégâts en plus et a 75% de durabilité en plus que des dagues communes."),
    ("Deals 20% more damage and has 100% more durability than common daggers.", "Inflige 20% de dégâts en plus et a 100% de durabilité en plus que des dagues communes."),
    ("A poorly crafted pair of crude daggers ", "Une paire de dagues brutes de piètre facture, "),
    ("A poorly forged pair of crude daggers ", "Une paire de dagues brutes de piètre facture, "),
    ("A standard pair of crude daggers ", "Une paire de dagues brutes "),
    ("A well-made pair of crude daggers ", "Une paire de dagues brutes bien fabriquée, "),
    ("A well-forged pair of crude daggers ", "Une paire de dagues brutes bien forgée, "),
    ("A finely crafted pair of crude daggers ", "Une paire de dagues brutes finement travaillée, "),
    ("An expertly forged pair of crude daggers ", "Une paire de dagues brutes forgée par un expert, "),
    ("A legendary pair of crude daggers ", "Une paire de dagues brutes légendaire, "),
    ("pair of crude daggers", "dagues brutes"),
    ("pair of copper daggers", "dagues en cuivre"),
    ("pair of iron daggers", "dagues en fer"),
    ("pair of cobalt daggers", "dagues en cobalt"),
    ("pair of thorium daggers", "dagues en thorium"),
    ("pair of mithril daggers", "dagues en mithril"),
    ("pair of adamantite daggers", "dagues en adamantite"),
    ("A poorly crafted ", "Un équipement de piètre facture, "),
    ("A poorly forged ", "Un équipement de piètre facture, "),
    ("with reduced effectiveness. ", "d'efficacité réduite. "),
    ("Deals 10% less damage and has 50% less durability than a common ", "Inflige 10% de dégâts en moins et a 50% de durabilité en moins qu'un "),
    ("Deals 5% more damage and has 25% more durability than a common ", "Inflige 5% de dégâts en plus et a 25% de durabilité en plus qu'un "),
    ("Deals 10% more damage and has 50% more durability than a common ", "Inflige 10% de dégâts en plus et a 50% de durabilité en plus qu'un "),
    ("Deals 15% more damage and has 75% more durability than a common ", "Inflige 15% de dégâts en plus et a 75% de durabilité en plus qu'un "),
    ("Deals 20% more damage and has 100% more durability than a common ", "Inflige 20% de dégâts en plus et a 100% de durabilité en plus qu'un "),
    ("This weapon shows signs of poor craftsmanship and will break quickly in combat.", "La facture médiocre de cette arme la fait s'user vite au combat."),
    ("This is the baseline quality for this weapon type, providing reliable performance in combat.", "Qualité de base pour ce type d'arme, aux performances fiables au combat."),
    ("A standard ", "Un équipement "),
    (" with balanced stats. ", " aux caractéristiques équilibrées. "),
    ("A well-made ", "Un équipement bien fabriqué, "),
    ("A well-forged ", "Un équipement bien forgé, "),
    ("A well-crafted ", "Un équipement bien fabriqué, "),
    (" with enhanced durability. ", " à la durabilité améliorée. "),
    ("The improved craftsmanship makes it more reliable in extended battles.", "La meilleure facture le rend plus fiable dans les combats prolongés."),
    ("The superior craftsmanship makes it more effective in battle.", "La facture supérieure le rend plus efficace au combat."),
    ("The inferior craftsmanship is evident in its weakened structure.", "La facture inférieure se voit à sa structure fragilisée."),
    ("The inferior craftsmanship is evident in its weakened blade.", "La facture inférieure se voit à sa lame fragilisée."),
    ("The inferior craftsmanship is evident in their weakened blades.", "La facture inférieure se voit à leurs lames fragilisées."),
    ("The subpar quality is evident in its weakened structure.", "La qualité médiocre se voit à sa structure fragilisée."),
    ("The subpar quality is evident in their weakened structure.", "La qualité médiocre se voit à leur structure fragilisée."),
    ("with exceptional resilience. Has 75% more durability and significantly lower stamina cost than a common bouclier. The masterful workmanship is evident in its superior defensive capabilities.", "d'une résilience exceptionnelle. 75% de durabilité en plus et coût d'endurance bien moindre qu'un bouclier commun. Le travail magistral se voit à ses capacités défensives supérieures."),
    ("A finely crafted ", "Un équipement finement travaillé, "),
    (" with superior stats. ", " aux caractéristiques supérieures. "),
    ("This weapon shows exceptional quality and will serve you well.", "Cette arme affiche une qualité exceptionnelle et vous servira bien."),
    ("This weapon demonstrates exceptional quality and reliability.", "Cette arme offre une qualité et une fiabilité exceptionnelles."),
    ("These weapons show exceptional quality and will serve you well.", "Ces armes affichent une qualité exceptionnelle et vous serviront bien."),
    ("These weapons demonstrate exceptional quality and reliability.", "Ces armes offrent une qualité et une fiabilité exceptionnelles."),
    ("An expertly forged ", "Un équipement forgé par un expert, "),
    (" with exceptional power. ", " d'une puissance exceptionnelle. "),
    ("The masterful craftsmanship is evident in every strike.", "La facture magistrale se voit à chaque coup."),
    ("The masterful workmanship is evident in its superior performance.", "Le travail magistral se voit à ses performances supérieures."),
    ("The masterful workmanship is evident in their superior performance.", "Le travail magistral se voit à leurs performances supérieures."),
    ("A legendary ", "Un équipement légendaire : "),
    (" of unparalleled quality. ", " d'une qualité inégalée. "),
    ("This weapon is a masterpiece of craftsmanship, capable of legendary feats.", "Cette arme est un chef-d'œuvre d'artisanat, capable d'exploits légendaires."),
    ("This weapon is a true masterpiece, capable of legendary combat prowess.", "Cette arme est un véritable chef-d'œuvre, capable de prouesses de combat légendaires."),
    ("These weapons are masterpieces of craftsmanship, capable of legendary feats.", "Ces armes sont des chefs-d'œuvre d'artisanat, capables d'exploits légendaires."),
    ("These weapons are true masterpieces, capable of legendary combat prowess.", "Ces armes sont de véritables chefs-d'œuvre, capables de prouesses de combat légendaires."),
    (" sword.", " épée."),
    (" daggers.", " dagues."),
    (" axe.", " hache."),
    (" battleaxe.", " hache de guerre."),
    (" mace.", " massue."),
    (" shortbow.", " arc court."),
    (" crossbow.", " arbalète."),
    ("Has 50% less durability and higher stamina cost than a common ", "A 50% de durabilité en moins et coûte plus d'endurance qu'un "),
    (" shield. ", " bouclier. "),
    ("This is the baseline quality for this shield type, providing reliable protection in combat.", "Qualité de base pour ce type de bouclier, offrant une protection fiable au combat."),
    ("Has 25% more durability and slightly lower stamina cost than a common ", "A 25% de durabilité en plus et coûte un peu moins d'endurance qu'un "),
    ("The superior craftsmanship makes it more reliable in extended battles.", "La facture supérieure le rend plus fiable dans les combats prolongés."),
    ("Has 50% more durability and reduced stamina cost compared to a common ", "A 50% de durabilité en plus et coûte moins d'endurance qu'un "),
    ("This shield demonstrates exceptional quality and reliability.", "Ce bouclier offre une qualité et une fiabilité exceptionnelles."),
    ("An expertly forged crude shield with exceptional resilience. Has 75% more durability and significantly lower stamina cost than a common shield. The masterful workmanship is evident in its superior defensive capabilities.", "Un bouclier brut forgé par un expert, d'une résilience exceptionnelle. 75% de durabilité en plus et coût d'endurance bien moindre qu'un bouclier commun. Le travail magistral se voit à ses capacités défensives supérieures."),
    ("A legendary crude shield of unparalleled quality. Has 100% more durability and minimal stamina cost compared to a common shield. This shield is a true masterpiece, capable of legendary defensive prowess.", "Un bouclier brut légendaire d'une qualité inégalée. 100% de durabilité en plus et coût d'endurance minimal. Ce bouclier est un véritable chef-d'œuvre, capable de prouesses défensives légendaires."),
    ("Has 100% more durability and minimal stamina cost compared to a common bouclier. This shield is a true masterpiece, capable of legendary defensive prowess.", "100% de durabilité en plus et coût d'endurance minimal par rapport à un bouclier commun. Ce bouclier est un véritable chef-d'œuvre, capable de prouesses défensives légendaires."),
    ("Provides 10% less protection and has 50% less durability than a common ", "Offre 10% de protection en moins et a 50% de durabilité en moins qu'un "),
    ("Armor.", " armure."),
    (" Chest.", " plastron."),
    (" Hands.", " gants."),
    (" Head.", " casque."),
    (" Legs.", " jambières."),
    ("This is the baseline quality for this armor type, providing reliable protection in combat.", "Qualité de base pour ce type d'armure, offrant une protection fiable au combat."),
    ("Provides 5% more protection and has 25% more durability than a common ", "Offre 5% de protection en plus et a 25% de durabilité en plus qu'un "),
    ("Provides 10% more protection and has 50% more durability than a common ", "Offre 10% de protection en plus et a 50% de durabilité en plus qu'un "),
    ("Provides 15% more protection and has 75% more durability than a common ", "Offre 15% de protection en plus et a 75% de durabilité en plus qu'un "),
    ("Provides 20% more protection and has 100% more durability than a common ", "Offre 20% de protection en plus et a 100% de durabilité en plus qu'un "),
    ("This armor demonstrates exceptional quality and reliability.", "Cette armure offre une qualité et une fiabilité exceptionnelles."),
    ("The masterful workmanship is evident in its superior performance.", "Le travail magistral se voit à ses performances supérieures."),
    ("This armor is a true masterpiece, capable of legendary feats.", "Cette armure est un véritable chef-d'œuvre, capable d'exploits légendaires."),
    ("Has 50% less durability than a common ", "A 50% de durabilité en moins qu'une "),
    ("Has 25% more durability than a common ", "A 25% de durabilité en plus qu'une "),
    ("Has 50% more durability than a common ", "A 50% de durabilité en plus qu'une "),
    ("Has 75% more durability than a common ", "A 75% de durabilité en plus qu'une "),
    ("Has 100% more durability than a common ", "A 100% de durabilité en plus qu'une "),
    (" Pickaxe.", " pioche."),
    (" Hammer.", " marteau."),
    (" Hatchet.", " hachette."),
    ("This is the baseline quality for this tool type, providing reliable performance.", "Qualité de base pour ce type d'outil, aux performances fiables."),
    ("The superior craftsmanship makes it more reliable.", "La facture supérieure le rend plus fiable."),
    ("This tool demonstrates exceptional quality and reliability.", "Cet outil offre une qualité et une fiabilité exceptionnelles."),
    ("This tool is a true masterpiece, capable of legendary feats.", "Cet outil est un véritable chef-d'œuvre, capable d'exploits légendaires."),
    ("Allows you to reforge ", "Permet de reforcer "),
    (" items to change their quality tier. Right-click to open the reforge interface.", " pour en changer la qualité. Clic droit pour ouvrir l'interface de reforge."),
    ("View all your quality items in a grid. Hover over items to see detailed information. Right-click to open.", "Consultez tous vos objets de qualité dans une grille. Survolez les objets pour les détails. Clic droit pour ouvrir."),
    ("Reforge Items", "Reforger des objets"),
    ("Item", "Objet"),
    ("Quality", "Qualité"),
    ("Quality Viewer", "Visionneuse en qualité"),
    ("iron crossbow", "arbalète en fer"),
    # Formes minuscules pour descriptions (alignées sur TERMS : "en" pour les matériaux)
    ("adamantite sword", "épée en adamantite"),
    ("mithril sword", "épée en mithril"),
    ("thorium sword", "épée en thorium"),
    ("cobalt sword", "épée en cobalt"),
    ("copper sword", "épée en cuivre"),
    ("iron sword", "épée en fer"),
    ("crude sword", "épée brute"),
    ("adamantite battleaxe", "hache de guerre en adamantite"),
    ("mithril battleaxe", "hache de guerre en mithril"),
    ("thorium battleaxe", "hache de guerre en thorium"),
    ("cobalt battleaxe", "hache de guerre en cobalt"),
    ("copper battleaxe", "hache de guerre en cuivre"),
    ("iron battleaxe", "hache de guerre en fer"),
    ("crude battleaxe", "hache de guerre brute"),
    ("adamantite mace", "massue en adamantite"),
    ("mithril mace", "massue en mithril"),
    ("thorium mace", "massue en thorium"),
    ("cobalt mace", "massue en cobalt"),
    ("copper mace", "massue en cuivre"),
    ("iron mace", "massue en fer"),
    ("crude mace", "massue brute"),
    ("adamantite axe", "hache en adamantite"),
    ("mithril axe", "hache en mithril"),
    ("thorium axe", "hache en thorium"),
    ("cobalt axe", "hache en cobalt"),
    ("copper axe", "hache en cuivre"),
    ("iron axe", "hache en fer"),
    ("crude axe", "hache brute"),
    ("adamantite shortbow", "arc court en adamantite"),
    ("mithril shortbow", "arc court en mithril"),
    ("thorium shortbow", "arc court en thorium"),
    ("cobalt shortbow", "arc court en cobalt"),
    ("copper shortbow", "arc court en cuivre"),
    ("iron shortbow", "arc court en fer"),
    ("crude shortbow", "arc court brut"),
    ("adamantite shield", "bouclier en adamantite"),
    ("mithril shield", "bouclier en mithril"),
    ("thorium shield", "bouclier en thorium"),
    ("cobalt shield", "bouclier en cobalt"),
    ("copper shield", "bouclier en cuivre"),
    ("iron shield", "bouclier en fer"),
    ("crude shield", "bouclier brut"),
    ("adamantite pickaxe", "pioche en adamantite"),
    ("mithril pickaxe", "pioche en mithril"),
    ("thorium pickaxe", "pioche en thorium"),
    ("cobalt pickaxe", "pioche en cobalt"),
    ("copper pickaxe", "pioche en cuivre"),
    ("iron pickaxe", "pioche en fer"),
    ("crude pickaxe", "pioche brute"),
    ("adamantite hatchet", "hachette en adamantite"),
    ("mithril hatchet", "hachette en mithril"),
    ("thorium hatchet", "hachette en thorium"),
    ("cobalt hatchet", "hachette en cobalt"),
    ("copper hatchet", "hachette en cuivre"),
    ("iron hatchet", "hachette en fer"),
    ("crude hatchet", "hachette brute"),
    ("iron hammer", "marteau en fer"),
    ("crude hammer", "marteau brut"),
]

# Corrections grammaticales et reforge
POST_FIXES = [
    ("qu'un épée", "qu'une épée"),
    ("qu'un hache ", "qu'une hache "),
    ("qu'un massue", "qu'une massue"),
    ("qu'un hachette", "qu'une hachette"),
    ("qu'un arbalète", "qu'une arbalète"),
    ("qu'un pioche", "qu'une pioche"),
    ("Permet de reforcer Crude pour", "Permet de reforcer les objets bruts pour"),
    ("Permet de reforcer Copper pour", "Permet de reforcer les objets en cuivre pour"),
    ("Permet de reforcer Iron pour", "Permet de reforcer les objets en fer pour"),
    ("Permet de reforcer Cobalt pour", "Permet de reforcer les objets en cobalt pour"),
    ("Permet de reforcer Thorium pour", "Permet de reforcer les objets en thorium pour"),
    ("Permet de reforcer Mithril pour", "Permet de reforcer les objets en mithril pour"),
    ("Permet de reforcer Adamantite pour", "Permet de reforcer les objets en adamantite pour"),
]

# Pour les lignes quality (qualities.Poor.name=Poor)
def tr_quality_val(v):
    return QUALITIES.get(v, v)

def tr_line(line):
    s = line.rstrip("\n\r")
    if not s.strip() or s.strip().startswith("#"):
        return s + "\n"
    if "=" not in s:
        return s + "\n"
    key, _, val = s.partition("=")
    key = key.strip()
    val = val.strip()

    if key.startswith("qualities.") and key.endswith(".name"):
        val = tr_quality_val(val)
        return key + "=" + val + "\n"

    if key.startswith("items.") or key.startswith("customUI."):
        # Phrases les plus longues en premier pour éviter des remplacements partiels
        for en, fr in sorted(PHRASES, key=lambda x: -len(x[0])):
            val = val.replace(en, fr)
        for en, fr in sorted(TERMS.items(), key=lambda x: -len(x[0])):
            val = val.replace(en, fr)
        # Formes en minuscules pour le texte des descriptions (ex. "crude sword" -> "épée brute")
        for en, fr in sorted(TERMS.items(), key=lambda x: -len(x[0])):
            en_low = en[0].lower() + en[1:] if len(en) > 0 else en
            fr_low = (fr[0].lower() + fr[1:]) if len(fr) > 0 else fr
            val = val.replace(en_low, fr_low)
        for en, fr in QUALITIES.items():
            val = re.sub(r"\b" + re.escape(en) + r"\b", fr, val)
        for en, fr in POST_FIXES:
            val = val.replace(en, fr)
        return key + "=" + val + "\n"

    return s + "\n"


def main():
    base = os.path.dirname(__file__)
    src = os.path.join(base, "src", "main", "resources", "Server", "Languages", "en-US", "server.lang")
    out_dir = os.path.join(base, "src", "main", "resources", "Server", "Languages", "fr-FR")
    out = os.path.join(out_dir, "server.lang")
    os.makedirs(out_dir, exist_ok=True)
    with open(src, "r", encoding="utf-8") as f:
        lines = f.readlines()
    with open(out, "w", encoding="utf-8") as f:
        for line in lines:
            f.write(tr_line(line))
    print("Écrit:", out)


if __name__ == "__main__":
    main()
