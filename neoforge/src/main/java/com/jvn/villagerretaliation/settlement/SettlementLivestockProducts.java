package com.jvn.villagerretaliation.settlement;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class SettlementLivestockProducts {
    private SettlementLivestockProducts() {
    }

    static void advance(ServerLevel level, SettlementRecord town, SettlementLivestockBreeding.Frame frame, long now) {
        if (!(frame.animal() instanceof Chicken chicken) || chicken.isChickenJockey() || frame.elapsed() <= 0) return;
        int age = chicken.getAge();
        int eggTime = chicken.eggTime;
        for (int tick = 0; tick < frame.elapsed(); tick++) {
            age += Integer.compare(0, age);
            if (age < 0 || --eggTime > 0) continue;
            var item = new ItemEntity(level, chicken.getX(), chicken.getY(), chicken.getZ(), new ItemStack(Items.EGG));
            item.setDefaultPickUpDelay();
            var drop = SettlementItemDrop.create(item, frame.state().area(), now - (frame.elapsed() - tick - 1L));
            for (var worker : town.workers().values()) {
                if (!worker.area().equals(frame.state().area())) continue;
                int steps = frame.state().steps(worker);
                if (steps == 0 && worker.position().getBottomCenter().distanceToSqr(item.position()) > 2.25D) steps = -1;
                drop.access(worker, steps);
            }
            if (drop.advance(level, now)) town.recordItemDrop(drop);
            eggTime = chicken.getRandom().nextInt(6_000) + 6_000;
        }
        frame.state().eggTime(eggTime);
    }
}
