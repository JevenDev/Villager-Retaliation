package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.ChoiceHistoryEntry;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.CompletionHistoryEntry;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.ProviderRebindHistoryEntry;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData.QuestState;
import java.util.List;
import java.util.Map;
import com.jvn.villagerretaliation.quest.provider.QuestProviderBinding;
import com.jvn.villagerretaliation.quest.provider.QuestProviderSnapshot;

public class QuestProgressState extends QuestProgressModel {
    private static final String PROFESSION = "villagerretaliation:profession";
    private static final String LEVEL = "villagerretaliation:level";
    protected String issuerProfession = "";
    protected int issuerLevel;
    private List<QuestProgressTypes.CompletionHistoryEntry> cachedCompletionRecords = List.of();
    private List<CompletionHistoryEntry> cachedCompletionHistory = List.of();

    public String issuerProfession() { return this.issuerProfession; }
    public int issuerLevel() { return this.issuerLevel; }

    @Override public Map<String, String> issuerMetadata() {
        return metadata(this.issuerProfession, this.issuerLevel);
    }

    @Override public void setProvider(QuestProviderSnapshot provider, Map<String, String> metadata) {
        super.setProvider(provider, metadata);
        this.issuerProfession = metadata.getOrDefault(PROFESSION, "");
        this.issuerLevel = metadataLevel(metadata);
    }

    public void setIssuer(java.util.UUID provider, String name, String profession, int level,
            net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, net.minecraft.core.BlockPos pos, String group) {
        if (provider != null) this.startedVillagerId = provider;
        this.issuerName = name == null ? "" : name;
        this.issuerProfession = profession == null ? "" : profession;
        this.issuerLevel = Math.max(0, level);
        this.issuerDimension = dimension;
        this.issuerPos = pos == null ? null : pos.immutable();
        this.issuerVillageKey = group == null ? "" : group;
    }

    public void rebindProvider(QuestProviderBinding binding, long gameTime, String reason) {
        if (binding == null || binding.providerId() == null) throw new IllegalArgumentException("provider binding must have an id");
        recordProviderRebind(binding.providerId(), gameTime, reason);
        setIssuer(binding.providerId(), binding.displayName(), binding.professionId() == null ? "" : binding.professionId().toString(),
                binding.level(), binding.dimension(), binding.pos(), binding.villageKey());
    }

    private static Map<String, String> metadata(String profession, int level) {
        return Map.of(PROFESSION, profession, LEVEL, Integer.toString(level));
    }

    private static int metadataLevel(Map<String, String> metadata) {
        try { return Math.max(0, Integer.parseInt(metadata.getOrDefault(LEVEL, "0"))); }
        catch (NumberFormatException invalid) { return 0; }
    }

    public QuestState state() {
        return QuestState.valueOf(status().name());
    }

    public List<CompletionHistoryEntry> completionHistory() {
        if (!this.completionHistory.equals(this.cachedCompletionRecords)) {
            this.cachedCompletionRecords = completionRecords();
            this.cachedCompletionHistory = this.cachedCompletionRecords.stream().map(QuestProgressState::legacyCompletion).toList();
        }
        return this.cachedCompletionHistory;
    }

    public List<ProviderRebindHistoryEntry> providerRebindHistory() {
        return providerRebindRecords().stream().map(QuestProgressState::legacyRebind).toList();
    }

    public List<ChoiceHistoryEntry> choiceHistory() {
        return choiceRecords().stream().map(QuestProgressState::legacyChoice).toList();
    }

    public ChoiceHistoryEntry lastChoice() {
        return lastDecision() == null ? null : legacyChoice(lastDecision());
    }

    public ChoiceHistoryEntry recordChoice(String scenePath, String responseId, String priorStage, String nextStage, long gameTime) {
        return legacyChoice(recordDecision(scenePath, responseId, priorStage, nextStage, gameTime));
    }

    protected static QuestProgressTypes.ChoiceHistoryEntry sharedChoice(ChoiceHistoryEntry value) {
        return new QuestProgressTypes.ChoiceHistoryEntry(value.scenePath(), value.responseId(), value.priorStage(), value.nextStage(), value.gameTime());
    }

    protected static QuestProgressTypes.CompletionHistoryEntry sharedCompletion(CompletionHistoryEntry value) {
        return new QuestProgressTypes.CompletionHistoryEntry(value.completionIndex(), value.issuerId(), value.startedGameTime(), value.completedGameTime(),
                value.issuerName(), metadata(value.issuerProfession(), value.issuerLevel()), value.issuerDimension(), value.issuerPos(), value.issuerVillageKey());
    }

    protected static QuestProgressTypes.ProviderRebindHistoryEntry sharedRebind(ProviderRebindHistoryEntry value) {
        return new QuestProgressTypes.ProviderRebindHistoryEntry(value.previousProviderId(), value.newProviderId(), value.previousName(),
                metadata(value.previousProfession(), value.previousLevel()), value.previousDimension(), value.previousPos(), value.previousVillageKey(), value.gameTime(), value.reason());
    }

    private static ChoiceHistoryEntry legacyChoice(QuestProgressTypes.ChoiceHistoryEntry value) {
        return new ChoiceHistoryEntry(value.scenePath(), value.responseId(), value.priorStage(), value.nextStage(), value.gameTime());
    }

    private static CompletionHistoryEntry legacyCompletion(QuestProgressTypes.CompletionHistoryEntry value) {
        return new CompletionHistoryEntry(value.completionIndex(), value.issuerId(), value.startedGameTime(), value.completedGameTime(),
                value.issuerName(), value.issuerMetadata().getOrDefault(PROFESSION, ""), metadataLevel(value.issuerMetadata()), value.issuerDimension(), value.issuerPos(), value.issuerVillageKey());
    }

    private static ProviderRebindHistoryEntry legacyRebind(QuestProgressTypes.ProviderRebindHistoryEntry value) {
        return new ProviderRebindHistoryEntry(value.previousProviderId(), value.newProviderId(), value.previousName(), value.previousMetadata().getOrDefault(PROFESSION, ""), metadataLevel(value.previousMetadata()),
                value.previousDimension(), value.previousPos(), value.previousVillageKey(), value.gameTime(), value.reason());
    }
}
