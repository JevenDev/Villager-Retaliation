package com.jvn.villagerretaliation.quest.site;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.dialogue.DialogueCondition;
import com.jvn.villagerretaliation.dialogue.conditions.ConditionEvaluator;
import com.jvn.villagerretaliation.quest.QuestDefinition;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.VillagerQuestService;
import com.jvn.villagerretaliation.quest.conditions.VillagerConditionAccess;
import com.jvn.villagerretaliation.quest.site.QuestSiteSavedData.Site;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

final class QuestSiteAccessService {
    private QuestSiteAccessService() { }

    static boolean compatible(Site site, QuestSiteDefinition config) {
        var saved = JsonParser.parseString(site.config).getAsJsonObject();
        var current = new Gson().toJsonTree(config).getAsJsonObject();
        if (!saved.has("access")) current.remove("access");
        return saved.equals(current);
    }

    static QuestSiteAccess access(Site site, QuestDefinition quest) {
        return access(site, quest.target().site());
    }

    static QuestSiteAccess access(Site site, QuestSiteDefinition config) {
        return JsonParser.parseString(site.config).getAsJsonObject().has("access") ? config.access() : null;
    }

    static void observe(ServerLevel level, Site site, QuestDefinition quest, List<ServerPlayer> players) {
        var access = access(site, quest);
        if (access == null || site.position == null || site.released) return;
        for (var player : players) {
            if (player.level() != level) continue;
            var progress = VillagerQuestSavedData.get(level).get(player.getUUID(), site.quest);
            if (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !java.util.Objects.equals(site.run, progress.questRunId())) continue;
            for (var route : access.routes()) {
                var position = site.position.offset(route.offset());
                boolean reached = player.distanceToSqr(position.getBottomCenter()) <= route.radius() * route.radius();
                boolean revealed = reached || !route.revealWhen().isEmpty() && matches(level, player, quest, route.revealWhen());
                if (revealed && site.knownRoutes.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashSet<>()).add(route.id())) {
                    QuestSiteSavedData.get(level).setDirty();
                    player.displayClientMessage(Component.translatable("quest.villagerretaliation.site.route_found", route.label(),
                            position.getX(), position.getY(), position.getZ()), false);
                }
                if (reached && site.reachedRoutes.computeIfAbsent(player.getUUID(), ignored -> new LinkedHashSet<>()).add(route.id()))
                    QuestSiteSavedData.get(level).setDirty();
            }
        }
    }

    static String approach(ServerLevel level, Site site, QuestDefinition quest, ServerPlayer player) {
        var progress = VillagerQuestSavedData.get(level).get(player.getUUID(), site.quest);
        if (player.level() != level || site.run == null || progress == null
                || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE || !site.run.equals(progress.questRunId())) return "";
        var access = access(site, quest);
        if (access == null) return site.defeated.containsAll(site.guards) ? "guards" : "";
        observe(level, site, quest, List.of(player));
        for (var approach : access.approaches()) {
            if (approach.guardsDefeated() && !site.defeated.containsAll(site.guards)) continue;
            if (!approach.route().isBlank() && !site.reachedRoutes.getOrDefault(player.getUUID(), Set.of()).contains(approach.route())) continue;
            if (matches(level, player, quest, approach.conditions())) return approach.id();
        }
        return "";
    }

    private static boolean matches(ServerLevel level, ServerPlayer player, QuestDefinition quest, List<DialogueCondition> conditions) {
        var progress = VillagerQuestSavedData.get(level).get(player.getUUID(), quest.id());
        return ConditionEvaluator.all(VillagerConditionAccess.forSaved(level, player, quest, progress,
                VillagerQuestService.conditionQueries(player)), null, conditions) == DialogueCondition.ConditionOutcome.MATCHED;
    }
}
