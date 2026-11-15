import java.io.*;
import  java.net.*;
import java.util.Scanner;
import javax.management.*;

public class Client {

    public static void main(String[] args){
        try(var ClientSock = new java.net.Socket()){
        final int CLIENTPORT = 2555;
        final String CLIENTIP = InetAddress.getLocalHost().getHostAddress();
        final InetSocketAddress CLIENTADDRESS = new InetSocketAddress(CLIENTIP, CLIENTPORT);
        ClientSock.bind(CLIENTADDRESS);
        //      Input da scambiare con interfaccia grafica
        Scanner Input = new Scanner(System.in);
        System.out.println("inserisci il port del pc");
        int ServerPort = Input.nextInt();
        System.out.println("inserisci l'ip del pc");
        String ServerIp = Input.next();
        final InetSocketAddress SERVERADDRESS = new InetSocketAddress(ServerIp, ServerPort);
        
        ClientSock.connect(SERVERADDRESS);
        
        while (true){
            System.out.println("ben");
        }

//            var ClientSock = ServerSock.accept();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}