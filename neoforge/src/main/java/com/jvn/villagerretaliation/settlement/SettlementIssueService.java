package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData;
import com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed;
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
        return issues(level, town, progression, SettlementDemandViewService.demands(level, town));
    }

    static List<Issue> issues(net.minecraft.server.level.ServerPlayer player, SettlementRecord town, SettlementProgressionService.Status progression) {
        return issues(player.serverLevel(), town, progression, SettlementDemandViewService.demands(player, town));
    }

    static List<Issue> issues(ServerLevel level, SettlementRecord town, SettlementProgressionService.Status progression,
                               List<com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand> demands) {
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
        if (SettlementPopulationRequirements.capped(town.stage(), progression.population())) {
            issues.add(new Issue(identity(town, "population_cap"), "", IssueKind.PROGRESSION,
                    "population_cap", net.minecraft.network.chat.Component.translatable("villagerretaliation.gui.settlement.population_cap_detail",
                            SettlementPopulationRequirements.growthTarget(), progression.target().name(), progression.target().population()).getString(), Page.RESIDENTS, null, null, null, false));
        }
        if (!SettlementPopulationRequirements.problem().isEmpty()) {
            issues.add(new Issue(identity(town, "population_configuration"), "", IssueKind.PROGRESSION,
                    "population_configuration", SettlementPopulationRequirements.problem(), Page.RESIDENTS, null, null, null, false));
        }
        var social = VillagerSocialGraphSavedData.get(level);
        var coveredEquipment = new java.util.HashSet<UUID>();
        var foodDemand = demands.stream().filter(demand -> demand.kind()
                == com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.FOOD).findFirst().orElse(null);
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
                        "resident_hungry", foodDemand == null ? "" : foodDemand.advice(),
                        foodDemand == null ? Page.RESIDENTS : Page.DEMAND, foodDemand == null ? member.id() : foodDemand.id(),
                        member.id(), foodDemand == null ? null : foodDemand.area(), false));
            }
            if (villager != null ? villager.getHealth() <= villager.getMaxHealth() / 2
                    : care != null && care.needsRecovery()) {
                issues.add(new Issue(identity(town, "health:" + member.id()), bounded(name, 128), IssueKind.RESIDENT,
                        "resident_injured", "", Page.RESIDENTS, member.id(), member.id(), null, false));
            }
            if (!blockedWorker(work.status())) continue;
            var area = town.duties().get(member.id());
            if (area == null) continue;
            var equipment = demands.stream().filter(demand -> demand.consumer().equals(member.id())
                    && equipmentIssue(demand, work.status())).toList();
            equipment.forEach(demand -> coveredEquipment.add(demand.id()));
            String detail = equipment.isEmpty() ? work.diagnostic() : equipment.stream()
                    .map(com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand::advice)
                    .collect(java.util.stream.Collectors.joining(" "));
            issues.add(new Issue(identity(town, "worker:" + member.id()), bounded(name, 128), IssueKind.WORKER,
                    bounded(work.status(), 128), bounded(detail, 512), Page.RESIDENTS, member.id(), member.id(), area, true));
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
        for (var demand : demands) {
            if (demand.cause() == SettlementSupplyRecovery.Cause.READY || coveredEquipment.contains(demand.id())) continue;
            issues.add(new Issue(demand.id(), demand.name(), IssueKind.SUPPLY, demand.cause().name().toLowerCase(Locale.ROOT),
                    demand.advice(), Page.DEMAND, demand.id(), null, demand.area(), false));
        }
        String simulation = SettlementSimulationService.suspension(level, town);
        if (!simulation.isEmpty()) {
            issues.add(new Issue(identity(town, "simulation"), "", IssueKind.SIMULATION,
                    simulation, "Scheduled towns: " + SettlementSimulationService.metrics(level).towns()
                            + ", queued refresh jobs: " + SettlementSimulationService.metrics(level).queued(), Page.SUMMARY, null, null, null, false));
        }
        return List.copyOf(issues);
    }

    private static boolean equipmentIssue(com.jvn.villagerretaliation.network.SettlementManagementPayload.Demand demand, String status) {
        if (demand.kind() != com.jvn.villagerretaliation.interaction.work.production.WorkSupplyDemand.Kind.EQUIPMENT
                || demand.cause() == SettlementSupplyRecovery.Cause.READY) return false;
        if (java.util.Set.of("workforce.missing_tools", "missing_equipment", "missing_tool",
                "missing_placement_tool", "missing_clear_tool").contains(status)) return true;
        var kind = switch (status) {
            case "missing_hoe" -> HiredEquipmentNeed.Kind.HOE;
            case "missing_rod", "missing_fishing_rod" -> HiredEquipmentNeed.Kind.ROD;
            case "missing_axe" -> HiredEquipmentNeed.Kind.AXE;
            case "missing_leaf_tool" -> HiredEquipmentNeed.Kind.LEAVES;
            case "missing_pickaxe" -> HiredEquipmentNeed.Kind.PICKAXE;
            case "missing_shovel" -> HiredEquipmentNeed.Kind.SHOVEL;
            case "missing_shears" -> HiredEquipmentNeed.Kind.SHEARS;
            case "missing_hunting_weapon" -> HiredEquipmentNeed.Kind.HUNTING;
            case "missing_cull_weapon" -> HiredEquipmentNeed.Kind.CULLING;
            case "missing_ranged_ammo" -> HiredEquipmentNeed.Kind.AMMUNITION;
            default -> null;
        };
        return kind != null && demand.alternatives().stream()
                .anyMatch(HiredEquipmentNeed.of(kind));
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
