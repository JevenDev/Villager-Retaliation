package com.jvn.villagerretaliation.quest.tutorial;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Provenance lives on actual newly produced stacks. Matching old player goods cannot become evidence. */
public final class TutorialOutputService {
    private static final String TAG = "VillagerRetaliationTutorialOutput";
    private TutorialOutputService() { }
    public static void stamp(Villager worker, TutorialRun.Permit permit, List<ItemStack> products) {
        if (permit == null) return;
        var run = TutorialWorkService.run(worker).orElse(null);
        if (run == null || !run.binding().equals(permit.binding())) return;
        for (var product : products) if (!product.isEmpty()) {
            var data = product.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            var marker = new CompoundTag(); marker.putUUID("Trial", run.trial()); marker.putUUID("Operation", permit.operation());
            marker.put("Product", identity(product).copyWithCount(1).save(worker.registryAccess()));
            data.put(TAG, marker); product.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        }
    }
    public static Map<String, Integer> quantities(List<ItemStack> products) {
        var result = new LinkedHashMap<String, Integer>();
        for (var product : products) if (!product.isEmpty()) result.merge(BuiltInRegistries.ITEM.getKey(product.getItem()).toString(), product.getCount(), Integer::sum);
        return result;
    }
    public static ItemStack clean(ItemStack stack) {
        var result = stack.copy();
        var data = result.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!data.contains(TAG)) return result;
        data.remove(TAG);
        if (data.isEmpty()) result.remove(DataComponents.CUSTOM_DATA); else result.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return result;
    }
    public static boolean attributable(Villager worker, ItemStack stack) {
        var run = TutorialWorkService.run(worker).orElse(null);
        if (run == null) return false;
        var marker = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(TAG);
        if (!marker.hasUUID("Trial") || !marker.getUUID("Trial").equals(run.trial()) || !marker.hasUUID("Operation")) return false;
        var receipt = run.snapshot().receipts().get(marker.getUUID("Operation"));
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var expected=ItemStack.parseOptional(worker.registryAccess(),marker.getCompound("Product"));
        return !expected.isEmpty()&&ItemStack.isSameItemSameComponents(identity(stack),expected)
                && receipt != null && !receipt.permit().action().equals("deposit") && receipt.products().getOrDefault(item, 0) > 0
                && run.produced(item) > run.deposited(item);
    }
    private static ItemStack identity(ItemStack stack){
        var result=com.jvn.villagerretaliation.inventory.HiredJobInventory.withoutJobItemMarker(clean(stack));
        var data=result.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        data.remove("VillagerRetaliationCourierDelivery");
        if(data.isEmpty())result.remove(DataComponents.CUSTOM_DATA);else result.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
        return result;
    }
    public static TutorialRun.Permit reserveDeposit(ServerLevel level, Villager worker, ItemStack stack, String destination, int offered) {
        if (!attributable(worker, stack)) return null;
        var run = TutorialWorkService.run(worker).orElseThrow();
        String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        int count = Math.min(offered, run.produced(item) - run.deposited(item));
        return TutorialWorkService.reserve(worker, "deposit", destination, count);
    }
    /** Explicit recovery moves only receipt-marked goods; it never manufactures or removes matching player goods. */
    public static int recoverCarried(Villager worker){
        var run=TutorialWorkService.run(worker).orElse(null);if(run==null||run.scope()==null)return 0;
        var session=com.jvn.villagerretaliation.interaction.HiredWorkSession.create((ServerLevel)worker.level(),worker,
                com.jvn.villagerretaliation.interaction.HiredVillagerRole.bySerializedName(run.binding().role()));
        var inventory=session.inventory();int moved=0;
        for(int slot:List.copyOf(inventory.supplySlots())){
            var source=inventory.getItem(slot);
            if(!attributable(worker,source))continue;
            String item=BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
            int amount=Math.min(source.getCount(),run.produced(item)-run.deposited(item));
            var offered=source.copyWithCount(amount);
            if(amount<=0||!session.context().canStoreOutputs(List.of(offered)))continue;
            var actual=inventory.removeItem(slot,amount);if(actual.isEmpty())continue;
            var remainder=inventory.insertOutput(actual);moved+=actual.getCount()-remainder.getCount();
            if(!remainder.isEmpty()){
                var restored=inventory.insertSupply(remainder);
                if(!restored.isEmpty())worker.spawnAtLocation(restored);
            }
        }
        return moved;
    }

}
