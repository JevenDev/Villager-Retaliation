package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorBindingService;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.io.IOException;
import net.minecraft.server.MinecraftServer;

public final class CampaignSceneCharacters {
    private CampaignSceneCharacters() { }

    public static String resolve(MinecraftServer server, SceneSavedData data, SceneInstance scene, CompiledScene definition) {
        for (var actor : definition.actors().values()) {
            boolean campaignRole = actor.bindingSource() == SceneActorDeclaration.BindingSource.CAMPAIGN_ROLE;
            boolean questProvider = actor.bindingSource() == SceneActorDeclaration.BindingSource.QUEST_PROVIDER && scene.owningCampaignId() != null;
            if (!campaignRole && !questProvider) continue;
            String role = campaignRole ? actor.bindingReference() : CampaignQuestCharacters.role(server, scene.owningQuestId());
            if (questProvider && role.isBlank()) continue;
            var result = CampaignCharacterService.resolve(server, scene.owningCampaignId(), role);
            if (!result.available()) {
                if (actor.required() || scene.actorBindings().containsKey(actor.alias())) return result.diagnostic();
                continue;
            }
            var entity = result.entity();
            var prior = scene.actorBindings().get(actor.alias());
            var sourceType = VillagerRetaliation.id(campaignRole ? "campaign_role" : "villager");
            if (prior != null && !prior.sourceType().equals(sourceType)) {
                return "Scene character binding conflicts with its campaign. Restore the original binding before resuming.";
            }
            var binding = prior == null || !entity.getUUID().equals(prior.entityId())
                    ? SceneActorBinding.entity(actor.alias(), actor.actorType(), entity.getUUID(), sourceType,
                            entity.level().dimension().location(), entity.blockPosition(), entity.getDisplayName().getString(), true)
                    : prior.withObservation(entity.level().dimension().location(), entity.blockPosition(), true);
            if (prior != null && !entity.getUUID().equals(prior.entityId())) {
                String reason = CampaignCharacterReplacements.authorization(server, scene.owningCampaignId(), role, prior.entityId(), entity.getUUID());
                if (reason.isBlank()) return "Scene character has no saved campaign replacement authorization. Repair its binding before resuming.";
                var operator = CampaignCharacterReplacements.repairOperator(server, scene.owningCampaignId(), role, prior.entityId(), entity.getUUID());
                var kind = operator == null ? SceneActorBindingService.RebindKind.COMPATIBLE : SceneActorBindingService.RebindKind.OPERATOR;
                var rebound = SceneActorBindingService.rebind(actor, prior, binding, kind,
                        server.overworld().getGameTime(), reason, operator == null ? "" : operator.toString());
                if (!rebound.accepted()) return rebound.diagnostic();
                scene.replaceBinding(actor.alias(), rebound.binding());
                data.changed();
                try { data.persistCampaignHandoff(server.overworld()); }
                catch (IOException exception) {
                    scene.replaceBinding(actor.alias(), prior);
                    data.changed();
                    return "Scene replacement could not be saved: " + exception.getMessage();
                }
                continue;
            }
            if (!binding.equals(prior)) {
                scene.replaceBinding(actor.alias(), binding);
                data.changed();
            }
        }
        return "";
    }
}
