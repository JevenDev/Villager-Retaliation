package com.jvn.villagerretaliation.quest.tutorial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jvn.villagerretaliation.action.ActionResult;
import com.jvn.villagerretaliation.action.VillagerActionDefinition;
import com.jvn.villagerretaliation.action.VillagerActionRegistry;
import com.jvn.villagerretaliation.action.VillagerActionResult;
import com.jvn.villagerretaliation.dialogue.DialogueContext;
import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerContractService;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import com.jvn.villagerretaliation.interaction.HiredVillagerRoles;
import com.jvn.villagerretaliation.interaction.HiredWorkSession;
import com.jvn.villagerretaliation.quest.VillagerQuestSavedData;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/** Lifecycle actions use the ordinary quest stage/response machinery and never infer a nearest worker. */
public final class TutorialService {
    private TutorialService() { }
    public static ResourceLocation questId(HiredVillagerRole role) {
        return ResourceLocation.fromNamespaceAndPath("villagerretaliation", "clipboard_" + role.serializedName());
    }
    public static boolean supported(HiredVillagerRole role) {
        return role == HiredVillagerRole.NITWIT || role == HiredVillagerRole.FARMING || role == HiredVillagerRole.MINING || role == HiredVillagerRole.FISHING || role == HiredVillagerRole.COMBAT || role == HiredVillagerRole.CRAFTSMAN || role == HiredVillagerRole.COOK || role == HiredVillagerRole.SMELTER || role == HiredVillagerRole.LOGGING || role == HiredVillagerRole.HUNTING || role == HiredVillagerRole.ANIMAL_HANDLING || role == HiredVillagerRole.COURIER || role == HiredVillagerRole.BREWING || role == HiredVillagerRole.BUILDER;
    }
    public static boolean eligible(DialogueContext context, HiredVillagerRole role) {
        if (context == null || context.player() == null || context.villager() == null || !supported(role)) return false;
        Villager worker = context.villager();
        var existing = TutorialSavedData.get(context.level()).find(context.player().getUUID(), questId(role).toString()).orElse(null);
        if (existing != null && existing.completed()) return existing.state() == TutorialRun.State.AWAITING_CHOICE;
        if (!worker.isAlive() || worker.isBaby() || !(role == HiredVillagerRole.BUILDER ? HiredVillagerRoles.canOfferBuilderService(context.level(),worker) : HiredVillagerRoles.availableContractRoles(context.level(), worker).contains(role))
                || role == HiredVillagerRole.FARMING && worker.getVillagerData().getProfession() != VillagerProfession.FARMER
                || role == HiredVillagerRole.NITWIT && worker.getVillagerData().getProfession() != VillagerProfession.NITWIT) return false;
        var contract = HireContractStore.load(worker).orElse(null);
        return contract == null || !contract.isActiveOrAwaitingAutoPayment()
                || existing != null && contract.tutorialId().filter(existing.trial()::equals).isPresent();
    }
    public static JsonObject actionSchema() {
        return JsonParser.parseString("""
                {"type":"object","required":["type","role","operation"],"properties":{
                 "type":{"const":"tutorial"},"role":{"enum":["farming","fishing","mining","logging","hunting",
                    "animal_handling","brewing","craftsman","cook","smelter","courier","nitwit","combat","builder"]},
                 "step":{"type":"string","minLength":1,"maxLength":128},
                 "operation":{"enum":["start","activate","quote","continue","stop","replace","blueprint","supplies","learn","recover"]}}}
                """).getAsJsonObject();
    }
    public static ActionResult execute(DialogueContext context, VillagerActionDefinition action) {
        HiredVillagerRole role = HiredVillagerRole.bySerializedName(action.factKey());
        if (context == null || context.villager() == null || role == null || !questId(role).equals(action.questId()))
            return result(action, false, "This action must belong to the matching personal Clipboard lesson.");
        var progress = VillagerQuestSavedData.get(context.level()).get(context.player().getUUID(), action.questId());
        if ((progress == null || progress.state() != VillagerQuestSavedData.QuestState.ACTIVE) && !action.factValue().equals("stop"))
            return result(action, false, "Accept this lesson first.");
        var data = TutorialSavedData.get(context.level());
        var run = data.find(context.player().getUUID(), action.questId().toString()).orElse(null);
        String operation = action.factValue();
        if (operation.equals("start")) {
            if (run != null) {
                if (run.completed()) return result(action, true, "Your lesson is already earned. Choose whether to stop or request the current normal fee.");
                if (!run.binding().worker().equals(context.villager().getUUID()))
                    return result(action, false, "Resume with your bound worker, or explicitly choose a replacement for the remaining work.");
                if (run.state() == TutorialRun.State.ABANDONED) run.reaccept(run.binding());
                var previous = HireContractStore.load(context.villager()).orElse(null);
                if (previous == null || !previous.isActiveOrAwaitingAutoPayment()) {
                    var resumed = HireContract.tutorial(context.player().getUUID(), context.level().getGameTime(), role, run.trial(), run.binding().assignment());
                    if (!HiredVillagerContractService.beginTutorialContract(context.level(), context.villager(), context.player(), resumed))
                        return result(action, false, "The original worker cannot resume while another assignment or safety restriction is active.");
                }
                data.setDirty();
                if (TutorialProvisionService.introductoryClipboard(context.player(), run) == TutorialProvisionService.Delivery.WAITING_FOR_SPACE)
                    return result(action, false, "Make one inventory slot free for your once-only introductory Clipboard, then resume this lesson.");
                return result(action, true, "Resume the same lesson. Its supplies and remaining allowance have been retained.");
            }
            if (!eligible(context, role)) return result(action, false, "This tutor is unavailable for that lesson.");
            UUID trial = UUID.randomUUID();
            var contract = HireContract.tutorial(context.player().getUUID(), context.level().getGameTime(), role, trial);
            var binding = new TutorialRun.Binding(context.player().getUUID(), context.villager().getUUID(), context.villager().getUUID(),
                    contract.id(), UUID.randomUUID(), progress.questRunId(), 0, role.serializedName(), context.level().dimension().location().toString());
            run = new TutorialRun(trial, action.questId().toString(), binding);
            if (!data.reserve(run)) return result(action, false, "This lesson is reserved or waiting for recovery; no new allowance was issued.");
            if (!HiredVillagerContractService.beginTutorialContract(context.level(), context.villager(), context.player(), contract)) {
                run.pause(); data.setDirty();
                return result(action, false, "The worker has conflicting work, ownership or safety restrictions. No payment was taken.");
            }
            if (TutorialProvisionService.introductoryClipboard(context.player(), run) == TutorialProvisionService.Delivery.WAITING_FOR_SPACE)
                return result(action, false, "Your free lesson is reserved. Make one inventory slot free for the introductory Clipboard and resume; no budget was reset.");
            return result(action, true, "This finite lesson has no assignment fee or automatic payment. Set up the named worker before starting work.");
        }
        if (run == null) return result(action, false, "The reserved tutorial is unavailable; do not buy a paid hire to bypass it.");
        if(operation.equals("learn")){
            String blocker=TutorialLessonCheckpoints.acknowledge(context,run,action.text());
            return result(action,blocker.isEmpty(),blocker.isEmpty()?"Server-confirmed. That step is committed on your bound worker.":blocker);
        }
        if(operation.equals("recover")){
            var worker=boundWorker(context,run);
            if(worker==null||!worker.getUUID().equals(context.villager().getUUID()))return result(action,false,"Return to the bound worker, or choose an explicit compatible replacement.");
            return result(action,true,TutorialRecovery.recover(context.player(),worker,run));
        }
        if(operation.equals("supplies")){
            var worker=boundWorker(context,run);
            if(worker==null||!worker.getUUID().equals(context.villager().getUUID()))return result(action,false,"Return to the bound worker for this lesson's remaining working inputs.");
            return result(action,true,TutorialSupplies.provide(context.player(),worker,run));
        }
        if(operation.equals("blueprint")&&role==HiredVillagerRole.BUILDER){
            var delivered=TutorialBuilderPlan.blueprint(context.player(),run);
            return result(action,delivered==TutorialProvisionService.Delivery.DELIVERED||delivered==TutorialProvisionService.Delivery.ALREADY_DELIVERED,
                    delivered==TutorialProvisionService.Delivery.WAITING_FOR_SPACE?"Free one inventory slot and ask again; the same blueprint allowance is retained.":"Your once-only Plains Accessory 1 blueprint has zero purchase and construction labor fees. Place it with Use; the materials come from the actual template.");
        }
        if (operation.equals("stop")) {
            if (run.state() == TutorialRun.State.CONVERTED || run.state() == TutorialRun.State.STOPPED)
                return result(action, true, "This tutorial assignment has already ended. Your completion is retained.");
            if (!run.stop(run.binding())) { data.setDirty(); return result(action, false, "Work is paused while an in-flight operation is reconciled."); }
            var worker = boundWorker(context, run);
            if (worker != null) HiredVillagerContractService.stopTutorialContract((net.minecraft.server.level.ServerLevel) worker.level(), worker,
                    context.player().getUUID(), run.binding().assignment(), run.trial());
            data.setDirty();
            return result(action, true, run.completed() ? "Lesson complete. Work stopped and no payment was taken. Your output and building remain yours."
                    : "Lesson paused without a fee. Your remaining allowance and grant history are retained.");
        }
        if (operation.equals("quote") || operation.equals("continue")) {
            var worker = boundWorker(context, run);
            if (worker == null) return result(action, false, "The bound worker must be loaded for a paid continuation. You can still choose Stop.");
            return com.jvn.villagerretaliation.interaction.TutorialContinuationService.execute(context, action, run, worker);
        }
        if (operation.equals("activate")) {
            var worker = boundWorker(context, run);
            if (worker == null || !worker.getUUID().equals(context.villager().getUUID()))
                return result(action, false, "Return to the named bound worker in the lesson dimension.");
            if (run.state() == TutorialRun.State.ACTIVE || run.completed()) return result(action, true, "The remaining finite work is already authorized.");
            String recoveryBlocker=TutorialRecovery.revalidate(context.level(),worker,run);
            if(!recoveryBlocker.isEmpty())return result(action,false,recoveryBlocker);
            TutorialRun.Scope scope;
            if (role == HiredVillagerRole.NITWIT) scope = new TutorialRun.Scope(Map.of("report", Set.of(worker.getUUID().toString())),
                    Map.of("report", 1), Map.of("report", 1), "");
            else if (role == HiredVillagerRole.BUILDER) {
                String blocker=TutorialBuilderPlan.readiness(context.level(),worker,run);
                if(!blocker.isEmpty())return result(action,false,blocker);
                scope=run.scope();
            }
            else if (role == HiredVillagerRole.BREWING) {
                var plan = TutorialBrewingPlan.inspect(context.level(), worker);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else if (role == HiredVillagerRole.COURIER) {
                var plan = TutorialCourierPlan.inspect(context.level(), worker);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else if (role == HiredVillagerRole.HUNTING) {
                var plan = TutorialHuntingPlan.inspect(context.level(), worker);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else if (role == HiredVillagerRole.ANIMAL_HANDLING) {
                var plan = TutorialAnimalPlan.inspect(context.level(), worker);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else if (role == HiredVillagerRole.LOGGING) {
                var plan = TutorialLoggingPlan.inspect(context.level(), worker);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else if (role == HiredVillagerRole.COOK || role == HiredVillagerRole.SMELTER) {
                var plan = TutorialFurnacePlan.inspect(context.level(), worker, role);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else if (role == HiredVillagerRole.CRAFTSMAN) {
                var plan = TutorialCraftingPlan.inspect(context.level(), worker);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else if (role == HiredVillagerRole.COMBAT) {
                var plan = TutorialGuardPlan.inspect(context.level(), worker);
                if (plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
                if (run.scope() != null && !TutorialGuardPlan.matches(context.level(), HiredWorkSession.create(context.level(), worker, role).context(), run))
                    return result(action, false, "Restore the original three-node route; earned checkpoint progress is retained.");
            }
            else if (role == HiredVillagerRole.FISHING) {
                var plan = TutorialFishingPlan.inspect(context.level(), worker);
                if (plan.scope() == null && run.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
                if (run.scope() != null && run.committedUnits("catch") == 0 && !TutorialFishingPlan.matches(worker, run))
                    return result(action, false, "Restore the original one-catch Task; replacing the command cannot replenish this lesson.");
            }
            else if (role == HiredVillagerRole.MINING) {
                var plan = TutorialMiningPlan.inspect(context.level(), worker);
                if (run.scope() == null && plan.scope() == null) return result(action, false, plan.blocker());
                scope = run.scope() == null ? plan.scope() : run.scope();
            }
            else {
                var plan = run.scope() == null ? TutorialFarmingPlan.inspect(context.level(), worker) : TutorialFarmingPlan.revalidate(context.level(), worker, run);
                if (plan.scope() == null) return result(action, false, plan.blocker());
                scope = plan.scope();
            }
            boolean activated = run.scope() == null ? run.activate(run.binding(), scope) : run.resume(run.binding(), true);
            data.setDirty();
            return result(action, activated, activated ? "The finite task is ready. In Job > Work Settings, inspect the work state and enable work once."
                    : "The task is interrupted. Revalidate its bound worker and remaining task before resuming.");
        }
        if (operation.equals("replace")) {
            if (run.completed()) return result(action, true, "Your earned completion is retained; no replacement free assignment is needed.");
            if (!eligible(context, role)) return result(action, false, "Choose an available compatible tutor without conflicting work.");
            if(!run.binding().dimension().equals(context.level().dimension().location().toString()))return result(action,false,"Replace the worker in the original lesson dimension so the approved task footprint stays the same.");
            var oldBinding = run.binding();
            if(run.state()==TutorialRun.State.ABANDONED)run.reaccept(oldBinding);
            var oldWorker = boundWorker(context, run);
            run.pause();
            TutorialRecovery.reconcileLivingQuarry(context.level(),run);
            var contract = HireContract.tutorial(context.player().getUUID(), context.level().getGameTime(), role, run.trial());
            if (!run.replaceWorker(oldBinding, context.villager().getUUID(), context.villager().getUUID(), contract.id(), oldBinding.task(),
                    context.level().dimension().location().toString(), true)) return result(action, false, "Reconcile pending operations before replacing the worker.");
            data.setDirty();
            if (oldWorker != null) HiredVillagerContractService.stopTutorialContract((net.minecraft.server.level.ServerLevel) oldWorker.level(), oldWorker,
                    context.player().getUUID(), oldBinding.assignment(), run.trial());
            if (!HiredVillagerContractService.beginTutorialContract(context.level(), context.villager(), context.player(), contract))
                return result(action, false, "Replacement is bound but paused. Resolve the new worker's restriction and resume; no budget or supplies were reset.");
            return result(action, true, "Replacement bound. Recheck its tools, stored work area, settings and storage links. Only the remaining allowance is available.");
        }
        return result(action, false, "This recovery action is not yet available for the current assignment.");
    }
    public static Villager boundWorker(DialogueContext context, TutorialRun run) {
        var dim = ResourceLocation.tryParse(run.binding().dimension());
        var level = dim == null ? null : context.level().getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dim));
        return level != null && level.getEntity(run.binding().worker()) instanceof Villager villager && villager.isAlive() ? villager : null;
    }
    public static ActionResult result(VillagerActionDefinition action, boolean success, String text) {
        var legacy = new VillagerActionResult(true, "", text, Map.of("tutorial_status", success ? "ready" : "blocked"), true);
        return success ? ActionResult.success(legacy, VillagerActionRegistry.capabilities(action))
                : ActionResult.unavailable(text, VillagerActionRegistry.capabilities(action), legacy);
    }
}
