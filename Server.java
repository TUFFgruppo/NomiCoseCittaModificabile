import java.io.*;
import  java.net.*;

import Thread_Functions.Client_handler;

//questo file deve essere importato inseme a Client_handler.java nel PC server


public class Server {


    public static void main(String[] args){
        try(var ServerSock = new java.net.ServerSocket()){
            final int SERVERPORT = 7777;
            final String SERVERIP = InetAddress.getLocalHost().getHostAddress();
            final InetSocketAddress SERVERADDRESS = new InetSocketAddress(SERVERIP, SERVERPORT);
            System.out.println("[Server starting on Address " + SERVERIP + ":" + SERVERPORT + "]");
            ServerSock.bind(SERVERADDRESS);
            Thread[] Client_List = new Thread[0];
            Thread[] Client_List_Copy;
            while (true) {
                Socket ClientSock = ServerSock.accept();
                System.out.println("[Connected to client: " + ClientSock.getInetAddress().getHostAddress() + "]");
                Client_handler Handler_Function = new Client_handler(ClientSock);
                Thread Handler_Thread = new Thread(Handler_Function);

                //blocco di codice per aumentare la lunghezza della lista di 1 e far partire un thread
                Client_List_Copy = Client_List.clone();
                Client_List = new Thread[Client_List.length+1];
                for(int i = 0; i < Client_List_Copy.length-1; i++){
                    Client_List[i] = Client_List_Copy[i];
                }
                Client_List[Client_List.length-1] = Handler_Thread;
                Client_List[Client_List.length-1].start();;
            }
        }

        catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}