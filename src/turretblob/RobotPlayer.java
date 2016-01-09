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

    static class Comm {
        static void reportTarget(RobotInfo target) throws Exception {
            int dx = target.location.x - rc.getLocation().x;
            int dy = target.location.y - rc.getLocation().y;
            System.out.println("reporting target " + dx + " " + dy);
            rc.broadcastMessageSignal(dx, dy, 2 * rc.getType().sensorRadiusSquared);
        }

        static ArrayList<MapLocation> getTargets() throws Exception {
            ArrayList<MapLocation> result = new ArrayList();
            for (Signal signal : rc.emptySignalQueue()) {
                if (signal.getTeam() == rc.getTeam()) {
                    int[] data = signal.getMessage();
                    if (data != null) {
                        MapLocation pos = signal.getLocation();
                        result.add(pos.add(data[0], data[1]));
                    }
                } else {
                    // TODO: take unit type and uncertainty into account
                    result.add(signal.getLocation());
                }
            }
            return result;
        }
    }

    public static void run(RobotController rc_) {
        rc = rc_;
        rand = new Random(rc.getID());
        try {
            if (rc.getType() == RobotType.ARCHON) {
                archonRun();
            } else if (rc.getType() == RobotType.TURRET) {
                turretRun();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
    }

    static void archonRun() throws Exception {
        while (true) {
            if (rand.nextInt(5) == 0 && tryBuild(RobotType.TURRET))
                continue;

            RobotInfo[] allies = rc.senseNearbyRobots(50, rc.getTeam());
            RobotInfo[] enemies = senseEnemies(50);

            int oldDist = Utils.distToNearest(rc.getLocation(), allies);

            Direction dirToMove = directions[rand.nextInt(8)];
            if (rc.isCoreReady() && rc.canMove(dirToMove)) {
                int newDist = Utils.distToNearest(rc.getLocation().add(dirToMove), allies);

                if (newDist < 10 || newDist <= oldDist) {
                    rc.move(dirToMove);
                    continue;
                }
            }

            int cnt = 0;
            for (RobotInfo enemy : enemies) {
                if (cnt > 10)
                    break;
                Comm.reportTarget(enemy);
            }

            Clock.yield();
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
                Clock.yield();
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
                continue;
            }

            Clock.yield();
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
