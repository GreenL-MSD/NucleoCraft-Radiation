package com.nucleocraft.radiation.player.alpha;
import com.nucleocraft.radiation.core.IsotopeInventory;

import com.nucleocraft.radiation.radiation.profiles.alpha.AlphaRadiationProfile;

/** Two body compartments. All isotope inventories decay lazily, biological
 * elimination is independent of nuclear decay. Accumulated dose is historical.
 */
public final class PlayerAlphaState {
    public static final double INHALED_BIOLOGICAL_HALF_LIFE=6*3600.0;
    public static final double INGESTED_BIOLOGICAL_HALF_LIFE=12*3600.0;
    private IsotopeInventory inhaled, ingested;
    private double accumulatedRad;
    public PlayerAlphaState(long now) {inhaled=IsotopeInventory.empty(now);ingested=IsotopeInventory.empty(now);}
    public PlayerAlphaState(IsotopeInventory inhaled,IsotopeInventory ingested,double dose) {
        this.inhaled=inhaled;
        this.ingested=ingested;
        this.accumulatedRad=Math.max(0,dose);
    }
    public IsotopeInventory inhaled(){return inhaled;}
    public IsotopeInventory ingested(){return ingested;}
    public double accumulatedRad(){return accumulatedRad;}
    private static IsotopeInventory advance(IsotopeInventory material,long now,double clearance) {
        double elapsed=Math.max(0,now-material.epochMillis())/1000.0;
        return material.at(now).scaled(Math.pow(0.5,elapsed/clearance));
    }
    public void advance(long now) {
        inhaled=advance(inhaled,now,INHALED_BIOLOGICAL_HALF_LIFE);
        ingested=advance(ingested,now,INGESTED_BIOLOGICAL_HALF_LIFE);
    }
    public void inhale(IsotopeInventory intake,long now) {advance(now);inhaled=inhaled.plus(intake,now);}
    public void ingest(IsotopeInventory intake,long now) {advance(now);ingested=ingested.plus(intake,now);}
    public double internalAlphaEmission(long now) {
        advance(now);
        return inhaled.alphaEmissions(now)+ingested.alphaEmissions(now);
    }
    public double internalDoseRate(long now) {
        return AlphaRadiationProfile.INSTANCE.internalAbsorbedRate(internalAlphaEmission(now));
    }
    public double internalBetaEmission(long now) {
        advance(now);
        return inhaled.betaEmissions(now)+ingested.betaEmissions(now);
    }
    public void addAbsorbedDose(double amount) {
        if(Double.isFinite(amount)&&amount>0)accumulatedRad=Math.min(1e9,accumulatedRad+amount);
    }
    public void tickDose(long now,double seconds) {
        if(seconds<0||!Double.isFinite(seconds))throw new IllegalArgumentException("Bad seconds");
        accumulatedRad=Math.min(1e9,accumulatedRad+internalDoseRate(now)*seconds);
    }
    public void reset(long now) {
        inhaled=IsotopeInventory.empty(now);ingested=IsotopeInventory.empty(now);accumulatedRad=0;
    }
}
