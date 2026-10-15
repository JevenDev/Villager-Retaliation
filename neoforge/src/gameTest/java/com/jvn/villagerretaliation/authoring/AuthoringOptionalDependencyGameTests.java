package com.jvn.villagerretaliation.authoring;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("authoring")
@PrefixGameTestTemplate(false)
public final class AuthoringOptionalDependencyGameTests {
    private AuthoringOptionalDependencyGameTests() { }

    @GameTest(template = "empty", batch = "authoring")
    public static void bootstrapDoesNotResolveAbsentImGui(GameTestHelper helper) {
        helper.assertTrue(!ModList.get().isLoaded("imguimc"), "this absence check requires the normal headless runtime without ImGuiMC");
        String boundary = "com.jvn.villagerretaliation.authoring.client.AuthoringEditorClient";
        ClassLoader parent = AuthoringOptionalDependencyGameTests.class.getClassLoader();
        ClassLoader isolated = new ClassLoader(parent) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("imgui.") || name.startsWith("foundry.imgui.")
                        || name.endsWith(".ImGuiAuthoringEditor")) {
                    throw new ClassNotFoundException("Optional editor dependency deliberately unavailable: " + name);
                }
                if (!name.equals(boundary)) return super.loadClass(name, resolve);
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    try (InputStream stream = parent.getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        byte[] bytes = stream.readAllBytes();
                        loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException exception) {
                        throw new ClassNotFoundException(name, exception);
                    }
                }
                if (resolve) resolveClass(loaded);
                return loaded;
            }
        };
        try {
            Class.forName(boundary, true, isolated).getMethod("register", IEventBus.class).invoke(null, new Object[] {null});
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Optional integration must return before loading editor classes", exception);
        }
        helper.succeed();
    }
}
