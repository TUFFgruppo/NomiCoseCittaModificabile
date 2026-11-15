import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Motore di gioco semplificato (tipo "Nomi, Cose, Città").
 * Stesso comportamento pubblico della versione precedente, ma con
 * molto meno codice: niente snapshot immutabili, niente enum di
 * risultati, niente classi di supporto. Gli errori sono segnalati
 * lanciando IllegalArgumentException.
 */
public class GameEngine {

    /** Fasi del gioco. */
    public enum Phase { LOBBY, ANSWERING, VOTING, FINISHED }

    /** Un giocatore: nome e punteggio. */
    public static class Player {
        public final String id;
        public final String name;
        public int score;

        Player(String id, String name) {
            this.id = id;
            this.name = name;
            this.score = 0;
        }
    }

    /** Una risposta data da un giocatore per una categoria. */
    public static class Answer {
        public final long id;
        public final String playerId;
        public final String category;
        public final String text;
        public boolean approved;

        /** numero di voti favorevoli (letto nei test) */
        public int votesFor;
        /** numero di voti contrari (letto nei test) */
        public int votesAgainst;

        final List<String> voters = new ArrayList<>();

        Answer(long id, String playerId, String category, String text) {
            this.id = id;
            this.playerId = playerId;
            this.category = category;
            this.text = text;
        }
    }

    private final List<String> categories;
    private final Random random;

    public final List<Player> players = new ArrayList<>();
    public final List<Answer> answers = new ArrayList<>();

    public Phase phase = Phase.LOBBY;
    public String hostId;      // id del primo giocatore che entra
    public char letter;        // lettera iniziale della partita
    public int round;          // numero del turno corrente
    public final List<String> winnerIds = new ArrayList<>();

    private final List<String> submittedPlayers = new ArrayList<>();
    private long nextAnswerId = 1;

    public GameEngine() {
        this(defaultCategories(), new Random());
    }

    public GameEngine(List<String> categories, long seed) {
        this(categories, new Random(seed));
    }

    private GameEngine(List<String> categories, Random random) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("Serve almeno una categoria");
        }
        this.categories = new ArrayList<>(categories);
        this.random = random;
    }

    private static List<String> defaultCategories() {
        return List.of("Nome", "Cosa", "Citta");
    }

    /** Aggiunge un giocatore nella fase di lobby. */
    public void addPlayer(String id, String name) {
        requirePhase(Phase.LOBBY);
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID giocatore non valido");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Nome giocatore non valido");
        }
        if (findPlayer(id) != null) {
            throw new IllegalArgumentException("Giocatore gia presente");
        }
        players.add(new Player(id, name));
        if (hostId == null) {
            hostId = id;
        }
    }

    /** Il capogioco avvia la partita: serve almeno un giocatore. */
    public void start(String playerId) {
        requirePhase(Phase.LOBBY);
        if (!hostIdEquals(playerId)) {
            throw new IllegalStateException("Solo il capogioco puo avviare la partita");
        }
        if (players.isEmpty()) {
            throw new IllegalStateException("Servono giocatori per iniziare");
        }
        letter = (char) ('A' + random.nextInt(26));
        round = 1;
        phase = Phase.ANSWERING;
    }

    /** Un giocatore invia le sue risposte per la lettera corrente. */
    public void submitAnswers(String playerId, Map<String, String> given) {
        requirePhase(Phase.ANSWERING);
        requirePlayer(playerId);
        if (submittedPlayers.contains(playerId)) {
            throw new IllegalStateException("Risposte gia inviate");
        }
        if (given == null) {
            throw new IllegalArgumentException("Risposte non valide");
        }
        for (Map.Entry<String, String> e : given.entrySet()) {
            if (!categories.contains(e.getKey())) {
                throw new IllegalArgumentException("Categoria sconosciuta: " + e.getKey());
            }
            String text = trim(e.getValue());
            if (!text.isEmpty() && upper(text.charAt(0)) != letter) {
                throw new IllegalArgumentException("La risposta deve iniziare con " + letter);
            }
        }
        for (String category : categories) {
            String text = trim(given.get(category));
            if (!text.isEmpty()) {
                answers.add(new Answer(nextAnswerId++, playerId, category, text));
            }
        }
        submittedPlayers.add(playerId);
    }

    /** Si passa alla fase di votazione. */
    public void beginVoting() {
        requirePhase(Phase.ANSWERING);
        phase = Phase.VOTING;
    }

    /** Un giocatore vota si/no una risposta altrui, una sola volta. */
    public void castVote(String playerId, long answerId, boolean approve) {
        requirePhase(Phase.VOTING);
        requirePlayer(playerId);
        Answer answer = findAnswer(answerId);
        if (answer == null) {
            throw new IllegalArgumentException("Risposta inesistente");
        }
        if (answer.playerId.equals(playerId)) {
            throw new IllegalStateException("Non puoi votare la tua risposta");
        }
        if (answer.voters.contains(playerId)) {
            throw new IllegalStateException("Hai gia votato questa risposta");
        }
        answer.voters.add(playerId);
        if (approve) {
            answer.votesFor++;
        } else {
            answer.votesAgainst++;
        }
    }

    /** Chiude le votazioni, assegna i punti e dichiara i vincitori. */
    public void finishVoting() {
        requirePhase(Phase.VOTING);
        int best = -1;
        for (Answer a : answers) {
            a.approved = a.votesFor > a.votesAgainst;
            if (a.approved) {
                findPlayer(a.playerId).score++;
            }
        }
        for (Player p : players) {
            best = Math.max(best, p.score);
        }
        for (Player p : players) {
            if (p.score == best) {
                winnerIds.add(p.id);
            }
        }
        phase = Phase.FINISHED;
    }

    // --- piccoli helper ---

    private void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException("Azione non valida in fase " + phase);
        }
    }

    private void requirePlayer(String playerId) {
        if (findPlayer(playerId) == null) {
            throw new IllegalArgumentException("Giocatore sconosciuto: " + playerId);
        }
    }

    private Player findPlayer(String id) {
        for (Player p : players) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private Answer findAnswer(long answerId) {
        for (Answer a : answers) {
            if (a.id == answerId) {
                return a;
            }
        }
        return null;
    }

    private boolean hostIdEquals(String playerId) {
        return hostId != null && hostId.equals(playerId);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static char upper(char c) {
        return (c >= 'a' && c <= 'z') ? (char) (c - 'a' + 'A') : c;
    }
}
