package com.viscript.npc.npc.ai.flow;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;

public record NpcFlowDefinition(int version, List<NpcFlowRule> rules) {
    public NpcFlowDefinition {
        version = Math.max(1, version);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static NpcFlowDefinition read(CompoundTag tag) {
        List<NpcFlowRule> rules = new ArrayList<>();
        if (tag != null && tag.contains("rules", 9)) {
            var list = tag.getList("rules", 10);
            for (int index = 0; index < list.size(); index++) rules.add(NpcFlowRule.read(list.getCompound(index)));
        }
        return new NpcFlowDefinition(tag == null ? 1 : tag.getInt("version"), rules);
    }
}
