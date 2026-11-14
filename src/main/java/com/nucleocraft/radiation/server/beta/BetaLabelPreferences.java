package com.nucleocraft.radiation.server.beta;

import com.nucleocraft.radiation.radiation.beta.BetaLabelSelection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Debug modes are ephemeral per-player preferences. No world-save or contamination changes. */
public final class BetaLabelPreferences {
    private final Map<UUID, BetaLabelSelection.Settings> byPlayer = new HashMap<>();
    public void set(UUID player, BetaLabelSelection.Settings settings) {
        Objects.requireNonNull(player); Objects.requireNonNull(settings);
        if (settings.mode() == BetaLabelSelection.Mode.OFF) byPlayer.remove(player);
        else byPlayer.put(player, settings);
    }
    public BetaLabelSelection.Settings get(UUID player) {
        return byPlayer.getOrDefault(player, BetaLabelSelection.Settings.off());
    }
    public void remove(UUID player) { byPlayer.remove(player); }
    public void clear() { byPlayer.clear(); }
    public int activePlayerCount() { return byPlayer.size(); }
}
