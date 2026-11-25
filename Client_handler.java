package Thread_Functions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
//questo file deve essere importato inseme a server.java nel PC server


public class Client_handler implements Runnable {

    Socket Client_Sock;
    public Client_handler(Socket Client_Sock){
        this.Client_Sock = Client_Sock;
    }
    @Override
    public void run() {
        String Client_IP = null;
        try{
            Client_IP = Client_Sock.getInetAddress().getHostAddress();
            BufferedReader Client_Reader = new BufferedReader(new InputStreamReader(Client_Sock.getInputStream()));
            while(true){
                char[] Reader_Buffer = new char[1024];
                Client_Reader.read(Reader_Buffer);
                System.out.println(Reader_Buffer);
            }
        }
        catch(IOException e){
            System.err.print("[Fatal Error with client " + Client_IP + " Disconnecting...");
            throw new RuntimeException(e);
        }
    }
}
