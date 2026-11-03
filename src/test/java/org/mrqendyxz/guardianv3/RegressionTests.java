package org.mrqendyxz.guardianv3;

import org.mrqendyxz.guardianv3.Utils.*;
import java.util.UUID;
import java.util.concurrent.*;

public final class RegressionTests {
    private static int assertions;
    private static void equal(double expected, double actual, String message) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1e-9) throw new AssertionError(message + ": " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(BadPacketRules.position(0, -64, 0) == null, "negative world height is valid");
        check(BadPacketRules.position(30_000_000, 2000, -30_000_000) == null, "boundary and above build height are valid");
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 30_000_001, -30_000_001}) {
            check(BadPacketRules.position(value, 0, 0) != null, "invalid X");
            check(BadPacketRules.position(0, value, 0) != null, "invalid Y");
            check(BadPacketRules.position(0, 0, value) != null, "invalid Z");
        }
        for (float yaw : new float[]{0, -720, 720, Float.MAX_VALUE}) {
            for (float pitch : new float[]{-90, 0, 90}) {
                check(BadPacketRules.rotation(yaw, pitch, true) == null, "finite unbounded yaw and legal pitch");
            }
        }
        for (float value : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            check(BadPacketRules.rotation(value, 0, true) != null, "nonfinite yaw");
            check(BadPacketRules.rotation(0, value, true) != null, "nonfinite pitch");
        }
        check(BadPacketRules.rotation(0, 91, true) != null, "pitch above player range");
        check(BadPacketRules.rotation(0, -91, true) != null, "pitch below player range");
        check(BadPacketRules.rotation(0, 180, false) == null, "vehicle pitch is not player pitch");
        for (int slot = 0; slot <= 8; slot++) check(BadPacketRules.hotbar(slot) == null, "valid hotbar slot");
        check(BadPacketRules.hotbar(-1) != null, "negative hotbar slot");
        check(BadPacketRules.hotbar(9) != null, "slot outside hotbar");
        check(BadPacketRules.attack(42, 42) != null, "self attack");
        check(BadPacketRules.attack(43, 42) == null, "other entity attack");

        PacketMotion motion = new PacketMotion(999, 80, 999, 0, 0);
        motion.update(false, 0, 0, 0, true, 90, 20);
        equal(999, motion.x(), "rotation-only preserves position");
        motion.update(true, 10, 65, 20, false, 0, 0);
        equal(0, motion.dx(), "first position does not use the server position");
        equal(90, motion.yaw(), "position-only preserves yaw");
        motion.update(true, 10.3, 65.42, 19.8, true, 100, 30);
        equal(.3, motion.dx(), "consecutive packet X delta");
        equal(.42, motion.dy(), "consecutive packet Y delta");
        equal(-.2, motion.dz(), "consecutive packet Z delta");
        motion.update(false, 0, 0, 0, false, 0, 0);
        equal(0, motion.dx(), "ground-only packet does not repeat displacement");
        equal(65.42, motion.y(), "ground-only packet preserves Y");
        PacketMotion rejoin = new PacketMotion(0, 0, 0, 0, 0);
        rejoin.update(true, -1000, -64, -200, true, 0, 0);
        equal(0, rejoin.dy(), "new session has no old delta");

        equal(2.7, ReachGeometry.distance(0, 1.62, 0, 2.7, 0, -.3, 3.3, 1.8, .3), "three-block hit has no feet-height penalty");
        equal(0, ReachGeometry.distance(3, 1, 0, 2.7, 0, -.3, 3.3, 1.8, .3), "inside AABB");
        equal(2.2, ReachGeometry.distance(3, 4, 0, 2.7, 0, -.3, 3.3, 1.8, .3), "vertical distance to top face");
        check(ReachGeometry.distance(0, 1.62, 0, 4.7, 0, -.3, 5.3, 1.8, .3) > 3.1, "distant hit exceeds reach");

        PacketRateWindow rate = new PacketRateWindow();
        for (int i = 0; i < 200; i++) check(!rate.record(i * 50L, 24), "20 TPS is valid");
        rate = new PacketRateWindow();
        for (int i = 0; i < 10; i++) check(!rate.record(i * 500L, 24), "slow packets");
        int exceeded = 0;
        for (int i = 0; i < 40; i++) if (rate.record(10_000 + i * 20L, 24)) exceeded++;
        equal(1, exceeded, "slow traffic cannot bank burst credit; once per window");
        check(!rate.record(11_000, 24), "new fixed window resets count");
        rate = new PacketRateWindow();
        exceeded = 0;
        for (int i = 0; i < 200; i++) if (rate.record(i * 40L, 24)) exceeded++;
        equal(8, exceeded, "sustained 25 TPS detected in each window");

        ViolationLedger ledger = new ViolationLedger();
        UUID id = UUID.randomUUID();
        equal(1, ledger.increment(id, "Speed", 0, 30000), "initial VL");
        equal(2, ledger.increment(id, "Speed", 29999, 30000), "before decay boundary");
        equal(2, ledger.increment(id, "Speed", 30000, 30000), "decay boundary");
        equal(1, ledger.increment(id, "Reach", 30000, 30000), "checks are independent");
        equal(1, ledger.increment(id, "Speed", 120000, 30000), "idle VL expires");
        ledger.cleanup(id);
        equal(1, ledger.increment(id, "Speed", 120001, 30000), "rejoin resets levels");
        ledger.clear();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 8; i++) futures.add(pool.submit(() -> {
                for (int j = 0; j < 1000; j++) ledger.increment(id, "Speed", 0, 30000);
            }));
            for (Future<?> future : futures) future.get();
        } finally { pool.shutdownNow(); }
        equal(8001, ledger.increment(id, "Speed", 0, 30000), "concurrent increments are not lost");
        PacketMotion teleport = new PacketMotion(10, 64, 10, 0, 0);
        teleport.update(true, 10, 64, 10, true, 0, 0);
        teleport.update(true, 15, 64, 10, false, 0, 0);
        equal(5, teleport.dx(), "first move after an authoritative teleport remains checked");

        FlightModel fly = new FlightModel();
        check(!fly.record(0, true, 0, .42, 0, 0, 3), "standing on a collision shape");
        double vertical = .42;
        for (int i = 1; i <= 15; i++) {
            check(!fly.record(vertical, false, 0, .42, 0, i * 50L, 3), "vanilla jump/fall trajectory");
            vertical = (vertical - .08) * .98;
        }
        fly.reset();
        int flags = 0;
        for (int i = 0; i < 5; i++) if (fly.record(0, false, 0, .42, 0, i * 50L, 3)) flags++;
        check(flags > 0, "hover with spoofed onGround cannot bypass collision-based flight detection");
        fly.reset();
        check(fly.record(1, false, 0, .42, 0, 0, 3), "large upward flight reacts immediately");
        fly.reset();
        check(!fly.record(1, false, 0, .42, 1, 0, 3), "real server impulse is allowed");
        fly.reset();
        fly.record(-1.2, false, 0, .42, 0, 0, 3);
        fly.record(-.8, true, 1, .42, 0, 50, 3);
        check(!fly.record(1.2, false, 0, .42, 0, 100, 3), "slime bounce from observed falling speed");
        fly.reset();
        fly.record(.42, false, 0, .42, 0, 0, 3);
        fly.record(0, false, 0, .42, 0, 50, 3);
        check(!fly.record(-.0784, false, 0, .42, 0, 100, 3), "ceiling collision followed by falling");
        fly.reset();
        int fakelagFlags = 0;
        for (int i = 0; i < 5; i++) if (fly.record(-.001, false, 0, .42, 0, i * 300L, 3)) fakelagFlags++;
        check(fakelagFlags > 0, "fakelag slow glide cannot bypass FlightModel");

        MovementEvidence speed = new MovementEvidence();
        check(!speed.record(true, false, 3), "first suspicious speed sample");
        check(!speed.record(true, false, 3), "second suspicious speed sample");
        check(speed.record(true, false, 3), "third suspicious speed sample triggers correction");
        speed.reset();
        check(speed.record(true, true, 3), "severe speed is immediate even after reset");
        speed.reset();
        speed.record(true, false, 3);
        check(!speed.clean(), "suspect movement cannot replace the safe anchor");
        speed.record(false, false, 3);
        check(speed.clean(), "valid motion decays evidence");

        var order = new java.util.ArrayList<String>();
        check(SetbackExecutor.execute(() -> order.add("alert"), () -> true,
                () -> { order.add("teleport"); return true; }, () -> order.add("stop")), "successful setback");
        check(order.equals(java.util.List.of("alert", "teleport", "stop")), "alert then teleport then velocity reset in the same call");
        order.clear();
        check(!SetbackExecutor.execute(() -> order.add("alert"), () -> true,
                () -> { order.add("cancelled"); return false; }, () -> order.add("stop")), "cancelled teleport fails explicitly");
        check(order.equals(java.util.List.of("alert", "cancelled")), "cancelled teleport leaves velocity alone");
        order.clear();
        check(!SetbackExecutor.execute(() -> order.add("kick"), () -> false,
                () -> { order.add("teleport"); return true; }, () -> order.add("stop")), "punishment may disconnect during alert");
        check(order.equals(java.util.List.of("kick")), "no movement changes after disconnect");

        check(MovementEnvironment.supports(.5, 64, .5, 0, 1, 64, 0, 1), "full block floor");
        check(MovementEnvironment.supports(1.2995, 64, .5, 0, 1, 64, 0, 1), "sneaking at extreme block edge is supported");
        check(MovementEnvironment.supports(.5, 64.5, .5, 0, 1, 64.5, 0, 1), "slab collision top");
        check(MovementEnvironment.supports(.5, 64.0625, .5, 0, 1, 64.0625, 0, 1), "carpet collision top");
        check(!MovementEnvironment.supports(.5, 64.7, .5, 0, 1, 64, 0, 1), "0.7-block hovering is not ground");
        check(!MovementEnvironment.supports(1.4, 64, .5, 0, 1, 64, 0, 1), "nearby floor is not support beneath player");
        check(!MovementEnvironment.supports(.5, 63.8, .5, 0, 1, 64, 0, 1), "inside a block is not its top surface");

        TeleportTracker acknowledgements = new TeleportTracker();
        acknowledgements.sent(id, 10);
        check(acknowledgements.pending(id), "movement waits for teleport acknowledgement");
        check(!acknowledgements.confirm(id, 9), "wrong teleport id is ignored");
        acknowledgements.sent(id, 11);
        check(!acknowledgements.confirm(id, 10), "late previous acknowledgement does not release new teleport");
        check(acknowledgements.confirm(id, 11), "exact teleport id unlocks movement");
        check(!acknowledgements.pending(id), "no extra timed bypass after acknowledgement");
        acknowledgements.sent(id, 12);
        acknowledgements.cleanup(id);
        check(!acknowledgements.pending(id), "disconnect clears pending teleport");
        equal(2, RotationMath.deltaDegrees(-179, 179), "yaw wraps across 180 degrees");
        equal(-2, RotationMath.deltaDegrees(179, -179), "yaw wraps in reverse");
        check(Float.isFinite(RotationMath.deltaDegrees(Float.MAX_VALUE, -Float.MAX_VALUE)), "extreme yaw cannot overflow or hang");
        check(Math.abs(RotationMath.deltaDegrees(Float.MAX_VALUE, 0)) <= 180, "extreme finite yaw remains bounded");
        equal(0, RotationMath.deltaDegrees(Float.NaN, 0), "nonfinite yaw is rejected");

        SustainedRate sustainable = new SustainedRate();
        int rateFlags = 0;
        for (int i = 0; i < 300; i++) if (sustainable.record(i * 50L, 24, 3)) rateFlags++;
        equal(0, rateFlags, "sustained 20 TPS is legitimate");
        sustainable = new SustainedRate(); rateFlags = 0;
        for (int i = 0; i < 240; i++) if (sustainable.record(i * 25L, 24, 3)) rateFlags++;
        check(rateFlags > 0, "sustained timer speed is detected");
        sustainable = new SustainedRate(); rateFlags = 0;
        for (int i = 0; i < 60; i++) if (sustainable.record(0, 24, 3)) rateFlags++;
        for (int i = 1; i < 100; i++) if (sustainable.record(1000 + i * 50L, 24, 3)) rateFlags++;
        equal(0, rateFlags, "one coalesced TCP burst is not repeated speedup");
        sustainable = new SustainedRate(); rateFlags = 0;
        for (int batch = 0; batch < 8; batch++) for (int i = 0; i < 30; i++) {
            if (sustainable.record(batch * 1500L, 24, 3)) rateFlags++;
        }
        equal(0, rateFlags, "30 packets arriving every 1.5 seconds are still 20 TPS");
        check(!AirStrafeModel.excessive(.3, 0, .3, 0, .1), "camera rotation with unchanged velocity is legal");
        check(!AirStrafeModel.excessive(.28, .02, .3, 0, .1), "ordinary air steering is legal");
        check(!AirStrafeModel.excessive(0, 0, .3, 0, .1), "hitting a wall is deceleration, not strafe acceleration");
        check(AirStrafeModel.excessive(.6, .4, .2, 0, .1), "excessive mid-air acceleration");
        equal(1, ConfigDurations.seconds(Long.MIN_VALUE), "negative durations clamped");
        equal(86400, ConfigDurations.seconds(Long.MAX_VALUE), "huge durations cannot overflow multiplication");
        check("vanilla".equals(BrandParser.parse(new byte[]{7, 'v', 'a', 'n', 'i', 'l', 'l', 'a'})), "normal client brand");
        check(BrandParser.parse(new byte[]{(byte) 128}) == null, "truncated brand VarInt");
        check(BrandParser.parse(new byte[]{2, (byte) 0xc3, 0x28}) == null, "malformed brand UTF-8");
        byte[] longBrand = new byte[132]; longBrand[0] = (byte) 130; longBrand[1] = 1;
        java.util.Arrays.fill(longBrand, 2, longBrand.length, (byte) 'a');
        equal(64, BrandParser.parse(longBrand).length(), "multi-byte length decoded and display bounded");
        String formatted = "evil\n§c&4";
        byte[] raw = formatted.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] brand = new byte[raw.length + 1]; brand[0] = (byte) raw.length;
        System.arraycopy(raw, 0, brand, 1, raw.length);
        check(!BrandParser.parse(brand).contains("§") && !BrandParser.parse(brand).contains("&") && !BrandParser.parse(brand).contains("\n"), "brand cannot inject alert formatting or newlines");
        assertions += RegistryRegression.verify();
        collisionSampling();
        System.out.println("Passed " + assertions + " regression assertions.");
    }
    private static void collisionSampling() {
        boolean[] loaded = {true};
        int[] lookups = {0};
        double[] top = {1};
        UUID worldId = UUID.randomUUID();
        org.bukkit.World world = (org.bukkit.World) java.lang.reflect.Proxy.newProxyInstance(
                RegressionTests.class.getClassLoader(), new Class<?>[]{org.bukkit.World.class}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "getUID" -> worldId;
                case "isChunkLoaded" -> loaded[0];
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "getBlockAt" -> {
                    lookups[0]++;
                    int y = (int) args[1];
                    yield java.lang.reflect.Proxy.newProxyInstance(RegressionTests.class.getClassLoader(),
                            new Class<?>[]{org.bukkit.block.Block.class}, (block, blockMethod, blockArgs) -> {
                        if (!blockMethod.getName().equals("getCollisionShape")) throw new AssertionError(blockMethod);
                        return java.lang.reflect.Proxy.newProxyInstance(RegressionTests.class.getClassLoader(),
                                new Class<?>[]{org.bukkit.util.VoxelShape.class}, (shape, shapeMethod, shapeArgs) -> {
                            if (!shapeMethod.getName().equals("getBoundingBoxes")) throw new AssertionError(shapeMethod);
                            return y == 63 ? java.util.List.of(new org.bukkit.util.BoundingBox(0, 0, 0, 1, top[0], 1)) : java.util.List.of();
                        });
                    });
                }
                default -> throw new AssertionError(method);
            };
        });
        var at = new org.bukkit.Location(world, -10.5, 64, -20.5);
        MovementEnvironment.clearCache();
        check(MovementEnvironment.supported(at), "local block shapes translated at negative world coordinates");
        check(MovementEnvironment.clear(at, 1.8), "standing body above floor is clear");
        int first = lookups[0];
        for (int i = 0; i < 100; i++) {
            check(MovementEnvironment.supported(at), "cached floor support");
            check(MovementEnvironment.clear(at, 1.8), "cached body clearance");
        }
        equal(first, lookups[0], "repeated packets reuse collision shapes without block lookups");
        check(!MovementEnvironment.supported(at.clone().add(0, .7, 0)), "old 0.8-block ground bypass removed");
        check(!MovementEnvironment.clear(at.clone().subtract(0, .5, 0), 1.8), "inside-block anchor is rejected");
        MovementEnvironment.clearCache();
        top[0] = .5;
        check(MovementEnvironment.supported(at.clone().subtract(0, .5, 0)), "actual slab shape support");
        top[0] = 1.5;
        MovementEnvironment.clearCache();
        check(MovementEnvironment.supported(at.clone().add(0, .5, 0)), "tall fence shape support");
        loaded[0] = false;
        first = lookups[0];
        check(!MovementEnvironment.supported(at), "unloaded terrain is not safe support");
        check(!MovementEnvironment.clear(at, 1.8), "unloaded terrain cannot be a setback anchor");
        equal(first, lookups[0], "unloaded chunks are never queried");
        MovementEnvironment.clearCache();
    }

}
