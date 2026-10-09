package com.cozooo.dlc_manager.client.screen

import com.mojang.blaze3d.platform.GlStateManager
import com.mojang.blaze3d.systems.RenderSystem
import com.sighs.apricityui.init.Document
import com.sighs.apricityui.init.Element
import com.sighs.apricityui.layout.Position
import com.sighs.apricityui.spi.AuiRenderService
import com.sighs.apricityui.spi.AuiServices
import com.sighs.apricityui.spi.FboHandle
import com.sighs.apricityui.spi.MeshFormat
import com.sighs.apricityui.spi.MeshMode
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.GameRenderer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL30

class DlcUiTransition @JvmOverloads constructor(private val backend: Backend = GpuBackend()) {
    private var snapshot: Snapshot? = null
    private var waiting = false
    private var startedAt = 0L

    fun capture(region: Element?): Boolean {
        var next: Snapshot? = null
        try {
            if (region != null && (!usable(region) || disabled(region))) {
                cancel()
                return false
            }
            next = backend.capture(region)
            if (next == null || !next.isValid()) {
                release(next)
                cancel()
                return false
            }
            val previous = snapshot
            snapshot = next
            waiting = true
            startedAt = 0
            release(previous)
            return true
        } catch (_: RuntimeException) {
            if (next !== snapshot) release(next)
            cancel()
            return false
        } catch (_: LinkageError) {
            if (next !== snapshot) release(next)
            cancel()
            return false
        }
    }

    fun render(graphics: GuiGraphics?, ready: Boolean, now: Long) {
        val current = snapshot ?: return
        try {
            if (!current.isValid()) {
                cancel()
                return
            }
            if (waiting && ready) {
                waiting = false
                startedAt = now
            }
            val progress = if (waiting) 0.0 else ((now - startedAt).toDouble() / DURATION).coerceIn(0.0, 1.0)
            if (progress >= 1) {
                cancel()
                return
            }
            val alpha = (1 - progress * progress * (3 - 2 * progress)).toFloat()
            current.draw(graphics, alpha)
        } catch (_: RuntimeException) {
            cancel()
        } catch (_: LinkageError) {
            cancel()
        }
    }

    fun cancel() {
        val previous = snapshot
        snapshot = null
        waiting = false
        startedAt = 0
        release(previous)
    }

    fun isRunning(): Boolean = snapshot != null

    fun isWaiting(): Boolean = snapshot != null && waiting

    interface Backend {
        fun capture(region: Element?): Snapshot?
    }

    interface Snapshot {
        fun isValid(): Boolean
        fun draw(graphics: GuiGraphics?, alpha: Float)
        fun close()
    }

    class Bounds private constructor(
        @JvmField val framebufferWidth: Int,
        @JvmField val framebufferHeight: Int,
        @JvmField val guiWidth: Int,
        @JvmField val guiHeight: Int,
        @JvmField val left: Int,
        @JvmField val bottom: Int,
        @JvmField val right: Int,
        @JvmField val top: Int
    ) {
        fun guiLeft(): Float = (left.toDouble() * guiWidth / framebufferWidth).toFloat()
        fun guiRight(): Float = (right.toDouble() * guiWidth / framebufferWidth).toFloat()
        fun guiTop(): Float = ((framebufferHeight - top).toDouble() * guiHeight / framebufferHeight).toFloat()
        fun guiBottom(): Float = ((framebufferHeight - bottom).toDouble() * guiHeight / framebufferHeight).toFloat()

        companion object {
            @JvmStatic
            fun crop(framebufferWidth: Int, framebufferHeight: Int, guiWidth: Int, guiHeight: Int,
                     x: Double, y: Double, width: Double, height: Double): Bounds? {
                if (framebufferWidth <= 0 || framebufferHeight <= 0 || guiWidth <= 0 || guiHeight <= 0
                    || !x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite()
                    || width <= 0 || height <= 0 || !(x + width).isFinite() || !(y + height).isFinite()) return null
                val x0 = x.coerceIn(0.0, guiWidth.toDouble())
                val y0 = y.coerceIn(0.0, guiHeight.toDouble())
                val x1 = (x + width).coerceIn(0.0, guiWidth.toDouble())
                val y1 = (y + height).coerceIn(0.0, guiHeight.toDouble())
                if (x1 <= x0 || y1 <= y0) return null
                val left = kotlin.math.floor(x0 * framebufferWidth / guiWidth).toInt()
                val right = kotlin.math.ceil(x1 * framebufferWidth / guiWidth).toInt()
                val bottom = framebufferHeight - kotlin.math.ceil(y1 * framebufferHeight / guiHeight).toInt()
                val top = framebufferHeight - kotlin.math.floor(y0 * framebufferHeight / guiHeight).toInt()
                return Bounds(framebufferWidth, framebufferHeight, guiWidth, guiHeight, left, bottom, right, top)
            }
        }
    }

    private class GpuBackend : Backend {
        override fun capture(region: Element?): Snapshot? {
            val render = AuiServices.render()
            if (!render.isOnRenderThread) return null
            val minecraft = Minecraft.getInstance() ?: return null
            val window = minecraft.window ?: return null
            val main = render.mainRenderTarget ?: return null
            val guiWidth = window.guiScaledWidth
            val guiHeight = window.guiScaledHeight
            val bounds = if (region == null) {
                Bounds.crop(main.width, main.height, guiWidth, guiHeight, 0.0, 0.0, guiWidth.toDouble(), guiHeight.toDouble())
            } else {
                Document.withContext(region.document).use {
                    val rect = region.boundingClientRect
                    val start = region.document.documentToScreenPosition(Position(rect.left, rect.top))
                    val end = region.document.documentToScreenPosition(Position(rect.right, rect.bottom))
                    Bounds.crop(main.width, main.height, guiWidth, guiHeight, start.x, start.y, end.x - start.x, end.y - start.y)
                }
            } ?: return null
            val previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            val previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
            val colorMask = BufferUtils.createByteBuffer(4)
            val clearColor = BufferUtils.createFloatBuffer(4)
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask)
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor)
            var target: FboHandle? = null
            var captured = false
            try {
                render.pushFilterRenderState().use {
                    render.disableScissorTest()
                    target = render.createOffscreenTarget(bounds.right - bounds.left, bounds.top - bounds.bottom, false)
                    val current = target ?: return null
                    render.blitFramebuffer(main, current, bounds.left, bounds.bottom, bounds.right, bounds.top)
                    render.bindWrite(current, false)
                    render.setColorMask(false, false, false, true)
                    RenderSystem.clearColor(0f, 0f, 0f, 1f)
                    RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX)
                    captured = true
                    return GpuSnapshot(render, current, bounds)
                }
            } finally {
                try {
                    if (!captured) target?.let { destroyTarget(render, it) }
                } finally {
                    render.setColorMask(colorMask.get(0).toInt() != 0, colorMask.get(1).toInt() != 0,
                        colorMask.get(2).toInt() != 0, colorMask.get(3).toInt() != 0)
                    RenderSystem.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3))
                    if (scissor) render.enableScissorTest() else render.disableScissorTest()
                    GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead)
                    GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw)
                }
            }
        }
    }

    private class GpuSnapshot(private val render: AuiRenderService, private var target: FboHandle?, private val bounds: Bounds) : Snapshot {
        override fun isValid(): Boolean {
            if (target == null || !render.isOnRenderThread) return false
            val minecraft = Minecraft.getInstance() ?: return false
            val window = minecraft.window ?: return false
            val main = render.mainRenderTarget ?: return false
            return main.width == bounds.framebufferWidth && main.height == bounds.framebufferHeight
                && window.guiScaledWidth == bounds.guiWidth && window.guiScaledHeight == bounds.guiHeight
        }

        override fun draw(graphics: GuiGraphics?, alpha: Float) {
            if (graphics == null) return
            val current = target ?: return
            graphics.flush()
            val scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
            val stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
            try {
                render.pushFilterRenderState().use {
                    render.disableScissorTest()
                    render.disableStencilTest()
                    render.disableDepthTest()
                    render.setDepthMask(false)
                    render.disableCull()
                    render.enableBlend()
                    render.setBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
                    render.setShader(GameRenderer.getPositionTexShader())
                    render.bindColorTexture(current, 0)
                    render.setShaderColor(1f, 1f, 1f, alpha)
                    val mesh = render.beginMesh(MeshMode.QUADS, MeshFormat.POSITION_TEX)
                    val matrix = graphics.pose().last().pose()
                    val left = bounds.guiLeft()
                    val right = bounds.guiRight()
                    val top = bounds.guiTop()
                    val bottom = bounds.guiBottom()
                    mesh.vertexUV(matrix, left, bottom, 0f, 0f, 0f)
                    mesh.vertexUV(matrix, right, bottom, 0f, 1f, 0f)
                    mesh.vertexUV(matrix, right, top, 0f, 1f, 1f)
                    mesh.vertexUV(matrix, left, top, 0f, 0f, 1f)
                    mesh.submit()
                }
            } finally {
                if (scissor) render.enableScissorTest() else render.disableScissorTest()
                if (stencil) render.enableStencilTest() else render.disableStencilTest()
            }
        }

        override fun close() {
            val previous = target ?: return
            target = null
            if (render.isOnRenderThread) destroyTarget(render, previous)
            else render.recordRenderCall {
                try {
                    destroyTarget(render, previous)
                } catch (_: RuntimeException) {
                } catch (_: LinkageError) {
                }
            }
        }
    }

    companion object {
        private const val DURATION = 200_000_000L

        private fun destroyTarget(render: AuiRenderService, target: FboHandle) {
            val previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING)
            val previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)
            try {
                render.pushFilterRenderState().use { render.destroyBuffers(target) }
            } finally {
                GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead)
                GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw)
            }
        }

        private fun release(snapshot: Snapshot?) {
            if (snapshot == null) return
            try {
                snapshot.close()
            } catch (_: RuntimeException) {
            } catch (_: LinkageError) {
            }
        }

        private fun usable(element: Element): Boolean = element.document != null && element.document.isActive && element.isConnected

        private fun disabled(element: Element): Boolean {
            if (none(element.getAttribute("data-dlc-transition"))) return true
            return element.document.body?.let { none(it.getAttribute("data-dlc-transition")) } == true
        }

        private fun none(value: String?): Boolean = value?.trim()?.equals("none", true) == true
    }
}
