package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public final class CampaignSceneLocations {
    private CampaignSceneLocations() { }

    public static String resolve(MinecraftServer server, SceneSavedData data, SceneInstance scene, CompiledScene definition) {
        for (var actor : definition.actors().values()) {
            if (actor.bindingSource() != SceneActorDeclaration.BindingSource.CAMPAIGN_LOCATION) continue;
            var result = CampaignLocationService.resolve(server, scene.owningCampaignId(), actor.bindingReference());
            if (!result.available()) {
                if (actor.required() || scene.actorBindings().containsKey(actor.alias())) return result.diagnostic();
                continue;
            }
            var location = result.binding();
            var binding = new SceneActorBinding(actor.alias(), actor.actorType(), location.instance() + "/" + location.alias(), null,
                    VillagerRetaliation.id("campaign_location"), location.dimension(), location.position(),
                    Map.of("campaign", location.campaign().toString(), "location", location.alias()), 1L,
                    SceneActorBinding.BindingState.SNAPSHOT, List.of());
            var prior = scene.actorBindings().get(actor.alias());
            if (prior != null && !prior.equals(binding)) return "Scene location binding conflicts with its campaign. Restore the original binding before resuming.";
            if (prior == null) {
                scene.replaceBinding(actor.alias(), binding);
                data.changed();
            }
        }
        return "";
    }
}
