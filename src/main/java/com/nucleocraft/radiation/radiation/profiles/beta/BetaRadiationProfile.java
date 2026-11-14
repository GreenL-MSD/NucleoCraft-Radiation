package com.nucleocraft.radiation.radiation.profiles.beta;

import com.nucleocraft.radiation.radiation.beta.BetaIsotope;
import com.nucleocraft.radiation.radiation.beta.BetaIsotopeLookup;
import java.util.Map;

/**
 * Beta behaviour ONLY: external transport, shielding and internal dose.
 * No Minecraft, dust, save, or block code belongs in a radiation profile.
 * All distance/shield constants are gameplay tuning, not a particle transport simulator.
 */
public final class BetaRadiationProfile {
    public static final double DEFAULT_MAX_AIR_RANGE_METRES = 4.0;
    public static final double DEFAULT_AIR_ATTENUATION_PER_METRE = 0.7;
    private final double maxAirRangeMetres;
    private final double attenuationPerMetre;

    public BetaRadiationProfile() {
        this(DEFAULT_MAX_AIR_RANGE_METRES, DEFAULT_AIR_ATTENUATION_PER_METRE);
    }
    public BetaRadiationProfile(double maxAirRangeMetres, double attenuationPerMetre) {
        if (!Double.isFinite(maxAirRangeMetres) || maxAirRangeMetres <= 0)
            throw new IllegalArgumentException("Invalid Beta air range");
        if (!Double.isFinite(attenuationPerMetre) || attenuationPerMetre < 0)
            throw new IllegalArgumentException("Invalid air attenuation");
        this.maxAirRangeMetres = maxAirRangeMetres;
        this.attenuationPerMetre = attenuationPerMetre;
    }
    /** Configured/synthetic sources emit Beta but NEVER create isotope material. */
    public double externalRate(double sourceRadPerSecond, double distanceMetres, double shieldingTransmission) {
        if (!Double.isFinite(sourceRadPerSecond) || sourceRadPerSecond < 0
                || !Double.isFinite(distanceMetres) || distanceMetres < 0
                || !Double.isFinite(shieldingTransmission) || shieldingTransmission < 0 || shieldingTransmission > 1)
            throw new IllegalArgumentException("Invalid Beta exposure input");
        if (distanceMetres > maxAirRangeMetres) return 0;
        return sourceRadPerSecond * Math.exp(-attenuationPerMetre * distanceMetres) * shieldingTransmission;
    }
    /** Inhaled isotope material remains with the PLAYER after external sources disappear. */
    public double internalRate(Map<String, Double> physicalIsotopes, BetaIsotopeLookup lookup) {
        if (physicalIsotopes == null || lookup == null) throw new IllegalArgumentException("Missing material/registry");
        double rate = 0;
        for (var e : physicalIsotopes.entrySet()) {
            double quantity = e.getValue();
            if (!Double.isFinite(quantity) || quantity < 0) throw new IllegalArgumentException("Invalid internal material");
            BetaIsotope isotope = lookup.find(e.getKey());
            if (isotope != null) rate += quantity * isotope.betaRatePerUnit() * isotope.internalDoseMultiplier();
        }
        return rate;
    }
}
