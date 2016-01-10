package team304.turretblob;

import battlecode.common.*;
import team304.Utils;

import java.util.ArrayList;
import java.util.Random;

@SuppressWarnings("unused")
public class RobotPlayer {
    static Random rand;
    static RobotController rc;

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

    static class Comm {
        static final int TARGET = 0;

        static final int MAX_DELTA = 10;
        static final int MD2 = MAX_DELTA * 2 + 1;

        static void reportTarget(RobotInfo target) throws Exception {
            int dx = target.location.x - rc.getLocation().x;
            int dy = target.location.y - rc.getLocation().y;
            Utils.check(Math.abs(dx) <= MAX_DELTA);
            Utils.check(Math.abs(dy) <= MAX_DELTA);
            int data = dx + MAX_DELTA +
                      (dy + MAX_DELTA) * MD2;
            rc.setIndicatorLine(rc.getLocation(), target.location, 255, 255, 0);
            rc.broadcastMessageSignal(TARGET, data, 2 * rc.getType().sensorRadiusSquared);
        }

        static void processData() {
            targets.clear();
            for (Signal signal : rc.emptySignalQueue()) {
                if (signal.getTeam() == rc.getTeam()) {
                    int[] data = signal.getMessage();
                    if (data != null) {
                        if (data[0] == TARGET) {
                            MapLocation pos = signal.getLocation();
                            int dx = data[1] % MD2 - MAX_DELTA;
                            int dy = data[1] / MD2 - MAX_DELTA;
                            Target t = new Target();
                            t.location = pos.add(dx, dy);
                            targets.add(t);
                        }
                    }
                } else {
                    // TODO: take unit type and uncertainty into account
                    Target t = new Target();
                    t.location = signal.getLocation();
                    targets.add(t);
                }
            }
        }

        static ArrayList<Target> targets = new ArrayList();

        static ArrayList<Target> getTargets() throws Exception {
            return targets;
        }
    }

    public static void run(RobotController rc_) {
        rc = rc_;
        rand = new Random(rc.getID());
        currentRound = rc.getRoundNum();

        try {
            updateNeighbors();
            if (rc.getType() == RobotType.ARCHON) {
                archonRun();
            } else if (rc.getType() == RobotType.TURRET) {
                turretRun();
            } else if (rc.getType() == RobotType.SCOUT) {
                scoutRun();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    static void yield() throws Exception {
        Clock.yield();
        currentRound++;
        int t = rc.getRoundNum();
        Utils.check(currentRound == t, "thinking for too long: " + currentRound + " " + t);

        updateNeighbors();
        Comm.processData();
        repairedThisTurn = false;
    }

    static int currentRound = 0;
    static boolean repairedThisTurn = false;

    static RobotInfo[] allies;
    static RobotInfo[] enemies;

    static void updateNeighbors() throws Exception {
        int r = rc.getType().sensorRadiusSquared;
        allies = rc.senseNearbyRobots(r, rc.getTeam());
        enemies = senseEnemies(r);
    }

    static void scoutRun() throws Exception {
        while (true) {
            yield();
        }
    }

    static void archonRun() throws Exception {
        System.out.println(rc.getLocation().x + " " + rc.getLocation().y);
        while (true) {
            if (rand.nextInt(10) == 0 &&
                (allies.length > 0 || enemies.length == 0 || rand.nextInt(8) == 0) &&
                tryBuild(RobotType.TURRET)) {
                yield();
                continue;
            }

            doRepairs();
            doSpotting();

            if (rc.isCoreReady()) {
                double baseScore = archonPositionScore(rc.getLocation());
                double bestScore = 0;
                Direction bestDir = null;

                for (Direction dir : Utils.DIRECTIONS) {
                    if (!rc.canMove(dir))
                        continue;
                    MapLocation newLoc = rc.getLocation().add(dir);

                    int newDist = Utils.distToNearest(newLoc, allies);
                    double score = archonPositionScore(newLoc) - baseScore;
                    score += (rand.nextInt(10) - 5) * 0.001;
                    if (dir.isDiagonal())
                        score /= 1.4;

                    if (score > bestScore) {
                        bestScore = score;
                        bestDir = dir;
                    }
                }
                if (bestDir != null) {
                    rc.move(bestDir);
                }
            }

            yield();
        }
    }

    static double archonPositionScore(MapLocation loc) throws Exception {
        double result = -vulnerabilityScore(loc);
        result -= Math.max(8, Utils.distToNearest(loc, allies)) * 10;
        return result;
    }

    static double vulnerabilityScore(MapLocation loc) throws Exception {
        double result = 0;
        for (RobotInfo enemy : enemies) {
            int d2 = Utils.dist(loc, enemy);
            // TODO: take into account turret min range
            RobotType type = enemy.type;
            if (d2 > type.attackRadiusSquared)
                continue;
            if (type.attackDelay <= 0)
                continue;
            // TODO: take into account viper effect when not infected
            result += (1 - 0.005 * d2) * type.attackPower / type.attackDelay;
        }
        return result;
    }

    static void doRepairs() throws Exception {
        if (repairedThisTurn)
            return;

        MapLocation repLoc = null;
        double minHp = 10000;
        for (RobotInfo ally : allies) {
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

    static void doSpotting() throws Exception {
        int cnt = 0;
        for (RobotInfo enemy : enemies) {
            Comm.reportTarget(enemy);
            // to avoid spam
            if (cnt >= 5)
                break;
        }
    }

    static void turretRun() throws Exception {
        int myAttackRange = rc.getType().attackRadiusSquared;

        for ( ; ; yield()) {
            if (!rc.isWeaponReady())
                continue;

            double bestPriority = -1;
            Target bestTarget = null;
            for (RobotInfo enemy : enemies) {
                if (rc.getLocation().distanceSquaredTo(enemy.location) >=
                    GameConstants.TURRET_MINIMUM_RANGE) {
                    Target t = new Target(enemy);
                    if (t.priority() > bestPriority) {
                        bestPriority = t.priority();
                        bestTarget = t;
                    }
                }
            }
            for (Target t : Comm.getTargets()) {
                int d = rc.getLocation().distanceSquaredTo(t.location);
                if (d >= GameConstants.TURRET_MINIMUM_RANGE &&
                    d <= myAttackRange) {
                    if (t.priority() > bestPriority) {
                        bestPriority = t.priority();
                        bestTarget = t;
                    }
                }
            }

            if (bestTarget != null) {
                rc.attackLocation(bestTarget.location);
            }
        }
    }

    static RobotInfo[] senseEnemies(int range) throws Exception {
        RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(range, rc.getTeam().opponent());
        RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(range, Team.ZOMBIE);
        return Utils.concatArrays(enemiesWithinRange, zombiesWithinRange);
    }

    static boolean tryBuild(RobotType type) throws Exception {
        if (!rc.isCoreReady())
            return false;
        if (!rc.hasBuildRequirements(type))
            return false;

        Direction dir = Utils.DIRECTIONS[rand.nextInt(8)];
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
}
