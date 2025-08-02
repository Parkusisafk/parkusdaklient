package com.github.parkusisafk.parkusdaklient.gui;

import com.github.parkusisafk.parkusdaklient.SkyblockMod;
import com.github.parkusisafk.parkusdaklient.render.QuicksandFontRenderer;
import com.github.parkusisafk.parkusdaklient.tasks.BreakTask;
import com.github.parkusisafk.parkusdaklient.tasks.Task;
import com.github.parkusisafk.parkusdaklient.tasks.TeleportTask;
import com.github.parkusisafk.parkusdaklient.tasks.WalkToTask;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

public class GuiTaskMenu extends GuiScreen {
    private QuicksandFontRenderer f() { return SkyblockMod.QUICKSAND_14; }

    private enum TaskType { WALK_TO, BREAK, TELEPORT }

    private final EntityPlayer player;
    private TaskType selectedType = TaskType.WALK_TO;

    private GuiButton btnTeleport, btnWalkTo, btnBreak, btnExecute, btnExecuteLoop, btnExit, btnOk, btnClear, btnClearPrev;

    private GuiTextField xField, yField, zField;
    private GuiTextField loopField; // loops input

    // Right panel layout
    private int panelX;          // left of task list panel
    private int panelY;          // top of task list panel
    private int panelW = 200;    // width of task list panel
    private int panelH;          // height computed in init/draw
    private int rowH   = 14;     // per-row height

    // Dragging state
    private boolean dragging = false;
    private int dragIndex = -1;        // index in queue being dragged
    private int dragMouseYOffset = 0;  // mouseY - rowTop at drag start
    private int lastMouseY = 0;

    // Hovered close index (for visuals)
    private int hoverCloseIndex = -1;

    public GuiTaskMenu(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);

        int leftX = width / 2 - 150;
        int centerX = width / 2 - 20;
        int topY = Math.max(20, height / 2 - 90); // gives a bit more headroom on short windows

        // Left: Task type & control buttons
        int step = 22;
        int chickennugget = 10;
        btnWalkTo    = new GuiButton(1, leftX, topY + step*0, 90, 20, "Walk To");
        btnBreak     = new GuiButton(2, leftX, topY + step*1, 90, 20, "Break");
        btnExecute   = new GuiButton(3, leftX, topY + step*2, 90, 20, "Execute");
        btnExecuteLoop = new GuiButton(8, leftX, topY + step*3, 90, 20, "Execute xN");
        btnClearPrev = new GuiButton(7, leftX, topY + step*4, 90, 20, "Clear Prev");
        btnClear     = new GuiButton(6, leftX, topY + step*5, 90, 20, "Clear");
        btnExit      = new GuiButton(4, leftX, topY + step*6, 90, 20, "Exit");
        btnTeleport = new GuiButton(8,  leftX, topY + step*7, 90, 20, "Teleport");
        buttonList.add(btnWalkTo);
        buttonList.add(btnBreak);
        buttonList.add(btnExecute);
        buttonList.add(btnExecuteLoop);
        buttonList.add(btnClearPrev);
        buttonList.add(btnClear);
        buttonList.add(btnExit);
        buttonList.add(btnTeleport);

        // Center: input fields + OK
        xField = new GuiTextField(10, fontRendererObj, centerX, topY + 15, 80, 18);
        yField = new GuiTextField(11, fontRendererObj, centerX, topY + 39, 80, 18);
        zField = new GuiTextField(12, fontRendererObj, centerX, topY + 63, 80, 18);
        xField.setText(String.valueOf((int) player.posX));
        yField.setText(String.valueOf((int) player.posY-1));
        zField.setText(String.valueOf((int) player.posZ));

        btnOk = new GuiButton(5, centerX, topY + 24*3 + 15, 80, 20, "OK");
        buttonList.add(btnOk);
        int fieldW = 40;
        int fieldH = 18;
        int gap    = 6;
        int loopsX = btnExecuteLoop.xPosition - fieldW - gap;                          // to the LEFT of the button
        int loopsY = btnExecuteLoop.yPosition + (btnExecuteLoop.height - fieldH) / 2;  // vertically centered with button

        loopField = new GuiTextField(13, fontRendererObj, loopsX, loopsY, fieldW, fieldH);
        loopField.setMaxStringLength(4);
        loopField.setText("2");
        loopField.setFocused(false);

        // Right panel geometry
        panelX = width / 2 + 100;   // was +120
        panelY = topY - 6;
        panelH = Math.min(height - panelY - 20, 200);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button == btnWalkTo) {
            selectedType = TaskType.WALK_TO;
        } else if (button == btnBreak) {
            selectedType = TaskType.BREAK;
        } else if (button == btnExecute) {
            List<Task> snapshot = SkyblockMod.taskManager.getQueueSnapshot();
            if (snapshot.isEmpty()) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§7No tasks queued."));
                return;
            }
            // Close first, then start on next tick to ensure handlers see GUI closed
            mc.displayGuiScreen(null);
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(
                    () -> SkyblockMod.taskManager.executeAll(1,snapshot)
            );
        } else if (button == btnExecuteLoop) {
            int loops = parseLoops(loopField.getText(), 2);
            if (loops < 1) loops = 1;
            if (loops > 5000) loops = 5000; // safety cap

            List<Task> snapshot = SkyblockMod.taskManager.getQueueSnapshot();
            if (snapshot.isEmpty()) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§7No tasks queued."));
                return;
            }

            mc.displayGuiScreen(null);
            final int loopsFinal = loops;
            net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() -> {
                mc.thePlayer.addChatMessage(new ChatComponentText("§aExecuting tasks x" + loopsFinal));
                SkyblockMod.taskManager.executeAll(loopsFinal, snapshot);
            });
        } else if (button == btnClearPrev) {
            Task removed = SkyblockMod.taskManager.removeLast();
            if (removed != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§eRemoved last task: " + removed.getDescription()));
            } else {
                mc.thePlayer.addChatMessage(new ChatComponentText("§7No tasks to remove."));
            }
        } else if (button == btnClear) {
            SkyblockMod.taskManager.clear();
            SkyblockMod.moveForwardHandler.cancel();
            SkyblockMod.blockBreakingHandler.cancel();
            mc.thePlayer.addChatMessage(new ChatComponentText("§eCleared all tasks and stopped actions."));
        } else if (button == btnExit) {
            mc.displayGuiScreen(null);
        } else if (button == btnOk) {
            addTaskFromInputs();
        } else if (button == btnTeleport) {
            selectedType = TaskType.TELEPORT;
        }
    }
    private int parseLoops(String s, int def) {
        try {
            return Math.max(1, Integer.parseInt(s.trim()));
        } catch (Exception e) {
            return def;
        }
    }

    /** Create a fresh instance of the same task type with the same position. */
    private Task cloneTask(Task t) {
        if (t == null || t.pos == null) return null;
        BlockPos p = new BlockPos(t.pos.getX(), t.pos.getY(), t.pos.getZ());
        if (t instanceof WalkToTask) {
            // If you added custom timeout ctor, pick that instead.
            return new WalkToTask(p);
        } else if (t instanceof BreakTask) {
            return new BreakTask(p);
        } else if (t instanceof  TeleportTask) {
            return new TeleportTask(p);
        }
        // Unknown task type; skip
        return null;
    }

    private void addTaskFromInputs() {
        try {
            int tx = Integer.parseInt(xField.getText().trim());
            int ty = Integer.parseInt(yField.getText().trim());
            int tz = Integer.parseInt(zField.getText().trim());

            BlockPos pos = new BlockPos(tx, ty, tz);
            Task task;

            switch (selectedType) {
                case TELEPORT:
                    task = new TeleportTask(pos);
                    if (mc.theWorld != null) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(
                                "Queued TELEPORT on " +
                                        mc.theWorld.getBlockState(pos).getBlock().getLocalizedName() +
                                        " @ " + tx + "," + ty + "," + tz));
                    }
                    break;
                case BREAK:
                    task = new BreakTask(pos); // has timeout default in your class
                    if (mc.theWorld != null) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(
                                "Queued BREAK on " +
                                        mc.theWorld.getBlockState(pos).getBlock().getLocalizedName() +
                                        " @ " + tx + "," + ty + "," + tz));
                    }
                    break;
                case WALK_TO:
                default:
                    task = new WalkToTask(pos);
                    if (mc.theWorld != null) {
                        mc.thePlayer.addChatMessage(new ChatComponentText(
                                "Queued WALK TO " + tx + "," + ty + "," + tz + " (" +
                                        mc.theWorld.getBlockState(pos).getBlock().getLocalizedName() + ")"));
                    }
                    break;
            }

            SkyblockMod.taskManager.add(task);

        } catch (NumberFormatException e) {
            mc.thePlayer.addChatMessage(new ChatComponentText("§cInvalid coordinates!"));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        int leftX = width / 2 - 150;
        int centerX = width / 2 - 20;
        int topY = height / 2 - 80;

        // Titles & labels
        if (f() != null) f().drawCenteredString("Task Menu", width / 2f, topY - 16, 0xFFFFFFFF);
        else drawCenteredString(fontRendererObj, "Task Menu", width / 2, topY - 16, 0xFFFFFF);

        if (f() != null) {
            f().drawString("X:", centerX - 12, topY + 4, 0xFFA0A0A0);
            f().drawString("Y:", centerX - 12, topY + 28, 0xFFA0A0A0);
            f().drawString("Z:", centerX - 12, topY + 52, 0xFFA0A0A0);
        } else {
            drawString(fontRendererObj, "X:", centerX - 12, topY + 4, 0xA0A0A0);
            drawString(fontRendererObj, "Y:", centerX - 12, topY + 28, 0xA0A0A0);
            drawString(fontRendererObj, "Z:", centerX - 12, topY + 52, 0xA0A0A0);

        }
        int labelX = loopField.xPosition;
        int labelY = loopField.yPosition - 12;

        if (f() != null) {
            f().drawString("Loops:", labelX, labelY, 0xFFA0A0A0);
        } else {
            drawString(fontRendererObj, "Loops:", labelX, labelY, 0xA0A0A0);
        }

        xField.drawTextBox();
        yField.drawTextBox();
        zField.drawTextBox();
        loopField.drawTextBox();

        // Right panel background
        panelH = Math.min(height - panelY - 20, 220);
        Gui.drawRect(panelX - 6, panelY - 10, panelX + panelW + 6, panelY + panelH + 6, 0x80000000);
        if (f() != null) f().drawString("Tasks:", panelX, panelY - 6, 0xFFFFFFFF);
        else drawString(fontRendererObj, "Tasks:", panelX, panelY - 6, 0xFFFFFF);

        // Render queue rows with close "X" and drag visuals
        List<Task> list = SkyblockMod.taskManager.getQueueSnapshot();
        int listTop = panelY + 10;
        int rowY = listTop;

        // Update hover index for close box
        hoverCloseIndex = -1;
        int idx = 0;

        // Space reserved for the "floating" dragged row
        int insertionIndex = -1;
        if (dragging && dragIndex >= 0 && dragIndex < list.size()) {
            // Compute tentative insertion index from current mouse
            int relY = clamp(mouseY - listTop, 0, Math.max(0, rowH * (list.size() - 1)));
            insertionIndex = clamp(relY / rowH, 0, list.size() - 1);
        }

        // Draw rows
        for (Task t : list) {
            int rowTop = rowY;
            int rowBottom = rowTop + rowH;

            boolean isDraggedRow = dragging && idx == dragIndex;

            // Row background (skip the dragged row; we'll draw it later as floating)
            if (!isDraggedRow) {
                Gui.drawRect(panelX, rowTop, panelX + panelW, rowBottom, 0x40000000); // base
            }

            // Close box geometry (10x10 square on right)
            int padding = 4;
            int closeSize = 8; // smaller close box

            int closeX1 = panelX + panelW - closeSize - padding;
            int closeY1 = rowTop + (rowH - closeSize) / 2;
            int closeX2 = closeX1 + closeSize;
            int closeY2 = closeY1 + closeSize;

            // Detect hover on close box (only if not dragging)
            if (!dragging && mouseX >= closeX1 && mouseX <= closeX2 && mouseY >= closeY1 && mouseY <= closeY2) {
                hoverCloseIndex = idx;
                Gui.drawRect(closeX1 - 1, closeY1 - 1, closeX2 + 1, closeY2 + 1, 0x80FF5555);
            } else {
                Gui.drawRect(closeX1 - 1, closeY1 - 1, closeX2 + 1, closeY2 + 1, 0x80555555);
            }
            //draw the damn x

            if (f() != null) f().drawCenteredString("x", (closeX1 + closeX2) / 2f, closeY1 - 1, 0xFFFF7777);
            else drawCenteredString(fontRendererObj, "x", (closeX1 + closeX2) / 2, closeY1 - 1, 0xFFFF7777);


            // compute close box rect first...
            int maxTextW = (closeX1 - padding) - (panelX + padding);
            String text = (idx + 1) + ". " + t.getDescription();
            String clipped = (f() != null)
                    ? f().trimToWidth(text, maxTextW)
                    : fontRendererObj.trimStringToWidth(text, maxTextW);

            if (!isDraggedRow) {
                if (f() != null) f().drawString(clipped, panelX + padding, rowTop + 2, 0xFFCCCCCC);
                else drawString(fontRendererObj, clipped, panelX + padding, rowTop + 2, 0xCCCCCC);

            }

            rowY += rowH;
            idx++;
            if (rowY > panelY + panelH) break;
        }

        // Draw placeholder line for insertion
        if (dragging && dragIndex >= 0 && dragIndex < list.size() && insertionIndex >= 0) {
            int placeholderY = listTop + insertionIndex * rowH;
            Gui.drawRect(panelX, placeholderY, panelX + panelW, placeholderY + 2, 0x80FFFFFF);
        }

        // Draw the dragged row as a floating element at mouseY - offset
        if (dragging && dragIndex >= 0 && dragIndex < list.size()) {
            Task dragged = list.get(dragIndex);
            int floatTop = mouseY - dragMouseYOffset;
            int floatBottom = floatTop + rowH;
            Gui.drawRect(panelX, floatTop, panelX + panelW, floatBottom, 0x60FFFFFF);
            String text = (dragIndex + 1) + ". " + dragged.getDescription();
            if (f() != null) f().drawString(f().trimToWidth(text, panelW - 8), panelX + 4, floatTop + 2, 0xFF000000);
            else drawString(fontRendererObj, text, panelX + 4, floatTop + 2, 0x000000);

        }

        // Current running task indicator (not draggable)
        if (SkyblockMod.taskManager.getCurrent() != null) {

            String running = "Running: " + SkyblockMod.taskManager.getCurrent().getDescription();
            if (f() != null) f().drawString(running, panelX, panelY + panelH + 10, 0xFF00FF00);

            else drawString(fontRendererObj, running, panelX, panelY + panelH + 10, 0x00FF00);}

        lastMouseY = mouseY;
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        xField.mouseClicked(mouseX, mouseY, mouseButton);
        yField.mouseClicked(mouseX, mouseY, mouseButton);
        zField.mouseClicked(mouseX, mouseY, mouseButton);
        loopField.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton != 0) return; // left click only

        List<Task> list = SkyblockMod.taskManager.getQueueSnapshot();
        if (list.isEmpty()) return;

        int listTop = panelY + 10;
        if (mouseX < panelX || mouseX > panelX + panelW) return;
        if (mouseY < listTop || mouseY > listTop + rowH * list.size()) return;

        int index = (mouseY - listTop) / rowH;
        index = clamp(index, 0, list.size() - 1);

        // Close box hit test for that row
        int rowTop = listTop + index * rowH;
        int closeSize = 8;
        int closeX1 = panelX + panelW - closeSize - 4;
        int closeY1 = rowTop + (rowH - closeSize) / 2;
        int closeX2 = closeX1 + closeSize;
        int closeY2 = closeY1 + closeSize;

        if (mouseX >= closeX1 && mouseX <= closeX2 && mouseY >= closeY1 && mouseY <= closeY2) {
            // Remove this entry
            Task removed = SkyblockMod.taskManager.removeAt(index);
            if (removed != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText("§eRemoved: " + removed.getDescription()));
            }
            return;
        }

        // Start dragging this row
        dragging = true;
        dragIndex = index;
        dragMouseYOffset = mouseY - rowTop;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        lastMouseY = mouseY;
        // Nothing else needed; we compute insertion index in drawScreen
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);

        if (!dragging) return;

        List<Task> list = SkyblockMod.taskManager.getQueueSnapshot();
        if (!list.isEmpty() && dragIndex >= 0 && dragIndex < list.size()) {
            int listTop = panelY + 10;

            // Compute final insertion index
            int relY = clamp(mouseY - listTop, 0, Math.max(0, rowH * (list.size() - 1)));
            int toIndex = clamp(relY / rowH, 0, list.size() - 1);

            if (toIndex != dragIndex) {
                SkyblockMod.taskManager.move(dragIndex, toIndex);
            }
        }

        dragging = false;
        dragIndex = -1;
        dragMouseYOffset = 0;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            addTaskFromInputs();
        } else {
            super.keyTyped(typedChar, keyCode);
        }
        xField.textboxKeyTyped(typedChar, keyCode);
        yField.textboxKeyTyped(typedChar, keyCode);
        zField.textboxKeyTyped(typedChar, keyCode);
        loopField.textboxKeyTyped(typedChar, keyCode);

    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}