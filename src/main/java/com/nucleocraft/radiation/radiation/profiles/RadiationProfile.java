package com.nucleocraft.radiation.radiation.profiles;

/** Common contract; Beta and Gamma will each get their own profile in later milestones. */
public interface RadiationProfile {
    String id();
    double externalAbsorbedRate(double emittedRate);
    double internalAbsorbedRate(double emittedRate);
}
