package com.viscript.npc.npc.data.ai;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum NpcAiSource implements StringRepresentable {
    EMBEDDED("embedded"),
    TEMPLATE("template");

    private final String serializedName;

    NpcAiSource(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }
}
