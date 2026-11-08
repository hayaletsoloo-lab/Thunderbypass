package thunder.hack.features.modules.movement;

import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import thunder.hack.events.impl.EventClickSlot;
import thunder.hack.events.impl.PacketEvent;
import thunder.hack.features.modules.Module;
import thunder.hack.setting.Setting;
import thunder.hack.utility.Timer;
import thunder.hack.utility.player.MovementUtility;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class GuiMove extends Module {
    public GuiMove() {
        super("GuiMove", Category.MOVEMENT);
    }

    private final Setting<Bypass> clickBypass = new Setting<>("Bypass", Bypass.None);
    private final Setting<Boolean> rotateOnArrows = new Setting<>("RotateOnArrows", true);
    private final Setting<Boolean> sneak = new Setting<>("sneak", false);

    private final Queue<ClickSlotC2SPacket> storedClicks = new LinkedList<>();
    private final AtomicBoolean pause = new AtomicBoolean();

    // FunTime (ported from exosware GuiWalk)
    private final Timer funTimer = new Timer();
    private boolean wasInventoryOpen = false;
    private boolean pendingFlush = false;

    @Override
    public void onUpdate() {
        if (clickBypass.is(Bypass.FunTime)) {
            boolean inventoryOpen = mc.currentScreen instanceof InventoryScreen;
            if (inventoryOpen) {
                wasInventoryOpen = true;
            } else if (wasInventoryOpen) {
                wasInventoryOpen = false;
                pendingFlush = true;
                funTimer.reset();
            }
            if (pendingFlush && funTimer.passedMs(80)) {
                pendingFlush = false;
                flushStored();
            }
        }

        if (mc.currentScreen != null && !(mc.currentScreen instanceof ChatScreen) && !(mc.currentScreen instanceof SignEditScreen)) {
            KeyBinding[] keys = {mc.options.forwardKey, mc.options.backKey, mc.options.leftKey, mc.options.rightKey, mc.options.jumpKey, mc.options.sprintKey};
            boolean funTimePause = clickBypass.is(Bypass.FunTime) && (pendingFlush || !storedClicks.isEmpty()) && !funTimer.passedMs(100);

            for (KeyBinding k : keys)
                k.setPressed(!funTimePause && isKeyPressed(InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey()).getCode()));

            float deltaX = 0;
            float deltaY = 0;

            if (rotateOnArrows.getValue()) {
                if (isKeyPressed(264))
                    deltaY += 30f;

                if (isKeyPressed(265))
                    deltaY -= 30f;

                if (isKeyPressed(262))
                    deltaX += 30f;

                if (isKeyPressed(263))
                    deltaX -= 30f;

                if (deltaX != 0 || deltaY != 0)
                    mc.player.changeLookDirection(deltaX, deltaY);
            }

            if (sneak.getValue())
                mc.options.sneakKey.setPressed(isKeyPressed(InputUtil.fromTranslationKey(mc.options.sneakKey.getBoundKeyTranslationKey()).getCode()));
        }
    }

    @EventHandler
    public void onClickSlot(EventClickSlot e) {
        if (clickBypass.is(Bypass.DisableClicks) && (MovementUtility.isMoving() || mc.options.jumpKey.isPressed()))
            e.cancel();
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send e) {
        if (clickBypass.is(Bypass.FunTime) && e.getPacket() instanceof ClickSlotC2SPacket click && MovementUtility.isMoving() && !pause.get()) {
            storedClicks.add(click);
            e.cancel();
        }
    }

    private void flushStored() {
        pause.set(true);
        while (!storedClicks.isEmpty())
            sendPacket(storedClicks.poll());
        pause.set(false);
    }

    @Override
    public void onDisable() {
        storedClicks.clear();
        pendingFlush = false;
        wasInventoryOpen = false;
    }

    private enum Bypass {
        None, FunTime, DisableClicks
    }
}
