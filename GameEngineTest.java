import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test del GameEngine semplificato (nomi delle variabili in italiano).
 * Gli errori sono segnalati con eccezioni, quindi i test usano
 * questi helper invece dei codici di risultato della vecchia API.
 */
public final class GameEngineTest {

    public static void main(String[] args) {
        GameEngine gioco = new GameEngine(categorieDiDefault(), 1234);

//        verifica che l'azione riesca / fallisca con l'eccezione attesa
        checkOk(() -> gioco.aggiungiGiocatore("p1", "Carlo"), "first player joins");
        checkOk(() -> gioco.aggiungiGiocatore("p2", "Agrelli"), "second player joins");
        checkOk(() -> gioco.aggiungiGiocatore("p3", "Leo"), "third player joins");
        check("p1".equals(gioco.idCapogioco), "first player is host");
        checkThrows(() -> gioco.inizia("p2"), IllegalStateException.class, "only host can start");
        checkOk(() -> gioco.inizia("p1"), "host starts game");

        String prefisso = String.valueOf(gioco.lettera);
        Map<String, String> risposteP1 = Map.of(
                "Nome", prefisso + "nome",
                "Cosa", prefisso + "cosa",
                "Citta", prefisso + "citta");
        checkOk(() -> gioco.inviaRisposte("p1", risposteP1), "valid answers submitted");

        checkThrows(() -> gioco.inviaRisposte("p2", Map.of("Nome", "Risposta non valida")),
                IllegalArgumentException.class, "wrong initial letter rejected");
        checkOk(() -> gioco.inviaRisposte("p2", Map.of("Nome", prefisso + "nome")),
                "valid answer submitted");
        checkOk(gioco::iniziaVotazione, "voting begins");

        long idRispostaP1 = gioco.risposte.get(0).id;
        checkThrows(() -> gioco.vota("p1", idRispostaP1, true),
                IllegalStateException.class, "players cannot vote on own answer");
        checkOk(() -> gioco.vota("p2", idRispostaP1, true), "first vote accepted");
        checkThrows(() -> gioco.vota("p2", idRispostaP1, false),
                IllegalStateException.class, "duplicate vote rejected");
        checkOk(() -> gioco.vota("p3", idRispostaP1, true), "second vote accepted");
        checkOk(gioco::finisciVotazione, "voting finishes");

        check(gioco.fase == GameEngine.Fase.TERMINATO, "game is finished");
        check(gioco.risposte.get(0).approvata, "majority answer approved");
        check(gioco.giocatori.get(0).punteggio == 1, "approved answer scores a point");
        check(gioco.idVincitori.size() == 1 && "p1".equals(gioco.idVincitori.get(0)),
                "highest scorer wins");
        System.out.println("All GameEngine tests passed.");
    }

    /** Categorie di gioco usate nei test. */
    private static List<String> categorieDiDefault() {
        List<String> categorie = new ArrayList<>();
        categorie.add("Nome");
        categorie.add("Cosa");
        categorie.add("Citta");
        return categorie;
    }

    /** La condizione deve essere vera. */
    private static void check(boolean condizione, String descrizione) {
        if (!condizione) {
            throw new AssertionError("Test failed: " + descrizione);
        }
    }

    /** L'azione deve riuscire senza lanciare eccezioni. */
    private static void checkOk(Runnable azione, String descrizione) {
        try {
            azione.run();
        } catch (RuntimeException e) {
            throw new AssertionError("Test failed: " + descrizione + " -> " + e, e);
        }
    }

    /** L'azione deve fallire con il tipo di eccezione atteso. */
    private static void checkThrows(Runnable azione, Class<? extends Exception> attesa,
                                    String descrizione) {
        try {
            azione.run();
        } catch (Exception e) {
            if (!attesa.isInstance(e)) {
                throw new AssertionError("Test failed: " + descrizione
                        + " -> attesa " + attesa.getSimpleName() + ", ricevuta " + e);
            }
            return;
        }
        throw new AssertionError("Test failed: " + descrizione + " -> nessuna eccezione lanciata");
    }
}
