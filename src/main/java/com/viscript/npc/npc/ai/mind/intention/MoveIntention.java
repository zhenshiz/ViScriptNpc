package com.viscript.npc.npc.ai.mind.intention;

import com.viscript.npc.npc.ai.mind.api.IntentionPriority;
import com.viscript.npc.npc.ai.mind.api.IntentionType;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

public class MoveIntention extends SimpleIntention {
    @Getter
    protected final Vec3 pos;

    public MoveIntention(Entity origin, Vec3 pos) {
        super(origin, IntentionType.MOVE);
        this.pos = pos;
    }

    public MoveIntention(Entity origin, Vec3 pos, IntentionPriority priority) {
        super(origin, IntentionType.MOVE, priority);
        this.pos = pos;
    }

    @Override
    public boolean execute() {
        if (this.origin.position().equals(this.pos)) {
            return true;
        } else {
            this.origin.move(MoverType.SELF, this.pos.subtract(this.origin.position()));
            return false;
        }
    }
}
