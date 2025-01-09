package honeybunnies;

import battlecode.common.MapLocation;
import battlecode.common.Message;

public class HoneyMessage {
    public enum HoneyMessageType {
        FOUND_RUIN,
        GOTO_RUIN
    }

    HoneyMessageType type;
    short data;

    public HoneyMessage(Message m) {
        this.data = (short) m.getBytes();

        switch (m.getBytes() >> 16) {
            case 0: this.type = HoneyMessageType.FOUND_RUIN; break;
            case 1: this.type = HoneyMessageType.GOTO_RUIN; break;
        }
    }

    public HoneyMessageType getType() {
        return type;
    }

    public int toMessage() {
        return type.ordinal() << 16 + data;
    }

    public MapLocation getLocation() {
        return new MapLocation(data & 0x00FF, (data >> 8) & 0x00FF);
    }

    public void setLocation(MapLocation l) {
        data = (short) (l.x + l.y << 8);
    }

    public static int messageFromLocation(MapLocation l, HoneyMessageType type) {
        return (l.x + l.y << 8) + type.ordinal() << 16;
    }
}
