package com.jvn.villagerretaliation.settlement;

import com.jvn.villagerretaliation.block.PaymentBoxBlockEntity;
import com.jvn.villagerretaliation.block.SellBoxBlockEntity;
import com.jvn.villagerretaliation.block.SettlementDepotBlockEntity;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.inventory.AssignedStorageService;
import com.jvn.villagerretaliation.inventory.PartyContainerLootService;
import com.jvn.villagerretaliation.item.VillagerAttributeFilterData;
import com.jvn.villagerretaliation.item.VillagerFilterPolicy;
import com.jvn.villagerretaliation.item.VillagerItemFilterData;
import com.jvn.villagerretaliation.item.VillagerRecipeFilterData;
import com.jvn.villagerretaliation.item.VillagerRetaliationItems;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

public final class SettlementStorageService {
    private SettlementStorageService() { }

    public static SettlementStorageLinks effective(SettlementRecord town, UUID resident) {
        var settings = town.effectiveWorkerSettings(resident);
        return settings == null ? SettlementStorageLinks.AUTOMATIC : settings.storage();
    }

    public static String configure(ServerPlayer player, SettlementRecord town,
                                   com.jvn.villagerretaliation.network.SettlementManagementActionPayload.SaveStorage request) {
        if (!player.isAlive() || player.isSpectator() || SettlementSavedData.get(player.serverLevel()).readOnly()) return "player_unavailable";
        if (!town.permits(player.getUUID(), SettlementPermission.STAFF)) return "permission_staff";
        if (!town.permits(player.getUUID(), SettlementPermission.LOGISTICS)) return "permission_logistics";
        if (!town.dimension().equals(player.level().dimension().location())) return "wrong_dimension";
        var area = town.workplace(request.area());
        var plan = town.worksitePlans().get(request.area());
        if (area == null || area.type().role() == null || request.resident() == null && plan == null) return "worksite_missing";
        if (request.resident() != null && !request.area().equals(town.duties().get(request.resident()))) return "state_changed";
        var settings = request.resident() == null ? plan.settings()
                : SettlementWorkerConfigurationService.snapshot(player.serverLevel(), town, request.resident());
        boolean override = request.resident() == null || (settings.overrides() & SettlementStorageLinks.OVERRIDE) != 0;
        if (override != request.expectedOverride() || !settings.storage().equals(request.expected())) return "state_changed";
        var requested = request.links() != null ? request.links()
                : request.resident() == null || plan == null ? SettlementStorageLinks.AUTOMATIC : plan.settings().storage();
        String failure = validate(player, town, area.type().role(), settings.storage(), requested);
        if (!failure.isEmpty()) return failure;
        boolean changed;
        if (request.resident() == null) {
            changed = SettlementWorksiteService.configure(player, town, area.id(), new SettlementWorksitePlan(
                    settings.withStorage(requested), plan.desiredStaff(), plan.links(), plan.route()), false);
        } else {
            var updated = request.links() == null && plan != null ? settings.withOverrides(settings.overrides() & ~SettlementStorageLinks.OVERRIDE)
                    : settings.withStorage(requested);
            changed = SettlementWorkerConfigurationService.configure(player, town, request.resident(), area.id(), updated);
        }
        return changed ? "" : "state_changed";
    }

    public static com.jvn.villagerretaliation.network.SettlementManagementPayload.Storage snapshot(
            ServerPlayer player, SettlementRecord town, String query) {
        UUID scope;
        try { scope = UUID.fromString(query); }
        catch (IllegalArgumentException invalid) { return null; }
        UUID resident = town.duties().containsKey(scope) ? scope : null;
        var area = town.workplace(resident == null ? scope : town.duties().get(resident));
        if (area == null || area.type().role() == null) return null;
        var plan = town.worksitePlans().get(area.id());
        var settings = resident != null ? SettlementWorkerConfigurationService.snapshot(player.serverLevel(), town, resident)
                : plan == null ? SettlementWorkerSettings.capture(area.type().role(), new net.minecraft.nbt.CompoundTag()) : plan.settings();
        var links = settings.storage();
        var entries = new java.util.ArrayList<>(links.entries());
        if (links.automatic()) {
            var depots = resident == null ? SettlementWorksiteService.links(town, area.id()) : SettlementWorksiteService.links(town, area.id(), resident);
            for (var depot : town.depots().values()) {
                if (!depot.purpose().permitsRole(area.type().role())) continue;
                boolean courier = area.type().role() == HiredVillagerRole.COURIER;
                if ((courier || depot.purpose().providesSupplies()) && depots.permitsSupply(depot.id())) {
                    entries.add(new SettlementStorageLinks.Link(depot.position(), SettlementStorageLinks.Purpose.SUPPLY, 9 - depot.priority()));
                }
                if ((courier || depot.purpose().acceptsOutputs()) && depots.permitsOutput(depot.id())) {
                    entries.add(new SettlementStorageLinks.Link(depot.position(), SettlementStorageLinks.Purpose.OUTPUT, 9 - depot.priority()));
                }
            }
        }
        var containers = entries.stream().limit(SettlementStorageLinks.MAX_LINKS)
                .map(link -> status(player.serverLevel(), town, area.type().role(), link)).toList();
        return new com.jvn.villagerretaliation.network.SettlementManagementPayload.Storage(scope, area.name(), area.id(), resident,
                links, resident == null || (settings.overrides() & SettlementStorageLinks.OVERRIDE) != 0, containers);
    }

    private static com.jvn.villagerretaliation.network.SettlementManagementPayload.StorageStatus status(
            ServerLevel level, SettlementRecord town, HiredVillagerRole role, SettlementStorageLinks.Link link) {
        String status = AssignedStorageService.containerStatus(level, link.position(), link.purpose().id(), actor(level, town));
        if (!town.contains(link.position()) || AssignedStorageService.containerLoaded(level, link.position())
                && AssignedStorageService.containerPositions(level, link.position()).stream().anyMatch(position -> !town.contains(position))) status = "outside_territory";
        else if (AssignedStorageService.containerLoaded(level, link.position()) && !compatible(level, town, role, link) && !status.equals("missing")) status = "incompatible";
        String name = level.hasChunkAt(link.position()) ? level.getBlockState(link.position()).getBlock().getName().getString() : "Container";
        if (name.length() > 128) name = name.substring(0, 128);
        var filters = status.equals("available") || status.equals("full") ? frames(level, link.position()).stream()
                .map(frame -> new com.jvn.villagerretaliation.network.SettlementManagementPayload.StorageFilter(frame.getUUID(), frame.getItem())).toList()
                : java.util.List.<com.jvn.villagerretaliation.network.SettlementManagementPayload.StorageFilter>of();
        return new com.jvn.villagerretaliation.network.SettlementManagementPayload.StorageStatus(link, name, status, filters);
    }

    private static java.util.List<net.minecraft.world.entity.decoration.ItemFrame> frames(ServerLevel level, BlockPos position) {
        if (!AssignedStorageService.containerLoaded(level, position)) return java.util.List.of();
        var positions = AssignedStorageService.containerPositions(level, position);
        if (positions.stream().anyMatch(pos -> !com.jvn.villagerretaliation.inventory.ContainerFilterResolver.canObserveEntities(level, pos))) return java.util.List.of();
        return positions.stream().flatMap(pos -> level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
                new net.minecraft.world.phys.AABB(pos).inflate(1), frame -> frame.isAlive() && !frame.getItem().isEmpty()
                        && positions.contains(frame.getPos().relative(frame.getDirection().getOpposite()))).stream())
                .distinct().sorted(java.util.Comparator.comparing(net.minecraft.world.entity.Entity::getUUID)).limit(16).toList();
    }

    public static String configureFilter(ServerPlayer player, SettlementRecord town,
                                         com.jvn.villagerretaliation.network.SettlementManagementActionPayload.StorageFilter request) {
        return changeFilter(player, town, request.scope(), request.position(), request.frame(), request.expected(),
                updated -> VillagerFilterPolicy.applyChange(updated, request.field(), request.value()));
    }

    public static String saveFilter(ServerPlayer player, SettlementRecord town,
                                    com.jvn.villagerretaliation.network.SettlementManagementActionPayload.SaveStorageFilter request) {
        return changeFilter(player, town, request.scope(), request.position(), request.frame(), request.expected(), updated -> {
            var filter = request.filter();
            if (!filter.is(updated.getItem()) || !VillagerFilterPolicy.read(filter).valid()) return false;
            if (VillagerRetaliationItems.isItemFilter(filter)) {
                if (!com.jvn.villagerretaliation.item.VillagerFilterMatcher.rawMatchResult(player.level(), filter,
                        new ItemStack(net.minecraft.world.item.Items.STICK)).valid()) return false;
                VillagerItemFilterData.copyConfiguration(filter, updated);
            } else if (VillagerRetaliationItems.isAttributeFilter(filter)) {
                if (VillagerAttributeFilterData.read(filter).malformed()) return false;
                VillagerAttributeFilterData.copyConfiguration(filter, updated);
            } else if (VillagerRetaliationItems.isRecipeFilter(filter)) {
                if (VillagerRecipeFilterData.read(filter).state() != VillagerRecipeFilterData.StoredState.EMPTY
                        && !VillagerRecipeFilterData.resolve(player.level(), filter).valid()) return false;
                VillagerRecipeFilterData.copyConfiguration(filter, updated);
            } else return false;
            return true;
        });
    }

    private static String changeFilter(ServerPlayer player, SettlementRecord town, UUID scope, BlockPos position, UUID frameId,
                                       ItemStack expected, java.util.function.Predicate<ItemStack> change) {
        if (!player.isAlive() || player.isSpectator() || SettlementSavedData.get(player.serverLevel()).readOnly()) return "player_unavailable";
        if (!town.permits(player.getUUID(), SettlementPermission.LOGISTICS)) return "permission_logistics";
        if (!town.dimension().equals(player.level().dimension().location())) return "wrong_dimension";
        var snapshot = snapshot(player, town, scope.toString());
        if (snapshot == null || snapshot.containers().stream().noneMatch(container -> container.link().position().equals(position)
                && (container.status().equals("available") || container.status().equals("full")))) return "state_changed";
        var level = player.serverLevel();
        if (!town.contains(position) || !AssignedStorageService.containerLoaded(level, position)) return "storage_unloaded";
        if (!PartyContainerLootService.isAvailable(level, position, player)) return "storage_inaccessible";
        var frame = frames(level, position).stream().filter(candidate -> candidate.getUUID().equals(frameId)).findFirst().orElse(null);
        if (frame == null || !net.minecraft.world.item.ItemStack.matches(frame.getItem(), expected)) return "state_changed";
        var updated = frame.getItem().copy();
        if (!change.test(updated)) return "storage_filter";
        frame.setItem(updated);
        com.jvn.villagerretaliation.inventory.ContainerFilterResolver.invalidateFrame(level, frame);
        town.depots().values().stream().filter(depot -> depot.position().equals(position))
                .forEach(depot -> SettlementDepotService.observeRules(level, depot));
        return "";
    }

    public static String validate(ServerPlayer player, SettlementRecord town, HiredVillagerRole role,
                                  SettlementStorageLinks previous, SettlementStorageLinks requested) {
        if (requested.equals(previous)) return "";
        if (!town.permits(player.getUUID(), SettlementPermission.LOGISTICS)) return "permission_logistics";
        var level = player.serverLevel();
        if (!town.dimension().equals(level.dimension().location())) return "wrong_dimension";
        var keys = new HashSet<String>();
        for (var link : requested.entries()) {
            if (!town.contains(link.position()) || level.isOutsideBuildHeight(link.position())
                    || !level.getWorldBorder().isWithinBounds(link.position())) return "storage_outside";
            boolean retained = previous.entries().stream().anyMatch(old -> old.position().equals(link.position()) && old.purpose() == link.purpose());
            if (!AssignedStorageService.containerLoaded(level, link.position())) {
                if (retained) continue;
                return "storage_unloaded";
            }
            if (!compatible(level, town, role, link)) {
                if (retained) continue;
                return "storage_incompatible";
            }
            var positions = AssignedStorageService.containerPositions(level, link.position());
            if (positions.stream().anyMatch(position -> !town.contains(position))) return "storage_outside";
            for (var position : positions) {
                if (!keys.add(position.asLong() + ":" + link.purpose().name())) return "storage_duplicate";
            }
            if (!PartyContainerLootService.isAvailable(level, link.position(), player)) return "storage_inaccessible";
        }
        return "";
    }

    public static boolean compatible(ServerLevel level, SettlementRecord town, HiredVillagerRole role, SettlementStorageLinks.Link link) {
        if (!AssignedStorageService.containerLoaded(level, link.position())
                || !AssignedStorageService.isValidContainerForPurpose(level, link.position(), link.purpose().id())) return false;
        var block = level.getBlockEntity(link.position());
        if (block instanceof PaymentBoxBlockEntity || block instanceof SellBoxBlockEntity) return false;
        if (block instanceof SettlementDepotBlockEntity depotBlock) {
            var depot = depotBlock.inventory();
            if (depot == null || town.depots().get(depot.id()) != depot) return false;
            if (!depot.purpose().permitsRole(role)) return false;
            var area = town.areas().get(depot.area());
            if (area == null || !area.enabled()) return false;
            return role == HiredVillagerRole.COURIER || (!link.purpose().supplies() || depot.purpose().providesSupplies())
                    && (!link.purpose().outputs() || depot.purpose().acceptsOutputs());
        }
        return true;
    }

    public static boolean permitted(ServerLevel level, SettlementRecord town, HiredVillagerRole role, SettlementStorageLinks.Link link) {
        return town.dimension().equals(level.dimension().location()) && town.contains(link.position())
                && level.hasChunkAt(link.position()) && compatible(level, town, role, link)
                && AssignedStorageService.containerPositions(level, link.position()).stream().allMatch(town::contains)
                && PartyContainerLootService.isAvailable(level, link.position(), actor(level, town));
    }

    public static ServerPlayer actor(ServerLevel level, SettlementRecord town) {
        var online = level.getServer().getPlayerList().getPlayer(town.leader());
        return online != null && online.serverLevel() == level && online.isAlive() ? online
                : FakePlayerFactory.get(level, new GameProfile(town.leader(), "CivicStorage"));
    }

    public static SettlementWorksiteLinks depotLinks(SettlementRecord town, SettlementWorksiteLinks automatic, SettlementStorageLinks storage) {
        if (storage.automatic()) return automatic;
        var supplies = new HashSet<UUID>();
        var outputs = new HashSet<UUID>();
        for (var depot : town.depots().values()) {
            for (var link : storage.entries()) {
                if (!link.position().equals(depot.position())) continue;
                if (link.purpose().supplies()) supplies.add(depot.id());
                if (link.purpose().outputs()) outputs.add(depot.id());
            }
        }
        return new SettlementWorksiteLinks(supplies, outputs, automatic.stations(), true);
    }
}
