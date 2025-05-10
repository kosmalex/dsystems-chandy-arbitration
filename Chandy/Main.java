package Chandy;
import java.io.*;

public class Main {
    public static void main(String[] args) {

        try {
            Process[] p = new Process[2];
            p[0] = new Process(1);
            p[1] = new Process(1);

            for (int i = 0; i < p.length; i++) {
                p[i].createServer();
            }
            
            p[0].createClient("localhost", 5000 + p[1].getID());
            p[1].createClient("localhost", 5000 + p[0].getID());

            p[0].setBaton(new int[] { 0, 0 });
            p[1].setBaton(null);

            for (int i = 0; i < p.length; i++) {
                p[i].start();
            }
        } catch (IOException e) {
          e.printStackTrace();
        }
    }
}