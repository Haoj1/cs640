/*
 *  Import package 
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    // Instance field
    ServerSocket server;
    Socket client;
    InputStream inputStream;
    byte[] read_buf;

    public Server(int port) {
        // check port 
        if (port < 1024 || port >  65535) {
            System.out.println("Error: portnumber must be in the range 1024 to 65535");
            System.exit(1);
        }
        // initialize server socket
        try{
            server = new ServerSocket(port);
        }
        catch (IOException e) {
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
        read_buf = new byte[1000];
    }
    
    public long recieve () {
        long data_recieve = 0;
        // tag the start and end of transaction
        long start=0, end=0;
        double time_recieve = 0;
        int bytes_length;
        try {
            client = server.accept();
            inputStream = client.getInputStream();
            start = System.nanoTime();
            while (true) {
                // read the input into stream
                
                bytes_length = inputStream.read(read_buf, 0, read_buf.length);
                
                // if client close, stop the loop
                if (bytes_length == -1) {
                    end = System.nanoTime();
                    time_recieve += end - start; 
                    break;
                }
                data_recieve += bytes_length;
            }
            inputStream.close();
            client.close();
            server.close();
        }
        catch (IOException e) {
            System.out.println("Error: IO Exception");
            System.exit(1);
        }
        // convert to second
        time_recieve = time_recieve / 1000000000;
        // convert to KB
        data_recieve = data_recieve / 1000;
        // convert to mbps
        double rate = ((double) data_recieve * 8 / time_recieve) / 1000;
        System.out.println("received="+data_recieve+" KB"+" rate="+String.format("%.3f", rate)+" Mbps");
        return data_recieve;
    }
    public static void main(String[] args) {
        Server server = new Server(5535);
        server.recieve();
    }
}