package com.nucleocraft.radiation.commands.beta;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.nucleocraft.radiation.core.IsotopeDefinitions;
import com.nucleocraft.radiation.player.alpha.PlayerAlphaState;
import com.nucleocraft.radiation.server.beta.BetaSimulation;
import com.nucleocraft.radiation.server.beta.BetaWorld;
import com.nucleocraft.radiation.server.data.RadiationAttachments;
import com.nucleocraft.radiation.radiation.beta.BetaBlockLabel;
import com.nucleocraft.radiation.radiation.profiles.beta.BetaRadiationProfile;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.Locale;

/** Beta-only diagnostics. Physical deposition remains /radiation contaminate looking <isotope> <units>. */
public final class BetaCommands {
    private final BetaSimulation simulation;
    public BetaCommands(BetaSimulation simulation){this.simulation=simulation;}
    @SubscribeEvent public void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("radiation")
            .then(Commands.literal("give").requires(s->s.hasPermission(2))
                .then(Commands.literal("looking").then(Commands.literal("beta")
                    .then(Commands.argument("rate",DoubleArgumentType.doubleArg(.000001,100000))
                        .executes(c->source(c.getSource().getPlayerOrException(),DoubleArgumentType.getDouble(c,"rate")))))))
            .then(Commands.literal("debug").requires(s->s.hasPermission(2))
                .then(Commands.literal("beta").executes(c->debug(c.getSource().getPlayerOrException()))))
            .then(Commands.literal("beta")
                .then(Commands.literal("status").executes(c->status(c.getSource().getPlayerOrException())))));
    }
    private int source(ServerPlayer p,double rate){
        if(!(p.pick(6,1f,false) instanceof BlockHitResult hit)){
            p.sendSystemMessage(Component.literal("Look at a block within six blocks."));return 0;
        }
        var pos=hit.getBlockPos();BetaWorld.synthetic(p.serverLevel(),pos,BetaWorld.synthetic(p.serverLevel(),pos)+rate);
        p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
            "Synthetic Beta +%.3f RAD/s at %s (no physical isotopes created)",rate,pos)));return 1;
    }
    private int status(ServerPlayer p){
        long now=System.currentTimeMillis();
        PlayerAlphaState state=p.getData(RadiationAttachments.PLAYER.get()).state();
        state.advance(now);
        var profile=new BetaRadiationProfile();
        double internal=profile.internalRate(state.inhaled().units(),BetaWorld.ISOTOPES)
            +profile.internalRate(state.ingested().units(),BetaWorld.ISOTOPES);
        p.sendSystemMessage(Component.literal(String.format(Locale.ROOT,
            "Beta: internal %.5f RAD/s | combined accumulated dose %.3f RAD | simulation edges %d",
            internal,state.accumulatedRad(),simulation.processedEdges())));return 1;
    }
    private int debug(ServerPlayer p){
        long now=System.currentTimeMillis();
        p.sendSystemMessage(Component.literal("Beta engine active: 0.75%/s per donor, 90% neighbour cap, 6 faces; physical transfers conserve units."));
        if(p.pick(6,1f,false) instanceof BlockHitResult hit){
            var pos=hit.getBlockPos();
            var cell=BetaWorld.at(p.serverLevel(),pos,now);
            var label=BetaBlockLabel.fromCell(BetaWorld.key(pos),cell,BetaWorld.ISOTOPES);
            p.sendSystemMessage(Component.literal(label.map(x->x.header()+" | "+x.material()).orElse("No physical Beta contamination at target")+
                String.format(Locale.ROOT," | synthetic %.3f RAD/s",BetaWorld.synthetic(p.serverLevel(),pos))));
        }
        return 1;
    }
}
