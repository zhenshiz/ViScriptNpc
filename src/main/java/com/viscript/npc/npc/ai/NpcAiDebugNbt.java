package com.viscript.npc.npc.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.thexeler.api.debug.IntentionDebugEntry;
import org.thexeler.api.debug.MindMachineDebugSnapshot;
import org.thexeler.api.persistence.DiagnosticNbtCodec;
import org.thexeler.api.persistence.StateValueCodec;

public final class NpcAiDebugNbt {
    private NpcAiDebugNbt() {
    }

    public static CompoundTag encode(MindMachineDebugSnapshot snapshot) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("actor", snapshot.actorId());
        tag.putBoolean("paused", snapshot.paused());
        tag.putInt("tick_count", snapshot.tickCount());
        snapshot.active().ifPresent(value -> tag.put("active", entry(value)));
        ListTag queued = new ListTag();
        snapshot.queued().forEach(value -> queued.add(entry(value)));
        tag.put("queued", queued);
        ListTag transitions = new ListTag();
        snapshot.recentTransitions().forEach(value -> {
            CompoundTag transition = new CompoundTag();
            transition.putUUID("handle", value.handleId());
            transition.putString("from", value.from().name());
            transition.putString("to", value.to().name());
            transition.putInt("tick_count", value.tickCount());
            value.diagnostic().ifPresent(diagnostic ->
                    transition.put("diagnostic", DiagnosticNbtCodec.encode(diagnostic)));
            transitions.add(transition);
        });
        tag.put("transitions", transitions);
        ListTag diagnostics = new ListTag();
        snapshot.diagnostics().forEach(value -> diagnostics.add(DiagnosticNbtCodec.encode(value)));
        tag.put("diagnostics", diagnostics);
        return tag;
    }

    private static CompoundTag entry(IntentionDebugEntry value) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("handle", value.handleId());
        tag.putString("type", value.type().toString());
        tag.putString("priority", value.priority().name());
        tag.putString("status", value.status().name());
        CompoundTag parameters = new CompoundTag();
        value.parameters().forEach((name, parameter) -> {
            try { parameters.put(name, StateValueCodec.encode(parameter)); }
            catch (RuntimeException ignored) { parameters.putString(name, String.valueOf(parameter)); }
        });
        tag.put("parameters", parameters);
        ListTag history = new ListTag();
        value.statusHistory().forEach(status -> history.add(StringTag.valueOf(status.name())));
        tag.put("status_history", history);
        value.failure().ifPresent(failure -> tag.put("failure", DiagnosticNbtCodec.encode(failure)));
        value.assetGeneration().ifPresent(generation -> tag.putUUID("asset_generation", generation));
        value.resolvedHash().ifPresent(hash -> tag.putString("resolved_hash", hash));
        ListTag callStack = new ListTag();
        value.compositeCallStack().forEach(id -> callStack.add(StringTag.valueOf(id.toString())));
        tag.put("call_stack", callStack);
        value.currentNode().ifPresent(node -> tag.putString("current_node", node));
        value.currentGuard().ifPresent(guard -> tag.putString("current_guard", guard));
        if (value.timeoutTicksRemaining().isPresent()) {
            tag.putLong("timeout_ticks_remaining", value.timeoutTicksRemaining().getAsLong());
        }
        CompoundTag retries = new CompoundTag();
        value.retryCounts().forEach(retries::putInt);
        tag.put("retry_counts", retries);
        value.currentChild().ifPresent(child -> tag.putString("current_child", child.toString()));
        return tag;
    }
}
