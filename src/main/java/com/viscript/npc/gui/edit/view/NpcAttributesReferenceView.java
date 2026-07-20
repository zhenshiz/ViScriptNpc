package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.CustomNpc;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NpcAttributesReferenceView extends View implements INpcEditorSlotView {
    private final NpcEditor editor;
    private final NpcAttributesPageState state;
    private final TextField filterField = new TextField();
    private final ScrollerView scrollerView = new ScrollerView();
    private final List<ReferenceEntry> entries = new ArrayList<>();

    public NpcAttributesReferenceView(NpcEditor editor, NpcAttributesPageState state) {
        super(String.format("%s.editor.view.attributes_reference", ViScriptNpc.MOD_ID));
        this.editor = editor;
        this.state = state;

        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.gapAll(2);
        });

        addChild(createWrappedLabel(Component.translatable("viscript_npc.editor.attributes.reference_hint"), 18));

        UIElement toolbar = new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.height(16);
            layout.flexDirection(FlexDirection.ROW);
            layout.gapAll(2);
        });
        addChild(toolbar);

        filterField.textFieldStyle(style -> style.placeholder(Component.translatable("viscript_npc.editor.attributes.reference_filter_placeholder")));
        filterField.layout(layout -> {
            layout.flex(1);
            layout.height(14);
        });
        filterField.registerValueListener(value -> refreshEntries());
        toolbar.addChild(filterField);

        toolbar.addChild(new Button()
                .setText("viscript_npc.editor.attributes.reference_refresh")
                .setOnClick(e -> refreshEntries())
                .layout(layout -> {
                    layout.width(34);
                    layout.height(14);
                }));

        scrollerView.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        addChild(scrollerView);

        refreshEntries();
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, com.viscript.npc.gui.edit.page.INpcEditorPage page) {
        refreshEntries();
    }

    public void refreshEntries() {
        scrollerView.viewContainer.clearAllChildren();
        entries.clear();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            scrollerView.viewContainer.addChild(createInfoLabel("viscript_npc.editor.basic.no_player"));
            return;
        }

        String currentNpcTypeValue = "";
        if (editor.getCurrentProject() instanceof NPCProject npcProject) {
            currentNpcTypeValue = npcProject.getCurrentNpcType();
        }
        final String currentNpcType = currentNpcTypeValue;
        String filter = filterField.getText().trim().toLowerCase();

        var npcs = player.level().getEntitiesOfClass(CustomNpc.class, player.getBoundingBox().inflate(32))
                .stream()
                .filter(npc -> !npc.getNpcType().equals(currentNpcType))
                .filter(npc -> filter.isEmpty() || npc.getNpcType().toLowerCase().contains(filter))
                .sorted(Comparator.comparing(CustomNpc::getNpcType, String::compareToIgnoreCase))
                .toList();

        if (npcs.isEmpty()) {
            scrollerView.viewContainer.addChild(createInfoLabel("viscript_npc.editor.attributes.reference_empty"));
            return;
        }

        for (CustomNpc npc : npcs) {
            var entry = new ReferenceEntry(npc);
            entries.add(entry);
            scrollerView.viewContainer.addChild(entry);
        }
    }

    private UIElement createInfoLabel(String key) {
        return new Label()
                .setText(Component.translatable(key))
                .textStyle(style -> style
                        .textWrap(TextWrap.WRAP)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .textAlignVertical(Vertical.CENTER)
                        .adaptiveHeight(true))
                .layout(layout -> {
                    layout.widthPercent(100);
                    layout.heightPercent(100);
                    layout.flex(1);
                    layout.alignItems(AlignItems.CENTER);
                    layout.justifyContent(AlignContent.CENTER);
                });
    }

    private Label createWrappedLabel(Component text, float minHeight) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style
                .textWrap(TextWrap.WRAP)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.TOP)
                .adaptiveHeight(true));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.minHeight(minHeight);
        });
        return label;
    }

    private final class ReferenceEntry extends UIElement {
        private final CustomNpc npc;

        private ReferenceEntry(CustomNpc npc) {
            this.npc = npc;
            layout(layout -> {
                layout.widthPercent(100);
                layout.minHeight(18);
                layout.paddingHorizontal(2);
                layout.paddingVertical(1);
                layout.gapAll(1);
            });

            addChild(createWrappedLabel(Component.literal(npc.getNpcType()), 10));
            addChild(createWrappedLabel(Component.translatable("viscript_npc.editor.attributes.reference_health_line",
                    formatNumber(npc.getNpcAttributes().getMaxHealth()),
                    formatNumber(npc.getNpcAttributes().getMeleeConfig().getAttackDamage()),
                    formatNumber(npc.getNpcAttributes().getRangedConfig().getDamage())), 8));

            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                if (event.button == 0) {
                    state.toggleReference(npc);
                    updateSelected();
                    refreshAllSelections();
                }
            });

            updateSelected();
        }

        private void updateSelected() {
            style(style -> style.backgroundTexture(state.isSelected(npc.getId())
                    ? ColorPattern.T_GREEN.rectTexture()
                    : ColorPattern.BLACK.rectTexture()));
        }
    }

    private void refreshAllSelections() {
        for (ReferenceEntry entry : entries) {
            entry.updateSelected();
        }
    }

    private static String formatNumber(double value) {
        if (Math.floor(value) == value) {
            return Integer.toString((int) value);
        }
        return String.format("%.2f", value);
    }
}
