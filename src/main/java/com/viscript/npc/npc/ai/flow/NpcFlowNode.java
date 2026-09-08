package com.viscript.npc.npc.ai.flow;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record NpcFlowNode(UUID id, String type, CompoundTag options, List<NpcFlowNode> children) {
    public NpcFlowNode {
        id = id == null ? UUID.randomUUID() : id;
        type = type == null ? "end" : type;
        options = options == null ? new CompoundTag() : options.copy();
        children = children == null ? List.of() : List.copyOf(children);
    }

    public static NpcFlowNode read(CompoundTag tag) {
        UUID id = tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID();
        List<NpcFlowNode> children = new ArrayList<>();
        if (tag.contains("children", 9)) {
            var list = tag.getList("children", 10);
            for (int index = 0; index < list.size(); index++) children.add(read(list.getCompound(index)));
        }
        return new NpcFlowNode(id, tag.getString("type"), tag.getCompound("options"), children);
    }
}
