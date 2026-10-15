package com.jvn.villagerretaliation.quest.tutorial;

import com.jvn.villagerretaliation.interaction.HireContract;
import com.jvn.villagerretaliation.interaction.HireContractStore;
import com.jvn.villagerretaliation.interaction.HiredVillagerRole;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/** Server-only bridge between the existing work services and the durable finite ledger. */
public final class TutorialWorkService {
    private TutorialWorkService() { }
    public static Optional<TutorialRun> run(Villager villager) {
        if (!(villager.level() instanceof ServerLevel level)) return Optional.empty();
        var contract = HireContractStore.load(villager).filter(HireContract::isActive).filter(HireContract::tutorialTrial).orElse(null);
        if (contract == null) return Optional.empty();
        return contract.tutorialId().flatMap(id -> TutorialSavedData.get(level).trial(id))
                .filter(run -> matches(villager, contract, run));
    }
    private static boolean matches(Villager villager, HireContract contract, TutorialRun run) {
        var binding = run.binding();
        return binding.worker().equals(villager.getUUID()) && binding.assignment().equals(contract.id())
                && contract.owner().filter(binding.player()::equals).isPresent()
                && contract.role() != null && binding.role().equals(contract.role().serializedName())
                && binding.dimension().equals(villager.level().dimension().location().toString());
    }
    public static boolean isTrial(Villager villager) {
        return HireContractStore.load(villager).filter(HireContract::isActive).filter(HireContract::tutorialTrial).isPresent();
    }
    /** No generic worker loop may execute a trial without an integrated action adapter. */
    public static boolean canTick(Villager villager, HiredVillagerRole role) {
        if (!isTrial(villager)) return true;
        var run = run(villager).orElse(null);
        if (run == null || run.state() != TutorialRun.State.ACTIVE || !villager.isAlive()) return false;
        var level = (ServerLevel) villager.level();
        if(!com.jvn.villagerretaliation.interaction.HiredWorkStateStore.state(villager).getBoolean("Enabled"))return false;
        var owner = level.getServer().getPlayerList().getPlayer(run.binding().player());
        if (owner == null || owner.level() != level) return false;
        if(com.jvn.villagerretaliation.settlement.SettlementWorkforceService.duty(villager).isPresent())return false;
        if(java.util.Set.of(HiredVillagerRole.CRAFTSMAN,HiredVillagerRole.COOK,HiredVillagerRole.SMELTER,HiredVillagerRole.BREWING).contains(role)
                && com.jvn.villagerretaliation.interaction.work.production.HiredProductionService.access(level,villager,role).managed())return false;
        return TutorialService.supported(role);
    }
    public static String position(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "/" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
    public static boolean permitsTarget(net.minecraft.world.level.BlockGetter blocks, net.minecraft.nbt.CompoundTag state, String action, BlockPos pos) {
        if (!state.hasUUID("TutorialTrial")) return true;
        if (!(blocks instanceof ServerLevel level) || !state.hasUUID("TutorialWorker") || !state.hasUUID("TutorialAssignment")) return false;
        var run = TutorialSavedData.get(level).trial(state.getUUID("TutorialTrial")).orElse(null);
        if (run == null || !run.binding().worker().equals(state.getUUID("TutorialWorker"))
                || !run.binding().assignment().equals(state.getUUID("TutorialAssignment")) || run.scope() == null
                || run.state() != TutorialRun.State.ACTIVE) return false;
        String target = position(level, pos);
        return run.scope().targets().getOrDefault(action, java.util.Set.of()).contains(target)
                && run.snapshot().receipts().values().stream().noneMatch(r -> r.permit().action().equals(action) && r.permit().target().equals(target));
    }
    /** Null means denied for a trial; ordinary workers do not need a permit. */
    public static TutorialRun.Permit reserve(Villager villager, String action, String target, int units) {
        var run = run(villager).orElse(null);
        if (run == null || !canTick(villager, HiredVillagerRole.bySerializedName(run.binding().role()))) return null;
        var permit = run.reserve(run.binding(), UUID.randomUUID(), action, target, units);
        if (permit != null) {
            var data=TutorialSavedData.get((ServerLevel)villager.level());data.setDirty();data.saveOwnerCheckpoints();
        }
        return permit;
    }
    public static void failed(Villager villager, TutorialRun.Permit permit) {
        run(villager).ifPresent(run -> {
            if (run.failed(permit)) TutorialSavedData.get((ServerLevel) villager.level()).setDirty();
        });
    }
    public static boolean committed(Villager villager, TutorialRun.Permit permit, String item,
            int quantity, String destination) {
        if (permit != null && permit.action().equals("deposit") && quantity > 0 && quantity < permit.units())
            permit = new TutorialRun.Permit(permit.operation(), permit.binding(), permit.action(), permit.target(), quantity);
        if (permit == null || !(villager.level() instanceof ServerLevel level)) return false;
        var run = run(villager).orElse(null);
        if (run == null || !run.commit(new TutorialRun.Receipt(permit, item, quantity, destination, level.getGameTime())))
            return false;
        var savedData=TutorialSavedData.get(level);savedData.setDirty();savedData.saveOwnerCheckpoints();
        publish(villager, run, "work_action_committed", permit.operation(),
                java.util.Map.of("action", permit.action(), "target", permit.target(), "item", item == null ? "" : item,
                        "quantity", Integer.toString(quantity), "destination", destination == null ? "" : destination));
        return true;
    }
    public static boolean committedProducts(Villager villager, TutorialRun.Permit permit, java.util.List<net.minecraft.world.item.ItemStack> products) {
        if (permit == null || !(villager.level() instanceof ServerLevel level)) return false;
        var run = run(villager).orElse(null);
        var quantities = TutorialOutputService.quantities(products);
        String first = quantities.keySet().stream().findFirst().orElse("");
        if (run == null || !run.commit(new TutorialRun.Receipt(permit, first, quantities.getOrDefault(first, 0), "", level.getGameTime(), quantities))) return false;
        var savedData=TutorialSavedData.get(level);savedData.setDirty();savedData.saveOwnerCheckpoints();
        publish(villager, run, "work_action_committed", permit.operation(), java.util.Map.of("action", permit.action(), "target", permit.target()));
        return true;
    }
    public static void reconcileContinuation(Villager worker) {
        var contract=HireContractStore.load(worker).filter(HireContract::isActive).filter(HireContract::tutorialTrial).orElse(null);
        if(contract!=null&&worker.level() instanceof ServerLevel level){
            var saved=contract.tutorialId().flatMap(id->TutorialSavedData.get(level).trial(id)).orElse(null);
            if(saved!=null&&(!saved.binding().worker().equals(worker.getUUID())||!saved.binding().assignment().equals(contract.id())
                    ||saved.state()==TutorialRun.State.STOPPED||saved.state()==TutorialRun.State.ABANDONED)){
                contract.owner().ifPresent(owner->com.jvn.villagerretaliation.interaction.HiredVillagerContractService.stopTutorialContract(level,worker,owner,contract.id(),saved.trial()));
                return;
            }
        }
        run(worker).ifPresent(run -> {
            var player = ((ServerLevel) worker.level()).getServer().getPlayerList().getPlayer(run.binding().player());
            if (player != null) com.jvn.villagerretaliation.interaction.TutorialContinuationService.reconcile(player, worker, run);
        });
    }
    public static void setupCommitted(Villager villager, UUID actor, String kind, java.util.Map<String, String> data) {
        run(villager).filter(run -> run.binding().player().equals(actor)).ifPresent(run ->
                publish(villager, run, kind, UUID.randomUUID(), data));
    }
    private static void publish(Villager villager, TutorialRun run, String kind, UUID operation,
            java.util.Map<String, String> details) {
        var level = (ServerLevel) villager.level();
        var player = level.getServer().getPlayerList().getPlayer(run.binding().player());
        if (player == null) return;
        var payload = new java.util.LinkedHashMap<>(details);
        payload.put("operation", operation.toString()); payload.put("trial", run.trial().toString());
        payload.put("run", run.binding().run().toString()); payload.put("worker", villager.getUUID().toString());
        payload.put("assignment", run.binding().assignment().toString()); payload.put("task", run.binding().task().toString());
        payload.put("role", run.binding().role()); payload.put("dimension", run.binding().dimension());
        payload.put("generation", Integer.toString(run.binding().generation()));
        payload.put("game_time", Long.toString(level.getGameTime()));
        com.jvn.villagerretaliation.quest.VillagerQuestService.onCriterion(level, player,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("villagerretaliation", kind), payload);
    }
    public static void interrupted(Villager villager) {
        run(villager).ifPresent(run -> {
            run.pause(); TutorialSavedData.get((ServerLevel) villager.level()).setDirty();
        });
    }
}
