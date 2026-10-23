package com.jvn.villagerretaliation.quest.tracking;

import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.QuestPerformance;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;

public final class QuestPerformancePresentation {
    private QuestPerformancePresentation() { }

    public static String title(ServerPlayer player, QuestPerformance.Tier tier) {
        return QuestTrackerPresenter.resolveText(player,
                new QuestDefinition.SelectedText(tier.title(), tier.titleKey()), Map.of());
    }

    public static String result(ServerPlayer player, QuestDefinition definition) {
        if (player == null || definition.rewards().performance().tiers().isEmpty()) return "";
        var progress = VillagerQuestSavedData.get(player.serverLevel()).get(player.getUUID(), definition.id());
        if (progress == null || !progress.performance().frozen()) return "";
        var state = progress.performance();
        String title = state.tier().isEmpty() ? text(player, "base", "Base rewards", Map.of())
                : QuestTrackerPresenter.resolveText(player,
                        new QuestDefinition.SelectedText(state.tierTitle(), state.tierTitleKey()), Map.of());
        return text(player, "result", "Performance: {tier}", Map.of("tier", title));
    }

    public static String description(ServerPlayer player, QuestDefinition definition, String description) {
        var performance = definition.rewards().performance();
        if (performance.tiers().isEmpty()) return description;
        List<String> lines = new ArrayList<>();
        if (!description.isBlank()) lines.add(description);
        String result = result(player, definition);
        if (!result.isBlank()) lines.add(result);
        lines.add(text(player, "requirements", "Performance rewards", Map.of()));
        for (var tier : performance.tiers()) {
            lines.add(title(player, tier));
            for (var entry : tier.requirements().entrySet()) {
                var measurement = performance.measurements().get(entry.getKey());
                String metric = text(player, "metric." + measurement.metric().name().toLowerCase(Locale.ROOT),
                        metricLabel(measurement.metric()), Map.of());
                String scope = "";
                if (!measurement.stage().isEmpty()) {
                    var stage = definition.stages().get(measurement.stage());
                    scope = stage == null ? measurement.stage() : QuestTrackerPresenter.resolveText(player,
                            new QuestDefinition.SelectedText(stage.metadata().getOrDefault("title", stage.id().replace('_', ' ')), stage.metadata().getOrDefault("title_key", "")), Map.of());
                } else if (!measurement.objective().isEmpty()) {
                    var objective = definition.objectives().stream().filter(value -> value.id().equals(measurement.objective())).findFirst().orElse(null);
                    scope = objective == null ? measurement.objective() : QuestTrackerPresenter.resolveText(player, new QuestDefinition.SelectedText(
                            objective.tracker().text(), objective.tracker().textKey()), Map.of());
                }
                if (measurement.health()) metric += " (" + measurement.actor().replace('_', ' ') + ")";
                if (!scope.isBlank()) metric += " (" + scope + ")";
                var bounds = entry.getValue();
                String requirement = bounds.min() == null
                        ? text(player, "maximum", "at most {value}", Map.of("value", number(bounds.max(), measurement.health())))
                        : bounds.max() == null
                        ? text(player, "minimum", "at least {value}", Map.of("value", number(bounds.min(), measurement.health())))
                        : text(player, "range", "{min} to {max}", Map.of("min", number(bounds.min(), measurement.health()), "max", number(bounds.max(), measurement.health())));
                lines.add(metric + ": " + requirement);
            }
        }
        return String.join(System.lineSeparator(), lines);
    }

    private static String number(double value, boolean percent) {
        return (value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value)) + (percent ? "%" : "");
    }

    private static String metricLabel(QuestPerformance.Metric metric) {
        return switch (metric) {
            case ELAPSED_TICKS -> "Time (ticks)";
            case FINAL_HEALTH_PERCENT -> "Final health";
            case LOWEST_HEALTH_PERCENT -> "Lowest health";
            case DEATHS -> "Player deaths";
            case DAMAGE_TAKEN -> "Damage taken";
            case KILLS -> "Kills";
            case OPTIONAL_OBJECTIVES -> "Optional objectives completed";
        };
    }

    public static String text(ServerPlayer player, String key, String fallback, Map<String, String> values) {
        return QuestTrackerPresenter.resolveGlobalText(player, "quest.performance." + key, fallback, values);
    }
}
