import Thread_Functions.Client_handler;
import  java.io.*;
import java.net.*;
import java.util.Scanner;

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
            
            //IMPORTANTE: PrintWriter manda dati solo se fai flush, aggiungi true come secondo parametro per l'auto flush

            while(true){
                Client_handler Server_Handler_Class = new Client_handler(ClientSock);
                Thread Server_Handler_Thread = new Thread(Server_Handler_Class);
                Server_Handler_Thread.start();
                System.out.println("dimmi quando ricevere");
                Input.next();
                System.out.println(Server_Handler_Class.Get_Input());
            }

        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}