package org.mrqendyxz.guardianv3.Managers;

import org.mrqendyxz.guardianv3.Checks.Check;
import org.mrqendyxz.guardianv3.Checks.Combat.*;
import org.mrqendyxz.guardianv3.Checks.Movement.*;
import org.mrqendyxz.guardianv3.Checks.World.*;
import java.util.List;

public final class CheckRegistry {
    public static List<Check> create() {
        return List.of(
                new org.mrqendyxz.guardianv3.Checks.Packet.BadPacketsCheck(),
                new FlyCheck(),
                new SpeedCheck(),
                new WaterSpeedCheck(),
                new AirJumpCheck(),
                new AutoClickerCheck(),
                new BlinkCheck(),
                new BoatFlyCheck(),
                new CrystalAuraCheck(),
                new ElytraFlyCheck(),
                new FastBreakCheck(),
                new FastPlaceCheck(),
                new HighJumpCheck(),
                new HitBoxCheck(),
                new JesusCheck(),
                new KillAuraCheck(),
                new LongJumpCheck(),
                new NoClipCheck(),
                new NoFallCheck(),
                new NoSlowCheck(),
                new NoWebCheck(),
                new ReachCheck(),
                new ScaffoldCheck(),
                new ShieldAttackCheck(),
                new StepCheck(),
                new StrafeCheck(),
                new TimerCheck(),
                new VelocityCheck(),
                new WallClimbCheck(),
                new WallHitCheck()
        );
    }
}
