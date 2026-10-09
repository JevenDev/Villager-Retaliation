package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
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
            if (prior != null) {
                if (!prior.alias().equals(binding.alias()) || !prior.actorType().equals(binding.actorType())
                        || !prior.targetIdentity().equals(binding.targetIdentity()) || prior.entityId() != null
                        || !prior.sourceType().equals(binding.sourceType()) || !prior.displaySnapshot().equals(binding.displaySnapshot())
                        || prior.state() != SceneActorBinding.BindingState.SNAPSHOT)
                    return "Scene location ownership conflicts with its campaign. Restore the original binding before resuming.";
                if (java.util.Objects.equals(prior.lastDimension(), binding.lastDimension())
                        && java.util.Objects.equals(prior.lastPosition(), binding.lastPosition())) continue;
                var repairs = CampaignLocationSavedData.get(server.overworld()).authorization(location, prior.lastDimension(), prior.lastPosition());
                if (repairs.isEmpty()) return "Scene location has no saved campaign repair authorization. Restore its original binding before resuming.";
                var repair = repairs.getLast();
                var history = new ArrayList<>(prior.replacementHistory());
                history.add(new SceneActorBinding.ReplacementHistoryEntry(prior.targetIdentity(), binding.targetIdentity(),
                        prior.generation(), prior.generation() + 1, "campaign_location_repair:" + repair.operation(),
                        server.overworld().getGameTime(), repair.operator().toString()));
                var updated = new SceneActorBinding(binding.alias(), binding.actorType(), binding.targetIdentity(), null,
                        binding.sourceType(), binding.lastDimension(), binding.lastPosition(), binding.displaySnapshot(),
                        prior.generation() + 1, binding.state(), history);
                scene.replaceBinding(actor.alias(), updated);
                data.changed();
                try { data.persistCampaignHandoff(server.overworld()); }
                catch (IOException exception) {
                    scene.replaceBinding(actor.alias(), prior);
                    data.changed();
                    return "Scene location repair could not be saved: " + exception.getMessage();
                }
                continue;
            }
            if (prior == null) {
                scene.replaceBinding(actor.alias(), binding);
                data.changed();
            }
        }
        return "";
    }
}
