import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatClient {
    private JTextField sendField = new JTextField(20);
    private JTextField usernameField = new JTextField(20);
    private JButton sendButton = new JButton("Send");
    private JButton usernameButton = new JButton("Set Username");
    private JTextArea receiveArea = new JTextArea(10,20);
    private PrintWriter writer;
    private BufferedReader reader;
    private JFrame frame = new JFrame("Chat Client");
    private String username;
    private ArrayList<String> usernameList = new ArrayList<>();
    private boolean hasUsername = false;

    public static void main(String[] args) {
        new ChatClient().go();
        new ChatClient().go(); //create second instance to demonstrate functionality on local machine
    }
    public void go() {
        establishNetwork();

        //set frame parameters and create panel for objects
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setBounds(700,200,300,400);
        JPanel mainPanel = new JPanel();

        //create the message window and assign it a scroll pane
        receiveArea.setLineWrap(true);
        receiveArea.setWrapStyleWord(true);
        receiveArea.setEditable(false);
        JScrollPane scroller = new JScrollPane(receiveArea);
        scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        mainPanel.add(scroller);

        //create the same action listener for both the text field and send button
        ActionListener sendListener = l -> sendMessage();
        sendButton.addActionListener(sendListener);
        sendField.addActionListener(sendListener);
        sendField.setText("");
        mainPanel.add(sendField);
        mainPanel.add(sendButton);
        sendField.setVisible(false);
        sendButton.setVisible(false);

        //create the username field and button
        usernameButton.addActionListener(l -> {
            if (usernameButton.getText().equals("Set Username")) { //button text is 'Change Username' when not actively changing username
                setUsername();
            } else {
                usernameField.setVisible(true);
                usernameButton.setText("Set Username");
            }
        });
        usernameField.addActionListener(l -> setUsername());
        usernameField.setText("");
        mainPanel.add(usernameField);
        mainPanel.add(usernameButton);

        //create a thread to check for messages while the user interacts with the front end
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new IncomingReader());

        //add panel to frame, set layout manager, and make it all visible
        frame.getContentPane().add(BorderLayout.CENTER,mainPanel);
        frame.setVisible(true);

        //enable in-focus buttons to be activated via enter key
        UIManager.put("Button.focusInputMap", new UIDefaults.LazyInputMap(new
                Object[] {
                "ENTER", "pressed",
                "released ENTER", "released"
        }));
    }
    private void establishNetwork() { //connect to the local machine server, same as the chat server
        try {
            InetSocketAddress address = new InetSocketAddress("127.0.0.1",5000);
            SocketChannel socketChannel = SocketChannel.open(address);

            Writer channelWriter = Channels.newWriter(socketChannel, StandardCharsets.UTF_8);
            writer = new PrintWriter(channelWriter);
            Reader channelReader = Channels.newReader(socketChannel, StandardCharsets.UTF_8);
            reader = new BufferedReader(channelReader);

            System.out.println("Network Established");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void sendMessage() {
        if (!sendField.getText().equals("")) { //check if there is a message present. If so, send it to the server
            writer.println(username + ": " + sendField.getText());
            writer.flush();
            sendField.setText("");
            sendField.requestFocus();
        } else {
            System.out.println("Enter text to send!");
        }
    }
    private void setUsername() {
        String nameAttempt = usernameField.getText();
        if (!nameAttempt.equals("") && !nameAttempt.contains("%") && !usernameList.contains(nameAttempt)
                && !nameAttempt.equals(username)) { //check if there is a valid username present

            String oldUsername = username;
            username = nameAttempt;
            //use server tag so server knows message is usernames to add and remove from master list
            writer.println("%username:"+username+":"+oldUsername);
            writer.flush();

            //send message depending on if it's user's initial name or a name change
            if (!hasUsername) {
                writer.println("'" + username + "' has joined");
            } else {
                writer.println("'" + oldUsername + "' changed their name to '" + username + "'");
            }
            writer.flush();

            //update visibility settings after name change
            hasUsername = true;
            usernameButton.setText("Edit Username");
            usernameField.setVisible(false);
            sendField.setVisible(true);
            sendButton.setVisible(true);
        } else if (usernameList.contains(nameAttempt)) {
            System.out.println("Name taken!");
        } else {
            //Banning '%' prevents mixups with server tags
            System.out.println("Enter valid username! Note: Usernames cannot contain '%'");
        }
    }
    public class IncomingReader implements Runnable { //inner class to run code on a separate thread
        public void run() {
            String message;
            try {
                while ((message = reader.readLine()) != null) { //reader from outer class
                    String[] result = message.split(":");
                    //check for server tag indicating that message is a list of taken usernames
                    if (result[0].equals("%username")) {
                        usernameList.clear();
                        for (int i = 1; i < result.length; i++) {
                            usernameList.add(result[i]);
                        }
                    } else { //if not, display the message to the user
                        receiveArea.append(message + "\n");
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
