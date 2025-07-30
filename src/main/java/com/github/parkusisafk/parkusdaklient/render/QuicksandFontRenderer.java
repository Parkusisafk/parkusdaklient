package com.github.parkusisafk.parkusdaklient.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal TTF-based font renderer for 1.8.9 GUIs.
 * - Loads Quicksand from assets.
 * - Caches each glyph as a DynamicTexture.
 * - Provides drawString, getStringWidth, getFontHeight.
 *
 * NOTE: This is GUI-only (2D). It doesn't replace vanilla chat/fonts globally.
 */
public class QuicksandFontRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final Font font;
    private final boolean antiAlias;

    private final Map<Character, Glyph> glyphCache = new HashMap<>();
    private final int fontHeight;       // line height in pixels
    private final int ascent;           // baseline ascent for vertical placement
    private final String nameKey;       // key used for dynamic textures

    public static QuicksandFontRenderer loadFromAssets(String modid, String path, float size, boolean antiAlias) throws Exception {
        // path is something like "fonts/Quicksand-Regular.ttf"
        ResourceLocation rl = new ResourceLocation(modid, path);
        InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(rl).getInputStream();
        Font base = Font.createFont(Font.TRUETYPE_FONT, in);
        Font f = base.deriveFont(Font.PLAIN, size);
        return new QuicksandFontRenderer(f, antiAlias, modid + ":quicksand_" + Math.round(size));
    }

    public QuicksandFontRenderer(Font font, boolean antiAlias, String nameKey) {
        this.font = font;
        this.antiAlias = antiAlias;
        this.nameKey = nameKey;

        // Compute metrics once using an offscreen image
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = tmp.createGraphics();
        applyHints(g, antiAlias);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        this.fontHeight = fm.getHeight();
        this.ascent = fm.getAscent();
        g.dispose();
    }

    public int getFontHeight() {
        return fontHeight;
    }

    public int getStringWidth(String s) {
        if (s == null || s.isEmpty()) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            Glyph glyph = getGlyph(s.charAt(i));
            w += glyph.advance;
        }
        return w;
    }

    /** Draw left-aligned string. Color is 0xAARRGGBB (alpha honored). */
    public void drawString(String s, float x, float y, int color) {
        if (s == null || s.isEmpty()) return;

        // Extract RGBA
        float a = ((color >> 24) & 0xFF) / 255.0f;
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >>  8) & 0xFF) / 255.0f;
        float b = ((color      ) & 0xFF) / 255.0f;
        if (a <= 0) a = 1.0f; // default if no alpha given

        // Setup GL for 2D textured quads
        GlStateManager.enableBlend();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);

        float cx = x;
        for (int i = 0; i < s.length(); i++) {
            Glyph glyph = getGlyph(s.charAt(i));
            if (glyph.width <= 0 || glyph.height <= 0 || glyph.location == null) {
                cx += glyph.advance;
                continue;
            }

            mc.getTextureManager().bindTexture(glyph.location);
            GlStateManager.color(r, g, b, a);

            float gx = cx;
            float gy = y + (ascent - glyph.yBearing); // align baseline

            drawTexturedQuad(gx, gy, glyph.width, glyph.height);

            cx += glyph.advance;
        }

        // Restore (optional)
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
    }

    /** Draw centered string horizontally. */
    public void drawCenteredString(String s, float centerX, float y, int color) {
        int w = getStringWidth(s);
        drawString(s, centerX - w / 2.0f, y, color);
    }

    /** Trim to width (px), appending ellipsis if clipped. */
    public String trimToWidth(String s, int maxWidth) {
        if (getStringWidth(s) <= maxWidth) return s;
        String ellipsis = "...";
        int w = getStringWidth(ellipsis);
        StringBuilder out = new StringBuilder();
        int acc = 0;
        for (int i = 0; i < s.length(); i++) {
            Glyph g = getGlyph(s.charAt(i));
            if (acc + g.advance + w > maxWidth) break;
            out.append(s.charAt(i));
            acc += g.advance;
        }
        out.append(ellipsis);
        return out.toString();
    }

    // ---------- internals ----------

    private Glyph getGlyph(char c) {
        Glyph g = glyphCache.get(c);
        if (g != null) return g;

        g = rasterize(c);
        glyphCache.put(c, g);
        return g;
    }

    private Glyph rasterize(char c) {
        // Use a temp graphics to measure
        BufferedImage tmp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = tmp.createGraphics();
        applyHints(g2, antiAlias);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int chWidth = Math.max(1, fm.charWidth(c));
        int chHeight = Math.max(1, fm.getHeight());

        // Add 2px padding each side to avoid texture bleeding
        int pad = 2;
        int imgW = chWidth + pad * 2;
        int imgH = chHeight + pad * 2;

        g2.dispose();

        BufferedImage img = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        applyHints(g, antiAlias);
        g.setFont(font);
        g.setColor(Color.WHITE);

        // Draw the glyph with baseline at (pad, pad+ascent)
        g.drawString(String.valueOf(c), pad, pad + ascent);
        g.dispose();

        DynamicTexture dyn = new DynamicTexture(img);
        ResourceLocation loc = mc.getTextureManager().getDynamicTextureLocation(nameKey + "_" + (int)c, dyn);

        Glyph glyph = new Glyph();
        glyph.location = loc;
        glyph.width = imgW;
        glyph.height = imgH;
        glyph.advance = chWidth;   // logical advance
        glyph.yBearing = ascent;   // baseline alignment

        return glyph;
    }

    private void drawTexturedQuad(float x, float y, float w, float h) {
        Tessellator tess = Tessellator.getInstance();
        WorldRenderer wr = tess.getWorldRenderer();
        wr.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        wr.pos(x    , y + h, 0).tex(0, 1).endVertex();
        wr.pos(x + w, y + h, 0).tex(1, 1).endVertex();
        wr.pos(x + w, y    , 0).tex(1, 0).endVertex();
        wr.pos(x    , y    , 0).tex(0, 0).endVertex();
        tess.draw();
    }

    private static void applyHints(Graphics2D g, boolean aa) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                aa ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                aa ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    private static class Glyph {
        ResourceLocation location;
        int width, height;
        int advance;
        int yBearing; // ascent baseline alignment
    }
}
