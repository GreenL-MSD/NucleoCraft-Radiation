package com.nucleocraft.radiation.world;

import com.nucleocraft.radiation.NucleoCraftRadiation;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Visual-only, no ticking BlockEntity, and no obtainable dust BlockItem. */
public final class AlphaDustRegistry {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(NucleoCraftRadiation.MOD_ID);
    public static final DeferredBlock<SnowLayerBlock> DUST=BLOCKS.registerBlock(
            "radioactive_dust",SnowLayerBlock::new,
            BlockBehaviour.Properties.of().strength(.10f).sound(SoundType.SAND)
                    .noCollission().replaceable());
    private AlphaDustRegistry() {}
    public static void register(IEventBus bus){BLOCKS.register(bus);}
}
