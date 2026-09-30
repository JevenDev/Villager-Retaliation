package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementArea;
import com.jvn.villagerretaliation.settlement.SettlementBrewingOrder;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementDepotPurpose;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementTradeOrder;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSetting;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSettings;
import com.jvn.villagerretaliation.settlement.SettlementWorksitePlan;
import com.jvn.villagerretaliation.settlement.SettlementEquipmentPlan;
import com.jvn.villagerretaliation.settlement.SettlementStorageLinks;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementManagementActionPayload(UUID town, Command command, UUID request) implements CustomPacketPayload {
    public static final Type<SettlementManagementActionPayload> TYPE = VillagerPayloads.type("settlement_management_action");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementManagementActionPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementManagementActionPayload::encode, SettlementManagementActionPayload::decode);

    public SettlementManagementActionPayload(UUID town, Command command) {
        this(town, command, UUID.randomUUID());
    }

    public SettlementManagementActionPayload {
        Objects.requireNonNull(request);
        Objects.requireNonNull(town);
        Objects.requireNonNull(command);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementManagementActionPayload payload) {
        buffer.writeUUID(payload.town());
        buffer.writeUUID(payload.request());
        switch (payload.command()) {
            case SaveStorageFilter filter -> {
                buffer.writeEnum(Kind.SAVE_STORAGE_FILTER);
                buffer.writeUUID(filter.scope());
                buffer.writeBlockPos(filter.position());
                buffer.writeUUID(filter.frame());
                net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buffer, filter.expected());
                net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buffer, filter.filter());
            }
            case StorageFilter filter -> {
                buffer.writeEnum(Kind.STORAGE_FILTER);
                buffer.writeUUID(filter.scope());
                buffer.writeBlockPos(filter.position());
                buffer.writeUUID(filter.frame());
                net.minecraft.world.item.ItemStack.STREAM_CODEC.encode(buffer, filter.expected());
                buffer.writeEnum(filter.field());
                buffer.writeVarInt(filter.value());
            }
            case SaveStorage storage -> {
                buffer.writeEnum(Kind.SAVE_STORAGE);
                buffer.writeUUID(storage.area());
                buffer.writeBoolean(storage.resident() != null);
                if (storage.resident() != null) buffer.writeUUID(storage.resident());
                buffer.writeBoolean(storage.expectedOverride());
                storage.expected().write(buffer);
                buffer.writeBoolean(storage.links() != null);
                if (storage.links() != null) storage.links().write(buffer);
            }
            case SaveRoute route -> {
                buffer.writeEnum(Kind.SAVE_ROUTE);
                buffer.writeUUID(route.area());
                buffer.writeBoolean(route.resident() != null);
                if (route.resident() != null) buffer.writeUUID(route.resident());
                writeGuardOrder(buffer, route.expected());
                writeGuardOrder(buffer, route.route());
            }
            case ClaimPlot plot -> {
                buffer.writeEnum(Kind.CLAIM_PLOT);
                buffer.writeInt(plot.x());
                buffer.writeInt(plot.z());
                buffer.writeBoolean(plot.free());
                buffer.writeVarLong(plot.revision());
                buffer.writeVarLong(plot.price());
            }
            case Home home -> {
                buffer.writeEnum(Kind.HOME);
                buffer.writeUUID(home.resident());
                buffer.writeBlockPos(home.bed());
                buffer.writeBoolean(home.assign());
            }
            case Management management -> {
                buffer.writeEnum(Kind.MANAGEMENT);
                buffer.writeBoolean(management.manual());
            }
            case Advance ignored -> buffer.writeEnum(Kind.ADVANCE);
            case View ignored -> buffer.writeEnum(Kind.VIEW);
            case DraftBlueprint draft -> {
                buffer.writeEnum(Kind.DRAFT_BLUEPRINT);
                buffer.writeResourceLocation(draft.structure());
            }
            case PlaceBlueprint place -> {
                buffer.writeEnum(Kind.PLACE_BLUEPRINT);
                buffer.writeUUID(place.blueprint());
                buffer.writeBlockPos(place.anchor());
                buffer.writeEnum(place.rotation());
            }
            case CommissionBuilding build -> {
                buffer.writeEnum(Kind.COMMISSION_BUILDING);
                buffer.writeUUID(build.request());
                buffer.writeResourceLocation(build.structure());
                buffer.writeBlockPos(build.anchor());
                buffer.writeEnum(build.rotation());
                buffer.writeUUID(build.builder());
            }
            case StartProject start -> {
                buffer.writeEnum(Kind.START_PROJECT);
                buffer.writeUUID(start.resident());
                buffer.writeUUID(start.blueprint());
            }
            case ConfigureProject project -> {
                buffer.writeEnum(Kind.CONFIGURE_PROJECT);
                buffer.writeUUID(project.project());
                buffer.writeVarLong(project.revision());
                buffer.writeVarInt(project.priority());
                buffer.writeVarInt(project.assistants().size());
                project.assistants().forEach(buffer::writeUUID);
            }
            case CancelProject cancel -> {
                buffer.writeEnum(Kind.CANCEL_PROJECT);
                buffer.writeUUID(cancel.project());
            }
            case Brew brew -> {
                buffer.writeEnum(Kind.BREW);
                buffer.writeUUID(brew.resident());
                buffer.writeUUID(brew.area());
                writeBrewingOrder(buffer, brew.order());
            }
            case WorkerEnabled enabled -> {
                buffer.writeEnum(Kind.WORKER_ENABLED);
                buffer.writeUUID(enabled.resident());
                buffer.writeUUID(enabled.area());
                buffer.writeBoolean(enabled.enabled());
            }
            case SaveEquipment save -> {
                buffer.writeEnum(Kind.SAVE_EQUIPMENT);
                buffer.writeUUID(save.area());
                buffer.writeLong(save.revision());
                buffer.writeBoolean(save.configuration() != null);
                if (save.configuration() != null) writeEquipmentConfiguration(buffer, save.configuration());
            }
            case SaveWorksite save -> {
                buffer.writeEnum(Kind.SAVE_WORKSITE);
                buffer.writeUUID(save.area());
                writeWorksitePlan(buffer, save.plan());
                buffer.writeBoolean(save.adopt());
            }
            case ResetWorker reset -> {
                buffer.writeEnum(Kind.RESET_WORKER);
                buffer.writeUUID(reset.resident());
                buffer.writeUUID(reset.area());
                buffer.writeInt(reset.mask());
            }
            case FillStaffing fill -> {
                buffer.writeEnum(Kind.FILL_STAFFING);
                buffer.writeUUID(fill.area());
            }
            case SaveWorker save -> {
                buffer.writeEnum(Kind.SAVE_WORKER);
                buffer.writeUUID(save.resident());
                buffer.writeUUID(save.area());
                writeWorkerSettings(buffer, save.settings());
            }
            case Guard guard -> {
                buffer.writeEnum(Kind.GUARD);
                buffer.writeUUID(guard.resident());
                writeGuardOrder(buffer, guard.order());
            }
            case Assign assign -> {
                buffer.writeEnum(Kind.ASSIGN);
                buffer.writeUUID(assign.resident());
                buffer.writeUUID(assign.area());
            }
            case AutomaticStaffing automatic -> {
                buffer.writeEnum(Kind.AUTOMATIC_STAFFING);
                buffer.writeBoolean(automatic.resident() != null);
                if (automatic.resident() != null) buffer.writeUUID(automatic.resident());
            }
            case Release release -> {
                buffer.writeEnum(Kind.RELEASE);
                buffer.writeUUID(release.resident());
            }
            case Applicant applicant -> {
                buffer.writeEnum(Kind.APPLICANT);
                buffer.writeUUID(applicant.id());
                buffer.writeBoolean(applicant.approve());
            }
            case SaveArea save -> {
                buffer.writeEnum(Kind.SAVE_AREA);
                SettlementArea area = save.area();
                buffer.writeUUID(area.id());
                buffer.writeEnum(area.type());
                buffer.writeUtf(area.name(), SettlementArea.MAX_NAME_LENGTH);
                buffer.writeBlockPos(area.min());
                buffer.writeBlockPos(area.max());
                buffer.writeVarInt(area.priority());
                buffer.writeBoolean(area.enabled());
            }
            case RemoveArea remove -> {
                buffer.writeEnum(Kind.REMOVE_AREA);
                buffer.writeUUID(remove.id());
            }
            case ConfigureDepot depot -> {
                buffer.writeEnum(Kind.CONFIGURE_DEPOT);
                buffer.writeUUID(depot.id());
                buffer.writeEnum(depot.purpose());
                buffer.writeVarInt(depot.priority());
            }
            case StockTarget target -> {
                buffer.writeEnum(Kind.STOCK_TARGET);
                buffer.writeUUID(target.depot());
                buffer.writeVarInt(target.count());
            }
            case Treasury treasury -> {
                buffer.writeEnum(Kind.TREASURY);
                buffer.writeBoolean(treasury.deposit());
                buffer.writeVarInt(treasury.amount());
            }
            case Budget budget -> {
                buffer.writeEnum(Kind.BUDGET);
                budget.reserve().write(buffer);
                budget.daily().write(buffer);
            }
            case SaveOrder save -> {
                buffer.writeEnum(Kind.SAVE_ORDER);
                var order = save.order();
                buffer.writeUUID(order.id());
                buffer.writeEnum(order.direction());
                buffer.writeResourceLocation(order.item());
                buffer.writeVarInt(order.stockTarget());
                order.unitPriceLimit().write(buffer);
                order.dailyBudget().write(buffer);
                buffer.writeBoolean(order.enabled());
            }
            case RemoveOrder remove -> {
                buffer.writeEnum(Kind.REMOVE_ORDER);
                buffer.writeUUID(remove.id());
            }
            case Permissions permissions -> {
                buffer.writeEnum(Kind.PERMISSIONS);
                buffer.writeUUID(permissions.player());
                int bits = 0;
                for (SettlementPermission permission : permissions.grants()) {
                    bits |= 1 << permission.ordinal();
                }
                buffer.writeVarInt(bits);
            }
            case Transfer transfer -> {
                buffer.writeEnum(Kind.TRANSFER);
                buffer.writeUUID(transfer.successor());
            }
            case Inventory inventory -> {
                buffer.writeEnum(Kind.INVENTORY);
                buffer.writeUUID(inventory.resident());
            }
        }
    }

    private static SettlementManagementActionPayload decode(RegistryFriendlyByteBuf buffer) {
        UUID town = buffer.readUUID();
        UUID request = buffer.readUUID();
        Command command = switch (buffer.readEnum(Kind.class)) {
            case CLAIM_PLOT -> new ClaimPlot(buffer.readInt(), buffer.readInt(), buffer.readBoolean(), buffer.readVarLong(), buffer.readVarLong());
            case HOME -> new Home(buffer.readUUID(), buffer.readBlockPos(), buffer.readBoolean());
            case MANAGEMENT -> new Management(buffer.readBoolean());
            case ADVANCE -> new Advance();
            case VIEW -> new View();
            case GUARD -> new Guard(buffer.readUUID(), readGuardOrder(buffer));
            case BREW -> new Brew(buffer.readUUID(), buffer.readUUID(), readBrewingOrder(buffer));
            case SAVE_EQUIPMENT -> new SaveEquipment(buffer.readUUID(), buffer.readLong(),
                    buffer.readBoolean() ? readEquipmentConfiguration(buffer) : null);
            case SAVE_WORKSITE -> new SaveWorksite(buffer.readUUID(), readWorksitePlan(buffer), buffer.readBoolean());
            case SAVE_STORAGE_FILTER -> new SaveStorageFilter(buffer.readUUID(), buffer.readBlockPos(), buffer.readUUID(),
                    net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buffer), net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buffer));
            case STORAGE_FILTER -> new StorageFilter(buffer.readUUID(), buffer.readBlockPos(), buffer.readUUID(),
                    net.minecraft.world.item.ItemStack.STREAM_CODEC.decode(buffer),
                    buffer.readEnum(com.jvn.villagerretaliation.item.VillagerFilterPolicy.PolicyField.class), buffer.readVarInt());
            case SAVE_STORAGE -> new SaveStorage(buffer.readUUID(), buffer.readBoolean() ? buffer.readUUID() : null,
                    buffer.readBoolean(), SettlementStorageLinks.read(buffer),
                    buffer.readBoolean() ? SettlementStorageLinks.read(buffer) : null);
            case SAVE_ROUTE -> new SaveRoute(buffer.readUUID(), buffer.readBoolean() ? buffer.readUUID() : null,
                    readGuardOrder(buffer), readGuardOrder(buffer));
            case RESET_WORKER -> new ResetWorker(buffer.readUUID(), buffer.readUUID(), buffer.readInt());
            case FILL_STAFFING -> new FillStaffing(buffer.readUUID());
            case SAVE_WORKER -> new SaveWorker(buffer.readUUID(), buffer.readUUID(), readWorkerSettings(buffer));
            case WORKER_ENABLED -> new WorkerEnabled(buffer.readUUID(), buffer.readUUID(), buffer.readBoolean());
            case DRAFT_BLUEPRINT -> new DraftBlueprint(buffer.readResourceLocation());
            case PLACE_BLUEPRINT -> new PlaceBlueprint(buffer.readUUID(), buffer.readBlockPos(), buffer.readEnum(net.minecraft.world.level.block.Rotation.class));
            case COMMISSION_BUILDING -> new CommissionBuilding(buffer.readUUID(), buffer.readResourceLocation(), buffer.readBlockPos(),
                    buffer.readEnum(net.minecraft.world.level.block.Rotation.class), buffer.readUUID());
            case START_PROJECT -> new StartProject(buffer.readUUID(), buffer.readUUID());
            case CONFIGURE_PROJECT -> {
                var project = buffer.readUUID();
                long revision = buffer.readVarLong();
                int priority = buffer.readVarInt();
                int count = VillagerPayloads.readCollectionSize(buffer, 15, "construction assistants");
                var assistants = new java.util.ArrayList<UUID>(count);
                for (int index = 0; index < count; index++) assistants.add(buffer.readUUID());
                yield new ConfigureProject(project, revision, priority, assistants);
            }
            case CANCEL_PROJECT -> new CancelProject(buffer.readUUID());
            case ASSIGN -> new Assign(buffer.readUUID(), buffer.readUUID());
            case AUTOMATIC_STAFFING -> new AutomaticStaffing(buffer.readBoolean() ? buffer.readUUID() : null);
            case RELEASE -> new Release(buffer.readUUID());
            case APPLICANT -> new Applicant(buffer.readUUID(), buffer.readBoolean());
            case SAVE_AREA -> new SaveArea(new SettlementArea(buffer.readUUID(), buffer.readEnum(SettlementAreaType.class),
                    buffer.readUtf(SettlementArea.MAX_NAME_LENGTH), buffer.readBlockPos(), buffer.readBlockPos(),
                    buffer.readVarInt(), buffer.readBoolean()));
            case REMOVE_AREA -> new RemoveArea(buffer.readUUID());
            case CONFIGURE_DEPOT -> new ConfigureDepot(buffer.readUUID(), buffer.readEnum(SettlementDepotPurpose.class), buffer.readVarInt());
            case STOCK_TARGET -> new StockTarget(buffer.readUUID(), buffer.readVarInt());
            case TREASURY -> new Treasury(buffer.readBoolean(), buffer.readVarInt());
            case BUDGET -> new Budget(CurrencyAmount.read(buffer), CurrencyAmount.read(buffer));
            case SAVE_ORDER -> new SaveOrder(new SettlementTradeOrder(buffer.readUUID(), buffer.readEnum(SettlementTradeOrder.Direction.class),
                    buffer.readResourceLocation(), buffer.readVarInt(), CurrencyAmount.read(buffer), CurrencyAmount.read(buffer), buffer.readBoolean()));
            case REMOVE_ORDER -> new RemoveOrder(buffer.readUUID());
            case PERMISSIONS -> {
                UUID player = buffer.readUUID();
                int bits = buffer.readVarInt();
                Set<SettlementPermission> permissions = EnumSet.noneOf(SettlementPermission.class);
                for (SettlementPermission permission : SettlementPermission.values()) {
                    if ((bits & 1 << permission.ordinal()) != 0) {
                        permissions.add(permission);
                    }
                }
                yield new Permissions(player, permissions);
            }
            case TRANSFER -> new Transfer(buffer.readUUID());
            case INVENTORY -> new Inventory(buffer.readUUID());
        };
        return new SettlementManagementActionPayload(town, command, request);
    }

    static void writeEquipmentPlan(RegistryFriendlyByteBuf buffer, SettlementEquipmentPlan plan) {
        buffer.writeBoolean(plan != null);
        if (plan == null) return;
        buffer.writeUUID(plan.authority());
        buffer.writeLong(plan.revision());
        buffer.writeUtf(plan.blocker(), 128);
        writeEquipmentConfiguration(buffer, plan.configuration());
    }

    static SettlementEquipmentPlan readEquipmentPlan(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) return null;
        var authority = buffer.readUUID();
        long revision = buffer.readLong();
        String blocker = buffer.readUtf(128);
        return new SettlementEquipmentPlan(readEquipmentConfiguration(buffer), authority, revision, blocker);
    }

    private static void writeEquipmentConfiguration(RegistryFriendlyByteBuf buffer, SettlementEquipmentPlan.Configuration configuration) {
        buffer.writeVarInt(configuration.priority());
        buffer.writeBoolean(configuration.enabled());
        buffer.writeVarInt(configuration.selections().size());
        for (var selection : configuration.selections()) {
            buffer.writeEnum(selection.kind());
            buffer.writeVarInt(selection.spares());
            buffer.writeBoolean(selection.equip());
            buffer.writeBoolean(selection.protectedMinimum());
        }
    }

    private static SettlementEquipmentPlan.Configuration readEquipmentConfiguration(RegistryFriendlyByteBuf buffer) {
        int priority = buffer.readVarInt();
        boolean enabled = buffer.readBoolean();
        int count = VillagerPayloads.readCollectionSize(buffer, SettlementEquipmentPlan.MAX_SELECTIONS, "equipment selections");
        var selections = new java.util.ArrayList<SettlementEquipmentPlan.Selection>(count);
        for (int index = 0; index < count; index++)
            selections.add(new SettlementEquipmentPlan.Selection(buffer.readEnum(
                    com.jvn.villagerretaliation.interaction.work.HiredEquipmentNeed.Kind.class), buffer.readVarInt(),
                    buffer.readBoolean(), buffer.readBoolean()));
        return new SettlementEquipmentPlan.Configuration(selections, priority, enabled);
    }

    static void writeWorksitePlan(RegistryFriendlyByteBuf buffer, SettlementWorksitePlan plan) {
        buffer.writeBoolean(plan != null);
        if (plan != null) {
            writeWorkerSettings(buffer, plan.settings());
            buffer.writeVarInt(plan.desiredStaff());
            writeWorksiteLinks(buffer, plan.links());
            writeGuardOrder(buffer, plan.route());
        }
    }

    static SettlementWorksitePlan readWorksitePlan(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new SettlementWorksitePlan(readWorkerSettings(buffer), buffer.readVarInt(), readWorksiteLinks(buffer), readGuardOrder(buffer)) : null;
    }

    private static void writeWorksiteLinks(RegistryFriendlyByteBuf buffer, com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks links) {
        buffer.writeBoolean(links.explicitDepots());
        buffer.writeVarInt(links.supplies().size());
        links.supplies().stream().sorted().forEach(buffer::writeUUID);
        buffer.writeVarInt(links.outputs().size());
        links.outputs().stream().sorted().forEach(buffer::writeUUID);
        buffer.writeVarInt(links.stations().size());
        links.stations().stream().sorted().forEach(buffer::writeBlockPos);
    }

    private static com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks readWorksiteLinks(RegistryFriendlyByteBuf buffer) {
        boolean explicit = buffer.readBoolean();
        Set<UUID> supplies = readDepotLinks(buffer), outputs = readDepotLinks(buffer);
        int count = VillagerPayloads.readCollectionSize(buffer,
                com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks.MAX_LINKS, "worksite stations");
        var stations = new java.util.HashSet<net.minecraft.core.BlockPos>();
        for (int index = 0; index < count; index++) {
            if (!stations.add(buffer.readBlockPos())) throw new IllegalArgumentException("Duplicate worksite station");
        }
        return new com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks(supplies, outputs, stations, explicit);
    }

    private static Set<UUID> readDepotLinks(RegistryFriendlyByteBuf buffer) {
        int count = VillagerPayloads.readCollectionSize(buffer,
                com.jvn.villagerretaliation.settlement.SettlementWorksiteLinks.MAX_LINKS, "worksite depots");
        var links = new java.util.HashSet<UUID>();
        for (int index = 0; index < count; index++) {
            if (!links.add(buffer.readUUID())) throw new IllegalArgumentException("Duplicate worksite depot");
        }
        return links;
    }

    static void writeWorkerSettings(RegistryFriendlyByteBuf buffer, SettlementWorkerSettings settings) {
        buffer.writeEnum(settings.role());
        for (var setting : SettlementWorkerSetting.values()) {
            if (setting.supports(settings.role())) {
                buffer.writeVarInt(settings.options().get(setting));
            }
        }
        buffer.writeVarInt(settings.logs().size());
        settings.logs().stream().sorted().forEach(buffer::writeResourceLocation);
        buffer.writeVarInt(settings.animals().size());
        settings.animals().stream().sorted().forEach(buffer::writeResourceLocation);
        buffer.writeInt(settings.overrides());
        buffer.writeVarInt(settings.fishingSpots().positions().size());
        settings.fishingSpots().positions().forEach(buffer::writeBlockPos);
        settings.storage().write(buffer);
    }

    static SettlementWorkerSettings readWorkerSettings(RegistryFriendlyByteBuf buffer) {
        var role = buffer.readEnum(HiredVillagerRole.class);
        var options = new java.util.EnumMap<SettlementWorkerSetting, Integer>(SettlementWorkerSetting.class);
        for (var setting : SettlementWorkerSetting.values()) {
            if (setting.supports(role)) {
                options.put(setting, buffer.readVarInt());
            }
        }
        var logs = readWorkerFilters(buffer);
        var animals = readWorkerFilters(buffer);
        int overrides = buffer.readInt();
        int count = VillagerPayloads.readCollectionSize(buffer,
                com.jvn.villagerretaliation.interaction.work.HiredFishingSpots.MAX_SPOTS, "fishing spot priorities");
        var positions = new java.util.ArrayList<net.minecraft.core.BlockPos>();
        for (int index = 0; index < count; index++) positions.add(buffer.readBlockPos());
        return new SettlementWorkerSettings(role, options, logs, animals, overrides,
                new com.jvn.villagerretaliation.interaction.work.HiredFishingSpots(positions),
                com.jvn.villagerretaliation.settlement.SettlementStorageLinks.read(buffer));
    }

    private static Set<net.minecraft.resources.ResourceLocation> readWorkerFilters(RegistryFriendlyByteBuf buffer) {
        int count = VillagerPayloads.readCollectionSize(buffer, SettlementWorkerSettings.MAX_FILTERS, "settlement worker filters");
        var filters = new java.util.HashSet<net.minecraft.resources.ResourceLocation>();
        for (int index = 0; index < count; index++) {
            if (!filters.add(buffer.readResourceLocation())) {
                throw new IllegalArgumentException("Duplicate worker filter");
            }
        }
        return filters;
    }

    static void writeGuardOrder(RegistryFriendlyByteBuf buffer, SettlementGuardOrder order) {
        buffer.writeBoolean(order != null);
        if (order != null) {
            buffer.writeEnum(order.mode());
            buffer.writeVarInt(order.points().size());
            order.points().forEach(buffer::writeBlockPos);
            buffer.writeBoolean(order.loop());
            buffer.writeVarInt(order.branches().size());
            order.branches().forEach(branch -> { buffer.writeBlockPos(branch.anchor()); buffer.writeBlockPos(branch.end()); });
        }
    }

    static SettlementGuardOrder readGuardOrder(RegistryFriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return null;
        }
        var mode = buffer.readEnum(SettlementGuardOrder.Mode.class);
        int count = VillagerPayloads.readCollectionSize(buffer, SettlementGuardOrder.MAX_POINTS, "settlement guard points");
        var points = new java.util.ArrayList<net.minecraft.core.BlockPos>(count);
        for (int index = 0; index < count; index++) {
            points.add(buffer.readBlockPos());
        }
        boolean loop = buffer.readBoolean();
        int branches = VillagerPayloads.readCollectionSize(buffer, com.jvn.villagerretaliation.interaction.HiredRoute.MAX_BRANCHES, "route branches");
        var branchList = new java.util.ArrayList<com.jvn.villagerretaliation.interaction.HiredRoute.Branch>();
        for (int index = 0; index < branches; index++) branchList.add(new com.jvn.villagerretaliation.interaction.HiredRoute.Branch(buffer.readBlockPos(), buffer.readBlockPos()));
        return new SettlementGuardOrder(mode, points, loop, branchList);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void writeBrewingOrder(RegistryFriendlyByteBuf buffer, SettlementBrewingOrder order) {
        buffer.writeBoolean(order != null);
        if (order != null) {
            buffer.writeResourceLocation(order.item());
            buffer.writeResourceLocation(order.potion());
            buffer.writeVarInt(order.amount());
            buffer.writeBoolean(order.continuous());
        }
    }

    public static SettlementBrewingOrder readBrewingOrder(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? new SettlementBrewingOrder(buffer.readResourceLocation(), buffer.readResourceLocation(),
                buffer.readVarInt(), buffer.readBoolean()) : null;
    }

    private enum Kind {
        ADVANCE, ASSIGN, RELEASE, APPLICANT, SAVE_AREA, REMOVE_AREA, CONFIGURE_DEPOT, STOCK_TARGET,
        TREASURY, BUDGET, SAVE_ORDER, REMOVE_ORDER, PERMISSIONS, TRANSFER, INVENTORY, VIEW, GUARD, SAVE_WORKER, START_PROJECT, CANCEL_PROJECT, DRAFT_BLUEPRINT, PLACE_BLUEPRINT, BREW, HOME, COMMISSION_BUILDING, WORKER_ENABLED, MANAGEMENT, CLAIM_PLOT, AUTOMATIC_STAFFING, SAVE_WORKSITE, RESET_WORKER, FILL_STAFFING, CONFIGURE_PROJECT, SAVE_ROUTE, SAVE_STORAGE, STORAGE_FILTER, SAVE_STORAGE_FILTER, SAVE_EQUIPMENT
    }

    public sealed interface Command permits Advance, Assign, Release, Applicant, SaveArea, RemoveArea,
            ConfigureDepot, StockTarget, Treasury, Budget, SaveOrder, RemoveOrder, Permissions, Transfer, Inventory, View, Guard, SaveWorker, StartProject, CancelProject, DraftBlueprint, PlaceBlueprint, Brew, Home, CommissionBuilding, WorkerEnabled, Management, ClaimPlot, AutomaticStaffing, SaveWorksite, ResetWorker, FillStaffing, ConfigureProject, SaveRoute, SaveStorage, StorageFilter, SaveStorageFilter, SaveEquipment {
    }

    public record SaveRoute(UUID area, UUID resident, SettlementGuardOrder expected, SettlementGuardOrder route) implements Command {
        public SaveRoute { Objects.requireNonNull(area); }
    }

    public record SaveStorageFilter(UUID scope, net.minecraft.core.BlockPos position, UUID frame,
                                    net.minecraft.world.item.ItemStack expected, net.minecraft.world.item.ItemStack filter) implements Command {
        public SaveStorageFilter {
            Objects.requireNonNull(scope);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(frame);
            expected = expected.copyWithCount(1);
            filter = filter.copyWithCount(1);
        }
    }

    public record StorageFilter(UUID scope, net.minecraft.core.BlockPos position, UUID frame,
                                net.minecraft.world.item.ItemStack expected,
                                com.jvn.villagerretaliation.item.VillagerFilterPolicy.PolicyField field, int value) implements Command {
        public StorageFilter {
            Objects.requireNonNull(scope);
            position = Objects.requireNonNull(position).immutable();
            Objects.requireNonNull(frame);
            expected = expected.copyWithCount(1);
            Objects.requireNonNull(field);
        }
    }

    public record SaveStorage(UUID area, UUID resident, boolean expectedOverride, SettlementStorageLinks expected,
                              SettlementStorageLinks links) implements Command {
        public SaveStorage {
            Objects.requireNonNull(area);
            Objects.requireNonNull(expected);
        }
    }

    public record SaveEquipment(UUID area, long revision, SettlementEquipmentPlan.Configuration configuration) implements Command {
        public SaveEquipment {
            Objects.requireNonNull(area);
            if (revision < -1 || revision == Long.MAX_VALUE) throw new IllegalArgumentException("Invalid equipment revision");
        }
    }

    public record SaveWorksite(UUID area, SettlementWorksitePlan plan, boolean adopt) implements Command {
        public SaveWorksite {
            Objects.requireNonNull(area);
            Objects.requireNonNull(plan);
        }
    }

    public record ResetWorker(UUID resident, UUID area, int mask) implements Command {
        public ResetWorker {
            Objects.requireNonNull(resident);
            Objects.requireNonNull(area);
        }
    }

    public record FillStaffing(UUID area) implements Command {
        public FillStaffing {
            Objects.requireNonNull(area);
        }
    }

    public record Home(UUID resident, net.minecraft.core.BlockPos bed, boolean assign) implements Command {
        public Home {
            Objects.requireNonNull(resident);
            Objects.requireNonNull(bed);
            bed = bed.immutable();
        }
    }

    public record Brew(UUID resident, UUID area, SettlementBrewingOrder order) implements Command {
        public Brew {
            Objects.requireNonNull(resident);
            Objects.requireNonNull(area);
        }
    }

    public record ClaimPlot(int x, int z, boolean free, long revision, long price) implements Command {
        public ClaimPlot {
            if (!com.jvn.villagerretaliation.settlement.SettlementTerritory.validChunk(x, z) || revision < 0 || price < 0) {
                throw new IllegalArgumentException("Invalid plot claim");
            }
        }
    }

    public record Management(boolean manual) implements Command {
    }

    public record Advance() implements Command {
    }

    public record View() implements Command {
    }

    public record DraftBlueprint(net.minecraft.resources.ResourceLocation structure) implements Command {
        public DraftBlueprint {
            Objects.requireNonNull(structure);
        }
    }

    public record PlaceBlueprint(UUID blueprint, net.minecraft.core.BlockPos anchor, net.minecraft.world.level.block.Rotation rotation) implements Command {
        public PlaceBlueprint {
            Objects.requireNonNull(blueprint);
            Objects.requireNonNull(anchor);
            Objects.requireNonNull(rotation);
            anchor = anchor.immutable();
        }
    }

    public record CommissionBuilding(UUID request, net.minecraft.resources.ResourceLocation structure, net.minecraft.core.BlockPos anchor,
                                     net.minecraft.world.level.block.Rotation rotation, UUID builder) implements Command {
        public CommissionBuilding {
            Objects.requireNonNull(request);
            Objects.requireNonNull(structure);
            Objects.requireNonNull(anchor);
            Objects.requireNonNull(rotation);
            Objects.requireNonNull(builder);
            anchor = anchor.immutable();
        }
    }

    public record StartProject(UUID resident, UUID blueprint) implements Command {
        public StartProject {
            Objects.requireNonNull(resident);
            Objects.requireNonNull(blueprint);
        }
    }

    public record ConfigureProject(UUID project, long revision, int priority, List<UUID> assistants) implements Command {
        public ConfigureProject {
            Objects.requireNonNull(project);
            assistants = List.copyOf(assistants);
            if (revision < 0L || priority < 0 || priority > 9 || assistants.size() > 15
                    || new java.util.HashSet<>(assistants).size() != assistants.size()) throw new IllegalArgumentException("Invalid construction crew command");
        }
    }

    public record CancelProject(UUID project) implements Command {
        public CancelProject {
            Objects.requireNonNull(project);
        }
    }

    public record WorkerEnabled(UUID resident, UUID area, boolean enabled) implements Command {
        public WorkerEnabled {
            Objects.requireNonNull(resident);
            Objects.requireNonNull(area);
        }
    }

    public record SaveWorker(UUID resident, UUID area, SettlementWorkerSettings settings) implements Command {
        public SaveWorker {
            Objects.requireNonNull(resident);
            Objects.requireNonNull(area);
            Objects.requireNonNull(settings);
        }
    }

    public record Guard(UUID resident, SettlementGuardOrder order) implements Command {
    }

    public record Assign(UUID resident, UUID area) implements Command {
    }

    public record AutomaticStaffing(UUID resident) implements Command {
    }

    public record Release(UUID resident) implements Command {
    }

    public record Applicant(UUID id, boolean approve) implements Command {
    }

    public record SaveArea(SettlementArea area) implements Command {
    }

    public record RemoveArea(UUID id) implements Command {
    }

    public record ConfigureDepot(UUID id, SettlementDepotPurpose purpose, int priority) implements Command {
    }

    public record StockTarget(UUID depot, int count) implements Command {
    }

    public record Treasury(boolean deposit, int amount) implements Command {
    }

    public record Budget(CurrencyAmount reserve, CurrencyAmount daily) implements Command {
    }

    public record SaveOrder(SettlementTradeOrder order) implements Command {
    }

    public record RemoveOrder(UUID id) implements Command {
    }

    public record Permissions(UUID player, Set<SettlementPermission> grants) implements Command {
        public Permissions {
            grants = Set.copyOf(grants);
        }
    }

    public record Transfer(UUID successor) implements Command {
    }

    public record Inventory(UUID resident) implements Command {
    }
}
