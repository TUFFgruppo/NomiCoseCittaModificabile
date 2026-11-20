import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Corpo del gioco Nomi-Cose-Citta, un thread per ogni client.
 *
 * Svolgimento:
 * 1) preparazione: i client si collegano e ricevono le categorie (le colonne
 *    della tabella), che il primo giocatore puo cambiare;
 * 2) estrazione: il server estrae una lettera a caso;
 * 3) svolgimento: ogni client manda una riga per casella, il piu velocemente
 *    possibile, finche il round e aperto;
 * 4) fine round: chi riempie tutte le caselle scrive STOP e il round si chiude
 *    per tutti, oppure scade il timer;
 * 5) punteggi: 2 punti per una parola che nessun altro ha scritto, 1 punto se
 *    la parola e condivisa, 0 se la casella e vuota o la parola non comincia
 *    con la lettera del round.
 *
 * Protocollo (una riga per messaggio, UTF-8).
 * Dal server:
 *   [Gioco] ...              messaggi informativi
 *   ROUND 2/5                numero del round
 *   LETTERA B                lettera estratta
 *   TEMPO 60                 secondi a disposizione
 *   === Round 2/5 (lettera B) ===
 *   - nome: Nomi=bruno(2) Cose=borsa(1) Citta=-(0) -> 3 punti
 *   CLASSIFICA / 1. nome - 12 punti
 * Dal client:
 *   START                    avvia la partita (solo il primo giocatore)
 *   CATEGORIE Nomi,Cose,...  sceglie le colonne prima di partire (solo il primo)
 *   Nomi=luca                riempie una casella (categoria=parola, vuota cancella)
 *   STOP                     chiude il round se tutte le caselle sono piene
 *   AIUTO                    elenco dei comandi
 *
 * Aggancio nel Server, subito dopo accept():
 * <pre>
 *     Gioco.avvia(ClientSock);
 * </pre>
 */
public class Gioco implements Runnable {

    /** Categorie di partenza, modificabili dal primo giocatore con CATEGORIE. */
    private static final List<String> CATEGORIE_INIZIALI = List.of("Nomi", "Cose", "Citta", "Animali", "Colori");

    /** Lettere estraibili: le piu facili per nomi, cose e citta. */
    private static final char[] ALFABETO = "ABCDEFGILMNOPRSTV".toCharArray();

    private static final int ROUND_TOTALI = 5;

    /** Secondi per riempire la tabella in un round. */
    private static final int DURATA_ROUND = 60;

    /** Secondi fra un round e l'altro, per leggere i risultati. */
    private static final int PAUSA_FRA_ROUND = 10;

    private static final int PUNTI_PAROLA_UNICA = 2;

    private static final int PUNTI_PAROLA_CONDIVISA = 1;

    private static final int MIN_GIOCATORI = 2;

    private static final int MIN_CATEGORIE = 2;

    private static final int MAX_CATEGORIE = 8;

    private static final String NL = System.lineSeparator();

    private static final ScheduledExecutorService PIANIFICATORE = Executors.newSingleThreadScheduledExecutor(esecutore -> {
        Thread thread = new Thread(esecutore, "timer-gioco");
        thread.setDaemon(true);
        return thread;
    });

    // ------------------------------------------------------------------
    // Stato della partita, condiviso fra i thread dei client.
    // Ogni accesso avviene dentro synchronized (Gioco.class).
    // ------------------------------------------------------------------

    /** Client in partita, in ordine di arrivo: il primo e quello che comanda. */
    private static final List<Gioco> GIOCATORI = new ArrayList<>();

    /** Colonne della tabella di questa partita. */
    private static final List<String> CATEGORIE = new ArrayList<>(CATEGORIE_INIZIALI);

    private static final Set<Character> LETTERE_USATE = new HashSet<>();

    private static boolean partitaAvviata = false;

    private static boolean roundAperto = false;

    private static int roundCorrente = 0;

    private static char lettera = 'A';

    /** Quando finisce il round in corso, in nanoTime. */
    private static long fineRound = 0;

    /** Timer del round attivo, oppure la pausa prima del round successivo. */
    private static ScheduledFuture<?> attivitaProgrammata;

    // ------------------------------------------------------------------
    // Stato del singolo giocatore.
    // ------------------------------------------------------------------

    private final Socket socket;

    private final String nome;

    private BufferedReader input;

    private PrintWriter output;

    /** Parole del round in corso: categoria -> parola scritta. */
    private final Map<String, String> parole = new HashMap<>();

    private int punti = 0;

    private Gioco(Socket socket) {
        this.socket = socket;
        this.nome = nomeGiocatore(socket);
    }

    /** Avvia il thread di gioco di un client: una chiamata per ogni accept(). */
    public static void avvia(Socket client) {
        Thread thread = new Thread(new Gioco(client), "gioco-" + nomeGiocatore(client));
        thread.setDaemon(true);
        thread.start();
    }

    /** Nome del giocatore: indirizzo e porta del client. */
    private static String nomeGiocatore(Socket socket) {
        InetAddress indirizzo = socket.getInetAddress();
        String ip = indirizzo == null ? "sconosciuto" : indirizzo.getHostAddress();
        return ip + ":" + socket.getPort();
    }

    // ------------------------------------------------------------------
    // Ciclo di gioco (un thread per client)
    // ------------------------------------------------------------------

    @Override
    public void run() {
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            benvenuto();

            for (String riga = input.readLine(); riga != null; riga = input.readLine()) {
                gestisci(riga.trim());
            }
        } catch (IOException e) {
            System.out.println("[Gioco] Errore con il client " + nome + ": " + e.getMessage());
        } finally {
            abbandona();
            chiudi();
        }
    }

    /** Prepara il client: lo registra, gli manda le categorie e le istruzioni. */
    private void benvenuto() {
        int totale;
        synchronized (Gioco.class) {
            GIOCATORI.add(this);
            totale = GIOCATORI.size();
        }
        trasmetti("[Gioco] " + nome + " e entrato in partita (" + totale + " giocatori).");

        invia("[Gioco] Categorie di questa partita: " + elencoCategorie());
        invia("[Gioco] Una casella per riga, ad esempio: Nomi=luca (riga vuota per cancellarla).");
        invia("[Gioco] Scrivi STOP quando hai riempito tutte le caselle.");
        invia("[Gioco] Punti: " + PUNTI_PAROLA_UNICA + " per parola unica, " + PUNTI_PAROLA_CONDIVISA
                + " se la scrive anche un altro, 0 se vuota o con la lettera sbagliata.");
        if (sonoHost()) {
            invia("[Gioco] Sei il primo giocatore: scrivi START per avviare la partita (minimo "
                    + MIN_GIOCATORI + " giocatori).");
        }

        String stato = statoRound();
        if (!stato.isEmpty()) {
            invia(stato);   // entrato a partita iniziata: sa lettera e tempo rimasto
        }
    }

    /** Esegue un comando arrivato dal client. */
    private void gestisci(String riga) {
        if (riga.isEmpty()) {
            return;
        }
        String maiuscolo = riga.toUpperCase(Locale.ROOT);
        if (maiuscolo.equals("START")) {
            avviaPartita();
        } else if (maiuscolo.equals("STOP")) {
            fermaRound();
        } else if (maiuscolo.equals("AIUTO")) {
            aiuto();
        } else if (maiuscolo.startsWith("CATEGORIE")) {
            impostaCategorie(riga);
        } else if (riga.indexOf('=') > 0) {
            int separatore = riga.indexOf('=');
            salvaParola(riga.substring(0, separatore).trim(), riga.substring(separatore + 1).trim());
        } else {
            invia("[Gioco] Comando sconosciuto. Scrivi AIUTO per l'elenco dei comandi.");
        }
    }

    private void aiuto() {
        invia("[Gioco] Comandi:");
        invia("  START              avvia la partita (solo il primo giocatore)");
        invia("  CATEGORIE A,B,C    sceglie le categorie prima di partire (solo il primo giocatore)");
        invia("  Categoria=parola   riempie una casella, es. Nomi=luca; vuota la cancella");
        invia("  STOP               chiude il round, se hai riempito tutte le caselle");
        invia("  AIUTO              questo elenco");
    }

    /** Salva una parola nel round in corso; la casella vuota la cancella. */
    private void salvaParola(String categoriaRichiesta, String parola) {
        String risposta;
        synchronized (Gioco.class) {
            String categoria = categoriaValida(categoriaRichiesta);
            if (categoria == null) {
                risposta = "[Gioco] Categoria sconosciuta. Categorie: " + String.join(", ", CATEGORIE);
            } else if (!roundAperto) {
                risposta = "[Gioco] Nessun round in corso: aspetta la prossima lettera.";
            } else if (parola.isEmpty()) {
                parole.remove(categoria);
                risposta = "[Gioco] " + categoria + " svuotata.";
            } else if (Character.toUpperCase(parola.charAt(0)) != lettera) {
                risposta = "[Gioco] \"" + parola + "\" non inizia con la lettera " + lettera + ": non salvata.";
            } else {
                parole.put(categoria, parola);
                risposta = "[Gioco] " + categoria + " = " + parola;
            }
        }
        invia(risposta);
    }

    /** STOP: chiude il round per tutti, ma solo se la tabella del giocatore e piena. */
    private void fermaRound() {
        String errore = null;
        synchronized (Gioco.class) {
            if (!roundAperto) {
                errore = "[Gioco] Nessun round in corso: aspetta la prossima lettera.";
            } else {
                List<String> vuote = new ArrayList<>();
                for (String categoria : CATEGORIE) {
                    if (parole.getOrDefault(categoria, "").isEmpty()) {
                        vuote.add(categoria);
                    }
                }
                if (!vuote.isEmpty()) {
                    errore = "[Gioco] Per dire STOP devi riempire tutte le caselle. Mancano: "
                            + String.join(", ", vuote);
                }
            }
        }
        if (errore != null) {
            invia(errore);
            return;
        }
        chiudiRound(nome);
    }

    /** Il primo giocatore sceglie le categorie, prima di avviare la partita. */
    private void impostaCategorie(String riga) {
        String errore = null;
        List<String> nuove = new ArrayList<>();
        for (String pezzo : riga.substring("CATEGORIE".length()).split(",")) {
            String categoria = pezzo.trim();
            if (!categoria.isEmpty() && !nuove.contains(categoria)) {
                nuove.add(categoria);
            }
        }

        synchronized (Gioco.class) {
            if (!sonoHost()) {
                errore = "[Gioco] Solo il primo giocatore puo scegliere le categorie.";
            } else if (partitaAvviata) {
                errore = "[Gioco] La partita e gia in corso: le categorie si scelgono prima di START.";
            } else if (nuove.size() < MIN_CATEGORIE || nuove.size() > MAX_CATEGORIE) {
                errore = "[Gioco] Servono da " + MIN_CATEGORIE + " a " + MAX_CATEGORIE
                        + " categorie separate da virgola, esempio: CATEGORIE Nomi,Cose,Citta,Animali";
            } else {
                CATEGORIE.clear();
                CATEGORIE.addAll(nuove);
            }
        }

        if (errore != null) {
            invia(errore);
            return;
        }
        trasmetti("[Gioco] Categorie scelte: " + elencoCategorie());
    }

    /** Il primo giocatore fa partire la partita. */
    private void avviaPartita() {
        String errore = null;
        synchronized (Gioco.class) {
            if (!sonoHost()) {
                errore = "[Gioco] Solo il primo giocatore puo avviare la partita.";
            } else if (partitaAvviata) {
                errore = "[Gioco] La partita e gia in corso.";
            } else if (GIOCATORI.size() < MIN_GIOCATORI) {
                errore = "[Gioco] Servono almeno " + MIN_GIOCATORI + " giocatori: attendi che si colleghi un altro.";
            } else {
                partitaAvviata = true;
                roundCorrente = 0;
                LETTERE_USATE.clear();
                for (Gioco giocatore : GIOCATORI) {
                    giocatore.punti = 0;
                }
            }
        }

        if (errore != null) {
            invia(errore);
            return;
        }
        trasmetti("[Gioco] Partita avviata: " + ROUND_TOTALI + " round da " + DURATA_ROUND
                + " secondi, categorie " + elencoCategorie() + ".");
        avviaRound();
    }

    /** Estrae la lettera e apre un nuovo round per tutti. */
    private static void avviaRound() {
        String apertura;
        synchronized (Gioco.class) {
            if (!partitaAvviata || GIOCATORI.isEmpty()) {
                return;
            }
            roundCorrente++;
            lettera = estraiLettera();
            for (Gioco giocatore : GIOCATORI) {
                giocatore.parole.clear();
            }
            roundAperto = true;
            fineRound = System.nanoTime() + TimeUnit.SECONDS.toNanos(DURATA_ROUND);
            attivitaProgrammata = PIANIFICATORE.schedule(() -> chiudiRound(null), DURATA_ROUND, TimeUnit.SECONDS);

            apertura = "ROUND " + roundCorrente + "/" + ROUND_TOTALI + NL
                    + "LETTERA " + lettera + NL
                    + "TEMPO " + DURATA_ROUND;
        }
        trasmetti(apertura);
    }

    /** Lettera non ancora usata in questa partita; a esaurimento si riparte. */
    private static char estraiLettera() {
        if (LETTERE_USATE.size() >= ALFABETO.length) {
            LETTERE_USATE.clear();
        }
        char scelta;
        do {
            scelta = ALFABETO[ThreadLocalRandom.current().nextInt(ALFABETO.length)];
        } while (LETTERE_USATE.contains(scelta));
        LETTERE_USATE.add(scelta);
        return scelta;
    }

    // ------------------------------------------------------------------
    // Chiusura del round: confronto delle risposte e punteggi
    // ------------------------------------------------------------------

    /**
     * Chiude il round (STOP di un giocatore o tempo scaduto), assegna i punti e
     * rimanda a tutti il riepilogo; se la partita e finita manda la classifica.
     */
    private static void chiudiRound(String chiHaFermato) {
        String apertura;
        String rapporto;
        String finale = null;

        synchronized (Gioco.class) {
            if (!roundAperto) {
                return;   // gia chiuso: STOP e timer arrivati insieme
            }
            roundAperto = false;
            if (attivitaProgrammata != null) {
                attivitaProgrammata.cancel(false);
                attivitaProgrammata = null;
            }

            apertura = chiHaFermato == null
                    ? "[Gioco] Tempo scaduto: il round e chiuso."
                    : "[Gioco] STOP di " + chiHaFermato + ": il round e chiuso.";

            // Quante volte ogni parola e stata scritta: serve per le parole uniche.
            Map<String, Map<String, Integer>> conteggi = new HashMap<>();
            for (String categoria : CATEGORIE) {
                Map<String, Integer> perParola = new HashMap<>();
                for (Gioco giocatore : GIOCATORI) {
                    String parola = giocatore.parole.getOrDefault(categoria, "");
                    if (parolaValida(parola)) {
                        perParola.merge(parola.toLowerCase(Locale.ROOT), 1, Integer::sum);
                    }
                }
                conteggi.put(categoria, perParola);
            }

            StringBuilder testo = new StringBuilder();
            testo.append("=== Round ").append(roundCorrente).append('/').append(ROUND_TOTALI)
                    .append(" (lettera ").append(lettera).append(") ===").append(NL);

            for (Gioco giocatore : GIOCATORI) {
                StringBuilder riga = new StringBuilder("- ").append(giocatore.nome).append(':');
                int puntiRound = 0;
                for (String categoria : CATEGORIE) {
                    String parola = giocatore.parole.getOrDefault(categoria, "");
                    int puntiCella = 0;
                    if (parolaValida(parola)) {
                        boolean unica = conteggi.get(categoria).get(parola.toLowerCase(Locale.ROOT)) == 1;
                        puntiCella = unica ? PUNTI_PAROLA_UNICA : PUNTI_PAROLA_CONDIVISA;
                    }
                    giocatore.punti += puntiCella;
                    puntiRound += puntiCella;
                    riga.append(' ').append(categoria).append('=')
                            .append(parola.isEmpty() ? "-" : parola)
                            .append('(').append(puntiCella).append(')');
                }
                testo.append(riga).append(" -> ").append(puntiRound).append(" punti").append(NL);
            }
            testo.append(classificaTesto());

            if (roundCorrente >= ROUND_TOTALI) {
                partitaAvviata = false;
                finale = "[Gioco] Partita finita! Il primo giocatore puo scrivere START per giocare ancora.";
            } else {
                attivitaProgrammata = PIANIFICATORE.schedule(Gioco::avviaRound, PAUSA_FRA_ROUND, TimeUnit.SECONDS);
            }
            rapporto = testo.toString();
        }

        // Fuori dal lock: l'invio non deve bloccare gli altri thread.
        trasmetti(apertura);
        trasmetti(rapporto);
        if (finale != null) {
            trasmetti(finale);
        }
    }

    /** True se la parola non e vuota e comincia con la lettera del round. */
    private static boolean parolaValida(String parola) {
        return !parola.isEmpty() && Character.toUpperCase(parola.charAt(0)) == lettera;
    }

    /** Nome esatto della categoria richiesta, o null se non esiste. */
    private static String categoriaValida(String richiesta) {
        for (String categoria : CATEGORIE) {
            if (categoria.equalsIgnoreCase(richiesta)) {
                return categoria;
            }
        }
        return null;
    }

    /** Classifica ordinata per punti; va chiamata con il lock di Gioco.class. */
    private static String classificaTesto() {
        List<Gioco> ordinata = new ArrayList<>(GIOCATORI);
        ordinata.sort((primo, secondo) -> Integer.compare(secondo.punti, primo.punti));

        StringBuilder testo = new StringBuilder("CLASSIFICA" + NL);
        int posizione = 1;
        for (Gioco giocatore : ordinata) {
            testo.append(posizione++).append(". ").append(giocatore.nome)
                    .append(" - ").append(giocatore.punti).append(" punti").append(NL);
        }
        return testo.toString();
    }

    // ------------------------------------------------------------------
    // Messaggi verso i client
    // ------------------------------------------------------------------

    /** Rimanda il messaggio a tutti i client collegati. */
    private static void trasmetti(String messaggio) {
        List<Gioco> destinatari;
        synchronized (Gioco.class) {
            destinatari = new ArrayList<>(GIOCATORI);
        }
        for (Gioco giocatore : destinatari) {
            giocatore.invia(messaggio);
        }
    }

    /** Invia il messaggio al client di questo thread. */
    private void invia(String messaggio) {
        PrintWriter uscita = output;
        if (uscita == null) {
            return;
        }
        synchronized (uscita) {
            uscita.println(messaggio);
        }
    }

    /** Righe con lettera e tempo rimasto, se un round e aperto. */
    private static String statoRound() {
        synchronized (Gioco.class) {
            if (!roundAperto) {
                return "";
            }
            long rimasti = (fineRound - System.nanoTime()) / 1_000_000_000L;
            return "ROUND " + roundCorrente + "/" + ROUND_TOTALI + NL
                    + "LETTERA " + lettera + NL
                    + "TEMPO " + Math.max(0, rimasti);
        }
    }

    private static String elencoCategorie() {
        synchronized (Gioco.class) {
            return String.join(", ", CATEGORIE);
        }
    }

    /** True se questo client e il primo collegato, quello che comanda. */
    private boolean sonoHost() {
        synchronized (Gioco.class) {
            return !GIOCATORI.isEmpty() && GIOCATORI.get(0) == this;
        }
    }

    // ------------------------------------------------------------------
    // Uscita e pulizia
    // ------------------------------------------------------------------

    /** Toglie il client dalla partita; se resta solo il vuoto la partita si azzera. */
    private void abbandona() {
        boolean rimosso;
        boolean nessunGiocatore;
        boolean eraHost;

        synchronized (Gioco.class) {
            eraHost = sonoHost();
            rimosso = GIOCATORI.remove(this);
            nessunGiocatore = GIOCATORI.isEmpty();
            if (nessunGiocatore) {
                partitaAvviata = false;
                roundAperto = false;
                roundCorrente = 0;
                LETTERE_USATE.clear();
                if (attivitaProgrammata != null) {
                    attivitaProgrammata.cancel(false);
                    attivitaProgrammata = null;
                }
            }
        }

        if (!rimosso || nessunGiocatore) {
            return;
        }
        trasmetti("[Gioco] " + nome + " ha lasciato la partita.");
        if (eraHost) {
            String nuovoHost;
            synchronized (Gioco.class) {
                nuovoHost = GIOCATORI.get(0).nome;   // il primo rimasto comanda
            }
            trasmetti("[Gioco] " + nuovoHost + " e ora il primo giocatore.");
        }
    }

    private void chiudi() {
        try {
            socket.close();
        } catch (IOException e) {
            // socket gia chiusa: nulla da fare
        }
    }
}
