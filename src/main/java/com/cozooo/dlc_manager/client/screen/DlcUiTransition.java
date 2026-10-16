package com.cozooo.dlc_manager.client.screen;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
//? if <26.1 {
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.sighs.apricityui.layout.Position;
import com.sighs.apricityui.spi.AuiRenderService;
import com.sighs.apricityui.spi.AuiServices;
import com.sighs.apricityui.spi.FboHandle;
import com.sighs.apricityui.spi.MeshFormat;
import com.sighs.apricityui.spi.MeshMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
//?}
//? if >=1.20.1 <26.1 {
import net.minecraft.client.gui.GuiGraphics;
//?}


public final class DlcUiTransition {
    private static final long DURATION = 200_000_000L;
    private final Backend backend;
    private Snapshot snapshot;
    private boolean waiting;
    private long startedAt;

    public DlcUiTransition() {
        this(new GpuBackend());
    }

    DlcUiTransition(Backend backend) {
        this.backend = backend;
    }

    public boolean capture(Element region) {
        Snapshot next = null;
        try {
            if (region != null && (!usable(region) || disabled(region))) {
                cancel();
                return false;
            }
            next = backend.capture(region);
            if (next == null || !next.isValid()) {
                release(next);
                cancel();
                return false;
            }
            Snapshot previous = snapshot;
            snapshot = next;
            waiting = true;
            startedAt = 0;
            release(previous);
            return true;
        } catch (RuntimeException | LinkageError ignored) {
            if (next != snapshot) release(next);
            cancel();
            return false;
        }
    }

    public void render(Object graphics, boolean ready, long now) {
        Snapshot current = snapshot;
        if (current == null) return;
        try {
            if (!current.isValid()) {
                cancel();
                return;
            }
            if (waiting && ready) {
                waiting = false;
                startedAt = now;
            }
            double progress = waiting ? 0 : Math.max(0, Math.min(1, (double) (now - startedAt) / DURATION));
            if (progress >= 1) {
                cancel();
                return;
            }
            float alpha = (float) (1 - progress * progress * (3 - 2 * progress));
            current.draw(graphics, alpha);
        } catch (RuntimeException | LinkageError ignored) {
            cancel();
        }
    }

    public void cancel() {
        Snapshot previous = snapshot;
        snapshot = null;
        waiting = false;
        startedAt = 0;
        release(previous);
    }

    public boolean isRunning() { return snapshot != null; }

    public boolean isWaiting() { return snapshot != null && waiting; }

    private static void release(Snapshot snapshot) {
        if (snapshot == null) return;
        try {
            snapshot.close();
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private static boolean usable(Element element) {
        return element.document != null && element.document.isActive() && element.isConnected();
    }

    private static boolean disabled(Element element) {
        if (none(element.getAttribute("data-dlc-transition"))) return true;
        Element body = element.document.body;
        return body != null && none(body.getAttribute("data-dlc-transition"));
    }

    private static boolean none(String value) { return value != null && value.trim().equalsIgnoreCase("none"); }

    //? if <26.1 {
    private static void destroyTarget(AuiRenderService render, FboHandle target) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try (var state = render.pushFilterRenderState()) {
            render.destroyBuffers(target);
        } finally {
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        }
    }
    //?}

    interface Backend {
        Snapshot capture(Element region);
    }

    interface Snapshot {
        boolean isValid();
        void draw(Object graphics, float alpha);
        void close();
    }

    static final class Bounds {
        final int framebufferWidth;
        final int framebufferHeight;
        final int guiWidth;
        final int guiHeight;
        final int left;
        final int bottom;
        final int right;
        final int top;

        private Bounds(int framebufferWidth, int framebufferHeight, int guiWidth, int guiHeight,
                       int left, int bottom, int right, int top) {
            this.framebufferWidth = framebufferWidth;
            this.framebufferHeight = framebufferHeight;
            this.guiWidth = guiWidth;
            this.guiHeight = guiHeight;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            this.top = top;
        }

        static Bounds crop(int framebufferWidth, int framebufferHeight, int guiWidth, int guiHeight,
                           double x, double y, double width, double height) {
            if (framebufferWidth <= 0 || framebufferHeight <= 0 || guiWidth <= 0 || guiHeight <= 0
                || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)
                || width <= 0 || height <= 0 || !Double.isFinite(x + width) || !Double.isFinite(y + height)) return null;
            double x0 = Math.max(0, Math.min(guiWidth, x));
            double y0 = Math.max(0, Math.min(guiHeight, y));
            double x1 = Math.max(0, Math.min(guiWidth, x + width));
            double y1 = Math.max(0, Math.min(guiHeight, y + height));
            if (x1 <= x0 || y1 <= y0) return null;
            int left = (int) Math.floor(x0 * framebufferWidth / guiWidth);
            int right = (int) Math.ceil(x1 * framebufferWidth / guiWidth);
            int bottom = framebufferHeight - (int) Math.ceil(y1 * framebufferHeight / guiHeight);
            int top = framebufferHeight - (int) Math.floor(y0 * framebufferHeight / guiHeight);
            return new Bounds(framebufferWidth, framebufferHeight, guiWidth, guiHeight, left, bottom, right, top);
        }

        float guiLeft() { return (float) ((double) left * guiWidth / framebufferWidth); }
        float guiRight() { return (float) ((double) right * guiWidth / framebufferWidth); }
        float guiTop() { return (float) ((double) (framebufferHeight - top) * guiHeight / framebufferHeight); }
        float guiBottom() { return (float) ((double) (framebufferHeight - bottom) * guiHeight / framebufferHeight); }
    }

    //? if <26.1 {
    private static final class GpuBackend implements Backend {
        @Override
        public Snapshot capture(Element region) {
            AuiRenderService render = AuiServices.render();
            if (!render.isOnRenderThread()) return null;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getWindow() == null) return null;
            FboHandle main = render.getMainRenderTarget();
            if (main == null) return null;
            var window = minecraft.getWindow();
            int guiWidth = window.getGuiScaledWidth();
            int guiHeight = window.getGuiScaledHeight();
            Bounds bounds;
            if (region == null) {
                bounds = Bounds.crop(main.width, main.height, guiWidth, guiHeight, 0, 0, guiWidth, guiHeight);
            } else {
                try (var context = Document.withContext(region.document)) {
                    var rect = region.getBoundingClientRect();
                    Position start = region.document.documentToScreenPosition(new Position(rect.left, rect.top));
                    Position end = region.document.documentToScreenPosition(new Position(rect.right, rect.bottom));
                    bounds = Bounds.crop(main.width, main.height, guiWidth, guiHeight, start.x, start.y, end.x - start.x, end.y - start.y);
                }
            }
            if (bounds == null) return null;
            int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            var colorMask = BufferUtils.createByteBuffer(4);
            var clearColor = BufferUtils.createFloatBuffer(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            FboHandle target = null;
            boolean captured = false;
            try (var state = render.pushFilterRenderState()) {
                render.disableScissorTest();
                target = render.createOffscreenTarget(bounds.right - bounds.left, bounds.top - bounds.bottom, false);
                if (target == null) return null;
                render.blitFramebuffer(main, target, bounds.left, bounds.bottom, bounds.right, bounds.top);
                render.bindWrite(target, false);
                render.setColorMask(false, false, false, true);
                RenderSystem.clearColor(0, 0, 0, 1);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
                captured = true;
                return new GpuSnapshot(render, target, bounds);
            } finally {
                try {
                    if (!captured && target != null) destroyTarget(render, target);
                } finally {
                    render.setColorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                    RenderSystem.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
                    if (scissor) render.enableScissorTest();
                    else render.disableScissorTest();
                    GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
                    GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
                }
            }
        }
    }
    //?}

    //? if >=26.1 {
    /*private static final class GpuBackend implements Backend {
        @Override
        public Snapshot capture(Element region) { return null; }
    }
    *///?}

    //? if <26.1 {
    private static final class GpuSnapshot implements Snapshot {
        private final AuiRenderService render;
        private final Bounds bounds;
        private FboHandle target;

        private GpuSnapshot(AuiRenderService render, FboHandle target, Bounds bounds) {
            this.render = render;
            this.target = target;
            this.bounds = bounds;
        }

        @Override
        public boolean isValid() {
            if (target == null || !render.isOnRenderThread()) return false;
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getWindow() == null) return false;
            FboHandle main = render.getMainRenderTarget();
            var window = minecraft.getWindow();
            return main != null && main.width == bounds.framebufferWidth && main.height == bounds.framebufferHeight
                && window.getGuiScaledWidth() == bounds.guiWidth && window.getGuiScaledHeight() == bounds.guiHeight;
        }

        @Override
        public void draw(Object graphics, float alpha) {
            //? if >=1.20.1 <26.1 {
            drawModern((GuiGraphics) graphics, alpha);
            //?}
        }

        //? if >=1.20.1 <26.1 {
        private void drawModern(GuiGraphics graphics, float alpha) {
            if (graphics == null || target == null) return;
            graphics.flush();
            boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            boolean stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            try (var state = render.pushFilterRenderState()) {
                render.disableScissorTest();
                render.disableStencilTest();
                render.disableDepthTest();
                render.setDepthMask(false);
                render.disableCull();
                render.enableBlend();
                render.setBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                render.setShader(GameRenderer.getPositionTexShader());
                render.bindColorTexture(target, 0);
                render.setShaderColor(1, 1, 1, alpha);
                var mesh = render.beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX);
                var matrix = graphics.pose().last().pose();
                float left = bounds.guiLeft();
                float right = bounds.guiRight();
                float top = bounds.guiTop();
                float bottom = bounds.guiBottom();
                mesh.vertexUV(matrix, left, bottom, 0, 0, 0);
                mesh.vertexUV(matrix, right, bottom, 0, 1, 0);
                mesh.vertexUV(matrix, right, top, 0, 1, 1);
                mesh.vertexUV(matrix, left, top, 0, 0, 1);
                mesh.submit();
            } finally {
                if (scissor) render.enableScissorTest();
                else render.disableScissorTest();
                if (stencil) render.enableStencilTest();
                else render.disableStencilTest();
            }
        }
        //?}

        @Override
        public void close() {
            FboHandle previous = target;
            target = null;
            if (previous == null) return;
            if (render.isOnRenderThread()) destroyTarget(render, previous);
            else render.recordRenderCall(() -> {
                try {
                    destroyTarget(render, previous);
                } catch (RuntimeException | LinkageError ignored) {
                }
            });
        }
    }
    //?}
}
