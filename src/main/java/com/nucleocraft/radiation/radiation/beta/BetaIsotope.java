package com.nucleocraft.radiation.radiation.beta;

import java.util.Objects;

/**
 * Only the Beta-facing part of a shared isotope definition. It MUST be adapted
 * from the real isotope registry, not stored as a second physical inventory.
 * RAD/s per unit is a game-balance conversion, not a nuclear activity unit.
 */
public record BetaIsotope(String id, double betaRatePerUnit, double internalDoseMultiplier) {
    public BetaIsotope {
        Objects.requireNonNull(id);
        if (id.isBlank()) throw new IllegalArgumentException("Empty isotope ID");
        if (!Double.isFinite(betaRatePerUnit) || betaRatePerUnit < 0)
            throw new IllegalArgumentException("Invalid beta coefficient");
        if (!Double.isFinite(internalDoseMultiplier) || internalDoseMultiplier < 0)
            throw new IllegalArgumentException("Invalid internal multiplier");
    }
}
