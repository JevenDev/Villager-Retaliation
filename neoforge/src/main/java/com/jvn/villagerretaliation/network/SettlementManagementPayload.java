package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementEvent;
import com.jvn.villagerretaliation.settlement.SettlementDepotPurpose;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSettings;
import com.jvn.villagerretaliation.settlement.SettlementBrewingOrder;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import com.jvn.villagerretaliation.settlement.SettlementProgressionService;
import com.jvn.villagerretaliation.settlement.SettlementStage;
import com.jvn.villagerretaliation.settlement.SettlementTradeOrder;
import com.jvn.villagerretaliation.settlement.SettlementStorageLinks;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SettlementManagementPayload(ResourceLocation dimension, Header header, Page page, int index, int total,
                                          String query, int requestId, boolean openScreen, List<Entry> entries)
        implements CustomPacketPayload {
    public static final Type<SettlementManagementPayload> TYPE = VillagerPayloads.type("settlement_management");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementManagementPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementManagementPayload::encode, SettlementManagementPayload::decode);

    public SettlementManagementPayload {
        if (dimension == null || page == null || index < 0 || total < 0 || requestId < 0 || query == null || query.length() > 64
                || entries.size() > SettlementManagementRequestPayload.PAGE_SIZE || header == null && page != Page.TOWNS) {
            throw new IllegalArgumentException("Invalid settlement management page");
        }
        entries = List.copyOf(entries);
        for (Entry entry : entries) {
            boolean valid = switch (page) {
                case TOWNS -> entry instanceof Town;
                case RESIDENTS -> entry instanceof Resident;
                case AREAS -> entry instanceof Area;
                case DEPOTS -> entry instanceof Depot;
                case APPLICANTS -> entry instanceof Applicant;
                case TRADE -> entry instanceof Order;
                case DELEGATES -> entry instanceof Delegate;
                case PROJECTS -> entry instanceof Project;
                case BEDS -> entry instanceof Bed;
                case HISTORY -> entry instanceof History;
                case ISSUES -> entry instanceof Issue;
                case STATIONS -> entry instanceof Station;
                case STORAGE -> entry instanceof Storage;
                case SUPPLIES -> entry instanceof Supply;
                case SUPPLY_LINKS -> entry instanceof SupplyLink;
                case SUMMARY -> false;
            };
            if (!valid) {
                throw new IllegalArgumentException("Settlement entry does not belong to its page");
            }
        }
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementManagementPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeBoolean(payload.header() != null);
        if (payload.header() != null) {
            writeHeader(buffer, payload.header());
        }
        buffer.writeEnum(payload.page());
        buffer.writeVarInt(payload.index());
        buffer.writeVarInt(payload.total());
        buffer.writeUtf(payload.query(), 64);
        buffer.writeVarInt(payload.requestId());
        buffer.writeBoolean(payload.openScreen());
        buffer.writeVarInt(payload.entries().size());
        for (Entry entry : payload.entries()) writeEntry(buffer, entry);
    }

    static void writeEntry(RegistryFriendlyByteBuf buffer, Entry entry) {
        buffer.writeUUID(entry.id());
        buffer.writeUtf(entry.name(), 128);
        switch (entry) {
            case Supply supply -> {
                buffer.writeBlockPos(supply.position());
                writeOptionalId(buffer, supply.depot());
                writeOptionalId(buffer, supply.area());
                buffer.writeUtf(supply.storageName(), 128);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, supply.item());
                buffer.writeLong(supply.stored());
                buffer.writeLong(supply.incoming());
                buffer.writeVarInt(supply.target());
                buffer.writeUtf(supply.status(), 64);
            }
            case SupplyLink link -> {
                buffer.writeUUID(link.supply());
                buffer.writeEnum(link.kind());
                writeOptionalId(buffer, link.area());
                writeOptionalId(buffer, link.targetId());
                buffer.writeBlockPos(link.position());
                buffer.writeLong(link.incoming());
                buffer.writeVarInt(link.target());
                buffer.writeUtf(link.status(), 64);
                buffer.writeUtf(link.detail(), 512);
            }
            case Storage storage -> {
                buffer.writeUUID(storage.area());
                writeOptionalId(buffer, storage.resident());
                storage.links().write(buffer);
                buffer.writeBoolean(storage.override());
                buffer.writeVarInt(storage.containers().size());
                for (var container : storage.containers()) {
                    buffer.writeBlockPos(container.link().position());
                    buffer.writeEnum(container.link().purpose());
                    buffer.writeVarInt(container.link().priority());
                    buffer.writeUtf(container.name(), 128);
                    buffer.writeUtf(container.status(), 64);
                    buffer.writeVarInt(container.filters().size());
                    for (var filter : container.filters()) {
                        buffer.writeUUID(filter.frame());
                        ItemStack.STREAM_CODEC.encode(buffer, filter.item());
                    }
                }
            }
            case Town town -> {
                buffer.writeEnum(town.stage());
                buffer.writeVarInt(town.population());
                buffer.writeBlockPos(town.center());
            }
            case Resident resident -> {
                buffer.writeResourceLocation(resident.profession());
                buffer.writeBoolean(resident.baby());
                buffer.writeEnum(resident.presence());
                buffer.writeBlockPos(resident.position());
                writeOptionalId(buffer, resident.area());
                buffer.writeVarInt(resident.food());
                buffer.writeFloat(resident.health());
                buffer.writeUtf(resident.status(), 64);
                buffer.writeVarInt(resident.roles().size());
                resident.roles().forEach(buffer::writeEnum);
                SettlementManagementActionPayload.writeGuardOrder(buffer, resident.guardOrder());
                buffer.writeBoolean(resident.settings() != null);
                if (resident.settings() != null) {
                    SettlementManagementActionPayload.writeWorkerSettings(buffer, resident.settings());
                }
                SettlementManagementActionPayload.writeBrewingOrder(buffer, resident.brewingOrder());
                buffer.writeUtf(resident.diagnostic(), 180);
                buffer.writeBoolean(resident.manualStaffing());
                buffer.writeBoolean(resident.worksitePlan());
                buffer.writeVarInt(resident.overrideCount());
                buffer.writeUtf(resident.temporary(), 180);
            }
            case Area area -> {
                buffer.writeEnum(area.kind());
                buffer.writeBlockPos(area.min());
                buffer.writeBlockPos(area.max());
                buffer.writeVarInt(area.priority());
                buffer.writeBoolean(area.enabled());
                buffer.writeVarInt(area.beds());
                buffer.writeVarInt(area.fixtures());
                buffer.writeVarInt(area.workers());
                SettlementManagementActionPayload.writeWorksitePlan(buffer, area.plan());
            }
            case Station station -> buffer.writeBlockPos(station.position());
            case Depot depot -> {
                buffer.writeUUID(depot.area());
                buffer.writeBlockPos(depot.position());
                buffer.writeEnum(depot.purpose());
                buffer.writeVarInt(depot.priority());
                buffer.writeVarInt(depot.usedSlots());
                buffer.writeVarInt(depot.items());
                buffer.writeVarInt(depot.targets().size());
                for (StockTarget target : depot.targets()) {
                    buffer.writeUtf(target.name(), 128);
                    buffer.writeVarInt(target.count());
                }
            }
            case Applicant applicant -> {
                buffer.writeResourceLocation(applicant.profession());
                buffer.writeBoolean(applicant.approved());
            }
            case Order order -> {
                buffer.writeEnum(order.direction());
                buffer.writeResourceLocation(order.item());
                buffer.writeVarInt(order.target());
                order.price().write(buffer);
                order.budget().write(buffer);
                buffer.writeBoolean(order.enabled());
                buffer.writeUtf(order.result(), 64);
            }
            case Project project -> {
                buffer.writeUUID(project.area());
                buffer.writeUUID(project.builder());
                buffer.writeBlockPos(project.origin());
                buffer.writeBlockPos(project.min());
                buffer.writeBlockPos(project.max());
                buffer.writeEnum(project.status());
                buffer.writeVarInt(project.placed());
                buffer.writeVarInt(project.total());
                buffer.writeUtf(project.blocker(), 128);
                buffer.writeUtf(project.materials(), 512);
                buffer.writeEnum(project.phase());
                buffer.writeUtf(project.missingMaterials(), 512);
                buffer.writeVarInt(project.priority());
                buffer.writeVarLong(project.crewRevision());
                buffer.writeBoolean(project.crewAvailable());
                buffer.writeUtf(project.builderName(), 128);
                buffer.writeVarInt(project.assistants().size());
                for (var assistant : project.assistants()) {
                    buffer.writeUUID(assistant.id());
                    buffer.writeUtf(assistant.name(), 128);
                }
            }
            case Issue issue -> {
                buffer.writeEnum(issue.kind());
                buffer.writeUtf(issue.cause(), 128);
                buffer.writeUtf(issue.detail(), 512);
                buffer.writeEnum(issue.target());
                writeOptionalId(buffer, issue.targetId());
                writeOptionalId(buffer, issue.resident());
                writeOptionalId(buffer, issue.area());
                buffer.writeBoolean(issue.enabled());
            }
            case Bed bed -> {
                buffer.writeBlockPos(bed.position());
                writeOptionalId(buffer, bed.resident());
                buffer.writeUtf(bed.residentName(), 128);
                buffer.writeBoolean(bed.available());
            }
            case History history -> {
                buffer.writeVarLong(history.gameTime());
                buffer.writeEnum(history.kind());
            }
            case Delegate delegate -> writePermissions(buffer, delegate.permissions());
        }
    }

    private static SettlementManagementPayload decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation dimension = buffer.readResourceLocation();
        Header header = buffer.readBoolean() ? readHeader(buffer) : null;
        Page page = buffer.readEnum(Page.class);
        int index = buffer.readVarInt();
        int total = buffer.readVarInt();
        String query = buffer.readUtf(64);
        int requestId = buffer.readVarInt();
        boolean open = buffer.readBoolean();
        int count = VillagerPayloads.readCollectionSize(buffer, SettlementManagementRequestPayload.PAGE_SIZE, "settlement page");
        List<Entry> entries = new ArrayList<>(count);
        for (int entry = 0; entry < count; entry++) {
            entries.add(readEntry(buffer, page));
        }
        return new SettlementManagementPayload(dimension, header, page, index, total, query, requestId, open, entries);
    }

    static Entry readEntry(RegistryFriendlyByteBuf buffer, Page page) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(128);
        return switch (page) {
            case SUPPLIES -> new Supply(id, name, buffer.readBlockPos(), readOptionalId(buffer), readOptionalId(buffer),
                    buffer.readUtf(128), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer), buffer.readLong(), buffer.readLong(),
                    buffer.readVarInt(), buffer.readUtf(64));
            case SUPPLY_LINKS -> new SupplyLink(id, name, buffer.readUUID(), buffer.readEnum(SupplyLinkKind.class),
                    readOptionalId(buffer), readOptionalId(buffer), buffer.readBlockPos(), buffer.readLong(),
                    buffer.readVarInt(), buffer.readUtf(64), buffer.readUtf(512));
            case STORAGE -> {
                var area = buffer.readUUID();
                var resident = readOptionalId(buffer);
                var links = SettlementStorageLinks.read(buffer);
                boolean override = buffer.readBoolean();
                int count = VillagerPayloads.readCollectionSize(buffer, SettlementStorageLinks.MAX_LINKS, "storage status");
                var containers = new ArrayList<StorageStatus>();
                for (int index = 0; index < count; index++) {
                    var link = new SettlementStorageLinks.Link(buffer.readBlockPos(), buffer.readEnum(SettlementStorageLinks.Purpose.class), buffer.readVarInt());
                    String containerName = buffer.readUtf(128), status = buffer.readUtf(64);
                    int size = VillagerPayloads.readCollectionSize(buffer, 16, "storage filters");
                    var filters = new ArrayList<StorageFilter>();
                    for (int filter = 0; filter < size; filter++) {
                        filters.add(new StorageFilter(buffer.readUUID(), ItemStack.STREAM_CODEC.decode(buffer)));
                    }
                    containers.add(new StorageStatus(link, containerName, status, filters));
                }
                yield new Storage(id, name, area, resident, links, override, containers);
            }
            case TOWNS -> new Town(id, name, buffer.readEnum(SettlementStage.class), buffer.readVarInt(), buffer.readBlockPos());
            case RESIDENTS -> {
                ResourceLocation profession = buffer.readResourceLocation();
                boolean baby = buffer.readBoolean();
                var presence = buffer.readEnum(VillageOverviewPayload.Presence.class);
                BlockPos position = buffer.readBlockPos();
                UUID area = readOptionalId(buffer);
                int food = buffer.readVarInt();
                float health = buffer.readFloat();
                String status = buffer.readUtf(64);
                int roles = VillagerPayloads.readCollectionSize(buffer, HiredVillagerRole.values().length, "settlement worker roles");
                List<HiredVillagerRole> available = new ArrayList<>(roles);
                for (int role = 0; role < roles; role++) {
                    available.add(buffer.readEnum(HiredVillagerRole.class));
                }
                yield new Resident(id, name, profession, baby, presence, position, area, food, health, status, available,
                        SettlementManagementActionPayload.readGuardOrder(buffer),
                        buffer.readBoolean() ? SettlementManagementActionPayload.readWorkerSettings(buffer) : null,
                        SettlementManagementActionPayload.readBrewingOrder(buffer), buffer.readUtf(180), buffer.readBoolean(), buffer.readBoolean(),
                        buffer.readVarInt(), buffer.readUtf(180));
            }
            case AREAS -> new Area(id, name, buffer.readEnum(SettlementAreaType.class), buffer.readBlockPos(), buffer.readBlockPos(),
                    buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                    SettlementManagementActionPayload.readWorksitePlan(buffer));
            case STATIONS -> new Station(id, name, buffer.readBlockPos());
            case DEPOTS -> {
                UUID area = buffer.readUUID();
                BlockPos position = buffer.readBlockPos();
                var purpose = buffer.readEnum(SettlementDepotPurpose.class);
                int priority = buffer.readVarInt();
                int slots = buffer.readVarInt();
                int items = buffer.readVarInt();
                int targets = VillagerPayloads.readCollectionSize(buffer, 16, "depot stock targets");
                List<StockTarget> stock = new ArrayList<>(targets);
                for (int target = 0; target < targets; target++) {
                    stock.add(new StockTarget(buffer.readUtf(128), buffer.readVarInt()));
                }
                yield new Depot(id, name, area, position, purpose, priority, slots, items, stock);
            }
            case APPLICANTS -> new Applicant(id, name, buffer.readResourceLocation(), buffer.readBoolean());
            case TRADE -> new Order(id, name, buffer.readEnum(SettlementTradeOrder.Direction.class),
                    buffer.readResourceLocation(), buffer.readVarInt(), CurrencyAmount.read(buffer), CurrencyAmount.read(buffer),
                    buffer.readBoolean(), buffer.readUtf(64));
            case BEDS -> new Bed(id, name, buffer.readBlockPos(), readOptionalId(buffer), buffer.readUtf(128), buffer.readBoolean());
            case DELEGATES -> new Delegate(id, name, readPermissions(buffer));
            case HISTORY -> new History(id, name, buffer.readVarLong(), buffer.readEnum(SettlementEvent.Type.class));
            case PROJECTS -> new Project(id, name, buffer.readUUID(), buffer.readUUID(), buffer.readBlockPos(),
                    buffer.readBlockPos(), buffer.readBlockPos(),
                    buffer.readEnum(SettlementProject.Status.class), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(128), buffer.readUtf(512),
                    buffer.readEnum(com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase.class), buffer.readUtf(512),
                    buffer.readVarInt(), buffer.readVarLong(), buffer.readBoolean(), buffer.readUtf(128), readCrew(buffer));
            case ISSUES -> new Issue(id, name, buffer.readEnum(IssueKind.class), buffer.readUtf(128), buffer.readUtf(512),
                    buffer.readEnum(Page.class), readOptionalId(buffer), readOptionalId(buffer), readOptionalId(buffer),
                    buffer.readBoolean());
            case SUMMARY -> throw new IllegalArgumentException("Settlement summary cannot contain rows");
        };
    }

    static void writeHeader(RegistryFriendlyByteBuf buffer, Header header) {
        buffer.writeUUID(header.id());
        buffer.writeUtf(header.name(), 128);
        buffer.writeBlockPos(header.center());
        buffer.writeUUID(header.leader());
        writePermissions(buffer, header.permissions());
        buffer.writeEnum(header.stage());
        buffer.writeVarInt(header.radius());
        buffer.writeVarInt(header.projectLimit());
        buffer.writeVarInt(header.depotLimit());
        buffer.writeVarInt(header.marketSupply());
        buffer.writeVarInt(header.marketDemand());
        buffer.writeVarInt(header.population());
        buffer.writeVarInt(header.housing());
        buffer.writeDouble(header.foodDays());
        buffer.writeVarLong(header.shortageTicks());
        buffer.writeVarLong(header.supportTicks());
        buffer.writeVarLong(header.requiredTicks());
        buffer.writeBoolean(header.ready());
        buffer.writeVarInt(header.missing().size());
        header.missing().forEach(buffer::writeEnum);
        header.balance().write(buffer);
        header.reserve().write(buffer);
        header.budget().write(buffer);
        header.spent().write(buffer);
        buffer.writeVarInt(header.issues());
        buffer.writeBoolean(header.manualManagement());
        SettlementPlotSnapshot.Totals.write(buffer, header.plots());
        buffer.writeVarInt(header.growthTarget());
        header.populations().forEach(buffer::writeVarInt);
        buffer.writeBoolean(header.populationAdjusted());
        buffer.writeUtf(header.populationProblem(), 512);
    }

    static Header readHeader(RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID();
        String name = buffer.readUtf(128);
        BlockPos center = buffer.readBlockPos();
        UUID leader = buffer.readUUID();
        Set<SettlementPermission> permissions = readPermissions(buffer);
        SettlementStage stage = buffer.readEnum(SettlementStage.class);
        int radius = buffer.readVarInt();
        int projects = buffer.readVarInt();
        int depots = buffer.readVarInt();
        int supply = buffer.readVarInt();
        int demand = buffer.readVarInt();
        int population = buffer.readVarInt();
        int housing = buffer.readVarInt();
        double food = buffer.readDouble();
        long shortage = buffer.readVarLong();
        long support = buffer.readVarLong();
        long required = buffer.readVarLong();
        boolean ready = buffer.readBoolean();
        int count = VillagerPayloads.readCollectionSize(buffer, SettlementProgressionService.Requirement.values().length, "settlement requirements");
        List<SettlementProgressionService.Requirement> missing = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            missing.add(buffer.readEnum(SettlementProgressionService.Requirement.class));
        }
        return new Header(id, name, center, leader, permissions, stage, radius, projects, depots, supply, demand,
                population, housing, food, shortage, support, required,
                ready, missing, CurrencyAmount.read(buffer), CurrencyAmount.read(buffer), CurrencyAmount.read(buffer), CurrencyAmount.read(buffer),
                buffer.readVarInt(), buffer.readBoolean(), SettlementPlotSnapshot.Totals.read(buffer), buffer.readVarInt(),
                List.of(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()),
                buffer.readBoolean(), buffer.readUtf(512));
    }

    static void writeOptionalId(RegistryFriendlyByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeUUID(id);
        }
    }

    static UUID readOptionalId(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    private static void writePermissions(RegistryFriendlyByteBuf buffer, Set<SettlementPermission> permissions) {
        int bits = 0;
        for (SettlementPermission permission : permissions) {
            bits |= 1 << permission.ordinal();
        }
        buffer.writeVarInt(bits);
    }

    private static Set<SettlementPermission> readPermissions(RegistryFriendlyByteBuf buffer) {
        int bits = buffer.readVarInt();
        Set<SettlementPermission> result = EnumSet.noneOf(SettlementPermission.class);
        for (SettlementPermission permission : SettlementPermission.values()) {
            if ((bits & 1 << permission.ordinal()) != 0) {
                result.add(permission);
            }
        }
        return result;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Header(UUID id, String name, BlockPos center, UUID leader, Set<SettlementPermission> permissions,
                         SettlementStage stage, int radius, int projectLimit, int depotLimit, int marketSupply, int marketDemand,
                         int population, int housing, double foodDays, long shortageTicks,
                         long supportTicks, long requiredTicks, boolean ready, List<SettlementProgressionService.Requirement> missing,
                         CurrencyAmount balance, CurrencyAmount reserve, CurrencyAmount budget, CurrencyAmount spent, int issues, boolean manualManagement, SettlementPlotSnapshot.Totals plots,
                         int growthTarget, List<Integer> populations, boolean populationAdjusted, String populationProblem) {
        public Header {
            java.util.Objects.requireNonNull(plots);
            populations = List.copyOf(populations);
            if (growthTarget < 1 || growthTarget > 1000 || populations.size() != SettlementStage.values().length
                    || populations.getFirst() != 0 || populationProblem == null || populationProblem.length() > 512) {
                throw new IllegalArgumentException("Invalid settlement population requirements");
            }
            for (int index = 1; index < populations.size(); index++) {
                if (populations.get(index) <= populations.get(index - 1) || populations.get(index) > 1000) {
                    throw new IllegalArgumentException("Invalid effective settlement population threshold");
                }
            }
            if (radius < 1 || radius > 60_000_000) throw new IllegalArgumentException("Invalid camera envelope");
            if (issues < 0) throw new IllegalArgumentException("Invalid settlement issue count");
            new com.jvn.villagerretaliation.settlement.SettlementStageRules.Limits(
                    16, projectLimit, depotLimit, marketSupply, marketDemand, Set.of());
            permissions = Set.copyOf(permissions);
            missing = List.copyOf(missing);
            center = center.immutable();
        }
    }

    public sealed interface Entry permits Town, Resident, Area, Depot, Applicant, Order, Delegate, Project, Bed, History, Issue, Station, Storage, Supply, SupplyLink {
        UUID id();
        String name();
    }

    public record Supply(UUID id, String name, BlockPos position, UUID depot, UUID area, String storageName,
                         ItemStack item, long stored, long incoming, int target, String status) implements Entry {
        public Supply {
            if (id == null || name == null || name.length() > 128 || position == null || storageName == null
                    || storageName.length() > 128 || item == null || stored < -1 || incoming < -1 || target < 0
                    || target > 4096 || status == null || status.length() > 64) throw new IllegalArgumentException("Invalid supply row");
            position = position.immutable();
            item = item.copyWithCount(1);
        }

        @Override
        public ItemStack item() { return this.item.copy(); }

        public long shortage() { return this.stored < 0 || this.incoming < 0 ? -1 : Math.max(0L, this.target - this.stored - this.incoming); }
    }

    public enum SupplyLinkKind { STORAGE_TARGET, PRODUCTION, TRANSFER, WORKSITE, INPUT_STORAGE, PROJECT }

    public record SupplyLink(UUID id, String name, UUID supply, SupplyLinkKind kind, UUID area, UUID targetId,
                             BlockPos position, long incoming, int target, String status, String detail) implements Entry {
        public SupplyLink {
            if (id == null || name == null || name.length() > 128 || supply == null || kind == null || position == null
                    || incoming < 0 || target < 0 || target > 4096 || status == null || status.length() > 64
                    || detail == null || detail.length() > 512 || area == null
                    || kind != SupplyLinkKind.WORKSITE && targetId == null && kind != SupplyLinkKind.INPUT_STORAGE) {
                throw new IllegalArgumentException("Invalid supply link");
            }
            position = position.immutable();
        }
    }

    public record Storage(UUID id, String name, UUID area, UUID resident, SettlementStorageLinks links,
                          boolean override, List<StorageStatus> containers) implements Entry {
        public Storage {
            if (containers.size() > SettlementStorageLinks.MAX_LINKS) throw new IllegalArgumentException("Too many storage statuses");
            containers = List.copyOf(containers);
        }
    }

    public record StorageStatus(SettlementStorageLinks.Link link, String name, String status, List<StorageFilter> filters) {
        public StorageStatus {
            if (filters.size() > 16) throw new IllegalArgumentException("Too many framed storage filters");
            filters = List.copyOf(filters);
        }
    }

    public record StorageFilter(UUID frame, ItemStack item) {
        public StorageFilter { item = item.copyWithCount(1); }
    }

    public enum IssueKind {
        PROGRESSION, WORKER, PROJECT, TRADE, RESIDENT, SIMULATION
    }

    public record Issue(UUID id, String name, IssueKind kind, String cause, String detail,
                        Page target, UUID targetId, UUID resident, UUID area, boolean enabled) implements Entry {
        public Issue {
            if (id == null || name == null || name.length() > 128 || kind == null
                    || cause == null || cause.isBlank() || cause.length() > 128 || detail == null || detail.length() > 512
                    || target == null || switch (kind) {
                        case PROGRESSION -> target != Page.RESIDENTS && target != Page.AREAS && target != Page.DEPOTS;
                        case WORKER -> target != Page.RESIDENTS || targetId == null || !targetId.equals(resident) || area == null;
                        case PROJECT -> target != Page.PROJECTS || targetId == null || resident == null || area == null;
                        case TRADE -> target != Page.TRADE || targetId == null;
                        case RESIDENT -> target != Page.RESIDENTS || resident == null || !resident.equals(targetId);
                        case SIMULATION -> target != Page.SUMMARY;
                    }) {
                throw new IllegalArgumentException("Invalid settlement issue");
            }
        }

        public com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.Diagnostic diagnostic() {
            if (this.kind == IssueKind.PROGRESSION) {
                return new com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.Diagnostic(
                        this.cause.equals("population_cap") || this.cause.equals("population_configuration")
                                ? com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.Severity.WARNING
                                : com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.Severity.INFO, "");
            }
            return com.jvn.villagerretaliation.settlement.SettlementWorkerDiagnostics.diagnostic(
                    (this.kind == IssueKind.PROJECT ? "project." : this.kind == IssueKind.TRADE ? "trade." : "") + this.cause);
        }

        public boolean needsAttention() { return diagnostic().severity().attention(); }
    }

    public record Bed(UUID id, String name, BlockPos position, UUID resident, String residentName, boolean available) implements Entry {
        public Bed {
            if (id == null || name == null || name.length() > 128 || position == null
                    || residentName == null || residentName.length() > 128 || available && resident != null) {
                throw new IllegalArgumentException("Invalid settlement bed");
            }
            position = position.immutable();
        }
    }

    public record Town(UUID id, String name, SettlementStage stage, int population, BlockPos center) implements Entry {
    }

    public record Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                           BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles,
                           SettlementGuardOrder guardOrder, SettlementWorkerSettings settings, SettlementBrewingOrder brewingOrder, String diagnostic, boolean manualStaffing, boolean worksitePlan,
                           int overrideCount, String temporary) implements Entry {
        public Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                        BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles) {
            this(id, name, profession, baby, presence, position, area, food, health, status, roles, null, null);
        }

        public Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                        BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles,
                        SettlementGuardOrder guardOrder) {
            this(id, name, profession, baby, presence, position, area, food, health, status, roles, guardOrder, null);
        }

        public Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                        BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles,
                        SettlementGuardOrder guardOrder, SettlementWorkerSettings settings) {
            this(id, name, profession, baby, presence, position, area, food, health, status, roles, guardOrder, settings, null);
        }

        public Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                        BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles,
                        SettlementGuardOrder guardOrder, SettlementWorkerSettings settings, SettlementBrewingOrder brewingOrder) {
            this(id, name, profession, baby, presence, position, area, food, health, status, roles, guardOrder, settings, brewingOrder, "");
        }

        public Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                        BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles,
                        SettlementGuardOrder guardOrder, SettlementWorkerSettings settings, SettlementBrewingOrder brewingOrder, String diagnostic) {
            this(id, name, profession, baby, presence, position, area, food, health, status, roles, guardOrder, settings, brewingOrder, diagnostic, false);
        }

        public Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                        BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles,
                        SettlementGuardOrder guardOrder, SettlementWorkerSettings settings, SettlementBrewingOrder brewingOrder,
                        String diagnostic, boolean manualStaffing) {
            this(id, name, profession, baby, presence, position, area, food, health, status, roles, guardOrder, settings,
                    brewingOrder, diagnostic, manualStaffing, false);
        }

        public Resident(UUID id, String name, ResourceLocation profession, boolean baby, VillageOverviewPayload.Presence presence,
                        BlockPos position, UUID area, int food, float health, String status, List<HiredVillagerRole> roles,
                        SettlementGuardOrder guardOrder, SettlementWorkerSettings settings, SettlementBrewingOrder brewingOrder,
                        String diagnostic, boolean manualStaffing, boolean worksitePlan) {
            this(id, name, profession, baby, presence, position, area, food, health, status, roles, guardOrder, settings,
                    brewingOrder, diagnostic, manualStaffing, worksitePlan, settings == null ? 0 : Integer.bitCount(settings.overrides()), "");
        }

        public Resident {
            if (overrideCount < 0 || overrideCount > 32 || temporary == null || temporary.length() > 180) {
                throw new IllegalArgumentException("Invalid worker summary");
            }
            if (diagnostic == null || diagnostic.length() > 180) throw new IllegalArgumentException("Invalid resident work diagnostic");
            roles = List.copyOf(roles);
        }
    }

    public record Area(UUID id, String name, SettlementAreaType kind, BlockPos min, BlockPos max,
                       int priority, boolean enabled, int beds, int fixtures, int workers,
                       com.jvn.villagerretaliation.settlement.SettlementWorksitePlan plan) implements Entry {
        public Area(UUID id, String name, SettlementAreaType kind, BlockPos min, BlockPos max,
                    int priority, boolean enabled, int beds, int fixtures, int workers) {
            this(id, name, kind, min, max, priority, enabled, beds, fixtures, workers, null);
        }
    }

    public record Station(UUID id, String name, BlockPos position) implements Entry { }

    public record Depot(UUID id, String name, UUID area, BlockPos position, SettlementDepotPurpose purpose, int priority,
                        int usedSlots, int items, List<StockTarget> targets) implements Entry {
        public Depot {
            targets = List.copyOf(targets);
        }
    }

    public record StockTarget(String name, int count) {
    }

    public record Applicant(UUID id, String name, ResourceLocation profession, boolean approved) implements Entry {
    }

    public record Order(UUID id, String name, SettlementTradeOrder.Direction direction, ResourceLocation item, int target,
                        CurrencyAmount price, CurrencyAmount budget, boolean enabled, String result) implements Entry {
    }

    private static List<CrewMember> readCrew(RegistryFriendlyByteBuf buffer) {
        int count = VillagerPayloads.readCollectionSize(buffer, 15, "construction assistants");
        var crew = new ArrayList<CrewMember>(count);
        for (int index = 0; index < count; index++) crew.add(new CrewMember(buffer.readUUID(), buffer.readUtf(128)));
        return List.copyOf(crew);
    }

    public record CrewMember(UUID id, String name) {
        public CrewMember {
            if (id == null || name == null || name.length() > 128) throw new IllegalArgumentException("Invalid construction crew member");
        }
    }

    public record Project(UUID id, String name, UUID area, UUID builder, BlockPos origin, BlockPos min, BlockPos max, SettlementProject.Status status,
                          int placed, int total, String blocker, String materials,
                          com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase phase, String missingMaterials,
                          int priority, long crewRevision, boolean crewAvailable, String builderName, List<CrewMember> assistants) implements Entry {
        public Project {
            assistants = List.copyOf(assistants);
            if (id == null || area == null || builder == null || origin == null || status == null || name == null || name.length() > 128
                    || min == null || max == null || min.getX() > max.getX() || min.getY() > max.getY() || min.getZ() > max.getZ()
                    || placed < 0 || total <= 0 || placed > total || blocker == null || blocker.length() > 128
                    || materials == null || materials.length() > 512 || phase == null || missingMaterials == null || missingMaterials.length() > 512
                    || priority < 0 || priority > 9 || crewRevision < 0L || builderName == null || builderName.length() > 128 || assistants.size() > 15
                    || assistants.stream().map(CrewMember::id).distinct().count() != assistants.size()
                    || assistants.stream().anyMatch(member -> member.id().equals(builder))) {
                throw new IllegalArgumentException("Invalid construction project snapshot");
            }
        }
    }

    public record History(UUID id, String name, long gameTime, SettlementEvent.Type kind) implements Entry {
        public History {
            if (id == null || name == null || name.length() > 128 || gameTime < 0 || kind == null) {
                throw new IllegalArgumentException("Invalid settlement history snapshot");
            }
        }
    }

    public record Delegate(UUID id, String name, Set<SettlementPermission> permissions) implements Entry {
        public Delegate {
            permissions = Set.copyOf(permissions);
        }
    }
}
