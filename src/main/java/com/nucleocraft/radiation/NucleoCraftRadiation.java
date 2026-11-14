package com.nucleocraft.radiation;

import com.mojang.logging.LogUtils;
import com.nucleocraft.radiation.server.data.RadiationAttachments;
import com.nucleocraft.radiation.server.beta.BetaSimulation;
import com.nucleocraft.radiation.commands.beta.BetaCommands;
import com.nucleocraft.radiation.commands.beta.BetaLabelCommands;
import com.nucleocraft.radiation.commands.alpha.AlphaLabelCommands;
import com.nucleocraft.radiation.server.debug.RadiationLabelServer;
import com.nucleocraft.radiation.network.debug.RadiationLabelNetwork;
import com.nucleocraft.radiation.server.alpha.AlphaBlockEvents;
import com.nucleocraft.radiation.commands.alpha.AlphaCommands;
import com.nucleocraft.radiation.server.alpha.AlphaSimulation;
import com.nucleocraft.radiation.client.alpha.AlphaTooltips;
import com.nucleocraft.radiation.client.beta.BetaTooltips;
import com.nucleocraft.radiation.client.shared.MaterialTooltips;
import com.nucleocraft.radiation.world.alpha.AlphaDustRegistry;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Alpha-stable v0.1.2 baseline with independent Beta v0.2.0 host and shared floating labels. */
@Mod(NucleoCraftRadiation.MOD_ID)
public final class NucleoCraftRadiation {
    public static final String MOD_ID="nucleocraft_radiation";
    private static final Logger LOGGER=LogUtils.getLogger();
    public NucleoCraftRadiation(IEventBus bus,ModContainer container) {
        RadiationAttachments.register(bus);
        AlphaDustRegistry.register(bus);
        RadiationLabelNetwork.register(bus);
        AlphaSimulation simulation=new AlphaSimulation();
        NeoForge.EVENT_BUS.register(simulation);
        NeoForge.EVENT_BUS.register(new AlphaBlockEvents(simulation));
        NeoForge.EVENT_BUS.register(new AlphaCommands(simulation));
        NeoForge.EVENT_BUS.register(new MaterialTooltips());
        NeoForge.EVENT_BUS.register(new AlphaTooltips());
        NeoForge.EVENT_BUS.register(new BetaTooltips());
        BetaSimulation beta=new BetaSimulation();
        NeoForge.EVENT_BUS.register(beta);
        NeoForge.EVENT_BUS.register(new BetaCommands(beta));
        RadiationLabelServer labels=new RadiationLabelServer();
        NeoForge.EVENT_BUS.register(labels);
        NeoForge.EVENT_BUS.register(new AlphaLabelCommands(labels));
        NeoForge.EVENT_BUS.register(new BetaLabelCommands(labels));
        LOGGER.info("NucleoCraft Radiation v0.2.0 integration candidate - Alpha + Beta + live labels");
    }
}
