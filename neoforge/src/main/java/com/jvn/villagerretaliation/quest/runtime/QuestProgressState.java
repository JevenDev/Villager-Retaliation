package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestTriggerContext;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.ChoiceHistoryEntry;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.CompletionHistoryEntry;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.ProviderRebindHistoryEntry;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.util.WorldLocation;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import static com.jvn.villagerretaliation.quest.VillagerQuestSavedData.MAX_CHOICE_HISTORY;
import static com.jvn.villagerretaliation.quest.VillagerQuestSavedData.MAX_COMPLETION_HISTORY;
import static com.jvn.villagerretaliation.quest.VillagerQuestSavedData.MAX_PROVIDER_REBIND_HISTORY;
import static com.jvn.villagerretaliation.quest.VillagerQuestSavedData.MAX_PENDING_TRIGGER_EVENTS;

public class QuestProgressState {
    protected QuestState state = QuestState.NOT_STARTED;
    protected UUID startedVillagerId;
    protected long startedGameTime = -1L;
    protected long completedGameTime = -1L;
    protected long abandonedGameTime = -1L;
    protected long expiredGameTime = -1L;
    protected long failedGameTime = -1L;
    protected String failureReason = "";
    protected boolean visitedTarget;
    protected boolean hasProof;
    protected boolean pendingPartyReward;
    protected boolean partyRewardClaimed;
    protected boolean completionPending;
    protected QuestRewardDelivery rewardDelivery;
    protected Tag preservedRewardDelivery;
    protected String rewardDeliveryIssue = "";
    protected UUID partyQuestInstanceId;
    protected UUID questRunId;
    protected String issuerName = "";
    protected String issuerProfession = "";
    protected int issuerLevel;
    protected ResourceKey<Level> issuerDimension;
    protected BlockPos issuerPos;
    protected String issuerVillageKey = "";
    protected ResourceKey<Level> targetDimension;
    protected BlockPos targetPos;
    protected String targetObjectiveId = "";
    protected String currentStage = "";
    protected final Map<String, QuestTimerState> timers = new LinkedHashMap<>();
    protected final Map<String, QuestParallelPath> parallelPaths = new LinkedHashMap<>();

    public Map<String, QuestParallelPath> parallelPaths() { return Map.copyOf(parallelPaths); }

    public boolean aliasCurrentStage(String stage) {
        if (stage == null || stage.isBlank() || stage.equals(currentStage)) return false;
        currentStage = stage;
        return true;
    }

    public Set<String> activeStages() {
        var stages = new LinkedHashSet<String>();
        stages.add(currentStage());
        parallelPaths.values().stream().filter(path -> path.phase() != QuestParallelPath.Phase.ARRIVED)
                .forEach(path -> stages.add(path.stage()));
        return java.util.Collections.unmodifiableSet(stages);
    }

    public boolean stageActive(String id) { return activeStages().contains(id); }

    public void remapParallelStages(Map<String, String> aliases) {
        Map<String, QuestParallelPath> remapped = new LinkedHashMap<>();
        parallelPaths.forEach((branch, path) -> remapped.put(aliases.getOrDefault(branch, branch),
                new QuestParallelPath(aliases.getOrDefault(path.stage(), path.stage()),
                        aliases.getOrDefault(path.target(), path.target()), path.phase())));
        parallelPaths.clear();
        parallelPaths.putAll(remapped);
        Map<String, QuestTimerState> renamedTimers = new LinkedHashMap<>();
        timers.forEach((key, timer) -> renamedTimers.put(key.startsWith("stage/")
                ? QuestTimers.stageKey(aliases.getOrDefault(key.substring("stage/".length()), key.substring("stage/".length()))) : key, timer));
        timers.clear();
        timers.putAll(renamedTimers);
    }

    public QuestTimerState timer(String key) { return timers.get(key); }
    public Map<String, Long> remainingTimers(QuestDefinition definition) {
        Map<String, Long> values = new LinkedHashMap<>();
        QuestTimers.active(definition, this).forEach(timer -> values.put(timer.key(), timer.state().remaining(timer.definition())));
        return Map.copyOf(values);
    }
    protected final Map<String, QuestActionCheckpoint> stageActions = new LinkedHashMap<>();
    protected String pendingStageTransition = "";
    protected String pendingStageEntry = "";
    protected final java.util.Set<String> completedObjectives = new java.util.HashSet<>();
    protected final Map<String, Integer> objectiveCounters = new HashMap<>();
    protected final Map<String, ResourceLocation> resolvedObjectiveItems = new HashMap<>();
    protected final Set<String> claimedBonuses = new LinkedHashSet<>();
    protected int definitionRevision;
    protected String campaignId = "";
    protected String campaignEntryStage = "";
    protected String campaignWaitingQuest = "";
    protected long campaignStageRevision;
    protected com.jvn.villagerretaliation.campaign.CampaignQuestAcceptance campaignAcceptance;
    protected boolean campaignLegacyEntry;
    protected final Set<UUID> campaignCatchUpOperations = new LinkedHashSet<>();
    protected String lastRevisionPolicy = "";
    protected long lastRevisionGameTime = -1L;
    protected int startCount;
    protected int completionCount;
    protected int abandonCount;
    protected String consumedReason = "";
    protected final Map<String, Long> triggerTimes = new HashMap<>();
    protected final List<ChoiceHistoryEntry> choiceHistory = new ArrayList<>();
    protected final List<CompletionHistoryEntry> completionHistory = new ArrayList<>();
    protected final List<ProviderRebindHistoryEntry> providerRebindHistory = new ArrayList<>();
    protected final Set<QuestDefinition.TriggerEvent> pendingLifecycleEvents = new LinkedHashSet<>();
    protected final List<QuestTriggerContext> pendingTriggerEvents = new ArrayList<>();

    protected static <T> void addBounded(List<T> history, T entry, int maximum) {
        if (entry == null || maximum <= 0) {
            return;
        }
        history.add(entry);
        while (history.size() > maximum) {
            history.removeFirst();
        }
    }

    public QuestState state() {
        return this.state;
    }

    public boolean terminal() {
        return switch (this.state) {
            case COMPLETED, FAILED, ABANDONED, EXPIRED, CONSUMED -> true;
            case NOT_STARTED, ACTIVE -> false;
        };
    }

    public long lastActivityGameTime() {
        return Math.max(
                Math.max(this.startedGameTime, this.completedGameTime),
                Math.max(
                        Math.max(this.abandonedGameTime, this.expiredGameTime),
                        this.failedGameTime));
    }

    public UUID startedVillagerId() {
        return this.startedVillagerId;
    }

    public long startedGameTime() {
        return this.startedGameTime;
    }

    public long completedGameTime() {
        return this.completedGameTime;
    }

    public long abandonedGameTime() {
        return this.abandonedGameTime;
    }

    public long expiredGameTime() {
        return this.expiredGameTime;
    }

    public long failedGameTime() {
        return this.failedGameTime;
    }

    public String failureReason() {
        return this.failureReason;
    }

    public boolean visitedTarget() {
        return this.visitedTarget;
    }

    public boolean hasProof() {
        return this.hasProof;
    }

    public boolean completionPending() { return this.completionPending; }

    public QuestRewardDelivery rewardDelivery() { return this.rewardDelivery; }

    public String rewardDeliveryIssue() {
        if (!this.rewardDeliveryIssue.isBlank()) return this.rewardDeliveryIssue;
        return this.completionPending && this.rewardDelivery == null ? "The saved completion has no reward delivery checkpoint." : "";
    }

    public void recordRewardDelivery(QuestRewardDelivery delivery) { this.rewardDelivery = delivery; }

    public void beginCompletion(QuestDefinition.Rewards rewards) {
        this.completionPending = true;
        if (this.rewardDelivery == null) this.rewardDelivery = new QuestRewardDelivery(rewards, 0);
    }

    public boolean pendingPartyReward() {
        return this.pendingPartyReward && !this.partyRewardClaimed;
    }

    public boolean partyRewardClaimed() {
        return this.partyRewardClaimed;
    }

    public void markPendingPartyReward() {
        if (!this.partyRewardClaimed) {
            this.pendingPartyReward = true;
        }
    }

    public void markPartyRewardClaimed() {
        this.pendingPartyReward = false;
        this.partyRewardClaimed = true;
    }

    public UUID partyQuestInstanceId() {
        return this.partyQuestInstanceId;
    }

    public UUID questRunId() {
        return this.questRunId;
    }

    public UUID beginRun(UUID playerId, ResourceLocation questId, UUID sharedRunId) {
        if (playerId == null || questId == null) {
            throw new IllegalArgumentException("quest run identity requires a player and quest id");
        }
        UUID definitiveRunId = sharedRunId == null
                ? deterministicRunId(playerId, questId, Math.max(1, this.startCount))
                : sharedRunId;
        if (this.questRunId != null && !this.questRunId.equals(definitiveRunId)) {
            throw new IllegalStateException("quest run identity cannot change during an active run");
        }
        this.questRunId = definitiveRunId;
        return this.questRunId;
    }

    public void ensureRunId(UUID playerId, ResourceLocation questId) {
        if (this.questRunId == null && this.state == QuestState.ACTIVE) {
            this.questRunId = this.partyQuestInstanceId != null
                    ? this.partyQuestInstanceId
                    : deterministicRunId(playerId, questId, Math.max(1, this.startCount));
        }
    }

    public static UUID deterministicRunId(UUID playerId, ResourceLocation questId, int runNumber) {
        if (playerId == null || questId == null) {
            throw new IllegalArgumentException("quest run identity requires a player and quest id");
        }
        return UUID.nameUUIDFromBytes((playerId + "|quest|" + questId + "|run|" + Math.max(1, runNumber))
                .getBytes(StandardCharsets.UTF_8));
    }

    public boolean linkPartyQuest(UUID instanceId) {
        if (instanceId != null && this.questRunId != null && !instanceId.equals(this.questRunId)) {
            return false;
        }
        this.partyQuestInstanceId = instanceId;
        if (instanceId != null && this.questRunId == null) this.questRunId = instanceId;
        return true;
    }

    public String issuerName() {
        return this.issuerName;
    }

    public String issuerProfession() {
        return this.issuerProfession;
    }

    public int issuerLevel() {
        return this.issuerLevel;
    }

    public ResourceKey<Level> issuerDimension() {
        return this.issuerDimension;
    }

    public BlockPos issuerPos() {
        return this.issuerPos;
    }

    public WorldLocation issuerLocation() {
        return WorldLocation.of(this.issuerDimension, this.issuerPos);
    }

    public String issuerVillageKey() {
        return this.issuerVillageKey;
    }

    public ResourceKey<Level> targetDimension() {
        return this.targetDimension;
    }

    public BlockPos targetPos() {
        return this.targetPos;
    }

    public WorldLocation targetLocation() {
        return WorldLocation.of(this.targetDimension, this.targetPos);
    }

    public String targetObjectiveId() {
        return this.targetObjectiveId;
    }

    public String currentStage() {
        if (!this.currentStage.isBlank()) {
            return this.currentStage;
        }
        return switch (this.state) {
            case ACTIVE -> "started";
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case ABANDONED -> "abandoned";
            case EXPIRED -> "expired";
            case CONSUMED -> "branch_lock".equals(this.consumedReason) ? "branch_locked" : "consumed";
            case NOT_STARTED -> "";
        };
    }

    public int startCount() {
        return this.startCount;
    }

    public int completionCount() {
        return this.completionCount;
    }

    public List<CompletionHistoryEntry> completionHistory() {
        return List.copyOf(this.completionHistory);
    }

    public List<ProviderRebindHistoryEntry> providerRebindHistory() {
        return List.copyOf(this.providerRebindHistory);
    }

    public Set<QuestDefinition.TriggerEvent> pendingLifecycleEvents() {
        return Set.copyOf(this.pendingLifecycleEvents);
    }

    public boolean hasPendingLifecycleEvents() {
        return !this.pendingLifecycleEvents.isEmpty();
    }

    public boolean deferLifecycleEvent(QuestDefinition.TriggerEvent event) {
        return isDeferredLifecycleEvent(event) && this.pendingLifecycleEvents.add(event);
    }

    public boolean resolveLifecycleEvent(QuestDefinition.TriggerEvent event) {
        return event != null && this.pendingLifecycleEvents.remove(event);
    }

    public List<QuestTriggerContext> pendingTriggerEvents() {
        return List.copyOf(this.pendingTriggerEvents);
    }

    public boolean hasPendingTriggerEvents() {
        return !this.pendingTriggerEvents.isEmpty();
    }

    public boolean deferTriggerEvent(QuestTriggerContext context) {
        if (context == null || context.event() == null) {
            return false;
        }
        addBounded(this.pendingTriggerEvents, context.withDialogueContext(null), MAX_PENDING_TRIGGER_EVENTS);
        return true;
    }

    public boolean resolveTriggerEvent(QuestTriggerContext context) {
        return context != null && this.pendingTriggerEvents.remove(context);
    }

    public int abandonCount() {
        return this.abandonCount;
    }

    public String consumedReason() {
        return this.consumedReason;
    }

    public void start(UUID villagerId, ResourceKey<Level> dimension, BlockPos pos, long gameTime) {
        this.state = QuestState.ACTIVE;
        this.startedVillagerId = villagerId;
        this.targetDimension = dimension;
        this.targetPos = pos == null ? null : pos.immutable();
        this.targetObjectiveId = "";
        this.startedGameTime = gameTime;
        this.completedGameTime = -1L;
        this.abandonedGameTime = -1L;
        this.expiredGameTime = -1L;
        this.failedGameTime = -1L;
        this.failureReason = "";
        this.visitedTarget = false;
        this.hasProof = false;
        this.pendingPartyReward = false;
        this.partyRewardClaimed = false;
        this.completionPending = false;
        this.rewardDelivery = null;
        this.preservedRewardDelivery = null;
        this.rewardDeliveryIssue = "";
        this.partyQuestInstanceId = null;
        this.questRunId = null;
        this.consumedReason = "";
        this.timers.clear();
        this.parallelPaths.clear();
        this.currentStage = "started";
        this.campaignEntryStage = "";
        this.campaignWaitingQuest = "";
        this.campaignStageRevision = 0L;
        this.campaignAcceptance = null;
        this.campaignLegacyEntry = false;
        this.campaignCatchUpOperations.clear();
        clearStageActions();
        this.issuerVillageKey = "";
        this.triggerTimes.clear();
        this.completedObjectives.clear();
        this.objectiveCounters.clear();
        this.resolvedObjectiveItems.clear();
        this.claimedBonuses.clear();
        this.lastRevisionPolicy = "";
        this.lastRevisionGameTime = -1L;
        this.choiceHistory.clear();
        this.pendingLifecycleEvents.clear();
        this.pendingTriggerEvents.clear();
        this.startCount = saturatingIncrement(this.startCount);
    }

    protected static boolean isDeferredLifecycleEvent(QuestDefinition.TriggerEvent event) {
        return event == QuestDefinition.TriggerEvent.STARTED
                || event == QuestDefinition.TriggerEvent.COMPLETED
                || event == QuestDefinition.TriggerEvent.FAILED
                || event == QuestDefinition.TriggerEvent.ABANDONED
                || event == QuestDefinition.TriggerEvent.EXPIRED;
    }

    public void setIssuer(
            UUID villagerId,
            String displayName,
            String professionId,
            int villagerLevel,
            ResourceKey<Level> dimension,
            BlockPos pos,
            String villageKey) {
        if (villagerId != null) {
            this.startedVillagerId = villagerId;
        }
        this.issuerName = displayName == null ? "" : displayName;
        this.issuerProfession = professionId == null ? "" : professionId;
        this.issuerLevel = Math.max(0, villagerLevel);
        this.issuerDimension = dimension;
        this.issuerPos = pos == null ? null : pos.immutable();
        this.issuerVillageKey = villageKey == null ? "" : villageKey;
    }

    public void rebindProvider(QuestProviderBinding binding, long gameTime, String reason) {
        if (binding == null || binding.providerId() == null) {
            throw new IllegalArgumentException("provider binding must have an id");
        }
        addBounded(this.providerRebindHistory, new ProviderRebindHistoryEntry(
                this.startedVillagerId,
                binding.providerId(),
                this.issuerName,
                this.issuerProfession,
                this.issuerLevel,
                this.issuerDimension,
                this.issuerPos,
                this.issuerVillageKey,
                gameTime,
                reason), MAX_PROVIDER_REBIND_HISTORY);
        setIssuer(
                binding.providerId(),
                binding.displayName(),
                binding.professionId() == null ? "" : binding.professionId().toString(),
                binding.level(),
                binding.dimension(),
                binding.pos(),
                binding.villageKey());
    }

    public boolean replaceIssuerVillageKey(String sourceKey, String targetKey) {
        if (!this.issuerVillageKey.equals(sourceKey)) {
            return false;
        }
        this.issuerVillageKey = targetKey;
        return true;
    }

    public boolean replaceVillagerIdentity(UUID sourceVillagerId, UUID targetVillagerId) {
        boolean changed = false;
        if (this.startedVillagerId != null && this.startedVillagerId.equals(sourceVillagerId)) {
            this.startedVillagerId = targetVillagerId;
            changed = true;
        }
        for (int index = 0; index < this.completionHistory.size(); index++) {
            CompletionHistoryEntry current = this.completionHistory.get(index);
            CompletionHistoryEntry updated = current.replacingVillagerIdentity(sourceVillagerId, targetVillagerId);
            if (updated != current) {
                this.completionHistory.set(index, updated);
                changed = true;
            }
        }
        return changed;
    }

    public void setTarget(UUID villagerId, ResourceKey<Level> dimension, BlockPos pos, String objectiveId) {
        this.startedVillagerId = villagerId;
        this.targetDimension = dimension;
        this.targetPos = pos == null ? null : pos.immutable();
        this.targetObjectiveId = objectiveId == null ? "" : objectiveId;
    }

    public void complete(long gameTime, boolean consume) {
        this.completionPending = false;
        this.state = consume ? QuestState.CONSUMED : QuestState.COMPLETED;
        this.completedGameTime = gameTime;
        this.completionCount = saturatingIncrement(this.completionCount);
        addBounded(this.completionHistory, new CompletionHistoryEntry(
                this.completionCount,
                this.startedVillagerId,
                this.startedGameTime,
                gameTime,
                this.issuerName,
                this.issuerProfession,
                this.issuerLevel,
                this.issuerDimension,
                this.issuerPos,
                this.issuerVillageKey), MAX_COMPLETION_HISTORY);
        this.consumedReason = consume ? "completion" : "";
        this.currentStage = "completed";
    }

    public void abandon(long gameTime, boolean consume) {
        this.state = consume ? QuestState.CONSUMED : QuestState.ABANDONED;
        this.abandonedGameTime = gameTime;
        this.abandonCount = saturatingIncrement(this.abandonCount);
        this.visitedTarget = false;
        this.hasProof = false;
        this.targetDimension = null;
        this.targetPos = null;
        this.targetObjectiveId = "";
        this.completedObjectives.clear();
        this.objectiveCounters.clear();
        this.consumedReason = consume ? "abandonment" : "";
        this.currentStage = "abandoned";
    }

    public void fail(long gameTime, String reason) {
        this.state = QuestState.FAILED;
        this.failedGameTime = gameTime;
        this.failureReason = QuestStateMachine.normalizeCode(reason, "unspecified_failure");
        this.visitedTarget = false;
        this.hasProof = false;
        this.targetDimension = null;
        this.targetPos = null;
        this.targetObjectiveId = "";
        this.completedObjectives.clear();
        this.objectiveCounters.clear();
        this.consumedReason = "";
        this.currentStage = "failed";
    }

    public void expire(long gameTime, boolean consume) {
        this.state = consume ? QuestState.CONSUMED : QuestState.EXPIRED;
        this.expiredGameTime = gameTime;
        this.visitedTarget = false;
        this.hasProof = false;
        this.targetDimension = null;
        this.targetPos = null;
        this.targetObjectiveId = "";
        this.completedObjectives.clear();
        this.objectiveCounters.clear();
        this.consumedReason = consume ? "expiration" : "";
        this.currentStage = "expired";
    }

    public void consume(String reason) {
        this.state = QuestState.CONSUMED;
        this.visitedTarget = false;
        this.hasProof = false;
        this.targetDimension = null;
        this.targetPos = null;
        this.targetObjectiveId = "";
        this.completedObjectives.clear();
        this.objectiveCounters.clear();
        this.consumedReason = reason == null ? "" : reason;
        this.currentStage = "branch_lock".equals(this.consumedReason) ? "branch_locked" : "consumed";
    }

    public Map<String, QuestActionCheckpoint> stageActions() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.stageActions));
    }

    public void recordStageAction(String key, QuestActionCheckpoint checkpoint) {
        this.stageActions.put(key, checkpoint);
    }

    public void removeStageAction(String key) {
        this.stageActions.remove(key);
    }

    public String pendingStageEntry() {
        return this.pendingStageEntry;
    }

    public void requestStageEntry(String stage) {
        this.pendingStageEntry = stage == null ? "" : stage.trim();
    }

    public String pendingStageTransition() {
        return this.pendingStageTransition;
    }

    public void requestStageTransition(String stage) {
        this.pendingStageTransition = stage == null ? "" : stage.trim();
    }

    private void clearStageActions() {
        this.stageActions.clear();
        this.pendingStageTransition = "";
        this.pendingStageEntry = "";
    }

    public boolean setCurrentStage(String stage) {
        String normalized = stage == null ? "" : stage.trim();
        if (this.currentStage.equals(normalized)) {
            return false;
        }
        Map<String, QuestActionCheckpoint> unfinished = new LinkedHashMap<>();
        this.stageActions.forEach((key, checkpoint) -> {
            if (!checkpoint.outcome().success()) {
                unfinished.put(key.startsWith("stage/") ? key : "stage/" + this.currentStage + "/" + key, checkpoint);
            }
        });
        this.parallelPaths.clear();
        this.currentStage = normalized;
        if (!this.campaignId.isBlank()) this.campaignStageRevision++;
        clearStageActions();
        this.stageActions.putAll(unfinished);
        return true;
    }

    public boolean markVisitedTarget() {
        if (this.visitedTarget) {
            return false;
        }
        this.visitedTarget = true;
        return true;
    }

    public boolean markHasProof() {
        if (this.hasProof) {
            return false;
        }
        this.hasProof = true;
        return true;
    }

    public boolean markObjectiveComplete(String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) {
            return false;
        }
        return this.completedObjectives.add(objectiveId);
    }

    public boolean objectiveComplete(String objectiveId) {
        return objectiveId != null && this.completedObjectives.contains(objectiveId);
    }

    public int objectiveCounter(String objectiveId) {
        if (objectiveId == null || objectiveId.isBlank()) {
            return 0;
        }
        return this.objectiveCounters.getOrDefault(objectiveId, 0);
    }

    public boolean claimBonus(String stageId, String bonusId) {
        if (stageId == null || stageId.isBlank() || bonusId == null || bonusId.isBlank()) {
            return false;
        }
        return this.claimedBonuses.add(stageId.trim() + "/" + bonusId.trim());
    }

    public boolean bonusClaimed(String stageId, String bonusId) {
        return stageId != null
                && bonusId != null
                && this.claimedBonuses.contains(stageId.trim() + "/" + bonusId.trim());
    }

    public Set<String> claimedBonuses() {
        return Set.copyOf(this.claimedBonuses);
    }

    public String campaignId() { return this.campaignId; }
    public String campaignEntryStage() { return this.campaignEntryStage; }
    public boolean campaignAlternateEntry() { return !this.campaignEntryStage.isBlank(); }
    public void campaignEntryStage(String stage) { this.campaignEntryStage = stage == null ? "" : stage; }

    public long campaignStageRevision() { return this.campaignStageRevision; }

    public com.jvn.villagerretaliation.campaign.CampaignQuestAcceptance campaignAcceptance() { return this.campaignAcceptance; }
    public void campaignAcceptance(com.jvn.villagerretaliation.campaign.CampaignQuestAcceptance acceptance) { this.campaignAcceptance = acceptance; }

    public String campaignWaitingQuest() { return this.campaignWaitingQuest; }
    public boolean campaignLegacyEntry() { return this.campaignLegacyEntry; }
    public void finishCampaignEntryMigration() { this.campaignLegacyEntry = false; }
    public boolean appliedCampaignCatchUp(UUID operation) { return this.campaignCatchUpOperations.contains(operation); }
    public void recordCampaignCatchUp(UUID operation) {
        if (operation != null) this.campaignCatchUpOperations.add(operation);
    }

    public void enterCampaignCatchUp(String stage, ResourceLocation waitingQuest, UUID operation) {
        clearStageActions();
        this.pendingLifecycleEvents.clear();
        this.pendingTriggerEvents.clear();
        this.completedObjectives.clear();
        this.objectiveCounters.clear();
        this.resolvedObjectiveItems.clear();
        this.targetDimension = null;
        this.targetPos = null;
        this.targetObjectiveId = "";
        this.visitedTarget = false;
        this.hasProof = false;
        this.campaignEntryStage = stage;
        this.campaignStageRevision++;
        this.campaignWaitingQuest = waitingQuest == null ? "" : waitingQuest.toString();
        if (waitingQuest == null) {
            this.currentStage = stage;
            requestStageEntry(stage);
        }
        recordCampaignCatchUp(operation);
    }

    public void bindCampaign(ResourceLocation id) {
        if (id == null) return;
        if (!this.campaignId.isBlank() && !this.campaignId.equals(id.toString())) {
            throw new IllegalStateException("quest progress already belongs to campaign " + this.campaignId);
        }
        this.campaignId = id.toString();
    }

    public int definitionRevision() {
        return this.definitionRevision;
    }

    public String lastRevisionPolicy() {
        return this.lastRevisionPolicy;
    }

    public long lastRevisionGameTime() {
        return this.lastRevisionGameTime;
    }

    public void adoptDefinitionRevision(int revision) {
        this.definitionRevision = Math.max(1, revision);
    }

    public void recordDefinitionMigration(int revision, QuestDefinition.RevisionPolicy policy, long gameTime) {
        this.definitionRevision = Math.max(1, revision);
        this.lastRevisionPolicy = policy == null ? "keep" : policy.name().toLowerCase(java.util.Locale.ROOT);
        this.lastRevisionGameTime = gameTime;
    }

    public void remapObjective(String previousId, String nextId) {
        if (previousId == null || previousId.isBlank() || nextId == null || nextId.isBlank()
                || previousId.equals(nextId)) {
            return;
        }
        if (this.completedObjectives.remove(previousId)) {
            this.completedObjectives.add(nextId);
        }
        Integer counter = this.objectiveCounters.remove(previousId);
        if (counter != null) {
            this.objectiveCounters.merge(nextId, counter, Math::max);
        }
        var timer = this.timers.remove(QuestTimers.objectiveKey(previousId));
        if (timer != null) this.timers.putIfAbsent(QuestTimers.objectiveKey(nextId), timer);
        ResourceLocation resolvedItem = this.resolvedObjectiveItems.remove(previousId);
        if (resolvedItem != null) {
            this.resolvedObjectiveItems.putIfAbsent(nextId, resolvedItem);
        }
    }

    public ResourceLocation resolvedObjectiveItem(String objectiveId) {
        return objectiveId == null || objectiveId.isBlank()
                ? null
                : this.resolvedObjectiveItems.get(objectiveId);
    }

    public boolean resolveObjectiveItem(String objectiveId, ResourceLocation itemId) {
        if (objectiveId == null || objectiveId.isBlank() || itemId == null) {
            return false;
        }
        ResourceLocation existing = this.resolvedObjectiveItems.putIfAbsent(objectiveId, itemId);
        if (existing != null && !existing.equals(itemId)) {
            throw new IllegalStateException("resolved quest objective item cannot change during a run");
        }
        return existing == null;
    }

    public void resetObjectiveProgress(Set<String> objectiveIds) {
        if (objectiveIds == null || objectiveIds.isEmpty()) {
            return;
        }
        this.completedObjectives.removeAll(objectiveIds);
        objectiveIds.forEach(this.objectiveCounters::remove);
    }

    public void resetBonusesForStage(String stageId) {
        if (stageId == null || stageId.isBlank()) {
            return;
        }
        String prefix = stageId.trim() + "/";
        this.claimedBonuses.removeIf(id -> id.startsWith(prefix));
    }

    public void restartForDefinitionRevision(String entryStage) {
        this.parallelPaths.clear();
        this.timers.clear();
        this.currentStage = entryStage == null ? "" : entryStage.trim();
        clearStageActions();
        this.completedObjectives.clear();
        this.objectiveCounters.clear();
        this.claimedBonuses.clear();
        this.visitedTarget = false;
        this.hasProof = false;
        this.targetObjectiveId = "";
    }

    public int addObjectiveCounter(String objectiveId, int amount) {
        if (objectiveId == null || objectiveId.isBlank() || amount == 0) {
            return objectiveCounter(objectiveId);
        }
        long sum = (long) this.objectiveCounters.getOrDefault(objectiveId, 0) + amount;
        int next = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, sum));
        this.objectiveCounters.put(objectiveId, next);
        return next;
    }

    public long lastTriggerGameTime(String triggerId) {
        if (triggerId == null || triggerId.isBlank()) {
            return -1L;
        }
        return this.triggerTimes.getOrDefault(triggerId, -1L);
    }

    public void markTriggerUsed(String triggerId, long gameTime) {
        if (triggerId == null || triggerId.isBlank()) {
            return;
        }
        this.triggerTimes.put(triggerId, gameTime);
    }

    public List<ChoiceHistoryEntry> choiceHistory() {
        return List.copyOf(this.choiceHistory);
    }

    public ChoiceHistoryEntry lastChoice() {
        return this.choiceHistory.isEmpty() ? null : this.choiceHistory.getLast();
    }

    public boolean hasChoice(String scenePath, String responseId, String priorStage) {
        String normalizedScene = normalizeChoiceValue(scenePath);
        String normalizedResponse = normalizeChoiceValue(responseId);
        String normalizedStage = normalizeChoiceValue(priorStage);
        return this.choiceHistory.stream().anyMatch(entry ->
                entry.scenePath().equals(normalizedScene)
                        && entry.responseId().equals(normalizedResponse)
                        && entry.priorStage().equals(normalizedStage));
    }

    public ChoiceHistoryEntry recordChoice(
            String scenePath,
            String responseId,
            String priorStage,
            String nextStage,
            long gameTime) {
        ChoiceHistoryEntry entry = new ChoiceHistoryEntry(
                scenePath,
                responseId,
                priorStage,
                nextStage,
                gameTime);
        addBounded(this.choiceHistory, entry, MAX_CHOICE_HISTORY);
        return entry;
    }

    private static int saturatingIncrement(int value) {
        return value == Integer.MAX_VALUE ? Integer.MAX_VALUE : value + 1;
    }

    private static String normalizeChoiceValue(String value) {
        return value == null ? "" : value.trim();
    }
}
