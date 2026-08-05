package com.jvn.villagerretaliation.client.ui;

/** A loader-agnostic rectangle occupied by custom client UI. */
public record ClientScreenArea(int left, int top, int width, int height) {
    public boolean contains(double x, double y) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
