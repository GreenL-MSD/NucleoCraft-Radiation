package com.nucleocraft.radiation.server.beta;

import com.nucleocraft.radiation.radiation.beta.BetaTransferEngine;
import com.nucleocraft.radiation.radiation.beta.BetaIsotopeLookup;

/** Stateless bridge runner; does not register NeoForge events on its own. */
public final class BetaWorldRunner {
    private final BetaTransferEngine engine;
    private final BetaIsotopeLookup isotopes;
    public BetaWorldRunner(BetaTransferEngine engine, BetaIsotopeLookup isotopes) {
        if (engine == null || isotopes == null) throw new IllegalArgumentException("Missing engine/catalogue");
        this.engine = engine;
        this.isotopes = isotopes;
    }
    public BetaTransferEngine.Statistics serverTick(BetaWorldPort host, double seconds) {
        if (host == null) throw new IllegalArgumentException("Missing host bridge");
        var before = host.snapshotPhysicalMaterial();
        var result = engine.step(before, host::canReceive, isotopes, seconds);
        if (result.statistics().appliedEdges() > 0) host.commit(before, result);
        return result.statistics();
    }
}
