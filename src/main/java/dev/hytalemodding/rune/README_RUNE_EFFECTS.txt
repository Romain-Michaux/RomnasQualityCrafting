EFFETS RUNE (BRÛLURE / POISON) EN JEU
=====================================

La logique est dans RuneEffectApplier.applyIfRuneWeapon(attacker, targetRef, store).

Pour que Brûlure et Poison s’appliquent quand tu frappes, il faut enregistrer un
DamageEventSystem (groupe Inspect) qui :
1. Reçoit les dégâts après application (InspectDamageGroup).
2. Récupère la cible : targetRef = chunk.getReferenceTo(index).
3. Si source instanceof Damage.EntitySource, récupère l’attaquant.
4. Si l’attaquant est un joueur, appelle :
   RuneEffectApplier.applyIfRuneWeapon(player, targetRef, store);

Les noms d’effets utilisés sont "Burning" ou "Fire" pour Brûlure, "Poison" ou "poison"
pour Poison (selon ce que ton jeu enregistre). RuneEffectApplier les cherche déjà.

Si ton SDK expose bien DamageEventSystem et les types (ArchetypeChunk, SystemGroup),
ajoute une classe qui étend DamageEventSystem, implémente handle() comme ci‑dessus,
et enregistre‑la avec getEntityStoreRegistry().registerSystem(new TonRuneDamageSystem()).


EFFET RUNE CHANCE (LUCK) AU MINAGE
==================================

La rune Luck est gérée par RuneLuckMiningSystem, un EntityEventSystem<EntityStore, BreakBlockEvent>
(enregistré via getEntityStoreRegistry().registerSystem(new RuneLuckMiningSystem())), calqué sur
EnchantmentFortuneSystem de SimpleEnchantments.

Comportement : en minant un bloc avec gathering.getBreaking() avec une pioche ayant la rune Luck,
on obtient une fois de plus les drops de type ore/crystal (double yield). Le système utilise
BlockHarvestUtils.getDrops(), filtre les drops dont l’itemId contient "ore" ou "crystal", puis
ItemComponent.generateItemDrops + commandBuffer.addEntities(..., AddReason.SPAWN) au centre du bloc.
