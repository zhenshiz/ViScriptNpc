package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import lombok.Data;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class NpcBehaviorProgramNode implements IPersistedSerializable {
    @Persisted
    private UUID uid = NpcBehaviorProgram.EMPTY_UUID;
    @Persisted
    private String type = "";
    @Persisted
    private String displayName = "";
    @Persisted
    private String comment = "";
    @Persisted
    private boolean disabled;
    @Persisted
    private boolean breakpoint;
    @Persisted
    private String abortType = "";
    @Persisted
    private int taskIndex = -1;
    @Persisted
    private List<UUID> children = new ArrayList<>();
    @Persisted
    private CompoundTag options = new CompoundTag();

    public NpcBehaviorProgramNode() {
    }

    public NpcBehaviorProgramNode(UUID uid, String type) {
        this.uid = uid == null ? NpcBehaviorProgram.EMPTY_UUID : uid;
        this.type = type == null ? "" : type;
    }

    public CompoundTag getOptions() {
        return options == null ? new CompoundTag() : options;
    }

    public void setOptions(CompoundTag options) {
        this.options = options == null ? new CompoundTag() : options.copy();
    }
}
