package com.nucleocraft.radiation.commands.alpha;

import com.nucleocraft.radiation.server.alpha.AlphaDustTransport;
import com.nucleocraft.radiation.server.alpha.AlphaItemStorage;
import com.nucleocraft.radiation.server.alpha.AlphaSimulation;
import com.nucleocraft.radiation.server.alpha.AlphaWorld;
import com.nucleocraft.radiation.server.alpha.PlayerAlphaAttachment;
import com.nucleocraft.radiation.server.data.ChunkRadiationData;
import com.nucleocraft.radiation.server.data.RadiationAttachments;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nucleocraft.radiation.core.IsotopeDefinitions;
import com.nucleocraft.radiation.core.IsotopeInventory;
import com.nucleocraft.radiation.player.alpha.PlayerAlphaState;
import com.nucleocraft.radiation.radiation.profiles.alpha.AlphaRadiationProfile;
import com.nucleocraft.radiation.world.alpha.AlphaDustRegistry;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** All exposed commands are real Alpha-only operations, not placeholders. */
public final class AlphaCommands {
    private final AlphaSimulation simulation;
    public AlphaCommands(AlphaSimulation simulation){this.simulation=simulation;}
    @SubscribeEvent public void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> give=Commands.literal("give").requires(s->s.hasPermission(2));
        for(String kind:new String[]{"hand","looking"})
            give.then(Commands.literal(kind)
                    .then(Commands.literal("alpha")
                            .then(Commands.argument("rate",DoubleArgumentType.doubleArg(.000001,1000000))
                                    .executes(c->give(c.getSource(),kind,DoubleArgumentType.getDouble(c,"rate"))))));
        LiteralArgumentBuilder<CommandSourceStack> contaminate=Commands.literal("contaminate")
                .requires(s->s.hasPermission(2));
        for(String kind:new String[]{"hand","looking"})
            contaminate.then(Commands.literal(kind)
                    .then(Commands.argument("isotope",StringArgumentType.word())
                            .then(Commands.argument("units",DoubleArgumentType.doubleArg(.000001,1000000))
                                    .executes(c->contaminate(c.getSource(),kind,
                                            StringArgumentType.getString(c,"isotope"),
                                            DoubleArgumentType.getDouble(c,"units"))))));
        event.getDispatcher().register(Commands.literal("radiation")
                .then(Commands.literal("status").executes(c->status(c.getSource())))
                .then(Commands.literal("internal").executes(c->internal(c.getSource())))
                .then(Commands.literal("inspect")
                        .then(Commands.literal("looking").executes(c->inspect(c.getSource(),"looking")))
                        .then(Commands.literal("hand").executes(c->inspect(c.getSource(),"hand"))))
                .then(Commands.literal("debug").requires(s->s.hasPermission(2))
                        .then(Commands.literal("alpha")
                                .executes(c->debugAlpha(c.getSource()))
                                .then(Commands.literal("inhale")
                                        .then(Commands.argument("units",DoubleArgumentType.doubleArg(.000001,100000))
                                                .executes(c->intake(c.getSource(),"polonium_210",
                                                        DoubleArgumentType.getDouble(c,"units"),false)))
                                        .then(Commands.argument("isotope",StringArgumentType.word())
                                                .then(Commands.argument("units",DoubleArgumentType.doubleArg(.000001,100000))
                                                        .executes(c->intake(c.getSource(),
                                                                StringArgumentType.getString(c,"isotope"),
                                                                DoubleArgumentType.getDouble(c,"units"),false)))))
                                .then(Commands.literal("ingest")
                                        .then(Commands.argument("isotope",StringArgumentType.word())
                                                .then(Commands.argument("units",DoubleArgumentType.doubleArg(.000001,100000))
                                                        .executes(c->intake(c.getSource(),
                                                                StringArgumentType.getString(c,"isotope"),
                                                                DoubleArgumentType.getDouble(c,"units"),true)))))))
                .then(Commands.literal("dust").requires(s->s.hasPermission(2))
                        .then(Commands.argument("isotope",StringArgumentType.word())
                                .then(Commands.argument("units",DoubleArgumentType.doubleArg(.000001,1000000))
                                        .executes(c->dust(c.getSource(),
                                                StringArgumentType.getString(c,"isotope"),
                                                DoubleArgumentType.getDouble(c,"units"))))))
                .then(contaminate)
                .then(give)
                .then(Commands.literal("clear").requires(s->s.hasPermission(2))
                        .then(Commands.literal("looking").executes(c->clear(c.getSource(),"looking")))
                        .then(Commands.literal("hand").executes(c->clear(c.getSource(),"hand"))))
                .then(Commands.literal("reset").requires(s->s.hasPermission(2))
                        .executes(c->reset(c.getSource())))
                .then(Commands.literal("perf").requires(s->s.hasPermission(2))
                        .executes(c->perf(c.getSource()))));
    }
    private static BlockPos looking(ServerPlayer player) {
        HitResult result=player.pick(6.0,0f,false);
        return result instanceof BlockHitResult hit && result.getType()==HitResult.Type.BLOCK
                ?hit.getBlockPos():null;
    }
    private static void msg(CommandSourceStack source,String value) {
        source.sendSuccess(()->Component.literal(value),false);
    }
    private static void err(CommandSourceStack source,String value){source.sendFailure(Component.literal(value));}
    private static IsotopeInventory physical(CommandSourceStack src,String isotope,double units) {
        IsotopeDefinitions.require(isotope);
        return IsotopeInventory.of(isotope,units,System.currentTimeMillis());
    }
    private static int give(CommandSourceStack src,String kind,double rate) throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        if(kind.equals("hand")) {
            ItemStack stack=player.getMainHandItem();
            if(stack.isEmpty()){err(src,"Hold an item first.");return 0;}
            AlphaItemStorage.syntheticAlpha(stack,AlphaItemStorage.syntheticAlpha(stack)+rate/stack.getCount());
            msg(src,String.format(Locale.ROOT,"Synthetic Alpha source on held stack: +%.3f emitted RAD/s; external absorbed dose = 0.",rate));
        } else {
            BlockPos pos=looking(player);
            if(pos==null){err(src,"Look at a block within six blocks.");return 0;}
            AlphaWorld.synthetic(player.serverLevel(),pos,AlphaWorld.synthetic(player.serverLevel(),pos)+rate);
            msg(src,String.format(Locale.ROOT,"Synthetic Alpha source at %s: +%.3f emitted RAD/s; no isotope matter created.",pos,rate));
        }
        return 1;
    }
    private static int dust(CommandSourceStack src,String isotope,double units)throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        IsotopeInventory amount;
        try{amount=physical(src,isotope,units);}catch(IllegalArgumentException bad){err(src,bad.getMessage());return 0;}
        BlockPos pos=looking(player);
        if(pos==null){err(src,"Look at a solid surface or existing dust within six blocks.");return 0;}
        if(!AlphaDustTransport.direct(player.serverLevel(),pos,amount,System.currentTimeMillis())){
            err(src,"Could not deposit dust. A loaded supporting block and free dust space are required.");return 0;
        }
        msg(src,String.format(Locale.ROOT,"Added %.5f units of [%s] as physical loose dust (no automatic Alpha propagation).",
                units,IsotopeDefinitions.require(isotope).displayName()));
        return 1;
    }
    private static int contaminate(CommandSourceStack src,String kind,String isotope,double units)
            throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        IsotopeInventory amount;
        try{amount=physical(src,isotope,units);}catch(IllegalArgumentException bad){err(src,bad.getMessage());return 0;}
        long now=System.currentTimeMillis();
        if(kind.equals("hand")) {
            ItemStack stack=player.getMainHandItem();
            if(stack.isEmpty()){err(src,"Hold an item first.");return 0;}
            IsotopeInventory prior=AlphaItemStorage.material(stack);
            IsotopeInventory additional=IsotopeInventory.of(isotope,units/stack.getCount(),now);
            AlphaItemStorage.material(stack,prior==null?additional:prior.plus(additional,now));
        } else {
            BlockPos pos=looking(player);
            if(pos==null){err(src,"Look directly at a block.");return 0;}
            var level=player.serverLevel();
            if(AlphaWorld.isDust(level,pos)) {
                if(!AlphaDustTransport.addLoose(level,pos,amount,now)){
                    err(src,"Cannot deposit on that dust layer.");return 0;
                }
            } else if(AlphaDustTransport.absorbs(level.getBlockState(pos))) {
                if(!AlphaDustTransport.addOnBlock(level,pos,ChunkRadiationData.Carrier.ABSORBED,amount,now))return 0;
            } else if(!AlphaDustTransport.addLoose(level,pos.above(),amount,now)
                    && !AlphaDustTransport.addOnBlock(level,pos,ChunkRadiationData.Carrier.SURFACE_RESIDUE,amount,now)) {
                err(src,"Target was not a suitable solid support.");return 0;
            }
        }
        msg(src,String.format(Locale.ROOT,"Added %.5f units of [%s] physical material to %s.",
                units,IsotopeDefinitions.require(isotope).displayName(),kind));
        return 1;
    }
    private static String label(IsotopeInventory inventory,long now) {
        if(inventory==null||inventory.isEmpty())return "none";
        StringBuilder sb=new StringBuilder();
        inventory.at(now).units().forEach((id,units)->{
            if(!sb.isEmpty())sb.append(", ");
            sb.append('[').append(IsotopeDefinitions.require(id).displayName()).append("] ")
                    .append(String.format(Locale.ROOT,"%.6f units",units));
        });
        return sb.toString();
    }
    private static int inspect(CommandSourceStack src,String kind)throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();long now=System.currentTimeMillis();
        if(kind.equals("hand")) {
            ItemStack item=player.getMainHandItem();
            IsotopeInventory m=AlphaItemStorage.material(item);
            double synthetic=AlphaItemStorage.syntheticAlpha(item)*item.getCount();
            msg(src,String.format(Locale.ROOT,"Hand: %s | physical: %s | isotope Alpha %.3f emitted RAD/s | synthetic %.3f emitted RAD/s",
                    item.getHoverName().getString(),label(m,now),
                    m==null?0:m.alphaEmissions(now)*item.getCount(),synthetic));
        } else {
            BlockPos pos=looking(player);
            if(pos==null){err(src,"Look at a block within six blocks.");return 0;}
            var level=player.serverLevel();
            var dep=AlphaWorld.deposit(level,pos);
            if(dep==null&&!AlphaWorld.isDust(level,pos)) {
                var above=AlphaWorld.deposit(level,pos.above());
                if(above!=null&&above.carrier()==ChunkRadiationData.Carrier.LOOSE_DUST)dep=above;
            }
            IsotopeInventory m=dep==null?null:dep.material();
            double synthetic=AlphaWorld.synthetic(level,pos);
            msg(src,String.format(Locale.ROOT,"Looking %s: carrier=%s | isotopes: %s | emitted Alpha %.3f RAD/s | synthetic Alpha %.3f RAD/s | external absorbed Alpha 0",
                    pos,dep==null?"none":dep.carrier(),label(m,now),
                    m==null?0:m.alphaEmissions(now),synthetic));
        }
        return 1;
    }
    private static int status(CommandSourceStack src)throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        PlayerAlphaState s=player.getData(RadiationAttachments.PLAYER.get()).state();
        long now=System.currentTimeMillis();
        msg(src,String.format(Locale.ROOT,"Radiation | accumulated %.3f RAD (Alpha+Beta) | internal Alpha %.5f RAD/s | external Alpha 0 RAD/s",
                s.accumulatedRad(),s.internalDoseRate(now)));
        return 1;
    }
    private static int internal(CommandSourceStack src)throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        PlayerAlphaState s=player.getData(RadiationAttachments.PLAYER.get()).state();long now=System.currentTimeMillis();
        s.advance(now);
        msg(src,"Inhaled: "+label(s.inhaled(),now));
        msg(src,"Ingested: "+label(s.ingested(),now));
        msg(src,String.format(Locale.ROOT,"Internal Alpha emitted %.5f RAD/s | weighted absorbed dose %.5f RAD/s",
                s.internalAlphaEmission(now),s.internalDoseRate(now)));
        return 1;
    }
    private int debugAlpha(CommandSourceStack src)throws CommandSyntaxException {
        ServerPlayer p=src.getPlayerOrException();
        PlayerAlphaState player=p.getData(RadiationAttachments.PLAYER.get()).state();
        msg(src,String.format(Locale.ROOT,"Alpha profile | external absorption %.1f | internal weight %.1f | clouds queued %d | releases queued %d | internal rate %.6f RAD/s",
                AlphaRadiationProfile.INSTANCE.externalAbsorbedRate(100),
                AlphaRadiationProfile.INTERNAL_WEIGHT,simulation.queuedClouds(),simulation.queuedReleases(),
                player.internalDoseRate(System.currentTimeMillis())));
        return 1;
    }
    private static int intake(CommandSourceStack src,String isotope,double units,boolean ingested)
            throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        IsotopeInventory amount;
        try{amount=physical(src,isotope,units);}catch(IllegalArgumentException bad){err(src,bad.getMessage());return 0;}
        var attachment=player.getData(RadiationAttachments.PLAYER.get());
        if(ingested)attachment.state().ingest(amount,System.currentTimeMillis());
        else attachment.state().inhale(amount,System.currentTimeMillis());
        player.setData(RadiationAttachments.PLAYER.get(),attachment);
        msg(src,String.format(Locale.ROOT,"DEBUG: %s %.5f physical units of [%s] into player (no world material created).",
                ingested?"ingested":"inhaled",units,IsotopeDefinitions.require(isotope).displayName()));
        return 1;
    }
    private static int clear(CommandSourceStack src,String kind)throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        if(kind.equals("hand")) {
            AlphaItemStorage.material(player.getMainHandItem(),null);
            AlphaItemStorage.syntheticAlpha(player.getMainHandItem(),0);
        } else {
            BlockPos pos=looking(player);
            if(pos==null){err(src,"Look at a block.");return 0;}
            var level=player.serverLevel();
            AlphaWorld.setDeposit(level,pos,null);
            AlphaWorld.synthetic(level,pos,0);
            if(AlphaWorld.isDust(level,pos))level.removeBlock(pos,false);
        }
        msg(src,"Cleared Alpha test material/source on "+kind+" (admin removal).");return 1;
    }
    private static int reset(CommandSourceStack src)throws CommandSyntaxException {
        ServerPlayer player=src.getPlayerOrException();
        PlayerAlphaAttachment data=player.getData(RadiationAttachments.PLAYER.get());
        data.reset(System.currentTimeMillis());
        player.setData(RadiationAttachments.PLAYER.get(),data);
        msg(src,"Reset combined accumulated RAD and internal isotope contamination for testing.");return 1;
    }
    private int perf(CommandSourceStack src) {
        Runtime vm=Runtime.getRuntime();
        msg(src,String.format(Locale.ROOT,
                "Alpha jobs: %d persistent clouds queued, %d delayed releases | Heap used %.1f / max %.1f MiB (whole JVM)",
                simulation.queuedClouds(),simulation.queuedReleases(),
                (vm.totalMemory()-vm.freeMemory())/1048576.0,vm.maxMemory()/1048576.0));
        return 1;
    }
}
