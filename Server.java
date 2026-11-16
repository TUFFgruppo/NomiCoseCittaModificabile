import java.io.*;
import  java.net.*;

//import javax.management.*;

public class Server {

    public static String Handle_Client(Socket Client_Sock){
        String Client_IP = null;
        try{
            Client_IP = Client_Sock.getInetAddress().getHostAddress();
            InputStream Client_Input_Stream = Client_Sock.getInputStream();
            //problema corrente, InputStream.read è blocking
        }
        catch(IOException e){System.err.print("[Fatal Error with client " + Client_IP + " Disconnecting...");
        throw new RuntimeException(e);}
        return "OK";
    }

    public static void main(String[] args){
        try(var ServerSock = new java.net.ServerSocket()){
        final int SERVERPORT = 7777;
        final String SERVERIP = InetAddress.getLocalHost().getHostAddress();
        final InetSocketAddress SERVERADDRESS = new InetSocketAddress(SERVERIP, SERVERPORT);
        ServerSock.bind(SERVERADDRESS);
        while (true) {
            Socket ClientSock = ServerSock.accept();
            System.out.println("[Connected to client: " + ClientSock.getInetAddress().getHostAddress() + "]");
            Handle_Client(ClientSock);
        }

        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }
}
