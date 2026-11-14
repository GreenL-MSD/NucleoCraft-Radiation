package com.nucleocraft.radiation.radiation.beta;

/**
 * A quantity of ONE isotope at the already evaluated simulation instant.
 * capRadPerSecond is the persisted Beta-spread cap for this location.
 * No timestamp is stored here: use the host's existing lazy isotope decay.
 */
public record BetaLoad(double quantity, double capRadPerSecond) {
    public BetaLoad {
        if (!Double.isFinite(quantity) || quantity < 0) throw new IllegalArgumentException("Invalid quantity");
        if (!Double.isFinite(capRadPerSecond) || capRadPerSecond < 0)
            throw new IllegalArgumentException("Invalid Beta cap");
    }
    public static BetaLoad initial(double quantity, double betaPerUnit) {
        if (!Double.isFinite(betaPerUnit) || betaPerUnit < 0)
            throw new IllegalArgumentException("Invalid Beta coefficient");
        return new BetaLoad(quantity, quantity * betaPerUnit);
    }
}
