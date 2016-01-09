package turretblob;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Random;

@SuppressWarnings("unused")
public class RobotPlayer {
    static final Direction[] directions = {
            Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
            Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    static Random rand;
    static RobotController rc;

    static boolean knowTopBorder = false;
    static boolean knowBottomBorder = false;
    static boolean knowLeftBorder = false;
    static boolean knowRightBorder = false;
    static int topBorder;
    static int bottomBorder;
    static int leftBorder;
    static int rightBorder;

    static class Comm {
        static final int TARGET = 0;
        static final int SCOUT_REPORT = 1;

        static final int W2 = GameConstants.MAP_MAX_WIDTH * 2;

        static void reportTarget(RobotInfo target) throws Exception {
            int dx = target.location.x - rc.getLocation().x;
            int dy = target.location.y - rc.getLocation().y;
            int data = dx + GameConstants.MAP_MAX_WIDTH +
                      (dy + GameConstants.MAP_MAX_HEIGHT) * W2;
            rc.setIndicatorLine(rc.getLocation(), target.location, 255, 255, 0);
            rc.broadcastMessageSignal(TARGET, data, 2 * rc.getType().sensorRadiusSquared);
        }

        static void reportScoutExistence() throws Exception {
            int d = GameConstants.MAP_MAX_WIDTH * GameConstants.MAP_MAX_WIDTH +
                    GameConstants.MAP_MAX_HEIGHT * GameConstants.MAP_MAX_HEIGHT;
            lastHeardFromScout = currentRound;
            rc.broadcastMessageSignal(SCOUT_REPORT, 42, d);
        }

        static void processData() {
            targets.clear();
            for (Signal signal : rc.emptySignalQueue()) {
                if (signal.getTeam() == rc.getTeam()) {
                    int[] data = signal.getMessage();
                    if (data != null) {
                        if (data[0] == TARGET) {
                            MapLocation pos = signal.getLocation();
                            int dx = data[1] % W2;
                            int dy = data[1] / W2;
                            dx -= GameConstants.MAP_MAX_WIDTH;
                            dy -= GameConstants.MAP_MAX_HEIGHT;
                            targets.add(pos.add(dx, dy));
                        } else if (data[0] == SCOUT_REPORT) {
                            lastHeardFromScout = currentRound;
                        }
                    }
                } else {
                    // TODO: take unit type and uncertainty into account
                    targets.add(signal.getLocation());
                }
            }
        }

        static ArrayList<MapLocation> targets = new ArrayList();
        static int lastHeardFromScout = -1000;
        final static int SCOUT_REPORT_PERIOD = 150;

        static ArrayList<MapLocation> getTargets() throws Exception {
            return targets;
        }
    }

    public static void run(RobotController rc_) {
        rc = rc_;
        rand = new Random(rc.getID());

        try {
            int x = rc.getLocation().x;
            int y = rc.getLocation().y;
            topBorder = y - GameConstants.MAP_MAX_HEIGHT;
            bottomBorder = y + GameConstants.MAP_MAX_HEIGHT;
            leftBorder = x - GameConstants.MAP_MAX_WIDTH;
            rightBorder = x + GameConstants.MAP_MAX_WIDTH;

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

    static void yield() {
        Clock.yield();
        currentRound++;
        Comm.processData();
        repairedThisTurn = false;
    }

    static int currentRound = 0;
    static boolean repairedThisTurn = false;

    static void scoutRun() throws  Exception {
        while (true) {
            if (!rc.isCoreReady()) {
                yield();
                continue;
            }

            if (Comm.lastHeardFromScout < currentRound - Comm.SCOUT_REPORT_PERIOD) {
                Comm.reportScoutExistence();
                yield();
                continue;
            }

            RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().sensorRadiusSquared, rc.getTeam());
            RobotInfo[] enemies = senseEnemies(rc.getType().sensorRadiusSquared);

            int cnt = 0;
            for (RobotInfo enemy : enemies) {
                if (cnt > 10)
                    break;
                Comm.reportTarget(enemy);
            }

            // diagonal
            Direction dir = directions[rand.nextInt(4) * 2 + 1];

            if (rc.isCoreReady() && rc.canMove(dir)) {
                int oldDist = Utils.distToNearest(rc.getLocation(), allies);
                int newDist = Utils.distToNearest(rc.getLocation().add(dir), allies);

                if (newDist < 10 || newDist <= oldDist) {
                    rc.move(dir);
                    continue;
                }

                rc.move(dir);
                yield();
                continue;
            }

            yield();
        }
    }

    static void archonRun() throws Exception {
        System.out.println(rc.getLocation().x + " " + rc.getLocation().y);
        while (true) {
            /*
            // Scout does not seem to be useful
            if (rand.nextInt(50) == 0 &&
                Comm.lastHeardFromScout < currentRound - Comm.SCOUT_REPORT_PERIOD) {
                if (tryBuild(RobotType.SCOUT)) {
                    System.out.println("Building scout");
                    yield();
                    continue;
                }
            }*/

            if (rand.nextInt(15) == 0 && tryBuild(RobotType.TURRET)) {
                yield();
                continue;
            }

            RobotInfo[] allies = rc.senseNearbyRobots(50, rc.getTeam());
            RobotInfo[] enemies = senseEnemies(50);

            if (!repairedThisTurn) {
                for (RobotInfo ally : allies) {
                    if (ally.health >= ally.maxHealth || ally.type == RobotType.ARCHON)
                        continue;
                    int d = Utils.dist(rc.getLocation(), ally);
                    if (d <= rc.getType().attackRadiusSquared) {
                        repairedThisTurn = true;
                        rc.repair(ally.location);
                        break;
                    }
                }
            }

            int cnt = 0;
            for (RobotInfo enemy : enemies) {
                if (cnt > 10)
                    break;
                Comm.reportTarget(enemy);
            }

            int oldDist = Utils.distToNearest(rc.getLocation(), allies);

            Direction dir = directions[rand.nextInt(8)];
            if (rc.isCoreReady() && rc.canMove(dir)) {
                int newDist = Utils.distToNearest(rc.getLocation().add(dir), allies);

                if (newDist < 10 || newDist <= oldDist) {
                    rc.move(dir);
                    continue;
                }
            }

            yield();
        }
    }

    static RobotInfo[] senseEnemies(int range) throws Exception {
        RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(range, rc.getTeam().opponent());
        RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(range, Team.ZOMBIE);
        return Utils.concatArrays(enemiesWithinRange, zombiesWithinRange);
    }

    static void turretRun() throws Exception {
        while (true) {
            if (!rc.isWeaponReady()) {
                yield();
                continue;
            }

            int myAttackRange = rc.getType().attackRadiusSquared;

            MapLocation target = null;
            for (RobotInfo enemy : senseEnemies(myAttackRange)) {
                if (rc.getLocation().distanceSquaredTo(enemy.location) >=
                    GameConstants.TURRET_MINIMUM_RANGE) {
                    target = enemy.location;
                    break;
                }
            }
            for (MapLocation t : Comm.getTargets()) {
                if (target != null)
                    continue;
                int d = rc.getLocation().distanceSquaredTo(t);
                if (d >= GameConstants.TURRET_MINIMUM_RANGE &&
                    d <= myAttackRange) {
                    target = t;
                }
            }

            if (target != null) {
                rc.attackLocation(target);
            }

            yield();
        }
    }

    static boolean tryBuild(RobotType type) throws Exception {
        if (!rc.isCoreReady())
            return false;
        if (!rc.hasBuildRequirements(type))
            return false;

        Direction dir = directions[rand.nextInt(8)];
        for (int i = 0; i < 8; i++) {
            MapLocation pos = rc.getLocation().add(dir);
            if (rc.canBuild(dir, type) &&
                rc.senseRubble(pos) < GameConstants.RUBBLE_SLOW_THRESH) {
                rc.build(dir, type);
                return true;
            } else {
                dir = dir.rotateLeft();
            }
        }
        return false;
    }
}
