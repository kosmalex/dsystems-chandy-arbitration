package Chandy;
import java.io.*;
import java.net.*;
import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;

public class Process extends Thread {
    private static int GEN = 0;

    private String HOST;
    private int    ID;

    private String filename = "roi.txt";
    
    private int nRcvdMsgs;
    private int nSentMsgs;
    
    public Client client;
    public Server server;

    public Process() {
        this.HOST = "localhost";
        this.ID   = GEN++;
        this.nRcvdMsgs = 0;
        this.nSentMsgs = 0;
    }
    public Process(String host) {
        this.HOST = host;
        this.ID   = GEN++;
        this.nRcvdMsgs = 0;
        this.nSentMsgs = 0;
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
    }

    public void run() {
        this.server.start();
        this.client.start();
        try {
            this.client.join();
            this.server.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public class Client extends Thread {
        private Process master;

        private Socket clientSocket;
        private ObjectInputStream  in;
        private ObjectOutputStream out;

        private String prefix;

        public Client(Process proc, Socket socket) throws IOException {
            this.master = proc;
            this.clientSocket = socket;
            this.out = new ObjectOutputStream(clientSocket.getOutputStream());
            this.in  = new ObjectInputStream(clientSocket.getInputStream());
            this.prefix = "[" + String.valueOf(master.ID) + "] -> ";
        }

        private void print(String string) {
            System.out.println(
                "[" + String.valueOf(master.ID) + "] -> " +
                string
            );
        }

        private int getRandomNumber(int min, int max) {
            return (int) ((Math.random() * (max - min)) + min);
        }

        public void sendMessage(String message) {
            this.master.nSentMsgs++;
            try {
                this.print("Sending: " + message);
                Packet packet = new Packet(message, this.master.ID);
                this.out.writeObject(packet);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        public Packet receiveMessage() throws IOException {
            this.master.nRcvdMsgs++;
            try {
                return (Packet)this.in.readObject();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        public boolean writeFile(String filePath) {
            boolean success = false;
            try {
                RandomAccessFile raf = new RandomAccessFile(filePath, "rw");
                FileLock lock = raf.getChannel().lock();
                this.print("Lock acquired on file: " + filePath);

                raf.seek(raf.length());
                // Convert the string to bytes
                byte[] bytesToWrite = ("<" + master.HOST + ", " + String.valueOf(master.ID) + ", " + java.time.ZonedDateTime.now() + ">\n").getBytes();

                int nseconds =  getRandomNumber(1, 2);
                
                while (nseconds > 0) {
                    TimeUnit.SECONDS.sleep(1);
                    raf.write(bytesToWrite);
                    nseconds--;
                }

                this.print("Successfully wrote to the file.");

                lock.release();
                
                raf.close();
                success = true;
            } catch (Exception e) {
                this.print("File is already locked by another process.");
            }

            return success;
        }    

        public void run() {
            try {
                // while(true) {
                    // System.out.println("Waiting for message...");
                    this.sendMessage(prefix + "Hello from Process!");
                    
                    while(!this.writeFile(master.filename)){
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                // }
            } finally {
                try {
                    in.close();
                    out.close();
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public class Server extends Thread {
        private Process master;
        private ServerSocket serverSocket;

        public Server(Process parent, int port) throws IOException {
            this.master = parent;
            this.serverSocket = new ServerSocket(port);
        }

        public void run() {
            try {
                Socket             clientSocket = this.serverSocket.accept();
                ObjectOutputStream clientOut    = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream  clientIn     = new ObjectInputStream(clientSocket.getInputStream());

                // while (true) {
                Packet packet = (Packet)clientIn.readObject();
                if (packet.message != null) {
                    System.out.println("Received: " + packet.message);

                    Packet reply = new Packet("Returning hello from Process ", this.master.ID);

                    clientOut.writeObject(reply);
                }

                // }
                clientSocket.close();
                clientIn.close();
                clientOut.close();
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