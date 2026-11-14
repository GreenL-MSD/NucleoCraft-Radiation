package com.nucleocraft.radiation.radiation.beta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Deterministic, strictly bounded selection for live labels; no world scan is performed here. */
public final class BetaLabelSelection {
    public static final int MAX_RADIUS = 12;
    public static final int DEFAULT_RADIUS = 8;
    public static final int MAX_LABELS = 24;
    public static final int UPDATE_TICKS = 10;
    public static final int EXPIRE_TICKS = 30;
    public enum Mode { OFF, LOOK, NEARBY }
    public record Settings(Mode mode, int radius) {
        public Settings {
            Objects.requireNonNull(mode);
            if (radius < 1 || radius > MAX_RADIUS) throw new IllegalArgumentException("Radius must be 1.." + MAX_RADIUS);
        }
        public static Settings off() { return new Settings(Mode.OFF, DEFAULT_RADIUS); }
    }
    private BetaLabelSelection() { }

    public static List<BetaBlockLabel> select(Settings settings, BetaPosition playerBlock,
                                              Optional<BetaPosition> lookingAt,
                                              Collection<BetaBlockLabel> candidates) {
        Objects.requireNonNull(settings); Objects.requireNonNull(playerBlock);
        Objects.requireNonNull(lookingAt); Objects.requireNonNull(candidates);
        if (settings.mode() == Mode.OFF) return List.of();
        ArrayList<BetaBlockLabel> filtered = new ArrayList<>();
        long maxDistanceSq = (long) settings.radius() * settings.radius();
        for (BetaBlockLabel label : candidates) {
            if (label == null) continue;
            if (distanceSq(label.pos(), playerBlock) > maxDistanceSq) continue;
            if (settings.mode() == Mode.LOOK && (lookingAt.isEmpty() || !label.pos().equals(lookingAt.get()))) continue;
            filtered.add(label);
        }
        filtered.sort(Comparator.comparingLong((BetaBlockLabel l) -> distanceSq(l.pos(), playerBlock))
            .thenComparing(BetaBlockLabel::pos));
        if (filtered.size() > MAX_LABELS) filtered.subList(MAX_LABELS, filtered.size()).clear();
        return List.copyOf(filtered);
    }
    private static long distanceSq(BetaPosition a, BetaPosition b) {
        long x = (long) a.x() - b.x(); long y = (long) a.y() - b.y(); long z = (long) a.z() - b.z();
        return x*x + y*y + z*z;
    }
}
