import Thread_Functions.Client_handler;
import  java.io.*;
import java.net.*;
import java.util.Scanner;

//questo file deve essere importato inseme a Client_handler.java nel PC server


public class Server {
    public static void main(String[] args){
        try(var ServerSock = new java.net.ServerSocket()){
            final int SERVERPORT = 7777;
            final String SERVERIP = InetAddress.getLocalHost().getHostAddress();
            final InetSocketAddress SERVERADDRESS = new InetSocketAddress(SERVERIP, SERVERPORT);
            System.out.println("[Server starting on Address " + SERVERIP + ":" + SERVERPORT + "]");
            ServerSock.bind(SERVERADDRESS);
            //lista delle classi che gestiscono i client, questa è la lista delle istanze della classe Client_Handler, le cui variabili vengono aggiornate in base al relativo thread, e quindi da qui puoi ottenere tutte le variabili delle istanze tramite dei getter
            Client_handler[] Client_Handler_Class_List = new Client_handler[0];
            Client_handler[] Client_Handler_Class_List_Copy = new Client_handler[0];
            //lista dei THREAD di ogni client, di fatto runna solo la funzione run in un thread a parte e non contiene la classe intera
            Thread[] Client_Thread_List = new Thread[0];
            Thread[] Client_Thread_List_Copy;

            //FASE 1: Accept Loop
            //detto anche controlla chi si vuole unire, dai al primo player la possibilità di vedere quanti giocatori sono connessi e quando partire
            while (true) {
                Socket ClientSock = ServerSock.accept();
                System.out.println("[Connected to client: " + ClientSock.getInetAddress().getHostAddress() + "]");
                Client_handler Handler_Class = new Client_handler(ClientSock);
                Thread Handler_Thread = new Thread(Handler_Class);

                //blocco di codice per aumentare la lunghezza delle liste di 1
                Client_Thread_List_Copy = Client_Thread_List.clone();
                Client_Handler_Class_List_Copy = Client_Handler_Class_List.clone();
                Client_Thread_List = new Thread[Client_Thread_List.length+1];
                Client_Handler_Class_List = new Client_handler[Client_Handler_Class_List.length+1];
                //le due liste devono forzatamente avere lo stesso numero di slot
                for(int i = 0; i < Client_Thread_List_Copy.length-1; i++){
                    Client_Thread_List[i] = Client_Thread_List_Copy[i];
                    Client_Handler_Class_List[i] = Client_Handler_Class_List_Copy[i];
                }

                //setta l'ultimo slot delle liste all'ultimo client che si è unito e fai partire il thread
                Client_Thread_List[Client_Thread_List.length-1] = Handler_Thread;
                Client_Handler_Class_List[Client_Handler_Class_List.length-1] = Handler_Class;
                Client_Thread_List[Client_Thread_List.length-1].start();
                
                System.out.println("Indica il numero del cliet a cui scrivere, poi indica cosa mandare");
                Scanner Input = new Scanner(System.in);
                Client_Handler_Class_List[Input.nextInt()].Send(Input.next());
            }
        }

        catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}