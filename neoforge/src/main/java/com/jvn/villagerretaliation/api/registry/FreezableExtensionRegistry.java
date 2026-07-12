package com.jvn.villagerretaliation.api.registry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/**
 * Explicit, deterministic registration lifecycle for datapack-visible extension types.
 * Registrations are mutable only during mod construction; callers receive immutable views.
 */
public final class FreezableExtensionRegistry<D extends ExtensionDescriptor> {
    private final String name;
    private final Map<ResourceLocation, D> byId = new LinkedHashMap<>();
    private final Map<ResourceLocation, ResourceLocation> aliases = new LinkedHashMap<>();
    private volatile boolean frozen;
    private volatile List<D> ordered = List.of();

    public FreezableExtensionRegistry(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("registry name must not be blank");
        }
        this.name = name;
    }

    public synchronized D register(D descriptor) {
        if (this.frozen) {
            throw new IllegalStateException("Cannot register " + this.name + " type after datapack registry freeze: "
                    + (descriptor == null ? "<null>" : descriptor.id()));
        }
        if (descriptor == null || descriptor.id() == null) {
            throw new IllegalArgumentException(this.name + " descriptor and namespaced id must not be null");
        }
        ResourceLocation id = descriptor.id();
        if (this.byId.containsKey(id) || this.aliases.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate or colliding " + this.name + " id: " + id);
        }
        for (ResourceLocation alias : descriptor.aliases()) {
            if (alias == null) {
                throw new IllegalArgumentException(this.name + " alias must not be null for " + id);
            }
            if (alias.equals(id) || this.byId.containsKey(alias) || this.aliases.containsKey(alias)) {
                throw new IllegalArgumentException("Alias collision in " + this.name + " registry: " + alias
                        + " while registering " + id);
            }
        }
        this.byId.put(id, descriptor);
        for (ResourceLocation alias : descriptor.aliases()) {
            this.aliases.put(alias, id);
        }
        return descriptor;
    }

    public synchronized void freeze() {
        if (this.frozen) {
            return;
        }
        List<D> descriptors = new ArrayList<>(this.byId.values());
        descriptors.sort(Comparator.comparing(value -> value.id().toString()));
        this.ordered = List.copyOf(descriptors);
        this.frozen = true;
    }

    public boolean frozen() {
        return this.frozen;
    }

    public Optional<D> get(ResourceLocation idOrAlias) {
        if (idOrAlias == null) {
            return Optional.empty();
        }
        D direct = this.byId.get(idOrAlias);
        if (direct != null) {
            return Optional.of(direct);
        }
        ResourceLocation canonical = this.aliases.get(idOrAlias);
        return canonical == null ? Optional.empty() : Optional.ofNullable(this.byId.get(canonical));
    }

    public List<D> descriptors() {
        if (this.frozen) {
            return this.ordered;
        }
        List<D> descriptors = new ArrayList<>(this.byId.values());
        descriptors.sort(Comparator.comparing(value -> value.id().toString()));
        return List.copyOf(descriptors);
    }

    public String name() {
        return this.name;
    }
}
