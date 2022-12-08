
/*
 *  Import package 
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class Client {

    // Instance field
    Socket socket;
    byte[] bytes;
    OutputStream stream;

    public Client(String hostname, int port) {
        // test whether hostname is valid
        // converting hostname to ipaddress 
        String ip;
        if (port < 1024 || port >  65535) {
            System.out.println("Error: portnumber must be in the range 1024 to 65535");
            System.exit(1);
        }
        try{
            ip = InetAddress.getByName(hostname).getHostAddress();
            // initialize socket and output stream
            socket = new Socket(ip, port);
            stream = socket.getOutputStream();
        }
        // teminate the process when hostname exception
        catch (UnknownHostException e) {
            System.out.println("Error: Invalid hostname");
            System.exit(1);
        }
        // teminate the process when IO exception
        catch (IOException e) {
            System.out.println("Error: "+e.getMessage());
            System.exit(1);
        }
        // initialize all bytes to 0
        bytes = new byte[1000];
        Arrays.fill(bytes, (byte)0x0);
    }

    public long sendData(long time) {
        // we use ns to count time
        long total_time = (long)(time * 1000000000);
        // how many times data sent
        long data_sent = 0;
        // tag the start and end of transaction
        long start, end;
        double time_sent;
        start = System.nanoTime();
        end = start;
        time_sent = 0;
        // sending bytes in a loop until reach the time
        start = System.nanoTime();
        try {
            while (end - start < total_time) {
                // start = System.nanoTime();
                stream.write(bytes);
                end = System.nanoTime();
                // time_sent += end - start;
                time_sent = end;
                data_sent++;
            }
            stream.close();
            socket.close();
        }
        catch (IOException e) {
            System.out.println("Error: IO Exception");
            System.exit(1);
        }
        //covert the time to second and data from byte to bit
        double rate = (((double) data_sent) * 8 * Math.pow(10, -3)) / (total_time/1000000000);
        System.out.println("sent="+data_sent+" KB"+" rate="+String.format("%.3f", rate)+" Mbps");
        return data_sent;
    }

    public static void main(String[] args) {
        Client client = new Client("34.170.196.64", 5535);
        client.sendData(2);
    }
}