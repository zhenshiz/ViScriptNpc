package com.viscript.npc.gui.edit.data;

import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.IScene;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.ISceneObject;
import com.lowdragmc.lowdraglib2.math.Transform;
import lombok.Getter;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@Getter
public class ClientNpcConfig extends NpcConfig implements ISceneObject {
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

    public ClientNpcConfig(NpcConfig config) {
        npcData.addAll(config.npcData);
    }
}