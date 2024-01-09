package protocol;

import java.io.Serializable;

public class MsgCountryLeaderboard implements Serializable {
    MsgCountryEntry[] entries;

    public MsgCountryLeaderboard(MsgCountryEntry[] entries) {
        this.entries = entries;
    }
}
