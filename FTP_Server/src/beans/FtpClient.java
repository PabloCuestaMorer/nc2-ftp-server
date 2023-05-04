package beans;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class FtpClient {

	private static final String SERVER_ADDRESS = "127.0.0.1";
	private static final int CONTROL_PORT = 21;
	private static final int DATA_PORT = 20;

	public static void main(String[] args) throws IOException {
		Socket controlSocket = new Socket(SERVER_ADDRESS, CONTROL_PORT);
		System.out.println("Connected to FTP Server at " + SERVER_ADDRESS + ":" + CONTROL_PORT);

		DataOutputStream dos = new DataOutputStream(controlSocket.getOutputStream());
		BufferedReader br = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
		System.out.println(br.readLine());

		BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in));
		boolean exit = false;

		while (!exit) {
			System.out.println("Select an action to perform:");
			System.out.println("1. List files");
			System.out.println("2. Download a file");
			System.out.println("3. Exit");
			System.out.print("Enter the number of your choice: ");
			String choice = consoleReader.readLine();

			switch (choice) {
			case "1":
				if (sendPortCommand(dos, br)) {
					listFiles(dos, br, ".");
				} else {
					System.out.println("An error occurred while processing the PORT command.");
				}
				break;
			case "2":
				System.out.print("Enter the file to download: ");
				String sourcePath = consoleReader.readLine();
				System.out.print("Enter the destination path: ");
				String destPath = consoleReader.readLine();
				if (sendPortCommand(dos, br)) {
					downloadFile(dos, br, sourcePath, destPath);
				} else {
					System.out.println("An error occurred while processing the PORT command.");
				}
				break;
			case "3":
				exit = true;
				break;
			default:
				System.out.println("Invalid choice. Please try again.");
			}
		}

		controlSocket.close();
	}

	private static boolean sendPortCommand(DataOutputStream dos, BufferedReader br) throws IOException {
		InetAddress localAddress = InetAddress.getLocalHost();
		byte[] addressBytes = localAddress.getAddress();
		int availablePort = findAvailablePort();
		String hostPort = String.format("%d,%d,%d,%d,%d,%d", addressBytes[0] & 0xFF, addressBytes[1] & 0xFF,
				addressBytes[2] & 0xFF, addressBytes[3] & 0xFF, availablePort / 256, availablePort % 256);
		dos.writeBytes("PORT " + hostPort + "\r\n");
		String response = br.readLine();
		return response.startsWith("200");
	}

	private static int findAvailablePort() throws IOException {
		try (ServerSocket serverSocket = new ServerSocket(0)) {
			return serverSocket.getLocalPort();
		}
	}

	private static void listFiles(DataOutputStream dos, BufferedReader br, String pathname) throws IOException {
		dos.writeBytes("LIST " + pathname + "\r\n");
		String response = br.readLine();
		System.out.println(response);

		if (response.startsWith("150")) {
			try (Socket dataClientSocket = new Socket(SERVER_ADDRESS, DATA_PORT);
					BufferedReader dataBr = new BufferedReader(
							new InputStreamReader(dataClientSocket.getInputStream()))) {

				String fileName;
				while ((fileName = dataBr.readLine()) != null) {
					System.out.println(fileName);
				}
			}
			System.out.println(br.readLine());
		} else {
			System.out.println("An error occurred while processing the request.");
		}
	}

	private static void downloadFile(DataOutputStream dos, BufferedReader br, String pathname, String destPath)
			throws IOException {
		dos.writeBytes("RETR " + pathname + "\r\n");
		String response = br.readLine();
		System.out.println(response);

		if (response.startsWith("150")) {
			try (Socket dataClientSocket = new Socket(SERVER_ADDRESS, DATA_PORT);
					DataInputStream dataDis = new DataInputStream(dataClientSocket.getInputStream());
					FileOutputStream fos = new FileOutputStream(destPath)) {

				byte[] buffer = new byte[4096];
				int bytesRead;

				while ((bytesRead = dataDis.read(buffer)) != -1) {
					fos.write(buffer, 0, bytesRead);
				}
			}
			System.out.println(br.readLine());
		} else {
			System.out.println("An error occurred while processing the request.");
		}
	}

}
