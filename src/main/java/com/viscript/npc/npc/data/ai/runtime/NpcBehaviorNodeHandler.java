package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.registry.ILDLRegister;
import net.minecraft.nbt.CompoundTag;

import java.util.function.Supplier;

public interface NpcBehaviorNodeHandler extends ILDLRegister<NpcBehaviorNodeHandler, Supplier<NpcBehaviorNodeHandler>> {
    String ID = "viscript_npc:npc_behavior_node_handler";

    String nodeType();

    boolean matches(Node node);

    default void compileOptions(Node node, CompoundTag options) {
    }

    default NpcBehaviorRuntime.Status evaluate(NpcBehaviorExecutionContext context, NpcBehaviorProgramNode node) {
        context.fail("unsupported_node_handler:" + nodeType());
        return NpcBehaviorRuntime.Status.FAILURE;
    }
}
