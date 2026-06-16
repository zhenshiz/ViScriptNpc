package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.viscript.npc.ViScriptNpcRegistries;

import java.util.Optional;
import java.util.function.Supplier;

public final class NpcBehaviorNodeHandlers {
    private NpcBehaviorNodeHandlers() {
    }

    public static Optional<NpcBehaviorNodeHandler> forNode(Node node) {
        if (node == null) {
            return Optional.empty();
        }
        for (AutoRegistry.Holder<LDLRegister, NpcBehaviorNodeHandler, Supplier<NpcBehaviorNodeHandler>> holder
                : ViScriptNpcRegistries.NPC_BEHAVIOR_NODE_HANDLERS) {
            NpcBehaviorNodeHandler handler = holder.value().get();
            if (handler.matches(node)) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }

    public static Optional<NpcBehaviorNodeHandler> forType(String nodeType) {
        if (nodeType == null || nodeType.isBlank()) {
            return Optional.empty();
        }
        for (AutoRegistry.Holder<LDLRegister, NpcBehaviorNodeHandler, Supplier<NpcBehaviorNodeHandler>> holder
                : ViScriptNpcRegistries.NPC_BEHAVIOR_NODE_HANDLERS) {
            NpcBehaviorNodeHandler handler = holder.value().get();
            if (nodeType.equals(handler.nodeType())) {
                return Optional.of(handler);
            }
        }
        return Optional.empty();
    }
}
