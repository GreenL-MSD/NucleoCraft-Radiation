package com.nucleocraft.radiation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, conserved physical isotope quantities measured at a reference epoch.
 * A decay call never changes the original record. Transfer always aligns epochs first.
 * Subnormal remnants below EPS are truncated solely as a documented numeric tolerance.
 */
public final class IsotopeInventory {
    public static final double EPS=1.e-12;
    private final long epoch;
    private final Map<String,Double> units;
    public IsotopeInventory(Map<String,Double> quantities, long epochMillis) {
        if(epochMillis<0 || quantities==null || quantities.size()>32)throw new IllegalArgumentException("Bad inventory");
        Map<String,Double> m=new LinkedHashMap<>();
        for(var entry:quantities.entrySet()) {
            IsotopeDefinitions.require(entry.getKey());
            Double n=entry.getValue();
            if(n==null || !Double.isFinite(n) || n<0)throw new IllegalArgumentException("Bad units");
            if(n>EPS)m.put(entry.getKey(),n);
        }
        this.epoch=epochMillis;
        this.units=Collections.unmodifiableMap(m);
    }
    public static IsotopeInventory empty(long now) {return new IsotopeInventory(Map.of(),now);}
    public static IsotopeInventory of(String isotope,double quantity,long now) {
        return new IsotopeInventory(Map.of(isotope,quantity),now);
    }
    public long epochMillis() {return epoch;}
    public Map<String,Double> units() {return units;}
    public boolean isEmpty() {return units.isEmpty();}
    public double totalUnits() {return units.values().stream().mapToDouble(Double::doubleValue).sum();}
    public IsotopeInventory at(long millis) {
        if(millis<=epoch)return this;
        Map<String,Double> m=new LinkedHashMap<>();
        for(var entry:units.entrySet())
            m.put(entry.getKey(),entry.getValue()*IsotopeDefinitions.require(entry.getKey()).remainingFraction((millis-epoch)/1000.0));
        return new IsotopeInventory(m,millis);
    }
    public double alphaEmissions(long millis) {
        double sum=0;
        long delta=Math.max(0,millis-epoch);
        for(var entry:units.entrySet()) {
            IsotopeDefinition d=IsotopeDefinitions.require(entry.getKey());
            sum += entry.getValue()*d.remainingFraction(delta/1000.0)*d.alphaEmissionPerUnit();
        }
        return sum;
    }
    public IsotopeInventory scaled(double f) {
        if(!Double.isFinite(f)||f<0||f>1)throw new IllegalArgumentException("Fraction must be 0..1");
        Map<String,Double> m=new LinkedHashMap<>();
        units.forEach((k,v)->m.put(k,v*f));
        return new IsotopeInventory(m,epoch);
    }
    public IsotopeInventory plus(IsotopeInventory other,long millis) {
        IsotopeInventory a=at(millis),b=other.at(millis);
        Map<String,Double> m=new LinkedHashMap<>(a.units);
        b.units.forEach((k,v)->m.merge(k,v,Double::sum));
        return new IsotopeInventory(m,Math.max(a.epoch,b.epoch));
    }
    /** Material that physically leaves a donor and arrives at a receiver. */
    public Split split(double fraction) {
        IsotopeInventory moved=scaled(fraction);
        Map<String,Double> rest=new LinkedHashMap<>();
        // Subtraction avoids rounding-driven increase in donor + receiver total.
        units.forEach((k,v)->rest.put(k,v-moved.units.getOrDefault(k,0.0)));
        return new Split(new IsotopeInventory(rest,epoch),moved);
    }
    public record Split(IsotopeInventory remaining,IsotopeInventory moved) {}
    @Override public boolean equals(Object x) {
        return x instanceof IsotopeInventory o && epoch==o.epoch && units.equals(o.units);
    }
    @Override public int hashCode() {return units.hashCode()*31+Long.hashCode(epoch);}
}
