package protocol;

import java.io.Serializable;

public class MsgScoreEntry implements Serializable {
  public int id;
  public int score;

  public MsgScoreEntry(int id, int score) {
    this.id = id;
    this.score = score;
  }

  @Override
  public String toString() {
    return "MsgScoreEntry{" +
            "id=" + id +
            ", score=" + score +
            '}';
  }
}
