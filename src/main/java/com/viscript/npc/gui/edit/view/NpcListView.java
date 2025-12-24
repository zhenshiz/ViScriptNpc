package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.CustomNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;

public class NpcListView extends View {
    public final NpcEditor editor;
    public final ScrollerView scrollerView = new ScrollerView();
    LocalPlayer player = Minecraft.getInstance().player;

    public NpcListView(NpcEditor editor) {
        super(String.format("%s.editor.view.npcList", ViScriptNpc.MOD_ID));
        this.editor = editor;
        this.scrollerView.layout((layout) -> {
            layout.setWidthPercent(100.0F);
            layout.setFlex(1.0F);
        });
        this.addChild(new Button().setOnClick(event -> {
            if (editor.getCurrentProject() instanceof NPCProject npcProject) {
                CompoundTag tag = npcProject.npc.npcConfig.serializeNBT(Platform.getFrozenRegistry());
                tag.putString("npcType", npcProject.getCurrentNpcType());
                RPCPacketDistributor.rpcToServer(C2SPayload.CREATE_NPC, tag);
            }
        }).setText("生成NPC").layout(layout -> {
            layout.setWidthPercent(50.0F);
            layout.setHeight(12);
        }));
        if (player != null) {
            Map<String, CustomNpc> npcMap = new HashMap<>();
            for (var npc : player.level().getEntitiesOfClass(CustomNpc.class, player.getBoundingBox().inflate(20))) {
                npcMap.put(npc.getNpcType(), npc);
            }
            npcMap.forEach((type, npc) -> {
                UIElement npcElement = new TextElement()
                        .setText(Component.nullToEmpty(type))
                        .layout((layout) -> {
                            layout.setWidthPercent(100.0F);
                            layout.setHeight(10);
                        })
                        .addEventListener(UIEvents.DOUBLE_CLICK, (event) -> {
                            NPCProject project = (NPCProject) NPCProject.PROVIDER.projectCreator.get();
                            project.initNewProject();
                            CompoundTag tag = new CompoundTag();
                            npc.saveWithoutId(tag);
                            project.npc.npcConfig.deserializeNBT(Platform.getFrozenRegistry(), tag);
                            editor.loadProject(project, null);
                        });
                scrollerView.viewContainer.addChild(npcElement);
            });
        }
        this.addChild(this.scrollerView);
    }
}
