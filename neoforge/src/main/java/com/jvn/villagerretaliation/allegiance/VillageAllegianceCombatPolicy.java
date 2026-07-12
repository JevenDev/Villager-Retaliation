package com.jvn.villagerretaliation.allegiance;

import com.jvn.villagerretaliation.party.PartyService;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class VillageAllegianceCombatPolicy {
    private VillageAllegianceCombatPolicy() {
    }

    public static AllegianceCombatDecision evaluate(
            ServerLevel level,
            LivingEntity actor,
            LivingEntity target,
            AllegianceCombatContext context,
            boolean opposingPartyAuthorization) {
        if (actor == target) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.SAME_ENTITY);
        }
        if (context == AllegianceCombatContext.DIRECT_PLAYER_DAMAGE && actor instanceof Player) {
            return AllegianceCombatDecision.pass(AllegianceCombatDecision.Reason.DIRECT_PLAYER_DAMAGE);
        }
        if (context == AllegianceCombatContext.DISCIPLINE) {
            return AllegianceCombatDecision.allow(AllegianceCombatDecision.Reason.DISCIPLINARY_RESPONSE);
        }
        if (PartyService.areInSameParty(actor, target)) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.SAME_PARTY);
        }
        AllegianceEntityClassifier.Classification targetClass = AllegianceEntityClassifier.classify(target);
        if (targetClass == AllegianceEntityClassifier.Classification.NEUTRAL_TRADER) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.NEUTRAL_TRADER);
        }
        if (!AllegianceEntityClassifier.protectedCivilian(target)) {
            return AllegianceCombatDecision.pass(AllegianceCombatDecision.Reason.NON_CIVILIAN_TARGET);
        }
        if (targetClass == AllegianceEntityClassifier.Classification.VILLAGE_GOLEM
                && isPartyContext(context)) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.GOLEM_RESTRICTED);
        }
        if (!AllegianceEntityClassifier.bearsAllegiance(actor)) {
            return AllegianceCombatDecision.pass(AllegianceCombatDecision.Reason.ORDINARY_BEHAVIOR);
        }
        VillageAllegianceData actorData = VillageAllegianceApi.get(actor).orElse(null);
        VillageAllegianceData targetData = VillageAllegianceApi.get(target).orElse(null);
        if (actorData == null || actorData.state() == AllegianceState.UNKNOWN) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.UNKNOWN_ACTOR);
        }
        if (targetData == null || targetData.state() == AllegianceState.UNKNOWN) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.UNKNOWN_TARGET);
        }
        if (!actorData.isKnown() || !targetData.isKnown()) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.NEUTRAL_TRADER);
        }
        VillageAllegianceRegistrySavedData registry = VillageAllegianceRegistrySavedData.get(level);
        Optional<VillageAllegianceId> actorCanonical = registry.canonical(actorData.primary());
        Optional<VillageAllegianceId> targetCanonical = registry.canonical(targetData.primary());
        if (actorCanonical.isEmpty()) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.UNKNOWN_ACTOR);
        }
        if (targetCanonical.isEmpty()) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.UNKNOWN_TARGET);
        }
        if (actorCanonical.get().equals(targetCanonical.get())) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.SAME_CANONICAL_ALLEGIANCE);
        }
        if (canonicalParents(registry, actorData).contains(targetCanonical.get())
                || canonicalParents(registry, targetData).contains(actorCanonical.get())) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.PARENT_PROTECTION);
        }
        if (!opposingPartyAuthorization && isPartyControlled(context)) {
            return AllegianceCombatDecision.deny(AllegianceCombatDecision.Reason.NO_OPPOSING_PARTY_AUTHORIZATION);
        }
        if (opposingPartyAuthorization) {
            return AllegianceCombatDecision.allow(AllegianceCombatDecision.Reason.AUTHORIZED_OPPOSING_ALLEGIANCE);
        }
        return AllegianceCombatDecision.pass(AllegianceCombatDecision.Reason.ORDINARY_BEHAVIOR);
    }

    private static Set<VillageAllegianceId> canonicalParents(
            VillageAllegianceRegistrySavedData registry,
            VillageAllegianceData data) {
        Set<VillageAllegianceId> result = new LinkedHashSet<>();
        for (VillageAllegianceId id : data.protectedParents()) {
            registry.canonical(id).ifPresent(result::add);
        }
        return result;
    }

    private static boolean isPartyContext(AllegianceCombatContext context) {
        return context == AllegianceCombatContext.PARTY_ATTACK
                || context == AllegianceCombatContext.PARTY_DEFEND;
    }

    private static boolean isPartyControlled(AllegianceCombatContext context) {
        return isPartyContext(context)
                || context == AllegianceCombatContext.CUSTOM_TARGET
                || context == AllegianceCombatContext.TARGET_CONTINUATION
                || context == AllegianceCombatContext.DAMAGE;
    }
}
