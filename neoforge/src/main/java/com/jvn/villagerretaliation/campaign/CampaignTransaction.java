package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.campaign.content.CampaignTransactionDefinition;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.skill.player.PlayerSocialAward;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class CampaignTransaction {
    private CampaignTransaction() { }

    static void require(boolean value, String issue) { if (!value) throw new IllegalArgumentException(issue); }
    private static boolean id(String value) { return value != null && value.length() <= 256 && CampaignFactFields.validId(value); }
    private static boolean fingerprint(String value) { return value != null && value.matches("[0-9a-f]{64}"); }

    public record Key(ResourceLocation campaign, UUID instance, UUID player, ResourceLocation quest, UUID questRun, String transaction) {
        public Key {
            require(campaign != null && instance != null && player != null && quest != null && questRun != null && id(transaction),
                    "Invalid campaign transaction identity");
        }

        public UUID operation() {
            return UUID.nameUUIDFromBytes((campaign + "|" + instance + "|transaction|" + player + "|" + quest + "|" + questRun + "|" + transaction)
                    .getBytes(StandardCharsets.UTF_8));
        }
    }

    public record Terms(Key key, int definitionRevision, String fingerprint, String titleKey, String title,
            String character, UUID seller, ResourceLocation dimension, ResourceLocation item, CompoundTag unit,
            int stackSize, int count, Integer bargainedCount, long capturedAt) {
        public Terms {
            require(key != null && definitionRevision > 0 && CampaignTransaction.fingerprint(fingerprint)
                    && titleKey != null && !titleKey.isBlank() && title != null && !title.isBlank() && id(character)
                    && seller != null && dimension != null && item != null && !item.equals(ResourceLocation.parse("minecraft:air"))
                    && unit != null && unit.contains("id", Tag.TAG_STRING) && item.toString().equals(unit.getString("id"))
                    && unit.contains("count", Tag.TAG_INT) && unit.getInt("count") == 1
                    && stackSize > 0 && stackSize <= Item.ABSOLUTE_MAX_STACK_SIZE
                    && count > 0 && count <= CampaignTransactionDefinition.MAX_ITEM_COUNT
                    && (bargainedCount == null || bargainedCount >= 0 && bargainedCount < count) && capturedAt >= 0,
                    "Invalid saved campaign transaction terms");
            unit = unit.copy();
        }

        @Override public CompoundTag unit() { return unit.copy(); }

        public boolean compatible(Terms other) {
            return key.equals(other.key) && definitionRevision == other.definitionRevision && fingerprint.equals(other.fingerprint)
                    && titleKey.equals(other.titleKey) && character.equals(other.character) && seller.equals(other.seller)
                    && dimension.equals(other.dimension) && item.equals(other.item) && unit.equals(other.unit)
                    && stackSize == other.stackSize && count == other.count && Objects.equals(bargainedCount, other.bargainedCount);
        }

        public List<CompoundTag> costs(int price) {
            require(price >= 0 && price <= count, "Campaign payment differs from its captured terms");
            var costs = new ArrayList<CompoundTag>();
            for (int remaining = price; remaining > 0;) {
                int amount = Math.min(remaining, stackSize);
                var cost = unit.copy();
                cost.putInt("count", amount);
                costs.add(cost);
                remaining -= amount;
            }
            return List.copyOf(costs);
        }
    }

    public record Source(UUID session, ResourceLocation conversation, int revision, String fingerprint, String check) {
        public Source {
            require(session != null && conversation != null && revision > 0 && CampaignTransaction.fingerprint(fingerprint) && id(check),
                    "Invalid campaign bargaining source");
        }

        public boolean compatible(Source other) {
            return conversation.equals(other.conversation) && revision == other.revision && fingerprint.equals(other.fingerprint) && check.equals(other.check);
        }
    }

    public record Negotiation(Source source, ConversationSession.Check check) {
        public Negotiation {
            require(source != null && check != null && source.check.equals(check.id()) && check.request() != null && check.player() != null
                    && id(check.destination()) && check.rolledAt() >= 0 && check.social() != null && check.social().skill() == PlayerSocialSkill.PERSUASION,
                    "Campaign bargaining requires a saved persuasion check");
        }

        public ConversationSession.Check forRequest(UUID request) {
            require(request != null, "Bargaining presentation requires a request identity");
            return new ConversationSession.Check(request, check.id(), check.die(), check.difficulty(), check.modifier(), check.total(),
                    check.success(), check.destination(), check.player(), check.rolledAt(), check.social(), check.modifiers());
        }

        public boolean matches(ConversationSession.Check presented) {
            return presented != null && forRequest(presented.request()).equals(presented);
        }

        public PlayerSocialAward experienceAward() {
            return new PlayerSocialAward(source.session, check.id(), check.request(), check.player(), check.social());
        }
    }

    public enum Phase { NEGOTIATED, PAYMENT_PENDING, PAID }

    public record Receipt(Terms terms, UUID token, Negotiation negotiation, Phase phase, long updatedAt) {
        public Receipt {
            require(terms != null && token != null && phase != null && updatedAt >= terms.capturedAt
                    && (phase != Phase.NEGOTIATED || negotiation != null)
                    && (negotiation == null || terms.bargainedCount != null && terms.key.player.equals(negotiation.check.player())),
                    "Invalid campaign transaction receipt");
        }

        public UUID operation() { return terms.key.operation(); }
        public int price() { return negotiation != null && negotiation.check.success() ? terms.bargainedCount : terms.count; }

        public java.util.Map<String, String> replacements(String buyer) {
            return java.util.Map.of("campaign_transaction", terms.key.transaction, "campaign_transaction_title", terms.title,
                    "campaign_transaction_buyer", buyer, "campaign_transaction_price", Integer.toString(price()),
                    "campaign_transaction_item", terms.item.toString());
        }

        public Receipt prepare(long time) {
            return phase == Phase.NEGOTIATED ? new Receipt(terms, token, negotiation, Phase.PAYMENT_PENDING, Math.max(updatedAt, time)) : this;
        }

        public Receipt finish(long time) {
            require(phase != Phase.NEGOTIATED, "Campaign payment has not been prepared");
            return phase == Phase.PAID ? this : new Receipt(terms, token, negotiation, Phase.PAID, Math.max(updatedAt, time));
        }

        public CampaignPlayerRewards.Delivery payment() {
            require(phase != Phase.NEGOTIATED, "Campaign payment has not been prepared");
            return new CampaignPlayerRewards.Delivery(operation(), token, terms.key.player, 0, List.of(), phase == Phase.PAID, terms.costs(price()));
        }
    }
}
