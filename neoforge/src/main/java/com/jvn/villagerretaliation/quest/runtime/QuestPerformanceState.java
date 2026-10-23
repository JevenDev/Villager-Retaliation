package com.jvn.villagerretaliation.quest.runtime;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestPerformance;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class QuestPerformanceState {
    private final Map<String, Sample> samples = new LinkedHashMap<>();
    private final Set<String> releasedActors = new java.util.HashSet<>();

    public void actorReleased(String alias) { releasedActors.add(alias); }
    public void actorBound(String alias) { releasedActors.remove(alias); }
    private boolean eligible;
    private String trackingSignature = "";
    private QuestPerformance trackingDefinition;
    private boolean frozen;
    private String tier = "";
    private String tierTitle = "";
    private String tierTitleKey = "";
    private QuestDefinition.Rewards resolvedRewards;
    private long updatedAt = -1;
    private boolean online;
    private boolean active;
    private java.util.UUID scopePlayer;

    public java.util.UUID scopePlayer() { return scopePlayer; }
    public void scopePlayer(java.util.UUID player) { scopePlayer = player; }

    public QuestPerformanceState(boolean eligible) {
        this.eligible = eligible;
        this.online = eligible;
        this.active = eligible;
    }

    public void refreshScopes(QuestDefinition definition, QuestProgressModel progress, long time) {
        update(definition, progress, time, online, active, null);
    }
    public boolean frozen() { return frozen; }
    public String tier() { return tier; }
    public String tierTitle() { return tierTitle; }
    public String tierTitleKey() { return tierTitleKey; }
    public QuestDefinition.Rewards resolvedRewards() { return resolvedRewards; }

    public Map<String, Double> values() {
        Map<String, Double> values = new LinkedHashMap<>();
        samples.forEach((id, sample) -> { if (sample.known) values.put(id, sample.value); });
        return Map.copyOf(values);
    }

    public void update(QuestDefinition definition, QuestProgressModel progress, long time,
            boolean online, boolean active, Function<String, Double> health) {
        if (frozen || definition.rewards().performance().measurements().isEmpty()) return;
        if (updatedAt < 0 && time > progress.startedGameTime()) eligible = false;
        if (trackingDefinition != definition.rewards().performance()) {
            String signature = definition.rewards().performance().trackingSignature();
            if (!trackingSignature.isEmpty() && !trackingSignature.equals(signature)) eligible = false;
            trackingSignature = signature;
            trackingDefinition = definition.rewards().performance();
        }
        long elapsed = updatedAt < 0 ? 0 : Math.max(0, time - updatedAt);
        for (var entry : definition.rewards().performance().measurements().entrySet()) {
            var measurement = entry.getValue();
            var sample = samples.computeIfAbsent(entry.getKey(), id -> new Sample());
            if (sample.open && measurement.metric() == QuestPerformance.Metric.ELAPSED_TICKS
                    && (this.online || measurement.includeOffline()) && (this.active || measurement.includeInactive()))
                sample.value += elapsed;
            boolean open = online ? inScope(definition, progress, measurement) : sample.open;
            if (open || sample.open) {
                if (measurement.health() && health == null) {
                    sample.open = open;
                    continue;
                }
                if (measurement.health()) {
                    Double value = health.apply(measurement.actor());
                    if (value != null) {
                        sample.value = measurement.metric() == QuestPerformance.Metric.LOWEST_HEALTH_PERCENT && sample.known
                                ? Math.min(sample.value, value) : value;
                        sample.known = true;
                    } else if (measurement.metric() == QuestPerformance.Metric.FINAL_HEALTH_PERCENT && !releasedActors.contains(measurement.actor())) {
                        sample.known = false;
                    }
                } else if (measurement.metric() == QuestPerformance.Metric.OPTIONAL_OBJECTIVES) {
                    sample.value = definition.objectives().stream().filter(QuestDefinition.Objective::optional)
                            .filter(objective -> measurement.objective().isEmpty() || objective.id().equals(measurement.objective()))
                            .filter(objective -> measurement.stage().isEmpty()
                                    || definition.stages().get(measurement.stage()).objectives().contains(objective.id()))
                            .filter(objective -> progress.objectiveComplete(objective.id())).count();
                    sample.known = true;
                } else {
                    sample.known = true;
                }
            }
            sample.open = open;
        }
        updatedAt = time;
        this.online = online;
        if (online) this.active = active;
    }

    private static boolean inScope(QuestDefinition definition, QuestProgressModel progress, QuestPerformance.Measurement measurement) {
        if (progress.status() != QuestProgressTypes.QuestState.ACTIVE || progress.completionPending()) return false;
        if (!measurement.stage().isEmpty()) return progress.stageActive(measurement.stage());
        if (measurement.objective().isEmpty()) return true;
        return !progress.objectiveComplete(measurement.objective()) && progress.activeStages().stream()
                .map(definition.stages()::get).filter(java.util.Objects::nonNull)
                .anyMatch(stage -> stage.objectives().contains(measurement.objective()));
    }

    public void event(QuestPerformance definition, QuestPerformance.Metric metric, double amount,
            java.util.function.Predicate<QuestPerformance.Measurement> matches) {
        if (frozen || !eligible || amount <= 0 || !Double.isFinite(amount)) return;
        definition.measurements().forEach((id, measurement) -> {
            var sample = samples.get(id);
            if (sample != null && sample.open && measurement.metric() == metric && matches.test(measurement)) {
                sample.value += amount;
                sample.known = true;
            }
        });
    }

    public void health(QuestPerformance definition, String actor, double percentage) {
        if (frozen || !Double.isFinite(percentage)) return;
        definition.measurements().forEach((id, measurement) -> {
            var sample = samples.get(id);
            if (sample != null && sample.open && measurement.health() && measurement.actor().equals(actor)) {
                sample.value = measurement.metric() == QuestPerformance.Metric.LOWEST_HEALTH_PERCENT && sample.known
                        ? Math.min(sample.value, percentage) : percentage;
                sample.known = true;
            }
        });
    }

    public QuestDefinition.Rewards freeze(QuestDefinition.Rewards base) {
        if (frozen) return resolvedRewards;
        var selected = eligible ? base.performance().select(values()) : null;
        tier = selected == null ? "" : selected.id();
        tierTitle = selected == null ? "" : selected.title();
        tierTitleKey = selected == null ? "" : selected.titleKey();
        resolvedRewards = selected == null ? new QuestDefinition.Rewards(base.experience(), base.reputation(),
                base.gossipReputation(), base.lootTable(), base.memoryEvent(), base.memoryScope()) : selected.rewards().apply(base);
        frozen = true;
        samples.values().forEach(sample -> sample.open = false);
        return resolvedRewards;
    }

    public CompoundTag save() {
        var tag = new CompoundTag();
        tag.putBoolean("Eligible", eligible);
        tag.putString("TrackingSignature", trackingSignature);
        tag.putBoolean("Frozen", frozen);
        tag.putString("Tier", tier);
        tag.putString("Title", tierTitle);
        tag.putString("TitleKey", tierTitleKey);
        tag.putLong("UpdatedAt", updatedAt);
        tag.putBoolean("Online", online);
        tag.putBoolean("Active", active);
        if (scopePlayer != null) tag.putUUID("ScopePlayer", scopePlayer);
        if (resolvedRewards != null) tag.put("Rewards", new QuestRewardDelivery(resolvedRewards, 0).save());
        var savedSamples = new CompoundTag();
        samples.forEach((id, sample) -> {
            var saved = new CompoundTag();
            saved.putDouble("Value", sample.value);
            saved.putBoolean("Known", sample.known);
            saved.putBoolean("Open", sample.open);
            savedSamples.put(id, saved);
        });
        tag.put("Samples", savedSamples);
        var released = new net.minecraft.nbt.ListTag();
        releasedActors.stream().sorted().forEach(alias -> released.add(net.minecraft.nbt.StringTag.valueOf(alias)));
        tag.put("ReleasedActors", released);
        return tag;
    }

    public static QuestPerformanceState load(CompoundTag tag) {
        var state = new QuestPerformanceState(tag.getBoolean("Eligible"));
        state.frozen = tag.getBoolean("Frozen");
        state.trackingSignature = tag.getString("TrackingSignature");
        state.tier = tag.getString("Tier");
        state.tierTitle = tag.getString("Title");
        state.tierTitleKey = tag.getString("TitleKey");
        state.updatedAt = tag.contains("UpdatedAt", Tag.TAG_LONG) ? tag.getLong("UpdatedAt") : -1;
        state.online = tag.getBoolean("Online");
        state.active = tag.getBoolean("Active");
        state.scopePlayer = tag.hasUUID("ScopePlayer") ? tag.getUUID("ScopePlayer") : null;
        if (tag.contains("Rewards", Tag.TAG_COMPOUND)) state.resolvedRewards = QuestRewardDelivery.load(tag.getCompound("Rewards")).rewards();
        if (state.frozen && state.resolvedRewards == null) throw new IllegalArgumentException("Missing frozen performance rewards");
        for (var alias : tag.getList("ReleasedActors", Tag.TAG_STRING)) state.releasedActors.add(alias.getAsString());
        var samples = tag.getCompound("Samples");
        for (String id : samples.getAllKeys()) {
            var saved = samples.getCompound(id);
            var sample = new Sample();
            sample.value = saved.getDouble("Value");
            if (!saved.contains("Value", Tag.TAG_DOUBLE) || !Double.isFinite(sample.value) || sample.value < 0)
                throw new IllegalArgumentException("Invalid performance sample: " + id);
            sample.known = saved.getBoolean("Known");
            sample.open = saved.getBoolean("Open");
            state.samples.put(id, sample);
        }
        return state;
    }

    private static final class Sample {
        double value;
        boolean known;
        boolean open;
    }
}
