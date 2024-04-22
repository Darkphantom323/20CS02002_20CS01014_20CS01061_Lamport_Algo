public class Message_Parser {
    private static String typeOfMessage;
    private static int timestamp;
    private static String ip;
    private static int port;

    public static void parseMessage(String message) {
        String[] parts = message.split(":");
        typeOfMessage = parts[0];
        ip = parts[1];

    }

    public static String getTypeOfMessage() {
        return typeOfMessage;
    }

    public static int getTimestamp() {
        return timestamp;
    }

    public static String getIp() {
        return ip;
    }

    public static int getPort() {
        return port;
    }
    public static void main(String[] args){
        String message = "192.168.2.1:8080";
        parseMessage(message);
        System.out.println(getIp());
        System.out.println(getTypeOfMessage());
    }


}
