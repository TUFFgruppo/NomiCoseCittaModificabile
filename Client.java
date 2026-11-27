import java.io.*;
import  java.net.*;
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
            PrintWriter Client_Writer = new PrintWriter(ClientSock.getOutputStream());

            while(true){
                System.out.println("dimmi: ");
                var To_Write = Input.next();

                Client_Writer.println(To_Write);
                Client_Writer.flush();
                
            }

        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}