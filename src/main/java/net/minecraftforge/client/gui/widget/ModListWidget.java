/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.locale.Language;
import net.minecraftforge.client.gui.ModListScreen;
import net.minecraftforge.versions.forge.ForgeVersion;
import net.minecraftforge.common.util.MavenVersionStringHelper;
import net.minecraftforge.fml.VersionChecker;
import net.minecraftforge.forgespi.language.IModInfo;

import com.mojang.blaze3d.systems.RenderSystem;

public class ModListWidget extends ObjectSelectionList<ModListWidget.ModEntry> {
    private static String stripControlCodes(String value) { return net.minecraft.util.StringUtil.stripColor(value); }
    private static final ResourceLocation VERSION_CHECK_ICONS = ResourceLocation.fromNamespaceAndPath(ForgeVersion.MOD_ID, "textures/gui/version_check_icons.png");
    private final int listWidth;

    private final ModListScreen parent;

    public ModListWidget(ModListScreen parent, int listWidth, int top, int bottom) {
        super(parent.getMinecraftInstance(), listWidth, bottom, top, parent.getFontRenderer().lineHeight * 2 + 8);
        this.parent = parent;
        this.listWidth = listWidth;
        this.refreshList();
    }

    @Override
    public int getRowWidth() {
        return this.listWidth;
    }

    public void refreshList() {
        this.clearEntries();
        parent.buildModList(this::addEntry, mod->new ModEntry(mod, this.parent));
    }

    /*
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.parent.renderBackground(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
    */

    public class ModEntry extends ObjectSelectionList.Entry<ModEntry> {
        private final IModInfo modInfo;
        private final ModListScreen parent;

        ModEntry(IModInfo info, ModListScreen parent) {
            this.modInfo = info;
            this.parent = parent;
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select", modInfo.getDisplayName());
        }

        @Override
        public void render(GuiGraphics guiGraphics, int entryIdx, int top, int left, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isMouseOver, float partialTick)
        {
            Component name = Component.literal(stripControlCodes(modInfo.getDisplayName()));
            Component version = Component.literal(stripControlCodes(MavenVersionStringHelper.artifactVersionToString(modInfo.getVersion())));
            VersionChecker.CheckResult vercheck = VersionChecker.getResult(modInfo);
            Font font = this.parent.getFontRenderer();
            guiGraphics.drawString(font, Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(name,    listWidth))), left + 3, top + 2, 0xFFFFFF, false);
            guiGraphics.drawString(font, Language.getInstance().getVisualOrder(FormattedText.composite(font.substrByWidth(version, listWidth))), left + 3, top + 2 + font.lineHeight, 0xCCCCCC, false);
            if (vercheck.status().shouldDraw()) {
                //TODO: Consider adding more icons for visualization
                RenderSystem.setShaderColor(1, 1, 1, 1);
                guiGraphics.pose().pushPose();
                guiGraphics.blit(RenderType::guiTextured, VERSION_CHECK_ICONS, getX() + width - 12, top + entryHeight / 4, vercheck.status().getSheetOffset() * 8, (vercheck.status().isAnimated() && ((System.currentTimeMillis() / 800 & 1)) == 1) ? 8 : 0, 8, 8, 64, 16);
                guiGraphics.pose().popPose();
            }
        }

        @Override
        public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
            parent.setSelected(this);
            ModListWidget.this.setSelected(this);
            return false;
        }

        public IModInfo getInfo() {
            return modInfo;
        }
    }
}
