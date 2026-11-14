package com.nucleocraft.radiation.radiation.beta;

import java.util.Map;
import java.util.TreeMap;

/**
 * Snapshot of the HOST isotope material at a given block, plus Beta caps.
 * Contains non-Beta isotopes unchanged so sharing a real isotope inventory is safe.
 */
public final class BetaCell {
    private static final BetaCell EMPTY = new BetaCell(Map.of());
    private final Map<String, BetaLoad> isotopes;

    public BetaCell(Map<String, BetaLoad> isotopes) {
        if (isotopes == null) throw new IllegalArgumentException("Null isotope map");
        TreeMap<String, BetaLoad> copy = new TreeMap<>();
        isotopes.forEach((id, load) -> {
            if (id == null || id.isBlank() || load == null) throw new IllegalArgumentException("Invalid isotope entry");
            if (load.quantity() > 1e-14) copy.put(id, load);
        });
        this.isotopes = Map.copyOf(copy);
    }
    public Map<String, BetaLoad> isotopes() { return isotopes; }
    public BetaLoad get(String isotope) { return isotopes.getOrDefault(isotope, new BetaLoad(0, 0)); }
    public boolean isEmpty() { return isotopes.isEmpty(); }
    public static BetaCell empty() { return EMPTY; }
    public static BetaCell single(String id, double quantity, double ratePerUnit) {
        return new BetaCell(Map.of(id, BetaLoad.initial(quantity, ratePerUnit)));
    }
}
