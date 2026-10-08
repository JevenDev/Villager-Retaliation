package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignConversationViewPayload(UUID viewId, int entityId, UUID character, int page, int pageCount,
        List<Topic> topics, Session session, String diagnostic) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 32;
    public static final int MAX_TEXT_LENGTH = 8192;
    public static final int MAX_LABEL_LENGTH = 512;
    public static final Type<CampaignConversationViewPayload> TYPE = VillagerPayloads.type("campaign_conversation_view");
    public static final StreamCodec<RegistryFriendlyByteBuf, CampaignConversationViewPayload> STREAM_CODEC =
            VillagerPayloads.codec(CampaignConversationViewPayload::encode, CampaignConversationViewPayload::decode);

    public CampaignConversationViewPayload {
        topics = List.copyOf(topics);
        if (viewId == null || character == null || entityId < 0 || page < 0 || pageCount < 1 || page >= pageCount || topics.size() > PAGE_SIZE
                || session != null && !topics.isEmpty()) throw new IllegalArgumentException("Invalid campaign conversation view");
        bounded(diagnostic, MAX_TEXT_LENGTH);
    }

    public record Topic(String conversation, String id, String label, boolean available) {
        public Topic { bounded(conversation, 256); bounded(id, 256); bounded(label, MAX_LABEL_LENGTH); }
    }
    public record Response(String id, String label, boolean available) {
        public Response { bounded(id, 256); bounded(label, MAX_LABEL_LENGTH); }
    }
    public record Member(UUID id, String name) {
        public Member { if (id == null) throw new IllegalArgumentException("Missing conversation member"); bounded(name, MAX_LABEL_LENGTH); }
    }
    public record Line(String key, UUID speaker, int entityId, String name, String text, int part, int parts) {
        public Line { if (part < 0 || parts < 1 || part >= parts) throw new IllegalArgumentException("Invalid conversation text page"); bounded(key, 512); bounded(name, MAX_LABEL_LENGTH); bounded(text, MAX_TEXT_LENGTH); }
    }
    public record Check(String id, int difficulty, boolean resolved, int die, boolean success) {
        public Check {
            bounded(id, 256);
            if (difficulty < 0 || (resolved ? die < 1 || die > 20 || success != (die == 20 || die != 1 && die >= difficulty) : die != 0 || success)) {
                throw new IllegalArgumentException("Invalid conversation check presentation");
            }
        }
    }
    public record Session(UUID id, long revision, String title, ConversationSession.Phase phase, Line line,
            boolean canControl, boolean canTakeControl, String controller, List<Member> handover,
            List<Response> responses, Check check, String outcome) {
        public Session {
            handover = List.copyOf(handover);
            responses = List.copyOf(responses);
            if (id == null || revision < 1 || phase == null || handover.size() > 32 || responses.size() > PAGE_SIZE) {
                throw new IllegalArgumentException("Invalid conversation session presentation");
            }
            bounded(title, MAX_LABEL_LENGTH); bounded(controller, MAX_LABEL_LENGTH); bounded(outcome, MAX_LABEL_LENGTH);
        }
    }

    private static void bounded(String value, int maximum) {
        if (value == null || value.length() > maximum) throw new IllegalArgumentException("Campaign conversation text exceeds its network limit");
    }

    private static void encode(RegistryFriendlyByteBuf buffer, CampaignConversationViewPayload value) {
        buffer.writeUUID(value.viewId()); buffer.writeVarInt(value.entityId()); buffer.writeUUID(value.character());
        buffer.writeVarInt(value.page()); buffer.writeVarInt(value.pageCount());
        buffer.writeVarInt(value.topics().size());
        for (var topic : value.topics()) {
            buffer.writeUtf(topic.conversation(), 256); buffer.writeUtf(topic.id(), 256);
            buffer.writeUtf(topic.label(), MAX_LABEL_LENGTH); buffer.writeBoolean(topic.available());
        }
        buffer.writeBoolean(value.session() != null);
        if (value.session() != null) writeSession(buffer, value.session());
        buffer.writeUtf(value.diagnostic(), MAX_TEXT_LENGTH);
    }

    private static CampaignConversationViewPayload decode(RegistryFriendlyByteBuf buffer) {
        UUID view = buffer.readUUID(); int entity = buffer.readVarInt(); UUID character = buffer.readUUID();
        int page = buffer.readVarInt(); int pages = buffer.readVarInt();
        var topics = new ArrayList<Topic>();
        for (int count = count(buffer, PAGE_SIZE); count > 0; count--) {
            topics.add(new Topic(buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(MAX_LABEL_LENGTH), buffer.readBoolean()));
        }
        var session = buffer.readBoolean() ? readSession(buffer) : null;
        return new CampaignConversationViewPayload(view, entity, character, page, pages, topics, session, buffer.readUtf(MAX_TEXT_LENGTH));
    }

    private static void writeSession(RegistryFriendlyByteBuf buffer, Session value) {
        buffer.writeUUID(value.id()); buffer.writeVarLong(value.revision()); buffer.writeUtf(value.title(), MAX_LABEL_LENGTH); buffer.writeEnum(value.phase());
        buffer.writeBoolean(value.line() != null);
        if (value.line() != null) {
            var line = value.line();
            buffer.writeUtf(line.key(), 512); buffer.writeBoolean(line.speaker() != null);
            if (line.speaker() != null) buffer.writeUUID(line.speaker());
            buffer.writeVarInt(line.entityId()); buffer.writeUtf(line.name(), MAX_LABEL_LENGTH); buffer.writeUtf(line.text(), MAX_TEXT_LENGTH);
            buffer.writeVarInt(line.part()); buffer.writeVarInt(line.parts());
        }
        buffer.writeBoolean(value.canControl()); buffer.writeBoolean(value.canTakeControl()); buffer.writeUtf(value.controller(), MAX_LABEL_LENGTH);
        buffer.writeVarInt(value.handover().size());
        for (var member : value.handover()) { buffer.writeUUID(member.id()); buffer.writeUtf(member.name(), MAX_LABEL_LENGTH); }
        buffer.writeVarInt(value.responses().size());
        for (var response : value.responses()) {
            buffer.writeUtf(response.id(), 256); buffer.writeUtf(response.label(), MAX_LABEL_LENGTH); buffer.writeBoolean(response.available());
        }
        buffer.writeBoolean(value.check() != null);
        if (value.check() != null) {
            var check = value.check();
            buffer.writeUtf(check.id(), 256); buffer.writeVarInt(check.difficulty()); buffer.writeBoolean(check.resolved());
            buffer.writeVarInt(check.die()); buffer.writeBoolean(check.success());
        }
        buffer.writeUtf(value.outcome(), MAX_LABEL_LENGTH);
    }

    private static Session readSession(RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID(); long revision = buffer.readVarLong(); String title = buffer.readUtf(MAX_LABEL_LENGTH);
        var phase = buffer.readEnum(ConversationSession.Phase.class);
        Line line = null;
        if (buffer.readBoolean()) line = new Line(buffer.readUtf(512), buffer.readBoolean() ? buffer.readUUID() : null,
                buffer.readVarInt(), buffer.readUtf(MAX_LABEL_LENGTH), buffer.readUtf(MAX_TEXT_LENGTH), buffer.readVarInt(), buffer.readVarInt());
        boolean control = buffer.readBoolean(); boolean take = buffer.readBoolean(); String controller = buffer.readUtf(MAX_LABEL_LENGTH);
        var members = new ArrayList<Member>();
        for (int count = count(buffer, 32); count > 0; count--) members.add(new Member(buffer.readUUID(), buffer.readUtf(MAX_LABEL_LENGTH)));
        var responses = new ArrayList<Response>();
        for (int count = count(buffer, PAGE_SIZE); count > 0; count--) responses.add(new Response(buffer.readUtf(256), buffer.readUtf(MAX_LABEL_LENGTH), buffer.readBoolean()));
        Check check = buffer.readBoolean() ? new Check(buffer.readUtf(256), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(), buffer.readBoolean()) : null;
        return new Session(id, revision, title, phase, line, control, take, controller, members, responses, check, buffer.readUtf(MAX_LABEL_LENGTH));
    }

    private static int count(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid conversation collection size");
        return count;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
