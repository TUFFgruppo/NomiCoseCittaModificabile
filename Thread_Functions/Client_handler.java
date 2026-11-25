package Thread_Functions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
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
            String Received_String = "";
            while(true){
                char[] Reader_Buffer = new char[1024];
                Client_Reader.read(Reader_Buffer);
                //per qualche ragione l'ultimo slot di readbuffer e' /n, quindi lo elimino in received string
                for(int i = 0; i < Reader_Buffer.length-2; i++){
                    Received_String += Reader_Buffer[i];
                }
                System.out.println("[Client " + Client_Sock.getInetAddress().getHostAddress()+ " says:" + Received_String + "]");
                System.out.println(Reader_Buffer[Reader_Buffer.length-1]);
            }
        }
        catch(IOException e){
            System.err.print("[Fatal Error with client " + Client_IP + " Disconnecting...");
            throw new RuntimeException(e);
        }
    }
}
