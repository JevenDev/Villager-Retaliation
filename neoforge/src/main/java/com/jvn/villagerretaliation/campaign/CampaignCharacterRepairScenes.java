package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorBindingService;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

final class CampaignCharacterRepairScenes {
    private CampaignCharacterRepairScenes() { }

    static String preflight(MinecraftServer server, CampaignCharacterSavedData characters, CampaignCharacterBinding candidate, UUID operator) {
        var scenes = SceneSavedData.get(server.overworld());
        if (scenes.futureVersion()) return "Restore compatible scene data before repairing campaign characters.";
        var catalog = QuestContentCatalogs.current(server);
        for (var scene : scenes.active()) {
            if (!candidate.campaign().equals(scene.owningCampaignId())) continue;
            for (var prior : scene.actorBindings().values()) {
                if (prior.entityId() == null || prior.entityId().equals(candidate.entityId())) continue;
                boolean affected = affected(characters, candidate, prior.entityId());
                if (!affected && characters.bindings().containsKey(candidate.key())) continue;
                var definition = catalog.scenes().get(scene.sceneId());
                if (definition == null || !definition.definitionHash().equals(scene.definitionHash()))
                    return "Restore the definition of active scene " + scene.id() + " before repairing its character.";
                var actor = definition.actors().get(prior.alias());
                if (actor == null) return "Restore the actor declaration for active scene " + scene.id() + ".";
                if (actor.bindingSource() != SceneActorDeclaration.BindingSource.CAMPAIGN_ROLE
                        && actor.bindingSource() != SceneActorDeclaration.BindingSource.QUEST_PROVIDER) continue;
                if (!affected) {
                    String role = actor.bindingSource() == SceneActorDeclaration.BindingSource.CAMPAIGN_ROLE
                            ? actor.bindingReference() : CampaignQuestCharacters.role(server, scene.owningQuestId());
                    if (!candidate.role().equals(role)) continue;
                    return "Restore the original character " + prior.entityId() + " for scene " + scene.id() + " before reassigning its missing binding.";
                }
                var proposed = SceneActorBinding.entity(prior.alias(), prior.actorType(), candidate.entityId(), prior.sourceType(),
                        candidate.dimension(), candidate.position(), prior.displaySnapshot().getOrDefault("name", ""), true);
                var result = SceneActorBindingService.rebind(actor, prior, proposed, SceneActorBindingService.RebindKind.OPERATOR,
                        server.overworld().getGameTime(), "campaign character repair", operator.toString());
                if (!result.accepted()) return "Active scene " + scene.id() + " prevents this character repair: " + result.diagnostic() + ".";
                if (actor.replacementPolicy() == SceneActorDeclaration.ReplacementPolicy.OPERATOR_REBINDABLE
                        && characters.known(candidate.key(), prior.entityId()) == null)
                    return "Reconcile the authored successor in scene " + scene.id() + " before repairing its operator-rebindable actor.";
            }
        }
        return "";
    }

    private static boolean affected(CampaignCharacterSavedData characters, CampaignCharacterBinding candidate, UUID previous) {
        if (characters.known(candidate.key(), previous) != null) return true;
        var current = characters.bindings().get(candidate.key());
        return current != null && characters.bindings().keySet().stream().anyMatch(key ->
                key.campaign().equals(candidate.campaign()) && characters.authorizes(key, previous, current.entityId()));
    }
}
