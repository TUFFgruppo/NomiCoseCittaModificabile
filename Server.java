import java.io.*;
import  java.net.*;

import javax.management.*;

public class Server {

    public static void main(String[] args){
        try(var ServerSock = new java.net.ServerSocket()){
        final int SERVERPORT = 7777;
        final String SERVERIP = InetAddress.getLocalHost().getHostAddress();
        final InetSocketAddress SERVERADDRESS = new InetSocketAddress(SERVERIP, SERVERPORT);
        ServerSock.bind(SERVERADDRESS);
        
//            var ClientSock = ServerSock.accept();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}