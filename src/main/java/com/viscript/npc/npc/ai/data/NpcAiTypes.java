package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/** Shared LDLib2 data registrations used by the NPC AI graph on both logical sides. */
public final class NpcAiTypes {
    private static boolean registered;

    private NpcAiTypes() {}

    /** Registers position and entity-reference persistence/network accessors once per game instance. */
    public static synchronized void register() {
        if (registered) return;
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(Vec3.class)
                .codec(Vec3.CODEC)
                .streamCodec(StreamCodec.of(
                        (RegistryFriendlyByteBuf buffer, Vec3 value) -> {
                            buffer.writeDouble(value.x);
                            buffer.writeDouble(value.y);
                            buffer.writeDouble(value.z);
                        },
                        (RegistryFriendlyByteBuf buffer) -> new Vec3(
                                buffer.readDouble(), buffer.readDouble(), buffer.readDouble())))
                .copyMark(value -> new Vec3(value.x, value.y, value.z))
                .build(), 1000);
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(AiEntityRef.class)
                .codec(com.lowdragmc.lowdraglib2.utils.PersistedParser.createCodec(AiEntityRef::new))
                .streamCodec(com.lowdragmc.lowdraglib2.utils.PersistedParser.createStreamCodec(AiEntityRef::new))
                .copyMark(AiEntityRef::copy).build(), 1000);
        registered = true;
    }
}
