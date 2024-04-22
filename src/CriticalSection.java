import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
public class CriticalSection {
    public static void communicateWithClient(Socket clientSocket) {
        try {
            DataInputStream inputStream = new DataInputStream(clientSocket.getInputStream());
//            System.out.println(inputStream.readUTF());
            BufferedWriter writer = new BufferedWriter(new FileWriter("src/Sample_Text.txt", true));
            writer.write(inputStream.readUTF());
            writer.newLine(); // This is for writing in a new line
            writer.close();
            // simulating accessing a critical section. You can actually replace this line with anything else let's say writing a file on pc1
            Thread.sleep(15000);
            DataOutputStream outputStream = new DataOutputStream(clientSocket.getOutputStream());
            outputStream.writeUTF("Completed");

            clientSocket.close();
        } catch (IOException e) {
            System.out.println("IO Exception occurred: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Thread was interrupted: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(8080);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> communicateWithClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Server socket error: " + e.getMessage());
        }
    }

}
