package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import java.io.IOException;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class CampaignMovementRepairs {
    private CampaignMovementRepairs() { }

    public static SceneStepResult reconcile(SceneExecutionContext context) {
        if (context.instance().owningCampaignId() == null) return SceneStepResult.ready();
        var target = context.step().parameters().get("target_actor");
        String alias = context.step().follow() != null ? context.step().follow().targetActor()
                : target != null && target.isJsonPrimitive() ? target.getAsString() : "";
        var actor = context.definition().actors().get(alias);
        if (actor == null || actor.bindingSource() != SceneActorDeclaration.BindingSource.CAMPAIGN_LOCATION) return SceneStepResult.ready();
        if (context.step().follow() != null) {
            var journey = CampaignFollowSavedData.get(context.server().overworld()).journeys()
                    .get(new CampaignFollowJourney.Key(context.instance().id(), context.step().id()));
            if (journey != null && journey.state() != CampaignFollowJourney.State.ACTIVE) return SceneStepResult.ready();
        }
        var resolved = CampaignBindingQueries.location(context.server(), context.instance().owningCampaignId(), actor.bindingReference());
        if (!resolved.diagnostic().isBlank() || resolved.binding() == null)
            return SceneStepResult.block("movement_location_unavailable", resolved.diagnostic().isBlank() ? "The campaign destination is unbound." : resolved.diagnostic());
        var values = context.record().durableValues();
        var dimension = ResourceLocation.tryParse(values.getOrDefault("destination_dimension", ""));
        BlockPos position;
        try {
            position = new BlockPos(Integer.parseInt(values.get("destination_x")), Integer.parseInt(values.get("destination_y")),
                    Integer.parseInt(values.get("destination_z")));
        } catch (NumberFormatException exception) {
            return SceneStepResult.block("movement_destination_invalid", "The saved movement destination is unreadable.");
        }
        var binding = resolved.binding();
        if (binding.dimension().equals(dimension) && binding.position().equals(position)) return SceneStepResult.ready();
        if (CampaignLocationSavedData.get(context.server().overworld()).authorization(binding, dimension, position).isEmpty())
            return SceneStepResult.block("movement_repair_missing", "The saved movement destination has no campaign repair authorization.");
        var updated = Map.of("destination_dimension", binding.dimension().toString(), "destination_x", Integer.toString(binding.position().getX()),
                "destination_y", Integer.toString(binding.position().getY()), "destination_z", Integer.toString(binding.position().getZ()));
        updated.forEach(context.record()::putDurableValue);
        context.repository().changed();
        try {
            context.repository().persistCampaignHandoff(context.server().overworld());
            return SceneStepResult.ready();
        } catch (IOException exception) {
            updated.keySet().forEach(key -> context.record().putDurableValue(key, values.get(key)));
            context.repository().changed();
            String issue = "The repaired movement destination is waiting for world storage.";
            return context.step().follow() == null ? SceneStepResult.waitUntil(context.gameTime() + 20, issue) : CampaignFollowService.waitFor(context, issue);
        }
    }
}
