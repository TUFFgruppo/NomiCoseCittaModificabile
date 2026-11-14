import java.util.LinkedHashMap;
import java.util.Map;

public final class GameEngineTest {
    public static void main(String[] args) {
        GameEngine game = new GameEngine(GameEngineTest.defaultCategories(), 1234);

        check(game.addPlayer("p1", "Carlo") == GameEngine.Result.OK, "first player joins");
        check(game.addPlayer("p2", "Agrelli") == GameEngine.Result.OK, "second player joins");
        check(game.addPlayer("p3", "Leo") == GameEngine.Result.OK, "third player joins");
        check("p1".equals(game.snapshot().hostId), "first player is host");
        check(game.start("p2") == GameEngine.Result.NOT_HOST, "only host can start");
        check(game.start("p1") == GameEngine.Result.OK, "host starts game");

        String prefix = String.valueOf(game.snapshot().letter);
        Map<String, String> p1Answers = new LinkedHashMap<>();
        p1Answers.put("Nome", prefix + "nome");
        p1Answers.put("Cosa", prefix + "cosa");
        p1Answers.put("Citta", prefix + "citta");
        check(game.submitAnswers("p1", p1Answers) == GameEngine.Result.OK, "valid answers submitted");

        check(game.submitAnswers("p2", answers("Nome", "Risposta non valida"))
                == GameEngine.Result.INVALID_ANSWER, "wrong initial letter rejected");
        check(game.submitAnswers("p2", answers("Nome", prefix + "nome"))
                == GameEngine.Result.OK, "valid answer submitted");
        check(game.beginVoting() == GameEngine.Result.OK, "voting begins");

        GameEngine.Snapshot beforeVotes = game.snapshot();
        long p1AnswerId = beforeVotes.answers.get(0).id;
        check(game.castVote("p1", p1AnswerId, true) == GameEngine.Result.CANNOT_VOTE_OWN_ANSWER,
                "players cannot vote on own answer");
        check(game.castVote("p2", p1AnswerId, true) == GameEngine.Result.OK, "first vote accepted");
        check(game.castVote("p2", p1AnswerId, false) == GameEngine.Result.ALREADY_VOTED,
                "duplicate vote rejected");
        check(game.castVote("p3", p1AnswerId, true) == GameEngine.Result.OK, "second vote accepted");
        check(game.finishVoting() == GameEngine.Result.OK, "voting finishes");

        GameEngine.Snapshot finished = game.snapshot();
        check(finished.phase == GameEngine.Phase.FINISHED, "game is finished");
        check(finished.answers.get(0).approved, "majority answer approved");
        check(finished.players.get(0).score == 1, "approved answer scores a point");
        check(finished.winnerIds.size() == 1 && "p1".equals(finished.winnerIds.get(0)),
                "highest scorer wins");
        System.out.println("All GameEngine tests passed.");
    }

    private static Map<String, String> answers(String category, String text) {
        Map<String, String> answers = new LinkedHashMap<>();
        answers.put(category, text);
        return answers;
    }

    private static java.util.List<String> defaultCategories() {
        java.util.List<String> categories = new java.util.ArrayList<>();
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
}
