package com.viscript.npc.gui.edit.view;

import com.viscript.npc.npc.CustomNpc;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public class NpcAttributesPageState {
    private final List<CustomNpc> selectedReferences = new ArrayList<>();
    private final List<Runnable> changeListeners = new ArrayList<>();

    public void toggleReference(CustomNpc npc) {
        int index = indexOf(npc.getId());
        if (index >= 0) {
            selectedReferences.remove(index);
            notifyChanged();
            return;
        }
        if (selectedReferences.size() >= 2) {
            selectedReferences.removeFirst();
        }
        selectedReferences.add(npc);
        notifyChanged();
    }

    public boolean isSelected(int entityId) {
        return indexOf(entityId) >= 0;
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void notifyChanged() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    private int indexOf(int entityId) {
        for (int i = 0; i < selectedReferences.size(); i++) {
            if (selectedReferences.get(i).getId() == entityId) {
                return i;
            }
        }
        return -1;
    }
}
