package team304;

import battlecode.common.Direction;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;

public final class Utils {
    public static final Direction[] DIRECTIONS = {
            Direction.NORTH, Direction.NORTH_EAST, Direction.EAST, Direction.SOUTH_EAST,
            Direction.SOUTH, Direction.SOUTH_WEST, Direction.WEST, Direction.NORTH_WEST};

    public static void check(boolean cond, String message) throws Exception {
        if (!cond) {
            throw new Exception(message);
        }
    }

    public static void check(boolean cond) throws Exception {
        if (!cond) {
            throw new Exception();
        }
    }

    public static int dist(MapLocation a, RobotInfo b) {
        return a.distanceSquaredTo(b.location);
    }

    public static int distToNearest(MapLocation loc, RobotInfo[] others) {
        int result = 100000;
        for (RobotInfo other : others) {
            result = Math.min(result, dist(loc, other));
        }
        return result;
    }

    public static RobotInfo[] concatArrays(RobotInfo a[], RobotInfo b[]) {
        int length = a.length + b.length;
        RobotInfo[] result = new RobotInfo[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
