package com.nucleocraft.radiation.network.debug;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Profile-neutral, tightly bounded server-authoritative snapshot. */
public record RadiationLabelsPayload(String dimension,List<Entry> labels) implements CustomPacketPayload {
    public record Entry(BlockPos pos,String type,String header,String material) {
        public Entry {
            if(pos==null||type==null||header==null||material==null||type.length()>12||header.length()>95||material.length()>95)
                throw new IllegalArgumentException("Bad label");
        }
    }
    public static final Type<RadiationLabelsPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath("nucleocraft_radiation","radiation_debug_labels"));
    public static final StreamCodec<RegistryFriendlyByteBuf,RadiationLabelsPayload> STREAM_CODEC=
        StreamCodec.of(RadiationLabelsPayload::encode,RadiationLabelsPayload::decode);
    public RadiationLabelsPayload {
        if(dimension==null||dimension.length()>90||labels==null||labels.size()>24)throw new IllegalArgumentException("Bad payload");
        labels=List.copyOf(labels);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    private static void encode(RegistryFriendlyByteBuf buf,RadiationLabelsPayload p){
        buf.writeUtf(p.dimension,90);buf.writeVarInt(p.labels.size());
        for(Entry e:p.labels){buf.writeBlockPos(e.pos());buf.writeUtf(e.type(),12);buf.writeUtf(e.header(),95);buf.writeUtf(e.material(),95);}
    }
    private static RadiationLabelsPayload decode(RegistryFriendlyByteBuf buf){
        String dim=buf.readUtf(90);int count=buf.readVarInt();
        if(count<0||count>24)throw new IllegalArgumentException("Bad label count");
        List<Entry> out=new ArrayList<>(count);
        for(int i=0;i<count;i++)out.add(new Entry(buf.readBlockPos(),buf.readUtf(12),buf.readUtf(95),buf.readUtf(95)));
        return new RadiationLabelsPayload(dim,out);
    }
}
