package com.nucleocraft.radiation.server;

import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.core.AlphaDustInteraction;
import com.nucleocraft.radiation.world.AlphaDustRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Explicit separation: dust breaks release material, absorbing blocks carry
 * material on their drops, synthetic sources never manufacture isotope dust.
 */
public final class AlphaBlockEvents {
    private final AlphaSimulation simulation;
    public AlphaBlockEvents(AlphaSimulation simulation){this.simulation=simulation;}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level))return;
        BlockPos pos=event.getPos();
        // The mod places/adjusts physical dust itself: never erase its record.
        if(event.getPlacedBlock().is(AlphaDustRegistry.DUST.get()))return;
        AlphaWorld.setDeposit(level,pos,null);
        AlphaWorld.synthetic(level,pos,0);
        if(!(event.getEntity() instanceof Player player)||player.isCreative())return;
        ItemStack stack=player.getMainHandItem();
        if(!(stack.getItem() instanceof BlockItem block)||block.getBlock()!=event.getPlacedBlock().getBlock()) {
            ItemStack off=player.getOffhandItem();
            if(!(off.getItem() instanceof BlockItem other)||other.getBlock()!=event.getPlacedBlock().getBlock())return;
            stack=off;
        }
        IsotopeInventory material=AlphaItemStorage.material(stack);
        if(material!=null&&!material.isEmpty()) {
            AlphaWorld.setDeposit(level,pos,new ChunkAlphaData.Deposit(
                    ChunkAlphaData.Carrier.ABSORBED,AlphaWorld.blockId(level,pos),material));
        }
        double synthetic=AlphaItemStorage.syntheticAlpha(stack);
        if(synthetic>0)AlphaWorld.synthetic(level,pos,synthetic);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if(event.isCanceled()||!(event.getLevel() instanceof ServerLevel level))return;
        BlockPos pos=event.getPos();
        if(event.getState().is(AlphaDustRegistry.DUST.get())) {
            // A final one-layer deposit is persistent: striking it never moves or
            // duplicates isotope matter. Leave the block AND its chunk record intact.
            // An operator may still deliberately remove it with /radiation clear looking.
            if(AlphaDustInteraction.retainsOnBreak(event.getState().getValue(SnowLayerBlock.LAYERS))) {
                event.setCanceled(true);
                AlphaDustTransport.puff(level,pos,7); // visual feedback only; no airborne isotope
                return;
            }
            ChunkAlphaData.Deposit record=AlphaWorld.takeDeposit(level,pos);
            if(record!=null)simulation.scheduleRelease(level,pos,record.material());
            else AlphaDustTransport.puff(level,pos,14);
            return;
        }
        if(event.getPlayer().isCreative()) {
            ChunkAlphaData.Deposit record=AlphaWorld.takeDeposit(level,pos);
            if(record!=null&&record.carrier()==ChunkAlphaData.Carrier.SURFACE_RESIDUE)
                simulation.scheduleRelease(level,pos.above(),record.material());
            AlphaWorld.synthetic(level,pos,0);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public void onDrops(BlockDropsEvent event) {
        if(event.isCanceled())return;
        ServerLevel level=event.getLevel();
        BlockPos pos=event.getPos();
        if(event.getState().is(AlphaDustRegistry.DUST.get())) {
            event.getDrops().clear();
            // Normally the BreakEvent already moved it; this also covers nonplayer breaks.
            ChunkAlphaData chunk=AlphaWorld.readChunk(level,pos);
            ChunkAlphaData.Deposit leftover=chunk==null?null:chunk.deposit(pos);
            if(leftover!=null) {
                AlphaWorld.setDeposit(level,pos,null);
                simulation.scheduleRelease(level,pos,leftover.material());
            }
            return;
        }
        // The state may already be air here, so read the raw chunk attachment,
        // then validate against the *broken* block state, not the new state.
        ChunkAlphaData chunk=AlphaWorld.readChunk(level,pos);
        ChunkAlphaData.Deposit record=chunk==null?null:chunk.deposit(pos);
        if(record!=null) {
            String broken=net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getKey(event.getState().getBlock()).toString();
            if(!broken.equals(record.blockId()))record=null;
        }
        if(record!=null) {
            AlphaWorld.setDeposit(level,pos,null);
            if(record.carrier()==ChunkAlphaData.Carrier.SURFACE_RESIDUE) {
                simulation.scheduleRelease(level,pos.above(),record.material());
            } else if(!event.getDrops().isEmpty()) {
                ItemEntity chosen=event.getDrops().get(0);
                for(ItemEntity drop:event.getDrops())if(drop.getItem().getItem()==event.getState().getBlock().asItem()) {
                    chosen=drop;break;
                }
                ItemStack stack=chosen.getItem();
                if(stack.getCount()>1) {
                    ItemStack single=stack.copyWithCount(1);stack.shrink(1);
                    ItemEntity separate=new ItemEntity(level,chosen.getX(),chosen.getY(),chosen.getZ(),single);
                    event.getDrops().add(separate);chosen=separate;stack=single;
                }
                IsotopeInventory original=AlphaItemStorage.material(stack);
                AlphaItemStorage.material(stack,original==null?record.material()
                        :original.plus(record.material(),System.currentTimeMillis()));
            } else {
                // No drop: remain physically present as an airborne/settling cloud.
                simulation.scheduleRelease(level,pos.above(),record.material());
            }
        }
        AlphaWorld.synthetic(level,pos,0);
    }
}
