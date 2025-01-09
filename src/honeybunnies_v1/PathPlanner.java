package honeybunnies_v1;

import battlecode.common.*;

import java.util.Random;

public class PathPlanner {

    private static final int[] CWSearch = {
            0, 1, 2, 3
    };

    private static final int[] CWSearchCCW = {
            0, -1, -2, -3, -4, -5, -6, -7, -8
    };

    private static final int[] CCWSearch = {
            0, -1, -2, -3
    };

    private static final int[] CCWSearchCW = {
            0, 1, 2, 3, 4, 5, 6, 7, 8
    };

    private static MapLocation lastFrom;
    private static MapLocation lastTarget;
    private static int lastDirectionIndex = 0;
    private static boolean onObstacle = false;
    private static int roundsOnObstacle = 0;
    private static Random random = new Random(2718);

    public static Direction planRoute(RobotController rc, MapLocation fromLocation, MapLocation goalLocation) throws GameActionException {

        if (fromLocation.equals(goalLocation)) {
            return Direction.CENTER;
        }

        // Are we still on the same path?
        if (lastTarget == null || !lastTarget.equals(goalLocation)) {
            lastFrom = fromLocation;
            lastTarget = goalLocation;
            onObstacle = false;
            roundsOnObstacle = 0;
        }

        // Stop sticking to the obstacle when we get on the line
        if (onObstacle && onLine(fromLocation, lastFrom, lastTarget)) {
            onObstacle = false;
            roundsOnObstacle = 0;
        }

        // If not currently following an obstacle, move in the best direction
        if (!onObstacle) {
            lastDirectionIndex = fromLocation.directionTo(goalLocation).ordinal();
            Direction testDirection = Direction.values()[lastDirectionIndex];

            // If we can move in the straight-line direction, go that way
            if (canMove(rc, testDirection)) {
                return testDirection;
            }

            // If we can't, see if it's a real obstacle (walls) or not
            if (rc.canSenseLocation(fromLocation.add(testDirection))) {
                MapInfo blockedArea = rc.senseMapInfo(fromLocation.add(testDirection));

                // If we are stuck on something other than a wall, only try to path
                // around it for just 2 rounds because it's probably temporary.
                if (blockedArea != null && !(blockedArea.isWall() || blockedArea.hasRuin())) {
                    roundsOnObstacle = HoneyConstants.PATH_PLANNING_MAX_ROUNDS_ON_OBJECT - 2;
                }
            }

            // Time to start following the obstacle!
            onObstacle = true;
        }

        rc.setIndicatorString("On obstacle for " + roundsOnObstacle + " rounds!");

        // Break out of obstacle-tracking if we've been doing it too long
        // This usually happens when following the outside walls of the map
        if (roundsOnObstacle >= HoneyConstants.PATH_PLANNING_MAX_ROUNDS_ON_OBJECT) {
            onObstacle = false;
            roundsOnObstacle = 0;
        }
        roundsOnObstacle++;

        int[] primSearch = CWSearch;
        int[] secSearch = CWSearchCCW;

        if (random.nextBoolean()) {
            primSearch = CCWSearch;
            secSearch = CCWSearchCW;
        }

        // Can we continue how we were going?
        if (canMove(rc, indexToDirection(lastDirectionIndex))) {
            // See if we can hug closer (looking CW)
            for (int i = 1; i < primSearch.length; i++) {
                if (!canMove(rc, indexToDirection(lastDirectionIndex + primSearch[i]))) {
                    // Found a place we can't move, go back one
                    lastDirectionIndex = (lastDirectionIndex + primSearch[i-1] + 8) % 8;

                    return indexToDirection(lastDirectionIndex);
                }
            }

            // If not, then let's just keep going that way
            return indexToDirection(lastDirectionIndex);
        }

        // If in front is not good, we have to look CCW
        for (int i=1; i < secSearch.length; i++) {
            if (canMove(rc, indexToDirection(lastDirectionIndex + secSearch[i]))) {
                // Found a place we can go!
                lastDirectionIndex = (lastDirectionIndex + secSearch[i] + 8) % 8;

                return indexToDirection(lastDirectionIndex);
            }
        }

        return null;
    }

    public static boolean canMove(RobotController rc, Direction direction) {
        return rc.canMove(direction);
    }

    /**
     * Test if point tp is on the line formed by points p1 and p2.
     * This is extremely suspicious, but it works so whatever.
     */
    public static boolean onLine(MapLocation tp, MapLocation p1, MapLocation p2) {
        return Math.abs(eucDistance(tp, p1) + eucDistance(tp, p2) - eucDistance(p1, p2)) < 0.5;
    }

    public static double eucDistance(MapLocation p1, MapLocation p2) {
        return Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2));
    }

    public static Direction indexToDirection(int index) {
        return Direction.values()[(index + 8) % 8];
    }
}
