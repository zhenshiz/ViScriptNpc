package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import lombok.Data;
import net.minecraft.nbt.CompoundTag;

@Data
public class NpcBehaviorDebugNodeSnapshot implements IPersistedSerializable {
    @Persisted
    private String nodeId = "";
    @Persisted
    private String type = "";
    @Persisted
    private String displayName = "";
    @Persisted
    private String status = "SKIPPED";
    @Persisted
    private int taskIndex = -1;
    @Persisted
    private int visitIndex = -1;
    @Persisted
    private boolean disabled;
    @Persisted
    private boolean breakpoint;
    @Persisted
    private boolean breakpointHit;
    @Persisted
    private String abortType = "";
    @Persisted
    private long lastTick = -1L;
    @Persisted
    private long durationNanos;
    @Persisted
    private String failureReason = "";
    @Persisted
    private double targetDistance = -1.0D;
    @Persisted
    private boolean navigationDone = true;
    @Persisted
    private int cooldownRemaining;
    @Persisted
    private CompoundTag options = new CompoundTag();

    public CompoundTag getOptions() {
        return options == null ? new CompoundTag() : options;
    }

    public void setOptions(CompoundTag options) {
        this.options = options == null ? new CompoundTag() : options.copy();
    }
}
