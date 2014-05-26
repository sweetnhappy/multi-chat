/**
 * File name:		ChatClient.java
 * Description:		A chat client that attaches to a chat server.
 * Class:			COMP 455 - AB1 - Winter 2010 - Rushton
 * Date Created:	2010/02/28
 * Date Modified:	2010/02/28
 * @author - Helmut Wollenberg
 */

package hmw;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.WindowConstants;

/**
 * This class defines and creates a chat client to connect to a chat server.
 */
public class ChatClient extends JFrame {
	private static final long serialVersionUID = 1L;				/* Serial version of the class. */
	private DataInputStream in;										/* The client's data in stream. */
	private DataOutputStream out;									/* The client's data out stream. */
	private JButton sendChatButton = new JButton("Send Message");	/* The send message button. */
	private JTextArea chatInputArea = new JTextArea();				/* The chat message input text area. */
	private JTextArea chatMessagesArea = new JTextArea();			/* The chat message history text area. */
	private Socket socket;											/* The client's socket. */
	private String clientUserName;									/* The client's user name. */
	
	/**
	 * The main driver of the chat client.
	 * @param	args	String[]
	 */
	public static void main(String[] args) {
		new ChatClient();
	}
	
	/**
	 * Create a multi-threaded chat client ready to send and receive messages.
	 */
	public ChatClient() {
		getClientUserName(); /* Ask for the client's user name. */
		
		/* Get the chat server the user wants to connect to. */
		String serverLocation = JOptionPane.showInputDialog(null,
				"Enter the address or name of the server you'd like to connect to.\n" +
				"Leave blank to connect to localhost.",
				"Enter Server", JOptionPane.QUESTION_MESSAGE);
		
		/* If location is left blank, use server at localhost */
		if (serverLocation.equals("")) {
			serverLocation = "localhost";
		}
		
		setLayout(new BorderLayout());
		this.chatInputArea.setRows(8);
		this.chatMessagesArea.setRows(35);
		this.chatMessagesArea.setEditable(false);
		this.sendChatButton.setEnabled(false); /* Is enabled only if text has been input. */
		this.sendChatButton.addActionListener(new SendButtonListener());
		
		/* The chat messages history text area should always have a vertical scroll bar (for differentiation). */
		add(new JScrollPane(this.chatMessagesArea,
				ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.NORTH);
		add(new JScrollPane(this.chatInputArea), BorderLayout.CENTER);
		add(this.sendChatButton, BorderLayout.SOUTH);
		/* Add a window listener for clean-up at close. */
		this.addWindowListener(new WindowCloseListener());
		
		setSize(500, 750);
		/* There are clean-up tasks to do before closing down. */
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
		
		/* Try block which creates the socket, validates the user name, and sets up client threads. */
		try {
			boolean userNameOK = false; /* Flag indicating if the user name is available. */
			
			/* A new socket, etc. is required if the user must choose a different user name. */
			while (userNameOK == false) {
				this.socket = new Socket(serverLocation, 7777);
				
				in = new DataInputStream(this.socket.getInputStream());
				out = new DataOutputStream(this.socket.getOutputStream());
				
				/* Send the user name to the server for validation. */
				out.writeUTF(this.clientUserName);
				out.flush();
				
				/* Get the server's response. */
				userNameOK = in.readBoolean();
				
				/* If the name is not available, tell the user and prompt for a different user name. */
				if (userNameOK == false) {
					JOptionPane.showMessageDialog(null, "User name already in use.\n",
							"User Name In Use", JOptionPane.ERROR_MESSAGE);
					
					/* Close this socket as it is no longer needed. */
					this.socket.close();
					
					getClientUserName();
				}
			}
			
			/* Set the title of and show the client window only when the user name is valid. */
			setTitle("Chat Client for: " + this.clientUserName);
			setVisible(true);
			
			/* Update message history text area indicating successful connection. */
			this.chatMessagesArea.append("Successfully connected as " +
					this.clientUserName + " to chat server at " +
					serverLocation + ".\n\n");
			
			/* Create and start the client/server handling threads for this client. */
			HandleServerMessages fetchTask = new HandleServerMessages();
			CheckForInput checkTask = new CheckForInput();
			
			new Thread(fetchTask).start();
			new Thread(checkTask).start();
		}
		/* An IOException is thrown if the specified server cannot be reached.  Exit after notifying user. */
		catch (IOException e) {
			JOptionPane.showMessageDialog(null, "You have specified an invalid server or the server is not running.",
					"Cannot Connect to Server", JOptionPane.ERROR_MESSAGE);
			
			System.exit(0);
		}
	}
	
	/**
	 * Prompts the user for a client name to use for connecting to the chat server.
	 */
	public void getClientUserName() {
		this.clientUserName = null;
		
		/* Repeat if user name is invalid or empty. */
		while (this.clientUserName == null) {
			String userName = JOptionPane.showInputDialog(null,
					"Please enter your user name.\nPlease note that it must be unique." +
					"\nIf you don't want to chat press Cancel.",
					"Enter User Name", JOptionPane.QUESTION_MESSAGE);
			
			/* Checks to see if Cancel button was pressed, if not proceed. */
			if (userName != null) {
				userName.trim(); /* Trim any leading or trailing spaces. */
				
				/* Make sure response is valid - not empty or a space. */
				if (! (userName.equals("") || (userName.equals(" ")))) {
					this.clientUserName = userName;
				}
				/* User response invalid so tell the user. */
				else {
					JOptionPane.showMessageDialog(null, "Invalid User Name",
							"Invalid Input", JOptionPane.ERROR_MESSAGE);
				}
			}
			/* If Cancel was pressed (or the prompt dialog closed), then exit. */
			else {
				System.exit(0);
			}
		}
	}
	
	/**
	 * This class forms a thread that displays chat messages received from the server.
	 */
	class HandleServerMessages implements Runnable {
		public void run() {
			/* Continuously check for messages to display. */
			while (true) {
				try {
					String message = in.readUTF();
					
					/* Check to see if the server is shutting down.
					 * Note the String "SERVER_SHUTDOWN" cannot be sent via the input box in the client.
					 * If that is attempted the String actually received will not be equivalent. */
					if (message.equals("SERVER_SHUTDOWN")) {
						socket.close();
						
						/* Tell the user the server has been shut down then exit. */
						JOptionPane.showMessageDialog(null, "The chat server has been shut down.",
								"Server Shutdown", JOptionPane.INFORMATION_MESSAGE);
						
						System.exit(0);
					}
					/* Otherwise append the message received to the chat message history text area. */
					else {
						chatMessagesArea.append(message);
					}
				}
				catch (IOException e) {}
			}
		}
	}
	
	/**
	 * This class forms a thread that checks if input is available to send.
	 */
	class CheckForInput implements Runnable {
		public void run() {
			/* Continuously check for input. */
			while (true) {
				try {
					/* If there is no input disable the send button. */
					if (chatInputArea.getText().equals("")) {
						sendChatButton.setEnabled(false);
					}
					/* Otherwise ensure the send button is enabled. */
					else {
						sendChatButton.setEnabled(true);
					}
				}
				catch (NullPointerException e) {} /* May be generated - can't determine why - intermittent. */
			}
		}
	}
	
	/**
	 * This class acts as the Action Listener for the send button.
	 */
	private class SendButtonListener implements ActionListener {
		public void actionPerformed(ActionEvent e) {
			String message = new String();	/* The message to send to the server. */
			Date now = new Date();			/* The (Date) time stamp to add to the message. */
			/* Format the Date as a time stamp (with time zone). */
			SimpleDateFormat dateFormat = new SimpleDateFormat("hh:mm:ss aa z");
			
			/* Format the message with date and the user name of who sent it. */
			message += "At ";
			message += dateFormat.format(now);
			message += ", " + clientUserName + " says: ";
			message += chatInputArea.getText() + "\n";
			
			try {
				/* Send the formatted message to the server and clear the input text area. */
				out.writeUTF(message);
				out.flush();
				
				chatInputArea.setText("");
			}
			catch (IOException ioe) {}
		}
	}
	
	/**
	 * This class acts as the Window "Listener" to perform clean-up tasks if the client is closed.
	 */
	private class WindowCloseListener extends WindowAdapter {
		public void windowClosing(WindowEvent e) {
			try {
				/* Tell the server this client is being closed and close the socket. */
				out.writeUTF("CLOSE_SOCKET");
				out.flush();
				
				socket.close();
			}
			catch (IOException ioe) {}
			/* Exit cleanly once the server has been notified and the socket closed. */
			finally {
				System.exit(0);
			}
		}
	}
}