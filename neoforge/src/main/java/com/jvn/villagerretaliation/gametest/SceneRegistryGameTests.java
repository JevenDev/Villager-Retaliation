package com.jvn.villagerretaliation.gametest;

import com.google.gson.JsonObject;
import com.jvn.villagerretaliation.VillagerRetaliation;
import com.jvn.villagerretaliation.api.VillagerRetaliationRegistries;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ClientSync;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ToolingMetadata;
import com.jvn.villagerretaliation.api.registry.FreezableExtensionRegistry;
import com.jvn.villagerretaliation.api.registry.RuntimeTypeDescriptor;
import com.jvn.villagerretaliation.quest.QuestRegistryMetadata;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder
@PrefixGameTestTemplate(false)
public final class SceneRegistryGameTests {
    private static final String EMPTY_TEMPLATE = "villagerretaliation:empty";

    private SceneRegistryGameTests() {
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void publicRegistryRejectsDuplicatesAliasesAndLateRegistration(GameTestHelper helper) {
        FreezableExtensionRegistry<RuntimeTypeDescriptor> registry = new FreezableExtensionRegistry<>("test type");
        RuntimeTypeDescriptor first = descriptor("registry_first", Set.of(VillagerRetaliation.id("legacy_first")));
        registry.register(first);
        helper.assertTrue(registry.get(VillagerRetaliation.id("legacy_first")).orElseThrow() == first,
                "registered alias should resolve to its canonical descriptor");
        expectFailure(helper, () -> registry.register(descriptor("registry_first", Set.of())), "duplicate id");
        expectFailure(helper, () -> registry.register(descriptor("registry_second",
                Set.of(VillagerRetaliation.id("legacy_first")))), "alias collision");
        registry.freeze();
        expectFailure(helper, () -> registry.register(descriptor("registry_late", Set.of())), "registry freeze");
        helper.succeed();
    }

    @GameTest(template = EMPTY_TEMPLATE, timeoutTicks = 100)
    public static void extensionDescriptorsExportDeterministically(GameTestHelper helper) {
        VillagerRetaliationRegistries.registerBuiltIns();
        JsonObject registries = QuestRegistryMetadata.export().getAsJsonObject("registries");
        helper.assertTrue(registries.getAsJsonArray("actor_types").size() >= 5,
                "actor descriptors should be exported for third-party tooling");
        helper.assertTrue(registries.getAsJsonArray("scene_steps").asList().stream()
                        .map(value -> value.getAsJsonObject().get("id").getAsString())
                        .anyMatch("villagerretaliation:move_actor"::equals),
                "built-in scene steps should use the public descriptor export");
        helper.succeed();
    }

    private static RuntimeTypeDescriptor descriptor(String path, Set<net.minecraft.resources.ResourceLocation> aliases) {
        return new RuntimeTypeDescriptor(VillagerRetaliation.id(path), aliases, Set.of(), Set.of(),
                JsonObject::deepCopy, value -> List.of(), (value, context) -> value, String::valueOf,
                RecoveryMode.NATURALLY_IDEMPOTENT,
                new ToolingMetadata(path, path, Map.of("type", "object"), true), ClientSync.NONE);
    }

    private static void expectFailure(GameTestHelper helper, Runnable operation, String message) {
        try {
            operation.run();
            helper.fail("Expected " + message + " rejection");
        } catch (IllegalArgumentException | IllegalStateException expected) {
            // Expected precise registry contract rejection.
        }
    }
}
