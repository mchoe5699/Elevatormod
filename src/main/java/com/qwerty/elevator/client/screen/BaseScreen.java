package com.qwerty.elevator.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Small widget toolkit shared by the elevator screens. */
public abstract class BaseScreen extends Screen {
    protected int panelW = 380, panelH = 236;
    protected int left, top;
    protected final List<Label> labels = new ArrayList<>();

    protected record Label(Component text, int x, int y, int color) {}

    protected BaseScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        rebuild();
    }

    protected void rebuild() {
        clearWidgets();
        labels.clear();
        build();
    }

    protected abstract void build();

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected void label(Component text, int x, int y) {
        labels.add(new Label(text, x, y, 0xD8DEE3));
    }

    protected void label(Component text, int x, int y, int color) {
        labels.add(new Label(text, x, y, color));
    }

    protected Button button(Component text, int x, int y, int w, Button.OnPress press) {
        return addRenderableWidget(Button.builder(text, press).bounds(x, y, w, 18).build());
    }

    protected Button small(Component text, int x, int y, int w, Button.OnPress press) {
        return addRenderableWidget(Button.builder(text, press).bounds(x, y, w, 16).build());
    }

    /** "label: value" button that cycles a value on click (shift = backwards). */
    protected void cycle(Component name, int x, int y, int w, Supplier<Component> value, IntConsumer change) {
        Component text = Component.empty().append(name).append(": ").append(value.get());
        addRenderableWidget(Button.builder(text, b -> {
            change.accept(hasShiftDown() ? -1 : 1);
            rebuild();
        }).bounds(x, y, w, 18).build());
    }

    protected void toggle(Component name, int x, int y, int w, boolean value, Consumer<Boolean> set) {
        Component text = Component.empty().append(name).append(": ").append(Component.translatable(value ? "gui.qwertys_elevator.on" : "gui.qwertys_elevator.off"));
        addRenderableWidget(Button.builder(text, b -> {
            set.accept(!value);
            rebuild();
        }).bounds(x, y, w, 18).build());
    }

    /** label  [-] value [+]  ; shift-click uses the big step. */
    protected void stepper(Component name, int x, int y, int labelW, Supplier<String> value, IntConsumer step) {
        label(name, x, y + 5);
        int bx = x + labelW;
        small(Component.literal("-"), bx, y + 1, 16, b -> { step.accept(hasShiftDown() ? -10 : -1); rebuild(); });
        labels.add(new Label(Component.literal(value.get()), bx + 22, y + 5, 0xFFFFFF));
        small(Component.literal("+"), bx + 74, y + 1, 16, b -> { step.accept(hasShiftDown() ? 10 : 1); rebuild(); });
    }

    protected EditBox edit(int x, int y, int w, String value, int max, Consumer<String> onChange) {
        EditBox box = new EditBox(font, x, y, w, 16, Component.empty());
        box.setMaxLength(max);
        box.setValue(value);
        box.setResponder(onChange);
        return addRenderableWidget(box);
    }

    protected void drawPanel(GuiGraphics g) {
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, 0xE0101418);
        g.fill(left - 3, top - 3, left + panelW + 3, top - 2, 0xFF5A7B9A);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        renderBackground(g);
        drawPanel(g);
        drawExtra(g, mouseX, mouseY, pt);
        for (Label l : labels) g.drawString(font, l.text(), l.x(), l.y(), l.color(), false);
        super.render(g, mouseX, mouseY, pt);
    }

    protected void drawExtra(GuiGraphics g, int mouseX, int mouseY, float pt) {}

    protected static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-6) return Long.toString(Math.round(v));
        return String.format("%.2f", v);
    }
}
