package Chandy;
import java.io.Serializable;

public class Packet implements Serializable {
  public String message;

  public Packet(String text, int id) {
    this.message = text + " " + String.valueOf(id);
  }
}
