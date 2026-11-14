package com.nucleocraft.radiation.server;

import com.nucleocraft.radiation.NucleoCraftRadiation;
import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class AlphaAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,NucleoCraftRadiation.MOD_ID);
    public static final Supplier<AttachmentType<ChunkAlphaData>> CHUNK=TYPES.register(
            "alpha_chunk_v1",()->AttachmentType.serializable(ChunkAlphaData::new).build());
    public static final Supplier<AttachmentType<PlayerAlphaAttachment>> PLAYER=TYPES.register(
            "alpha_player_v1",()->AttachmentType.serializable(PlayerAlphaAttachment::new).build());
    private AlphaAttachments() {}
    public static void register(IEventBus bus){TYPES.register(bus);}
}
