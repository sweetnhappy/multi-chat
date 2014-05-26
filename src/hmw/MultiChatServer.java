/**
 * File name:		MultiChatServer.java
 * Description:		A chat server that can handle any number of clients.
 * Class:			COMP 455 - AB1 - Winter 2010 - Rushton
 * Date Created:	2010/02/28
 * Date Modified:	2010/02/28
 * @author - Helmut Wollenberg
 */

package hmw;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Date;
import java.util.Vector;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;

/**
 * This class defines and creates a multi-threaded chat server.
 */
public class MultiChatServer extends JFrame {
	private static final long serialVersionUID = 1L;				/* Serial version of the class. */
	private int messagesSent = 0;									/* Counter for the number of messages sent. */
	private int messagesReceived = 0;								/* Counter for the number of messages received. */
	private JTextArea statusTextArea = new JTextArea();				/* Text area for server status updates. */
	private Vector<String> messages = new Vector<String>(25, 5);	/* A Vector (list) containing all chat message history. */
	private Vector<String> clientUsers = new Vector<String>();		/* A Vector containing all client user names. */
	private Vector<Socket> clientSockets = new Vector<Socket>();	/* A Vector containing all client sockets. */

	/**
	 * The main driver of the chat server.
	 * @param	args	String[]
	 */
	public static void main(String[] args) {
		new MultiChatServer();
	}
	
	/**
	 * Create a multi-threaded chat server ready to receive client connections.
	 */
	public MultiChatServer() {
		setLayout(new BorderLayout());
		this.statusTextArea.setEditable(false);
		add(new JScrollPane(this.statusTextArea), BorderLayout.CENTER);
		/* Add a window listener for clean-up at close. */
		this.addWindowListener(new WindowCloseListener());

		setTitle("Multi Chat Server Status");
		setSize(500, 750);
		/* There are clean-up tasks to do before closing down. */
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		setVisible(true);

		/* Try block which creates the server socket and takes client connection requests. */
		try {
			ServerSocket serverSocket = new ServerSocket(7777);
			this.statusTextArea.append("Multi Chat Server started at " + new Date() + "\n");

			int clientNumber = 0; /* Each client will have a unique ID number. */

			/* Create and begin the server thread responsible for broadcasting received messages to clients. */
			BroadcastChats broadcastTask = new BroadcastChats();
			new Thread(broadcastTask).start();
			
			/* Continuously serve any new client that requests a connection. */
			while (true) {
				Socket socket = serverSocket.accept();
				
				DataInputStream in = new DataInputStream(socket.getInputStream());
				DataOutputStream out = new DataOutputStream(socket.getOutputStream());
				
				/* Get user name the client is requesting. */
				String clientUserName = in.readUTF();
				boolean clientNameUsed = false;
				
				/* Check to ensure the user name is not already in use. */
				for (int i = 0; i < clientUsers.size(); i++) {
					if (clientUsers.get(i) != null) {
						if (clientUserName.equalsIgnoreCase(clientUsers.get(i))) {
							clientNameUsed = true;
						}
					}
				}
				
				/* If the user name is in use, tell client. */
				if (clientNameUsed == true) {
					out.writeBoolean(false);
					out.flush();
				}
				/* Otherwise create the socket and client handling thread. */
				else {
					out.writeBoolean(true);
					out.flush();
					
					/* Add client user name and socket to appropriate Vectors. */
					clientUsers.add(clientUserName);
					clientSockets.add(socket);

					/* Update status text area with the start of this client thread with client information. */
					this.statusTextArea.append("Starting thread for client " + clientNumber +
							" on " + new Date() + "\n");

					InetAddress inetAddress = socket.getInetAddress();

					this.statusTextArea.append("Client " + clientNumber + " identifies as " +
							inetAddress.getHostName() + " " + inetAddress.getHostAddress() + "\n");

					/* Create and start the client handling thread for this client. */
					HandleChatClient clientTask = new HandleChatClient(socket, clientNumber, clientUserName);
					new Thread(clientTask).start();
					
					clientNumber++;
				}
			}
		}
		catch (BindException e) {
			/* A BindException is thrown if there is a server running already. */
			System.err.println("Server already running here.");
			System.exit(0);
		}
		catch (IOException e) {
			/* Catches other IOExceptions indicating that the server could not start or has encountered a problem. */
			System.err.println(e);
			System.exit(0);
		}
	}
	
	/**
	 * This class forms a thread that broadcasts chat messages to clients.
	 */
	class BroadcastChats implements Runnable {
		public void run() {
			/* Continuously check for messages to broadcast. */
			while (true) {
				/* Must have a delay for the if condition to be checked correctly. */
				try {
					Thread.sleep(10);
				}
				catch (InterruptedException ie) {}
				
				/* Check to see if all messages received have been broadcasted, if not broadcast. */
				if (messagesReceived > messagesSent) {
					/* Attempt to broadcast to all sockets that may exist.
					 * This ensures that closed sockets don't affect broadcasts to remaining clients. */
					for (int i = 0; i < clientSockets.capacity(); i++) {
						try {
							/* Make sure the current Socket in the Vector is a valid socket. */
							if (clientSockets.get(i) != null) {
								try {
									/* Send out the message and update the status text area to indicate success. */
									DataOutputStream out = new DataOutputStream(clientSockets.get(i).getOutputStream());

									out.writeUTF(messages.get(messagesSent));
									out.flush();

									statusTextArea.append("Chat message number " + messagesSent + " sent to " +
											clientSockets.get(i).getInetAddress().getHostAddress() + "\n");
								}
								catch (IOException e) {}
							}
						}
						catch (Exception e) {} /* Other exceptions could be generated - can be ignored. */
					}
					/* Increment the messages sent counter to show this message was broadcasted. */
					messagesSent++;
				}
			}
		}
	}
	
	/**
	 * This class forms a thread that handles receiving chat messages from a client.
	 */
	class HandleChatClient implements Runnable {
		private Socket socket;			/* The current thread's client's socket. */
		private String clientUserName;	/* The current thread's client's user name. */
		private int clientNumber;		/* The current thread's client's ID number. */
		
		/**
		 * Create the object to run in the thread.
		 * @param	socket	Socket
		 */
		public HandleChatClient(Socket socket, int clientNumber, String clientUserName) {
			this.socket = socket;
			this.clientNumber = clientNumber;
			this.clientUserName = clientUserName;
		}
		
		/**
		 * The thread's running tasks for handling clients.
		 */
		public void run() {
			try {
				DataInputStream in = new DataInputStream(this.socket.getInputStream());
				
				/* Continuously serve the client until it signals it's closing. */
				while (true) {
					String chatString = in.readUTF();

					/* Check to see if the client is closing.
					 * Note the String "CLOSE_SOCKET" cannot be sent via the input box in the client.
					 * If that is attempted the String actually received will not be equivalent. */
					if (chatString.equals("CLOSE_SOCKET")) {
						statusTextArea.append("Chat client number " + clientNumber +
								" has closed down at " + new Date() + "\n");
						
						clientUsers.remove(this.clientUserName);
						
						this.socket.close();
					}
					/* Otherwise add the chat message to the message history and update the status text area. */
					else {
						statusTextArea.append("Received chat message number " + messagesReceived +
								" from " + this.socket.getInetAddress().getHostAddress() + 
								": \n" + chatString);

						messages.add(messagesReceived, chatString);
						messagesReceived++;
					}
				}
			}
			catch (IOException e) {}
		}
	}
	
	/**
	 * This class acts as the Window "Listener" to perform clean-up tasks if the server is closed.
	 */
	private class WindowCloseListener extends WindowAdapter {
		public void windowClosing(WindowEvent e) {
			try {
				/* Attempt to tell all clients that the server is shutting down. */
				for (int i = 0; i < clientSockets.size(); i++) {
					if (clientSockets.get(i) != null) {
						DataOutputStream out = new DataOutputStream(clientSockets.get(i).getOutputStream());

						out.writeUTF("SERVER_SHUTDOWN");
						out.flush();
					}
				}
			}
			catch (IOException ioe) {}
			catch (Exception ex) {} /* Other exceptions could be generated - can be ignored. */
			/* Exit cleanly once all clients are notified. */
			finally {
				System.exit(0);
			}
		}
	}
}
