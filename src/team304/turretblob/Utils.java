package team304.turretblob;

import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;

public final class Utils {
    static int dist(MapLocation a, RobotInfo b) {
        return a.distanceSquaredTo(b.location);
    }

    static int distToNearest(MapLocation loc, RobotInfo[] others) {
        int result = 100000;
        for (RobotInfo other : others) {
            result = Math.min(result, dist(loc, other));
        }
        return result;
    }

    static RobotInfo[] concatArrays(RobotInfo a[], RobotInfo b[]) {
        int length = a.length + b.length;
        RobotInfo[] result = new RobotInfo[length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
