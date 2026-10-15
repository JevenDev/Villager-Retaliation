package com.jvn.villagerretaliation.interaction;

import com.google.gson.Gson;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.quest.tutorial.TutorialRun;
import com.jvn.villagerretaliation.quest.tutorial.TutorialSavedData;
import com.jvn.villagerretaliation.quest.tutorial.TutorialService;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;

/** Explicit one-day consent. Inventory debit and its recovery receipt share the same player save. */
public final class TutorialContinuationService {
    private static final String RECEIPTS = "VillagerRetaliationTutorialPayments";
    private static final Gson GSON = new Gson();
    private TutorialContinuationService() { }
    public static TutorialRun.Quote quote(ServerPlayer player, Villager worker, TutorialRun run) {
        var level = (ServerLevel) worker.level();
        var role = HiredVillagerRole.bySerializedName(run.binding().role());
        return new TutorialRun.Quote(UUID.randomUUID(), run.binding().assignment(), run.binding().generation(),
                run.binding().role(), BuiltInRegistries.ITEM.getKey(VillagerCurrencyResources.primaryItem(level.getServer())).toString(),
                1, HiredVillagerContractService.getHireCost(level, worker, player, 1, role),
                "One Minecraft day of elapsed game time, including rest, setup and owner-offline time. Automatic payment OFF.");
    }
    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action, TutorialRun run, Villager worker) {
        if (!run.completed()) return TutorialService.result(action, false, "Finish the finite lesson before considering paid continuation.");
        if (run.binding().role().equals("builder"))
            return TutorialService.result(action, false, "Order a new structure through the ordinary Builder service and confirm its separate blueprint and construction quotes. No daily contract is offered.");
        if (run.state() == TutorialRun.State.CONVERTED) return TutorialService.result(action, true, "Your accepted continuation was already recorded. No second payment was taken.");
        if (run.state() != TutorialRun.State.AWAITING_CHOICE) return TutorialService.result(action, false, "This tutorial assignment has already ended.");
        var current = quote(context.player(), worker, run);
        var data = TutorialSavedData.get(context.level());
        if (action.factValue().equals("quote")) {
            run.offer(current); data.setDirty();
            return TutorialService.result(action, true, "Keep " + worker.getName().getString() + " working as your " + HiredVillagerRole.bySerializedName(current.role()).label() + " for one Minecraft day at "
                    + VillagerCurrencyResources.format(context.level().getServer(), current.total()) + "? " + current.terms()
                    + " Choose Accept quoted fee and continue, or Stop. Asking for this quote takes no payment.");
        }
        if(context.player().getPersistentData().getCompound(RECEIPTS).contains(run.trial().toString())){
            boolean recovered=reconcile(context.player(),worker,run);
            return TutorialService.result(action,recovered,recovered?"The recorded payment was recovered without another charge.":"The recorded payment needs assignment recovery; no second charge was attempted.");
        }
        var saved = run.snapshot().quote();
        if (saved == null) return TutorialService.result(action, false, "Ask for the current fee first. No payment has been authorized yet.");
        if (!run.accepts(saved.id(), current)) return TutorialService.result(action, false,
                "The fee or currency changed. No payment was taken. Ask for the current quote again before accepting.");
        var contract = HireContractStore.load(worker).orElse(null);
        if (contract == null || !contract.isActive() || !contract.tutorialTrial() || !contract.id().equals(run.binding().assignment())
                || !contract.tutorialId().filter(run.trial()::equals).isPresent()
                || !contract.owner().filter(context.player().getUUID()::equals).isPresent())
            return TutorialService.result(action, false, "The worker's assignment changed. No payment was taken; a later contract will not be overwritten.");
        CompoundTag receipts = context.player().getPersistentData().getCompound(RECEIPTS);
        String key = run.trial().toString();
        if (!receipts.contains(key)) {
            if (!VillagerCurrencyPayment.tryRemove(context.player(), saved.total())) return TutorialService.result(action, false,
                    "You do not have the quoted currency. Work remains paused; retry after obtaining it, or choose Stop.");
            var paid = HireContract.regular(context.player().getUUID(), context.level().getGameTime(), saved.durationDays(), saved.total(), saved.total(),
                    HiredVillagerRole.bySerializedName(saved.role()));
            var receipt = new CompoundTag();
            receipt.putUUID("Worker", worker.getUUID()); receipt.putUUID("OldAssignment", run.binding().assignment());
            receipt.putUUID("Receipt", UUID.randomUUID()); receipt.put("PaidContract", paid.encoded().copy());
            receipt.putString("Quote", GSON.toJson(saved)); receipts.put(key, receipt);
            context.player().getPersistentData().put(RECEIPTS, receipts);
            // Vanilla saves the inventory and this marker together. A replay recovers this receipt instead of debiting again.
            context.level().getServer().getPlayerList().saveAll();
        }
        boolean resumed = reconcile(context.player(), worker, run);
        return TutorialService.result(action, resumed, resumed ? "The accepted one-day fee was paid once. Normal work may continue; automatic payment remains off."
                : "Your payment receipt is retained for recovery. Work remains paused until the assignment can be restored safely.");
    }
    public static boolean reconcile(ServerPlayer player, Villager worker, TutorialRun run) {
        var receipt = player.getPersistentData().getCompound(RECEIPTS).getCompound(run.trial().toString());
        if (!receipt.hasUUID("Worker") || !receipt.getUUID("Worker").equals(worker.getUUID()) || !receipt.hasUUID("Receipt")) return false;
        var paid = new HireContract(receipt.getCompound("PaidContract").copy());
        if (!paid.owner().filter(player.getUUID()::equals).isPresent() || paid.tutorialTrial()) return false;
        var current = HireContractStore.load(worker).orElse(null);
        if (current == null || !current.id().equals(paid.id())) {
            if (current == null || !current.tutorialTrial() || !current.id().equals(receipt.getUUID("OldAssignment"))) return false;
            if (!HiredVillagerContractService.convertTutorialContract((ServerLevel) worker.level(), worker,
                    player.getUUID(), run.binding().assignment(), run.trial(), paid)) return false;
        }
        if (run.state() == TutorialRun.State.AWAITING_CHOICE) {
            TutorialRun.Quote accepted = GSON.fromJson(receipt.getString("Quote"), TutorialRun.Quote.class);
            run.offer(accepted);
            run.converted(accepted.id(), accepted, receipt.getUUID("Receipt"));
            TutorialSavedData.get((ServerLevel) worker.level()).setDirty();
        }
        return run.state() == TutorialRun.State.CONVERTED;
    }
}
