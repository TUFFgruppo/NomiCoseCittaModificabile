package Thread_Functions;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
//questo file deve essere importato inseme a server.java nel PC server


public class Client_handler implements Runnable {
    Socket Client_Sock;
    volatile String Received_String = "";
    volatile String Send_String = "";
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
                Received_String = "";
                //per qualche ragione l'ultimo slot di readbuffer e' vuoto, quindi lo elimino in received string
                for(int i = 0; i < Reader_Buffer.length-1; i++){
                    Received_String += Reader_Buffer[i];
                }
//                System.out.print("[Client " + Client_Sock.getInetAddress().getHostAddress()+ " says:" + Received_String + "]");
            }
        }
        catch(IOException e){
            System.err.print("[Fatal Error with client " + Client_IP + " Disconnecting...");
            throw new RuntimeException(e);
        }
    }

    public String Get_Client_input(){
        return Received_String;
    }

    public void Send_Output_To_Client(String Output){
        String Client_IP = null;
        try{
            //IMPORTANTE: PrintWriter manda dati solo se fai flush, aggiungi true come secondo parametro per l'auto flush
            Client_IP = Client_Sock.getInetAddress().getHostAddress();
            PrintWriter Server_Writer = new PrintWriter(Client_Sock.getOutputStream());
            Server_Writer.println(Output);
            Server_Writer.flush();
        }
        catch(IOException e){
            System.err.print("[Fatal Error with client " + Client_IP + " Disconnecting...");
            throw new RuntimeException(e);
        }
    }
}
