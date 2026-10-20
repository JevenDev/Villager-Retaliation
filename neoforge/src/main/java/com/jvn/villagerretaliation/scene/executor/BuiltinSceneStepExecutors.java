package com.jvn.villagerretaliation.scene.executor;

import com.jvn.villagerretaliation.dialogue.EntityDialogueContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutor;
import com.jvn.villagerretaliation.api.scene.SceneStepExecutors;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationActors;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.scene.actor.SceneActorBinding;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.SceneLifecycleIntegration;
import com.jvn.villagerretaliation.scene.model.SceneQuestTransition;
import com.jvn.villagerretaliation.scene.runtime.SceneExecutionContext;
import com.jvn.villagerretaliation.scene.runtime.SceneOperationReceipt;
import com.jvn.villagerretaliation.scene.runtime.SceneReceiptGuard;
import com.jvn.villagerretaliation.scene.runtime.SceneStepResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public final class BuiltinSceneStepExecutors {
    private static boolean registered;

    private BuiltinSceneStepExecutors() {}

    public static synchronized void register() {
        if (registered) return;
        register("wait_ticks", new WaitTicks());
        register("wait_condition", new WaitCondition());
        var movement = new MoveActor();
        register("move_actor", movement);
        register("follow_actor", new com.jvn.villagerretaliation.campaign.CampaignFollowStepExecutor(movement));
        register("face_actor", new Face(false));
        register("face_position", new Face(true));
        register("dialogue", new Dialogue());
        register("action_batch", new ActionBatch());
        register("quest_transition", new QuestTransition());
        register("scene_branch", new Branch());
        register("scene_complete", new Terminal());
        register("scene_fail", new Terminal());
        registered = true;
    }

    private static void register(String id, SceneStepExecutor executor) {
        SceneStepExecutors.register(VillagerRetaliation.id(id), executor);
    }

    private abstract static class Base implements SceneStepExecutor {
        private final RecoveryMode recovery;

        Base(RecoveryMode recovery) {
            this.recovery = recovery;
        }

        public RecoveryMode recoveryMode() {
            return recovery;
        }

        public SceneStepResult prepare(SceneExecutionContext context) {
            return SceneStepResult.ready();
        }

        public SceneStepResult verify(SceneExecutionContext context) {
            return SceneStepResult.complete();
        }

        public SceneStepResult reconcile(SceneExecutionContext context) {
            return apply(context);
        }
    }

    private static final class WaitTicks extends Base {
        WaitTicks() {
            super(RecoveryMode.NATURALLY_IDEMPOTENT);
        }

        public SceneStepResult prepare(SceneExecutionContext c) {
            if (c.record().durableValues().get("wake_time") == null)
                c.record()
                        .putDurableValue(
                                "wake_time",
                                Long.toString(
                                        c.gameTime() + Math.max(0, longValue(c, "ticks", 0))));
            return SceneStepResult.ready();
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            long wake = durableLong(c, "wake_time", c.gameTime());
            return c.gameTime() < wake
                    ? SceneStepResult.waitUntil(wake, "waiting for authored tick deadline")
                    : SceneStepResult.applied();
        }
    }

    private static final class WaitCondition extends Base {
        WaitCondition() {
            super(RecoveryMode.NATURALLY_IDEMPOTENT);
        }

        public SceneStepResult prepare(SceneExecutionContext c) {
            long timeout = longValue(c, "timeout_ticks", 0);
            if (timeout > 0 && c.record().durableValues().get("deadline") == null)
                c.record().putDurableValue("deadline", Long.toString(c.gameTime() + timeout));
            return SceneStepResult.ready();
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            DialogueRuntimeContext dialogue = context(c);
            if (dialogue == null)
                return actorUnavailable(c, "condition context actors are unavailable");
            List<DialogueCondition> conditions =
                    DialogueCondition.readList(
                            c.definition().id(), "scene " + c.step().id(), c.step().parameters());
            if (DialogueCondition.matchesAll(dialogue, conditions))
                return SceneStepResult.applied();
            long deadline = durableLong(c, "deadline", 0);
            if (deadline > 0 && c.gameTime() >= deadline)
                return SceneStepResult.fail("condition_timeout", "wait_condition timed out");
            return SceneStepResult.waitUntil(
                    c.gameTime() + Math.max(1, longValue(c, "poll_ticks", 20)),
                    "waiting for registered condition");
        }
    }

    private static final class MoveActor extends Base {
        MoveActor() {
            super(RecoveryMode.WORLD_RECONCILED);
        }

        public SceneStepResult prepare(SceneExecutionContext c) {
            Destination destination = destination(c);
            if (destination == null)
                return SceneStepResult.block(
                        "destination_missing", "move_actor has no resolvable target");
            c.record().putDurableValue("destination_dimension", destination.dimension.toString());
            c.record().putDurableValue("destination_x", Integer.toString(destination.pos.getX()));
            c.record().putDurableValue("destination_y", Integer.toString(destination.pos.getY()));
            c.record().putDurableValue("destination_z", Integer.toString(destination.pos.getZ()));
            long timeout = c.step().follow() == null ? longValue(c, "timeout_ticks", 20L * 60L) : c.step().follow().timeoutTicks();
            c.record().putDurableValue("deadline", Long.toString(c.gameTime() + timeout));
            return SceneStepResult.ready();
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            Entity actor = actor(c, sourceAlias(c));
            if (actor == null)
                return actorUnavailable(c, "moving actor is unavailable or unloaded");
            String reservation = com.jvn.villagerretaliation.campaign.CampaignFollowService.movementIssue(c, actor);
            if (!reservation.isBlank()) return SceneStepResult.waitUntil(c.gameTime() + 20, reservation);
            var repaired = com.jvn.villagerretaliation.campaign.CampaignMovementRepairs.reconcile(c);
            if (repaired.outcome() != SceneStepResult.Outcome.READY) {
                if (actor instanceof Mob mob) mob.getNavigation().stop();
                return repaired;
            }
            Destination target = durableDestination(c);
            if (target == null)
                return SceneStepResult.block(
                        "destination_lost", "persisted movement destination is unreadable");
            ServerLevel level =
                    c.server()
                            .getLevel(
                                    ResourceKey.create(
                                            net.minecraft.core.registries.Registries.DIMENSION,
                                            target.dimension));
            if (level == null || !level.hasChunkAt(target.pos))
                return waitOrTimeout(c, "destination chunk is unloaded");
            if (actor.level() != level)
                return movementFailure(c, actor, target, "actor is in another dimension");
            double arrival = c.step().follow() == null ? Math.max(0.25, doubleValue(c, "arrival_distance", 1.5)) : c.step().follow().arrivalDistance();
            if (actor.distanceToSqr(Vec3.atCenterOf(target.pos)) <= arrival * arrival)
                return SceneStepResult.applied();
            if (!(actor instanceof Mob mob))
                return movementFailure(c, actor, target, "actor type has no path navigation");
            boolean moving =
                    mob.getNavigation()
                            .moveTo(
                                    target.pos.getX() + 0.5,
                                    target.pos.getY(),
                                    target.pos.getZ() + 0.5,
                                    c.step().follow() == null ? Math.max(0.05, doubleValue(c, "speed", 0.55)) : c.step().follow().speed());
            if (!moving && mob.getNavigation().isDone())
                return movementFailure(c, actor, target, "pathfinder could not create a path");
            SceneActorBinding binding = c.instance().actorBindings().get(sourceAlias(c));
            if (binding != null)
                c.instance()
                        .replaceBinding(
                                sourceAlias(c),
                                binding.withObservation(
                                        level.dimension().location(), actor.blockPosition(), true));
            return waitOrTimeout(c, "actor is moving");
        }

        private SceneStepResult waitOrTimeout(SceneExecutionContext c, String message) {
            return c.step().follow() == null && c.gameTime() >= durableLong(c, "deadline", Long.MAX_VALUE)
                    ? SceneStepResult.fail("movement_timeout", message)
                    : SceneStepResult.waitUntil(
                            c.gameTime() + (c.step().follow() == null ? Math.max(1, longValue(c, "poll_ticks", 5)) : c.step().follow().pollTicks()), message);
        }

        private SceneStepResult movementFailure(
                SceneExecutionContext c, Entity actor, Destination target, String message) {
            String policy = c.step().follow() == null ? string(c, "path_failure_policy", "block").toLowerCase(Locale.ROOT) : "block";
            if (policy.equals("skip")) return SceneStepResult.skip();
            if (policy.equals("teleport") && bool(c, "allow_teleport", false)) {
                actor.teleportTo(
                        target.pos.getX() + 0.5, target.pos.getY(), target.pos.getZ() + 0.5);
                return SceneStepResult.applied();
            }
            return policy.equals("fail")
                    ? SceneStepResult.fail("movement_path_failed", message)
                    : SceneStepResult.block("movement_path_failed", message);
        }
    }

    private static final class Face extends Base {
        private final boolean position;

        Face(boolean position) {
            super(RecoveryMode.NATURALLY_IDEMPOTENT);
            this.position = position;
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            Entity source = actor(c, sourceAlias(c));
            if (!(source instanceof LivingEntity living))
                return actorUnavailable(c, "facing actor is unavailable");
            Vec3 target;
            if (position) {
                Destination destination = destination(c);
                if (destination == null) return actorUnavailable(c, "face position is unavailable");
                target = Vec3.atCenterOf(destination.pos);
            } else {
                Entity entity = actor(c, string(c, "target_actor", ""));
                if (entity == null) return actorUnavailable(c, "face target actor is unavailable");
                target = entity.getEyePosition();
            }
            living.lookAt(EntityAnchorArgument.Anchor.EYES, target);
            return SceneStepResult.applied();
        }
    }

    private static final class Dialogue extends Base {
        Dialogue() {
            super(RecoveryMode.RECEIPT_REQUIRED);
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            if (!c.step().actors().isEmpty())
                for (String alias : c.step().actors())
                    if (actor(c, alias) == null) {
                        SceneStepResult missing =
                                actorUnavailable(
                                        c, alias, "dialogue actor " + alias + " is unavailable");
                        if (missing.outcome() != SceneStepResult.Outcome.SKIP) return missing;
                    }
            String text = string(c, "text", "");
            var exchanges = com.jvn.villagerretaliation.dialogue.DialogueExchange.read(c.step().parameters());
            if (text.isBlank() && exchanges.isEmpty())
                return SceneStepResult.fail("dialogue_text_missing", "dialogue text is empty");
            var dialogue = exchanges.isEmpty() ? null : context(c);
            var provider = firstActor(c);
            var speakingPlayer = dialogue != null ? dialogue.player() : c.instance().owner().playerId() == null ? null
                    : c.server().getPlayerList().getPlayer(c.instance().owner().playerId());
            if (exchanges.stream().anyMatch(line -> line.speaker() == com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind.ROLE) && provider == null)
                return actorUnavailable(c, "dialogue provider is unavailable");
            if (exchanges.stream().anyMatch(line -> line.speaker() == com.jvn.villagerretaliation.dialogue.conversation.ConversationDefinition.SpeakerKind.PLAYER) && speakingPlayer == null)
                return actorUnavailable(c, "dialogue player is unavailable");
            List<UUID> recipients = new ArrayList<>(c.instance().participants());
            if (recipients.isEmpty() && c.instance().owner().playerId() != null)
                recipients.add(c.instance().owner().playerId());
            for (UUID id : recipients) {
                ServerPlayer player = c.server().getPlayerList().getPlayer(id);
                if (player == null) {
                    String policy = string(c, "offline_policy", "wait").toLowerCase(Locale.ROOT);
                    if (policy.equals("skip")) continue;
                    if (policy.equals("fail"))
                        return SceneStepResult.fail(
                                "dialogue_recipient_offline",
                                "dialogue recipient " + id + " is offline");
                    return SceneStepResult.waitUntil(
                            c.gameTime() + Math.max(1, longValue(c, "offline_poll_ticks", 20)),
                            "waiting for dialogue recipient " + id);
                }
                if (provider != null && !(provider instanceof Villager) && provider.level() != player.serverLevel())
                    return SceneStepResult.waitUntil(c.gameTime() + 20L, "waiting for the dialogue recipient to return to the actor's dimension");
                var applied =
                        SceneReceiptGuard.applyOnce(
                                c,
                                "dialogue/" + id,
                                SceneOperationReceipt.Kind.DIALOGUE_DELIVERY,
                                () -> {
                                    if (exchanges.isEmpty()) player.sendSystemMessage(Component.literal(text));
                                    else {
                                        var replacements = new java.util.LinkedHashMap<String, String>();
                                        if (dialogue != null) replacements.putAll(com.jvn.villagerretaliation.dialogue.normal.DialoguePlaceholders.base(dialogue));
                                        if (speakingPlayer != null) replacements.put("player", speakingPlayer.getName().getString());
                                        var lines = exchanges.stream().map(exchange -> {
                                            Entity speaker = switch (exchange.speaker()) {
                                                case ROLE -> provider;
                                                case PLAYER -> speakingPlayer;
                                                case NARRATOR -> null;
                                            };
                                            String line = com.jvn.villagerretaliation.dialogue.conversation.ConversationText.resolve(
                                                    player, exchange.textKey(), exchange.text(), replacements, true);
                                            return new com.jvn.villagerretaliation.dialogue.DialogueSpeech(speaker == null ? null : speaker.getUUID(),
                                                    speaker == null ? -1 : speaker.getId(), speaker == null ? "" : dialogue == null ? speaker.getName().getString() : dialogue.displayName(speaker),
                                                    com.jvn.villagerretaliation.dialogue.normal.DialogueTextSegment.parse(line,
                                                            com.jvn.villagerretaliation.dialogue.normal.DialogueTextEffects.NONE), exchange.camera());
                                        }).toList();
                                        if (provider instanceof Villager villager) VillagerInteractionService.sendDialogueExchanges(player, villager, lines, 0);
                                        else {
                                            var recipientContext = provider == null
                                                    ? new EntityDialogueContext(player.serverLevel(), player, null, player.clientInformation().language(), "")
                                                    : ConversationActors.context(player, provider);
                                            recipientContext.runtime().finish(recipientContext, lines, "", false);
                                        }
                                    }
                                },
                                "recipient=" + id);
                if (applied.status() == SceneReceiptGuard.Status.AMBIGUOUS_PREPARED)
                    return SceneStepResult.block(
                            "dialogue_delivery_ambiguous",
                            "cannot prove whether dialogue was delivered before reload");
                applied.receipt().completed(c.gameTime(), "recipient=" + id);
            }
            return SceneStepResult.applied();
        }

        public SceneStepResult reconcile(SceneExecutionContext c) {
            return apply(c);
        }
    }

    private static com.jvn.villagerretaliation.action.ActionResult executeAction(SceneExecutionContext execution,
            DialogueRuntimeContext context, VillagerActionDefinition action) {
        return executeAction(execution, context, action, null);
    }

    private static com.jvn.villagerretaliation.action.ActionResult executeAction(SceneExecutionContext execution,
            DialogueRuntimeContext context, VillagerActionDefinition action,
            com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants approvalParticipants) {
        if (!(context instanceof DialogueContext) && (!execution.instance().campaignConsent().isEmpty()
                || !execution.instance().decisionParticipants().isEmpty() || action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME))
            return com.jvn.villagerretaliation.action.ActionResult.unavailable("Campaign actions require their campaign actor adapter.",
                    java.util.Set.of(), com.jvn.villagerretaliation.action.VillagerActionResult.EMPTY);
        var participants = approvalParticipants == null ? execution.instance().decisionParticipants() : approvalParticipants.save();
        String issue = com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.issue(participants);
        if (!issue.isBlank()) return com.jvn.villagerretaliation.action.ActionResult.unavailable(issue,
                VillagerActionRegistry.capabilities(action), com.jvn.villagerretaliation.action.VillagerActionResult.EMPTY);
        var authority = execution.instance().conversationEffect().isEmpty() ? null
                : com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.resolve(execution.server(), execution.instance());
        if (authority != null && !authority.diagnostic().isBlank()) return com.jvn.villagerretaliation.action.ActionResult.unavailable(authority.diagnostic(),
                VillagerActionRegistry.capabilities(action), com.jvn.villagerretaliation.action.VillagerActionResult.EMPTY);
        var progress = authority == null || action.questId() == null ? null
                : com.jvn.villagerretaliation.quest.runtime.QuestRuntimes.progress(context.level(), authority.actor(), action.questId());
        boolean shared = authority != null && action.kind() == VillagerActionDefinition.Kind.CAMPAIGN_OUTCOME;
        if (shared && progress == null) return com.jvn.villagerretaliation.action.ActionResult.unavailable("The deciding player's original quest run is unavailable.",
                VillagerActionRegistry.capabilities(action), com.jvn.villagerretaliation.action.VillagerActionResult.EMPTY);
        var actor = shared ? new com.jvn.villagerretaliation.campaign.CampaignOutcomeService.Actor(authority.actor(), progress.questRunId()) : null;
        java.util.function.Supplier<com.jvn.villagerretaliation.action.ActionResult> apply = () ->
                com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.withScene(execution.instance(),
                        () -> context instanceof DialogueContext legacy
                                ? VillagerActionRegistry.execute(legacy, action, java.util.Map.of(), actor)
                                : context.actions().execute(context, action, java.util.Map.of()));
        var result = new com.jvn.villagerretaliation.action.ActionResult[1];
        com.jvn.villagerretaliation.campaign.CampaignQuestAcceptanceService.withConsent(execution.instance().campaignConsent(),
                () -> result[0] = com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.withParticipants(participants,
                        () -> authority == null ? apply.get()
                                : authority.withController(apply)));
        return result[0];
    }

    private static final class ActionBatch extends Base {
        ActionBatch() {
            super(RecoveryMode.RECEIPT_REQUIRED);
        }

        public SceneStepResult prepare(SceneExecutionContext c) {
            JsonElement actions = c.step().parameters().get("actions");
            if (actions == null || !actions.isJsonArray())
                return SceneStepResult.fail(
                        "actions_missing", "action_batch requires actions array");
            int i = 0;
            for (JsonElement raw : actions.getAsJsonArray()) {
                if (!raw.isJsonObject() || string(raw.getAsJsonObject(), "id", "").isBlank())
                    return SceneStepResult.fail(
                            "action_id_missing",
                            "action_batch action[" + i + "] requires stable id");
                i++;
            }
            return SceneStepResult.ready();
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            for (JsonElement raw : c.step().parameters().getAsJsonArray("actions")) {
                JsonObject object = raw.getAsJsonObject();
                String id = string(object, "id", "");
                JsonObject wrapper = new JsonObject();
                JsonArray array = new JsonArray();
                array.add(object);
                wrapper.add("actions", array);
                List<VillagerActionDefinition> parsed =
                        VillagerActionDefinition.readList(
                                c.definition().id(), "scene action " + id, wrapper, null);
                if (parsed.size() != 1)
                    return SceneStepResult.fail(
                            "action_invalid", "safe action " + id + " did not parse");
                var action = parsed.getFirst();
                SceneOperationReceipt previous = c.instance().receipts().get(c.operationId("action/" + id));
                boolean recorded = !c.instance().conversationEffect().isEmpty() && previous != null
                        && previous.settled() && !previous.actionResult().suspended();
                DialogueRuntimeContext dialogue = recorded ? null
                        : c.instance().conversationEffect().isEmpty() ? context(c) : conversationContext(c, action);
                if (dialogue == null && !recorded) {
                    if (c.instance().conversationEffect().isEmpty()) return actorUnavailable(c, "action context actors are unavailable");
                    if (c.preparedThisSession() && previous == null) c.record().putDurableValue("waiting_player_action", id);
                    return SceneStepResult.waitUntil(c.gameTime() + 20L, "waiting for the original player and scene character");
                }
                SceneOperationReceipt.Kind kind = receiptKind(action);
                boolean playerReturned = previous == null && id.equals(c.record().durableValues().get("waiting_player_action"));
                if (playerReturned) {
                    c = new SceneExecutionContext(c.server(), c.repository(), c.instance(), c.definition(),
                            c.step(), c.record(), c.gameTime(), true);
                }
                var approval = com.jvn.villagerretaliation.dialogue.conversation.ConversationNestedConfirmations.beforeScene(c, dialogue, id, action);
                if (!approval.waiting().isBlank()) return SceneStepResult.waitUntil(c.gameTime() + 20L, approval.waiting());
                if (approval.resolved() && previous == null) c = new SceneExecutionContext(c.server(), c.repository(), c.instance(), c.definition(),
                        c.step(), c.record(), c.gameTime(), true);
                boolean resuming = previous != null && previous.state() == SceneOperationReceipt.ReceiptState.WAITING;
                UUID originatingScene = c.instance().id();
                var execution = c;
                var guarded =
                        SceneReceiptGuard.applyActionOnce(
                                c,
                                "action/" + id,
                                kind,
                                () ->
                                        approval.rejection() != null ? approval.rejection() : SceneLifecycleIntegration.withOriginatingScene(originatingScene, () ->
                                                executeAction(execution, dialogue, action, approval.participants())),
                                approval.resolved() && approval.rejection() != null
                                        || dialogue instanceof DialogueContext && com.jvn.villagerretaliation.campaign.CampaignSceneApproval.canRecover(c.instance(), action));
                com.jvn.villagerretaliation.dialogue.conversation.ConversationNestedConfirmations.settle(c.server(), c.instance().conversationEffect());
                if (guarded.ambiguous())
                    return SceneStepResult.block(
                            "action_ambiguous",
                            "cannot prove whether action " + id + " applied before reload");
                if (guarded.outcome().suspended()) {
                    return SceneStepResult.waitUntil(c.gameTime() + 1L, "waiting for action " + id);
                }
                if (action.required() && !guarded.outcome().success()) {
                    return SceneStepResult.fail("required_action_failed", "action " + id + ": " + guarded.outcome().message());
                }
                if (playerReturned) c.record().putDurableValue("waiting_player_action", "");
                if (resuming) {
                    // the persisted wait proves later batch actions have not run yet
                    c = new SceneExecutionContext(c.server(), c.repository(), c.instance(), c.definition(),
                            c.step(), c.record(), c.gameTime(), true);
                }
            }
            return SceneStepResult.applied();
        }

        public SceneStepResult reconcile(SceneExecutionContext c) {
            return apply(c);
        }
    }

    private static final class QuestTransition extends Base {
        QuestTransition() {
            super(RecoveryMode.RECEIPT_REQUIRED);
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            DialogueRuntimeContext dialogue = context(c);
            if (dialogue == null) {
                if (c.instance().conversationEffect().isEmpty()) return actorUnavailable(c, "quest transition context is unavailable");
                if (c.preparedThisSession() && !c.instance().receipts().containsKey(c.operationId("quest_transition"))) {
                    c.record().putDurableValue("waiting_player_transition", "true");
                }
                return SceneStepResult.waitUntil(c.gameTime() + 20L, "waiting for the original player and scene character");
            }
            boolean playerReturned = !c.instance().receipts().containsKey(c.operationId("quest_transition"))
                    && "true".equals(c.record().durableValues().get("waiting_player_transition"));
            SceneQuestTransition transition;
            try {
                transition = SceneQuestTransition.parse(c.step().parameters());
            } catch (IllegalArgumentException exception) {
                return SceneStepResult.fail("quest_transition_invalid", exception.getMessage());
            }
            JsonObject action = new JsonObject();
            action.addProperty("quest", transition.questId().toString());
            if (transition.target() == SceneQuestTransition.Target.STAGE) {
                var progress =
                        com.jvn.villagerretaliation.quest.runtime.QuestRuntimes.progress(dialogue.level(),
                                dialogue.player().getUUID(), transition.questId());
                action.addProperty("type", "quest_transition");
                action.addProperty("target_stage", transition.targetStage());
                String fromStage = c.record().durableValues().get("quest_transition_stage");
                if (fromStage == null) {
                    if (progress == null) return SceneStepResult.fail(
                            "quest_transition_inactive", "quest transition requires an active quest");
                    fromStage = progress.currentStage();
                    c.record().putDurableValue("quest_transition_stage", fromStage);
                    c.repository().changed();
                }
                action.addProperty("from_stage", fromStage);
                action.addProperty("scene_path", c.definition().id().toString());
                action.addProperty("response_id", c.instance().id() + "/" + c.step().id());
            } else {
                action.addProperty("type", "quest");
                action.addProperty(
                        "action",
                        switch (transition.target()) {
                            case COMPLETE -> "turn_in";
                            case FAIL -> "fail";
                            case ABANDON -> "abandon";
                            case STAGE -> throw new IllegalStateException();
                        });
            }
            JsonObject wrapper = new JsonObject();
            JsonArray array = new JsonArray();
            array.add(action);
            wrapper.add("actions", array);
            var parsed =
                    VillagerActionDefinition.readList(
                            c.definition().id(), "scene quest_transition", wrapper, null);
            if (parsed.size() != 1)
                return SceneStepResult.fail(
                        "quest_transition_invalid", "quest transition action is invalid");
            var guarded =
                    SceneReceiptGuard.applyActionOnce(
                            c,
                            "quest_transition",
                            SceneOperationReceipt.Kind.QUEST_TRANSITION,
                            () ->
                                    SceneLifecycleIntegration.withOriginatingScene(
                                            c.instance().id(),
                                            () ->
                                                    executeAction(c, dialogue, parsed.getFirst())),
                            playerReturned);
            if (guarded.ambiguous())
                return SceneStepResult.block(
                        "quest_transition_ambiguous",
                        "cannot prove quest transition outcome after reload");
            if (guarded.outcome().suspended()) {
                return SceneStepResult.waitUntil(c.gameTime() + 1L, "waiting for quest transition");
            }
            if (!guarded.outcome().success()) {
                return SceneStepResult.fail("quest_transition_failed", guarded.outcome().message());
            }
            if (playerReturned) c.record().putDurableValue("waiting_player_transition", "");
            return SceneStepResult.applied();
        }

        public SceneStepResult reconcile(SceneExecutionContext c) {
            return apply(c);
        }
    }

    private static final class Branch extends Base {
        Branch() {
            super(RecoveryMode.NATURALLY_IDEMPOTENT);
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            String persisted = c.record().durableValues().get("chosen_transition");
            if (persisted != null) return SceneStepResult.applied();
            DialogueRuntimeContext dialogue = context(c);
            if (dialogue == null)
                return actorUnavailable(c, "branch condition context is unavailable");
            String chosen = string(c, "default_transition", "");
            JsonElement raw = c.step().parameters().get("branches");
            if (raw != null && raw.isJsonArray())
                for (JsonElement value : raw.getAsJsonArray())
                    if (value.isJsonObject()) {
                        JsonObject branch = value.getAsJsonObject();
                        List<DialogueCondition> conditions =
                                DialogueCondition.readList(
                                        c.definition().id(), "scene branch", branch);
                        if (DialogueCondition.matchesAll(dialogue, conditions)) {
                            chosen = string(branch, "transition", "");
                            break;
                        }
                    }
            if (chosen.isBlank())
                return SceneStepResult.fail(
                        "branch_unmatched", "scene branch has no matching or default transition");
            c.record().putDurableValue("chosen_transition", chosen);
            c.record().chooseTransition(chosen);
            return SceneStepResult.applied();
        }

        public SceneStepResult verify(SceneExecutionContext c) {
            return SceneStepResult.complete(c.record().durableValues().get("chosen_transition"));
        }
    }

    private static final class Terminal extends Base {
        Terminal() {
            super(RecoveryMode.NATURALLY_IDEMPOTENT);
        }

        public SceneStepResult apply(SceneExecutionContext c) {
            return SceneStepResult.applied();
        }
    }

    private static SceneOperationReceipt.Kind receiptKind(VillagerActionDefinition action) {
        return switch (action.kind()) {
            case EXPERIENCE -> SceneOperationReceipt.Kind.EXPERIENCE_GRANT;
            case REPUTATION -> SceneOperationReceipt.Kind.REPUTATION_CHANGE;
            case GOSSIP -> SceneOperationReceipt.Kind.GOSSIP_CHANGE;
            case COUNTER -> SceneOperationReceipt.Kind.COUNTER_INCREMENT;
            case QUEST, QUEST_TRANSITION -> SceneOperationReceipt.Kind.QUEST_TRANSITION;
            case LOOT -> SceneOperationReceipt.Kind.LOOT_GRANT;
            default -> SceneOperationReceipt.Kind.ITEM_GRANT;
        };
    }

    private static DialogueRuntimeContext context(SceneExecutionContext c) {
        if (!c.instance().conversationEffect().isEmpty()) {
            return conversationContext(c, null);
        }
        if (!c.instance().campaignConsent().isEmpty()) {
            return com.jvn.villagerretaliation.campaign.CampaignSceneApproval.resolve(c.server(), c.instance()).context();
        }
        ServerPlayer player = null;
        var decision = c.instance().decisionParticipants();
        if (!decision.isEmpty()) {
            if (!com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.issue(decision).isBlank()) return null;
            player = c.server().getPlayerList().getPlayer(com.jvn.villagerretaliation.campaign.CampaignDecisionParticipants.load(decision).player());
        } else {
            for (UUID id : c.instance().participants()) {
                player = c.server().getPlayerList().getPlayer(id);
                if (player != null) break;
            }
            if (player == null && c.instance().owner().playerId() != null)
                player = c.server().getPlayerList().getPlayer(c.instance().owner().playerId());
        }
        LivingEntity provider = firstActor(c);
        return player == null || provider == null || !(provider instanceof Villager) && provider.level() != player.serverLevel()
                ? null
                : ConversationActors.context(player, provider);
    }

    private static DialogueRuntimeContext conversationContext(SceneExecutionContext c, VillagerActionDefinition action) {
        return com.jvn.villagerretaliation.dialogue.conversation.ConversationSceneExecution.runtimeContext(c.server(), c.instance(), action,
                c.instance().campaignConsent().isEmpty() ? firstActor(c) : null);
    }

    private static LivingEntity firstActor(SceneExecutionContext c) {
        for (var declaration : c.definition().actors().values()) {
            if (declaration.bindingSource() != SceneActorDeclaration.BindingSource.QUEST_PROVIDER) continue;
            var binding = c.instance().actorBindings().get(declaration.alias());
            if (binding != null && !binding.sourceType().equals(VillagerRetaliation.id("villager")))
                return find(c, binding) instanceof LivingEntity living && ConversationActors.supports(living) ? living : null;
        }
        for (SceneActorBinding binding : c.instance().actorBindings().values()) {
            if (find(c, binding) instanceof Villager villager) return villager;
        }
        for (SceneActorBinding binding : c.instance().actorBindings().values()) {
            if (find(c, binding) instanceof LivingEntity living && ConversationActors.supports(living)) return living;
        }
        return null;
    }

    private static Entity actor(SceneExecutionContext c, String alias) {
        SceneActorBinding binding = c.instance().actorBindings().get(alias);
        return binding == null ? null : find(c, binding);
    }

    private static Entity find(SceneExecutionContext c, SceneActorBinding binding) {
        if (binding.entityId() == null || binding.state() == SceneActorBinding.BindingState.DOWNED)
            return null;
        if (binding.lastDimension() != null) {
            ServerLevel level =
                    c.server()
                            .getLevel(
                                    ResourceKey.create(
                                            net.minecraft.core.registries.Registries.DIMENSION,
                                            binding.lastDimension()));
            if (level != null) return level.getEntity(binding.entityId());
        }
        for (ServerLevel level : c.server().getAllLevels()) {
            Entity value = level.getEntity(binding.entityId());
            if (value != null) return value;
        }
        return null;
    }

    private static SceneStepResult actorUnavailable(SceneExecutionContext c, String message) {
        return actorUnavailable(c, sourceAlias(c), message);
    }

    private static SceneStepResult actorUnavailable(
            SceneExecutionContext c, String alias, String message) {
        SceneActorBinding binding = c.instance().actorBindings().get(alias);
        if (binding != null && binding.state() == SceneActorBinding.BindingState.DOWNED)
            return SceneStepResult.block(
                    "actor_downed", "scene actor " + alias + " is incapacitated");
        SceneActorDeclaration declaration = c.definition().actors().get(alias);
        if (declaration == null) return SceneStepResult.block("actor_missing", message);
        return switch (declaration.missingActorPolicy()) {
            case SKIP -> SceneStepResult.skip();
            case FAIL -> SceneStepResult.fail("actor_missing", message);
            case BLOCK -> SceneStepResult.block("actor_missing", message);
            case WAIT_UNTIL_TIMEOUT -> {
                String key = "actor_deadline/" + alias;
                long timeout = declaration.timeoutTicks();
                long deadline = durableLong(c, key, 0);
                if (deadline == 0) {
                    deadline = c.gameTime() + timeout;
                    c.record().putDurableValue(key, Long.toString(deadline));
                }
                yield timeout > 0 && c.gameTime() >= deadline
                        ? SceneStepResult.fail("actor_timeout", message)
                        : SceneStepResult.waitUntil(c.gameTime() + 20, message);
            }
        };
    }

    private static String sourceAlias(SceneExecutionContext c) {
        String authored = c.step().follow() == null ? string(c, "actor", "") : c.step().follow().actor();
        return authored.isBlank()
                ? (c.step().actors().isEmpty() ? "" : c.step().actors().getFirst())
                : authored;
    }

    private static Destination destination(SceneExecutionContext c) {
        String target = c.step().follow() == null ? string(c, "target_actor", "") : c.step().follow().targetActor();
        if (!target.isBlank()) {
            SceneActorBinding binding = c.instance().actorBindings().get(target);
            if (binding != null
                    && binding.lastDimension() != null
                    && binding.lastPosition() != null)
                return new Destination(binding.lastDimension(), binding.lastPosition());
            Entity entity = actor(c, target);
            if (entity != null)
                return new Destination(
                        entity.level().dimension().location(), entity.blockPosition());
        }
        if (c.step().parameters().has("x")
                && c.step().parameters().has("y")
                && c.step().parameters().has("z")) {
            ResourceLocation dimension =
                    ResourceLocation.tryParse(string(c, "dimension", "minecraft:overworld"));
            return dimension == null
                    ? null
                    : new Destination(
                            dimension,
                            new BlockPos(
                                    (int) longValue(c, "x", 0),
                                    (int) longValue(c, "y", 0),
                                    (int) longValue(c, "z", 0)));
        }
        return null;
    }

    private static Destination durableDestination(SceneExecutionContext c) {
        ResourceLocation d =
                ResourceLocation.tryParse(
                        c.record().durableValues().getOrDefault("destination_dimension", ""));
        if (d == null) return null;
        try {
            return new Destination(
                    d,
                    new BlockPos(
                            Integer.parseInt(c.record().durableValues().get("destination_x")),
                            Integer.parseInt(c.record().durableValues().get("destination_y")),
                            Integer.parseInt(c.record().durableValues().get("destination_z"))));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String string(SceneExecutionContext c, String key, String fallback) {
        return string(c.step().parameters(), key, fallback);
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static long longValue(SceneExecutionContext c, String key, long fallback) {
        try {
            return c.step().parameters().has(key)
                    ? c.step().parameters().get(key).getAsLong()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double doubleValue(SceneExecutionContext c, String key, double fallback) {
        try {
            return c.step().parameters().has(key)
                    ? c.step().parameters().get(key).getAsDouble()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean bool(SceneExecutionContext c, String key, boolean fallback) {
        try {
            return c.step().parameters().has(key)
                    ? c.step().parameters().get(key).getAsBoolean()
                    : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static long durableLong(SceneExecutionContext c, String key, long fallback) {
        try {
            return Long.parseLong(
                    c.record().durableValues().getOrDefault(key, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record Destination(ResourceLocation dimension, BlockPos pos) {}
}
