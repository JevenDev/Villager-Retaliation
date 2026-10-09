package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveEvaluationContext;
import com.jvn.villagerretaliation.quest.objectives.QuestObjectiveQuery;
import com.jvn.villagerretaliation.scene.model.CompiledScene;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneInstance;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import java.io.IOException;
import java.util.LinkedHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public final class CampaignFollowService {
    private CampaignFollowService() { }

    public static SceneStepResult prepare(SceneExecutionContext context) {
        var resolved = resolve(context);
        if (!resolved.available()) return waitFor(context, resolved.diagnostic());
        var data = CampaignFollowSavedData.get(context.server().overworld());
        if (!data.diagnostic().isBlank()) return waitFor(context, data.diagnostic());
        var key = key(context);
        var existing = data.journeys().get(key);
        if (existing != null) return retarget(context, resolved, existing, data);
        try {
            for (var other : data.journeys().values()) {
                if (other.state() != CampaignFollowJourney.State.ACTIVE || !other.actor().equals(resolved.guide().getUUID())) continue;
                var scene = context.repository().get(other.key().sceneInstance()).orElse(null);
                if (scene != null && scene.state().terminal()) data.record(other.cancelled());
                else return waitFor(context, "The campaign character is already guiding another journey.");
            }
            var settings = context.step().follow();
            Vec3 target = Vec3.atCenterOf(resolved.destination().position());
            if (resolved.guide().position().distanceTo(target) <= settings.arrivalDistance())
                return SceneStepResult.fail("follow_already_arrived", "The character is already at this destination. Use the authored alternate path.");
            var participants = new LinkedHashMap<CampaignFollowJourney.Participant, CampaignFollowJourney.Trail>();
            var quests = VillagerQuestSavedData.get(context.server().overworld());
            var catalog = QuestContentCatalogs.current(context.server());
            for (var playerId : context.instance().participants()) {
                var player = context.server().getPlayerList().getPlayer(playerId);
                if (!present(player, resolved.guide(), settings.followDistance())
                        || player.position().distanceTo(target) <= settings.arrivalDistance()) continue;
                for (var entry : quests.activeProgress(playerId)) {
                    var quest = catalog.quest(entry.getKey()).orElse(null);
                    var progress = entry.getValue();
                    if (quest == null || !context.instance().owningCampaignId().equals(quest.campaign()) || progress.questRunId() == null
                            || !CampaignQuestAccess.executionIssue(player.serverLevel(), playerId, quest, progress).isBlank()) continue;
                    for (var objective : QuestObjectiveQuery.activeObjectives(quest, progress)) {
                        if (progress.objectiveComplete(objective.id()) || !(objective.story() instanceof CampaignStoryObjective.Follow follow)
                                || !matches(context, resolved, follow)) continue;
                        var participant = new CampaignFollowJourney.Participant(playerId, quest.id(), progress.questRunId(), objective.id());
                        participants.put(participant, new CampaignFollowJourney.Trail(player.position(), player.position(), 0));
                    }
                }
            }
            if (participants.isEmpty()) return waitFor(context, "Join the guide with the active follow objective before the journey begins.");
            var instance = CampaignSavedData.get(context.server().overworld()).instances().get(context.instance().owningCampaignId());
            data.record(new CampaignFollowJourney(key, context.instance().owningCampaignId(), instance.instanceId(), instance.fingerprint(),
                    context.definition().id(), context.definition().definitionHash(), resolved.role(), resolved.location(), resolved.guide().getUUID(),
                    resolved.destination().dimension(), resolved.destination().position(), settings.followDistance(), settings.arrivalDistance(),
                    resolved.guide().position(), resolved.guide().position(), 0, context.gameTime(), context.gameTime(),
                    CampaignFollowJourney.State.ACTIVE, participants));
            return SceneStepResult.ready();
        } catch (IOException | IllegalArgumentException exception) {
            return waitFor(context, "The journey could not be saved. Restore writable campaign storage: " + exception.getMessage());
        }
    }

    static SceneStepResult refreshDestination(SceneExecutionContext context) {
        var resolved = resolve(context);
        if (!resolved.available()) return waitFor(context, resolved.diagnostic());
        var data = CampaignFollowSavedData.get(context.server().overworld());
        if (!data.diagnostic().isBlank()) return waitFor(context, data.diagnostic());
        var journey = data.journeys().get(key(context));
        return journey == null ? SceneStepResult.block("follow_record_missing", "The prepared journey record is missing. Restore its campaign save.")
                : retarget(context, resolved, journey, data);
    }

    private static SceneStepResult retarget(SceneExecutionContext context, Binding binding, CampaignFollowJourney journey, CampaignFollowSavedData data) {
        String issue = identityIssue(context, binding, journey);
        if (!issue.isBlank()) return SceneStepResult.block("follow_identity_changed", issue);
        try { data.retarget(journey, binding.destination(), CampaignLocationSavedData.get(context.server().overworld())); }
        catch (IOException exception) { return waitFor(context, "The repaired journey destination is waiting for world storage."); }
        return SceneStepResult.ready();
    }

    public static SceneStepResult observe(SceneExecutionContext context) {
        var resolved = resolve(context);
        if (!resolved.available()) return waitFor(context, resolved.diagnostic());
        var data = CampaignFollowSavedData.get(context.server().overworld());
        if (!data.diagnostic().isBlank()) return waitFor(context, data.diagnostic());
        var journey = data.journeys().get(key(context));
        if (journey == null) return SceneStepResult.block("follow_record_missing", "The prepared journey record is missing. Restore its campaign save.");
        String issue = identityIssue(context, resolved, journey);
        if (!issue.isBlank()) return SceneStepResult.block("follow_identity_changed", issue);
        try { journey = data.retarget(journey, resolved.destination(), CampaignLocationSavedData.get(context.server().overworld())); }
        catch (IOException exception) { return waitFor(context, "The repaired journey destination is waiting for world storage."); }
        if (journey.state() == CampaignFollowJourney.State.COMPLETED) return SceneStepResult.applied();
        if (journey.state() == CampaignFollowJourney.State.CANCELLED)
            return SceneStepResult.fail("follow_cancelled", "This journey was cancelled. Start its authored alternate path.");
        Vec3 actorPosition = resolved.guide().position();
        double observedReach = Math.max(1.5, Math.min(4.0, Math.max(1, context.gameTime() - journey.observedAt()) * 0.75));
        if (actorPosition.distanceTo(journey.position()) > observedReach)
            return SceneStepResult.block("follow_route_interrupted", "The guide moved beyond the observed route. Restore its last journey position before resuming.");
        var observations = new LinkedHashMap<CampaignFollowJourney.Participant, CampaignFollowJourney.Trail>();
        var quests = VillagerQuestSavedData.get(context.server().overworld());
        var catalog = QuestContentCatalogs.current(context.server());
        Vec3 target = Vec3.atCenterOf(journey.destination());
        boolean arrived = actorPosition.distanceTo(target) <= journey.arrivalDistance();
        for (var entry : journey.participants().entrySet()) {
            var participant = entry.getKey();
            var player = context.server().getPlayerList().getPlayer(participant.player());
            if (!present(player, resolved.guide(), journey.followDistance()))
                return waitFor(context, "The guide is waiting for an enrolled player to return.");
            var quest = catalog.quest(participant.quest()).orElse(null);
            var progress = quests.get(participant.player(), participant.quest());
            if (quest == null || progress == null || !participant.questRun().equals(progress.questRunId())
                    || quests.activeProgress(participant.player()).stream().noneMatch(value -> value.getKey().equals(participant.quest()))
                    || QuestObjectiveQuery.activeObjectives(quest, progress).stream().noneMatch(objective -> objective.id().equals(participant.objective())
                            && objective.story() instanceof CampaignStoryObjective.Follow follow && matches(context, resolved, follow)))
                return SceneStepResult.fail("follow_objective_changed", "An enrolled player's original follow objective is no longer active.");
            issue = CampaignQuestAccess.executionIssue(player.serverLevel(), participant.player(), quest, progress);
            if (!issue.isBlank()) return waitFor(context, issue);
            if (player.position().distanceTo(entry.getValue().position()) > 32)
                return waitFor(context, "A player moved beyond the observed route. Return to the last recorded part of the journey.");
            var trail = entry.getValue().observed(player.position());
            observations.put(participant, trail);
            arrived &= player.position().distanceTo(target) <= journey.arrivalDistance() && trail.distance() > 0;
        }
        arrived &= journey.distance() + journey.position().distanceTo(actorPosition) > 0;
        boolean moved = !actorPosition.equals(journey.position()) || !observations.equals(journey.participants());
        if (moved || arrived) {
            try {
                data.record(journey.observed(actorPosition, observations, context.gameTime(), arrived));
            } catch (IOException | IllegalArgumentException exception) {
                return waitFor(context, "The observed journey could not be saved. Restore writable campaign storage: " + exception.getMessage());
            }
        }
        return arrived ? SceneStepResult.applied() : SceneStepResult.ready();
    }

    public static SceneStepResult cancel(SceneExecutionContext context) {
        stop(context.server(), context.instance(), context.definition());
        var data = CampaignFollowSavedData.get(context.server().overworld());
        var journey = data.journeys().get(key(context));
        if (journey == null || journey.state() != CampaignFollowJourney.State.ACTIVE) return SceneStepResult.complete();
        try {
            data.record(journey.cancelled());
            return SceneStepResult.complete();
        } catch (IOException | IllegalArgumentException exception) {
            return SceneStepResult.waitUntil(context.gameTime() + 20, "Journey cancellation could not be saved: " + exception.getMessage());
        }
    }

    public static boolean completed(QuestObjectiveEvaluationContext context, String objectiveId, CampaignStoryObjective.Follow follow) {
        var data = CampaignFollowSavedData.get(context.level());
        if (!data.diagnostic().isBlank() || context.progress().questRunId() == null) return false;
        var instance = CampaignSavedData.get(context.level()).instances().get(context.definition().campaign());
        var scene = QuestContentCatalogs.current(context.level().getServer()).scenes().get(follow.scene());
        if (instance == null || scene == null) return false;
        for (var journey : data.journeys().values()) {
            if (journey.state() != CampaignFollowJourney.State.COMPLETED || !journey.campaign().equals(context.definition().campaign())
                    || !journey.instance().equals(instance.instanceId()) || !journey.fingerprint().equals(instance.fingerprint())
                    || !journey.definition().equals(follow.scene()) || !journey.definitionHash().equals(scene.definitionHash())
                    || !journey.key().step().equals(follow.step()) || !journey.role().equals(follow.role()) || !journey.location().equals(follow.location())) continue;
            for (var participant : journey.participants().keySet())
                if (participant.player().equals(context.player().getUUID()) && participant.quest().equals(context.definition().id())
                        && participant.questRun().equals(context.progress().questRunId())
                        && participant.objective().equals(objectiveId))
                    return true;
        }
        return false;
    }

    public static void maintain(MinecraftServer server, SceneSavedData scenes) {
        for (var journey : CampaignFollowSavedData.get(server.overworld()).journeys().values()) {
            if (journey.state() != CampaignFollowJourney.State.ACTIVE) continue;
            var scene = scenes.get(journey.key().sceneInstance()).orElse(null);
            if (scene == null || scene.state().terminal() || scene.campaignPaused()
                    || scene.state() == com.jvn.villagerretaliation.scene.runtime.SceneState.BLOCKED
                    || scene.transitionIntent() != SceneInstance.TransitionIntent.NONE) stop(server, journey);
        }
    }

    public static String cleanup(MinecraftServer server, SceneInstance scene) {
        var data = CampaignFollowSavedData.get(server.overworld());
        for (var journey : data.journeys().values()) {
            if (!journey.key().sceneInstance().equals(scene.id()) || journey.state() != CampaignFollowJourney.State.ACTIVE) continue;
            stop(server, journey);
            try { data.record(journey.cancelled()); }
            catch (IOException | IllegalArgumentException exception) { return "Journey cleanup could not be saved: " + exception.getMessage(); }
        }
        return "";
    }

    public static String movementIssue(SceneExecutionContext context, net.minecraft.world.entity.Entity actor) {
        for (var journey : CampaignFollowSavedData.get(context.server().overworld()).journeys().values()) {
            if (journey.state() != CampaignFollowJourney.State.ACTIVE || !journey.actor().equals(actor.getUUID())
                    || journey.key().equals(key(context))) continue;
            var owner = context.repository().get(journey.key().sceneInstance()).orElse(null);
            if (owner == null || !owner.state().terminal()) return "The actor is reserved by another campaign follow journey.";
        }
        return "";
    }

    private static void stop(MinecraftServer server, CampaignFollowJourney journey) {
        for (var level : server.getAllLevels())
            if (level.getEntity(journey.actor()) instanceof Mob mob) mob.getNavigation().stop();
    }

    public static void stop(MinecraftServer server, SceneInstance instance, CompiledScene definition) {
        if (definition == null) return;
        var step = definition.steps().get(instance.currentStep());
        if (step == null || step.follow() == null) return;
        var binding = instance.actorBindings().get(step.follow().actor());
        if (binding == null || binding.entityId() == null) return;
        var key = new CampaignFollowJourney.Key(instance.id(), step.id());
        for (var journey : CampaignFollowSavedData.get(server.overworld()).journeys().values())
            if (journey.state() == CampaignFollowJourney.State.ACTIVE && journey.actor().equals(binding.entityId()) && !journey.key().equals(key)) return;
        for (var level : server.getAllLevels())
            if (level.getEntity(binding.entityId()) instanceof Mob mob) mob.getNavigation().stop();
    }

    static SceneStepResult waitFor(SceneExecutionContext context, String issue) {
        stop(context.server(), context.instance(), context.definition());
        if (context.instance().pauseCampaign(issue, context.gameTime())) context.repository().changed();
        return SceneStepResult.waitUntil(context.gameTime() + (context.step().follow() == null ? 20 : context.step().follow().pollTicks()), issue);
    }

    private static Binding resolve(SceneExecutionContext context) {
        if (!context.server().isSameThread() || context.step().follow() == null || context.instance().owningCampaignId() == null)
            return Binding.unavailable("Following requires a campaign-owned scene on the server.");
        if (CampaignWorldModeSavedData.get(context.server().overworld()).mode() != CampaignWorldMode.CAMPAIGN)
            return Binding.unavailable("Following requires Campaign Mode.");
        var campaign = CampaignResources.current(context.server()).campaigns().get(context.instance().owningCampaignId());
        if (campaign == null) return Binding.unavailable("Campaign content is missing. Restore its datapack.");
        String issue = CampaignSavedData.get(context.server().overworld()).compatibility(campaign.definition());
        if (!issue.isBlank()) return Binding.unavailable(issue);
        var guide = context.definition().actors().get(context.step().follow().actor());
        var destination = context.definition().actors().get(context.step().follow().targetActor());
        if (guide == null || destination == null) return Binding.unavailable("The authored guide or destination is missing.");
        var character = CampaignCharacterService.resolve(context.server(), campaign.definition().id(), guide.bindingReference());
        if (!character.available()) return Binding.unavailable(character.diagnostic());
        var location = CampaignLocationService.resolve(context.server(), campaign.definition().id(), destination.bindingReference());
        if (!location.available()) return Binding.unavailable(location.diagnostic());
        if (!character.entity().level().dimension().location().equals(location.binding().dimension()))
            return Binding.unavailable("The guide and destination are in different dimensions.");
        if (!character.entity().level().hasChunkAt(location.binding().position()))
            return Binding.unavailable("The destination chunk is unloaded. Visit its authored location before following.");
        var bound = context.instance().actorBindings().get(guide.alias());
        var target = context.instance().actorBindings().get(destination.alias());
        if (bound == null || !character.entity().getUUID().equals(bound.entityId()) || target == null
                || !location.binding().dimension().equals(target.lastDimension()) || !location.binding().position().equals(target.lastPosition()))
            return Binding.unavailable("Scene bindings do not match the campaign's guide and destination.");
        return new Binding(character.entity(), location.binding(), guide.bindingReference(), destination.bindingReference(), "");
    }

    private static String identityIssue(SceneExecutionContext context, Binding binding, CampaignFollowJourney journey) {
        var instance = CampaignSavedData.get(context.server().overworld()).instances().get(context.instance().owningCampaignId());
        return instance != null && journey.campaign().equals(context.instance().owningCampaignId()) && journey.instance().equals(instance.instanceId())
                && journey.fingerprint().equals(instance.fingerprint()) && journey.definition().equals(context.definition().id())
                && journey.definitionHash().equals(context.definition().definitionHash()) && journey.actor().equals(binding.guide().getUUID())
                && journey.role().equals(binding.role()) && journey.location().equals(binding.location())
                && journey.dimension().equals(binding.destination().dimension())
                && (journey.destination().equals(binding.destination().position()) || !CampaignLocationSavedData.get(context.server().overworld())
                        .authorization(binding.destination(), journey.dimension(), journey.destination()).isEmpty())
                && journey.followDistance() == context.step().follow().followDistance() && journey.arrivalDistance() == context.step().follow().arrivalDistance()
                ? "" : "The saved journey no longer matches its campaign, scene, character or destination. Restore compatible content and bindings.";
    }

    private static boolean matches(SceneExecutionContext context, Binding binding, CampaignStoryObjective.Follow follow) {
        return follow.scene().equals(context.definition().id()) && follow.step().equals(context.step().id())
                && follow.role().equals(binding.role()) && follow.location().equals(binding.location());
    }

    private static boolean present(ServerPlayer player, Villager guide, double radius) {
        return player != null && player.isAlive() && !player.isSpectator() && player.serverLevel() == guide.level()
                && player.distanceToSqr(guide) <= radius * radius;
    }

    private static CampaignFollowJourney.Key key(SceneExecutionContext context) {
        return new CampaignFollowJourney.Key(context.instance().id(), context.step().id());
    }

    private record Binding(Villager guide, CampaignLocationBinding destination, String role, String location, String diagnostic) {
        boolean available() { return guide != null && destination != null && diagnostic.isBlank(); }
        static Binding unavailable(String issue) { return new Binding(null, null, "", "", issue); }
    }
}
