package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NpcListView extends View implements INpcEditorSlotView {
    private final NpcEditor editor;
    private final UIElement buttonRow = new UIElement();
    private final TextField filterField = new TextField();
    private final ScrollerView scrollerView = new ScrollerView();
    private final List<NpcListEntry> npcEntries = new ArrayList<>();
    private int selectedEntityId = -1;

    public NpcListView(NpcEditor editor) {
        super(String.format("%s.editor.view.npcList", ViScriptNpc.MOD_ID));
        this.editor = editor;

        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.gapAll(2);
        });

        buttonRow.layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW);
            layout.gapAll(2);
            layout.height(16);
        });
        addChild(buttonRow);

        buttonRow.addChild(new Button()
                .setOnClick(event -> createNewNpc())
                .setText("viscript_npc.editor.basic.create_new_npc")
                .layout(layout -> {
                    layout.flex(1);
                    layout.height(14);
                }));

        buttonRow.addChild(new Button()
                .setOnClick(event -> confirmOverwriteNpc())
                .setText("viscript_npc.editor.basic.overwrite_npc")
                .layout(layout -> {
                    layout.flex(1);
                    layout.height(14);
                }));

        buttonRow.addChild(new Button()
                .setOnClick(event -> refreshNpcList())
                .setText("viscript_npc.editor.basic.refresh_npc_list")
                .layout(layout -> {
                    layout.width(36);
                    layout.height(14);
                }));

        filterField.setText("", false);
        filterField.textFieldStyle(style -> style.placeholder(Component.translatable("viscript_npc.editor.basic.filter_placeholder")));
        filterField.layout(layout -> {
            layout.widthPercent(100);
            layout.height(16);
        });
        filterField.registerValueListener(value -> refreshNpcList());
        addChild(filterField);

        scrollerView.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        addChild(scrollerView);

        refreshNpcList();
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, com.viscript.npc.gui.edit.page.INpcEditorPage page) {
        refreshNpcList();
    }

    public void refreshNpcList() {
        scrollerView.viewContainer.clearAllChildren();
        npcEntries.clear();
        selectedEntityId = -1;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            scrollerView.viewContainer.addChild(createInfoLabel("viscript_npc.editor.basic.no_player"));
            return;
        }

        var npcs = player.level().getEntitiesOfClass(CustomNpc.class, player.getBoundingBox().inflate(32))
                .stream()
                .filter(npc -> matchesFilter(npc.getNpcType()))
                .sorted(Comparator.comparing(CustomNpc::getNpcType, String::compareToIgnoreCase))
                .toList();

        if (npcs.isEmpty()) {
            scrollerView.viewContainer.addChild(createInfoLabel("viscript_npc.editor.basic.no_world_npc"));
            return;
        }

        for (CustomNpc npc : npcs) {
            var entry = new NpcListEntry(npc);
            npcEntries.add(entry);
            scrollerView.viewContainer.addChild(entry);
        }
    }

    private UIElement createInfoLabel(String key) {
        return new Label()
                .setText(Component.translatable(key))
                .textStyle(style -> style
                        .textAlignHorizontal(com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal.CENTER)
                        .textAlignVertical(com.lowdragmc.lowdraglib2.gui.ui.data.Vertical.CENTER)
                        .adaptiveHeight(true))
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightPercent(100);
                    layout.flex(1);
                    layout.alignItems(dev.vfyjxf.taffy.style.AlignItems.CENTER);
                    layout.justifyContent(dev.vfyjxf.taffy.style.AlignContent.CENTER);
                });
    }

    private boolean matchesFilter(String npcType) {
        String filter = filterField.getText().trim();
        if (filter.isEmpty()) {
            return true;
        }
        return npcType.toLowerCase().contains(filter.toLowerCase());
    }

    private void createNewNpc() {
        if (editor.getCurrentProject() instanceof NPCProject npcProject) {
            CompoundTag tag = npcProject.npc.npcConfig.serializeNBT(Platform.getFrozenRegistry());
            tag.putString("npcType", npcProject.getCurrentNpcType());
            RPCPacketDistributor.rpcToServer(C2SPayload.CREATE_NEW_NPC, tag);
        }
    }

    private void confirmOverwriteNpc() {
        if (selectedEntityId < 0) {
            showInfoDialog(
                    "viscript_npc.editor.basic.warning.title",
                    "viscript_npc.editor.basic.overwrite_npc_missing_target"
            );
            return;
        }
        var dialog = new Dialog()
                .setTitle(Component.translatable("viscript_npc.editor.basic.warning.title").getString())
                .addContent(new Label()
                        .setText(Component.translatable("viscript_npc.editor.basic.overwrite_npc_warning"))
                        .textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true))
                        .layout(layout -> layout.width(150)));
        dialog.addButton(new Button()
                .setOnClick(e -> {
                    overwriteSelectedNpc();
                    dialog.close();
                })
                .setText("ldlib.gui.tips.confirm")
                .addClass("__confirm-button__"));
        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ldlib.gui.tips.cancel")
                .addClass("__cancel-button__"));
        dialog.show(getModularUI());
    }

    private void overwriteSelectedNpc() {
        if (selectedEntityId < 0) {
            return;
        }
        if (editor.getCurrentProject() instanceof NPCProject npcProject) {
            CompoundTag tag = npcProject.npc.npcConfig.serializeNBT(Platform.getFrozenRegistry());
            tag.putString("npcType", npcProject.getCurrentNpcType());
            RPCPacketDistributor.rpcToServer(C2SPayload.OVERWRITE_NPC, selectedEntityId, tag);
        }
    }

    private void selectNpc(int entityId) {
        selectedEntityId = entityId;
        for (NpcListEntry entry : npcEntries) {
            entry.setSelected(entry.entityId == entityId);
        }
    }

    private void loadNpcToEditor(CustomNpc npc) {
        NPCProject project = (NPCProject) NPCProject.PROVIDER.projectCreator.get();
        project.initNewProject();
        CompoundTag tag = new CompoundTag();
        npc.saveWithoutId(tag);
        project.npc.npcConfig.deserializeNBT(Platform.getFrozenRegistry(), tag);
        editor.loadProject(project, null);
    }

    private void showInfoDialog(String titleKey, String messageKey) {
        var dialog = new Dialog()
                .setTitle(Component.translatable(titleKey).getString())
                .addContent(new Label()
                        .setText(Component.translatable(messageKey))
                        .textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true))
                        .layout(layout -> layout.width(150)));
        dialog.addButton(new Button()
                .setOnClick(e -> dialog.close())
                .setText("ldlib.gui.tips.confirm")
                .addClass("__confirm-button__"));
        dialog.show(getModularUI());
    }

    private final class NpcListEntry extends UIElement {
        private final int entityId;

        private NpcListEntry(CustomNpc npc) {
            this.entityId = npc.getId();
            layout(layout -> {
                layout.widthPercent(100);
                layout.height(12);
                layout.paddingHorizontal(2);
            });
            addChild(new Label()
                    .setText(Component.literal(npc.getNpcType()))
                    .layout(layout -> {
                        layout.widthPercent(100);
                        layout.heightPercent(100);
                    }));
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0) {
                    selectNpc(entityId);
                }
            });
            addEventListener(UIEvents.DOUBLE_CLICK, event -> loadNpcToEditor(npc));
            setSelected(false);
        }

        private void setSelected(boolean selected) {
            style(style -> style.backgroundTexture(selected ? ColorPattern.T_BLUE.rectTexture() : ColorPattern.BLACK.rectTexture()));
        }
    }
}
