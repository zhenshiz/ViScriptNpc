package com.viscript.npc.npc.ai.mind.api;

import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public abstract class BaseIntention {
    @Getter
    protected final Entity origin;
    @Getter
    protected IntentionPriority priority;
    @Getter
    protected IntentionType type;

    public BaseIntention(Entity origin, IntentionType type) {
        this.type = type;
        this.origin = origin;
        this.priority = IntentionPriority.NORMAL;
    }

    public BaseIntention(Entity origin, IntentionType type, IntentionPriority priority) {
        this.type = type;
        this.origin = origin;
        this.priority = priority;
    }

    public abstract boolean execute();

    public abstract void hold();

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();

        tag.putString("type", IntentionType.getName(type));
        tag.putString("priority", priority.name());

        return tag;
    }

    public void deserialize(CompoundTag tag) {
        if (!tag.getString("type").isEmpty()) {
            type = IntentionType.getType(tag.getString("type"));
        } else {
            type = IntentionType.NONE;
        }

        if (!tag.getString("priority").isEmpty()) {
            priority = IntentionPriority.valueOf(tag.getString("priority"));
        } else {
            priority = IntentionPriority.NORMAL;
        }
    }

}
