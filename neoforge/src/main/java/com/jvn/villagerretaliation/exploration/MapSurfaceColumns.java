package com.jvn.villagerretaliation.exploration;

import java.util.*;

// saved terrain keeps primitive arrays instead of a persistent object graph for every block
final class MapSurfaceColumns extends AbstractList<MapSurfaceSnapshot.Column> implements RandomAccess {
    final int[] indices, starts, data;
    final long[] coverage;
    final String[] states;
    private MapSurfaceColumns(int[] indices, int[] starts, int[] data, long[] coverage, String[] states) {
        this.indices = indices; this.starts = starts; this.data = data; this.coverage = coverage; this.states = states;
    }
    @Override public int size() { return indices.length; }
    @Override public MapSurfaceSnapshot.Column get(int index) {
        Objects.checkIndex(index, size());
        return new MapSurfaceSnapshot.Column(indices[index], new Layers(this, starts[index], starts[index + 1]), coverage[index]);
    }
    boolean sameContent(MapSurfaceColumns other) {
        return Arrays.equals(indices, other.indices) && Arrays.equals(starts, other.starts) && Arrays.equals(data, other.data)
                && Arrays.equals(coverage, other.coverage) && Arrays.equals(states, other.states);
    }
    static final class Layers extends AbstractList<MapSurfaceSnapshot.Layer> implements RandomAccess {
        private final MapSurfaceColumns owner;
        private final int start, end;
        Layers(MapSurfaceColumns owner, int start, int end) { this.owner = owner; this.start = start; this.end = end; }
        @Override public int size() { return end - start; }
        @Override public MapSurfaceSnapshot.Layer get(int index) {
            Objects.checkIndex(index, size()); int layer = start + index, offset = layer * 6; var d = owner.data;
            return new MapSurfaceSnapshot.Layer(new MapSnapshot.Block(d[offset], owner.states[layer], d[offset + 2], d[offset + 3], d[offset + 4], d[offset + 5]), d[offset + 1]);
        }
    }
    static final class Builder {
        private final int[] indices = new int[64], starts = new int[65];
        private final long[] coverage = new long[64];
        private int[] data = new int[64 * 6];
        private String[] states = new String[64];
        private int count, layers;
        void clear() { Arrays.fill(states, 0, layers, null); count = 0; layers = 0; }
        void column(int index, long mask) { indices[count] = index; coverage[count] = mask; starts[count++] = layers; }
        void layer(int y, int bottom, String state, int tint, int water, int faces, int light) {
            if (layers == states.length) { states = Arrays.copyOf(states, layers * 2); data = Arrays.copyOf(data, layers * 12); }
            int offset = layers * 6;
            states[layers++] = state; data[offset] = y; data[offset + 1] = bottom; data[offset + 2] = tint;
            data[offset + 3] = water; data[offset + 4] = faces; data[offset + 5] = light;
        }
        void copy(List<MapSurfaceSnapshot.Column> source, int index) {
            if (source instanceof MapSurfaceColumns compact) {
                column(compact.indices[index], compact.coverage[index]);
                for (int i = compact.starts[index]; i < compact.starts[index + 1]; i++) {
                    int o = i * 6; var d = compact.data;
                    layer(d[o], d[o + 1], compact.states[i], d[o + 2], d[o + 3], d[o + 4], d[o + 5]);
                }
            } else {
                var c = source.get(index); column(c.index(), c.coverage());
                for (int i = 0; i < c.layers().size(); i++) { var l = c.layers().get(i); var b = l.block(); layer(b.y(), l.bottom(), b.state(), b.tint(), b.waterTint(), b.faces(), b.light()); }
            }
        }
        MapSurfaceColumns build() {
            starts[count] = layers;
            return new MapSurfaceColumns(Arrays.copyOf(indices, count), Arrays.copyOf(starts, count + 1), Arrays.copyOf(data, layers * 6), Arrays.copyOf(coverage, count), Arrays.copyOf(states, layers));
        }
        MapSurfaceColumns sorted() {
            int[] order = new int[count], nextIndices = new int[count], nextStarts = new int[count + 1], nextData = new int[layers * 6];
            long[] nextCoverage = new long[count]; String[] nextStates = new String[layers];
            for (int i = 0; i < count; i++) order[i] = indices[i] * 64 + i;
            Arrays.sort(order); starts[count] = layers;
            int nextLayer = 0;
            for (int i = 0; i < count; i++) {
                int index = order[i] % 64, length = starts[index + 1] - starts[index];
                nextIndices[i] = indices[index]; nextCoverage[i] = coverage[index]; nextStarts[i] = nextLayer;
                System.arraycopy(states, starts[index], nextStates, nextLayer, length);
                System.arraycopy(data, starts[index] * 6, nextData, nextLayer * 6, length * 6); nextLayer += length;
            }
            nextStarts[count] = nextLayer;
            return new MapSurfaceColumns(nextIndices, nextStarts, nextData, nextCoverage, nextStates);
        }
    }
}
