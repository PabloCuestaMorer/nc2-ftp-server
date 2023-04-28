package main;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

/**
 * @author Pablo Cuesta Morer
 * @date 2023-04-27
 */

public class FtpClient2 {
	private String serverHost;
	private int serverPort;
	private Socket controlSocket;
	private BufferedReader controlReader;
	private BufferedWriter controlWriter;
	private Socket dataSocket;
	private DataInputStream dataInputStream;
	private DataOutputStream dataOutputStream;

	public FtpClient2(String serverHost, int serverPort) {
		this.serverHost = serverHost;
		this.serverPort = serverPort;
	}

	public void connect() throws IOException {
		controlSocket = new Socket(serverHost, serverPort);
		controlReader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
		controlWriter = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
		readControlResponse();
		System.out.println("Connected to FTP server at " + serverHost + ":" + serverPort);
	}

	public void disconnect() throws IOException {
		sendControlCommand("QUIT");
		readControlResponse();
		closeControlConnection();
	}

	public String listFiles() throws IOException {
		setupDataConnection();
		sendControlCommand("LIST");
		readControlResponse();

		StringBuilder fileList = new StringBuilder();
		String line;
		while ((line = dataInputStream.readLine()) != null) {
			fileList.append(line).append("\n");
		}

		readControlResponse();
		closeDataConnection();

		return fileList.toString();
	}

	public void downloadFile(String remoteFilename, String localFilename) throws IOException {
		setupDataConnection();
		sendControlCommand("RETR " + remoteFilename);
		readControlResponse();

		try (FileOutputStream fos = new FileOutputStream(localFilename)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = dataInputStream.read(buffer)) != -1) {
				fos.write(buffer, 0, bytesRead);
			}
		}

		readControlResponse();
		closeDataConnection();
	}

	public void uploadFile(String localFilename, String remoteFilename) throws IOException {
		setupDataConnection();
		sendControlCommand("STOR " + remoteFilename);
		readControlResponse();

		try (FileInputStream fis = new FileInputStream(localFilename)) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = fis.read(buffer)) != -1) {
				dataOutputStream.write(buffer, 0, bytesRead);
			}
			dataOutputStream.flush();
		}

		readControlResponse();
		closeDataConnection();
	}

	private void setupDataConnection() throws IOException {
		int clientPort = findAvailablePort();

		// Set up active mode data connection
		sendControlCommand("PORT " + generateHostPortString(clientPort));
		readControlResponse();

		// Connect the data socket to the server
		dataSocket = new Socket(serverHost, clientPort);
		dataSocket.setSoTimeout(10000);
		dataSocket.connect(new InetSocketAddress(serverHost, serverPort - 1));

		// Initialize data input/output streams
		dataInputStream = new DataInputStream(dataSocket.getInputStream());
		dataOutputStream = new DataOutputStream(dataSocket.getOutputStream());
	}

	private String generateHostPortString(int port) throws IOException {
		// Get local IP address
		String ipAddress = InetAddress.getLocalHost().getHostAddress();

		// Get port number as two bytes
		byte[] portBytes = new byte[2];
		portBytes[0] = (byte) ((port >> 8) & 0xFF);
		portBytes[1] = (byte) (port & 0xFF);

		// Convert IP address and port number to comma-separated string
		String[] ipParts = ipAddress.split("\\.");
		String[] portParts = new String[2];
		for (int i = 0; i < portBytes.length; i++) {
			int unsignedByte = portBytes[i] & 0xFF;
			portParts[i] = String.valueOf(unsignedByte);
		}
		String hostPortString = String.join(",", ipParts) + "," + String.join(",", portParts);

		return hostPortString;
	}

	private int findAvailablePort() throws IOException {
		try (ServerSocket tempSocket = new ServerSocket(0)) {
			return tempSocket.getLocalPort();
		}

	}

	private void sendControlCommand(String command) throws IOException {
		controlWriter.write(command + "\r\n");
		controlWriter.flush();
	}

	private String readControlResponse() throws IOException {
		String response = controlReader.readLine();
		System.out.println("Server response: " + response);
		return response;
	}

	private void closeControlConnection() throws IOException {
		controlReader.close();
		controlWriter.close();
		controlSocket.close();
	}

	private void closeDataConnection() throws IOException {
		dataInputStream.close();
		dataOutputStream.close();
		dataSocket.close();
	}

	public static void main(String[] args) {
		try {
			FtpClient2 client = new FtpClient2(InetAddress.getLocalHost().getHostAddress(), 21);
			client.connect();

			Scanner scanner = new Scanner(System.in);
			String command;

			while (true) {
				System.out.print("ftp> ");
				command = scanner.nextLine();
				if (command.equalsIgnoreCase("LIST")) {
					System.out.println("Listing files:");
					System.out.println(client.listFiles());
				} else if (command.startsWith("download")) {
					String[] parts = command.split(" ");
					if (parts.length != 3) {
						System.out.println("Usage: download <remote_file> <local_file>");
					} else {
						System.out.println("Downloading " + parts[1]);
						client.downloadFile(parts[1], parts[2]);
					}
				} else if (command.startsWith("upload")) {
					String[] parts = command.split(" ");
					if (parts.length != 3) {
						System.out.println("Usage: upload <local_file> <remote_file>");
					} else {
						System.out.println("Uploading " + parts[1]);
						client.uploadFile(parts[1], parts[2]);
					}
				} else if (command.equals("QUIT")) {
					break;
				} else {
					System.out.println("Invalid command");
				}
			}

			client.disconnect();
			System.out.println("Disconnected");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}