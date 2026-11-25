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
            Client_handler[] Client_List = new Client_handler[0];
            Client_handler[] Client_List_Copy;
            while (true) {
                Socket ClientSock = ServerSock.accept();
                System.out.println("[Connected to client: " + ClientSock.getInetAddress().getHostAddress() + "]");
                Client_handler Handler = new Client_handler(ClientSock);

                //blocco di codice per aumentare la lunghezza della lista di 1 e far partire un thread
                Client_List_Copy = Client_List.clone();
                Client_List = new Client_handler[Client_List.length+1];
                for(int i = 0; i < Client_List_Copy.length-1; i++){
                    Client_List[i] = Client_List_Copy[i];
                }
                Client_List[Client_List.length-1] = Handler;
                Client_List[Client_List.length-1].run();
            }
        }

        catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}