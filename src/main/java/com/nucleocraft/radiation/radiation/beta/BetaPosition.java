package com.nucleocraft.radiation.radiation.beta;

import java.util.List;

/** Immutable world coordinates; order is stable for deterministic simulation. */
public record BetaPosition(int x, int y, int z) implements Comparable<BetaPosition> {
    public List<BetaPosition> neighbours() {
        return List.of(new BetaPosition(x - 1, y, z), new BetaPosition(x + 1, y, z),
            new BetaPosition(x, y - 1, z), new BetaPosition(x, y + 1, z),
            new BetaPosition(x, y, z - 1), new BetaPosition(x, y, z + 1));
    }
    @Override public int compareTo(BetaPosition other) {
        int a = Integer.compare(x, other.x);
        if (a != 0) return a;
        a = Integer.compare(y, other.y);
        return a != 0 ? a : Integer.compare(z, other.z);
    }
}
