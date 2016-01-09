package turretblob;

import battlecode.common.*;

import java.util.Random;

@SuppressWarnings("unused")
public class RobotPlayer {
    static final Direction[] directions = {
            Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
            Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};
    static Random rand;
    static RobotController rc;

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
        while(true) {
            if (rand.nextInt(5) == 0 && tryBuild(RobotType.TURRET))
                continue;

            Direction dirToMove = directions[rand.nextInt(8)];
            if (rc.isCoreReady() && rc.canMove(dirToMove)) {
                rc.move(dirToMove);
                continue;
            }

            Clock.yield();
        }
    }

    static void turretRun() throws Exception {
        while (true) {
            if (!rc.isWeaponReady()) {
                Clock.yield();
                continue;
            }

            int myAttackRange = rc.getType().attackRadiusSquared;
            RobotInfo[] enemiesWithinRange = rc.senseNearbyRobots(myAttackRange, rc.getTeam().opponent());
            RobotInfo[] zombiesWithinRange = rc.senseNearbyRobots(myAttackRange, Team.ZOMBIE);

            RobotInfo[] enemies = concatArrays(enemiesWithinRange, zombiesWithinRange);
            RobotInfo target = null;

            for (RobotInfo enemy : enemies) {
                if (rc.getLocation().distanceSquaredTo(enemy.location) >=
                    GameConstants.TURRET_MINIMUM_RANGE) {
                    target = enemy;
                    break;
                }
            }
            if (target != null) {
                rc.attackLocation(target.location);
                continue;
            }

            Clock.yield();
        }
    }

    static RobotInfo[] concatArrays(RobotInfo a[], RobotInfo b[]) {
        int length = a.length + b.length;
        RobotInfo[] result = new RobotInfo[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    static boolean tryBuild(RobotType t) throws Exception {
        if (!rc.isCoreReady())
            return false;
        if (!rc.hasBuildRequirements(t))
            return false;

        Direction dirToBuild = directions[rand.nextInt(8)];
        for (int i = 0; i < 8; i++) {
            // If possible, build in this direction
            if (rc.canBuild(dirToBuild, t)) {
                rc.build(dirToBuild, t);
                return true;
            } else {
                dirToBuild = dirToBuild.rotateLeft();
            }
        }
        return false;
    }
}
