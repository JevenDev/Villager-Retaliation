package com.jvn.villagerretaliation.inventory;

import com.jvn.villagerretaliation.item.VillagerFilterPolicy;

public interface VillagerFilterPolicyMenu {
    VillagerFilterPolicy.Policy filterPolicy();

    boolean applyPolicyChange(VillagerFilterPolicy.PolicyField field, int value);

    void applyClientPolicyChange(VillagerFilterPolicy.PolicyField field, int value);
}
