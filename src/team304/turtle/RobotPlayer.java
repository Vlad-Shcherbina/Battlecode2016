package team304.turtle;

import battlecode.common.*;
import team304.Utils;

import java.util.ArrayList;
import java.util.Random;


@SuppressWarnings("unused")
public class RobotPlayer {
    static Random rand;
    static RobotController rc;

    static Direction[] ALL_DIRECTIONS = Direction.values();

    final static int CENTER_POS = 0;
    final static int TARGET = 1;

    static int packPositionSignal(MapLocation loc) {
        int dx = loc.x - rc.getLocation().x;
        int dy = loc.y - rc.getLocation().y;
        return (dx + 100) + (dy + 100) * 200;
    }
    static MapLocation decodePositionSignal(Signal signal) {
        int d = signal.getMessage()[1];
        int dx = d % 200 - 100;
        int dy = d / 200 - 100;
        return signal.getLocation().add(dx, dy);
    }

    public static void run(RobotController rc_) {
        rc = rc_;
        rand = new Random(rc.getID());
        currentRound = rc.getRoundNum();
        center = rc.getLocation();

        try {
            if (rc.getType() == RobotType.ARCHON) {
                archonRun();
            } else if (rc.getType() == RobotType.TURRET || rc.getType() == RobotType.TTM) {
                turretRun();
            } else if (rc.getType() == RobotType.SCOUT) {
                scoutRun();
            } else if (rc.getType() == RobotType.SOLDIER) {
                soldierRun();
            } else if (rc.getType() == RobotType.GUARD) {
                guardRun();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    static ArrayList<Target> targets = new ArrayList();

    static void yield() throws Exception {
        Clock.yield();
        repairedThisTurn = false;
        currentRound++;
        int t = rc.getRoundNum();
//        Utils.check(currentRound == t, "thinking for too long: " + currentRound + " " + t);
        //if (currentRound != t)
        //    System.out.println("thinking for too long");

        int n = 0;
        int sx = 0;
        int sy = 0;

        targets.clear();

        for (Signal signal : rc.emptySignalQueue()) {
            if (signal.getTeam() == rc.getTeam()) {
                int[] data = signal.getMessage();
                if (data != null) {
                    if (data[0] == CENTER_POS) {
                        MapLocation loc = decodePositionSignal(signal);
                        n++;
                        sx += loc.x;
                        sy += loc.y;
                    } else if (data[0] == TARGET) {
                        if (rc.getType() == RobotType.TURRET) {
                            Target target = new Target();
                            target.location = decodePositionSignal(signal);
                            target.type = null;
                            targets.add(target);
                        }
                    }
                }
            }
        }
        if (rc.getType() == RobotType.ARCHON) {
            MapLocation c = findCenterPos();
            n++;
            sx += c.x;
            sy += c.y;
        }

        // inertia
        sx += center.x * n;
        sy += center.y * n;
        n += n;

        if (n > 0) {
            int M = 100000;
            center = new MapLocation((sx + n / 2 + M * n) / n - M, (sy + n / 2 + M * n) / n - M);
        }
    }

    static MapLocation center;
    static int currentRound = 0;

    static void scoutRun() throws Exception {
        int lastSent = 100;
        while (true) {
            rc.setIndicatorLine(rc.getLocation(), center, 0, 0, 0);
            if (rc.isCoreReady()) {
                Direction dir = findMoveDir(0.1);
                if (dir != null && rc.canMove(dir)) {
                    rc.move(dir);
                }
            }

            MapLocation c = findCenterPos();
            lastSent++;
            if (!c.equals(rc.getLocation()) && lastSent > 15) {
                lastSent = 0;
                rc.broadcastMessageSignal(
                        CENTER_POS,
                        packPositionSignal(findCenterPos()),
                        (2 + rand.nextInt(2)) * rc.getType().sensorRadiusSquared);
            }

            int cnt = 0;
            for (RobotInfo target : rc.senseHostileRobots(rc.getLocation(), -1)) {
                rc.broadcastMessageSignal(
                        TARGET,
                        packPositionSignal(target.location),
                        RobotType.SOLDIER.sensorRadiusSquared);
                cnt++;
                if (cnt >= 5)
                    break;
            }

            yield();
        }
    }

    static void archonRun() throws Exception {
        yield();
        while (true) {
            rc.setIndicatorLine(rc.getLocation(), center, 0, 0, 0);

            int numZombies = 0;
            int numOpp = 0;
            int numScouts = 0;
            int numGuards = 0;
            int numSoldiers = 0;
            int numTurrets = 0;
            for (RobotInfo enemy : rc.senseHostileRobots(rc.getLocation(), -1)) {
                if (enemy.type.isZombie) {
                    numZombies++;
                    if (enemy.type == RobotType.FASTZOMBIE)
                        numZombies++;
                    if (enemy.type == RobotType.BIGZOMBIE)
                        numZombies += 7;
                } else
                    numOpp++;
            }
            for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
                if (ally.type == RobotType.SCOUT)
                    numScouts++;
                else if (ally.type == RobotType.SOLDIER)
                    numSoldiers++;
                else if (ally.type == RobotType.GUARD)
                    numGuards++;
                else if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM)
                    numTurrets++;
            }

            boolean needGuard = numGuards * 3 < numZombies;
            boolean needSoldier = numSoldiers + numGuards < 1;
            boolean needScout = numScouts * 7 < numTurrets;

            if (rc.isCoreReady()) {
                if (needGuard && tryBuild(RobotType.GUARD))
                    ;
                if (needSoldier && tryBuild(RobotType.SOLDIER))
                    ;
                if (needScout && tryBuild(RobotType.SCOUT))
                    ;
                if (rand.nextInt(5) == 0 && tryBuild(RobotType.TURRET))
                    ;
            }

            rc.broadcastMessageSignal(
                    CENTER_POS,
                    packPositionSignal(findCenterPos()),
                    (2 + rand.nextInt(2) * rand.nextInt(6) * rand.nextInt(6)) * rc.getType().sensorRadiusSquared);

            if (rc.isCoreReady()) {
                if (center.distanceSquaredTo(rc.getLocation()) > 0) {
                    Direction dir = findMoveDir(0.05);
                    if (dir != null && rc.canMove(dir)) {
                        rc.move(dir);
                    }
                }
            }

            clearRubble();

            doRepairs();

            yield();
        }
    }

    static void clearRubble() throws Exception {
        if (!rc.isCoreReady())
            return;
        for (Direction dir : ALL_DIRECTIONS) {
            int dot = dir.dx * (center.x - rc.getLocation().x) +
                      dir.dy * (center.y - rc.getLocation().y);
            if (rc.senseRubble(rc.getLocation().add(dir)) >= GameConstants.RUBBLE_SLOW_THRESH &&
                (dot >= 0 || rand.nextInt(12) == 0 ||
                 rc.senseNearbyRobots(rc.getLocation().add(dir), 0, rc.getTeam()).length > 0 )) {
                rc.clearRubble(dir);
                break;
            }

        }
    }

    static void turretRun() throws Exception {
        while (true) {
//            rc.setIndicatorLine(rc.getLocation(), center, 0, 0, 0);

            if (rc.getType() == RobotType.TURRET) {
                double bestPriority = -1;
                Target bestTarget = null;
                for (RobotInfo enemy : rc.senseHostileRobots(rc.getLocation(), -1)) {
                    if (rc.getLocation().distanceSquaredTo(enemy.location) >=
                            GameConstants.TURRET_MINIMUM_RANGE) {
                        Target t = new Target(enemy);
                        if (t.priority() > bestPriority) {
                            bestPriority = t.priority();
                            bestTarget = t;
                        }
                    }
                }
                for (Target t : targets) {
                    int d = t.location.distanceSquaredTo(rc.getLocation());
                    if (d < GameConstants.TURRET_MINIMUM_RANGE ||
                        d > RobotType.TURRET.attackRadiusSquared)
                        continue;
                    if (t.priority() > bestPriority) {
                        bestPriority = t.priority();
                        bestTarget = t;
                    }
                }

                if (bestTarget != null) {
                    if (rc.isWeaponReady()) {
                        rc.attackLocation(bestTarget.location);
                    } else {
                        yield();
                        continue;
                    }
                }
            }

            if (!rc.isCoreReady()) {
                yield();
                continue;
            }

            if (rc.getType() == RobotType.TTM) {
                Direction dir;
                dir = findMoveDir(0.2);
                if (dir != null) {
                    rc.move(dir);
                    while (!rc.isCoreReady())
                        yield();
                    if (rc.senseNearbyRobots(8, rc.getTeam()).length >= 4)
                        rc.unpack();
                }
            } else {
                Direction dir;
                if (rc.senseNearbyRobots(8, rc.getTeam()).length < 4)
                    dir = findMoveDir(0.05);
                else
                    dir = findMoveDir(0.005);
                if (dir != null && rc.isCoreReady())
                    rc.pack();
            }

            yield();
        }
    }

    static void soldierRun() throws Exception {
        while (true) {

            double bestPriority = -1;
            Target bestTarget = null;
            for (RobotInfo enemy : rc.senseHostileRobots(rc.getLocation(), rc.getType().attackRadiusSquared)) {
                Target t = new Target(enemy);
                if (t.priority() > bestPriority) {
                    bestPriority = t.priority();
                    bestTarget = t;
                }
            }

            if (bestTarget != null) {
                if (rc.isWeaponReady()) {
                    rc.attackLocation(bestTarget.location);
                } else {
                    yield();
                    continue;
                }
            }

            if (rc.isCoreReady()) {
                if (center.distanceSquaredTo(rc.getLocation()) > 0) {
                    Direction dir = findMoveDir(0.08);
                    if (dir != null && rc.canMove(dir)) {
                        rc.move(dir);
                    }
                }
            }

            clearRubble();

            yield();
        }
    }

    static void guardRun() throws Exception {
        while (true) {
            double bestPriority = -1;
            Target bestTarget = null;
            for (RobotInfo enemy : rc.senseHostileRobots(rc.getLocation(), -1)) {
                int d = rc.getLocation().distanceSquaredTo(enemy.location);
                Target t = new Target(enemy);
                double pri = t.priority();
                if (enemy.type.isZombie)
                    pri += 1000;
                if (d <= rc.getType().attackRadiusSquared)
                    pri += 500;
                if (pri > bestPriority) {
                    bestPriority = pri;
                    bestTarget = t;
                }
            }

            if (bestTarget != null) {
                if (rc.canAttackLocation(bestTarget.location)) {
                    if (!rc.isWeaponReady()) {
                        yield();
                        continue;
                    }
                    rc.attackLocation(bestTarget.location);
                } else {
                    if (!rc.isCoreReady()) {
                        yield();
                        continue;
                    }
                    Direction bestDir = Direction.EAST;
                    int bestDist = 1000;
                    for (Direction dir : ALL_DIRECTIONS) {
                        if (rc.canMove(dir)) {
                            int d = rc.getLocation().add(dir).distanceSquaredTo(bestTarget.location);
                            if (d < bestDist) {
                                bestDir = dir;
                                bestDist = d;
                            }
                        }
                    }
                    if (rc.isCoreReady() && rc.canMove(bestDir))
                        rc.move(bestDir);
                }
            }

            if (rc.isCoreReady()) {
                if (center.distanceSquaredTo(rc.getLocation()) > 0) {
                    Direction dir = findMoveDir(0.1);
                    if (dir != null && rc.canMove(dir)) {
                        rc.move(dir);
                    }
                }
            }

            clearRubble();

            yield();
        }
    }

    static double robotWeight(RobotType type) {
        switch (type) {
            case ARCHON: return 1.6;
            case TURRET: return 1.0;
            case TTM: return 1.0;
            default:
                return 0.9;
        }
    }

    static Direction findMoveDir(double multiplier) throws Exception {
        RobotInfo[] allies = rc.senseNearbyRobots(2, rc.getTeam());
        boolean obstructed[][] = new boolean[3][3];

        double grad_x = rc.getLocation().x - center.x + 0.1 * (rand.nextInt(8) - 3.5);
        double grad_y = rc.getLocation().y - center.y + 0.1 * (rand.nextInt(8) - 3.5);
        double d = grad_x * grad_x + grad_y * grad_y;
        if (d > 0) {
            d = 1.0 / Math.sqrt(d);
            grad_x *= d;
            grad_y *= d;
        }

        double partsPressure = Math.max(rc.getTeamParts() - 125, 0) / 125.0;
        double weight = robotWeight(rc.getType()) * (8 - 1.3 - Math.pow(partsPressure, 1.2));
        double fx = 0;
        double fy = 0;
        int x = rc.getLocation().x;
        int y = rc.getLocation().y;
        for (RobotInfo ally : allies) {
            double dx = ally.location.x - x;
            double dy = ally.location.y - y;
            double dd = dx == 0 || dy == 0 ? 1.0 : 0.707;
            weight -= robotWeight(ally.type);
            fx -= dx * dd;
            fy -= dy * dd;
            obstructed[ally.location.x - x + 1][ally.location.y - y + 1] = true;
        }

        if (rc.getType() == RobotType.SCOUT) {
            for (RobotInfo ally : rc.senseNearbyRobots(8, rc.getTeam())) {
                if (ally.type != RobotType.SCOUT)
                    continue;
                double dx = ally.location.x - x;
                double dy = ally.location.y - y;
                double dd = 1.5 / (dx * dx + dy * dy);
                fx -= dx * dd;
                fy -= dy * dd;
            }
        }

        for (Direction dir : ALL_DIRECTIONS) {
            if (!rc.onTheMap(rc.getLocation().add(dir))) {
                weight -= 1.0;
                double dx = dir.dx;
                double dy = dir.dy;
                double dd = dx == 0 || dy == 0 ? 1.0 : 0.707;
                fx -= dx * dd;
                fy -= dy * dd;
                obstructed[dir.dx + 1][dir.dy + 1] = true;
            }
        }

        fx -= grad_x * weight;
        fy -= grad_y * weight;

        rc.setIndicatorString(0, fx + " " + fy);

        for (int i = 0; i < 8; i++) {
            Direction dir = ALL_DIRECTIONS[rand.nextInt(8)];
            if (rc.getType() == RobotType.TURRET) {
                if (obstructed[dir.dx + 1][dir.dy + 1] ||
                    rc.senseRubble(rc.getLocation().add(dir)) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH)
                    continue;
            } else {
                if (!rc.canMove(dir))
                    continue;
            }
            double dot = fx * dir.dx + fy * dir.dy;
            if (rand.nextDouble() < dot * multiplier) {
                return dir;
            }
        }
        return null;
    }

    static MapLocation findCenterPos() throws Exception {
        int r = 1;
        while ((r + 1) * (r + 1) <= rc.getType().sensorRadiusSquared)
            r++;
        int x = rc.getLocation().x;
        int y = rc.getLocation().y;
        for (Direction dir : ALL_DIRECTIONS) {
            if (dir.dx != 0 && dir.dy != 0)
                continue;
            int t = r;
            while (true) {
                if (rc.onTheMap(rc.getLocation().add(dir, t))) {
                    break;
                } else {
                    t--;
                    if (dir.dx != 0) {
                        x = rc.getLocation().x + t * dir.dx;
                    }
                    else {
                        y = rc.getLocation().y + t * dir.dy;
                    }
                }
            }
        }
        return new MapLocation(x, y);
    }

    static boolean tryBuild(RobotType type) throws Exception {
        if (!rc.isCoreReady())
            return false;
        if (!rc.hasBuildRequirements(type))
            return false;

        Direction dir = ALL_DIRECTIONS[rand.nextInt(8)];
        for (int i = 0; i < 8; i++, dir = dir.rotateLeft()) {
            MapLocation pos = rc.getLocation().add(dir);
            if (!rc.canBuild(dir, type))
                continue;
            if (type == RobotType.TURRET && rc.senseRubble(pos) >= GameConstants.RUBBLE_SLOW_THRESH)
                continue;
            rc.build(dir, type);
            return true;
        }
        return false;
    }

    static class Target {
        MapLocation location;
        RobotType type;
        double health;

        Target() {}
        Target(RobotInfo ri) {
            location = ri.location;
            type = ri.type;
            health = ri.health;
        }

        double priority() {
            if (type == null)
                return 0.01;
            if (type == RobotType.SCOUT)
                return 0.1;
            if (type.attackDelay <= 0)
                return 0.005;
            return type.attackPower / (type.attackDelay * Math.max(health, 1.0));
        }
    }

    static void doRepairs() throws Exception {
        if (repairedThisTurn)
            return;

        MapLocation repLoc = null;
        double minHp = 10000;
        for (RobotInfo ally : rc.senseNearbyRobots(RobotType.ARCHON.attackRadiusSquared, rc.getTeam())) {
            if (ally.health >= ally.maxHealth || ally.type == RobotType.ARCHON)
                continue;
            int d = Utils.dist(rc.getLocation(), ally);
            if (d <= rc.getType().attackRadiusSquared) {
                if (ally.health < minHp) {
                    minHp = ally.health;
                    repLoc = ally.location;
                }
            }
        }
        if (repLoc != null) {
            repairedThisTurn = true;
            rc.repair(repLoc);
        }
    }

    static boolean repairedThisTurn = false;
}
