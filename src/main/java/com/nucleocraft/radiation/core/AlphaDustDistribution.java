package com.nucleocraft.radiation.core;

import java.util.ArrayList;
import java.util.List;

/** Alpha release: 2-block Manhattan radius, half the weight per extra block.
 * Normalisation is essential: a 100-unit event must NOT spawn 100+50+25 units.
 */
public final class AlphaDustDistribution {
    public static final int MAX_DISTANCE=2;
    private AlphaDustDistribution() {}
    public record Target<T>(T location,int distance) {
        public Target {if(distance<0||distance>MAX_DISTANCE)throw new IllegalArgumentException("Bad distance");}
    }
    public record Portion<T>(T location,double fraction) {}
    public static <T> List<Portion<T>> normalized(List<Target<T>> eligible) {
        if(eligible.isEmpty())return List.of();
        double weight=0;
        for(var t:eligible)weight+=Math.scalb(1.0,-t.distance());
        List<Portion<T>> result=new ArrayList<>();
        for(var t:eligible)result.add(new Portion<>(t.location(),Math.scalb(1.0,-t.distance())/weight));
        return List.copyOf(result);
    }
    public static int manhattan(int dx,int dy,int dz){return Math.abs(dx)+Math.abs(dy)+Math.abs(dz);}
}
