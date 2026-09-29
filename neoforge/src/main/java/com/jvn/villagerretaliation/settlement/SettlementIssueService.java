package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.Issue;
import com.jvn.villagerretaliation.network.SettlementManagementPayload.IssueKind;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData;
import com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

final class SettlementIssueService {
    private SettlementIssueService() {
    }

    static List<Issue> issues(ServerLevel level, SettlementRecord town, SettlementProgressionService.Status progression) {
        List<Issue> issues = new ArrayList<>();
        for (var missing : progression.missing()) {
            Page target = switch (missing) {
                case POPULATION -> Page.RESIDENTS;
                case FOOD_STOCK, DEPOT -> Page.DEPOTS;
                default -> Page.AREAS;
            };
            String cause = missing.name().toLowerCase(Locale.ROOT);
            issues.add(new Issue(identity(town, "progression:" + cause), "", IssueKind.PROGRESSION,
                    cause, "", target, null, null, null, false));
        }
        var social = VillagerSocialGraphSavedData.get(level);
        for (var member : VillageAllegianceRegistrySavedData.get(level).residentRoster(town.id(), level.getGameTime())) {
            var entity = level.getEntity(member.id());
            Villager villager = entity instanceof Villager loaded && loaded.isAlive() ? loaded : null;
            var work = SettlementManagementService.residentWork(level, town, member.id(), villager, town.workers().get(member.id()));
            var care = town.residents().get(member.id());
            String name = villager == null ? social.knownName(member.id()).orElse("")
                    : VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
            if (care != null && care.shortageTicks() >= 1_200L && (villager == null ? care.food()
                    : com.jvn.villagerretaliation.villager.VillagerRecoveryService.captureRecoveryState(villager).food()) < 6) {
                issues.add(new Issue(identity(town, "food:" + member.id()), bounded(name, 128), IssueKind.RESIDENT,
                        "resident_hungry", "", Page.RESIDENTS, member.id(), member.id(), null, false));
            }
            if (villager != null ? villager.getHealth() <= villager.getMaxHealth() / 2
                    : care != null && care.needsRecovery()) {
                issues.add(new Issue(identity(town, "health:" + member.id()), bounded(name, 128), IssueKind.RESIDENT,
                        "resident_injured", "", Page.RESIDENTS, member.id(), member.id(), null, false));
            }
            if (!blockedWorker(work.status())) continue;
            var area = town.duties().get(member.id());
            if (area == null) continue;
            issues.add(new Issue(identity(town, "worker:" + member.id()), bounded(name, 128), IssueKind.WORKER,
                    bounded(work.status(), 128), bounded(work.diagnostic(), 512), Page.RESIDENTS, member.id(), member.id(), area, true));
        }
        for (var project : town.projects().values()) {
            if (!project.reservesFootprint()) continue;
            String cause = project.blocker().replace("interaction.work.builder.", "");
            if (cause.isBlank() && !project.missingMaterials().isBlank()) cause = "missing_materials";
            if (cause.isBlank()) continue;
            issues.add(new Issue(identity(town, "project:" + project.id()), bounded(project.label(), 128), IssueKind.PROJECT,
                    bounded(cause, 128), bounded(project.missingMaterials(), 512), Page.PROJECTS, project.id(), project.builder(), project.area(), false));
        }
        for (var order : town.economy().orders().values()) {
            var result = town.economy().lastResults().get(order.id());
            if (!order.enabled() || result == null) continue;
            String cause = result.status().name().toLowerCase(Locale.ROOT);
            if (!SettlementWorkerDiagnostics.diagnostic("trade." + cause).severity().attention()) continue;
            issues.add(new Issue(identity(town, "trade:" + order.id()), bounded(order.sample().getHoverName().getString(), 128),
                    IssueKind.TRADE, cause, "", Page.TRADE, order.id(), null, null, false));
        }
        String simulation = SettlementSimulationService.suspension(level, town);
        if (!simulation.isEmpty()) {
            issues.add(new Issue(identity(town, "simulation"), "", IssueKind.SIMULATION,
                    simulation, "", Page.SUMMARY, null, null, null, false));
        }
        return List.copyOf(issues);
    }

    static boolean blockedWorker(String status) {
        return SettlementWorkerDiagnostics.diagnostic(status).severity().attention();
    }

    private static UUID identity(SettlementRecord town, String subject) {
        return UUID.nameUUIDFromBytes((town.id().value() + ":" + subject).getBytes(StandardCharsets.UTF_8));
    }

    private static String bounded(String value, int limit) {
        if (value.length() <= limit) return value;
        return value.substring(0, Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit);
    }
}
