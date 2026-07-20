package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries;
import com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor;
import com.lowdragmc.lowdraglib2.utils.PersistedParser;

import java.util.function.Supplier;

public final class NpcBehaviorDataSerializers {
    private static boolean registered;

    private NpcBehaviorDataSerializers() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        register(NpcBehaviorProgram.class, NpcBehaviorProgram::new);
        register(NpcBehaviorProgramNode.class, NpcBehaviorProgramNode::new);
        register(NpcBehaviorDebugSnapshot.class, NpcBehaviorDebugSnapshot::new);
        register(NpcBehaviorDebugNodeSnapshot.class, NpcBehaviorDebugNodeSnapshot::new);
        registered = true;
    }

    private static <T> void register(Class<T> type, Supplier<T> factory) {
        AccessorRegistries.registerAccessor(CustomDirectAccessor.builder(type)
                .codec(PersistedParser.createCodec(factory))
                .streamCodec(PersistedParser.createStreamCodec(factory))
                .codecMark()
                .build(), 900);
    }
}
