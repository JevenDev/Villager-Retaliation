package com.jvn.villagerretaliation.api.registry;

import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ClientSync;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.DebugFormatter;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.Parser;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RecoveryMode;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.RuntimeImplementation;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.ToolingMetadata;
import com.jvn.villagerretaliation.api.registry.ExtensionContracts.Validator;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** Complete public contract for a datapack-visible runtime type. */
public record RuntimeTypeDescriptor(
        ResourceLocation id,
        Set<ResourceLocation> aliases,
        Set<ResourceLocation> liveCapabilities,
        Set<ResourceLocation> snapshotCapabilities,
        Parser<?> parser,
        Validator<Object> validator,
        RuntimeImplementation<Object, Object, Object> implementation,
        DebugFormatter<Object> debugFormatter,
        RecoveryMode recoveryMode,
        ToolingMetadata tooling,
        ClientSync clientSync
) implements ExtensionDescriptor {
    public RuntimeTypeDescriptor {
        if (id == null) {
            throw new IllegalArgumentException("runtime type id must not be null");
        }
        aliases = immutable(aliases);
        liveCapabilities = immutable(liveCapabilities);
        snapshotCapabilities = immutable(snapshotCapabilities);
        if (parser == null || validator == null || implementation == null || debugFormatter == null) {
            throw new IllegalArgumentException("runtime type " + id
                    + " must declare parser, validator, implementation, and debug formatter");
        }
        recoveryMode = recoveryMode == null ? RecoveryMode.UNSAFE_BLOCK : recoveryMode;
        tooling = tooling == null ? ToolingMetadata.runtimeOnly(id.toString(), "") : tooling;
        clientSync = clientSync == null ? ClientSync.NONE : clientSync;
    }

    private static Set<ResourceLocation> immutable(Set<ResourceLocation> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<ResourceLocation> copy = new LinkedHashSet<>();
        for (ResourceLocation value : values) {
            if (value == null) {
                throw new IllegalArgumentException("capability and alias ids must not be null");
            }
            copy.add(value);
        }
        return Set.copyOf(copy);
    }
}
