package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.allegiance.VillageAllegianceId;
import com.jvn.villagerretaliation.interaction.work.builder.BuilderStructureScanner;
import com.jvn.villagerretaliation.network.ServerboundRequestLimiter;
import com.jvn.villagerretaliation.network.SettlementBuildingInfoPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingRequestPayload;
import com.jvn.villagerretaliation.network.SettlementBuildingRequestPayload.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.network.PacketDistributor;

public final class SettlementBuildingManagement {
    private SettlementBuildingManagement() {}

    public static void request(ServerPlayer player, SettlementBuildingRequestPayload request) {
        if (!ServerboundRequestLimiter.tryAcquire(player, SettlementBuildingRequestPayload.TYPE.id(), 4L)) return;
        PacketDistributor.sendToPlayer(player, inspect(player, request));
    }

    public static SettlementBuildingInfoPayload inspect(ServerPlayer player, SettlementBuildingRequestPayload request) {
        var level = player.serverLevel();
        var town = SettlementSavedData.get(level).get(new VillageAllegianceId(request.town())).orElse(null);
        if (town == null || !town.dimension().equals(level.dimension().location()) || !player.isAlive() || player.isSpectator()
                || !town.permits(player.getUUID(), SettlementPermission.VIEW)) return reply(request, "Building", List.of(), "", "Building access is not permitted", false);
        var source = town.project(request.building());
        if (source == null || !source.completedBuilding() || !town.owns(source))
            return reply(request, "Building", List.of(), "", "This completed building is no longer available", false);
        var accepted = town.project(request.request());
        if (request.confirm() && accepted != null && accepted.buildingWork() != null
                && accepted.buildingWork().source.equals(source.id()) && accepted.buildingWork().action == request.action())
            return reply(request, source.label(), List.of(), "", "Builder work already queued", true);
        var original = SettlementConstructionService.scan(level, source);
        if (original == null || !source.matchesPlan(original))
            return reply(request, source.label(), List.of(), "", "The authored template changed or is unavailable", false);
        var lines = new ArrayList<String>();
        var changes = new ArrayList<BuilderStructureScanner.BuildBlock>();
        int intact = 0, missing = 0, altered = 0, beds = 0, stations = 0;
        String failure = "";
        for (var block : original.blocks()) {
            var pos = original.worldPos(source.origin(), block);
            if (!level.hasChunkAt(pos)) return reply(request, source.label(), List.of(), "", "Load the whole building to inspect it", false);
            var current = level.getBlockState(pos);
            boolean matches = BuilderStructureScanner.sameSchematicState(current, block.state());
            if (matches) intact++; else if (current.isAir()) missing++; else altered++;
            if (current.getBlock() instanceof BedBlock && current.getValue(BedBlock.PART) == BedPart.HEAD) beds++;
            if (PoiTypes.forState(current).filter(poi -> poi.is(PoiTypeTags.ACQUIRABLE_JOB_SITE)).isPresent()) stations++;
            if (request.action() == Action.INSPECT) continue;
            if (request.action() == Action.DISMANTLE) {
                if (current.hasBlockEntity()) { failure = "Remove containers and other block entities before dismantling"; continue; }
                if (!matches && !current.isAir()) { failure = "Remove altered blocks before dismantling the authored building"; continue; }
                if (current.isAir()) continue;
            } else {
                if (matches || request.action() == Action.REPAIR && !current.isAir()) continue;
                if (current.hasBlockEntity()) { failure = "Move the existing block entity before renovating this position"; continue; }
            }
            if (!level.mayInteract(player, pos) || !town.contains(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
                failure = "A changed position is protected or outside settlement land"; continue;
            }
            var target = request.action() == Action.DISMANTLE ? Blocks.AIR.defaultBlockState() : block.state();
            changes.add(new BuilderStructureScanner.BuildBlock(block.localPos(), target,
                    request.action() == Action.DISMANTLE ? null : block.blockEntityTag(),
                    request.action() == Action.DISMANTLE ? ItemStack.EMPTY : block.requiredItem(),
                    request.action() == Action.DISMANTLE ? BuilderStructureScanner.BuilderToolAction.NONE : block.toolAction(), current));
        }
        var bounds = new SettlementArea(source.id(), SettlementAreaType.HOME, "Building survey", source.min(), source.max(), 5, true);
        var survey = SettlementBuildingSurvey.inspect(level, bounds);
        var occupants = town.homes().values().stream().filter(home -> home.bed() != null && bounds.contains(home.bed())).toList();
        lines.add("Purpose: " + (beds > 0 && stations > 0 ? "Home and workplace" : beds > 0 ? "Housing" : stations > 0 ? "Workplace" : "Utility building"));
        lines.add("Condition: " + intact + "/" + original.blocks().size() + " authored blocks intact");
        lines.add("Missing: " + missing + " / altered: " + altered);
        lines.add("Beds: " + beds + " / usable: " + survey.beds().size() + " / occupants: " + occupants.size());
        lines.add("Workstations: " + stations);
        for (var home : occupants.stream().limit(12).toList()) lines.add("Resident: " + name(level, home.resident()));
        for (var duty : town.duties().entrySet().stream().filter(row -> {
            var area = town.workplace(row.getValue()); return area != null && area.min().getX() <= source.max().getX()
                    && area.max().getX() >= source.min().getX() && area.min().getZ() <= source.max().getZ() && area.max().getZ() >= source.min().getZ();
        }).limit(12).toList()) lines.add("Worker: " + name(level, duty.getKey()));
        if (request.action() == Action.INSPECT) return reply(request, source.label(), lines, "", "Select an action to review its quote", false);
        if (!SettlementConstructionService.canBuild(player, town)) failure = "Construction is not permitted";
        if (town.stage().projects() == 0) failure = "Advance the settlement stage to unlock construction";
        if (town.projects().values().stream().anyMatch(p -> p.unfinished() && p.buildingWork() != null && p.buildingWork().source.equals(source.id())))
            failure = "This building already has queued maintenance";
        if (!failure.isEmpty()) return reply(request, source.label(), lines, "", failure, false);
        if (changes.isEmpty()) return reply(request, source.label(), lines, "", "No changes are needed for this action", false);
        if (request.action() == Action.DISMANTLE) java.util.Collections.reverse(changes);
        int fee = Math.max(1, (int) Math.ceil((double) original.price() * changes.size() / original.blocks().size()));
        var work = new SettlementBuildingWork(source.id(), request.action(), source.origin(), changes, fee);
        var plan = work.plan(request.request());
        String fingerprint = BuilderStructureScanner.fingerprint(plan);
        lines.add("Work: " + changes.size() + " blocks / labor fee: " + fee);
        lines.add("Materials: " + (plan.materials().isEmpty() ? "None" : plan.materialSummary(12)));
        lines.add(request.action() == Action.RENOVATE ? "Restores the original design, including altered blocks" : request.action() == Action.DISMANTLE
                ? "Removes authored blocks. No guaranteed material refund" : "Repairs missing blocks and preserves alterations");
        if (!request.confirm()) return reply(request, source.label(), lines, fingerprint, "Review the changes, then queue builder work", false);
        if (!fingerprint.equals(request.fingerprint())) return reply(request, source.label(), lines, "", "The building changed. Review a new quote", false);
        if (SettlementSavedData.get(level).settlements().stream().anyMatch(other -> other.knowsProject(request.request())))
            return reply(request, source.label(), lines, "", "This request identity is already in use", false);
        var area = SettlementConstructionService.workplace(level, town, plan, source.origin(), request.request());
        if (area == null || !town.owns(area)) return reply(request, source.label(), lines, "", "Construction bounds are outside owned land", false);
        var project = SettlementProject.planned(town, area, SettlementProject.AUTOMATIC_BUILDER, plan, source.origin(), request.request(), level.getGameTime());
        project.buildingWork(work); project.queue(player.getUUID());
        if (!town.queueProject(player.getUUID(), project)) return reply(request, source.label(), lines, "", "The queue is full or overlaps unfinished work", false);
        SettlementConstructionService.tickQueue(level, town);
        return reply(request, source.label(), lines, "", "Builder work queued", true);
    }

    private static String name(net.minecraft.server.level.ServerLevel level, java.util.UUID id) {
        String name = level.getEntity(id) instanceof net.minecraft.world.entity.npc.Villager villager
                ? com.jvn.villagerretaliation.villager.VillagerPresetNameRegistry.resolveDisplayName(villager).getString()
                : com.jvn.villagerretaliation.social.VillagerSocialGraphSavedData.get(level).knownName(id).orElse("Unknown resident");
        return name.substring(0, Math.min(200, name.length()));
    }

    private static SettlementBuildingInfoPayload reply(SettlementBuildingRequestPayload request, String name, List<String> lines,
                                                        String fingerprint, String feedback, boolean queued) {
        return new SettlementBuildingInfoPayload(request.town(), request.building(), request.request(), name.substring(0, Math.min(128, name.length())),
                lines.stream().map(line -> line.substring(0, Math.min(256, line.length()))).toList(), fingerprint, feedback, queued);
    }
}
