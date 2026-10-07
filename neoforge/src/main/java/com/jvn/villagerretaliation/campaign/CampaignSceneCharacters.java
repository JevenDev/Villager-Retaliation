package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import net.minecraft.server.MinecraftServer;

public final class CampaignSceneCharacters {
    private CampaignSceneCharacters() { }

    public static String resolve(MinecraftServer server, SceneSavedData data, SceneInstance scene, CompiledScene definition) {
        for (var actor : definition.actors().values()) {
            if (actor.bindingSource() != SceneActorDeclaration.BindingSource.CAMPAIGN_ROLE) continue;
            var result = CampaignCharacterService.resolve(server, scene.owningCampaignId(), actor.bindingReference());
            if (!result.available()) {
                if (actor.required() || scene.actorBindings().containsKey(actor.alias())) return result.diagnostic();
                continue;
            }
            var entity = result.entity();
            var prior = scene.actorBindings().get(actor.alias());
            if (prior != null && (!entity.getUUID().equals(prior.entityId())
                    || !prior.sourceType().equals(VillagerRetaliation.id("campaign_role")))) {
                return "Scene character binding conflicts with its campaign. Restore the original binding before resuming.";
            }
            var binding = prior == null ? SceneActorBinding.entity(actor.alias(), actor.actorType(), entity.getUUID(),
                    VillagerRetaliation.id("campaign_role"), entity.level().dimension().location(), entity.blockPosition(), entity.getDisplayName().getString(), true)
                    : prior.withObservation(entity.level().dimension().location(), entity.blockPosition(), true);
            if (!binding.equals(prior)) {
                scene.replaceBinding(actor.alias(), binding);
                data.changed();
            }
        }
        return "";
    }
}
