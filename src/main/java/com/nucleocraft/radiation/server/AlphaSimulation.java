package com.nucleocraft.radiation.server;

import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.core.AlphaDustInteraction;
import com.nucleocraft.radiation.core.PlayerAlphaState;
import com.nucleocraft.radiation.world.AlphaDustRegistry;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.level.chunk.LevelChunk;

/** Bounded server-only environmental work. No global per-block ticking.
 * Cloud queues rebuild on chunk load from persistent chunk attachments.
 */
public final class AlphaSimulation {
    private static final int CLOUD_JOBS_PER_SECOND=48;
    private static final int RELEASES_PER_TICK=32;
    private static final double CLOUD_INHALED_FRACTION=.02;
    private static final double CLOUD_SETTLE_FRACTION=.25;
    private long ticks;
    private final Map<ServerLevel,Set<BlockPos>> activeClouds=new HashMap<>();
    private final Map<UUID,Vec3> lastFeet=new HashMap<>();
    private final Map<UUID,Boolean> lastGrounded=new HashMap<>();
    private final Map<UUID,Long> lastDisturbance=new HashMap<>();
    private final ArrayDeque<ReleaseEvent> pendingReleases=new ArrayDeque<>();
    private record ReleaseEvent(ServerLevel level,BlockPos pos) {}
    public void scheduleCloud(ServerLevel level,BlockPos pos) {
        if(AlphaWorld.loaded(level,pos))activeClouds.computeIfAbsent(level,k->new HashSet<>()).add(pos.immutable());
    }
    public void scheduleRelease(ServerLevel level,BlockPos pos,IsotopeInventory material) {
        if(material==null||material.isEmpty()||!AlphaWorld.loaded(level,pos))return;
        // Source material becomes a persistent chunk cloud BEFORE scheduling the event.
        // Unloading or saving in this one-tick window never loses physical matter.
        AlphaWorld.addCloud(level,pos,material,System.currentTimeMillis());
        scheduleCloud(level,pos);
        pendingReleases.addLast(new ReleaseEvent(level,pos.immutable()));
    }
    public int queuedClouds() {return activeClouds.values().stream().mapToInt(Set::size).sum();}
    public int queuedReleases() {return pendingReleases.size();}
    @SubscribeEvent public void onPlayerClone(PlayerEvent.Clone event) {
        if(!(event.getEntity() instanceof ServerPlayer replacement))return;
        if(event.isWasDeath()) {
            // Deliberate default: respawn clears old accumulated/internal dose.
            replacement.setData(AlphaAttachments.PLAYER.get(),new PlayerAlphaAttachment());
        } else if(event.getOriginal().hasData(AlphaAttachments.PLAYER.get())) {
            // Non-death cloning (e.g. return from End) preserves contamination.
            replacement.setData(AlphaAttachments.PLAYER.get(),
                    event.getOriginal().getData(AlphaAttachments.PLAYER.get()));
        }
    }
    @SubscribeEvent public void onChunkLoad(ChunkEvent.Load event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk))return;
        if(!chunk.hasData(AlphaAttachments.CHUNK.get()))return;
        chunk.getData(AlphaAttachments.CHUNK.get()).cloudsSnapshot().forEach((packed,material)->
                activeClouds.computeIfAbsent(level,k->new HashSet<>()).add(
                        ChunkAlphaData.unpack(chunk.getPos().x,chunk.getPos().z,packed)));
    }
    @SubscribeEvent public void onChunkUnload(ChunkEvent.Unload event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getChunk() instanceof LevelChunk chunk))return;
        Set<BlockPos> set=activeClouds.get(level);
        if(set!=null)set.removeIf(p->p.getX()>>4==chunk.getPos().x && p.getZ()>>4==chunk.getPos().z);
    }
    @SubscribeEvent public void onServerTick(ServerTickEvent.Post event) {
        ticks++;
        long now=System.currentTimeMillis();
        // Break releases are deferred so air at the original dust position is real.
        for(int i=0;i<RELEASES_PER_TICK&&!pendingReleases.isEmpty();i++) {
            ReleaseEvent release=pendingReleases.pollFirst();
            if(AlphaWorld.loaded(release.level(),release.pos())) {
                IsotopeInventory stored=AlphaWorld.cloud(release.level(),release.pos());
                if(stored!=null&&!stored.isEmpty()) {
                    AlphaWorld.cloud(release.level(),release.pos(),null,now);
                    AlphaDustTransport.release(release.level(),release.pos(),stored,now,this);
                    sampleImmediate(release.level(),release.pos(),now);
                }
            }
            // If unloaded, the complete quantity remains safely persisted as a cloud.
            // It will resume settling when the chunk loads, without loading it here.
        }
        for(ServerPlayer player:event.getServer().getPlayerList().getPlayers())
            if(player.isAlive()&&!player.isSpectator())disturbDust(player,now);
        if(ticks%20==0) {
            for(ServerPlayer player:event.getServer().getPlayerList().getPlayers()) {
                if(!player.isAlive()||player.isSpectator())continue;
                PlayerAlphaAttachment attachment=player.getData(AlphaAttachments.PLAYER.get());
                attachment.state().tickDose(now,1.0);
                // Resets on death are explicit; never reset internal contamination on dimension travel.
                player.setData(AlphaAttachments.PLAYER.get(),attachment);
            }
            tickClouds(event.getServer(),now);
        }
        if(ticks%200==0) {
            Set<UUID> online=new HashSet<>();
            for(ServerPlayer p:event.getServer().getPlayerList().getPlayers())online.add(p.getUUID());
            lastFeet.keySet().retainAll(online);
            lastGrounded.keySet().retainAll(online);
            lastDisturbance.keySet().retainAll(online);
        }
    }
    /** Movement always emits visible particles on loose dust, while only real material
     * enters the cloud. Player steps are rate-limited to prevent tick-speed extraction. */
    private void disturbDust(ServerPlayer player,long now) {
        UUID uuid=player.getUUID();
        Vec3 current=player.position();
        Vec3 old=lastFeet.put(uuid,current);
        boolean onGround=player.onGround();
        boolean wasGrounded=Boolean.TRUE.equals(lastGrounded.put(uuid,onGround));
        if(old==null)return;
        boolean jumped=player.getDeltaMovement().y>.12 && current.y>old.y+.01;
        // Do not create dust puffs or radioactive clouds merely because a creative
        // flyer/elytra player passes above a contaminated surface. Ground walking,
        // sprinting and a genuine ground-launched jump remain eligible.
        if(!AlphaDustInteraction.canDisturb(player.getAbilities().flying,
                player.isFallFlying(),player.isPassenger(),onGround,jumped&&wasGrounded))return;
        if(current.distanceToSqr(old)<.0016&&!jumped)return;
        long last=lastDisturbance.getOrDefault(uuid,-100L);
        if(ticks-last<7)return;
        ServerLevel level=player.serverLevel();
        BlockPos foot=player.blockPosition();
        BlockPos prior=BlockPos.containing(old.x,old.y,old.z);
        for(BlockPos pos:new BlockPos[]{foot,foot.below(),prior,prior.below()}) {
            if(!AlphaWorld.loaded(level,pos)||!AlphaWorld.isDust(level,pos))continue;
            lastDisturbance.put(uuid,ticks);
            AlphaDustTransport.puff(level,pos,player.isSprinting()?18:9);
            ChunkAlphaData.Deposit deposit=AlphaWorld.deposit(level,pos);
            if(deposit==null||deposit.material().isEmpty())break;
            double fraction=player.isSprinting()?.04:(jumped?.05:.01);
            IsotopeInventory existing=deposit.material().at(now);
            IsotopeInventory.Split split=existing.split(fraction);
            AlphaWorld.setDeposit(level,pos,new ChunkAlphaData.Deposit(deposit.carrier(),
                    deposit.blockId(),split.remaining()));
            AlphaDustTransport.syncDust(level,pos,split.remaining());
            BlockPos cloudPos=pos.above();
            AlphaWorld.addCloud(level,cloudPos,split.moved(),now);
            scheduleCloud(level,cloudPos);
            inhale(player,level,cloudPos,now,.01); // immediate inhalation at disturbance
            break;
        }
    }
    public void sampleImmediate(ServerLevel level,BlockPos pos,long now) {
        for(ServerPlayer player:level.players())
            if(player.isAlive()&&!player.isSpectator())inhale(player,level,pos,now,.01);
    }
    private static boolean closeEnough(ServerPlayer player,BlockPos pos) {
        double dx=player.getX()-pos.getX()-.5;
        double dy=player.getEyeY()-pos.getY()-.5;
        double dz=player.getZ()-pos.getZ()-.5;
        return dx*dx+dy*dy+dz*dz<=6.25;
    }
    private static void inhale(ServerPlayer player,ServerLevel level,BlockPos pos,long now,double fraction) {
        IsotopeInventory cloud=AlphaWorld.cloud(level,pos);
        if(cloud==null||cloud.isEmpty()||!closeEnough(player,pos))return;
        IsotopeInventory.Split split=cloud.at(now).split(fraction);
        if(split.moved().isEmpty())return;
        AlphaWorld.cloud(level,pos,split.remaining(),now);
        PlayerAlphaAttachment attachment=player.getData(AlphaAttachments.PLAYER.get());
        attachment.state().inhale(split.moved(),now);
        player.setData(AlphaAttachments.PLAYER.get(),attachment);
    }
    private void tickClouds(MinecraftServer server,long now) {
        int budget=CLOUD_JOBS_PER_SECOND;
        for(ServerLevel level:server.getAllLevels()) {
            Set<BlockPos> clouds=activeClouds.get(level);
            if(clouds==null||clouds.isEmpty())continue;
            Iterator<BlockPos> it=clouds.iterator();
            // Fairness: rotate processed positions to the end after this scan.
            java.util.List<BlockPos> rotate=new java.util.ArrayList<>();
            while(it.hasNext()&&budget>0) {
                BlockPos pos=it.next();it.remove();budget--;
                if(!AlphaWorld.loaded(level,pos))continue;
                IsotopeInventory stored=AlphaWorld.cloud(level,pos);
                if(stored==null||stored.isEmpty())continue;
                // Apply radioactive decay without simulating physical spreading offline.
                IsotopeInventory current=stored.at(now);
                for(ServerPlayer player:level.players()) {
                    if(!player.isAlive()||player.isSpectator())continue;
                    if(!closeEnough(player,pos))continue;
                    IsotopeInventory.Split breathed=current.split(CLOUD_INHALED_FRACTION);
                    current=breathed.remaining();
                    if(!breathed.moved().isEmpty()) {
                        PlayerAlphaAttachment state=player.getData(AlphaAttachments.PLAYER.get());
                        state.state().inhale(breathed.moved(),now);
                        player.setData(AlphaAttachments.PLAYER.get(),state);
                    }
                }
                IsotopeInventory.Split settling=current.split(CLOUD_SETTLE_FRACTION);
                IsotopeInventory notDeposited=AlphaDustTransport.settle(level,pos,settling.moved(),now);
                current=settling.remaining().plus(notDeposited,now);
                AlphaWorld.cloud(level,pos,current,now);
                if(!current.isEmpty()) {
                    rotate.add(pos);
                    level.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                            pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,
                            7,.35,.25,.35,.003);
                }
            }
            clouds.addAll(rotate);
            if(budget==0)break;
        }
    }
}
