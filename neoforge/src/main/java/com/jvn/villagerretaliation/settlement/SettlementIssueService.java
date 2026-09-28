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
            if (!blockedWorker(work.status())) continue;
            var area = town.duties().get(member.id());
            if (area == null) continue;
            String name = villager == null ? social.knownName(member.id()).orElse("")
                    : VillagerPresetNameRegistry.resolveDisplayName(villager).getString();
            issues.add(new Issue(identity(town, "worker:" + member.id()), bounded(name, 128), IssueKind.WORKER,
                    bounded(work.status(), 128), bounded(work.diagnostic(), 512), Page.RESIDENTS, member.id(), member.id(), area, true));
        }
        for (var project : town.projects().values()) {
            if (!project.active()) continue;
            String cause = project.blocker().replace("interaction.work.builder.", "");
            if (cause.isBlank() && !project.missingMaterials().isBlank()) cause = "missing_materials";
            if (cause.isBlank() || cause.equals("site_unloaded") || cause.equals("pending_world")) continue;
            issues.add(new Issue(identity(town, "project:" + project.id()), bounded(project.label(), 128), IssueKind.PROJECT,
                    bounded(cause, 128), bounded(project.missingMaterials(), 512), Page.PROJECTS, project.id(), project.builder(), project.area(), false));
        }
        return List.copyOf(issues);
    }

    static boolean blockedWorker(String status) {
        if (status.startsWith("missing_")) return true;
        return switch (status) {
            case "needs_attention", "profession_required", "no_filter", "no_crafting_table", "no_furnace",
                    "no_brewing_stand", "choose_brewing_order", "storage_full", "output_full", "supply_full",
                    "forest_conflict", "mine_conflict", "sapling_blocked", "no_safe_mining_route", "floor_unreachable",
                    "mining_support_blocked", "no_excavation_shaft", "support_inventory_full", "fill_supply_full", "animal_cull_failed",
                    "hunting_path_blocked", "hunting_storage_unreachable", "construction_conflict",
                    "construction_outside_world", "construction_plan_changed", "construction_unsafe",
                    "construction_unreachable", "construction_blocked_existing", "construction_blocked_support",
                    "workforce.output_backpressure", "workforce.no_work_area", "workforce.no_storage",
                    "workforce.storage_full", "workforce.inventory_full", "workforce.missing_tools",
                    "workforce.too_far", "workforce.missing_materials", "workforce.material_storage_unreachable",
                    "workforce.material_inventory_full", "workforce.build_site_unreachable" -> true;
            default -> false;
        };
    }

    private static UUID identity(SettlementRecord town, String subject) {
        return UUID.nameUUIDFromBytes((town.id().value() + ":" + subject).getBytes(StandardCharsets.UTF_8));
    }

    private static String bounded(String value, int limit) {
        if (value.length() <= limit) return value;
        return value.substring(0, Character.isHighSurrogate(value.charAt(limit - 1)) ? limit - 1 : limit);
    }
}
