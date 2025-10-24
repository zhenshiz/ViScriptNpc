package com.viscript.npc.npc.ai.mind.api;

public enum IntentionPriority {
    NEVER,
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    URGENT;

    public boolean biggerThanOrEqual(IntentionPriority priority) {
        return this.ordinal() >= priority.ordinal();
    }

    public boolean biggerThan(IntentionPriority priority) {
        return this.ordinal() > priority.ordinal();
    }

    public boolean smallerThanOrEqual(IntentionPriority priority) {
        return this.ordinal() <= priority.ordinal();
    }

    public boolean smallerThan(IntentionPriority priority) {
        return this.ordinal() < priority.ordinal();
    }

    public boolean equal(IntentionPriority priority) {
        return this.ordinal() == priority.ordinal();
    }


}
