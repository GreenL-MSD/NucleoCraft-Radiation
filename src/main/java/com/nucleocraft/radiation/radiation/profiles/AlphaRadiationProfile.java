package com.nucleocraft.radiation.radiation.profiles;

/** Alpha radiation's biological behaviour. It does NOT move isotope matter.
 * Loose dust transport is a physical material event elsewhere in the engine.
 */
public final class AlphaRadiationProfile implements RadiationProfile {
    public static final AlphaRadiationProfile INSTANCE=new AlphaRadiationProfile();
    public static final double INTERNAL_WEIGHT=5.0; // Explicit gameplay dose weighting.
    private AlphaRadiationProfile() {}
    @Override public String id() {return "alpha";}
    @Override public double externalAbsorbedRate(double emittedRate) {return 0.0;}
    @Override public double internalAbsorbedRate(double emittedRate) {
        return Math.max(0, emittedRate)*INTERNAL_WEIGHT;
    }
}
