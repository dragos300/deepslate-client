package com.deepslate.ui.screen;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.Theme;
import com.deepslate.ui.hud.CrosshairRenderer;
import com.deepslate.ui.hud.CrosshairStyle;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * Live editor for custom crosshair size, gap, thickness, colors, outline, and center dot.
 */
public class CrosshairEditorScreen extends Screen {
    private final Screen parent;

    public CrosshairEditorScreen(Screen parent) {
        super(Component.literal("Crosshair"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DeepslateConfig c = DeepslateConfig.get();
        int left = this.width / 2 - 200;
        int y = 58;
        int w = 210;
        int h = 20;
        int gap = 22;

        CrosshairStyle initial = CrosshairStyle.fromId(c.crosshairStyle);
        addRenderableWidget(
                CycleButton.builder((CrosshairStyle s) -> Component.literal(s.label()), initial)
                        .withValues(CrosshairStyle.values())
                        .create(left, y, w, h, Component.literal("Style"), (btn, value) -> {
                            DeepslateConfig.get().crosshairStyle = value.name();
                        })
        );
        y += gap;

        addRenderableWidget(intSlider(left, y, w, h, "Size", 1, 20, c.crosshairSize, v -> {
            DeepslateConfig.get().crosshairSize = v;
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Gap", 0, 12, c.crosshairGap, v -> {
            DeepslateConfig.get().crosshairGap = v;
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Thickness", 1, 8, c.crosshairThickness, v -> {
            DeepslateConfig.get().crosshairThickness = v;
        }));
        y += gap;

        addRenderableWidget(intSlider(left, y, w, h, "Red", 0, 255, ARGB.red(c.crosshairColor), v -> {
            DeepslateConfig cfg = DeepslateConfig.get();
            cfg.crosshairColor = withChannel(cfg.crosshairColor, Channel.R, v);
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Green", 0, 255, ARGB.green(c.crosshairColor), v -> {
            DeepslateConfig cfg = DeepslateConfig.get();
            cfg.crosshairColor = withChannel(cfg.crosshairColor, Channel.G, v);
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Blue", 0, 255, ARGB.blue(c.crosshairColor), v -> {
            DeepslateConfig cfg = DeepslateConfig.get();
            cfg.crosshairColor = withChannel(cfg.crosshairColor, Channel.B, v);
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Opacity", 0, 255, ARGB.alpha(c.crosshairColor), v -> {
            DeepslateConfig cfg = DeepslateConfig.get();
            cfg.crosshairColor = withChannel(cfg.crosshairColor, Channel.A, v);
        }));
        y += gap;

        addRenderableWidget(
                CycleButton.onOffBuilder(c.crosshairOutline)
                        .create(left, y, w, h, Component.literal("Outline"), (btn, value) -> {
                            DeepslateConfig.get().crosshairOutline = value;
                        })
        );
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Outline size", 1, 4, c.crosshairOutlineThickness, v -> {
            DeepslateConfig.get().crosshairOutlineThickness = v;
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Outline R", 0, 255, ARGB.red(c.crosshairOutlineColor), v -> {
            DeepslateConfig cfg = DeepslateConfig.get();
            cfg.crosshairOutlineColor = withChannel(cfg.crosshairOutlineColor, Channel.R, v);
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Outline G", 0, 255, ARGB.green(c.crosshairOutlineColor), v -> {
            DeepslateConfig cfg = DeepslateConfig.get();
            cfg.crosshairOutlineColor = withChannel(cfg.crosshairOutlineColor, Channel.G, v);
        }));
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Outline B", 0, 255, ARGB.blue(c.crosshairOutlineColor), v -> {
            DeepslateConfig cfg = DeepslateConfig.get();
            cfg.crosshairOutlineColor = withChannel(cfg.crosshairOutlineColor, Channel.B, v);
        }));
        y += gap;

        addRenderableWidget(
                CycleButton.onOffBuilder(c.crosshairDot)
                        .create(left, y, w, h, Component.literal("Center dot"), (btn, value) -> {
                            DeepslateConfig.get().crosshairDot = value;
                        })
        );
        y += gap;
        addRenderableWidget(intSlider(left, y, w, h, "Dot size", 1, 6, c.crosshairDotSize, v -> {
            DeepslateConfig.get().crosshairDotSize = v;
        }));
        y += gap + 8;

        addRenderableWidget(
                Button.builder(Component.literal("Reset defaults"), b -> {
                            DeepslateConfig cfg = DeepslateConfig.get();
                            cfg.crosshairSize = 5;
                            cfg.crosshairGap = 2;
                            cfg.crosshairThickness = 2;
                            cfg.crosshairColor = Theme.ACCENT;
                            cfg.crosshairOutline = true;
                            cfg.crosshairOutlineColor = Theme.SPACE_1;
                            cfg.crosshairOutlineThickness = 1;
                            cfg.crosshairDot = false;
                            cfg.crosshairDotSize = 2;
                            this.rebuildWidgets();
                        })
                        .bounds(left, y, w, 22)
                        .build()
        );

        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(this.width / 2 - 55, this.height - 30, 110, 22)
                        .build()
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Branding.drawMenuBackdrop(graphics, this.width, this.height);

        int panelX = this.width / 2 - 220;
        int panelY = 28;
        Branding.drawElevatedPanel(
                graphics, panelX, panelY, 250, this.height - 70, 14, Theme.PANEL_FILL, Theme.PANEL_BORDER
        );
        graphics.drawString(this.font, "CROSSHAIR", panelX + 16, panelY + 12, Theme.TEXT, false);
        graphics.drawString(this.font, "EDITOR", panelX + 16, panelY + 24, Theme.ACCENT, false);

        int previewX = this.width / 2 + 130;
        int previewY = this.height / 2 - 10;
        Branding.drawElevatedPanel(
                graphics, previewX - 64, previewY - 64, 128, 148, 14, Theme.PANEL_FILL, Theme.PANEL_BORDER
        );
        Branding.fillRounded(graphics, previewX - 48, previewY - 48, 96, 96, 10, Theme.SPACE_2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int previewX = this.width / 2 + 130;
        int previewY = this.height / 2 - 10;
        CrosshairRenderer.renderPreview(graphics, previewX, previewY);
        graphics.drawCenteredString(this.font, "Preview", previewX, previewY + 58, Theme.ACCENT);
    }

    @Override
    public void onClose() {
        DeepslateConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private enum Channel {
        A,
        R,
        G,
        B
    }

    private static int withChannel(int argb, Channel channel, int value) {
        int a = ARGB.alpha(argb);
        int r = ARGB.red(argb);
        int g = ARGB.green(argb);
        int b = ARGB.blue(argb);
        value = Mth.clamp(value, 0, 255);
        return switch (channel) {
            case A -> ARGB.color(value, r, g, b);
            case R -> ARGB.color(a, value, g, b);
            case G -> ARGB.color(a, r, value, b);
            case B -> ARGB.color(a, r, g, value);
        };
    }

    @FunctionalInterface
    private interface IntConsumer {
        void accept(int value);
    }

    private static AbstractSliderButton intSlider(
            int x, int y, int w, int h, String label, int min, int max, int current, IntConsumer onChange
    ) {
        double initial = (current - min) / (double) (max - min);
        return new AbstractSliderButton(x, y, w, h, Component.empty(), Mth.clamp(initial, 0.0, 1.0)) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                int v = valueToInt();
                this.setMessage(Component.literal(label + ": " + v));
            }

            @Override
            protected void applyValue() {
                onChange.accept(valueToInt());
            }

            private int valueToInt() {
                return min + (int) Math.round(Mth.clamp(this.value, 0.0, 1.0) * (max - min));
            }
        };
    }
}
