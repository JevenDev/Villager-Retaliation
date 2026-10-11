package com.jvn.villagerretaliation.network;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationLinePresentation;
import net.minecraft.resources.ResourceLocation;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationCheckModifier;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.skill.player.SocialProgressionSchema;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CampaignConversationViewPayload(UUID viewId, int entityId, UUID character, int page, int pageCount,
        List<Topic> topics, Session session, String diagnostic) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 32;
    public static final int MAX_TRANSACTIONS = 128;
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

    public enum TopicGroup { NEW_BUSINESS, CURRENT_OBJECTIVES, REPORTING_BACK, FOLLOW_UPS, GENERAL, HISTORY }

    public record Topic(String conversation, String id, String label, boolean available, TopicGroup group) {
        public Topic { bounded(conversation, 256); bounded(id, 256); bounded(label, MAX_LABEL_LENGTH); java.util.Objects.requireNonNull(group); }
        public Topic(String conversation, String id, String label, boolean available) { this(conversation, id, label, available, TopicGroup.GENERAL); }
    }
    public record Response(String id, String label, boolean available, String reason) {
        public Response { bounded(id, 256); bounded(label, MAX_LABEL_LENGTH); bounded(reason, MAX_LABEL_LENGTH); }
        public Response(String id, String label, boolean available) { this(id, label, available, ""); }
    }
    public record History(int page, int pages, List<String> entries) {
        public History {
            entries = List.copyOf(entries);
            if (page < 0 || pages < 1 || page >= pages || entries.size() > 8) throw new IllegalArgumentException("Invalid conversation history page");
            entries.forEach(entry -> bounded(entry, MAX_TEXT_LENGTH));
        }
    }
    public record Member(UUID id, String name) {
        public Member { if (id == null) throw new IllegalArgumentException("Missing conversation member"); bounded(name, MAX_LABEL_LENGTH); }
    }
    public record Line(String key, UUID speaker, int entityId, String name, String text, int part, int parts,
            ConversationLinePresentation.Camera camera, ResourceLocation voice, List<CameraSubject> subjects) {
        public Line(String key, UUID speaker, int entityId, String name, String text, int part, int parts,
                ConversationLinePresentation.Camera camera, ResourceLocation voice) {
            this(key, speaker, entityId, name, text, part, parts, camera, voice, List.of());
        }

        public Line {
            subjects = List.copyOf(subjects);
            if (subjects.size() > 64) throw new IllegalArgumentException("Too many camera subjects");
            if (part < 0 || parts < 1 || part >= parts) throw new IllegalArgumentException("Invalid conversation text page");
            bounded(key, 512); bounded(name, MAX_LABEL_LENGTH); bounded(text, MAX_TEXT_LENGTH);
            if (voice != null) bounded(voice.toString(), 256);
        }

        public Line(String key, UUID speaker, int entityId, String name, String text, int part, int parts) {
            this(key, speaker, entityId, name, text, part, parts, null, null);
        }
    }
    public record CameraSubject(UUID id, int entityId) {
        public CameraSubject {
            if (id == null || entityId < 0) throw new IllegalArgumentException("Invalid camera subject");
        }
    }

    public record Modifier(String id, String label, int amount, boolean applied) {
        public Modifier {
            bounded(id, 256); bounded(label, MAX_LABEL_LENGTH);
            if (!id.matches("[a-z0-9][a-z0-9_./-]*") || label.isBlank()
                    || amount < -ConversationCheckModifier.MAX_AMOUNT || amount > ConversationCheckModifier.MAX_AMOUNT)
                throw new IllegalArgumentException("Invalid conversation modifier presentation");
        }
    }
    public record Check(String id, int difficulty, boolean resolved, int die, boolean success,
            int modifier, int total, PlayerSocialSkill skill, int rewardExperience, boolean experienceAwarded, String experiencePlayer,
            List<Modifier> modifiers) {
        public Check {
            bounded(id, 256);
            bounded(experiencePlayer, MAX_LABEL_LENGTH);
            modifiers = List.copyOf(modifiers);
            int situational = modifiers.stream().filter(Modifier::applied).mapToInt(Modifier::amount).sum();
            int skillBonus = modifier - situational;
            if (modifiers.size() > ConversationCheckModifier.MAX_COUNT
                    || modifiers.stream().map(Modifier::id).distinct().count() != modifiers.size()
                    || difficulty < 0 || skillBonus < 0 || skillBonus > SocialProgressionSchema.MAX_BONUS || rewardExperience < 0
                    || skill == null && (skillBonus != 0 || rewardExperience != 0 || experienceAwarded)
                    || (resolved ? die < 1 || die > 20 || total != die + modifier
                            || success != (die == 20 || die != 1 && total >= difficulty)
                            : die != 0 || total != 0 || modifier != 0 || success || experienceAwarded || !modifiers.isEmpty())) {
                throw new IllegalArgumentException("Invalid conversation check presentation");
            }
        }

        public int skillBonus() {
            return modifier - modifiers.stream().filter(Modifier::applied).mapToInt(Modifier::amount).sum();
        }

        public Check(String id, int difficulty, boolean resolved, int die, boolean success,
                int modifier, int total, PlayerSocialSkill skill, int rewardExperience, boolean experienceAwarded, String experiencePlayer) {
            this(id, difficulty, resolved, die, success, modifier, total, skill, rewardExperience, experienceAwarded, experiencePlayer, List.of());
        }

        public Check(String id, int difficulty, boolean resolved, int die, boolean success,
                int modifier, int total, PlayerSocialSkill skill, int rewardExperience, boolean experienceAwarded) {
            this(id, difficulty, resolved, die, success, modifier, total, skill, rewardExperience, experienceAwarded, "", List.of());
        }

        public Check(String id, int difficulty, boolean resolved, int die, boolean success) {
            this(id, difficulty, resolved, die, success, 0, die, null, 0, false, "", List.of());
        }
    }
    public enum TransactionState { QUOTED, NEGOTIATED, PAYMENT_PENDING, PAID }

    public record Transaction(String quest, String id, String response, String check, String title, UUID buyer, String buyerName,
            String item, int basePrice, int bargainPrice, int price, TransactionState state, boolean possible, String diagnostic) {
        public Transaction {
            bounded(quest, 256); bounded(id, 256); bounded(response, 256); bounded(check, 256);
            bounded(title, MAX_LABEL_LENGTH); bounded(buyerName, MAX_LABEL_LENGTH); bounded(item, 256); bounded(diagnostic, MAX_LABEL_LENGTH);
            if (net.minecraft.resources.ResourceLocation.tryParse(quest) == null || net.minecraft.resources.ResourceLocation.tryParse(item) == null
                    || !id.matches("[a-z0-9][a-z0-9_./-]*") || buyer == null || state == null
                    || basePrice < 1 || basePrice > 256 || bargainPrice < -1 || bargainPrice >= basePrice
                    || price != basePrice && price != bargainPrice || price < 0
                    || state == TransactionState.QUOTED && price != basePrice)
                throw new IllegalArgumentException("Invalid conversation transaction presentation");
        }
    }

    public record Session(UUID id, long revision, String title, ConversationSession.Phase phase, Line line,
            boolean canControl, boolean canTakeControl, String controller, List<Member> handover,
            List<Response> responses, Check check, String outcome, UUID confirmation, List<Transaction> transactions,
            boolean canCancel, boolean canMigrate, History history) {
        public Session(UUID id, long revision, String title, ConversationSession.Phase phase, Line line,
                boolean canControl, boolean canTakeControl, String controller, List<Member> handover,
                List<Response> responses, Check check, String outcome, UUID confirmation, List<Transaction> transactions) {
            this(id, revision, title, phase, line, canControl, canTakeControl, controller, handover, responses, check, outcome,
                    confirmation, transactions, false, false, null);
        }
        public Session {
            handover = List.copyOf(handover);
            responses = List.copyOf(responses);
            transactions = List.copyOf(transactions);
            if (transactions.size() > MAX_TRANSACTIONS) throw new IllegalArgumentException("Too many conversation transactions");
            if (id == null || revision < 1 || phase == null || handover.size() > 32 || responses.size() > PAGE_SIZE) {
                throw new IllegalArgumentException("Invalid conversation session presentation");
            }
            if ((phase == ConversationSession.Phase.CONFIRMATION) != (confirmation != null)) throw new IllegalArgumentException("Invalid conversation confirmation presentation");
            bounded(title, MAX_LABEL_LENGTH); bounded(controller, MAX_LABEL_LENGTH); bounded(outcome, MAX_LABEL_LENGTH);
        }
        public Session(UUID id, long revision, String title, ConversationSession.Phase phase, Line line,
                boolean canControl, boolean canTakeControl, String controller, List<Member> handover,
                List<Response> responses, Check check, String outcome, UUID confirmation) {
            this(id, revision, title, phase, line, canControl, canTakeControl, controller, handover, responses, check, outcome, confirmation, List.of());
        }

        public Session(UUID id, long revision, String title, ConversationSession.Phase phase, Line line,
                boolean canControl, boolean canTakeControl, String controller, List<Member> handover,
                List<Response> responses, Check check, String outcome) {
            this(id, revision, title, phase, line, canControl, canTakeControl, controller, handover, responses, check, outcome, null);
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
            buffer.writeUtf(topic.label(), MAX_LABEL_LENGTH); buffer.writeBoolean(topic.available()); buffer.writeEnum(topic.group());
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
            topics.add(new Topic(buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(MAX_LABEL_LENGTH), buffer.readBoolean(), buffer.readEnum(TopicGroup.class)));
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
            com.jvn.villagerretaliation.dialogue.DialogueCamera.write(buffer, line.camera());
            buffer.writeBoolean(line.voice() != null);
            if (line.voice() != null) buffer.writeUtf(line.voice().toString(), 256);
            buffer.writeVarInt(line.subjects().size());
            for (var subject : line.subjects()) { buffer.writeUUID(subject.id()); buffer.writeVarInt(subject.entityId()); }
        }
        buffer.writeBoolean(value.canControl()); buffer.writeBoolean(value.canTakeControl()); buffer.writeUtf(value.controller(), MAX_LABEL_LENGTH);
        buffer.writeVarInt(value.handover().size());
        for (var member : value.handover()) { buffer.writeUUID(member.id()); buffer.writeUtf(member.name(), MAX_LABEL_LENGTH); }
        buffer.writeVarInt(value.responses().size());
        for (var response : value.responses()) {
            buffer.writeUtf(response.id(), 256); buffer.writeUtf(response.label(), MAX_LABEL_LENGTH); buffer.writeBoolean(response.available()); buffer.writeUtf(response.reason(), MAX_LABEL_LENGTH);
        }
        buffer.writeBoolean(value.check() != null);
        if (value.check() != null) {
            var check = value.check();
            buffer.writeUtf(check.id(), 256); buffer.writeVarInt(check.difficulty()); buffer.writeBoolean(check.resolved());
            buffer.writeVarInt(check.die()); buffer.writeBoolean(check.success());
            buffer.writeVarInt(check.modifier()); buffer.writeVarInt(check.total());
            buffer.writeBoolean(check.skill() != null);
            if (check.skill() != null) buffer.writeEnum(check.skill());
            buffer.writeVarInt(check.rewardExperience()); buffer.writeBoolean(check.experienceAwarded());
            buffer.writeUtf(check.experiencePlayer(), MAX_LABEL_LENGTH);
            buffer.writeVarInt(check.modifiers().size());
            for (var modifier : check.modifiers()) {
                buffer.writeUtf(modifier.id(), 256); buffer.writeUtf(modifier.label(), MAX_LABEL_LENGTH);
                buffer.writeVarInt(modifier.amount()); buffer.writeBoolean(modifier.applied());
            }
        }
        buffer.writeUtf(value.outcome(), MAX_LABEL_LENGTH);
        buffer.writeBoolean(value.confirmation() != null);
        if (value.confirmation() != null) buffer.writeUUID(value.confirmation());
        buffer.writeBoolean(value.canCancel()); buffer.writeBoolean(value.canMigrate());
        buffer.writeBoolean(value.history() != null);
        if (value.history() != null) {
            buffer.writeVarInt(value.history().page()); buffer.writeVarInt(value.history().pages());
            buffer.writeVarInt(value.history().entries().size());
            for (String entry : value.history().entries()) buffer.writeUtf(entry, MAX_TEXT_LENGTH);
        }
        buffer.writeVarInt(value.transactions().size());
        for (var transaction : value.transactions()) {
            buffer.writeUtf(transaction.quest(), 256); buffer.writeUtf(transaction.id(), 256);
            buffer.writeUtf(transaction.response(), 256); buffer.writeUtf(transaction.check(), 256); buffer.writeUtf(transaction.title(), MAX_LABEL_LENGTH);
            buffer.writeUUID(transaction.buyer()); buffer.writeUtf(transaction.buyerName(), MAX_LABEL_LENGTH); buffer.writeUtf(transaction.item(), 256);
            buffer.writeVarInt(transaction.basePrice()); buffer.writeVarInt(transaction.bargainPrice()); buffer.writeVarInt(transaction.price());
            buffer.writeEnum(transaction.state()); buffer.writeBoolean(transaction.possible()); buffer.writeUtf(transaction.diagnostic(), MAX_LABEL_LENGTH);
        }
    }

    private static Session readSession(RegistryFriendlyByteBuf buffer) {
        UUID id = buffer.readUUID(); long revision = buffer.readVarLong(); String title = buffer.readUtf(MAX_LABEL_LENGTH);
        var phase = buffer.readEnum(ConversationSession.Phase.class);
        Line line = null;
        if (buffer.readBoolean()) line = readLine(buffer);
        boolean control = buffer.readBoolean(); boolean take = buffer.readBoolean(); String controller = buffer.readUtf(MAX_LABEL_LENGTH);
        var members = new ArrayList<Member>();
        for (int count = count(buffer, 32); count > 0; count--) members.add(new Member(buffer.readUUID(), buffer.readUtf(MAX_LABEL_LENGTH)));
        var responses = new ArrayList<Response>();
        for (int count = count(buffer, PAGE_SIZE); count > 0; count--) responses.add(new Response(buffer.readUtf(256), buffer.readUtf(MAX_LABEL_LENGTH), buffer.readBoolean(), buffer.readUtf(MAX_LABEL_LENGTH)));
        Check check = null;
        if (buffer.readBoolean()) check = new Check(buffer.readUtf(256), buffer.readVarInt(), buffer.readBoolean(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean() ? buffer.readEnum(PlayerSocialSkill.class) : null,
                buffer.readVarInt(), buffer.readBoolean(), buffer.readUtf(MAX_LABEL_LENGTH), readModifiers(buffer));
        String outcome = buffer.readUtf(MAX_LABEL_LENGTH);
        UUID confirmation = buffer.readBoolean() ? buffer.readUUID() : null;
        boolean canCancel = buffer.readBoolean(); boolean canMigrate = buffer.readBoolean();
        History history = null;
        if (buffer.readBoolean()) {
            int historyPage = buffer.readVarInt(); int historyPages = buffer.readVarInt();
            var entries = new ArrayList<String>();
            for (int count = count(buffer, 8); count > 0; count--) entries.add(buffer.readUtf(MAX_TEXT_LENGTH));
            history = new History(historyPage, historyPages, entries);
        }
        var transactions = new ArrayList<Transaction>();
        for (int count = count(buffer, MAX_TRANSACTIONS); count > 0; count--) transactions.add(new Transaction(
                buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(256), buffer.readUtf(MAX_LABEL_LENGTH),
                buffer.readUUID(), buffer.readUtf(MAX_LABEL_LENGTH), buffer.readUtf(256), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readEnum(TransactionState.class), buffer.readBoolean(), buffer.readUtf(MAX_LABEL_LENGTH)));
        return new Session(id, revision, title, phase, line, control, take, controller, members, responses, check, outcome, confirmation, transactions,
                canCancel, canMigrate, history);
    }

    private static Line readLine(RegistryFriendlyByteBuf buffer) {
        String key = buffer.readUtf(512);
        UUID speaker = buffer.readBoolean() ? buffer.readUUID() : null;
        int entity = buffer.readVarInt();
        String name = buffer.readUtf(MAX_LABEL_LENGTH);
        String text = buffer.readUtf(MAX_TEXT_LENGTH);
        int part = buffer.readVarInt();
        int parts = buffer.readVarInt();
        var camera = com.jvn.villagerretaliation.dialogue.DialogueCamera.read(buffer);
        var voice = buffer.readBoolean() ? ResourceLocation.parse(buffer.readUtf(256)) : null;
        var subjects = new ArrayList<CameraSubject>();
        for (int size = count(buffer, 64); size > 0; size--) subjects.add(new CameraSubject(buffer.readUUID(), buffer.readVarInt()));
        return new Line(key, speaker, entity, name, text, part, parts, camera, voice, subjects);
    }

    private static List<Modifier> readModifiers(RegistryFriendlyByteBuf buffer) {
        var modifiers = new ArrayList<Modifier>();
        for (int count = count(buffer, ConversationCheckModifier.MAX_COUNT); count > 0; count--)
            modifiers.add(new Modifier(buffer.readUtf(256), buffer.readUtf(MAX_LABEL_LENGTH), buffer.readVarInt(), buffer.readBoolean()));
        return List.copyOf(modifiers);
    }

    private static int count(RegistryFriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid conversation collection size");
        return count;
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
