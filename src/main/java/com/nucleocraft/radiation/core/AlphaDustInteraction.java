package com.nucleocraft.radiation.core;

/** Shared, dependency-free contact and one-layer dust-break rules.
 * Keeps the Alpha gameplay policy independently testable without NeoForge.
 */
public final class AlphaDustInteraction {
    private AlphaDustInteraction() {}

    /** A final dust layer remains in place instead of redistributing. */
    public static boolean retainsOnBreak(int layers) {
        if (layers < 1 || layers > 8) throw new IllegalArgumentException("Dust layers must be 1..8");
        return layers == 1;
    }

    /** Dust must actually be contacted; simply flying over it is not contact.
     * A jump only counts if it was launched from the ground on the previous tick.
     */
    public static boolean canDisturb(boolean flying, boolean fallFlying, boolean riding,
                                     boolean onGround, boolean justJumpedFromGround) {
        return !flying && !fallFlying && !riding && (onGround || justJumpedFromGround);
    }
}
