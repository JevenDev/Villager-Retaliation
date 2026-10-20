package com.jvn.villagerretaliation.quest.follower;

import com.jvn.villagerretaliation.action.*;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.dialogue.DialogueRuntimeContext;
import com.jvn.villagerretaliation.quest.*;
import com.jvn.villagerretaliation.scene.actor.SceneActorDeclaration;
import com.jvn.villagerretaliation.scene.persistence.SceneSavedData;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class QuestFollowerService {
    private static final Set<Mob> GOALS = Collections.newSetFromMap(new WeakHashMap<>());
    private QuestFollowerService() { }

    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        return execute(context, action, (actor, player) -> {
            if (actor instanceof net.minecraft.world.entity.npc.Villager villager) {
                var owner = com.jvn.villagerretaliation.interaction.VillagerAssignmentStore.commandOwner(villager).orElse(null);
                if (owner != null && !owner.equals(player.getUUID())) return "This villager is assigned to another player.";
            }
            return "";
        });
    }

    public static ActionResult execute(DialogueRuntimeContext context, VillagerActionDefinition action, QuestFollowerPolicy policy) {
        Objects.requireNonNull(policy);
        if (context == null || action.follower() == null || action.questId() == null) return blocked("Follower action requires an active quest.");
        var data = QuestFollowerSavedData.get(context.level());
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), action.questId());
        if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) return blocked("Follower quest is not active.");
        var directive = action.follower();
        var previous = data.entries.values().stream().filter(value -> value.quest.equals(action.questId())
                && value.run.equals(progress.questRunId()) && value.directive.actor().alias().equals(directive.actor().alias())).findFirst().orElse(null);
        if (directive.command().equals("release")) {
            if (previous != null) release(context.level(), previous.actor);
            return ActionResult.success(VillagerActionResult.success(), Set.of());
        }
        var entity = previous == null ? resolve(context, action.questId(), progress, directive.actor())
                : find(context.level().getServer(), previous.actor);
        if (!(entity instanceof PathfinderMob mob) || !entity.isAlive()) {
            if (!directive.actor().required() && directive.actor().missingActorPolicy() == SceneActorDeclaration.MissingActorPolicy.SKIP)
                return ActionResult.success(VillagerActionResult.success(), Set.of());
            return blocked("Follower actor is missing or has no path navigation.");
        }
        if (directive.actor().actorType().getPath().equals("villager") && !(mob instanceof net.minecraft.world.entity.npc.Villager))
            return blocked("Follower actor is not a villager.");
        if (!directive.actor().requiredCapabilities().stream().allMatch(id -> com.jvn.villagerretaliation.api.VillagerRetaliationRegistries.ACTOR_TYPES.get(directive.actor().actorType())
                .map(type -> type.liveCapabilities().contains(id)).orElse(false)))
            return blocked("Follower actor does not support the requested capabilities.");
        if (directive.actor().filters().containsKey("entity_type") && !directive.actor().filters().get("entity_type")
                .equals(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()))
            return blocked("Follower actor does not match its entity type filter.");
        if (!mayControlPet(mob, context.player())) return blocked("This pet belongs to another player.");
        String policyIssue = policy.issue(mob, context.player());
        if (policyIssue != null && !policyIssue.isBlank()) return blocked(policyIssue);
        var existing = data.entries.get(mob.getUUID());
        if (existing != null && (!existing.quest.equals(action.questId()) || !existing.run.equals(progress.questRunId())))
            return blocked("Follower actor is controlled by another quest.");
        var entry = new QuestFollowerSavedData.Entry(context.player().getUUID(), action.questId(), progress.questRunId(),
                mob.getUUID(), previous == null ? context.player().getUUID() : previous.target, directive,
                directive.stageScoped() ? progress.currentStage() : "", directive.command().equals("wait") ? "wait" : "follow",
                mob.blockPosition(), mob.level().dimension().location().toString());
        data.entries.put(mob.getUUID(), entry);
        data.setDirty();
        install(mob);
        mob.getNavigation().stop();
        return ActionResult.success(VillagerActionResult.success(), Set.of());
    }

    private static ActionResult blocked(String message) {
        return ActionResult.unavailable(message, Set.of(), VillagerActionResult.EMPTY);
    }

    private static Entity resolve(DialogueRuntimeContext context, ResourceLocation quest, VillagerQuestSavedData.QuestProgress progress,
            SceneActorDeclaration actor) {
        var server = context.level().getServer();
        return switch (actor.bindingSource()) {
            case QUEST_PROVIDER -> context.speaker();
            case UUID -> find(server, UUID.fromString(actor.bindingReference()));
            case CAMPAIGN_ROLE -> {
                var definition = VillagerQuestResources.quest(server, quest).orElse(null);
                yield definition == null || definition.campaign() == null ? null
                        : com.jvn.villagerretaliation.campaign.CampaignCharacterService.resolve(server, definition.campaign(), actor.bindingReference()).entity();
            }
            case ENTITY_TAG -> {
                var matches = context.level().getEntitiesOfClass(PathfinderMob.class, context.player().getBoundingBox().inflate(64),
                        mob -> mob.getTags().contains(actor.bindingReference())
                                && (!actor.actorType().getPath().equals("villager") || mob instanceof net.minecraft.world.entity.npc.Villager)
                                && (!actor.filters().containsKey("entity_type") || actor.filters().get("entity_type").equals(
                                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString())));
                yield matches.size() == 1 ? matches.getFirst() : null;
            }
            case OWNED_SPAWN, ENCOUNTER -> {
                Entity result = null;
                for (var scene : SceneSavedData.get(context.level()).all()) {
                    if (!Objects.equals(scene.owningQuestInstance(), progress.questRunId())) continue;
                    var binding = scene.actorBindings().get(actor.bindingReference());
                    if (binding != null && binding.entityId() != null) {
                        var candidate = find(server, binding.entityId());
                        if (candidate != null && result != null && !candidate.getUUID().equals(result.getUUID())) { result = null; break; }
                        if (candidate != null) result = candidate;
                    }
                }
                yield result;
            }
            default -> null;
        };
    }

    private static Entity find(MinecraftServer server, UUID id) {
        if (id == null) return null;
        for (var level : server.getAllLevels()) {
            var entity = level.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }

    private static boolean valid(ServerLevel level, QuestFollowerSavedData.Entry entry) {
        var quest = VillagerQuestResources.quest(level.getServer(), entry.quest).orElse(null);
        var progress = VillagerQuestSavedData.get(level).get(entry.player, entry.quest);
        return quest != null && progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE
                && entry.run.equals(progress.questRunId()) && (entry.stage.isEmpty() || entry.stage.equals(progress.currentStage()));
    }

    public static boolean protects(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        var entry = QuestFollowerSavedData.get(level).entries.get(entity.getUUID());
        return entry != null && valid(level, entry) && entry.directive.data().has("protected")
                && entry.directive.data().get("protected").getAsBoolean();
    }

    public static void maintain(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        var level = server.overworld();
        var data = QuestFollowerSavedData.get(level);
        for (var entry : List.copyOf(data.entries.values())) {
            if (VillagerQuestResources.quest(server, entry.quest).isEmpty()) continue;
            if (!valid(level, entry)) { release(level, entry.actor); continue; }
            var actor = find(server, entry.actor);
            if (actor != null && !actor.isAlive()) entry.dead = true;
            if (actor != null && actor.isAlive()) {
                if (entry.missingSince >= 0 || entry.dead) { entry.missingSince = -1; entry.dead = false; data.setDirty(); }
                continue;
            }
            var player = server.getPlayerList().getPlayer(entry.player);
            if (player == null) continue;
            if (entry.missingSince < 0) { entry.missingSince = level.getGameTime(); data.setDirty(); }
            var declaration = entry.directive.actor();
            boolean timeout = declaration.timeoutTicks() > 0 && level.getGameTime() - entry.missingSince >= declaration.timeoutTicks();
            if (!entry.dead && !timeout) continue;
            boolean fail = entry.dead && declaration.deathPolicy() == SceneActorDeclaration.DeathPolicy.FAIL
                    || declaration.deathPolicy() == SceneActorDeclaration.DeathPolicy.APPLY_MISSING_POLICY
                    && declaration.missingActorPolicy() == SceneActorDeclaration.MissingActorPolicy.FAIL
                    || timeout && declaration.missingActorPolicy() == SceneActorDeclaration.MissingActorPolicy.WAIT_UNTIL_TIMEOUT;
            if (fail) {
                var progress = VillagerQuestSavedData.get(level).get(entry.player, entry.quest);
                var provider = progress == null ? null : find(server, progress.startedVillagerId());
                var context = provider instanceof net.minecraft.world.entity.npc.Villager villager
                        ? com.jvn.villagerretaliation.interaction.VillagerInteractionService.createDialogueContext(
                                (ServerLevel) villager.level(), player, villager) : DialogueContext.forPlayer(player);
                VillagerQuestService.performAction(context, entry.quest, VillagerActionDefinition.QuestAction.FAIL);
            } else if (declaration.missingActorPolicy() == SceneActorDeclaration.MissingActorPolicy.SKIP
                    && declaration.deathPolicy() == SceneActorDeclaration.DeathPolicy.APPLY_MISSING_POLICY) release(level, entry.actor);
        }
    }

    public static void died(LivingEntity actor) {
        if (!(actor.level() instanceof ServerLevel level)) return;
        var data = QuestFollowerSavedData.get(level);
        var entry = data.entries.get(actor.getUUID());
        if (entry != null) { entry.dead = true; data.setDirty(); }
    }

    public static boolean retained(Mob mob) {
        return mob.level() instanceof ServerLevel level && QuestFollowerSavedData.get(level).entries.containsKey(mob.getUUID());
    }

    public static boolean controls(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        var entry = QuestFollowerSavedData.get(level).entries.get(mob.getUUID());
        return entry != null && valid(level, entry) && !com.jvn.villagerretaliation.interaction.NarrativeActorReservations.scene(level, mob.getUUID());
    }

    public static void tickMob(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        var data = QuestFollowerSavedData.get(level);
        var entry = data.entries.get(mob.getUUID());
        if (entry == null) return;
        if (VillagerQuestResources.quest(level.getServer(), entry.quest).isEmpty()) { mob.getNavigation().stop(); return; }
        if (!valid(level, entry)) { release(level, mob.getUUID()); return; }
        if (com.jvn.villagerretaliation.interaction.NarrativeActorReservations.scene(level, mob.getUUID())) return;
        install(mob);
        if (!com.jvn.villagerretaliation.campaign.CampaignQuestAccess.executionIssue(level, entry.player,
                VillagerQuestResources.quest(level.getServer(), entry.quest).orElseThrow(),
                VillagerQuestSavedData.get(level).get(entry.player, entry.quest)).isBlank()) {
            mob.getNavigation().stop();
            return;
        }
        var target = level.getServer().getPlayerList().getPlayer(entry.target);
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (!entry.command.equals("wait") && (target == null || !target.isAlive() || target.isSpectator() || target.level() != level
                || mob.distanceToSqr(target) > entry.directive.maximumDistance() * entry.directive.maximumDistance()
                || !mayControlPet(mob, target))) {
            mob.getNavigation().stop();
            return;
        }
        if (entry.command.equals("wait") && !level.dimension().location().toString().equals(entry.dimension)) { mob.getNavigation().stop(); return; }
        BlockPos destination = entry.command.equals("wait") ? entry.anchor : target.blockPosition();
        double distance = entry.command.equals("wait") ? 1 : entry.directive.distance();
        if (mob.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination)) <= distance * distance) {
            mob.getNavigation().stop();
        } else if (mob.tickCount % 5 == 0) {
            boolean moving = mob.getNavigation().moveTo(destination.getX() + 0.5, destination.getY(),
                    destination.getZ() + 0.5, entry.directive.speed());
            String issue = moving ? "" : "Follower cannot find a path.";
            if (!entry.diagnostic.equals(issue)) { entry.diagnostic = issue; data.setDirty(); }
        }
    }

    private static void install(Mob mob) {
        if (!GOALS.add(mob)) return;
        mob.goalSelector.addGoal(-1, new Goal() {
            { setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
            @Override public boolean canUse() { return controls(mob); }
            @Override public boolean canContinueToUse() { return controls(mob); }
        });
    }

    public static void release(ServerLevel level, UUID actor) {
        var data = QuestFollowerSavedData.get(level);
        if (data.entries.remove(actor) == null) return;
        var entity = find(level.getServer(), actor);
        if (entity instanceof Mob mob) mob.getNavigation().stop();
        data.setDirty();
    }

    public static void terminal(ServerLevel level, UUID player, ResourceLocation quest) {
        var data = QuestFollowerSavedData.get(level);
        for (var entry : List.copyOf(data.entries.values()))
            if (entry.player.equals(player) && entry.quest.equals(quest)) release(level, entry.actor);
    }

    public static boolean command(ServerPlayer player, UUID actor, boolean follow) {
        var entity = player.serverLevel().getEntity(actor);
        if (!(entity instanceof Mob mob) || !mob.isAlive() || player.isSpectator() || player.distanceToSqr(mob) > 64) return false;
        var data = QuestFollowerSavedData.get(player.serverLevel());
        var entry = data.entries.get(actor);
        if (entry == null || !authorized(player, entry) || !mayControlPet(mob, player)) return false;
        entry.command = follow ? "follow" : "wait";
        if (follow) entry.target = player.getUUID();
        entry.anchor = mob.blockPosition();
        entry.dimension = mob.level().dimension().location().toString();
        mob.getNavigation().stop();
        data.setDirty();
        return true;
    }

    private static boolean authorized(ServerPlayer player, QuestFollowerSavedData.Entry entry) {
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), entry.quest);
        return progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE && entry.run.equals(progress.questRunId());
    }

    private static boolean mayControlPet(Mob mob, ServerPlayer player) {
        return !(mob instanceof TamableAnimal pet) || !pet.isTame() || player.getUUID().equals(pet.getOwnerUUID());
    }

    public static boolean interact(ServerPlayer player, Entity entity) {
        if (!(entity instanceof Mob) || player.distanceToSqr(entity) > 64) return false;
        var entry = QuestFollowerSavedData.get(player.serverLevel()).entries.get(entity.getUUID());
        if (entry == null || !authorized(player, entry) || !mayControlPet((Mob) entity, player)) return false;
        var follow = Component.translatable("villagerretaliation.quest.follower.follow").withStyle(style -> style
                .withColor(net.minecraft.ChatFormatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vr escort follow " + entity.getUUID())));
        var wait = Component.translatable("villagerretaliation.quest.follower.wait").withStyle(style -> style
                .withColor(net.minecraft.ChatFormatting.YELLOW).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vr escort wait " + entity.getUUID())));
        player.sendSystemMessage(Component.translatable("villagerretaliation.quest.follower.commands", entity.getDisplayName(), follow, wait));
        return true;
    }

    public static boolean arrived(ServerLevel level, UUID player, ResourceLocation quest, String alias, BlockPos target,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, double radius) {
        if (target == null) return false;
        for (var entry : QuestFollowerSavedData.get(level).entries.values()) {
            if (!entry.quest.equals(quest) || !entry.directive.actor().alias().equals(alias)) continue;
            var progress = VillagerQuestSavedData.get(level).get(player, quest);
            if (progress == null || !entry.run.equals(progress.questRunId())) continue;
            var mob = find(level.getServer(), entry.actor);
            return mob != null && mob.isAlive() && mob.level().dimension().equals(dimension == null ? level.dimension() : dimension)
                    && mob.blockPosition().distSqr(target) <= radius * radius;
        }
        return false;
    }
}
