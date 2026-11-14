import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class GameEngine {
    public enum Phase {
        LOBBY,
        ANSWERING,
        VOTING,
        FINISHED
    }

    public enum Result {
        OK,
        INVALID_PLAYER,
        INVALID_NAME,
        DUPLICATE_PLAYER,
        WRONG_PHASE,
        NOT_HOST,
        NOT_ENOUGH_PLAYERS,
        INVALID_ANSWER,
        ALREADY_SUBMITTED,
        INVALID_ANSWER_ID,
        CANNOT_VOTE_OWN_ANSWER,
        ALREADY_VOTED
    }

    public static final class PlayerView {
        public final String id;
        public final String name;
        public final int score;

        private PlayerView(String id, String name, int score) {
            this.id = id;
            this.name = name;
            this.score = score;
        }
    }

    public static final class AnswerView {
        public final long id;
        public final String playerId;
        public final String playerName;
        public final String category;
        public final String text;
        public final int votesFor;
        public final int votesAgainst;
        public final boolean approved;

        private AnswerView(long id, String playerId, String playerName, String category,
                           String text, int votesFor, int votesAgainst, boolean approved) {
            this.id = id;
            this.playerId = playerId;
            this.playerName = playerName;
            this.category = category;
            this.text = text;
            this.votesFor = votesFor;
            this.votesAgainst = votesAgainst;
            this.approved = approved;
        }
    }

    public static final class Snapshot {
        public final Phase phase;
        public final String hostId;
        public final char letter;
        public final int round;
        public final List<String> categories;
        public final List<PlayerView> players;
        public final List<AnswerView> answers;
        public final List<String> winnerIds;

        private Snapshot(Phase phase, String hostId, char letter, int round,
                         List<String> categories, List<PlayerView> players,
                         List<AnswerView> answers, List<String> winnerIds) {
            this.phase = phase;
            this.hostId = hostId;
            this.letter = letter;
            this.round = round;
            this.categories = Collections.unmodifiableList(categories);
            this.players = Collections.unmodifiableList(players);
            this.answers = Collections.unmodifiableList(answers);
            this.winnerIds = Collections.unmodifiableList(winnerIds);
        }
    }

    private static final class Player {
        private final String name;
        private int score;

        private Player(String name) {
            this.name = name;
        }
    }

    private static final class Answer {
        private final long id;
        private final String playerId;
        private final String category;
        private final String text;
        private final Map<String, Boolean> votes = new LinkedHashMap<>();
        private boolean approved;

        private Answer(long id, String playerId, String category, String text) {
            this.id = id;
            this.playerId = playerId;
            this.category = category;
            this.text = text;
        }
    }

    private final List<String> categories;
    private final Random random;
    private final Map<String, Player> players = new LinkedHashMap<>();
    private final List<Answer> answers = new ArrayList<>();
    private final Set<String> submittedPlayers = new LinkedHashSet<>();
    private final List<String> winnerIds = new ArrayList<>();
    private String hostId;
    private Phase phase = Phase.LOBBY;
    private char letter;
    private int round;
    private long nextAnswerId = 1;

    public GameEngine() {
        this(defaultCategories(), new Random());
    }

    public GameEngine(List<String> categories, long randomSeed) {
        this(categories, new Random(randomSeed));
    }

    private GameEngine(List<String> categories, Random random) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("At least one category is required");
        }
        Set<String> uniqueCategories = new LinkedHashSet<>();
        for (String category : categories) {
            if (category == null || category.trim().isEmpty() || !uniqueCategories.add(category)) {
                throw new IllegalArgumentException("Categories must be non-empty and unique");
            }
        }
        this.categories = Collections.unmodifiableList(new ArrayList<>(categories));
        this.random = random;
    }

    private static List<String> defaultCategories() {
        List<String> categories = new ArrayList<>();
        categories.add("Nome");
        categories.add("Cosa");
        categories.add("Citta");
        return categories;
    }

    public synchronized Result addPlayer(String id, String name) {
        if (phase != Phase.LOBBY) {
            return Result.WRONG_PHASE;
        }
        if (id == null || id.isEmpty()) {
            return Result.INVALID_PLAYER;
        }
        if (name == null || name.trim().isEmpty()) {
            return Result.INVALID_NAME;
        }
        if (players.containsKey(id)) {
            return Result.DUPLICATE_PLAYER;
        }

        players.put(id, new Player(name));
        if (hostId == null) {
            hostId = id;
        }
        return Result.OK;
    }

    public synchronized Result start(String playerId) {
        if (phase != Phase.LOBBY) {
            return Result.WRONG_PHASE;
        }
        if (hostId == null || !hostId.equals(playerId)) {
            return Result.NOT_HOST;
        }
        if (players.size() < 2) {
            return Result.NOT_ENOUGH_PLAYERS;
        }

        letter = (char) ('A' + random.nextInt(26));
        round = 1;
        phase = Phase.ANSWERING;
        return Result.OK;
    }

    public synchronized Result submitAnswers(String playerId, Map<String, String> submittedAnswers) {
        if (phase != Phase.ANSWERING) {
            return Result.WRONG_PHASE;
        }
        if (!players.containsKey(playerId)) {
            return Result.INVALID_PLAYER;
        }
        if (submittedPlayers.contains(playerId)) {
            return Result.ALREADY_SUBMITTED;
        }
        if (submittedAnswers == null) {
            return Result.INVALID_ANSWER;
        }

        for (Map.Entry<String, String> entry : submittedAnswers.entrySet()) {
            if (!categories.contains(entry.getKey())) {
                return Result.INVALID_ANSWER;
            }
            String text = trim(entry.getValue());
            if (!text.isEmpty() && uppercaseAscii(text.charAt(0)) != letter) {
                return Result.INVALID_ANSWER;
            }
        }

        for (String category : categories) {
            String text = trim(submittedAnswers.get(category));
            if (!text.isEmpty()) {
                answers.add(new Answer(nextAnswerId++, playerId, category, text));
            }
        }
        submittedPlayers.add(playerId);
        return Result.OK;
    }

    public synchronized Result beginVoting() {
        if (phase != Phase.ANSWERING) {
            return Result.WRONG_PHASE;
        }
        phase = Phase.VOTING;
        return Result.OK;
    }

    public synchronized Result castVote(String playerId, long answerId, boolean approve) {
        if (phase != Phase.VOTING) {
            return Result.WRONG_PHASE;
        }
        if (!players.containsKey(playerId)) {
            return Result.INVALID_PLAYER;
        }

        Answer answer = findAnswer(answerId);
        if (answer == null) {
            return Result.INVALID_ANSWER_ID;
        }
        if (answer.playerId.equals(playerId)) {
            return Result.CANNOT_VOTE_OWN_ANSWER;
        }
        if (answer.votes.containsKey(playerId)) {
            return Result.ALREADY_VOTED;
        }
        answer.votes.put(playerId, approve);
        return Result.OK;
    }

    public synchronized Result finishVoting() {
        if (phase != Phase.VOTING) {
            return Result.WRONG_PHASE;
        }

        for (Answer answer : answers) {
            int votesFor = 0;
            int votesAgainst = 0;
            for (boolean vote : answer.votes.values()) {
                if (vote) {
                    votesFor++;
                } else {
                    votesAgainst++;
                }
            }
            answer.approved = votesFor > votesAgainst;
            if (answer.approved) {
                players.get(answer.playerId).score++;
            }
        }

        int highestScore = -1;
        for (Player player : players.values()) {
            highestScore = Math.max(highestScore, player.score);
        }
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            if (entry.getValue().score == highestScore) {
                winnerIds.add(entry.getKey());
            }
        }
        phase = Phase.FINISHED;
        return Result.OK;
    }

    public synchronized Snapshot snapshot() {
        List<PlayerView> playerViews = new ArrayList<>();
        for (Map.Entry<String, Player> entry : players.entrySet()) {
            playerViews.add(new PlayerView(entry.getKey(), entry.getValue().name, entry.getValue().score));
        }

        List<AnswerView> answerViews = new ArrayList<>();
        for (Answer answer : answers) {
            int votesFor = 0;
            int votesAgainst = 0;
            for (boolean vote : answer.votes.values()) {
                if (vote) {
                    votesFor++;
                } else {
                    votesAgainst++;
                }
            }
            Player player = players.get(answer.playerId);
            answerViews.add(new AnswerView(
                    answer.id, answer.playerId, player == null ? "" : player.name,
                    answer.category, answer.text, votesFor, votesAgainst, answer.approved));
        }

        return new Snapshot(
                phase, hostId, letter, round, new ArrayList<>(categories),
                playerViews, answerViews, new ArrayList<>(winnerIds));
    }

    private Answer findAnswer(long answerId) {
        for (Answer answer : answers) {
            if (answer.id == answerId) {
                return answer;
            }
        }
        return null;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static char uppercaseAscii(char value) {
        return value >= 'a' && value <= 'z' ? (char) (value - 'a' + 'A') : value;
    }
}
