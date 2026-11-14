package com.nucleocraft.radiation.network.debug;

import com.nucleocraft.radiation.client.debug.RadiationLabelClientState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A single packet for both types. No second client/server material store. */
public final class RadiationLabelNetwork {
    private RadiationLabelNetwork(){}
    public static void register(IEventBus modBus){
        modBus.addListener(RadiationLabelNetwork::onPayload);
    }
    private static void onPayload(RegisterPayloadHandlersEvent e){
        e.registrar("1").playToClient(RadiationLabelsPayload.TYPE,RadiationLabelsPayload.STREAM_CODEC,
            (payload,ctx)->RadiationLabelClientState.receive(payload));
    }
}
