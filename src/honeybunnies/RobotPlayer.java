package honeybunnies;

import battlecode.common.*;
import battlecode.schema.RobotType;
import scala.Unit;

import java.util.ArrayList;
import java.util.Random;

public class RobotPlayer {

    static final Random rng = new Random(6147);

    static int spawnCount = 0;

    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    static MapLocation targetRuin = null;

    static MapLocation home = null;

    public static void setup(RobotController rc) throws GameActionException {

    }

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        try {
            setup(rc);
        } catch (GameActionException e) {
            System.out.println("Failed on setup!");
            e.printStackTrace();
        }

        while (true) {
            try {
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    public static void runSoldier(RobotController rc) throws GameActionException {

        int r = 0;
        int g = 0;
        int b = 0;

        // Read messages
        Message[] incMessages = rc.readMessages(-1);
        for (Message m : incMessages) {
            HoneyMessage hm = new HoneyMessage(m);
            if (hm.getType() == HoneyMessage.HoneyMessageType.GOTO_RUIN) {
                targetRuin = hm.getLocation();
            }
        }

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

        // Search for a nearby ruin to complete.
        MapInfo curRuin = null;
        MapInfo lastUnpainted = null;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                curRuin = tile;
            }

            if (tile.getMapLocation().equals(targetRuin)) {
                targetRuin = null;
            }

            if (tile.isPassable() && !tile.getPaint().isAlly()) {
                lastUnpainted = tile;
            }
        }

        for (RobotInfo robot : nearbyRobots){
            if (curRuin != null && robot.getLocation().equals(curRuin.getMapLocation())) {
                curRuin = null;
            }

            if (robot.getTeam().isPlayer() && robot.getType().isTowerType() && robot.getType().paintPerTurn > 0) {
                home = robot.getLocation();

                if (rc.canTransferPaint(home, -50)) {
                    rc.transferPaint(home, -50);
                }
            }

            if (!robot.getTeam().isPlayer() && robot.getType().isTowerType()) {
                if (rc.canAttack(robot.getLocation())) {
                    rc.attack(robot.getLocation());
                }
            }
        }

        MapLocation targetLoc = targetRuin;

        if (rc.getPaint() < 40) {
            targetLoc = home;
            g = 255;
        }

        if (curRuin != null){
            MapLocation ruinLoc = curRuin.getMapLocation();

            targetLoc = ruinLoc;
            r = 255;
            b = 255;
            g = 255;

            Direction dir = rc.getLocation().directionTo(ruinLoc);
            // Mark the pattern we need to draw to build a tower here if we haven't already.
            MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);

            int randTower = rng.nextInt(8);
            UnitType towertype = UnitType.LEVEL_ONE_PAINT_TOWER;
            if (randTower == 0) {
                towertype = UnitType.LEVEL_ONE_MONEY_TOWER;
            }

            if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(towertype, ruinLoc)){
                rc.markTowerPattern(towertype, ruinLoc);
                System.out.println("Trying to build a tower at " + ruinLoc);
            }

            // Complete the ruin if we can.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
                System.out.println("Built a tower at " + ruinLoc + "!");
            }

            // Complete the ruin if we can.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLoc);
                rc.setTimelineMarker("Tower built", 255, 255, 0);
                System.out.println("Built a tower at " + ruinLoc + "!");
            }
        }

        // Fill in any spots in the pattern with the appropriate paint.
        for (MapInfo patternTile : rc.senseNearbyMapInfos(rc.getLocation(), 8)){
            if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                if (rc.canAttack(patternTile.getMapLocation()))
                    rc.attack(patternTile.getMapLocation(), useSecondaryColor);
            }
        }

        if (targetLoc == null && lastUnpainted != null) {
            targetLoc = lastUnpainted.getMapLocation();
            r = 255;
        }

        if (targetLoc == null) {
            targetLoc = rc.getLocation().add(directions[rng.nextInt(directions.length)]);
            b = 255;
        }

        rc.setIndicatorLine(rc.getLocation(), targetLoc, r, g, b);

        // Move and attack randomly if no objective.
        attemptToMove(rc, targetLoc);

        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
            rc.attack(rc.getLocation());
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException {
        // Sense information about all visible nearby tiles.
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

        MapLocation targetLoc = null;

        for (RobotInfo robot : nearbyRobots){
            if (robot.getTeam().isPlayer() && robot.getType().isTowerType() && robot.getType().paintPerTurn > 0) {
                home = robot.getLocation();

                if (rc.canTransferPaint(home, -50)) {
                    rc.transferPaint(home, -50);
                }
            }

//            if (robot.getTeam().isPlayer() && robot.getType() == UnitType.SOLDIER) {
//                targetLoc = robot.getLocation();
//                if (rc.canTransferPaint(robot.getLocation(), 10)) {
//                    rc.transferPaint(robot.getLocation(), 10);
//                }
//            }

            if (!robot.getTeam().isPlayer() && robot.getType().isRobotType()) {
                if (rc.canMopSwing(rc.getLocation().directionTo(robot.getLocation()))) {
                    rc.mopSwing(rc.getLocation().directionTo(robot.getLocation()));
                }
                if (rc.canAttack(robot.getLocation())) {
                    rc.attack(robot.getLocation());
                }
            }
        }

        // Fill in any spots in the pattern with the appropriate paint.
        for (MapInfo patternTile : rc.senseNearbyMapInfos(rc.getLocation(), 8)){
            if (!patternTile.getPaint().isAlly()){
                if (rc.canAttack(patternTile.getMapLocation()))
                    rc.attack(patternTile.getMapLocation());
            }
        }

        if (rc.getPaint() < 40) {
            targetLoc = home;
        }

        if (targetLoc == null) {
            targetLoc = rc.getLocation().add(directions[rng.nextInt(directions.length)]);
        }

        rc.setIndicatorLine(rc.getLocation(), targetLoc, 255, 0, 255);

        // Move and attack randomly if no objective.
        attemptToMove(rc, targetLoc);

        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
            rc.attack(rc.getLocation());
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException {

    }

    public static boolean attemptToMove(RobotController rc, MapLocation targetLocation) throws GameActionException {
        if (!rc.isMovementReady()) {
            return false;
        }

        Direction toTarget = PathPlanner.planRoute(rc, rc.getLocation(), targetLocation);

        // Move randomly if planning fails (should rarely happen, if ever)
        if (toTarget == null) {
            toTarget = directions[rng.nextInt(directions.length)];
        }

        // Now move if we can
        if (rc.canMove(toTarget)) {
            rc.move(toTarget);
            return true;
        }

        return false;
    }

    public static void runTower(RobotController rc) throws GameActionException {
        // Read messages
        Message[] incMessages = rc.readMessages(-1);
        for (Message m : incMessages) {
            HoneyMessage hm = new HoneyMessage(m);
            if (hm.getType() == HoneyMessage.HoneyMessageType.FOUND_RUIN) {
                targetRuin = hm.getLocation();
            }
        }

        // Attack nearby enemies
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();

        for (RobotInfo robot : nearbyRobots){
            if (!robot.getTeam().isPlayer() && robot.getType().isRobotType()) {
                if (rc.canAttack(robot.getLocation())) {
                    rc.attack(robot.getLocation());
                }
            }
        }

        // Pick a direction to build a soldier in.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);

        if (spawnCount < 1 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
            rc.buildRobot(UnitType.SOLDIER, nextLoc);
            spawnCount++;

            if (targetRuin != null) {
                rc.sendMessage(nextLoc, HoneyMessage.messageFromLocation(targetRuin, HoneyMessage.HoneyMessageType.GOTO_RUIN));
            }
        }

        if (spawnCount >= 1 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
            rc.buildRobot(UnitType.MOPPER, nextLoc);
            spawnCount = 0;
        }

        if (rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        }
    }
}
