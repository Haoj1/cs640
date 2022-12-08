

public class Iperfer {
    public static void main(String[] args) {
        try {
            if (args.length == 7 && args[0].equals("-c") && args[1].equals("-h") 
                && args[3].equals("-p") && args[5].equals("-t")) {
                    int port = Integer.parseInt(args[4]);
                    int time = Integer.parseInt(args[6]);
                    String hostname = args[2];
                    Client client = new Client(hostname, port);
                    client.sendData(time);
            }
            else if (args.length == 3 && args[0].equals("-s") && args[1].equals("-p")) {
                int listen_port = Integer.parseInt(args[2]);
                Server server = new Server(listen_port);
                server.recieve();
            }
            else {
                System.out.println("Error: invalid arguments");
                System.exit(1);
            }
        }
        catch (Exception e) {
            System.out.println("Error: invalid arguments");
            // System.out.println("Exception");
            System.exit(1);
        }
    } 
    
}
