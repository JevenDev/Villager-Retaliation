package com.jvn.villagerretaliation.dialogue.conversation;

import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.campaign.CampaignCharacterBinding;
import com.jvn.villagerretaliation.campaign.CampaignCharacterSavedData;
import com.jvn.villagerretaliation.campaign.CampaignQuestAccess;
import com.jvn.villagerretaliation.campaign.CampaignSavedData;
import com.jvn.villagerretaliation.campaign.CampaignTransaction;
import com.jvn.villagerretaliation.campaign.CampaignTransactionSavedData;
import com.jvn.villagerretaliation.campaign.CampaignTransactionSceneFields;
import com.jvn.villagerretaliation.campaign.content.CampaignResources;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Response;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload.Transaction;
import com.jvn.villagerretaliation.network.CampaignConversationViewPayload.TransactionState;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import com.jvn.villagerretaliation.quest.content.QuestContentCatalogs;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

final class ConversationTransactionPresentation {
    private final ServerPlayer viewer;
    private final ConversationSession session;
    private final ConversationDefinition definition;
    private final CampaignTransactionSavedData data;
    private final LinkedHashMap<Key, Transaction> rows = new LinkedHashMap<>();
    private final Set<String> unavailable = new LinkedHashSet<>();
    private final Set<String> issues = new LinkedHashSet<>();
    private boolean blocked;

    private ConversationTransactionPresentation(ServerPlayer viewer, ConversationSession session, ConversationDefinition definition) {
        this.viewer = viewer;
        this.session = session;
        this.definition = definition;
        data = CampaignTransactionSavedData.get(viewer.serverLevel());
    }

    static Report view(ServerPlayer viewer, ConversationSession session, ConversationDefinition definition, List<Response> responses) {
        var projection = new ConversationTransactionPresentation(viewer, session, definition);
        if (definition == null) {
            for (var operation : session.transactionChecks.values()) {
                var receipt = projection.data.receipts().get(operation);
                if (receipt != null) projection.add(receipt.terms().key().quest(), receipt.terms().key().transaction(), "", "", false);
            }
        } else if (session.phase == ConversationSession.Phase.CHECK || session.phase == ConversationSession.Phase.ROLLED) {
            projection.check(session.check, "", new LinkedHashSet<>());
        } else if (session.phase == ConversationSession.Phase.RESPONSES) {
            var node = definition.nodes().get(session.node);
            for (var response : responses) {
                var authored = node.responses().get(response.id());
                projection.effects(authored.effects(), response.id(), false);
                var visited = new LinkedHashSet<String>();
                if (!authored.check().isEmpty()) projection.check(authored.check(), response.id(), visited);
                else projection.nodes(authored.next(), response.id(), visited);
            }
        } else if (session.phase == ConversationSession.Phase.RESPONSE_EFFECTS || session.phase == ConversationSession.Phase.CONFIRMATION) {
            var response = definition.nodes().get(session.node).responses().get(session.response);
            if (response != null) projection.effects(response.effects(), "", false);
        } else if (session.phase == ConversationSession.Phase.NODE_EFFECTS) {
            projection.effects(definition.nodes().get(session.node).effects(), "", false);
        }
        return new Report(List.copyOf(projection.rows.values()), Set.copyOf(projection.unavailable), projection.blocked,
                String.join("\n", projection.issues));
    }

    private void check(String id, String response, Set<String> visited) {
        var check = definition.checks().get(id);
        if (check == null) return;
        if (!check.transaction().isEmpty()) add(definition.quest(), check.transaction(), response, id, false);
        nodes(check.success(), response, visited);
        nodes(check.failure(), response, visited);
    }

    private void nodes(String first, String response, Set<String> visited) {
        String id = first;
        while (!id.isEmpty() && visited.add(id)) {
            var node = definition.nodes().get(id);
            if (node == null) return;
            effects(node.effects(), response, true);
            if (!node.responses().isEmpty()) return;
            id = node.next();
        }
    }

    private void effects(List<ConversationDefinition.Effect> effects, String response, boolean possible) {
        var scenes = new ArrayDeque<ResourceLocation>();
        for (var effect : effects) action(effect.action(), response, possible, scenes);
        var visited = new LinkedHashSet<ResourceLocation>();
        while (!scenes.isEmpty()) {
            var id = scenes.removeFirst();
            if (!visited.add(id)) continue;
            if (visited.size() > 256) { issue(response, "This payment preview exceeds the supported scene dependency limit."); return; }
            var scene = QuestContentCatalogs.current(viewer.server).scenes().get(id);
            if (scene == null) { issue(response, "Restore the missing scene " + id + " to inspect its possible payments."); continue; }
            for (var step : scene.steps().values()) {
                var parameters = step.parameters();
                for (var reference : CampaignTransactionSceneFields.references(parameters))
                    if (reference.payment()) add(reference.quest(), reference.transaction(), response, "", true);
                if (step.type().getPath().equals("action_batch")) for (var action :
                        VillagerActionDefinition.readList(id, "conversation payment preview", parameters))
                    if (action.sceneId() != null) scenes.addLast(action.sceneId());
            }
        }
    }

    private void action(VillagerActionDefinition action, String response, boolean possible, ArrayDeque<ResourceLocation> scenes) {
        if (action.campaignPayment() != null) add(action.questId(), action.campaignPayment().transaction(), response, "", possible);
        if (action.sceneId() != null) scenes.addLast(action.sceneId());
    }

    private void add(ResourceLocation quest, String transaction, String response, String check, boolean possible) {
        var key = new Key(quest, transaction, response);
        var prior = rows.get(key);
        if (prior != null) {
            if (prior.check().isEmpty() && !check.isEmpty() || prior.possible() && !possible)
                rows.put(key, new Transaction(prior.quest(), prior.id(), prior.response(), check.isEmpty() ? prior.check() : check,
                        prior.title(), prior.buyer(), prior.buyerName(), prior.item(), prior.basePrice(), prior.bargainPrice(), prior.price(),
                        prior.state(), prior.possible() && possible, prior.diagnostic()));
            return;
        }
        if (rows.size() == CampaignConversationViewPayload.MAX_TRANSACTIONS) {
            blocked = true;
            issues.add("This conversation page has too many payment terms. Split its choices into smaller authored nodes.");
            return;
        }
        UUID buyer = response.isEmpty() || session.controller == null ? session.actingPlayer : session.controller;
        CampaignTransaction.Receipt receipt = null;
        for (var operation : session.transactionChecks.values()) {
            var linked = data.receipts().get(operation);
            if (linked != null && linked.terms().key().quest().equals(quest) && linked.terms().key().transaction().equals(transaction)) {
                receipt = linked;
                buyer = linked.terms().key().player();
                break;
            }
        }
        var campaign = receipt == null ? CampaignQuestAccess.campaignFor(viewer.server, quest) : receipt.terms().key().campaign();
        var content = campaign == null ? null : CampaignResources.current(viewer.server).campaigns().get(campaign);
        var authored = content == null ? null : content.definition().transactions().get(transaction);
        var progress = quest == null ? null : VillagerQuestSavedData.get(viewer.serverLevel()).get(buyer, quest);
        var instance = campaign == null ? null : CampaignSavedData.get(viewer.serverLevel()).instances().get(campaign);
        UUID operation = instance == null || progress == null || progress.questRunId() == null ? null
                : new CampaignTransaction.Key(campaign, instance.instanceId(), buyer, quest, progress.questRunId(), transaction).operation();
        if (receipt == null && operation != null) receipt = data.receipts().get(operation);
        if (receipt == null && (authored == null || !authored.quest().equals(quest))) {
            issue(response, "Transaction " + transaction + " is unavailable. Restore its campaign and owning quest.");
            return;
        }
        String diagnostic = data.diagnostic();
        String storageIssue = diagnostic.isBlank() && operation != null ? data.issue(operation) : "";
        if (diagnostic.isBlank()) diagnostic = CampaignQuestAccess.contentIssue(viewer.server, quest, campaign);
        if (diagnostic.isBlank() && receipt == null && !possible && (progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE))
            diagnostic = "Start the owning quest before arranging this transaction.";
        if (diagnostic.isBlank() && receipt == null && operation != null && ConversationSavedData.get(viewer.serverLevel()).hasTransaction(operation))
            diagnostic = "Restore the missing transaction receipt before continuing this bargain.";
        if (diagnostic.isBlank() && !response.isEmpty() && session.controller != null && !buyer.equals(session.controller))
            diagnostic = "This bargain belongs to " + ConversationPresentationService.playerName(viewer, buyer) + ". Hand control back to that buyer.";
        if (diagnostic.isBlank() && receipt != null && !check.isEmpty() && receipt.negotiation() == null)
            diagnostic = "Payment already reserved this transaction's price. Its terms cannot be renegotiated.";
        String role = receipt == null ? authored.character() : receipt.terms().character();
        var binding = campaign == null ? null : CampaignCharacterSavedData.get(viewer.serverLevel()).bindings().get(new CampaignCharacterBinding.Key(campaign, role));
        if (diagnostic.isBlank() && receipt != null && (binding == null || !binding.entityId().equals(receipt.terms().seller())))
            diagnostic = "Restore the transaction's original seller binding before continuing.";
        String title = receipt == null ? authored.titleKey() : receipt.terms().title();
        if (content != null) title = content.localization().plainText(viewer.clientInformation().language(),
                receipt == null ? authored.titleKey() : receipt.terms().titleKey()).orElse(title);
        var item = receipt == null ? authored.item() : receipt.terms().item();
        if (diagnostic.isBlank() && !BuiltInRegistries.ITEM.containsKey(item)) diagnostic = "The transaction's payment item is unavailable.";
        int base = receipt == null ? authored.count() : receipt.terms().count();
        Integer bargain = receipt == null ? authored.bargainedCount() : receipt.terms().bargainedCount();
        var state = receipt == null ? TransactionState.QUOTED : TransactionState.valueOf(receipt.phase().name());
        boolean cannotContinue = !diagnostic.isBlank();
        if (diagnostic.isBlank()) diagnostic = storageIssue;
        rows.put(key, new Transaction(quest.toString(), transaction, response, check, label(title), buyer,
                ConversationPresentationService.playerName(viewer, buyer), item.toString(), base, bargain == null ? -1 : bargain,
                receipt == null ? base : receipt.price(), state, possible, label(diagnostic)));
        if (cannotContinue) {
            if (response.isEmpty()) blocked = true;
            else unavailable.add(response);
        }
    }

    private void issue(String response, String issue) {
        issues.add(issue);
        if (response.isEmpty()) blocked = true;
        else unavailable.add(response);
    }

    private static String label(String value) {
        int end = Math.min(value.length(), CampaignConversationViewPayload.MAX_LABEL_LENGTH);
        if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private record Key(ResourceLocation quest, String transaction, String response) { }

    record Report(List<Transaction> transactions, Set<String> unavailableResponses, boolean blocked, String diagnostic) { }
}
