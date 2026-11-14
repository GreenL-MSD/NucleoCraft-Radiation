package com.nucleocraft.radiation.server.debug;
import com.nucleocraft.radiation.radiation.beta.BetaBlockLabel;
import com.nucleocraft.radiation.radiation.beta.BetaCell;

import com.nucleocraft.radiation.core.IsotopeDefinitions;
import com.nucleocraft.radiation.radiation.beta.*;
import com.nucleocraft.radiation.server.alpha.AlphaWorld;
import com.nucleocraft.radiation.server.beta.BetaWorld;
import com.nucleocraft.radiation.server.data.ChunkRadiationData;
import com.nucleocraft.radiation.server.data.RadiationAttachments;
import com.nucleocraft.radiation.network.debug.RadiationLabelsPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-authoritative, loaded-chunk-only snapshots. Per-player settings are never saved. */
public final class RadiationLabelServer {
    public enum Mode{OFF,LOOK,NEARBY}
    public record Settings(Mode mode,int radius){
        public Settings {if(mode==null||radius<1||radius>12)throw new IllegalArgumentException("Invalid debug settings");}
    }
    private final Map<UUID,Map<String,Settings>> settings=new HashMap<>();
    private long ticks;
    public void configure(ServerPlayer player,String type,Mode mode,int radius){
        if(!type.equals("alpha")&&!type.equals("beta"))return;
        settings.computeIfAbsent(player.getUUID(),u->new HashMap<>()).put(type,new Settings(mode,radius));
        update(player);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event){
        if(++ticks%10!=0)return;
        for(var p:event.getServer().getPlayerList().getPlayers())if(settings.containsKey(p.getUUID()))update(p);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){settings.remove(event.getEntity().getUUID());}
    @SubscribeEvent public void stop(ServerStoppedEvent event){settings.clear();ticks=0;}
    private void update(ServerPlayer player){
        List<RadiationLabelsPayload.Entry> all=new ArrayList<>();
        for(String type:List.of("alpha","beta")){
            Settings state=settings.getOrDefault(player.getUUID(),Map.of()).get(type);
            if(state==null||state.mode()==Mode.OFF)continue;
            if(state.mode()==Mode.LOOK){
                if(player.pick(state.radius(),1f,false) instanceof BlockHitResult hit){
                    var found=label(player.serverLevel(),hit.getBlockPos(),type);
                    if(found!=null)all.add(found);
                }
            }else{
                // Only inspect sparse attachment positions, not 20,000+ blocks in a cube.
                ServerLevel level=player.serverLevel();BlockPos center=player.blockPosition();
                int radius=state.radius(),cx=center.getX()>>4,cz=center.getZ()>>4;
                int chunkRadius=(radius+15)/16;
                for(int x=cx-chunkRadius;x<=cx+chunkRadius;x++)for(int z=cz-chunkRadius;z<=cz+chunkRadius;z++){
                    var chunk=level.getChunkSource().getChunkNow(x,z);
                    if(chunk==null||!chunk.hasData(RadiationAttachments.CHUNK.get()))continue;
                    var data=chunk.getData(RadiationAttachments.CHUNK.get());
                    Set<Integer> positions=new HashSet<>(data.depositsSnapshot().keySet());
                    if(type.equals("alpha"))positions.addAll(data.syntheticAlphaSnapshot().keySet());
                    else positions.addAll(data.syntheticBetaSnapshot().keySet());
                    for(int packed:positions){
                        BlockPos pos=ChunkRadiationData.unpack(x,z,packed);
                        if(pos.distSqr(center)>radius*radius)continue;
                        var found=label(level,pos,type);
                        if(found!=null)all.add(found);
                    }
                }
            }
        }
        BlockPos center=player.blockPosition();
        all.sort(Comparator.comparingDouble((RadiationLabelsPayload.Entry e)->e.pos().distSqr(center))
            .thenComparing(e->e.type()).thenComparing(e->e.pos().asLong()));
        if(all.size()>24)all=all.subList(0,24);
        PacketDistributor.sendToPlayer(player,new RadiationLabelsPayload(player.serverLevel().dimension().location().toString(),all));
    }
    private static RadiationLabelsPayload.Entry label(ServerLevel level,BlockPos pos,String type){
        if(!AlphaWorld.loaded(level,pos))return null;
        long now=System.currentTimeMillis();
        var record=AlphaWorld.deposit(level,pos);
        if(type.equals("alpha")){
            double rad=(record==null?0:record.material().alphaEmissions(now));
            double synthetic=AlphaWorld.synthetic(level,pos);
            if(rad<=0&&synthetic<=0)return null;
            String name=record==null?"[Synthetic Alpha]":summary(record.material().at(now).units(),true);
            return new RadiationLabelsPayload.Entry(pos,type,String.format(Locale.ROOT,"α %.3f RAD/s | external 0",rad+synthetic),name);
        }
        BetaCell cell=BetaWorld.at(level,pos,now);
        double synth=BetaWorld.synthetic(level,pos);
        if(cell.isEmpty()&&synth<=0)return null;
        Optional<BetaBlockLabel> detail=BetaBlockLabel.fromCell(BetaWorld.key(pos),cell,BetaWorld.ISOTOPES);
        if(detail.isEmpty()&&synth<=0)return null;
        double rate=detail.map(BetaBlockLabel::betaRadPerSecond).orElse(0.0)+synth;
        double cap=detail.map(BetaBlockLabel::capRadPerSecond).orElse(0.0);
        String material=detail.map(BetaBlockLabel::material).orElse("[Synthetic Beta]");
        if(synth>0&&detail.isPresent())material+=" +synthetic";
        return new RadiationLabelsPayload.Entry(pos,type,String.format(Locale.ROOT,"β %.3f RAD/s | cap %.3f",rate,cap),material);
    }
    private static String summary(Map<String,Double> units,boolean alpha){
        String id=units.entrySet().stream().filter(e->{var d=IsotopeDefinitions.require(e.getKey());return alpha?d.alphaEmissionPerUnit()>0:d.betaEmissionPerUnit()>0;})
            .max(Comparator.comparingDouble(Map.Entry::getValue)).map(Map.Entry::getKey).orElse("");
        if(id.isEmpty())return "[Synthetic Alpha]";
        return String.format(Locale.ROOT,"[%s] %.4f units",IsotopeDefinitions.require(id).displayName(),units.get(id));
    }
}
