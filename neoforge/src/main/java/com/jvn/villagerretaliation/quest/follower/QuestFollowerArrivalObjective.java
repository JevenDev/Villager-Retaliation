package com.jvn.villagerretaliation.quest.follower;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveResult;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveType;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public final class QuestFollowerArrivalObjective implements QuestObjectiveType<Void> {
    @Override
    public Optional<JsonObject> structuralSchema() {
        return Optional.of(JsonParser.parseString("""
                {"type":"object","required":["match"],"properties":{"match":{"type":"object","required":["actor"],
                  "properties":{"actor":{"type":"string","pattern":"^[a-z][a-z0-9_.-]{0,63}$"},
                    "destination_tag":{"type":"string","minLength":1,"maxLength":128}}}},
                  "oneOf":[{"required":["location"]},{"properties":{"match":{"required":["destination_tag"]}}}]}
                """).getAsJsonObject());
    }

    @Override
    public Optional<String> validationError(QuestDefinition.Objective objective) {
        boolean tagged = objective.criterionData().containsKey("destination_tag");
        return (objective.location() != null) == tagged || !objective.criterionData().containsKey("actor")
                ? Optional.of("follower_arrival requires match.actor and either location or match.destination_tag.") : Optional.empty();
    }

    public static Optional<Destination> destination(ServerLevel level, QuestDefinition.Objective objective) {
        String tag = objective.criterionData().get("destination_tag");
        if (tag == null) return objective.location() == null ? Optional.empty()
                : Optional.of(new Destination(objective.location(), objective.dimension() == null ? level.dimension() : objective.dimension(), ""));
        LivingEntity found = null;
        for (var candidateLevel : level.getServer().getAllLevels()) {
            if (objective.dimension() != null && !candidateLevel.dimension().equals(objective.dimension())) continue;
            for (var entity : candidateLevel.getAllEntities()) {
                if (!(entity instanceof LivingEntity living) || !living.isAlive() || !entity.getTags().contains(tag)) continue;
                if (found != null) return Optional.empty();
                found = living;
            }
        }
        return found == null ? Optional.empty() : Optional.of(new Destination(found.blockPosition(),
                found.level().dimension(), found.getDisplayName().getString()));
    }

    @Override
    public QuestObjectiveResult evaluate(QuestObjectiveEvaluationContext context, QuestDefinition.Objective objective) {
        var target = destination(context.level(), objective).orElse(null);
        if (target == null) return QuestObjectiveResult.incomplete(0, "Destination NPC is missing or ambiguous.");
        boolean arrived = QuestFollowerService.arrived(context.level(), context.player().getUUID(), context.definition().id(),
                objective.criterionData().get("actor"), target.position(), target.dimension(), objective.radius());
        return arrived ? QuestObjectiveResult.complete("") : QuestObjectiveResult.incomplete(0, "Waiting for escorted NPC.");
    }

    public record Destination(BlockPos position, ResourceKey<Level> dimension, String name) { }
}
