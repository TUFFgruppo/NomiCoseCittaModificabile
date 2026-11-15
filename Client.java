import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Client di gioco.
 * All'avvio, se esiste il file intro.mp4 (o quello passato come argomento),
 * lo riproduce come video di introduzione tramite un player esterno
 * (ffplay di ffmpeg, oppure mpv o VLC se ffplay non e installato).
 */
public class Client {

    /** Nome di default del file video dell'intro. */
    private static final String INTRO_PER_DEFAULT = "intro.mp4";

    public static void main(String[] args){
        try(var socketCliente = new java.net.Socket()){
        final int PORTA_CLIENTE = 2555;
        final String IP_CLIENTE = InetAddress.getLocalHost().getHostAddress();
        final InetSocketAddress INDIRIZZO_CLIENTE = new InetSocketAddress(IP_CLIENTE, PORTA_CLIENTE);
        socketCliente.bind(INDIRIZZO_CLIENTE);

        // Video di introduzione: mostrato prima di collegarsi al server.
        String fileIntro = args.length > 0 ? args[0] : INTRO_PER_DEFAULT;
        riproduciIntro(fileIntro);

        //      Input da scambiare con interfaccia grafica
        Scanner input = new Scanner(System.in);
        System.out.println("inserisci il port del pc");
        int portaServer = input.nextInt();
        System.out.println("inserisci l'ip del pc");
        String ipServer = input.next();
        final InetSocketAddress INDIRIZZO_SERVER = new InetSocketAddress(ipServer, portaServer);

        socketCliente.connect(INDIRIZZO_SERVER);

        while (true){
            System.out.println("ben");
        }

//            var ClientSock = ServerSock.accept();
        } catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    /**
     * Riproduce il video dell'intro con un player esterno, se il file esiste.
     * Il client resta in attesa della fine del video prima di procedere.
     * Viene cercata la variabile d'ambiente INTRO_PLAYER (comando o path del
     * player, es. ffplay/mpv/vlc); se assente o non eseguibile, il client
     * prosegue senza intro. Lo script avvia-client.sh la imposta da solo.
     */
    private static void riproduciIntro(String percorsoVideo) {
        File video = new File(percorsoVideo);
        if (!video.isFile()) {
            System.out.println("Nessun video intro trovato (" + percorsoVideo + "), proseguo senza intro.");
            return;
        }
        String player = System.getenv("INTRO_PLAYER");
        if (player == null || player.isEmpty()) {
            System.out.println("Variabile INTRO_PLAYER non impostata: salto l'intro.");
            System.out.println("Usa lo script avvia-client.sh per configurarla automaticamente.");
            return;
        }
        try {
            // Il player viene eseguito in foreground: il client aspetta la fine del video.
            // Se il comando finisce con "ffplay" aggiunge i flag per chiudersi a fine video,
            // altrimenti il client resterebbe bloccato in attesa.
            String[] parametri;
            if (new File(player).getName().contains("ffplay")) {
                parametri = new String[]{ player, "-autoexit", "-loglevel", "quiet", percorsoVideo };
            } else {
                parametri = new String[]{ player, percorsoVideo };
            }
            Process processo = new ProcessBuilder(parametri).inheritIO().start();
            int esito = processo.waitFor();   // attende la fine della riproduzione
            System.out.println("Intro terminata (codice " + esito + ").");
        } catch (IOException e) {
            System.out.println("Impossibile avviare il player " + player + ": salto l'intro.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}