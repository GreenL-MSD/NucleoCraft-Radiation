package com.nucleocraft.radiation.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Small Alpha-only starter registry. Values are gameplay tuning, not real half-lives. */
public final class IsotopeDefinitions {
    private static final Map<String,IsotopeDefinition> MAP;
    static {
        Map<String,IsotopeDefinition> m=new LinkedHashMap<>();
        for (IsotopeDefinition d : new IsotopeDefinition[]{
                new IsotopeDefinition("polonium_210", "Polonium 210", 3600, 1.80),
                new IsotopeDefinition("uranium_238", "Uranium 238", 30*86400.0, 1.00),
                new IsotopeDefinition("americium_241", "Americium 241", 24*3600.0, 1.25)}) m.put(d.id(),d);
        MAP=Collections.unmodifiableMap(m);
    }
    private IsotopeDefinitions() {}
    public static IsotopeDefinition require(String id) {
        IsotopeDefinition d=MAP.get(id);
        if (d==null) throw new IllegalArgumentException("Unknown Alpha isotope: " + id);
        return d;
    }
    public static Map<String,IsotopeDefinition> all() {return MAP;}
}
