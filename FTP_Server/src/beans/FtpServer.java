package beans;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class FtpServer {

	private static final int CONTROL_PORT = 21;
	private static final int DATA_PORT = 20;

	public static void main(String[] args) throws IOException {
		ServerSocket controlSocket = new ServerSocket(CONTROL_PORT);
		System.out.println("FTP Server started on port " + CONTROL_PORT);

		Socket clientSocket = controlSocket.accept();
		System.out.println("Client connected from " + clientSocket.getInetAddress().getHostAddress());

		DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
		BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

		dos.writeBytes("220 Service ready for new user.\r\n");

		InetAddress dataClientAddress = null;
		int dataClientPort = -1;

		while (true) {
			String command = br.readLine();
			if (command == null) {
				break;
			}

			String[] commandParts = command.split(" ");
			switch (commandParts[0].toUpperCase()) {
			case "LIST":
				String pathname = commandParts.length > 1 ? commandParts[1] : ".";
				sendFileList(dos, pathname, dataClientAddress, dataClientPort);
				break;

			case "RETR":
				sendFile(dos, commandParts, dataClientAddress, dataClientPort);
				break;

			case "PORT":
				String[] hostPort = parseHostAndPort(commandParts[1]);
				if (hostPort != null) {
					dataClientAddress = InetAddress
							.getByName(String.join(".", hostPort[0], hostPort[1], hostPort[2], hostPort[3]));
					dataClientPort = Integer.parseInt(hostPort[4]) * 256 + Integer.parseInt(hostPort[5]);
					dos.writeBytes("200 Command okay.\r\n");
				} else {
					dos.writeBytes("501 Syntax error in parameters or arguments.\r\n");
				}
				break;
				//**NUEVO**
			case "STOR":
			    if (commandParts.length < 2) {
			        dos.writeBytes("501 Syntax error in parameters or arguments.\r\n");
			        return;
			    }
			    String filePath = commandParts[1];
			    receiveFile(dos, filePath, dataClientAddress, dataClientPort);
			    break;


			default:
				dos.writeBytes("500 Invalid command.\r\n");
			}
		}

		clientSocket.close();
		controlSocket.close();
	}

	private static void sendFileList(DataOutputStream dos, String pathname, InetAddress dataClientAddress,
			int dataClientPort) throws IOException {
		File directory = new File(pathname);
		if (directory.exists() && directory.isDirectory()) {
			dos.writeBytes("150 File status okay; about to open data connection.\r\n");

			try (ServerSocket serverDataSocket = new ServerSocket(DATA_PORT);
					Socket dataClientSocket = serverDataSocket.accept();
					DataOutputStream dataDos = new DataOutputStream(dataClientSocket.getOutputStream())) {

				String[] fileNames = directory.list();
				for (String fileName : fileNames) {
					dataDos.writeBytes(fileName + "\r\n");
				}
			} catch (IOException e) {
				dos.writeBytes("425 Can't open data connection.\r\n");
			}
			dos.writeBytes("226 Closing data connection. Requested file action successful.\r\n");
		} else {
			dos.writeBytes("550 Requested action not taken. File unavailable.\r\n");
		}
	}

	private static void sendFile(DataOutputStream dos, String[] commandParts, InetAddress dataClientAddress,
			int dataClientPort) throws IOException {
		if (commandParts.length < 2) {
			dos.writeBytes("501 Syntax error in parameters or arguments.\r\n");
			return;
		}

		String pathname = commandParts[1];
		File file = new File(pathname);

		if (file.exists() && file.isFile()) {
			dos.writeBytes("150 File status okay; about to open data connection.\r\n");

			try (ServerSocket serverDataSocket = new ServerSocket(DATA_PORT);
					Socket dataClientSocket = serverDataSocket.accept();
					DataOutputStream dataDos = new DataOutputStream(dataClientSocket.getOutputStream());
					FileInputStream fis = new FileInputStream(file)) {

				byte[] buffer = new byte[4096];
				int bytesRead;

				while ((bytesRead = fis.read(buffer)) != -1) {
					dataDos.write(buffer, 0, bytesRead);
				}
			} catch (IOException e) {
				dos.writeBytes("425 Can't open data connection.\r\n");
			}
			dos.writeBytes("226 Closing data connection. Requested file action successful.\r\n");
		} else {
			dos.writeBytes("550 Requested action not taken. File unavailable.\r\n");
		}
	}

	private static String[] parseHostAndPort(String hostPort) {
		String[] parts = hostPort.split(",");
		if (parts.length == 6) {
			return parts;
		}
		return null;
	}
	//**NUEVO**
	private static void receiveFile(DataOutputStream dos, String filePath, InetAddress dataClientAddress,
	        int dataClientPort) throws IOException {

	    dos.writeBytes("150 File status okay; about to open data connection.\r\n");

	    try (ServerSocket serverDataSocket = new ServerSocket(DATA_PORT);
	            Socket dataClientSocket = serverDataSocket.accept();
	            DataInputStream dataDis = new DataInputStream(dataClientSocket.getInputStream());
	            FileOutputStream fos = new FileOutputStream(filePath)) {

	        byte[] buffer = new byte[4096];
	        int bytesRead;

	        while ((bytesRead = dataDis.read(buffer)) != -1) {
	            fos.write(buffer, 0, bytesRead);
	        }
	    } catch (IOException e) {
	        dos.writeBytes("425 Can't open data connection.\r\n");
	    }
	    dos.writeBytes("226 Closing data connection. Requested file action successful.\r\n");
	}

}
