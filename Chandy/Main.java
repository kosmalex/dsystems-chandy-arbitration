package Chandy;
import java.io.*;

public class Main {
    public static void main(String[] args) {

        try {
            Process[] p = new Process[2];
            p[0] = new Process(); // Client process
            p[1] = new Process(); // Server process

            for (int i = 0; i < p.length; i++) {
                p[i].createServer();
                p[i].server.start();
            }
            
            p[0].createClient("localhost", 5000 + p[1].getID());
            p[1].createClient("localhost", 5000 + p[0].getID());
            p[0].client.start();
            p[1].client.start();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }
}