package Chandy;
import java.io.Serializable;

public class Packet implements Serializable {
  public int id;
  public int nInRoi_t;
  public int[] baton;

  public Packet(int id, int nInRoi_t, int[] baton) {
    this.id     = id;
    this.nInRoi_t = nInRoi_t;
    this.baton  = baton;
  }
}
