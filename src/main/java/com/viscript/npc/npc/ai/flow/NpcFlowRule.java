package com.viscript.npc.npc.ai.flow;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public record NpcFlowRule(UUID id, String trigger, CompoundTag options, NpcFlowNode root) {
    public NpcFlowRule {
        id = id == null ? UUID.randomUUID() : id;
        trigger = trigger == null ? "tick" : trigger;
        options = options == null ? new CompoundTag() : options.copy();
        root = root == null ? new NpcFlowNode(null, "end", null, null) : root;
    }

    public static NpcFlowRule read(CompoundTag tag) {
        return new NpcFlowRule(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(),
                tag.getString("trigger"), tag.getCompound("options"),
                NpcFlowNode.read(tag.getCompound("root")));
    }
}
