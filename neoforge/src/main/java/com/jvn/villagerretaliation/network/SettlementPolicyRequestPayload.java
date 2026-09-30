package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.settlement.SettlementSupplyPolicy;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SettlementPolicyRequestPayload(UUID town, UUID request, UUID policy, long revision, UUID area, Command command)
        implements CustomPacketPayload {
    public static final Type<SettlementPolicyRequestPayload> TYPE = VillagerPayloads.type("settlement_policy_request");
    public static final StreamCodec<RegistryFriendlyByteBuf, SettlementPolicyRequestPayload> STREAM_CODEC =
            VillagerPayloads.codec(SettlementPolicyRequestPayload::encode, SettlementPolicyRequestPayload::decode);

    public SettlementPolicyRequestPayload {
        Objects.requireNonNull(town);
        Objects.requireNonNull(request);
        Objects.requireNonNull(command);
        if (revision < 0 || policy == null && !(command instanceof View)) throw new IllegalArgumentException("Invalid policy request");
        var configuration = command instanceof Preview preview ? preview.configuration() : command instanceof Save save ? save.configuration() : null;
        if (configuration != null && configuration.steps().stream().anyMatch(step -> step.id().equals(policy)))
            throw new IllegalArgumentException("Policy and step identities must differ");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, SettlementPolicyRequestPayload payload) {
        buffer.writeUUID(payload.town());
        buffer.writeUUID(payload.request());
        optionalId(buffer, payload.policy());
        buffer.writeVarLong(payload.revision());
        optionalId(buffer, payload.area());
        switch (payload.command()) {
            case View ignored -> buffer.writeEnum(Kind.VIEW);
            case Preview preview -> {
                buffer.writeEnum(Kind.PREVIEW);
                buffer.writeNbt(preview.configuration().save(buffer.registryAccess()));
            }
            case Save save -> {
                buffer.writeEnum(Kind.SAVE);
                buffer.writeNbt(save.configuration().save(buffer.registryAccess()));
                buffer.writeBoolean(save.active());
            }
            case Change change -> {
                buffer.writeEnum(Kind.CHANGE);
                buffer.writeEnum(change.state());
            }
        }
    }

    private static SettlementPolicyRequestPayload decode(RegistryFriendlyByteBuf buffer) {
        var town = buffer.readUUID();
        var request = buffer.readUUID();
        var policy = optionalId(buffer);
        long revision = buffer.readVarLong();
        var area = optionalId(buffer);
        Command command = switch (buffer.readEnum(Kind.class)) {
            case VIEW -> new View();
            case PREVIEW -> new Preview(configuration(buffer));
            case SAVE -> new Save(configuration(buffer), buffer.readBoolean());
            case CHANGE -> new Change(buffer.readEnum(SettlementSupplyPolicy.State.class));
        };
        return new SettlementPolicyRequestPayload(town, request, policy, revision, area, command);
    }

    private static SettlementSupplyPolicy.Configuration configuration(RegistryFriendlyByteBuf buffer) {
        var tag = buffer.readNbt();
        if (tag == null) throw new IllegalArgumentException("Missing policy configuration");
        return SettlementSupplyPolicy.Configuration.load(tag, buffer.registryAccess());
    }

    static void optionalId(RegistryFriendlyByteBuf buffer, UUID value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeUUID(value);
    }

    static UUID optionalId(RegistryFriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public sealed interface Command permits View, Preview, Save, Change { }
    public record View() implements Command { }
    public record Preview(SettlementSupplyPolicy.Configuration configuration) implements Command {
        public Preview { Objects.requireNonNull(configuration); }
    }
    public record Save(SettlementSupplyPolicy.Configuration configuration, boolean active) implements Command {
        public Save { Objects.requireNonNull(configuration); }
    }
    public record Change(SettlementSupplyPolicy.State state) implements Command {
        public Change {
            if (state != SettlementSupplyPolicy.State.ACTIVE && state != SettlementSupplyPolicy.State.PAUSED
                    && state != SettlementSupplyPolicy.State.REMOVING) throw new IllegalArgumentException("Invalid policy action");
        }
    }
    private enum Kind { VIEW, PREVIEW, SAVE, CHANGE }
}
