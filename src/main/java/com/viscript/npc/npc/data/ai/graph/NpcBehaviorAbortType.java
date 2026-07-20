package com.viscript.npc.npc.data.ai.graph;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

@Getter
@RequiredArgsConstructor
public enum NpcBehaviorAbortType implements StringRepresentable {
    NONE("viscript_npc.ai.abort_type.none"),
    SELF("viscript_npc.ai.abort_type.self"),
    LOWER_PRIORITY("viscript_npc.ai.abort_type.lower_priority"),
    BOTH("viscript_npc.ai.abort_type.both");

    private final String serializedName;

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }
}
