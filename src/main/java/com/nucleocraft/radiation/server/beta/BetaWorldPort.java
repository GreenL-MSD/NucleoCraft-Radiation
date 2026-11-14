package com.nucleocraft.radiation.server.beta;

import com.nucleocraft.radiation.radiation.beta.BetaCell;
import com.nucleocraft.radiation.radiation.beta.BetaPosition;
import com.nucleocraft.radiation.radiation.beta.BetaTransferEngine;
import java.util.Map;

/**
 * NeoForge bridge contract to be implemented against the FROZEN Alpha v0.1.2
 * storage API, once its actual sources are available.
 *
 * All methods run on the server thread. Never keep block entities/chunk objects
 * in this pure module and never update chunks that are not currently loaded.
 */
public interface BetaWorldPort {
    /**
     * Read the current shared physical isotope inventory (lazy-decayed to the same
     * time) and saved Beta caps for loaded, nonempty cells. Do not add a duplicate
     * Beta material database. The map must cover both sides of chunk boundaries.
     */
    Map<BetaPosition, BetaCell> snapshotPhysicalMaterial();
    /** True only for an eligible, loaded, non-air receiving material. */
    boolean canReceive(BetaPosition pos);
    /**
     * Apply the OLD->NEW material deltas atomically on the server thread.
     * Persist caps and isotope changes using the existing Alpha chunk attachments.
     * Preserve unrelated Alpha material, timestamp and dust-layer state.
     */
    void commit(Map<BetaPosition,BetaCell> before, BetaTransferEngine.Result update);
}
