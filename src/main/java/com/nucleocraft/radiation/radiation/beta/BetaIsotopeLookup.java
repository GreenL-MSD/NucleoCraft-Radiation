package com.nucleocraft.radiation.radiation.beta;

/** Adapt from the shared isotope registry. Unknown isotopes must not silently become Beta emitters. */
@FunctionalInterface
public interface BetaIsotopeLookup {
    BetaIsotope find(String id);
}
