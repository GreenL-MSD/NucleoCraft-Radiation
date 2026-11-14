package com.nucleocraft.radiation.commands.beta;

import com.nucleocraft.radiation.server.debug.RadiationLabelServer;
import com.nucleocraft.radiation.server.debug.RadiationLabelServer.Mode;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BetaLabelCommands {
    private final RadiationLabelServer labels;
    public BetaLabelCommands(RadiationLabelServer labels){this.labels=labels;}
    @SubscribeEvent public void register(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("radiation").requires(s->s.hasPermission(2))
            .then(Commands.literal("debug").then(Commands.literal("beta")
                .then(Commands.literal("labels")
                    .then(Commands.literal("off").executes(c->change(c.getSource().getPlayerOrException(),Mode.OFF,8)))
                    .then(Commands.literal("look").executes(c->change(c.getSource().getPlayerOrException(),Mode.LOOK,12)))
                    .then(Commands.literal("nearby")
                        .executes(c->change(c.getSource().getPlayerOrException(),Mode.NEARBY,8))
                        .then(Commands.argument("radius",IntegerArgumentType.integer(1,12))
                            .executes(c->change(c.getSource().getPlayerOrException(),Mode.NEARBY,
                                IntegerArgumentType.getInteger(c,"radius")))))))));
    }
    private int change(ServerPlayer player,Mode mode,int radius){
        labels.configure(player,"beta",mode,radius);
        player.sendSystemMessage(Component.literal("Beta labels: "+mode.toString().toLowerCase()));return 1;
    }
}
