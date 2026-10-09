package com.jvn.villagerretaliation.campaign;

import static com.jvn.villagerretaliation.campaign.CampaignConversationBargainingGameTests.*;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.campaign.content.CampaignContentCatalog;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.dialogue.conversation.ConversationSession;
import com.jvn.villagerretaliation.interaction.VillagerInteractionService;
import com.jvn.villagerretaliation.skill.player.SocialProgressionTestStorage;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("campaign_state")
@PrefixGameTestTemplate(false)
public final class CampaignTransactionInspectionGameTests {
    private CampaignTransactionInspectionGameTests() { }

    @GameTest(template = "empty", batch = "conversation_bargaining")
    public static void inspectionRetainsPendingPaidAndDormantTransactionEvidence(GameTestHelper helper) throws IOException {
        try (var fixture = fixture(helper); var transactions = new CampaignTransactionTestStorage(helper.getLevel());
                var social = new SocialProgressionTestStorage(helper.getLevel())) {
            var player = fixture.player();
            startQuest(fixture, player);
            player.getInventory().items.set(0, new ItemStack(Items.EMERALD, 16));
            var result = ready(fixture, player);
            transactions.blockWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            var pending = CampaignTransactionInspection.inspect(fixture.base.server, fixture.base.campaignId);
            helper.assertValueEqual(pending.transactions().size(), 1, "failed storage retains pending terms for inspection");
            helper.assertFalse(pending.transactions().getFirst().persisted(), "inspection distinguishes unpublished terms");
            helper.assertFalse(pending.transactions().getFirst().diagnostics().isEmpty(), "pending terms explain their storage failure");
            helper.assertTrue(transactions.data().receipts().isEmpty(), "inspection cannot publish a pending roll");
            transactions.restoreWrites();
            result = fixture.intent(player, result, ConversationSession.Intent.ROLL, "");
            var raw = com.google.gson.JsonParser.parseString("""
                    {"actions":[{"type":"campaign_payment","transaction":"supplies"}]}
                    """).getAsJsonObject();
            var action = VillagerActionDefinition.readList(fixture.base.campaignId, "inspection", raw, fixture.base.campaignId).getFirst();
            var context = VillagerInteractionService.createDialogueContext(helper.getLevel(), player, fixture.mentor);
            helper.assertTrue(VillagerActionRegistry.execute(context, action, Map.of()).success(), "fixture payment succeeds");
            transactions.reload();
            var before = transactions.data().save(new CompoundTag(), helper.getLevel().registryAccess());
            var paid = CampaignStateInspection.inspect(fixture.base.server, fixture.base.campaignId).transactions().getFirst();
            helper.assertTrue(paid.persisted() && paid.diagnostics().isEmpty(), "paid terms are readable without repair diagnostics");
            helper.assertValueEqual(paid.receipt().phase(), CampaignTransaction.Phase.PAID, "inspection preserves payment status");
            helper.assertValueEqual(paid.receipt().terms().key().player(), player.getUUID(), "inspection preserves the original buyer");
            var content = CampaignResources.current(fixture.base.server);
            CampaignResources.installForTests(fixture.base.server, new CampaignContentCatalog(content.quests(), Map.of(), List.of()));
            var dormant = CampaignTransactionInspection.inspect(fixture.base.server, fixture.base.campaignId).transactions().getFirst();
            helper.assertValueEqual(dormant.receipt(), paid.receipt(), "missing content leaves transaction history readable");
            helper.assertTrue(dormant.diagnostics().stream().anyMatch(issue -> issue.contains("content is missing")), "dormant terms explain their missing content");
            helper.assertValueEqual(transactions.data().save(new CompoundTag(), helper.getLevel().registryAccess()), before, "inspection preserves transaction save bytes");
            CampaignResources.installForTests(fixture.base.server, content);
            var missing = before.copy();
            missing.put("Receipts", new ListTag());
            transactions.install(missing);
            var lost = CampaignStateInspection.inspect(fixture.base.server, fixture.base.campaignId);
            helper.assertTrue(lost.transactions().isEmpty(), "inspection cannot invent a missing receipt");
            helper.assertTrue(lost.diagnostics().stream().anyMatch(issue -> issue.contains(paid.receipt().operation().toString())),
                    "missing receipt diagnostics identify the original operation");
        }
        helper.succeed();
    }
}
