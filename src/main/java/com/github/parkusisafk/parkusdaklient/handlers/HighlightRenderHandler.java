package com.github.parkusisafk.parkusdaklient.handlers;

import com.github.parkusisafk.parkusdaklient.tasks.BreakTask;
import com.github.parkusisafk.parkusdaklient.tasks.Task;
import com.github.parkusisafk.parkusdaklient.tasks.TaskManager;
import com.github.parkusisafk.parkusdaklient.tasks.WalkToTask;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.List;

import static org.lwjgl.opengl.GL11.glLineWidth;

public class HighlightRenderHandler {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final TaskManager taskManager;

    public HighlightRenderHandler(TaskManager taskManager) {
        this.taskManager = taskManager;
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (mc.theWorld == null || mc.thePlayer == null) return;

        // Interpolated camera (viewer) position
        float pt = event.partialTicks;
        double viewerX = mc.thePlayer.lastTickPosX + (mc.thePlayer.posX - mc.thePlayer.lastTickPosX) * pt;
        double viewerY = mc.thePlayer.lastTickPosY + (mc.thePlayer.posY - mc.thePlayer.lastTickPosY) * pt;
        double viewerZ = mc.thePlayer.lastTickPosZ + (mc.thePlayer.posZ - mc.thePlayer.lastTickPosZ) * pt;

        // Gather tasks to render
        List<Task> queue = taskManager.getQueueSnapshot();
        Task current = taskManager.getCurrent();

        if ((queue == null || queue.isEmpty()) && current == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.disableDepth(); // draw on top of the world
        GlStateManager.depthMask(false);

        // 1) Draw queued tasks (thinner, more transparent)
        if (queue != null) {
            for (Task t : queue) {
                drawTaskBox(t, viewerX, viewerY, viewerZ, 1.5f, 0.35f);
            }
        }

        // 2) Draw current task (thicker, less transparent)
        if (current != null) {
            drawTaskBox(current, viewerX, viewerY, viewerZ, 3.0f, 0.8f);
        }

        // Restore GL state
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void drawTaskBox(Task task, double viewerX, double viewerY, double viewerZ, float lineWidth, float alpha) {
        if (task == null || task.pos == null) return;
        BlockPos pos = task.pos;

        AxisAlignedBB aabb = new AxisAlignedBB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1
        ).expand(0.002, 0.002, 0.002) // prevent z-fighting
                .offset(-viewerX, -viewerY, -viewerZ);

        // Color by task type
        float r, g, b;
        if (task instanceof WalkToTask) {
            r = 0.0f; g = 1.0f; b = 0.0f;      // green
        } else if (task instanceof BreakTask) {
            r = 1.0f; g = 0.0f; b = 0.0f;      // red
        } else {
            r = 0.2f; g = 0.6f; b = 1.0f;      // fallback: blue-ish
        }

        glLineWidth(lineWidth);
        GlStateManager.color(r, g, b, alpha);
        drawOutlinedBox(aabb);  // ← custom method below
    }

    private void drawOutlinedBox(AxisAlignedBB bb) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();

        wr.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION);

        // Bottom rectangle
        wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();

        wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();

        wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();

        wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();

        // Top rectangle
        wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();

        wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();

        wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();

        wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();

        // Vertical edges
        wr.pos(bb.minX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.minZ).endVertex();

        wr.pos(bb.maxX, bb.minY, bb.minZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.minZ).endVertex();

        wr.pos(bb.maxX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.maxX, bb.maxY, bb.maxZ).endVertex();

        wr.pos(bb.minX, bb.minY, bb.maxZ).endVertex();
        wr.pos(bb.minX, bb.maxY, bb.maxZ).endVertex();

        tess.draw();
    }
}
