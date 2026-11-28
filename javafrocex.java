import java.util.*;

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
        public final List<String> categories;
        public final List<PlayerView> players;
        public final List<AnswerView> answers;
        public final List<String> winnerIds;

        private Snapshot(Phase phase, String hostId, char letter, List<String> categories,
                         List<PlayerView> players, List<AnswerView> answers, List<String> winnerIds) {
            this.phase = phase;
            this.hostId = hostId;
            this.letter = letter;
            this.categories = List.copyOf(categories);
            this.players = List.copyOf(players);
            this.answers = List.copyOf(answers);
            this.winnerIds = List.copyOf(winnerIds);
        }
    }

    private static final class Player {
        final String name;
        int score;

        Player(String name) {
            this.name = name;
        }
    }

    private static final class Answer {
        final long id;
        final String playerId;
        final String category;
        final String text;
        final Map<String, Boolean> votes = new HashMap<>();
        boolean approved;

        Answer(long id, String playerId, String category, String text) {
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
    private final Set<String> submittedPlayers = new HashSet<>();
    private final List<String> winnerIds = new ArrayList<>();

    private String hostId;
    private Phase phase = Phase.LOBBY;
    private char letter;
    private long nextAnswerId = 1;

    public GameEngine() {
        this(List.of("Nome", "Cosa", "Citta"), new Random());
    }

    public GameEngine(List<String> categories, long randomSeed) {
        this(categories, new Random(randomSeed));
    }

    private GameEngine(List<String> categories, Random random) {
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("At least one category is required");
        }

        Set<String> unique = new HashSet<>();
        for (String category : categories) {
            if (category == null || category.isBlank() || !unique.add(category)) {
                throw new IllegalArgumentException("Categories must be non-empty and unique");
            }
        }

        this.categories = List.copyOf(categories);
        this.random = random;
    }

    public synchronized Result addPlayer(String id, String name) {
        if (phase != Phase.LOBBY) {
            return Result.WRONG_PHASE;
        }
        if (id == null || id.isBlank()) {
            return Result.INVALID_PLAYER;
        }
        if (name == null || name.isBlank()) {
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
        if (!Objects.equals(hostId, playerId)) {
            return Result.NOT_HOST;
        }
        if (players.size() < 2) {
            return Result.NOT_ENOUGH_PLAYERS;
        }

        letter = (char) ('A' + random.nextInt(26));
        phase = Phase.ANSWERING;
        return Result.OK;
    }

    public synchronized Result submitAnswers(String playerId, Map<String, String> submitted) {
        if (phase != Phase.ANSWERING) {
            return Result.WRONG_PHASE;
        }
        if (!players.containsKey(playerId)) {
            return Result.INVALID_PLAYER;
        }
        if (submittedPlayers.contains(playerId)) {
            return Result.ALREADY_SUBMITTED;
        }
        if (submitted == null || !categories.containsAll(submitted.keySet())) {
            return Result.INVALID_ANSWER;
        }

        for (String category : categories) {
            String text = trim(submitted.get(category));
            if (!text.isEmpty() && Character.toUpperCase(text.charAt(0)) != letter) {
                return Result.INVALID_ANSWER;
            }
        }

        for (String category : categories) {
            String text = trim(submitted.get(category));
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
        if (answer.votes.putIfAbsent(playerId, approve) != null) {
            return Result.ALREADY_VOTED;
        }
        return Result.OK;
    }

    public synchronized Result finishVoting() {
        if (phase != Phase.VOTING) {
            return Result.WRONG_PHASE;
        }

        for (Answer answer : answers) {
            int[] votes = countVotes(answer);
            answer.approved = votes[0] > votes[1];
            if (answer.approved) {
                players.get(answer.playerId).score++;
            }
        }

        int high = players.values().stream().mapToInt(player -> player.score).max().orElse(0);
        players.forEach((id, player) -> {
            if (player.score == high) {
                winnerIds.add(id);
            }
        });

        phase = Phase.FINISHED;
        return Result.OK;
    }

    public synchronized Snapshot snapshot() {
        List<PlayerView> playerViews = new ArrayList<>();
        for (var entry : players.entrySet()) {
            playerViews.add(new PlayerView(entry.getKey(), entry.getValue().name, entry.getValue().score));
        }

        List<AnswerView> answerViews = new ArrayList<>();
        for (Answer answer : answers) {
            int[] votes = countVotes(answer);
            answerViews.add(new AnswerView(
                    answer.id,
                    answer.playerId,
                    players.get(answer.playerId).name,
                    answer.category,
                    answer.text,
                    votes[0],
                    votes[1],
                    answer.approved
            ));
        }

        return new Snapshot(
                phase,
                hostId,
                letter,
                categories,
                playerViews,
                answerViews,
                winnerIds
        );
    }

    private Answer findAnswer(long answerId) {
        for (Answer answer : answers) {
            if (answer.id == answerId) {
                return answer;
            }
        }
        return null;
    }

    private static int[] countVotes(Answer answer) {
        int forVotes = 0;
        for (boolean vote : answer.votes.values()) {
            if (vote) {
                forVotes++;
            }
        }
        return new int[] { forVotes, answer.votes.size() - forVotes };
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
