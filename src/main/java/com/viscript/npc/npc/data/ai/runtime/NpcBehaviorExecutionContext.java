package com.viscript.npc.npc.data.ai.runtime;

import com.viscript.npc.npc.CustomNpc;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public record NpcBehaviorExecutionContext(
        CustomNpc npc,
        @Nullable LivingEntity editorDebugTarget,
        int tickRate,
        Consumer<String> failureSink
) {
    public void fail(String reason) {
        failureSink.accept(reason == null ? "" : reason);
    }

    public int optionInt(NpcBehaviorProgramNode node, String id, int fallback) {
        CompoundTag options = node.getOptions();
        return options.contains(id) ? options.getInt(id) : fallback;
    }

    public float optionFloat(NpcBehaviorProgramNode node, String id, float fallback) {
        CompoundTag options = node.getOptions();
        return options.contains(id) ? options.getFloat(id) : fallback;
    }

    public boolean optionBool(NpcBehaviorProgramNode node, String id, boolean fallback) {
        CompoundTag options = node.getOptions();
        return options.contains(id) ? options.getBoolean(id) : fallback;
    }
}
