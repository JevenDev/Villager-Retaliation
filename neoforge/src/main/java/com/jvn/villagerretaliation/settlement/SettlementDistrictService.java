package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.HiredWorkArea;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderConstructionCost;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderSitePlanner;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementBuildingPreviewRequestPayload;
import com.jvn.villagerretaliation.network.SettlementDistrictRequestPayload;
import com.jvn.villagerretaliation.network.SettlementDistrictResultPayload;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementDistrictService {
    private SettlementDistrictService() {}

    public static void request(ServerPlayer player, SettlementDistrictRequestPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, SettlementDistrictRequestPayload.TYPE.id(), 10L)) return;
        PacketDistributor.sendToPlayer(player, evaluate(player, request));
    }

    public static SettlementDistrictResultPayload evaluate(ServerPlayer player, SettlementDistrictRequestPayload request) {
        var level = player.serverLevel();
        var town = SettlementSavedData.get(level).get(new VillageAllegianceId(request.town())).orElse(null);
        if (!SettlementConstructionService.canBuild(player, town)) return reply(request, "", List.of(), "District construction is not permitted", false);
        var ids = new java.util.HashSet<java.util.UUID>(); request.buildings().forEach(b -> ids.add(b.request())); request.paths().forEach(p -> ids.add(p.id()));
        var existing = ids.stream().map(town::project).filter(java.util.Objects::nonNull).toList();
        if (!existing.isEmpty()) {
            boolean replay = request.confirm() && existing.size() == ids.size() && existing.stream().allMatch(project -> request.request().equals(project.district())
                    && request.fingerprint().equals(project.districtFingerprint()));
            return reply(request, "", List.of(), replay ? "" : "A project identity in this district is already in use", replay);
        }
        if (town.stage().projects() == 0) return reply(request, "", List.of(), "Advance the settlement stage to unlock construction", false);
        if (town.projects().values().stream().filter(SettlementProject::pending).count() + ids.size() > SettlementRecord.CONSTRUCTION_QUEUE_LIMIT)
            return reply(request, "", List.of(), "There is not enough space in the construction queue", false);
        var projects = new ArrayList<SettlementProject>();
        var plans = new ArrayList<BuilderStructureScanner.StructurePlan>();
        var lines = new ArrayList<String>();
        for (var building : request.buildings()) {
            if (SettlementSavedData.get(level).settlements().stream().anyMatch(other -> other.knowsProject(building.request())))
                return reply(request, "", lines, "A building request identity is already in use", false);
            var preview = SettlementBuildingPlanner.preview(player, new SettlementBuildingPreviewRequestPayload(request.town(), 1,
                    building.structure(), building.anchor(), building.rotation(), building.builder(), building.preparation()));
            if (!preview.failure().isEmpty()) return reply(request, "", lines, "Building placement: " + preview.failure().replace('_', ' '), false);
            var base = SettlementConstructionService.scan(level, building.structure(), building.rotation());
            var preparation = SettlementBuildingPreparation.preview(player, town, base, preview.origin(), building.preparation());
            if (!preparation.failure().isEmpty()) return reply(request, "", lines, "Building terrain changed. Review the plan again", false);
            var plan = preparation.preparation().apply(base);
            if (!building.builder().equals(SettlementProject.AUTOMATIC_BUILDER)
                    && !com.jvn.villagerretaliation.allegiance.VillageAllegianceRegistrySavedData.get(level).canonicalRecord(town.id())
                            .map(record -> record.residents().containsKey(building.builder())).orElse(false))
                return reply(request, "", lines, "A selected builder no longer belongs to the settlement", false);
            var area = SettlementConstructionService.workplace(level, town, plan, preview.origin(), building.request());
            if (area == null || !town.owns(area)) return reply(request, "", lines, "Building construction bounds are outside owned land", false);
            var project = SettlementProject.planned(town, area, building.builder(), plan, preview.origin(), building.request(), level.getGameTime());
            if (building.preparation().enabled()) project.preparation(preparation.preparation());
            project.queue(player.getUUID()); projects.add(project); plans.add(plan);
            lines.add("Building: " + truncate(plan.entry().menuLabel(), 180));
        }
        for (var path : request.paths()) {
            if (SettlementSavedData.get(level).settlements().stream().anyMatch(other -> other.knowsProject(path.id())))
                return reply(request, "", lines, "A path request identity is already in use", false);
            var preview = SettlementPathTerrain.preview(level, path.path(), pos -> town.contains(pos) && level.mayInteract(player, pos));
            if (!preview.error().isEmpty()) return reply(request, "", lines, "Path: " + preview.error(), false);
            var order = SettlementPathTerrain.order(preview);
            if (order.protectedSite(level, town)) return reply(request, "", lines, "A path crosses a protected worksite or completed building", false);
            var plan = order.plan(path.id()); var origin = order.origin();
            var area = SettlementConstructionService.workplace(level, town, plan, origin, path.id());
            if (area == null || !town.owns(area)) return reply(request, "", lines, "Path construction bounds are outside owned land", false);
            var check = BuilderSitePlanner.validateSite(level, player, null, HiredWorkArea.fromBounds(area.min(), area.max(), true), plan, origin);
            if (!check.valid()) return reply(request, "", lines, "Path placement: " + check.statusKey(), false);
            var project = SettlementProject.planned(town, area, SettlementProject.AUTOMATIC_BUILDER, plan, origin, path.id(), level.getGameTime());
            project.order(order); project.queue(player.getUUID()); projects.add(project); plans.add(plan);
            lines.add("Path: " + path.path().material().label());
        }
        for (int i = 0; i < projects.size(); i++) for (int j = i + 1; j < projects.size(); j++)
            if (projects.get(i).overlaps(projects.get(j))) return reply(request, "", lines, "District projects overlap. Move the buildings or connecting paths apart", false);
        if (projects.stream().anyMatch(project -> town.projects().values().stream().anyMatch(p -> p.reservesFootprint() && p.overlaps(project))))
            return reply(request, "", lines, "The district overlaps unfinished construction", false);
        int blocks = plans.stream().mapToInt(plan -> plan.blocks().size()).sum();
        if (blocks > 65_536) return reply(request, "", lines, "Split this district into plans of at most 65536 blocks", false);
        var totals = new LinkedHashMap<String, Long>(); var labels = new LinkedHashMap<String, String>();
        long fee = 0, beds = 0;
        var identity = new StringBuilder(request.town().toString()).append(request.request()).append(request.pathsFirst());
        for (int i = 0; i < plans.size(); i++) {
            var plan = plans.get(i); var project = projects.get(i); fee += plan.price();
            if (project.order() == null) beds += plan.blocks().stream().filter(b -> b.state().getBlock() instanceof BedBlock
                    && b.state().getValue(BedBlock.PART) == BedPart.FOOT).count();
            identity.append(project.id()).append(project.origin()).append(project.requestedBuilder()).append(BuilderStructureScanner.fingerprint(plan));
            for (var ingredient : BuilderConstructionCost.requirements(plan)) {
                String key = (ingredient.tag() ? "#" : "") + ingredient.id();
                totals.merge(key, (long) ingredient.count(), Long::sum); labels.put(key, ingredient.displayName());
            }
        }
        if (totals.size() > 256) return reply(request, "", lines, "Split the district into plans with fewer material types", false);
        lines.add(0, "Projects: " + projects.size() + " / planned beds: " + beds);
        lines.add(1, "Combined labor fees: " + fee + " / available funds: " + town.economy().constructionFunds().mixedFraction());
        lines.add(request.pathsFirst() && !request.paths().isEmpty() ? "Order: Complete paths before starting buildings" : "Order: Projects can start independently");
        lines.add("Combined materials:");
        for (var row : totals.entrySet()) lines.add(truncate(labels.get(row.getKey()), 210) + " x" + row.getValue());
        String fingerprint = digest(identity.toString());
        if (!request.confirm()) return reply(request, fingerprint, lines, "", false);
        if (!fingerprint.equals(request.fingerprint())) return reply(request, "", lines, "The district changed. Review the updated costs before commissioning", false);
        for (var project : projects) {
            project.district(request.request(), fingerprint);
            if (request.pathsFirst() && project.order() == null) project.prerequisites(request.paths().stream().map(SettlementDistrictRequestPayload.PlannedPath::id).toList());
        }
        if (!town.queueProjects(player.getUUID(), projects)) return reply(request, "", lines, "The queue or ownership changed. Review the district again", false);
        SettlementConstructionService.tickQueue(level, town);
        return reply(request, "", lines, "", true);
    }

    private static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private static String truncate(String text, int limit) { return text.substring(0, Math.min(limit, text.length())); }
    private static SettlementDistrictResultPayload reply(SettlementDistrictRequestPayload request, String fingerprint, List<String> lines, String failure, boolean queued) {
        return new SettlementDistrictResultPayload(request.town(), request.request(), fingerprint, lines, truncate(failure, 256), queued);
    }
}
