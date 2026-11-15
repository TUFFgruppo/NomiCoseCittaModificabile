import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * sempilce -> versione semplificata del motore di gioco.
 * Gli errori sono segnalati con eccezioni, quindi i test usano
 * questi helper invece dei codici di risultato della vecchia API.
 */
public final class GameEngineTest {

    public static void main(String[] args) {
        GameEngine game = new GameEngine(defaultCategories(), 1234);

        checkOk(() -> game.addPlayer("p1", "Carlo"), "first player joins");
        checkOk(() -> game.addPlayer("p2", "Agrelli"), "second player joins");
        checkOk(() -> game.addPlayer("p3", "Leo"), "third player joins");
        check("p1".equals(game.hostId), "first player is host");
        checkThrows(() -> game.start("p2"), IllegalStateException.class, "only host can start");
        checkOk(() -> game.start("p1"), "host starts game");

        String prefix = String.valueOf(game.letter);
        Map<String, String> p1Answers = Map.of(
                "Nome", prefix + "nome",
                "Cosa", prefix + "cosa",
                "Citta", prefix + "citta");
        checkOk(() -> game.submitAnswers("p1", p1Answers), "valid answers submitted");

        checkThrows(() -> game.submitAnswers("p2", Map.of("Nome", "Risposta non valida")),
                IllegalArgumentException.class, "wrong initial letter rejected");
        checkOk(() -> game.submitAnswers("p2", Map.of("Nome", prefix + "nome")),
                "valid answer submitted");
        checkOk(game::beginVoting, "voting begins");

        long p1AnswerId = game.answers.get(0).id;
        checkThrows(() -> game.castVote("p1", p1AnswerId, true),
                IllegalStateException.class, "players cannot vote on own answer");
        checkOk(() -> game.castVote("p2", p1AnswerId, true), "first vote accepted");
        checkThrows(() -> game.castVote("p2", p1AnswerId, false),
                IllegalStateException.class, "duplicate vote rejected");
        checkOk(() -> game.castVote("p3", p1AnswerId, true), "second vote accepted");
        checkOk(game::finishVoting, "voting finishes");

        check(game.phase == GameEngine.Phase.FINISHED, "game is finished");
        check(game.answers.get(0).approved, "majority answer approved");
        check(game.players.get(0).score == 1, "approved answer scores a point");
        check(game.winnerIds.size() == 1 && "p1".equals(game.winnerIds.get(0)),
                "highest scorer wins");
        System.out.println("All GameEngine tests passed.");
    }

    private static List<String> defaultCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("Nome");
        categories.add("Cosa");
        categories.add("Citta");
        return categories;
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Test failed: " + description);
        }
    }

    /** L'azione deve riuscire senza lanciare eccezioni. */
    private static void checkOk(Runnable action, String description) {
        try {
            action.run();
        } catch (RuntimeException e) {
            throw new AssertionError("Test failed: " + description + " -> " + e, e);
        }
    }

    /** L'azione deve fallire con il tipo di eccezione atteso. */
    private static void checkThrows(Runnable action, Class<? extends Exception> expected,
                                    String description) {
        try {
            action.run();
        } catch (Exception e) {
            if (!expected.isInstance(e)) {
                throw new AssertionError("Test failed: " + description
                        + " -> attesa " + expected.getSimpleName() + ", ricevuta " + e);
            }
            return;
        }
        throw new AssertionError("Test failed: " + description + " -> nessuna eccezione lanciata");
    }
}
