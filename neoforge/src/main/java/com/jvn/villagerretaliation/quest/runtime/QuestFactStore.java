package com.jvn.villagerretaliation.quest.runtime;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public interface QuestFactStore {
    boolean setTag(String scope, ResourceLocation tag);
    boolean clearTag(String scope, ResourceLocation tag);
    boolean hasTag(String scope, ResourceLocation tag);
    boolean setVariable(String scope, String key, String value);
    Optional<String> variable(String scope, String key);
    int addCounter(String scope, String key, int amount);
    int counter(String scope, String key);
}
