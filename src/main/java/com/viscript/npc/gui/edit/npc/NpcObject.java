package com.viscript.npc.gui.edit.npc;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.lowdraglib2.math.Transform;
import lombok.Getter;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@Getter
public class NpcObject implements INPCObject {
    @Configurable(name = "npcObject.name")
    public String name = "Npc Project";
    public final Transform transform = new Transform(this);
    @Nullable
    private IScene scene;

    @Override
    public Transform transform() {
        return this.transform;
    }

    @Override
    public void setSceneInternal(IScene scene) {
        this.scene = scene;
    }
}
