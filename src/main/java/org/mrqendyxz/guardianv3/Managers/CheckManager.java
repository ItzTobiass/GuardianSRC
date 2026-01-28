package org.mrqendyxz.guardianv3.Managers;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserConnectEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.mrqendyxz.guardianv3.Checks.Combat.*;
import org.mrqendyxz.guardianv3.Checks.Movement.*;
import org.mrqendyxz.guardianv3.Checks.World.*;
import org.mrqendyxz.guardianv3.Guardianv3;
import org.mrqendyxz.guardianv3.Utils.ClientBrandListener;

public class CheckManager implements PacketListener, Listener {

    private final KillAuraCheck killAuraCheck = new KillAuraCheck();
    private final ReachCheck reachCheck = new ReachCheck();
    private final HitBoxCheck hitBoxCheck = new HitBoxCheck();
    private final AutoClickerCheck autoClickerCheck = new AutoClickerCheck();
    private final ShieldAttackCheck shieldAttackCheck = new ShieldAttackCheck();

    private final BoatFlyCheck boatFlyCheck = new BoatFlyCheck();
    private final ClickTPCheck clickTPCheck = new ClickTPCheck();
    private final ElytraFlyCheck elytraFlyCheck = new ElytraFlyCheck();
    private final FlyCheck flyCheck = new FlyCheck();
    private final JesusCheck jesusCheck = new JesusCheck();
    private final LongJumpCheck longJumpCheck = new LongJumpCheck();
    private final NoFallCheck noFallCheck = new NoFallCheck();
    private final NoSlowCheck noSlowCheck = new NoSlowCheck();
    private final SpeedCheck speedCheck = new SpeedCheck();
    private final StrafeCheck strafeCheck = new StrafeCheck();
    private final WallClimbCheck wallClimbCheck = new WallClimbCheck();
    private final StepCheck stepCheck = new StepCheck();
    private final NoClipCheck noClipCheck = new NoClipCheck();
    private final WallHitCheck wallHitCheck = new WallHitCheck();
    private final BlinkCheck blinkCheck = new BlinkCheck();
    private final TimerCheck timerCheck = new TimerCheck();

    private final FastBreakCheck fastBreakCheck = new FastBreakCheck();
    private final FastPlaceCheck fastPlaceCheck = new FastPlaceCheck();
    private final ScaffoldCheck scaffoldCheck = new ScaffoldCheck();

    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.NORMAL);
        Bukkit.getPluginManager().registerEvents(this, Guardianv3.getInstance());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        clickTPCheck.handleTeleport(event.getPlayer().getUniqueId());
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        ClientBrandListener.handle(event);

        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();

        if (player.isOp() || player.hasPermission("guardian.bypass")) return;

        killAuraCheck.handle(event);
        reachCheck.handle(event);
        hitBoxCheck.handle(event);
        autoClickerCheck.handle(event);
        shieldAttackCheck.handle(event);

        boatFlyCheck.handle(event);
        clickTPCheck.handle(event);
        elytraFlyCheck.handle(event);
        flyCheck.handle(event);
        jesusCheck.handle(event);
        longJumpCheck.handle(event);
        noFallCheck.handle(event);
        noSlowCheck.handle(event);
        speedCheck.handle(event);
        strafeCheck.handle(event);
        wallClimbCheck.handle(event);
        stepCheck.handle(event);
        noClipCheck.handle(event);
        wallHitCheck.handle(event);
        blinkCheck.handle(event);
        timerCheck.handle(event);

        fastBreakCheck.handle(event);
        fastPlaceCheck.handle(event);
        scaffoldCheck.handle(event);
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {}
    @Override
    public void onUserConnect(UserConnectEvent event) {}
    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {}
}