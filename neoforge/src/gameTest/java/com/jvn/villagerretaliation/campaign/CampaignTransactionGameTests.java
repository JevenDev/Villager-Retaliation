package com.jvn.villagerretaliation.campaign;

import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.skill.player.PlayerSocialSkill;
import com.jvn.villagerretaliation.skill.player.SocialCheckBreakdown;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignTransactionGameTests {
    private static final ResourceLocation CAMPAIGN = ResourceLocation.parse("test:transactions");
    private static final ResourceLocation QUEST = ResourceLocation.parse("test:purchase");
    private static final ResourceLocation CONVERSATION = ResourceLocation.parse("test:bargaining");
    private static final String FINGERPRINT = "a".repeat(64);

    private CampaignTransactionGameTests() { }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void reopenedNegotiationsKeepTheirRollPriceAndExperienceIdentity(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var terms = terms(player);
            var source = source();
            var draws = new AtomicInteger();
            var original = disk.open().negotiate(terms, source, () -> { draws.incrementAndGet(); return check(player, 12); }, 2);
            var restored = disk.load();
            var replay = restored.negotiate(terms, source(), () -> { draws.incrementAndGet(); return check(player, 1); }, 20);
            helper.assertValueEqual(replay, original, "another session reuses the original negotiation");
            helper.assertValueEqual(draws.get(), 1, "reopening cannot reroll");
            helper.assertValueEqual(replay.price(), 5, "the agreed price survives reload");
            helper.assertValueEqual(replay.negotiation().experienceAward(), original.negotiation().experienceAward(), "experience retains its original session and request");
            var presented = replay.negotiation().forRequest(UUID.randomUUID());
            helper.assertTrue(replay.negotiation().matches(presented), "a new request can present the same saved check");
            helper.assertValueEqual(presented.rolledAt(), original.negotiation().check().rolledAt(), "the original roll time remains visible");
            var item = replay.terms().unit();
            item.putInt("count", 0);
            helper.assertValueEqual(replay.terms().unit().getInt("count"), 1, "captured payment items cannot be mutated through an accessor");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void failedNegotiationWritesKeepTheFirstUnpublishedRollForRetry(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var terms = terms(player);
            var data = disk.open();
            var source = source();
            var draws = new AtomicInteger();
            disk.block();
            for (int attempt = 0; attempt < 2; attempt++)
                expectIo(helper, () -> data.negotiate(terms, source, () -> { draws.incrementAndGet(); return check(player, 1); }, 2));
            helper.assertValueEqual(draws.get(), 1, "storage failures do not draw another die");
            helper.assertTrue(data.receipts().isEmpty(), "failed storage publishes no negotiation");
            helper.assertFalse(data.issue(terms.key().operation()).isBlank(), "pending negotiation explains the storage problem");
            expectIo(helper, () -> data.preparePayment(terms, 3));
            disk.restore();
            var recovered = data.negotiate(terms, source, () -> { draws.incrementAndGet(); return check(player, 20); }, 4);
            helper.assertValueEqual(recovered.negotiation().check().die(), 1, "storage recovery keeps the first roll");
            helper.assertValueEqual(recovered.price(), 8, "natural failure retains the original price");
            helper.assertValueEqual(draws.get(), 1, "recovery never rerolls");
            helper.assertTrue(data.issue(recovered.operation()).isBlank(), "successful persistence clears the temporary issue");
            helper.assertValueEqual(disk.load().receipts().get(recovered.operation()), recovered, "recovered negotiation is durable");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void playerPaymentSurvivesAnInterruptedWorldAcknowledgement(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            var terms = terms(player);
            var data = disk.open();
            data.negotiate(terms, source(), () -> check(player, 12), 2);
            var payment = data.preparePayment(terms, 3);
            helper.assertTrue(CampaignPlayerRewards.deliver(player, payment.payment(), disk.players), "first payment is saved with the player");
            disk.block();
            expectIo(helper, () -> data.finish(payment.operation(), payment.token(), player.getUUID(), 4));
            helper.assertValueEqual(data.receipts().get(payment.operation()).phase(), CampaignTransaction.Phase.PAYMENT_PENDING, "failed acknowledgement remains pending in committed world state");
            disk.restore();
            var restored = disk.load();
            player.getInventory().clearContent();
            player.getPersistentData().remove(Player.PERSISTED_NBT_TAG);
            player.load(disk.player(player));
            var retry = restored.preparePayment(terms, 5);
            helper.assertFalse(CampaignPlayerRewards.deliver(player, retry.payment(), disk.players), "recovery finds the player's original payment receipt");
            var paid = restored.finish(retry.operation(), retry.token(), player.getUUID(), 6);
            helper.assertValueEqual(player.getInventory().countItem(Items.EMERALD), 11, "interrupted acknowledgement cannot charge twice");
            helper.assertValueEqual(disk.load().receipts().get(paid.operation()).phase(), CampaignTransaction.Phase.PAID, "payment acknowledgement survives reload");
            helper.assertFalse(CampaignPlayerRewards.deliver(player, paid.payment(), disk.players), "a repeated paid transaction has no inventory effect");
            var replay = restored.negotiate(terms, source(), () -> { helper.fail("paid negotiation rerolled"); return check(player, 20); }, 7);
            helper.assertValueEqual(replay.price(), 5, "a paid transaction retains its negotiated terms");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void changedSellersTermsAndSourcesCannotResetABargain(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var terms = terms(player);
            var data = disk.open();
            var source = source();
            var saved = data.negotiate(terms, source, () -> check(player, 12), 2);
            for (var changed : List.of(copy(terms, UUID.randomUUID(), terms.fingerprint(), terms.count()),
                    copy(terms, terms.seller(), "b".repeat(64), terms.count()), copy(terms, terms.seller(), terms.fingerprint(), 9))) {
                helper.assertValueEqual(changed.key().operation(), terms.key().operation(), "changed terms cannot invent a fresh transaction identity");
                expectInvalid(helper, () -> data.preparePayment(changed, 3));
                expectInvalid(helper, () -> data.negotiate(changed, source, () -> check(player, 20), 3));
            }
            var changedSource = new CampaignTransaction.Source(UUID.randomUUID(), CONVERSATION, 2, "b".repeat(64), source.check());
            expectInvalid(helper, () -> data.negotiate(terms, changedSource, () -> check(player, 20), 3));
            helper.assertValueEqual(data.receipts().get(saved.operation()), saved, "rejected changes leave the original receipt intact");
            var otherRun = new CampaignTransaction.Key(terms.key().campaign(), terms.key().instance(), player.getUUID(), QUEST, UUID.randomUUID(), terms.key().transaction());
            var otherPlayer = new CampaignTransaction.Key(terms.key().campaign(), terms.key().instance(), UUID.randomUUID(), QUEST, terms.key().questRun(), terms.key().transaction());
            helper.assertFalse(otherRun.operation().equals(saved.operation()), "a different quest run has its own transaction");
            helper.assertFalse(otherPlayer.operation().equals(saved.operation()), "another player has a personal transaction");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void directPaymentReservesItsPriceBeforeAnyBargaining(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var terms = terms(player);
            var data = disk.open();
            var payment = data.preparePayment(terms, 2);
            helper.assertValueEqual(payment.price(), 8, "direct payment uses the original authored price");
            expectInvalid(helper, () -> data.negotiate(terms, source(), () -> { helper.fail("reserved payment rerolled"); return check(player, 20); }, 3));
            expectInvalid(helper, () -> data.finish(payment.operation(), UUID.randomUUID(), player.getUUID(), 3));
            expectInvalid(helper, () -> data.finish(payment.operation(), payment.token(), UUID.randomUUID(), 3));
            helper.assertValueEqual(disk.load().receipts().get(payment.operation()), payment, "rejected negotiation and acknowledgement leave the reserved price unchanged");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "campaign_transactions")
    public static void malformedAndFutureTransactionRecordsRemainAvailableForRecovery(GameTestHelper helper) throws IOException {
        try (var disk = new Disk()) {
            var player = helper.makeMockServerPlayerInLevel();
            var terms = terms(player);
            var data = disk.open();
            data.negotiate(terms, source(), () -> check(player, 12), 2);
            var original = data.save(new CompoundTag(), helper.getLevel().registryAccess());
            for (String mutation : List.of("version", "duplicate", "operation", "count", "unit", "seller", "roll", "buyer", "phase", "stack_size")) {
                var broken = original.copy();
                var receipt = broken.getList("Receipts", Tag.TAG_COMPOUND).getCompound(0);
                switch (mutation) {
                    case "version" -> broken.putInt("DataVersion", CampaignTransactionSavedData.CURRENT_DATA_VERSION + 1);
                    case "duplicate" -> broken.getList("Receipts", Tag.TAG_COMPOUND).add(receipt.copy());
                    case "operation" -> receipt.putUUID("Operation", UUID.randomUUID());
                    case "count" -> receipt.putInt("Count", 0);
                    case "unit" -> receipt.getCompound("Unit").remove("count");
                    case "seller" -> receipt.remove("Seller");
                    case "roll" -> receipt.getCompound("Negotiation").getCompound("Result").putInt("Die", 20);
                    case "buyer" -> receipt.getCompound("Negotiation").getCompound("Result").putUUID("Player", UUID.randomUUID());
                    case "phase" -> receipt.putString("Phase", "UNKNOWN");
                    case "stack_size" -> receipt.putInt("StackSize", 100);
                }
                var blocked = CampaignTransactionSavedData.load(broken, disk.directory);
                helper.assertFalse(blocked.diagnostic().isBlank(), "invalid history is diagnosed: " + mutation);
                helper.assertValueEqual(blocked.save(new CompoundTag(), helper.getLevel().registryAccess()), broken, "invalid history is preserved: " + mutation);
                expectIo(helper, () -> blocked.preparePayment(terms, 3));
            }
        }
        helper.succeed();
    }

    private static CampaignTransaction.Terms terms(ServerPlayer player) {
        var key = new CampaignTransaction.Key(CAMPAIGN, UUID.randomUUID(), player.getUUID(), QUEST, UUID.randomUUID(), "supplies");
        var item = ResourceLocation.parse("minecraft:emerald");
        return new CampaignTransaction.Terms(key, 1, FINGERPRINT, "test.transaction.supplies", "Supplies", "merchant", UUID.randomUUID(),
                ResourceLocation.parse("minecraft:overworld"), item, (CompoundTag) new ItemStack(Items.EMERALD).save(player.registryAccess()), 64, 8, 5, 1);
    }

    private static CampaignTransaction.Terms copy(CampaignTransaction.Terms terms, UUID seller, String fingerprint, int count) {
        return new CampaignTransaction.Terms(terms.key(), terms.definitionRevision(), fingerprint, terms.titleKey(), terms.title(), terms.character(),
                seller, terms.dimension(), terms.item(), terms.unit(), terms.stackSize(), count, terms.bargainedCount(), terms.capturedAt());
    }

    private static CampaignTransaction.Source source() { return new CampaignTransaction.Source(UUID.randomUUID(), CONVERSATION, 1, FINGERPRINT, "price"); }

    private static ConversationSession.Check check(ServerPlayer player, int die) {
        var social = new SocialCheckBreakdown(ResourceLocation.parse("test:social"), 1, FINGERPRINT, PlayerSocialSkill.PERSUASION, 25, 1, 20, 2, 7);
        boolean success = die == 20 || die != 1 && die + 2 >= 10;
        return new ConversationSession.Check(UUID.randomUUID(), "price", die, 10, 2, die + 2, success, success ? "agreed" : "declined", player.getUUID(), 2, social);
    }

    private static void expectInvalid(GameTestHelper helper, Checked action) throws IOException {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        helper.fail("invalid campaign transaction was accepted");
    }

    private static void expectIo(GameTestHelper helper, Checked action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        helper.fail("unavailable transaction storage accepted a change");
    }

    @FunctionalInterface private interface Checked { void run() throws IOException; }

    private static final class Disk implements AutoCloseable {
        private final Path root = Files.createTempDirectory("campaign-transactions-");
        private final Path directory = root.resolve("data");
        private final Path players = root.resolve("players");
        private final Path file = directory.resolve(CampaignTransactionSavedData.DATA_NAME + ".dat");
        private final Path backup = root.resolve("before.dat");

        private Disk() throws IOException { }
        private CampaignTransactionSavedData open() { return CampaignTransactionSavedData.load(new CompoundTag(), directory); }
        private CampaignTransactionSavedData load() throws IOException { return CampaignTransactionSavedData.load(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data"), directory); }
        private CompoundTag player(ServerPlayer player) throws IOException { return NbtIo.readCompressed(players.resolve(player.getStringUUID() + ".dat"), NbtAccounter.unlimitedHeap()); }

        private void block() throws IOException {
            if (Files.exists(file)) Files.move(file, backup);
            Files.createDirectories(file);
            Files.writeString(file.resolve("blocked"), "storage failure fixture");
        }

        private void restore() throws IOException {
            Files.delete(file.resolve("blocked"));
            Files.delete(file);
            if (Files.exists(backup)) Files.move(backup, file);
        }

        @Override public void close() throws IOException {
            IOUtilities.waitUntilIOWorkerComplete();
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
