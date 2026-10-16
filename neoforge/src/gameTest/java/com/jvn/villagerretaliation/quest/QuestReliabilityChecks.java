package com.jvn.villagerretaliation.quest;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** Runs the same detached runtime/NBT assertions as the native suite without a server or client. */
public final class QuestReliabilityChecks {
    private QuestReliabilityChecks() { }

    public static void main(String[] args) throws Exception {
        int count = 0;
        for (var method : Arrays.stream(QuestReliabilityGameTests.class.getDeclaredMethods())
                .sorted(Comparator.comparing(java.lang.reflect.Method::getName)).toList()) {
            if (!method.isAnnotationPresent(GameTest.class) || method.getName().startsWith("native")) continue;
            var assertions = new Assertions();
            try {
                method.invoke(null, assertions);
            } catch (InvocationTargetException exception) {
                throw new AssertionError(method.getName(), exception.getCause());
            }
            if (!assertions.completed) throw new AssertionError("Check did not finish: " + method.getName());
            System.out.println("PASS " + method.getName());
            count++;
        }
        System.out.println("Passed " + count + " headless quest reliability checks.");
    }

    private static final class Assertions extends GameTestHelper {
        private boolean completed;
        private Assertions() { super(null); }
        @Override public void succeed() { completed = true; }
        @Override public void fail(String message) { throw new AssertionError(message); }
        @Override public void assertTrue(boolean value, String message) { if (!value) fail(message); }
        @Override public void assertFalse(boolean value, String message) { assertTrue(!value, message); }
        @Override public <N> void assertValueEqual(N actual, N expected, String message) {
            assertTrue(Objects.equals(actual, expected), message + ": expected " + expected + ", got " + actual);
        }
    }
}
