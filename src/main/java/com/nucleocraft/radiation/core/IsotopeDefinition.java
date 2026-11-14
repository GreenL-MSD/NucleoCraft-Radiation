package com.nucleocraft.radiation.core;

/** Physical matter identity is separate from the Alpha behaviour profile. */
public record IsotopeDefinition(String id, String displayName, double gameplayHalfLifeSeconds,
                                double alphaEmissionPerUnit, double betaEmissionPerUnit) {
    public IsotopeDefinition(String id,String displayName,double gameplayHalfLifeSeconds,double alphaEmissionPerUnit) {
        this(id,displayName,gameplayHalfLifeSeconds,alphaEmissionPerUnit,0);
    }
    public IsotopeDefinition {
        if (id == null || !id.matches("[a-z][a-z0-9_]*")) throw new IllegalArgumentException("Bad isotope ID");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("Bad display name");
        if (!(gameplayHalfLifeSeconds > 0) || !Double.isFinite(gameplayHalfLifeSeconds))
            throw new IllegalArgumentException("Bad half-life");
        if (alphaEmissionPerUnit < 0 || !Double.isFinite(alphaEmissionPerUnit))
            throw new IllegalArgumentException("Bad alpha emission");
        if (betaEmissionPerUnit < 0 || !Double.isFinite(betaEmissionPerUnit))
            throw new IllegalArgumentException("Bad beta emission");
    }
    public double remainingFraction(double elapsedSeconds) {
        return Math.exp(-Math.log(2) * Math.max(0, elapsedSeconds) / gameplayHalfLifeSeconds);
    }
}
