package com.viscript.npc.npc.ai.mind;


import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.world.entity.Entity;
import com.viscript.npc.npc.ai.mind.api.BaseIntention;
import com.viscript.npc.npc.ai.mind.api.IntentionPriority;
import com.viscript.npc.npc.ai.mind.api.IntentionType;
import com.viscript.npc.npc.ai.mind.intention.SimpleIntention;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

@Slf4j
public class MindMachine {
    @Getter
    private final Entity origin;
    @Getter
    private final int stepTick;
    @Getter
    @Setter
    private int tickCount;

    private final List<BaseIntention> intentions;

    public MindMachine(Entity origin) {
        this.origin = origin;

        this.stepTick = 5;
        this.tickCount = 0;
        this.intentions = Collections.synchronizedList(new LinkedList<>());

        intentions.add(new SimpleIntention(origin, IntentionType.IDLE, IntentionPriority.LOWEST) {
            @Override
            public boolean execute() {
                return false;
            }
        });
    }


    public void addIntention(BaseIntention baseIntention) {
        if (baseIntention.getPriority() == IntentionPriority.URGENT) {
            this.intentions.add(0, baseIntention);
        } else {
            ListIterator<BaseIntention> i = this.intentions.listIterator();

            while (i.hasNext()) {
                BaseIntention current = i.next();
                if (!baseIntention.getPriority().biggerThan(current.getPriority())) {
                    i.previous();
                    i.add(baseIntention);
                    return;
                }
            }

            this.intentions.add(baseIntention);
        }
    }

    public void tick() {
        tickCount++;
        if (tickCount >= stepTick) {
            tickCount -= stepTick;
            step();
        }
    }


    private void step() {
        BaseIntention baseIntention = intentions.get(0);

        if (baseIntention != null) {
            if (baseIntention.getType() != IntentionType.IDLE) {
                if (baseIntention.execute()) {
                    if (baseIntention.getType() != IntentionType.IDLE) {
                        intentions.remove(0);
                    }
                }
                intentions.forEach(i -> {
                    if (i != baseIntention) {
                        i.hold();
                    }
                });
                intentions.sort((i1, i2) -> i2.getPriority().compareTo(i1.getPriority()));
            }
        }
    }

    public boolean interrupt() {
        if (intentions.size() > 1) {
            intentions.remove(0);
            return true;
        }
        return false;
    }
}
