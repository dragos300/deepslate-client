package com.deepslate.ui.screen;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.Feature;
import com.deepslate.ui.Features;
import com.deepslate.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Modern client-style mods menu: ambient backdrop, elevated panels, rich feature rows.
 */
public class DeepslateModsScreen extends Screen {
    private static final int SIDEBAR_W = 168;
    private static final int PANEL_MARGIN = 18;
    private static final int ROW_H = 52;
    private static final int ROW_H_CREDIT = 64;
    private static final int ROW_GAP = 8;
    private static final int TOGGLE_W = 48;
    private static final int TOGGLE_H = 24;
    private static final int LIST_TOP_PAD = 58;
    private static final int LIST_BOTTOM_PAD = 12;
    private static final int FOOTER_H = 44;

    private final Screen parent;
    private Feature.Category selected = Feature.Category.UI;
    private final List<FeatureRow> rows = new ArrayList<>();
    private double scrollOffset;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int contentH;

    public DeepslateModsScreen(Screen parent) {
        super(Component.literal("Deepslate Mods"));
        this.parent = parent;
    }

    public static void open() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new DeepslateModsScreen(mc.screen));
    }

    @Override
    protected void init() {
        rows.clear();
        scrollOffset = 0;

        listX = PANEL_MARGIN + SIDEBAR_W + 20 + 14;
        listY = PANEL_MARGIN + LIST_TOP_PAD;
        listW = this.width - listX - PANEL_MARGIN - 16;
        listH = this.height - PANEL_MARGIN * 2 - FOOTER_H - LIST_TOP_PAD - LIST_BOTTOM_PAD;

        int y = 0;
        for (Feature feature : Features.ALL) {
            if (feature.category() != selected) {
                continue;
            }
            int rowH = feature.hasCredit() ? ROW_H_CREDIT : ROW_H;
            int toggleX = listX + listW - TOGGLE_W - 8;
            rows.add(new FeatureRow(feature, listX, y, listW, rowH, toggleX, y + (rowH - TOGGLE_H) / 2));
            y += rowH + ROW_GAP;
        }
        contentH = Math.max(0, y - ROW_GAP);
        clampScroll();

        int footerY = this.height - PANEL_MARGIN - 28;
        this.addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(this.width - PANEL_MARGIN - 118, footerY, 118, 24)
                        .build()
        );

        if (selected == Feature.Category.HUD) {
            this.addRenderableWidget(
                    Button.builder(Component.literal("Customize Crosshair"), b -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new CrosshairEditorScreen(this));
                                }
                            })
                            .bounds(PANEL_MARGIN + SIDEBAR_W + 20, footerY, 148, 24)
                            .build()
            );
            this.addRenderableWidget(
                    Button.builder(Component.literal("Edit HUD Layout"), b -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new HudEditScreen(this));
                                }
                            })
                            .bounds(PANEL_MARGIN + SIDEBAR_W + 176, footerY, 128, 24)
                            .build()
            );
        }
    }

    private int maxScroll() {
        return Math.max(0, contentH - listH);
    }

    private void clampScroll() {
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll());
    }

    private int enabledIn(Feature.Category cat) {
        DeepslateConfig cfg = DeepslateConfig.get();
        int n = 0;
        for (Feature f : Features.ALL) {
            if (f.category() == cat && f.get(cfg)) {
                n++;
            }
        }
        return n;
    }

    private int totalIn(Feature.Category cat) {
        int n = 0;
        for (Feature f : Features.ALL) {
            if (f.category() == cat) {
                n++;
            }
        }
        return n;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Branding.drawMenuBackdrop(graphics, this.width, this.height);

        int sidebarX = PANEL_MARGIN;
        int sidebarY = PANEL_MARGIN;
        int sidebarH = this.height - PANEL_MARGIN * 2 - FOOTER_H;
        Branding.drawElevatedPanel(
                graphics, sidebarX, sidebarY, SIDEBAR_W, sidebarH, 14, Theme.PANEL_FILL, Theme.PANEL_BORDER
        );

        int panelX = PANEL_MARGIN + SIDEBAR_W + 20;
        int panelY = PANEL_MARGIN;
        int panelW = this.width - panelX - PANEL_MARGIN;
        int panelH = this.height - PANEL_MARGIN * 2 - FOOTER_H;
        Branding.drawElevatedPanel(
                graphics, panelX, panelY, panelW, panelH, 14, Theme.SPACE_2, Theme.PANEL_BORDER
        );
        Branding.fillRounded(graphics, panelX + 1, panelY + 1, panelW - 2, 46, 12, Theme.PANEL_FILL);

        Branding.drawElevatedPanel(
                graphics,
                PANEL_MARGIN,
                this.height - PANEL_MARGIN - FOOTER_H + 6,
                this.width - PANEL_MARGIN * 2,
                FOOTER_H - 8,
                10,
                Theme.PANEL_FILL,
                Theme.PANEL_BORDER
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int sidebarX = PANEL_MARGIN;
        int sidebarY = PANEL_MARGIN;
        int sidebarH = this.height - PANEL_MARGIN * 2 - FOOTER_H;

        Branding.fillRounded(graphics, sidebarX + 14, sidebarY + 14, 36, 36, 10, Theme.ACCENT_SOFT);
        Branding.drawRoundedBorder(graphics, sidebarX + 14, sidebarY + 14, 36, 36, 10, Theme.ACCENT);
        graphics.drawString(this.font, "D", sidebarX + 26, sidebarY + 26, Theme.ACCENT, false);

        graphics.drawString(this.font, "DEEPSLATE", sidebarX + 58, sidebarY + 18, Theme.TEXT, false);
        graphics.drawString(this.font, "MODS", sidebarX + 58, sidebarY + 30, Theme.ACCENT, false);
        Branding.fillRounded(graphics, sidebarX + 14, sidebarY + 58, SIDEBAR_W - 28, 1, 0, Theme.PANEL_BORDER);

        int catY = sidebarY + 74;
        for (Feature.Category cat : Feature.Category.values()) {
            boolean active = cat == selected;
            int enabled = enabledIn(cat);
            int total = totalIn(cat);
            if (active) {
                Branding.fillRounded(
                        graphics, sidebarX + 10, catY - 4, SIDEBAR_W - 20, 32, 9, Theme.ACCENT_SOFT
                );
                Branding.drawAccentBar(graphics, sidebarX + 10, catY, 24);
            } else if (mouseX >= sidebarX + 10
                    && mouseX <= sidebarX + SIDEBAR_W - 10
                    && mouseY >= catY - 4
                    && mouseY <= catY + 28) {
                Branding.fillRounded(
                        graphics, sidebarX + 10, catY - 4, SIDEBAR_W - 20, 32, 9, ARGB.color(40, 255, 255, 255)
                );
            }

            drawCategoryGlyph(graphics, sidebarX + 22, catY + 4, cat, active ? Theme.ACCENT : Theme.TEXT_MUTED);
            int color = active ? Theme.ACCENT : Theme.TEXT_MUTED;
            graphics.drawString(this.font, cat.label(), sidebarX + 40, catY + 2, color, false);
            String badge = enabled + "/" + total;
            graphics.drawString(
                    this.font,
                    badge,
                    sidebarX + SIDEBAR_W - 18 - this.font.width(badge),
                    catY + 14,
                    active ? Theme.ACCENT_SOFT : Theme.TEXT_FAINT,
                    false
            );
            catY += 38;
        }

        graphics.drawString(
                this.font,
                "Right Shift",
                sidebarX + 16,
                sidebarY + sidebarH - 28,
                Theme.TEXT_FAINT,
                false
        );
        graphics.drawString(
                this.font,
                "opens this menu",
                sidebarX + 16,
                sidebarY + sidebarH - 16,
                Theme.TEXT_FAINT,
                false
        );

        int panelX = PANEL_MARGIN + SIDEBAR_W + 20;
        int panelY = PANEL_MARGIN;
        graphics.drawString(this.font, selected.label(), panelX + 20, panelY + 14, Theme.TEXT, false);
        graphics.drawString(
                this.font,
                enabledIn(selected) + " enabled · scroll for more",
                panelX + 20,
                panelY + 28,
                Theme.TEXT_MUTED,
                false
        );

        DeepslateConfig cfg = DeepslateConfig.get();
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0f, (float) (listY - scrollOffset));
        int localMouseY = (int) (mouseY - listY + scrollOffset);
        for (FeatureRow row : rows) {
            row.render(graphics, this.font, cfg, mouseX, localMouseY);
        }
        graphics.pose().popMatrix();
        graphics.disableScissor();

        if (maxScroll() > 0) {
            drawScrollbar(graphics);
        }
    }

    private static void drawCategoryGlyph(
            GuiGraphics graphics, int x, int y, Feature.Category cat, int color
    ) {
        switch (cat) {
            case UI -> {
                Branding.fillRounded(graphics, x, y, 8, 8, 2, color);
            }
            case MENU -> {
                graphics.fill(x, y, x + 9, y + 2, color);
                graphics.fill(x, y + 4, x + 9, y + 6, color);
                graphics.fill(x, y + 8, x + 6, y + 10, color);
            }
            case HUD -> {
                graphics.fill(x + 3, y, x + 5, y + 10, color);
                graphics.fill(x, y + 3, x + 10, y + 5, color);
            }
            case MISC -> {
                Branding.fillRounded(graphics, x + 1, y + 1, 3, 3, 1, color);
                Branding.fillRounded(graphics, x + 6, y + 1, 3, 3, 1, color);
                Branding.fillRounded(graphics, x + 3, y + 6, 3, 3, 1, color);
            }
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int trackX = listX + listW + 2;
        int trackY = listY;
        int trackH = listH;
        Branding.fillRounded(graphics, trackX, trackY, 4, trackH, 2, Theme.SPACE_5);

        float visible = listH / (float) Math.max(1, contentH);
        int thumbH = Math.max(20, Math.round(trackH * visible));
        int thumbY = trackY + Math.round((float) ((trackH - thumbH) * (scrollOffset / Math.max(1, maxScroll()))));
        Branding.fillRounded(graphics, trackX, thumbY, 4, thumbH, 2, Theme.ACCENT);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            scrollOffset -= scrollY * (ROW_H + ROW_GAP);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        double mouseX = event.x();
        double mouseY = event.y();
        int sidebarX = PANEL_MARGIN;
        int catY = PANEL_MARGIN + 74;
        for (Feature.Category cat : Feature.Category.values()) {
            if (mouseX >= sidebarX + 10
                    && mouseX <= sidebarX + SIDEBAR_W - 10
                    && mouseY >= catY - 4
                    && mouseY <= catY + 28) {
                selected = cat;
                this.rebuildWidgets();
                return true;
            }
            catY += 38;
        }

        if (mouseX >= listX && mouseX <= listX + listW && mouseY >= listY && mouseY <= listY + listH) {
            double localY = mouseY - listY + scrollOffset;
            DeepslateConfig cfg = DeepslateConfig.get();
            for (FeatureRow row : rows) {
                if (row.click(mouseX, localY, cfg)) {
                    DeepslateConfig.save();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public void onClose() {
        DeepslateConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private static final class FeatureRow {
        private final Feature feature;
        private final int x;
        private final int y;
        private final int w;
        private final int h;
        private final int toggleX;
        private final int toggleY;

        private FeatureRow(Feature feature, int x, int y, int w, int h, int toggleX, int toggleY) {
            this.feature = feature;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
            this.toggleX = toggleX;
            this.toggleY = toggleY;
        }

        private void render(
                GuiGraphics graphics,
                net.minecraft.client.gui.Font font,
                DeepslateConfig cfg,
                int mouseX,
                int mouseY
        ) {
            boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
            boolean on = feature.get(cfg);
            int bg = hovered ? Theme.PANEL_FILL_HOVER : Theme.PANEL_FILL;
            Branding.drawElevatedPanel(graphics, x, y, w, h, 11, bg, hovered || on ? Theme.ACCENT_SOFT : Theme.PANEL_BORDER);
            if (on) {
                Branding.drawAccentBar(graphics, x + 5, y + 12, h - 24);
            }

            // Status dot
            int dot = on ? Theme.ACCENT : Theme.SPACE_7;
            Branding.fillRounded(graphics, x + 16, y + 14, 6, 6, 3, dot);

            int textX = x + 30;
            graphics.drawString(font, feature.title(), textX, y + 12, Theme.TEXT, false);
            graphics.drawString(font, feature.description(), textX, y + 24, Theme.TEXT_MUTED, false);
            if (feature.hasCredit()) {
                graphics.drawString(font, feature.credit(), textX, y + 36, Theme.ACCENT_SOFT, false);
            }

            int track = on ? Theme.ACCENT : Theme.SPACE_5;
            Branding.fillRounded(graphics, toggleX, toggleY, TOGGLE_W, TOGGLE_H, TOGGLE_H / 2, track);
            Branding.drawRoundedBorder(
                    graphics,
                    toggleX,
                    toggleY,
                    TOGGLE_W,
                    TOGGLE_H,
                    TOGGLE_H / 2,
                    on ? Theme.ACCENT : Theme.PANEL_BORDER
            );
            int knobX = on ? toggleX + TOGGLE_W - TOGGLE_H + 2 : toggleX + 2;
            Branding.fillRounded(
                    graphics,
                    knobX,
                    toggleY + 2,
                    TOGGLE_H - 4,
                    TOGGLE_H - 4,
                    (TOGGLE_H - 4) / 2,
                    on ? Theme.SPACE_1 : Theme.SPACE_12
            );
        }

        private boolean click(double mouseX, double mouseY, DeepslateConfig cfg) {
            if (mouseX >= toggleX
                    && mouseX <= toggleX + TOGGLE_W
                    && mouseY >= toggleY
                    && mouseY <= toggleY + TOGGLE_H) {
                feature.set(cfg, !feature.get(cfg));
                return true;
            }
            if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h) {
                feature.set(cfg, !feature.get(cfg));
                return true;
            }
            return false;
        }
    }
}
