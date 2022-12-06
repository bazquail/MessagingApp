import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private final List<PrintWriter> clientWriters = new ArrayList<>();
    private List<String> usernames = new ArrayList<>();

    public static void main(String[] args) {
        new ChatServer().go();
    }
    public void go() {
        //threadPool will enable server to check for messages from multiple clients simultaneously
        ExecutorService threadPool = Executors.newCachedThreadPool();
        try {
            //connect to same server as chat clients. In this case, local machine
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(5000));

            //accept new connections
            while (serverSocketChannel.isOpen()) {
                SocketChannel clientSocket = serverSocketChannel.accept();
                PrintWriter writer = new PrintWriter(Channels.newWriter(clientSocket, StandardCharsets.UTF_8));
                clientWriters.add(writer);
                threadPool.submit(new ClientReader(clientSocket)); //run instance of inner class on separate thread
                System.out.println("Got a connection");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void distribute(String outgoing) {
        for (PrintWriter writer : clientWriters) { //send the message to each connected client
            writer.println(outgoing);
            writer.flush();
        }
    }
    public class ClientReader implements Runnable { //inner class to run code on separate thread
        BufferedReader reader;
        SocketChannel socket;
        public ClientReader(SocketChannel clientSocket) {
            socket = clientSocket;
            reader = new BufferedReader(Channels.newReader(socket, StandardCharsets.UTF_8));
        }
        public void run() {
            String message;
            try {
                while ((message = reader.readLine()) != null) {
                    String[] result = message.split(":");
                    //check for tag indicating that message is new & old client usernames to add & remove from master list
                    if (result[0].equals("%username")) {
                        usernames.add(result[1]);
                        usernames.remove(result[2]);

                        //build string of usernames for each chat client to parse locally
                        String usernameString = "%username";
                        for (String username : usernames) {
                            usernameString += ":" + username;
                        }
                        distribute(usernameString);
                    } else{ //if no tag present, distribute message
                        distribute(message);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}