package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.network.SettlementManagementRequestPayload.Page;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementDepotPurpose;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementWorkerSettings;
import com.jvn.villagerretaliation.settlement.SettlementBrewingOrder;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementProject;
import com.jvn.villagerretaliation.settlement.SettlementProgressionService;
import com.jvn.villagerretaliation.settlement.SettlementStage;
import com.jvn.villagerretaliation.settlement.SettlementTradeOrder;
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
        for (Entry entry : payload.entries()) {
            buffer.writeUUID(entry.id());
            buffer.writeUtf(entry.name(), 128);
            switch (entry) {
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
                }
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
                    buffer.writeEnum(project.status());
                    buffer.writeVarInt(project.placed());
                    buffer.writeVarInt(project.total());
                    buffer.writeUtf(project.blocker(), 128);
                    buffer.writeUtf(project.materials(), 512);
                    buffer.writeEnum(project.phase());
                    buffer.writeUtf(project.missingMaterials(), 512);
                }
                case Bed bed -> {
                    buffer.writeBlockPos(bed.position());
                    writeOptionalId(buffer, bed.resident());
                    buffer.writeUtf(bed.residentName(), 128);
                    buffer.writeBoolean(bed.available());
                }
                case Delegate delegate -> writePermissions(buffer, delegate.permissions());
            }
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
            UUID id = buffer.readUUID();
            String name = buffer.readUtf(128);
            entries.add(switch (page) {
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
                            SettlementManagementActionPayload.readBrewingOrder(buffer));
                }
                case AREAS -> new Area(id, name, buffer.readEnum(SettlementAreaType.class), buffer.readBlockPos(), buffer.readBlockPos(),
                        buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
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
                case PROJECTS -> new Project(id, name, buffer.readUUID(), buffer.readUUID(), buffer.readBlockPos(),
                        buffer.readEnum(SettlementProject.Status.class), buffer.readVarInt(), buffer.readVarInt(), buffer.readUtf(128), buffer.readUtf(512),
                        buffer.readEnum(com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase.class), buffer.readUtf(512));
                case SUMMARY -> throw new IllegalArgumentException("Settlement summary cannot contain rows");
            });
        }
        return new SettlementManagementPayload(dimension, header, page, index, total, query, requestId, open, entries);
    }

    private static void writeHeader(RegistryFriendlyByteBuf buffer, Header header) {
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
    }

    private static Header readHeader(RegistryFriendlyByteBuf buffer) {
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
                ready, missing, CurrencyAmount.read(buffer), CurrencyAmount.read(buffer), CurrencyAmount.read(buffer), CurrencyAmount.read(buffer));
    }

    private static void writeOptionalId(RegistryFriendlyByteBuf buffer, UUID id) {
        buffer.writeBoolean(id != null);
        if (id != null) {
            buffer.writeUUID(id);
        }
    }

    private static UUID readOptionalId(RegistryFriendlyByteBuf buffer) {
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
                         CurrencyAmount balance, CurrencyAmount reserve, CurrencyAmount budget, CurrencyAmount spent) {
        public Header {
            new com.jvn.villagerretaliation.settlement.SettlementStageRules.Limits(
                    radius, projectLimit, depotLimit, marketSupply, marketDemand, Set.of());
            permissions = Set.copyOf(permissions);
            missing = List.copyOf(missing);
            center = center.immutable();
        }
    }

    public sealed interface Entry permits Town, Resident, Area, Depot, Applicant, Order, Delegate, Project, Bed {
        UUID id();
        String name();
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
                           SettlementGuardOrder guardOrder, SettlementWorkerSettings settings, SettlementBrewingOrder brewingOrder) implements Entry {
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

        public Resident {
            roles = List.copyOf(roles);
        }
    }

    public record Area(UUID id, String name, SettlementAreaType kind, BlockPos min, BlockPos max,
                       int priority, boolean enabled, int beds, int fixtures, int workers) implements Entry {
    }

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

    public record Project(UUID id, String name, UUID area, UUID builder, BlockPos origin, SettlementProject.Status status,
                          int placed, int total, String blocker, String materials,
                          com.jvn.villagerretaliation.interaction.work.builder.BuilderBuildPhase phase, String missingMaterials) implements Entry {
        public Project {
            if (id == null || area == null || builder == null || origin == null || status == null || name == null || name.length() > 128
                    || placed < 0 || total <= 0 || placed > total || blocker == null || blocker.length() > 128
                    || materials == null || materials.length() > 512 || phase == null || missingMaterials == null || missingMaterials.length() > 512) {
                throw new IllegalArgumentException("Invalid construction project snapshot");
            }
        }
    }

    public record Delegate(UUID id, String name, Set<SettlementPermission> permissions) implements Entry {
        public Delegate {
            permissions = Set.copyOf(permissions);
        }
    }
}
