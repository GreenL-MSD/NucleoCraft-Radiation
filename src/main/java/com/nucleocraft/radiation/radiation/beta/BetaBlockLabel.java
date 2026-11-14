package com.nucleocraft.radiation.radiation.beta;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** One server-authoritative, read-only Beta debug label for a block position. */
public record BetaBlockLabel(BetaPosition pos, String isotope, int additionalIsotopes,
                             double quantity, double betaRadPerSecond, double capRadPerSecond) {
    public BetaBlockLabel {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(isotope, "isotope");
        if (isotope.isBlank() || isotope.length() > 64 || additionalIsotopes < 0 || additionalIsotopes > 255)
            throw new IllegalArgumentException("Invalid isotope summary");
        for (double v : new double[] { quantity, betaRadPerSecond, capRadPerSecond })
            if (!Double.isFinite(v) || v < 0) throw new IllegalArgumentException("Invalid Beta label numeric value");
    }

    /** Read-only projection of the shared isotope material. NEVER store a second inventory. */
    public static Optional<BetaBlockLabel> fromCell(BetaPosition pos, BetaCell cell, BetaIsotopeLookup lookup) {
        Objects.requireNonNull(pos); Objects.requireNonNull(cell); Objects.requireNonNull(lookup);
        double rate = 0, cap = 0, quantity = 0, biggestRate = -1;
        int count = 0;
        String dominant = "";
        for (Map.Entry<String, BetaLoad> e : new TreeMap<>(cell.isotopes()).entrySet()) {
            BetaIsotope def = lookup.find(e.getKey());
            if (def == null || def.betaRatePerUnit() <= 0 || e.getValue().quantity() <= 0) continue;
            double contribution = e.getValue().quantity() * def.betaRatePerUnit();
            if (contribution <= 0) continue;
            count++;
            rate += contribution;
            cap += e.getValue().capRadPerSecond();
            quantity += e.getValue().quantity();
            if (contribution > biggestRate) {
                biggestRate = contribution;
                dominant = displayName(e.getKey());
            }
        }
        if (count == 0 || !Double.isFinite(rate) || !Double.isFinite(cap) || !Double.isFinite(quantity))
            return Optional.empty();
        return Optional.of(new BetaBlockLabel(pos, dominant, count - 1, quantity, rate, cap));
    }

    /** Debug-only configured emitter; does not contain physical isotope material. */
    public static BetaBlockLabel synthetic(BetaPosition pos, double rateRadPerSecond) {
        return new BetaBlockLabel(pos, "Synthetic Beta", 0, 0, rateRadPerSecond, 0);
    }
    public boolean isSynthetic() { return isotope.equals("Synthetic Beta"); }
    public String header() {
        return String.format(Locale.ROOT, "β %.3f RAD/s | cap %.3f", betaRadPerSecond, capRadPerSecond);
    }
    public String material() {
        if (isSynthetic()) return "[Synthetic Beta] configured source";
        return String.format(Locale.ROOT, "[%s]%s %.4f units", isotope,
            additionalIsotopes == 0 ? "" : " +" + additionalIsotopes, quantity);
    }
    public String positionLine() {
        return String.format(Locale.ROOT, "x%d y%d z%d", pos.x(), pos.y(), pos.z());
    }
    public static String displayName(String id) {
        StringBuilder out = new StringBuilder();
        for (String s : id.replace('-', '_').split("_")) {
            if (s.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return out.toString();
    }
}
