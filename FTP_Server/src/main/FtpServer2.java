package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author Pablo Cuesta Morer
 * @date 2023-04-27
 */
public class FtpServer2 {

	private static final int CONTROL_PORT = 21;
	private static final int DATA_PORT = 20;
	private static final String ROOT_DIRECTORY = "ftp_root";
	private ServerSocket serverSocket;
	private Socket controlSocket;
	private BufferedReader controlReader;
	private BufferedWriter controlWriter;
	private Socket dataSocket;
	private DataInputStream dataInputStream;
	private DataInputStream controlInputStream;
	private DataOutputStream dataOutputStream;

	public FtpServer2() {
		createRootDirectory();
	}

	public void start() {
		try {
//            ServerSocket serverSocket = new ServerSocket(CONTROL_PORT);
//            System.out.println("Server is listening on " + serverSocket.getInetAddress().getHostAddress() + ":" + CONTROL_PORT);
			serverSocket = new ServerSocket(CONTROL_PORT, 0, InetAddress.getByName("192.168.42.11"));
			System.out.println("FTP server started on " + InetAddress.getByName("192.168.42.11").getHostAddress() + " on port 21");

			while (true) {
				controlSocket = serverSocket.accept();
				controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
				controlWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));

				sendControlResponse("220 Service ready for new user.");

				handleClient();
			}
		} catch (IOException e) {
			System.err.println("Error starting FTP server: " + e.getMessage());
		} finally {
			closeConnections();
		}
	}

	private void handleClient() {
		String command;
		try {
			while ((command = controlReader.readLine()) != null) {
				System.out.println("Received command: " + command);
				String[] commandParts = command.split(" ");
				String commandType = commandParts[0].toUpperCase();

				switch (commandType) {
				case "PORT":
					handlePortCommand(commandParts[1]);
					break;
				case "LIST":
					handleListCommand();
					break;
				case "RETR":
					handleRetrCommand(commandParts[1]);
					break;
				case "STOR":
					handleStorCommand(commandParts[1]);
					break;
				case "QUIT":
					handleQuitCommand();
					return;
				default:
					sendControlResponse("500 Command not recognized.");
					break;
				}
			}
		} catch (IOException e) {
			System.err.println("Error reading client command: " + e.getMessage());
		}
	}

	private void handlePortCommand(String hostPort) {
		try {
			String[] hostPortParts = hostPort.split(",");
			String clientHost = hostPortParts[0] + "." + hostPortParts[1] + "." + hostPortParts[2] + "."
					+ hostPortParts[3];
			int clientPort = Integer.parseInt(hostPortParts[4]) * 256 + Integer.parseInt(hostPortParts[5]);

			dataSocket = new Socket();
			dataSocket.bind(new InetSocketAddress(DATA_PORT));
			dataSocket.connect(new InetSocketAddress(clientHost, clientPort));

			dataInputStream = new DataInputStream(dataSocket.getInputStream());
			dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());

			sendControlResponse("200 Command okay.");
		} catch (IOException e) {
			System.err.println("Error establishing data connection: " + e.getMessage());
			sendControlResponse("425 Can't open data connection.");
		}
	}

	private void handleListCommand() {
		if (dataSocket == null) {
			sendControlResponse("503 Bad sequence of commands.");
			return;
		}

		try {
			sendControlResponse("150 File status okay; about to open data connection.");
			File rootDir = new File(ROOT_DIRECTORY);
			File[] files = rootDir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile()) {
						dataOutputStream.writeBytes(file.getName() + "\r\n");
					}
				}
				dataOutputStream.flush();
			}
			sendControlResponse("226 Closing data connection. Requested file action successful.");
		} catch (IOException e) {
			System.err.println("Error sending file list: " + e.getMessage());
			sendControlResponse("451 Requested action aborted: local error in processing.");
		} finally {
			closeDataConnection();
		}
	}

	private void handleRetrCommand(String filename) {
		if (dataSocket == null) {
			sendControlResponse("503 Bad sequence of commands.");
			return;
		}

		File file = new File(ROOT_DIRECTORY, filename);
		if (!file.exists() || !file.isFile()) {
			sendControlResponse("550 Requested action not taken. File unavailable (e.g., file not found, no access).");
			return;
		}

		try {
			sendControlResponse("150 File status okay; about to open data connection.");

			byte[] buffer = new byte[8192];
			int bytesRead;
			try (FileInputStream fileInputStream = new FileInputStream(file)) {
				while ((bytesRead = fileInputStream.read(buffer)) != -1) {
					dataOutputStream.write(buffer, 0, bytesRead);
				}
				dataOutputStream.flush();
			}

			sendControlResponse("226 Closing data connection. Requested file action successful.");
		} catch (IOException e) {
			System.err.println("Error sending file: " + e.getMessage());
			sendControlResponse("451 Requested action aborted: local error in processing.");
		} finally {
			closeDataConnection();
		}
	}

	private void handleStorCommand(String filename) {
		if (dataSocket == null) {
			sendControlResponse("503 Bad sequence of commands.");
			return;
		}

		File file = new File(ROOT_DIRECTORY, filename);
		try {
			sendControlResponse("150 File status okay; about to open data connection.");

			byte[] buffer = new byte[8192];
			int bytesRead;
			try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
				while ((bytesRead = dataInputStream.read(buffer)) != -1) {
					fileOutputStream.write(buffer, 0, bytesRead);
				}
				fileOutputStream.flush();
			}

			sendControlResponse("226 Closing data connection. Requested file action successful.");
		} catch (IOException e) {
			System.err.println("Error receiving file: " + e.getMessage());
			sendControlResponse("451 Requested action aborted: local error in processing.");
		} finally {
			closeDataConnection();
		}
	}

	private void handleQuitCommand() {
		sendControlResponse("221 Service closing control connection.");
		closeControlConnection();
	}

	private void sendControlResponse(String response) {
		try {
			controlWriter.write(response + "\r\n");
			controlWriter.flush();
		} catch (IOException e) {
			System.err.println("Error sending control response: " + e.getMessage());
		}
	}

	private void closeConnections() {
		closeDataConnection();
		closeControlConnection();
	}

	private void closeDataConnection() {
		try {
			if (controlReader != null) {
				controlReader.close();
			}
			if (dataOutputStream != null) {
				dataOutputStream.close();
			}
			if (dataSocket != null) {
				dataSocket.close();
			}
		} catch (IOException e) {
			System.err.println("Error closing data connection: " + e.getMessage());
		}
	}

	private void closeControlConnection() {
		try {
			if (controlWriter != null) {
				controlWriter.close();
			}
			if (controlWriter != null) {
				controlWriter.close();
			}
			if (controlSocket != null) {
				controlSocket.close();
			}
		} catch (IOException e) {
			System.err.println("Error closing control connection: " + e.getMessage());
		}
	}

	private void createRootDirectory() {
		File rootDir = new File(ROOT_DIRECTORY);
		if (!rootDir.exists()) {
			if (!rootDir.mkdir()) {
				System.err.println("Error creating root directory. Check permissions.");
			}
		}
	}

	public static void main(String[] args) {
		FtpServer2 server = new FtpServer2();
		server.start();
	}
}
