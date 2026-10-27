package com.jvn.villagerretaliation.quest;

import com.jvn.villagerretaliation.party.PartyRecord;
import com.jvn.villagerretaliation.party.PartyService;
import com.jvn.villagerretaliation.party.PartySharedQuestRecord;
import com.jvn.villagerretaliation.quest.inventory.QuestItemAllocation;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public final class PartyQuestService {
    private PartyQuestService() {
    }

    public static boolean isShareable(QuestDefinition definition) {
        if (definition == null || definition.campaign() != null || (definition.tags().contains("clipboard_tutorial") || definition.tags().contains("group.clipboard"))) {
            return false;
        }
        boolean supported = false;
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.optional()) {
                continue;
            }
            boolean siteCriterion = definition.target().site() != null
                    && objective.type() == QuestDefinition.ObjectiveType.CRITERION && objective.criterion() != null
                    && objective.criterion().getNamespace().equals("villagerretaliation")
                    && java.util.Set.of("site_discovered", "captive_freed", "captive_rescued").contains(objective.criterion().getPath());
            if (siteCriterion) {
                supported = true;
                continue;
            }
            if (objective.type() != QuestDefinition.ObjectiveType.MOB_KILL
                    && objective.type() != QuestDefinition.ObjectiveType.ITEM_CHECK) {
                return false;
            }
            supported = true;
        }
        return supported;
    }

    public static PartySharedQuestRecord getOrCreate(
            ServerLevel level,
            PartyRecord party,
            QuestDefinition definition,
            UUID sourceVillagerId) {
        Optional<PartySharedQuestRecord> existing = findCompatible(
                party,
                definition,
                sourceVillagerId);
        if (existing.isPresent()) {
            return existing.get();
        }
        PartySharedQuestRecord created = new PartySharedQuestRecord(
                definition.id(),
                sourceVillagerId,
                level.getServer().overworld().getGameTime());
        party.addSharedQuest(created);
        PartyService.markChanged(level);
        return created;
    }

    public static Optional<PartySharedQuestRecord> findCompatible(
            PartyRecord party,
            QuestDefinition definition,
            UUID sourceVillagerId) {
        if (party == null || definition == null) {
            return Optional.empty();
        }
        return party.sharedQuests().stream()
                .filter(shared -> !shared.completed()
                        && shared.questId().equals(definition.id())
                        && (definition.rules().crossVillagerCompatible()
                        || java.util.Objects.equals(shared.sourceVillagerId(), sourceVillagerId)))
                .findFirst();
    }

    public static Optional<PartySharedQuestRecord> sharedForPlayer(
            ServerLevel level,
            UUID playerId,
            net.minecraft.resources.ResourceLocation questId) {
        PartyRecord party = PartyService.getPartyForPlayer(level, playerId).orElse(null);
        if (party == null) {
            return Optional.empty();
        }
        VillagerQuestSavedData.QuestProgress progress =
                VillagerQuestSavedData.get(level).get(playerId, questId);
        UUID instanceId = progress == null ? null : progress.partyQuestInstanceId();
        if (instanceId != null) {
            return party.sharedQuests().stream()
                    .filter(shared -> !shared.completed()
                            && shared.instanceId().equals(instanceId)
                            && shared.questId().equals(questId)
                            && shared.linked(playerId))
                    .findFirst();
        }
        return party.sharedQuests().stream()
                .filter(shared -> !shared.completed()
                        && shared.questId().equals(questId)
                        && shared.linked(playerId))
                .findFirst();
    }

    public static void mergePersonalProgress(
            PartySharedQuestRecord shared,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.rules().distinctVillagers() || objective.rules().live()) continue;
            shared.mergeObjectiveCounter(objective.id(), progress.objectiveCounter(objective.id()));
            if (progress.objectiveComplete(objective.id())) {
                shared.markObjectiveComplete(objective.id());
            }
        }
    }

    public static void syncPersonalProgress(
            PartySharedQuestRecord shared,
            QuestDefinition definition,
            VillagerQuestSavedData.QuestProgress progress) {
        if (!definition.rewards().performance().measurements().isEmpty()) {
            if (shared.performance() == null) shared.performance(progress.performance());
            progress.performance(shared.performance());
        }
        for (QuestDefinition.Objective objective : definition.objectives()) {
            if (objective.rules().distinctVillagers() || objective.rules().live()) continue;
            int delta = shared.objectiveCounter(objective.id()) - progress.objectiveCounter(objective.id());
            if (delta > 0) {
                progress.addObjectiveCounter(objective.id(), delta);
            }
            if (shared.objectiveComplete(objective.id())) {
                progress.markObjectiveComplete(objective.id());
            }
        }
    }

    public static void detachPlayer(ServerLevel level, PartyRecord party, UUID playerId) {
        if (level == null || party == null || playerId == null) {
            return;
        }
        boolean changed = false;
        List<UUID> emptyInstances = new ArrayList<>();
        VillagerQuestSavedData questData = VillagerQuestSavedData.get(level);
        for (PartySharedQuestRecord shared : party.sharedQuests()) {
            PartySharedQuestRecord.Enrollment enrollment = shared.enrollment(playerId);
            if (enrollment == null) {
                continue;
            }
            VillagerQuestSavedData.QuestProgress progress = questData.get(playerId, shared.questId());
            if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
                QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), shared.questId()).orElse(null);
                if (definition != null) {
                    syncPersonalProgress(shared, definition, progress);
                    questData.setDirty();
                }
            }
            if (progress != null && shared.performance() != null)
                progress.performance(com.jvn.villagerretaliation.quest.runtime.QuestPerformanceState.load(shared.performance().save()));
            shared.removeEnrollment(playerId);
            changed = true;
            if (shared.enrollments().isEmpty() || shared.settled()) {
                emptyInstances.add(shared.instanceId());
            }
        }
        emptyInstances.forEach(party::removeSharedQuest);
        if (changed) {
            PartyService.markChanged(level);
        }
    }

    public static void detachAll(ServerLevel level, PartyRecord party) {
        if (party == null) {
            return;
        }
        for (UUID playerId : List.copyOf(party.playerIds())) {
            detachPlayer(level, party, playerId);
        }
    }

    public static void detachQuest(
            ServerLevel level,
            PartyRecord party,
            UUID playerId,
            net.minecraft.resources.ResourceLocation questId) {
        if (level == null || party == null || playerId == null || questId == null) {
            return;
        }
        VillagerQuestSavedData questData = VillagerQuestSavedData.get(level);
        VillagerQuestSavedData.QuestProgress progress = questData.get(playerId, questId);
        UUID instanceId = progress == null ? null : progress.partyQuestInstanceId();
        for (PartySharedQuestRecord shared : List.copyOf(party.sharedQuests())) {
            if (!shared.questId().equals(questId)
                    || instanceId != null && !shared.instanceId().equals(instanceId)
                    || shared.enrollment(playerId) == null) {
                continue;
            }
            if (progress != null && progress.state() == VillagerQuestSavedData.QuestState.ACTIVE) {
                QuestDefinition definition = VillagerQuestResources.quest(level.getServer(), questId).orElse(null);
                if (definition != null) {
                    syncPersonalProgress(shared, definition, progress);
                    questData.setDirty();
                }
            }
            if (progress != null && shared.performance() != null)
                progress.performance(com.jvn.villagerretaliation.quest.runtime.QuestPerformanceState.load(shared.performance().save()));
            shared.removeEnrollment(playerId);
            if (shared.enrollments().isEmpty() || shared.settled()) {
                party.removeSharedQuest(shared.instanceId());
            }
            PartyService.markChanged(level);
            return;
        }
    }

    public static Optional<SubmissionPlan> planSharedItemSubmission(
            ServerPlayer submitter,
            PartySharedQuestRecord shared,
            List<QuestDefinition.Objective> objectives,
            BiPredicate<QuestDefinition.Objective, ItemStack> matcher) {
        if (submitter == null || shared == null || objectives.isEmpty() || matcher == null) {
            return Optional.empty();
        }
        ServerLevel level = submitter.serverLevel();
        PartyRecord party = PartyService.getPartyForPlayer(level, submitter.getUUID()).orElse(null);
        if (party == null || !shared.linked(submitter.getUUID())) {
            return Optional.empty();
        }

        List<StackSource> sources = itemSources(submitter, party, shared);
        var allocation = QuestItemAllocation.plan(objectives, sources.stream()
                .map(source -> new QuestItemAllocation.Supply(source.stack(), source.permittedCount())).toList(), matcher);
        if (allocation.isEmpty()) return Optional.empty();
        List<Removal> removals = new ArrayList<>();
        List<ItemStack> submitted = new ArrayList<>();
        for (var assigned : allocation.get()) {
            var source = sources.get(assigned.source());
            removals.add(new Removal(source, assigned.objective(), assigned.count()));
            submitted.add(source.stack().copyWithCount(assigned.count()));
        }
        return Optional.of(new SubmissionPlan(List.copyOf(removals), List.copyOf(submitted), matcher));
    }

    private static List<StackSource> itemSources(ServerPlayer submitter, PartyRecord party, PartySharedQuestRecord shared) {
        List<StackSource> sources = new ArrayList<>();
        var inventory = submitter.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (slot >= inventory.items.size() && slot < inventory.items.size() + inventory.armor.size()) continue;
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) sources.add(new StackSource(stack, inventory, slot, stack.copy(), stack.getCount(),
                    () -> submitter.isAlive() && shared.linked(submitter.getUUID())
                            && PartyService.getPartyForPlayer(submitter.serverLevel(), submitter.getUUID()).map(partyRecord -> partyRecord.id().equals(party.id())).orElse(false),
                    () -> { }, "Your inventory slot " + slot));
        }
        for (var contribution : QuestContributionService.contributions(submitter, party, shared)) {
            sources.add(new StackSource(contribution.stack(), contribution.inventory(), contribution.slot(), contribution.stack().copy(),
                    contribution.count(), contribution.valid(), contribution.consumed(), contribution.description()));
        }
        return sources;
    }

    public static final class SubmissionPlan {
        private final List<Removal> removals;
        private final List<ItemStack> submittedStacks;
        private final BiPredicate<QuestDefinition.Objective, ItemStack> matcher;
        private boolean consumed;

        private SubmissionPlan(
                List<Removal> removals,
                List<ItemStack> submittedStacks,
                BiPredicate<QuestDefinition.Objective, ItemStack> matcher) {
            this.removals = removals;
            this.submittedStacks = submittedStacks;
            this.matcher = matcher;
        }

        public List<ItemStack> submittedStacks() {
            return this.submittedStacks;
        }

        public List<String> preview() {
            return this.removals.stream().map(removal -> removal.count() + " x " + removal.source().stack().getHoverName().getString()
                    + " from " + removal.source().description()).toList();
        }

        public boolean remove() {
            if (this.consumed) return false;
            IdentityHashMap<ItemStack, Integer> totals = new IdentityHashMap<>();
            for (Removal removal : this.removals) {
                var source = removal.source();
                if (!source.valid().getAsBoolean() || source.container().getItem(source.slot()) != source.stack()
                        || !ItemStack.isSameItemSameComponents(source.snapshot(), source.stack())
                        || !this.matcher.test(removal.objective(), source.stack())) {
                    return false;
                }
                totals.merge(removal.source().stack(), removal.count(), Integer::sum);
            }
            for (Map.Entry<ItemStack, Integer> entry : totals.entrySet()) {
                if (entry.getKey().getCount() < entry.getValue()) {
                    return false;
                }
            }
            this.consumed = true;
            Map<Container, Boolean> changedContainers = new IdentityHashMap<>();
            for (Map.Entry<ItemStack, Integer> entry : totals.entrySet()) {
                entry.getKey().shrink(entry.getValue());
            }
            for (Removal removal : this.removals) {
                changedContainers.put(removal.source().container(), Boolean.TRUE);
            }
            changedContainers.keySet().forEach(Container::setChanged);
            this.removals.stream().map(Removal::source).distinct().forEach(source -> source.consumed().run());
            return true;
        }
    }

    private record StackSource(ItemStack stack, Container container, int slot, ItemStack snapshot, int permittedCount,
            java.util.function.BooleanSupplier valid, Runnable consumed, String description) {
    }

    private record Removal(StackSource source, QuestDefinition.Objective objective, int count) {
    }
}
