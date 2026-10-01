package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.interaction.work.production.WorkSupplyRequirement;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementRecord;
import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import com.jvn.villagerretaliation.settlement.SettlementPurchasePlan;
import com.jvn.villagerretaliation.sell.CurrencyAmount;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record SettlementPolicySnapshotPayload(ResourceLocation dimension, UUID town, UUID request, boolean applied, boolean editable,
        String feedback, String blocker, List<Summary> policies, CompoundTag selected, List<Worksite> worksites,
        List<Storage> storages, List<Consumer> consumers, List<Need> needs, List<Production> production,
        boolean trade, long gameTime, PurchaseAccount purchases, MarketPage market) implements CustomPacketPayload {
    public static final int MARKET_PAGE_SIZE = 32;
    public static final Type<SettlementPolicySnapshotPayload> TYPE = VillagerPayloads.type("settlement_policy_snapshot");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementPolicySnapshotPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementPolicySnapshotPayload::encode, SettlementPolicySnapshotPayload::decode);

    public SettlementPolicySnapshotPayload {
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(town);
        Objects.requireNonNull(request);
        Objects.requireNonNull(purchases);
        if (gameTime < 0) throw new IllegalArgumentException("Invalid policy snapshot time");
        code(feedback);
        code(blocker);
        policies = List.copyOf(policies);
        selected = selected == null ? null : selected.copy();
        worksites = List.copyOf(worksites);
        storages = List.copyOf(storages);
        consumers = List.copyOf(consumers);
        needs = List.copyOf(needs);
        production = List.copyOf(production);
        if (policies.size() > SettlementSupplyPolicy.MAX_POLICIES || worksites.size() > SettlementRecord.MAX_AREAS
                || storages.size() > 512 || consumers.size() > 512 || needs.size() > 1024
                || production.size() > SettlementSupplyPolicy.MAX_STEPS) throw new IllegalArgumentException("Oversized policy snapshot");
    }

    @Override public CompoundTag selected() { return selected == null ? null : selected.copy(); }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementPolicySnapshotPayload payload) {
        buffer.writeResourceLocation(payload.dimension());
        buffer.writeUUID(payload.town());
        buffer.writeUUID(payload.request());
        buffer.writeBoolean(payload.applied());
        buffer.writeBoolean(payload.editable());
        buffer.writeUtf(payload.feedback(), 128);
        buffer.writeUtf(payload.blocker(), 128);
        buffer.writeVarInt(payload.policies().size());
        for (var policy : payload.policies()) {
            buffer.writeUUID(policy.id());
            buffer.writeUtf(policy.name(), 64);
            buffer.writeVarLong(policy.revision());
            buffer.writeEnum(policy.state());
            buffer.writeUtf(policy.blocker(), 128);
        }
        buffer.writeNbt(payload.selected());
        buffer.writeVarInt(payload.worksites().size());
        for (var area : payload.worksites()) {
            buffer.writeUUID(area.id());
            buffer.writeUtf(area.name(), 128);
            buffer.writeEnum(area.type());
            buffer.writeBlockPos(area.position());
            buffer.writeBoolean(area.enabled());
        }
        buffer.writeVarInt(payload.storages().size());
        for (var storage : payload.storages()) {
            buffer.writeBlockPos(storage.position());
            buffer.writeUtf(storage.name(), 128);
            buffer.writeBoolean(storage.source());
            buffer.writeBoolean(storage.destination());
            buffer.writeBoolean(storage.known());
        }
        buffer.writeVarInt(payload.consumers().size());
        for (var consumer : payload.consumers()) {
            buffer.writeUUID(consumer.id());
            buffer.writeUtf(consumer.name(), 128);
            buffer.writeEnum(consumer.kind());
        }
        buffer.writeVarInt(payload.needs().size());
        for (var need : payload.needs()) {
            SettlementPolicyRequestPayload.optionalId(buffer, need.step());
            buffer.writeUUID(need.id());
            buffer.writeUtf(need.name(), 128);
            buffer.writeEnum(need.unit());
            buffer.writeLong(need.required());
            buffer.writeLong(need.available());
            buffer.writeLong(need.committed());
            buffer.writeLong(need.incoming());
            buffer.writeLong(need.missing());
            buffer.writeBoolean(need.known());
            buffer.writeVarInt(need.samples().size());
            for (var sample : need.samples()) ItemStack.STREAM_CODEC.encode(buffer, sample);
            buffer.writeVarInt(need.sources().size());
            for (var source : need.sources()) buffer.writeBlockPos(source);
        }
        buffer.writeVarInt(payload.production().size());
        for (var step : payload.production()) {
            buffer.writeUUID(step.id());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, step.output());
            buffer.writeLong(step.proposed());
            buffer.writeLong(step.requested());
            buffer.writeLong(step.retained());
            buffer.writeUtf(step.blocker(), 128);
        }
        buffer.writeBoolean(payload.trade());
        buffer.writeVarLong(payload.gameTime());
        payload.purchases().spent().write(buffer);
        payload.purchases().available().write(buffer);
        buffer.writeVarInt(payload.purchases().approvals().size());
        for (var purchase : payload.purchases().approvals()) {
            buffer.writeUUID(purchase.approval());
            buffer.writeLong(purchase.missing());
            buffer.writeBoolean(purchase.known());
            buffer.writeVarLong(purchase.waitTicks());
            purchase.unitPrice().write(buffer);
            buffer.writeUtf(purchase.blocker(), 128);
        }
        buffer.writeBoolean(payload.market() != null);
        if (payload.market() != null) {
            var market = payload.market();
            SettlementPolicyRequestPayload.optionalId(buffer, market.consumer());
            buffer.writeUUID(market.requirement());
            buffer.writeUtf(market.query(), 96);
            buffer.writeVarInt(market.page());
            buffer.writeBoolean(market.more());
            buffer.writeVarInt(market.commodities().size());
            for (var commodity : market.commodities()) {
                buffer.writeResourceLocation(commodity.item());
                commodity.unitPrice().write(buffer);
            }
            buffer.writeVarInt(market.depots().size());
            for (var depot : market.depots()) {
                buffer.writeBlockPos(depot.position());
                buffer.writeUtf(depot.name(), 128);
                buffer.writeBoolean(depot.source());
                buffer.writeBoolean(depot.destination());
                buffer.writeBoolean(depot.known());
            }
        }
    }

    private static SettlementPolicySnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
        var dimension = buffer.readResourceLocation();
        var town = buffer.readUUID();
        var request = buffer.readUUID();
        boolean applied = buffer.readBoolean();
        boolean editable = buffer.readBoolean();
        String feedback = buffer.readUtf(128);
        String blocker = buffer.readUtf(128);
        var policies = new ArrayList<Summary>();
        int size = VillagerPayloads.readCollectionSize(buffer, SettlementSupplyPolicy.MAX_POLICIES, "Policies");
        for (int index = 0; index < size; index++) policies.add(new Summary(buffer.readUUID(), buffer.readUtf(64), buffer.readVarLong(),
                buffer.readEnum(SettlementSupplyPolicy.State.class), buffer.readUtf(128)));
        var selected = buffer.readNbt();
        if (selected != null) SettlementSupplyPolicy.load(selected, buffer.registryAccess(), () -> {});
        var worksites = new ArrayList<Worksite>();
        size = VillagerPayloads.readCollectionSize(buffer, SettlementRecord.MAX_AREAS, "Policy worksites");
        for (int index = 0; index < size; index++) worksites.add(new Worksite(buffer.readUUID(), buffer.readUtf(128),
                buffer.readEnum(SettlementAreaType.class), buffer.readBlockPos(), buffer.readBoolean()));
        var storages = new ArrayList<Storage>();
        size = VillagerPayloads.readCollectionSize(buffer, 512, "Policy storage");
        for (int index = 0; index < size; index++) storages.add(new Storage(buffer.readBlockPos(), buffer.readUtf(128),
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
        var consumers = new ArrayList<Consumer>();
        size = VillagerPayloads.readCollectionSize(buffer, 512, "Policy consumers");
        for (int index = 0; index < size; index++) consumers.add(new Consumer(buffer.readUUID(), buffer.readUtf(128),
                buffer.readEnum(SettlementSupplyPolicy.TargetKind.class)));
        var needs = new ArrayList<Need>();
        size = VillagerPayloads.readCollectionSize(buffer, 1024, "Policy requirements");
        for (int index = 0; index < size; index++) {
            var step = SettlementPolicyRequestPayload.optionalId(buffer);
            var id = buffer.readUUID();
            String name = buffer.readUtf(128);
            var unit = buffer.readEnum(WorkSupplyRequirement.Unit.class);
            long required = buffer.readLong();
            long available = buffer.readLong();
            long committed = buffer.readLong();
            long incoming = buffer.readLong();
            long missing = buffer.readLong();
            boolean known = buffer.readBoolean();
            var samples = new ArrayList<ItemStack>();
            int count = VillagerPayloads.readCollectionSize(buffer, 16, "Policy alternatives");
            for (int entry = 0; entry < count; entry++) samples.add(ItemStack.STREAM_CODEC.decode(buffer));
            var sources = new ArrayList<BlockPos>();
            count = VillagerPayloads.readCollectionSize(buffer, 512, "Policy sources");
            for (int entry = 0; entry < count; entry++) sources.add(buffer.readBlockPos());
            needs.add(new Need(step, id, name, unit, required, available, committed, incoming, missing, known, samples, sources));
        }
        var production = new ArrayList<Production>();
        size = VillagerPayloads.readCollectionSize(buffer, SettlementSupplyPolicy.MAX_STEPS, "Policy steps");
        for (int index = 0; index < size; index++) production.add(new Production(buffer.readUUID(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                buffer.readLong(), buffer.readLong(), buffer.readLong(), buffer.readUtf(128)));
        boolean trade = buffer.readBoolean();
        long gameTime = buffer.readVarLong();
        var spent = CurrencyAmount.read(buffer);
        var available = CurrencyAmount.read(buffer);
        var purchases = new ArrayList<Purchase>();
        size = VillagerPayloads.readCollectionSize(buffer, SettlementPurchasePlan.MAX_APPROVALS, "Purchase approvals");
        for (int index = 0; index < size; index++) purchases.add(new Purchase(buffer.readUUID(), buffer.readLong(), buffer.readBoolean(),
                buffer.readVarLong(), CurrencyAmount.read(buffer), buffer.readUtf(128)));
        MarketPage market = null;
        if (buffer.readBoolean()) {
            var consumer = SettlementPolicyRequestPayload.optionalId(buffer);
            var requirement = buffer.readUUID();
            String query = buffer.readUtf(96);
            int page = buffer.readVarInt();
            boolean more = buffer.readBoolean();
            var commodities = new ArrayList<Commodity>();
            size = VillagerPayloads.readCollectionSize(buffer, MARKET_PAGE_SIZE, "Market commodities");
            for (int index = 0; index < size; index++) commodities.add(new Commodity(buffer.readResourceLocation(), CurrencyAmount.read(buffer)));
            var depots = new ArrayList<Storage>();
            size = VillagerPayloads.readCollectionSize(buffer, 512, "Purchase depots");
            for (int index = 0; index < size; index++) depots.add(new Storage(buffer.readBlockPos(), buffer.readUtf(128),
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean()));
            market = new MarketPage(consumer, requirement, query, page, more, commodities, depots);
        }
        return new SettlementPolicySnapshotPayload(dimension, town, request, applied, editable, feedback, blocker,
                policies, selected, worksites, storages, consumers, needs, production, trade, gameTime,
                new PurchaseAccount(spent, available, purchases), market);
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static void code(String value) {
        if (value == null || value.length() > 128) throw new IllegalArgumentException("Invalid policy text");
    }

    public record PurchaseAccount(CurrencyAmount spent, CurrencyAmount available, List<Purchase> approvals) {
        public static final PurchaseAccount EMPTY = new PurchaseAccount(CurrencyAmount.ZERO, CurrencyAmount.ZERO, List.of());
        public PurchaseAccount {
            Objects.requireNonNull(spent);
            Objects.requireNonNull(available);
            approvals = List.copyOf(approvals);
            if (approvals.size() > SettlementPurchasePlan.MAX_APPROVALS || approvals.stream().map(Purchase::approval).distinct().count() != approvals.size())
                throw new IllegalArgumentException("Invalid purchase account snapshot");
        }
    }
    public record Purchase(UUID approval, long missing, boolean known, long waitTicks, CurrencyAmount unitPrice, String blocker) {
        public Purchase {
            Objects.requireNonNull(approval);
            Objects.requireNonNull(unitPrice);
            code(blocker);
            if (missing < -1 || known && missing < 0 || waitTicks < 0 || waitTicks > SettlementPurchasePlan.SHORTAGE_TICKS)
                throw new IllegalArgumentException("Invalid purchase preview");
        }
    }
    public record Commodity(ResourceLocation item, CurrencyAmount unitPrice) {
        public Commodity {
            Objects.requireNonNull(item);
            Objects.requireNonNull(unitPrice);
            if (unitPrice.isZero() || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(item)
                    || net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item) == net.minecraft.world.item.Items.AIR)
                throw new IllegalArgumentException("Invalid purchase commodity");
        }
        public ItemStack sample() { return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(item)); }
    }
    public record MarketPage(UUID consumer, UUID requirement, String query, int page, boolean more, List<Commodity> commodities, List<Storage> depots) {
        public MarketPage {
            Objects.requireNonNull(requirement);
            commodities = List.copyOf(commodities);
            depots = List.copyOf(depots);
            if (query == null || query.length() > 96 || page < 0 || page > 4096 || commodities.size() > MARKET_PAGE_SIZE || depots.size() > 512
                    || commodities.stream().map(Commodity::item).distinct().count() != commodities.size())
                throw new IllegalArgumentException("Invalid purchase market page");
        }
    }

    public record Summary(UUID id, String name, long revision, SettlementSupplyPolicy.State state, String blocker) {
        public Summary {
            Objects.requireNonNull(id);
            Objects.requireNonNull(state);
            code(blocker);
            if (name == null || name.length() > 64 || revision < 0) throw new IllegalArgumentException("Invalid policy summary");
        }
    }
    public record Worksite(UUID id, String name, SettlementAreaType type, BlockPos position, boolean enabled) {
        public Worksite {
            Objects.requireNonNull(id);
            Objects.requireNonNull(type);
            position = Objects.requireNonNull(position).immutable();
            code(name);
        }
    }
    public record Storage(BlockPos position, String name, boolean source, boolean destination, boolean known) {
        public Storage {
            position = Objects.requireNonNull(position).immutable();
            code(name);
        }
    }
    public record Consumer(UUID id, String name, SettlementSupplyPolicy.TargetKind kind) {
        public Consumer {
            Objects.requireNonNull(id);
            Objects.requireNonNull(kind);
            code(name);
            if (kind == SettlementSupplyPolicy.TargetKind.STOCK) throw new IllegalArgumentException("Stock targets need an endpoint");
        }
    }
    public record Need(UUID step, UUID id, String name, WorkSupplyRequirement.Unit unit, long required, long available,
                       long committed, long incoming, long missing, boolean known, List<ItemStack> samples, List<BlockPos> sources) {
        public Need {
            Objects.requireNonNull(id);
            Objects.requireNonNull(unit);
            code(name);
            samples = samples.stream().map(ItemStack::copy).toList();
            sources = sources.stream().map(BlockPos::immutable).toList();
            if (required < 0 || available < 0 || committed < 0 || incoming < 0 || missing < 0
                    || samples.size() > 16 || sources.size() > 512) throw new IllegalArgumentException("Invalid policy requirement");
        }
        @Override public List<ItemStack> samples() { return samples.stream().map(ItemStack::copy).toList(); }
    }
    public record Production(UUID id, ItemStack output, long proposed, long requested, long retained, String blocker) {
        public Production {
            Objects.requireNonNull(id);
            output = Objects.requireNonNull(output).copy();
            code(blocker);
            if (proposed < 0 || requested < 0 || retained < 0) throw new IllegalArgumentException("Invalid policy quantities");
        }
        @Override public ItemStack output() { return output.copy(); }
    }
}
