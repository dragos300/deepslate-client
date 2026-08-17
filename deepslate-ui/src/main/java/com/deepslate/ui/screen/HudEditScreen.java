package com.deepslate.ui.screen;

import com.deepslate.ui.Branding;
import com.deepslate.ui.DeepslateConfig;
import com.deepslate.ui.Theme;
import com.deepslate.ui.hud.ArmorHudRenderer;
import com.deepslate.ui.hud.EffectsHudRenderer;
import com.deepslate.ui.hud.HudData;
import com.deepslate.ui.hud.HudLayout;
import com.deepslate.ui.hud.HudModule;
import com.deepslate.ui.hud.HudOverlayRenderer;
import com.deepslate.ui.hud.HudPos;
import com.deepslate.ui.hud.ItemCountHudRenderer;
import com.deepslate.ui.hud.ToolHudRenderer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Drag HUD modules to reposition them. Positions are saved as screen fractions.
 */
public class HudEditScreen extends Screen {
    private final Screen parent;
    private HudModule dragging;
    private int dragOffX;
    private int dragOffY;

    public HudEditScreen(Screen parent) {
        super(Component.literal("Edit HUD Layout"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        DeepslateConfig cfg = DeepslateConfig.get();
        HudLayout.ensureDefaults(cfg);
        if (this.minecraft != null) {
            HudData.tick(this.minecraft);
        }
        addRenderableWidget(
                Button.builder(Component.literal("Reset layout"), b -> {
                            DeepslateConfig c = DeepslateConfig.get();
                            c.posFps = new HudPos(0.01f, 0.02f);
                            c.posPing = new HudPos(0.01f, 0.07f);
                            c.posCoords = new HudPos(0.01f, 0.12f);
                            c.posBiome = new HudPos(0.01f, 0.17f);
                            c.posDay = new HudPos(0.01f, 0.22f);
                            c.posKeystrokes = new HudPos(0.01f, 0.72f);
                            c.posArmor = new HudPos(0.01f, 0.55f);
                            c.posEffects = new HudPos(0.78f, 0.02f);
                            c.posTools = new HudPos(0.55f, 0.86f);
                            c.posItemCount = new HudPos(0.70f, 0.86f);
                            DeepslateConfig.save();
                        })
                        .bounds(this.width / 2 - 160, this.height - 28, 100, 20)
                        .build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(this.width / 2 + 10, this.height - 28, 100, 20)
                        .build()
        );
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, ARGB.color(210, 6, 8, 10));
        graphics.fill(Math.max(0, this.width - 160), 0, this.width, 110, Theme.ACCENT_WASH);
        Branding.drawElevatedPanel(graphics, this.width / 2 - 210, 6, 420, 30, 10, Theme.PANEL_FILL, Theme.PANEL_BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(
                this.font,
                "Drag HUD modules · positions save automatically",
                this.width / 2,
                14,
                Theme.TEXT_MUTED
        );
        HudOverlayRenderer.render(graphics, this.width, this.height, true);

        for (Hitbox box : hitboxes()) {
            boolean hover = box.contains(mouseX, mouseY) || box.module == dragging;
            int color = hover ? Theme.ACCENT : Theme.TEXT_FAINT;
            graphics.fill(box.x, box.y, box.x + box.w, box.y + 1, color);
            graphics.fill(box.x, box.y + box.h - 1, box.x + box.w, box.y + box.h, color);
            graphics.fill(box.x, box.y, box.x + 1, box.y + box.h, color);
            graphics.fill(box.x + box.w - 1, box.y, box.x + box.w, box.y + box.h, color);
            if (hover) {
                graphics.fill(box.x, box.y, box.x + box.w, box.y + box.h, Theme.ACCENT_GLOW);
            }
            graphics.drawString(this.font, box.module.label(), box.x + 2, box.y - 10, color, false);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean bl) {
        if (event.button() == 0) {
            int mx = (int) event.x();
            int my = (int) event.y();
            for (Hitbox box : hitboxes()) {
                if (box.contains(mx, my)) {
                    dragging = box.module;
                    dragOffX = mx - box.x;
                    dragOffY = my - box.y;
                    return true;
                }
            }
        }
        return super.mouseClicked(event, bl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null && event.button() == 0) {
            DeepslateConfig cfg = DeepslateConfig.get();
            Hitbox box = hitboxFor(dragging);
            if (box != null) {
                int nx = (int) event.x() - dragOffX;
                int ny = (int) event.y() - dragOffY;
                HudLayout.setFromPixels(dragging.pos(cfg), nx, ny, this.width, this.height, box.w, box.h);
            }
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging != null) {
            dragging = null;
            DeepslateConfig.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        DeepslateConfig.save();
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private Hitbox hitboxFor(HudModule module) {
        for (Hitbox box : hitboxes()) {
            if (box.module == module) {
                return box;
            }
        }
        return null;
    }

    private List<Hitbox> hitboxes() {
        DeepslateConfig cfg = DeepslateConfig.get();
        Font font = this.font;
        List<Hitbox> list = new ArrayList<>();
        addBox(list, HudModule.FPS, "FPS", String.valueOf(HudData.fps), font, cfg);
        addBox(list, HudModule.PING, "PING", HudData.ping >= 0 ? HudData.ping + "ms" : "—", font, cfg);
        addBox(
                list,
                HudModule.COORDS,
                "XYZ",
                String.format("%.1f  %.1f  %.1f", HudData.x, HudData.y, HudData.z),
                font,
                cfg
        );
        addBox(
                list,
                HudModule.BIOME,
                "BIOME",
                HudData.biome == null || HudData.biome.isEmpty() ? "—" : HudData.biome,
                font,
                cfg
        );
        addBox(list, HudModule.DAY, "DAY", String.valueOf(HudData.day), font, cfg);

        if (cfg.hudKeystrokes) {
            int w = HudOverlayRenderer.keystrokesWidth();
            int h = HudOverlayRenderer.keystrokesHeight();
            list.add(new Hitbox(
                    HudModule.KEYSTROKES,
                    HudLayout.pixelX(cfg.posKeystrokes, this.width, w),
                    HudLayout.pixelY(cfg.posKeystrokes, this.height, h),
                    w,
                    h
            ));
        }
        if (cfg.hudArmorStatus) {
            int w = ArmorHudRenderer.width(font);
            int h = ArmorHudRenderer.height();
            list.add(new Hitbox(
                    HudModule.ARMOR,
                    HudLayout.pixelX(cfg.posArmor, this.width, w),
                    HudLayout.pixelY(cfg.posArmor, this.height, h),
                    w,
                    h
            ));
        }
        if (cfg.hudEffects) {
            List<MobEffectInstance> effects =
                    this.minecraft != null && this.minecraft.player != null
                            ? new ArrayList<>(this.minecraft.player.getActiveEffects())
                            : List.of();
            int w = EffectsHudRenderer.estimateWidth(font, effects);
            int h = EffectsHudRenderer.estimateHeight(Math.max(1, effects.size()));
            list.add(new Hitbox(
                    HudModule.EFFECTS,
                    HudLayout.pixelX(cfg.posEffects, this.width, w),
                    HudLayout.pixelY(cfg.posEffects, this.height, h),
                    w,
                    h
            ));
        }
        if (cfg.hudToolStatus) {
            int w = ToolHudRenderer.width();
            int h = ToolHudRenderer.height();
            list.add(new Hitbox(
                    HudModule.TOOLS,
                    HudLayout.pixelX(cfg.posTools, this.width, w),
                    HudLayout.pixelY(cfg.posTools, this.height, h),
                    w,
                    h
            ));
        }
        if (cfg.hudItemCount) {
            int count = 0;
            if (this.minecraft != null && this.minecraft.player != null) {
                count = ItemCountHudRenderer.countMatching(
                        this.minecraft.player, this.minecraft.player.getMainHandItem());
            }
            int w = ItemCountHudRenderer.width(font, count);
            int h = ItemCountHudRenderer.height();
            list.add(new Hitbox(
                    HudModule.ITEM_COUNT,
                    HudLayout.pixelX(cfg.posItemCount, this.width, w),
                    HudLayout.pixelY(cfg.posItemCount, this.height, h),
                    w,
                    h
            ));
        }
        return list;
    }

    private void addBox(
            List<Hitbox> list, HudModule module, String label, String value, Font font, DeepslateConfig cfg
    ) {
        if (!module.enabled(cfg)) {
            return;
        }
        int[] size = HudOverlayRenderer.boxSize(font, label, value);
        list.add(new Hitbox(
                module,
                HudLayout.pixelX(module.pos(cfg), this.width, size[0]),
                HudLayout.pixelY(module.pos(cfg), this.height, size[1]),
                size[0],
                size[1]
        ));
    }

    private record Hitbox(HudModule module, int x, int y, int w, int h) {
        boolean contains(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
}
