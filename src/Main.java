
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

public class Main {
    public static String self_ip;
    // critical section on one pc needs to be accessible to all the computers .
    // so it needs to be accessed through a code in the same pc which also uses a socket to connect to other pcs
    // ip of critical section is not necessary as the file is assumed to be in system 1 and running on different port number
    public static String ip1,ip2,ip3;
    public static int port1,port2,port3,port_cs; // port number of all the devices and the critical section code
    public static ServerSocket cs_socket; // we are creating a server socket for critical section code since we are giving access to the code
    private static int timeStamp=0; // timestamp of the process in this system
    // The next important thing is a priority queue.
    // This is there to push the request and see which request to handle first
    // The format of map is timestamp of request sent and who the sender is (ip+port)
    private static final PriorityQueue<Map.Entry<Integer,String>> requestQueue=new PriorityQueue<>(
            Comparator.comparingInt(Map.Entry::getKey)
    );

    // Exists so that sender thread or reciever thread alone can update timestamp not both
    // best example is you are making your own request and someone else at the same time sends his own request then there will be an issue if there is no lock
    static ReentrantLock timestampLock = new ReentrantLock();

    // To prevent two requests from updating at the same time
    static ReentrantLock priorityQueueLock = new ReentrantLock();

    // in case you get two replies at the same time you cannot give access to reply_no at the same time, so we can use reply_lock in that case
    static ReentrantLock replyLock = new ReentrantLock();
    private static int reply_no;
    private static int events=0; // no of events that we created or were created due to receiving message

    public static ArrayList<String> eventInfo=new ArrayList<>(); // information regarding what the event is, when was it formed
    private static boolean is_CS_Free = false;
    // create a listener socket for the listener thread
    public static ServerSocket listenerSocket;

    private static Semaphore semaphore = new Semaphore(2);

    public static void main(String[] args) throws IOException {
        try {
            self_ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
        System.out.println("System running on " + self_ip);  // if we need to know our local ip address when we are making an actual connection with three devices

        // you can always replace the below code by taking input using scanner .input the remaining code will not change.

        ip1 = self_ip;
        ip2 = self_ip;
        ip3 = self_ip;
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter all the port numbers of the devices:");
        port1 = scanner.nextInt();
        new Thread(()->listener()).start(); // since port 1 and ip1 is global we don't need to give any input, just need to give the method for thread to execute
        port2 = scanner.nextInt();
        port3 = scanner.nextInt();
        port_cs = 8080;

        // now we need to make a listener thread. For sender threads the plan is simple just add them on need basis like whenever are making a request
        // but listener thread needs to be active all the time to accept connections, so we can make it in the main function and leave it


        SenderInterface();

    }

    // The main job of the listener thread is to just accept sender's message from system 2 and system 3.
    public static void listener() {
        try {
            listenerSocket = new ServerSocket(port1);// create a listenerSocket on system 1
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // make the listener thread run indefinetly
        while(true) {
            Socket senderSocket;
            try {
                senderSocket = listenerSocket.accept(); // accept all connections from a sender
                // now that a connection from a sender to listener is formed we need to handle whatever message it sends
                // For this purpose we need a messageHandler
                new Thread(() -> {
                    try {
                        messageHandler(senderSocket);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Socket waitForSocket(String ip,int port) {
        // Socket is unreachable
        boolean SocketOpen = false;
        Socket socket  = null;
        while(!SocketOpen){
            // Trying to connect to the socket if fails try again.
            try {
                socket = new Socket(ip,port);
                SocketOpen = true;
            } catch (IOException e) {
                System.out.println("Socket unreachable. Trying again in 3s"); // retrying incase you don't switch it on
                try{
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }

        }
        return socket;
    }

    // Making a function to handle all possible messages like REQUEST, REPLY, RELEASE
    private static void messageHandler(Socket senderSocket) throws IOException {
        // To take the input from the client first we need to take create an input stream
        // from sender socket to listener socket
        DataInputStream in = new DataInputStream(senderSocket.getInputStream());
        // to handle the incoming message we need to know the message format
        // From the message we need to know a few things
        // 1.from which sender is the message from
        // 2.what kind of message did the sender send
        // 3.lastly when did he send it
        // we can keep the message format in any combination of the above as long as it is decipherable
        // Format I chose is this TypeofMessage_Timestamp@Ip:Port but as I said you can always jumble it around
        String message = in.readUTF();
        String[] parts = message.split("_|@|:");
        String typeOfMessage = parts[0];
        int timeStamp_Message = Integer.parseInt(parts[1]);
        String ip = parts[2];
        int port = Integer.parseInt(parts[3]);

        timestampLock.lock();
        try {
            //recieving event gets created
            events++;
            // recieving the message and making its timestamp max of previous event timestamp , message timestamp + 1
            timeStamp=Math.max(timeStamp_Message,timeStamp)+1;
            // adding the event info(name,timestamp,reason for creation of the event(type)) into the arrayList
            eventInfo.add(String.valueOf(events)+" : "+String.valueOf(timeStamp)+" : receive"+typeOfMessage);
        } finally {
            timestampLock.unlock();
        }

        if(Objects.equals(typeOfMessage, "REQ")){
            priorityQueueLock.lock();
            try {
                requestQueue.add(Map.entry(timeStamp_Message,ip+":"+port));
            } finally {
                priorityQueueLock.unlock();
            }
            // Now we increased our timestamp for recieving request but we need to send reply too right
            // Similar to input stream we used for recieving message . we can use output stream too for sending message.
            try{
                assert requestQueue.peek() != null;
                if (requestQueue.peek().getValue().equals((ip+":"+port))) {
                    System.out.println("Sending REPL to "+port+" "+ip);

                    // trying to connect to the socket of sender to send the reply
                    Socket client = waitForSocket(ip,port);
                    DataOutputStream out = new DataOutputStream(client.getOutputStream());
                    timestampLock.lock();
                    try {
                        events++; // creating a new event to send the reply
                        timeStamp++;
                        eventInfo.add(String.valueOf(events)+" : "+String.valueOf(timeStamp)+" : "+"sendREPL");
                    } finally {
                        timestampLock.unlock();
                    }
                    // we are sending a reply message to the sender of request
                    out.writeUTF("REPL_"+timeStamp+"@"+ip1+":"+port1);
                    out.flush();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        else if(Objects.equals(typeOfMessage, "REPL")){
            replyLock.lock();
            try{

                reply_no++; // increase the number of replies
                if(reply_no == 2){
                    semaphore.release();
                    semaphore.release();
                    reply_no = 0;
                }
            }finally {
                replyLock.unlock();
            }
        }
        else if(Objects.equals(typeOfMessage, "REL")){
            priorityQueueLock.lock();
            try {
                String addr = Objects.requireNonNull(requestQueue.poll()).getValue();
                if(!Objects.equals(addr, ip+":"+port )){
                    System.out.println("Invalid request since queue inconsistent");
                }

                if(requestQueue.peek() != null){
                    String[] address = requestQueue.peek().getValue().split(":");
                    String add_ip = address[0];
                    int add_port = Integer.parseInt(address[1]);
                    if (!requestQueue.peek().getValue().equals((ip1+":"+port1))) {
                        System.out.println("Sending REPL to "+add_port+" "+add_ip);
        
                        // trying to connect to the socket of sender to send the reply
                        Socket client = waitForSocket(add_ip,add_port);
                        DataOutputStream out = new DataOutputStream(client.getOutputStream());
                        timestampLock.lock();
                        try {
                            events++; // creating a new event to send the reply
                            timeStamp++;
                            eventInfo.add(String.valueOf(events)+" : "+String.valueOf(timeStamp)+" : "+"sendREPL");
                        } finally {
                            timestampLock.unlock();
                        }
                        // we are sending a reply message to the sender of request
                        out.writeUTF("REPL_"+timeStamp+"@"+ip1+":"+port1);
                        out.flush();
                    }
                }



            } finally {
                priorityQueueLock.unlock();
            }
        }
        System.out.println("Message received: " + message);
    }

    // Now we will handle the case where we are using the sender thread

    // as a sender Iam supposed to be able to create my own events or send requests
    // We will give the user an option of what to do by creating this interface for the sender

    public static void SenderInterface() throws IOException {
        System.out.println("Welcome User "+ip1+":"+port1);
        // run the interface indefinitely
        while(true){
            System.out.println("\n---------------------------------------\n Select the option below: \n 1.Event Creation \n 2.Request Critical Section \n 3.Current Top of Stack \n 4.Event Info \n");
            Scanner scanner = new Scanner(System.in);
            int option = scanner.nextInt();
            if(option == 1){
                timestampLock.lock();
                try{
                    events++; // create an event
                    timeStamp++;
                    eventInfo.add(String.valueOf(events)+" : "+String.valueOf(timeStamp)+" Dummy Event");
                } finally{
                    timestampLock.unlock();
                }
            }
            else if(option == 2){
                is_CS_Free = true;
                Handle_Request(); // request handler
                is_CS_Free = false;
            }
            else if(option == 3){ // print top element of queue
                priorityQueueLock.lock();
                try{
                    if(requestQueue.peek() == null){
                        System.out.println("Queue is empty");
                    }
                    else{
                        System.out.println("Top element in the queue is " + requestQueue.peek());
                    }
                }finally{
                    priorityQueueLock.unlock();
                }
            }
            else if(option == 4){ // Print all the events with their data
                for (String s : eventInfo) {
                    System.out.println(s);
                }
            }
            else{ // invalid option so we give the option to try again
                System.out.println("Invalid option. Please enter a number between 1 and 4");
            }
        }
    }

    public static void Handle_Request() throws IOException {
        timestampLock.lock();
        try{
            events++; // create an event which sends the request
            timeStamp++;
            eventInfo.add(String.valueOf(events)+" : "+String.valueOf(timeStamp) +" : " +"sendREQ");
        }finally{
            timestampLock.unlock();
        }
        replyLock.lock();
        try{
            reply_no = 0; // initialise reply_no with 0 in case of multiple requests
        }finally{
            replyLock.unlock();
        }

        priorityQueueLock.lock();
        try{
            requestQueue.add(Map.entry(timeStamp,(ip1+":"+port1))); //add our request to our on queue
        }finally {
            priorityQueueLock.unlock();
        }
        sender("REQ_"+timeStamp+"@"+ip1+":"+port1); // send request to pc2 and pc3
        try{
            // now this is the crucial part. we are using these semaphores to make sure that
            // 1. we are on top of the queue and are accessing the CS
            // 2. we can releasing semaphore only when we have completed the CS
            // 3. most importantly the reason why we are dividing sem.acquire into two parts is to make sure we encountered two replies
            semaphore.acquire(2);
            semaphore.acquire(2);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        System.out.println("Entering critical section");

        try{
            Handle_CS(); // Entering Critical Section
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println("exiting CS");
        semaphore.release();
        semaphore.release();

        priorityQueueLock.lock();
        try{
            requestQueue.poll(); // Pop the queue on release
        }finally {
            priorityQueueLock.unlock();
        }

        timestampLock.lock();
        try{
            timeStamp++;
            events++;
            eventInfo.add(events + " : " + timeStamp + " : sendREL");
        }finally {
            timestampLock.unlock();
        }
        sender("REL_"+timeStamp+"@"+ip1+":"+port1);

        if(requestQueue.peek() != null){
            String[] address = requestQueue.peek().getValue().split(":");
            String add_ip = address[0];
            int add_port = Integer.parseInt(address[1]);
            if (!requestQueue.peek().getValue().equals((ip1+":"+port1))) {
                System.out.println("Sending REPL to "+add_port+" "+add_ip);

                // trying to connect to the socket of sender to send the reply
                Socket client = waitForSocket(add_ip,add_port);
                DataOutputStream out = new DataOutputStream(client.getOutputStream());
                timestampLock.lock();
                try {
                    events++; // creating a new event to send the reply
                    timeStamp++;
                    eventInfo.add(String.valueOf(events)+" : "+String.valueOf(timeStamp)+" : "+"sendREPL");
                } finally {
                    timestampLock.unlock();
                }
                // we are sending a reply message to the sender of request
                out.writeUTF("REPL_"+timeStamp+"@"+ip1+":"+port1);
                out.flush();
            }
        }
    }

    public static void sender(String message) {
        // try to connect to pc2 and pc3
        try (Socket sender1 = waitForSocket(ip2, port2);
             Socket sender2 = waitForSocket(ip3, port3);
             DataOutputStream out1 = new DataOutputStream(sender1.getOutputStream());
             DataOutputStream out2 = new DataOutputStream(sender2.getOutputStream())) {
            out1.writeUTF(message);
            out1.flush();

            out2.writeUTF(message);
            out2.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void Handle_CS() throws IOException {
        // trying to connect to the socket which connects everyone to CS
        // Note: While running this code in other computers they need to make sure that the ip1 value is changed to ip of whichever system holds the CS file

        Socket sender = waitForSocket(ip1,port_cs);
        System.out.println("CS Socket Connected");
        DataInputStream in = new DataInputStream(sender.getInputStream());
        DataOutputStream out = new DataOutputStream(sender.getOutputStream());

        out.writeUTF(ip1+":"+String.valueOf(port1));
        // logging who is using the CS in the terminal
        String message = in.readUTF();
        System.out.println(message + " CS");
    }
}