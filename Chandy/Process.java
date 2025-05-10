package Chandy;
import java.io.*;
import java.net.*;
import java.nio.channels.FileLock;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;


public class Process extends Thread {
    private static int GEN = 0;

    private String HOST;
    private int    ID;

    private String filename = "roi.txt";
    
    private int[] baton;

    private int nInRoi;
    private int nInRoi_t;

    private int nRcvdMsgs;
    private int nSentMsgs;

    private boolean waiting;
    private boolean busy;

    public  int done;

    protected Queue<Packet> requests;
    
    public Client client;
    public Server server;

    public Process(int nInRoi_t) {
        this.HOST      = "localhost";
        this.ID        = GEN++;
        this.nRcvdMsgs = 0;
        this.nSentMsgs = 0;
        this.baton     = null;
        this.nInRoi    = 0;
        this.nInRoi_t  = nInRoi_t;
        this.requests  = new LinkedList<Packet>();
        this.busy      = false;
        this.waiting   = false;
    }
    
    public Process(String host, int nInRoi_t) {
        this.HOST      = host;
        this.ID        = GEN++;
        this.nRcvdMsgs = 0;
        this.nSentMsgs = 0;
        this.baton     = null;
        this.nInRoi    = 0;
        this.nInRoi_t  = nInRoi_t;
        this.requests  = new LinkedList<Packet>();
        this.busy      = false;
        this.waiting   = false;
    }

    public void setBaton(int[] baton) {
        this.baton = baton;
    }

    public int getID() {
        return this.ID;
    }

    public int getTotalMessages() {
        return this.nRcvdMsgs + this.nSentMsgs;
    }

    public void createClient(String host, int hport) throws IOException {
        this.client = new Client(this, new Socket(host, hport));
    }
    
    public void createServer() throws IOException {
        this.server = new Server(this, 5000 + this.ID);
        this.server.start();
    }

    private void print(String string) {
        System.out.println(
            "[" + String.valueOf(this.ID) + "] -> " +
            string
        );
    }

    private int getRandomNumber(int min, int max) {
        return (int) ((Math.random() * (max - min)) + min);
    }

    public boolean writeFile(String filePath) {
        boolean success = false;
        try {
            RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
            FileLock lock = raf.getChannel().lock();
            this.busy = true;
            this.print("Lock acquired on file: " + filePath);

            raf.seek(raf.length());
            // Convert the string to bytes
            byte[] bytesToWrite = ("<" + HOST + ", " + String.valueOf(ID) + ", " + java.time.ZonedDateTime.now() + ">\n").getBytes();

            int nseconds =  getRandomNumber(1, 20);
            
            while (nseconds > 0) {
                TimeUnit.SECONDS.sleep(1);
                raf.write(bytesToWrite);
                nseconds--;
            }

            this.print("Successfully wrote to the file.");

            lock.release();
            
            raf.close();
            success = true;
            busy = false;
        } catch (Exception e) {
            this.print("File is already locked by another process.");
        }

        return success;
    }    

    public void run() {
        while(true) {
            if (this.nInRoi < this.nInRoi_t) {
                this.print(this.baton == null ? "I have no baton" : "I have the baton");
                // this.print("Stats: " + String.valueOf(this.nInRoi) + " / " + String.valueOf(this.nInRoi_t));
                if (this.baton != null) {
                    this.waiting = false;
                    this.print("I have the baton: Entering the region of interest");
                    this.nInRoi++;
                    this.baton[this.ID] = this.nInRoi;
                    
                    this.writeFile(filename);
                } else if (!waiting && (this.baton == null)) {
                    this.print("Sending request to recieve grant of entry to the region of interest");
                    int[] temp_baton = {-1, -1};
                    this.client.sendRequest(
                        this.ID,
                        this.nInRoi_t,
                        temp_baton
                    );
                    this.waiting = true;
                } else if(waiting) {
                    // this.print("Waiting for the baton");
                }
            }

            if (requests.size() > 0) {
                this.print("I have buffered packets, sending the baton to " + requests.peek().id);
                Packet packet = requests.poll();

                if (packet.nInRoi_t > baton[packet.id]) {
                    this.client.sendRequest(
                        packet.id,
                        packet.nInRoi_t,
                        this.baton
                    );
                    this.baton = null;
                }
            }
            // try {
            //     TimeUnit.SECONDS.sleep(1);
            // } catch (InterruptedException e) {
            //     e.printStackTrace();
            // }
        }

        // this.print("[SUCCESS]: nRoiTarget MET");

        // try {
        //     this.client.join();
        //     this.server.join();
        // } catch (InterruptedException e) {
        //     e.printStackTrace();
        // }
    }

    public class Client {
        private Process master;

        private Packet packet2send;

        private Socket clientSocket;
        private ObjectInputStream  in;
        private ObjectOutputStream out;

        public Client(Process proc, Socket socket) throws IOException {
            this.master       = proc;
            this.clientSocket = socket;
            this.out          = new ObjectOutputStream(clientSocket.getOutputStream());
            this.in           = new ObjectInputStream(clientSocket.getInputStream());
        }

        public void stopClient() {
            try {
                this.clientSocket.close();
                this.in.close();
                this.out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendRequest(int id, int nInRoi_t, int[] baton) {
            this.packet2send = new Packet(id, nInRoi_t, baton);
            this.master.print("Sending: " + this.packet2send.id + ", " + this.packet2send.nInRoi_t + ", " + this.packet2send.baton[0] + ", " + this.packet2send.baton[1]);
            this.master.nSentMsgs++;
            try {
                this.out.writeObject(this.packet2send);
                this.packet2send = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public class Server extends Thread {
        private Process master;
        private ServerSocket serverSocket;
        private Socket clientSocket;
        private ObjectInputStream  in;
        private ObjectOutputStream out;

        public Server(Process parent, int port) throws IOException {
            this.master = parent;
            this.serverSocket = new ServerSocket(port);
        }

        public void run() {
            try {
                while(true) {
                    this.clientSocket = this.serverSocket.accept();
                    this.out = new ObjectOutputStream(clientSocket.getOutputStream());
                    this.in  = new ObjectInputStream(clientSocket.getInputStream());                        
                    
                    Packet packet = (Packet)this.in.readObject();
             
                    if (packet != null) {
                        this.master.nRcvdMsgs++;
                        this.master.print("Received: " + packet.id + ", " + packet.nInRoi_t);
                        if (this.master.baton == null) {
                            if (packet.baton != null) {
                                this.master.print("I rcvd the baton: " + packet.id);
                                this.master.baton = packet.baton;
                                this.master.print(this.master.baton[0] + ", " + this.master.baton[1]);
                            } else {
                                this.master.print("Adding packet: " + packet.id);
                                requests.add(packet);
                            }
                        } else {
                            if(busy) {
                                this.master.print("I am busy, buffering packet " + packet.id);
                                requests.add(packet);
                            } else {
                                this.master.print("I am not busy, sending the baton to " + packet.id);
                                this.master.client.sendRequest(
                                    packet.id,
                                    packet.nInRoi_t,
                                    this.master.baton
                                );
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    this.serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}