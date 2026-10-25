package com.viscript.npc.npc.ai.data;

import com.viscript_lib.annotation.ViScriptRegisterAccessors;
import com.viscript_lib.event.RegisterAccessorEvent;

/** 在 RPC 和持久化类型解析前注册 AI 数据载体。 */
public final class AiAccessors {
    @ViScriptRegisterAccessors
    public static void register(RegisterAccessorEvent event) {
        com.lowdragmc.lowdraglib2.syncdata.AccessorRegistries.registerAccessor(
                com.lowdragmc.lowdraglib2.syncdata.accessor.direct.CustomDirectAccessor.builder(net.minecraft.world.phys.Vec3.class)
                        .codec(net.minecraft.world.phys.Vec3.CODEC)
                        .streamCodec(net.minecraft.network.codec.ByteBufCodecs.fromCodec(net.minecraft.world.phys.Vec3.CODEC))
                        .codecMark().build(), 0);
        event.register(AiEntityRef.class, AiEntityRef::new);
        event.register(AiValue.class, AiValue::new);
        event.register(AiVariable.class, AiVariable::new);
        event.register(AiBinding.class, AiBinding::new);
        event.register(AiNodeData.class, AiNodeData::new);
        event.register(AiProgram.class, AiProgram::new);
        event.register(AiPerceptionVolume.class, AiPerceptionVolume::new);
        event.register(AiDebugSnapshot.class, AiDebugSnapshot::new);
        event.register(AiDebugEvent.class, AiDebugEvent::new);
        event.register(AiDebugLine.class, AiDebugLine::new);
    }
}
