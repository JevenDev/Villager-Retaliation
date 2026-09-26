package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.sell.CurrencyAmount;
import com.jvn.villagerretaliation.settlement.SettlementArea;
import com.jvn.villagerretaliation.settlement.SettlementAreaType;
import com.jvn.villagerretaliation.settlement.SettlementDepotPurpose;
import com.jvn.villagerretaliation.settlement.SettlementGuardOrder;
import com.jvn.villagerretaliation.settlement.SettlementPermission;
import com.jvn.villagerretaliation.settlement.SettlementTradeOrder;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementManagementActionPayload(UUID town, Command command) implements CustomPacketPayload {
    public static final Type<SettlementManagementActionPayload> TYPE = VillagerPayloads.type("settlement_management_action");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementManagementActionPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementManagementActionPayload::encode, SettlementManagementActionPayload::decode);

    public SettlementManagementActionPayload {
        Objects.requireNonNull(town);
        Objects.requireNonNull(command);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementManagementActionPayload payload) {
        buffer.writeUUID(payload.town());
        switch (payload.command()) {
            case Advance ignored -> buffer.writeEnum(Kind.ADVANCE);
            case View ignored -> buffer.writeEnum(Kind.VIEW);
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
        Command command = switch (buffer.readEnum(Kind.class)) {
            case ADVANCE -> new Advance();
            case VIEW -> new View();
            case GUARD -> new Guard(buffer.readUUID(), readGuardOrder(buffer));
            case ASSIGN -> new Assign(buffer.readUUID(), buffer.readUUID());
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
        return new SettlementManagementActionPayload(town, command);
    }

    static void writeGuardOrder(RegistryFriendlyByteBuf buffer, SettlementGuardOrder order) {
        buffer.writeBoolean(order != null);
        if (order != null) {
            buffer.writeEnum(order.mode());
            buffer.writeVarInt(order.points().size());
            order.points().forEach(buffer::writeBlockPos);
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
        return new SettlementGuardOrder(mode, points);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private enum Kind {
        ADVANCE, ASSIGN, RELEASE, APPLICANT, SAVE_AREA, REMOVE_AREA, CONFIGURE_DEPOT, STOCK_TARGET,
        TREASURY, BUDGET, SAVE_ORDER, REMOVE_ORDER, PERMISSIONS, TRANSFER, INVENTORY, VIEW, GUARD
    }

    public sealed interface Command permits Advance, Assign, Release, Applicant, SaveArea, RemoveArea,
            ConfigureDepot, StockTarget, Treasury, Budget, SaveOrder, RemoveOrder, Permissions, Transfer, Inventory, View, Guard {
    }

    public record Advance() implements Command {
    }

    public record View() implements Command {
    }

    public record Guard(UUID resident, SettlementGuardOrder order) implements Command {
    }

    public record Assign(UUID resident, UUID area) implements Command {
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
