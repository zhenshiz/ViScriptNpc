package com.viscript.npc.npc.ai.mind.intention;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import com.viscript.npc.npc.ai.mind.api.IntentionPriority;
import com.viscript.npc.npc.ai.mind.api.IntentionType;

public class FollowIntention extends SimpleIntention {
    @Getter
    protected Entity target;
    @Getter
    @Setter
    protected double distance;
    @Getter
    @Setter
    protected boolean forceDistance;

    public FollowIntention(Entity origin, Entity target) {
        super(origin, IntentionType.FOLLOW);
        this.target = target;
        this.distance = 0.1F;
        this.forceDistance = false;
    }

    public FollowIntention(Entity origin, Entity target, IntentionPriority priority) {
        super(origin, IntentionType.FOLLOW, priority);
        this.target = target;
        this.distance = 0.1F;
        this.forceDistance = false;
    }

    public FollowIntention(Entity origin, Entity target, double distance) {
        super(origin, IntentionType.FOLLOW);
        this.target = target;
        this.distance = distance;
        this.forceDistance = false;
    }

    public FollowIntention(Entity origin, Entity target, double distance, IntentionPriority priority) {
        super(origin, IntentionType.FOLLOW, priority);
        this.target = target;
        this.distance = distance;
        this.forceDistance = false;
    }

    public FollowIntention(Entity origin, Entity target, double distance, boolean forcedDistance) {
        super(origin, IntentionType.FOLLOW);
        this.target = target;
        this.distance = distance;
        this.forceDistance = forcedDistance;
    }

    public FollowIntention(Entity origin, Entity target, double distance, boolean forcedDistance, IntentionPriority priority) {
        super(origin, IntentionType.FOLLOW, priority);
        this.target = target;
        this.distance = distance;
        this.forceDistance = forcedDistance;
    }

    @Override
    public boolean execute() {
        Vec3 originPos = this.origin.position();
        Vec3 targetPos = this.target.position();

        double dx = targetPos.x - originPos.x,
                dy = targetPos.y - originPos.y,
                dz = targetPos.z - originPos.z;

        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (this.distance != distance) {
            if (forceDistance || this.distance < distance) {
                this.origin.move(MoverType.SELF, new Vec3(
                        originPos.x + (dx * distance),
                        originPos.y + (dy * distance),
                        originPos.z + (dz * distance)));
            }
        }

        return false;
    }
}
