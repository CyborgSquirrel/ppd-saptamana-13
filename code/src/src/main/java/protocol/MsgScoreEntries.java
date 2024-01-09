package protocol;

import java.io.Serializable;

public class MsgScoreEntries implements Serializable {
    public MsgScoreEntry[] msgScoreEntries;

    public MsgScoreEntries(MsgScoreEntry[] msgScoreEntries) {
        this.msgScoreEntries = msgScoreEntries;
    }
}
