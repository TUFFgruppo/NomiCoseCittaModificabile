import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Motore di gioco semplificato (tipo "Nomi, Cose, Città").
 *
 * Il gioco procede per fasi:
 *   LOBBY      -> i giocatori entrano nella stanza
 *   RISPOSTE   -> ogni giocatore invia le risposte per la lettera estratta
 *   VOTAZIONE  -> i giocatori votano si/no le risposte degli altri
 *   TERMINATO  -> vengono assegnati i punti e dichiarati i vincitori
 *
 * Gli errori (azione non permessa, dati non validi) sono segnalati
 * lanciando IllegalArgumentException o IllegalStateException.
 */
public class GameEngine {

    /** Fasi del gioco. */
    public enum Fase { LOBBY, RISPOSTE, VOTAZIONE, TERMINATO }

    /** Un giocatore: identificativo, nome e punteggio. */
    public static class Giocatore {
        public final String id;        // identificativo univoco del giocatore
        public final String nome;      // nome mostrato in gioco
        public int punteggio;          // punti accumulati

        Giocatore(String id, String nome) {
            this.id = id;
            this.nome = nome;
            this.punteggio = 0;
        }
    }

    /** Una risposta data da un giocatore per una categoria. */
    public static class Risposta {
        public final long id;                 // identificativo univoco della risposta
        public final String idGiocatore;      // chi ha dato la risposta
        public final String categoria;        // categoria della risposta
        public final String testo;            // testo della risposta
        public boolean approvata;             // true se approvata dalla maggioranza

        /** numero di voti favorevoli */
        public int votiFavorevoli;
        /** numero di voti contrari */
        public int votiContrari;

        /** id dei giocatori che hanno gia votato questa risposta */
        final List<String> votanti = new ArrayList<>();

        Risposta(long id, String idGiocatore, String categoria, String testo) {
            this.id = id;
            this.idGiocatore = idGiocatore;
            this.categoria = categoria;
            this.testo = testo;
        }
    }

    private final List<String> categorie;   // categorie di gioco (Nome, Cosa, ...)
    private final Random casuale;           // generatore per la lettera iniziale

    public final List<Giocatore> giocatori = new ArrayList<>();   // tutti i giocatori
    public final List<Risposta> risposte = new ArrayList<>();     // tutte le risposte inviate

    public Fase fase = Fase.LOBBY;
    public String idCapogioco;       // id del primo giocatore che entra (il "host")
    public char lettera;             // lettera iniziale della partita
    public int turno;                // numero del turno corrente
    public final List<String> idVincitori = new ArrayList<>();  // id dei vincitori a pari merito

    private final List<String> giocatoriInviati = new ArrayList<>(); // gia passati alle risposte
    private long prossimaIdRisposta = 1;                              // contatore per gli id

    /** Costruttore con categorie di default e casualita vera. */
    public GameEngine() {
        this(categorieDiDefault(), new Random());
    }

    /** Costruttore con categorie personalizzate e seme fisso (utile nei test). */
    public GameEngine(List<String> categorie, long seme) {
        this(categorie, new Random(seme));
    }

    private GameEngine(List<String> categorie, Random casuale) {
        if (categorie == null || categorie.isEmpty()) {
            throw new IllegalArgumentException("Serve almeno una categoria");
        }
        this.categorie = new ArrayList<>(categorie);
        this.casuale = casuale;
    }

    /** Categorie usate se non ne vengono fornite altre. */
    private static List<String> categorieDiDefault() {
        return List.of("Nome", "Cosa", "Citta");
    }

    /** Aggiunge un giocatore nella fase di lobby; il primo diventa capogioco. */
    public void aggiungiGiocatore(String id, String nome) {
        richiediFase(Fase.LOBBY);
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID giocatore non valido");
        }
        if (nome == null || nome.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome giocatore non valido");
        }
        if (trovaGiocatore(id) != null) {
            throw new IllegalArgumentException("Giocatore gia presente");
        }
        giocatori.add(new Giocatore(id, nome));
        if (idCapogioco == null) {
            idCapogioco = id;
        }
    }

    /** Il capogioco avvia la partita: estrae la lettera e passa alla fase risposte. */
    public void inizia(String idGiocatore) {
        richiediFase(Fase.LOBBY);
        if (!capogiocoUguale(idGiocatore)) {
            throw new IllegalStateException("Solo il capogioco puo avviare la partita");
        }
        if (giocatori.isEmpty()) {
            throw new IllegalStateException("Servono giocatori per iniziare");
        }
        lettera = (char) ('A' + casuale.nextInt(26));  // lettera casuale da A a Z
        turno = 1;
        fase = Fase.RISPOSTE;
    }

    /**
     * Un giocatore invia le sue risposte per la lettera corrente.
     * Le chiavi della mappa sono le categorie; valori vuoti = risposta omessa.
     */
    public void inviaRisposte(String idGiocatore, Map<String, String> date) {
        richiediFase(Fase.RISPOSTE);
        richiediGiocatore(idGiocatore);
        if (giocatoriInviati.contains(idGiocatore)) {
            throw new IllegalStateException("Risposte gia inviate");
        }
        if (date == null) {
            throw new IllegalArgumentException("Risposte non valide");
        }
        // Prima verifica: categorie esistenti e lettera iniziale corretta.
        for (Map.Entry<String, String> voce : date.entrySet()) {
            if (!categorie.contains(voce.getKey())) {
                throw new IllegalArgumentException("Categoria sconosciuta: " + voce.getKey());
            }
            String testo = togliSpazi(voce.getValue());
            if (!testo.isEmpty() && maiuscolo(testo.charAt(0)) != lettera) {
                throw new IllegalArgumentException("La risposta deve iniziare con " + lettera);
            }
        }
        // Poi registra solo le risposte non vuote, in ordine di categoria.
        for (String categoria : categorie) {
            String testo = togliSpazi(date.get(categoria));
            if (!testo.isEmpty()) {
                risposte.add(new Risposta(prossimaIdRisposta++, idGiocatore, categoria, testo));
            }
        }
        giocatoriInviati.add(idGiocatore);
    }

    /** Si passa dalla fase risposte alla fase di votazione. */
    public void iniziaVotazione() {
        richiediFase(Fase.RISPOSTE);
        fase = Fase.VOTAZIONE;
    }

    /** Un giocatore vota si/no una risposta altrui, una sola volta per risposta. */
    public void vota(String idGiocatore, long idRisposta, boolean favorevole) {
        richiediFase(Fase.VOTAZIONE);
        richiediGiocatore(idGiocatore);
        Risposta risposta = trovaRisposta(idRisposta);
        if (risposta == null) {
            throw new IllegalArgumentException("Risposta inesistente");
        }
        if (risposta.idGiocatore.equals(idGiocatore)) {
            throw new IllegalStateException("Non puoi votare la tua risposta");
        }
        if (risposta.votanti.contains(idGiocatore)) {
            throw new IllegalStateException("Hai gia votato questa risposta");
        }
        risposta.votanti.add(idGiocatore);
        if (favorevole) {
            risposta.votiFavorevoli++;
        } else {
            risposta.votiContrari++;
        }
    }

    /** Chiude le votazioni, assegna i punti e dichiara i vincitori. */
    public void finisciVotazione() {
        richiediFase(Fase.VOTAZIONE);
        // Ogni risposta approvata dalla maggioranza vale un punto al suo autore.
        for (Risposta r : risposte) {
            r.approvata = r.votiFavorevoli > r.votiContrari;
            if (r.approvata) {
                trovaGiocatore(r.idGiocatore).punteggio++;
            }
        }
        // Trova il punteggio piu alto...
        int migliore = -1;
        for (Giocatore g : giocatori) {
            migliore = Math.max(migliore, g.punteggio);
        }
        // ...e tutti i giocatori con quel punteggio vincono (a pari merito).
        for (Giocatore g : giocatori) {
            if (g.punteggio == migliore) {
                idVincitori.add(g.id);
            }
        }
        fase = Fase.TERMINATO;
    }

    // --- piccoli helper ---

    /** L'azione e permessa solo nella fase indicata. */
    private void richiediFase(Fase attesa) {
        if (fase != attesa) {
            throw new IllegalStateException("Azione non valida in fase " + fase);
        }
    }

    /** Il giocatore deve esistere. */
    private void richiediGiocatore(String idGiocatore) {
        if (trovaGiocatore(idGiocatore) == null) {
            throw new IllegalArgumentException("Giocatore sconosciuto: " + idGiocatore);
        }
    }

    /** Cerca un giocatore per id, o null se non trovato. */
    private Giocatore trovaGiocatore(String id) {
        for (Giocatore g : giocatori) {
            if (g.id.equals(id)) {
                return g;
            }
        }
        return null;
    }

    /** Cerca una risposta per id, o null se non trovata. */
    private Risposta trovaRisposta(long idRisposta) {
        for (Risposta r : risposte) {
            if (r.id == idRisposta) {
                return r;
            }
        }
        return null;
    }

    /** True se il giocatore indicato e il capogioco. */
    private boolean capogiocoUguale(String idGiocatore) {
        return idCapogioco != null && idCapogioco.equals(idGiocatore);
    }

    /** Togli gli spazi ai bordi (null -> stringa vuota). */
    private static String togliSpazi(String valore) {
        return valore == null ? "" : valore.trim();
    }

    /** Converte una lettera minuscola in maiuscola (per il confronto con la lettera). */
    private static char maiuscolo(char c) {
        return (c >= 'a' && c <= 'z') ? (char) (c - 'a' + 'A') : c;
    }
}
