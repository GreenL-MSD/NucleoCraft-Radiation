package com.nucleocraft.radiation;

import com.mojang.logging.LogUtils;
import com.nucleocraft.radiation.server.AlphaAttachments;
import com.nucleocraft.radiation.server.AlphaBlockEvents;
import com.nucleocraft.radiation.server.AlphaCommands;
import com.nucleocraft.radiation.server.AlphaSimulation;
import com.nucleocraft.radiation.server.AlphaTooltips;
import com.nucleocraft.radiation.world.AlphaDustRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Ground-up Alpha-only rewrite. No legacy Beta/Gamma handlers registered. */
@Mod(NucleoCraftRadiation.MOD_ID)
public final class NucleoCraftRadiation {
    public static final String MOD_ID="nucleocraft_radiation";
    private static final Logger LOGGER=LogUtils.getLogger();
    public NucleoCraftRadiation(IEventBus bus,ModContainer container) {
        AlphaAttachments.register(bus);
        AlphaDustRegistry.register(bus);
        AlphaSimulation simulation=new AlphaSimulation();
        NeoForge.EVENT_BUS.register(simulation);
        NeoForge.EVENT_BUS.register(new AlphaBlockEvents(simulation));
        NeoForge.EVENT_BUS.register(new AlphaCommands(simulation));
        NeoForge.EVENT_BUS.register(new AlphaTooltips());
        LOGGER.info("NucleoCraft Radiation Alpha Rewrite 0.1.1 - Alpha-only physics and physical matter");
    }
}
