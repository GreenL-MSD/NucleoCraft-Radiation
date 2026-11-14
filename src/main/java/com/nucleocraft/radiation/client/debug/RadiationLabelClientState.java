package com.nucleocraft.radiation.client.debug;

import com.nucleocraft.radiation.network.debug.RadiationLabelsPayload;
import java.util.List;

public final class RadiationLabelClientState {
    private static RadiationLabelsPayload current=new RadiationLabelsPayload("",List.of());
    private static long updated;
    private RadiationLabelClientState(){}
    public static void receive(RadiationLabelsPayload payload){current=payload;updated=System.nanoTime();}
    public static List<RadiationLabelsPayload.Entry> labels(String dimension){
        return dimension.equals(current.dimension())&&System.nanoTime()-updated<1_500_000_000L?current.labels():List.of();
    }
    public static void clear(){current=new RadiationLabelsPayload("",List.of());updated=0;}
}
