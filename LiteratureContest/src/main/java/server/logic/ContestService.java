package server.logic;

import server.storage.Storage;
import server.time.ConfigService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Минимальная файловая «база» для конкурса. Без сторонних библиотек,
 * поэтому формат хранения максимально простой: CSV с разделителем "|".
 */
public class ContestService {

    public record Submission(String clientId, String submissionId, String title,
                             String fileName, long receivedAtUtc) {
    }

    public record Assignment(String clientId, List<String> storyIds) {
    }

    public record Review(String reviewerId, String storyId, int score, long receivedAtUtc) {
    }

    public record Results(List<ResultItem> items, long generatedAtUtc) {
    }

    public record ResultItem(String storyId, String title, double avgScore, int reviewsCount) {
    }

    private final Path submissionsCsv   = Storage.ROOT.resolve("registry/submissions.csv");
    private final Path assignmentsCsv   = Storage.ROOT.resolve("registry/assignments.csv");
    private final Path reviewsIndexCsv  = Storage.ROOT.resolve("registry/reviews_index.csv");
    private final Path resultsJson      = Storage.ROOT.resolve("results/final.json");

    public ContestService() throws IOException {
        Files.createDirectories(submissionsCsv.getParent());
        Files.createDirectories(resultsJson.getParent());
        touch(submissionsCsv);
        touch(assignmentsCsv);
        touch(reviewsIndexCsv);
        touch(resultsJson);
    }

    /* ===================== submissions ===================== */

    public synchronized Submission registerTextSubmission(String clientId, String title, String text) throws IOException {
        Path dir = Storage.ROOT.resolve("submissions").resolve(safe(clientId));
        Files.createDirectories(dir);
        Path file = dir.resolve("story.txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return registerSubmission(clientId, title, file.getFileName().toString());
    }

    public synchronized Submission registerBinarySubmission(String clientId, String title, String ext, byte[] body) throws IOException {
        Path dir = Storage.ROOT.resolve("submissions").resolve(safe(clientId));
        Files.createDirectories(dir);
        Path file = dir.resolve("story" + ext);
        Files.write(file, body);
        return registerSubmission(clientId, title, file.getFileName().toString());
    }

    private Submission registerSubmission(String clientId, String title, String fileName) throws IOException {
        List<Submission> all = loadSubmissions();

        Submission existing = all.stream()
                .filter(s -> s.clientId().equalsIgnoreCase(clientId))
                .findFirst()
                .orElse(null);

        String submissionId = existing != null ? existing.submissionId() : UUID.randomUUID().toString();
        long now = Instant.now().toEpochMilli();
        Submission updated = new Submission(clientId, submissionId, sanitize(title), fileName, now);

        all = all.stream()
                .filter(s -> !s.clientId().equalsIgnoreCase(clientId))
                .collect(Collectors.toCollection(ArrayList::new));
        all.add(updated);

        saveSubmissions(all);
        invalidateAssignmentsIfChanged(all);
        return updated;
    }

    public List<Submission> loadSubmissions() throws IOException {
        if (Files.notExists(submissionsCsv)) return List.of();
        List<Submission> list = new ArrayList<>();
        for (String line : Files.readAllLines(submissionsCsv, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 5) continue;
            list.add(new Submission(p[0], p[1], p[2], p[3], parseLong(p[4])));
        }
        return list;
    }

    private void saveSubmissions(List<Submission> list) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Submission s : list) {
            lines.add(String.join("|",
                    sanitize(s.clientId()),
                    sanitize(s.submissionId()),
                    sanitize(s.title()),
                    sanitize(s.fileName()),
                    Long.toString(s.receivedAtUtc())));
        }
        Files.write(submissionsCsv, lines, StandardCharsets.UTF_8);
    }

    /* ===================== assignments ===================== */

    public synchronized Assignment assignmentsFor(String clientId) throws IOException {
        List<Submission> submissions = loadSubmissions();
        regenerateAssignmentsIfNeeded(submissions);

        Map<String, Assignment> map = loadAssignments();
        return map.getOrDefault(clientId, new Assignment(clientId, List.of()));
    }

    private Map<String, Assignment> loadAssignments() throws IOException {
        Map<String, Assignment> map = new HashMap<>();
        if (Files.notExists(assignmentsCsv)) return map;
        for (String line : Files.readAllLines(assignmentsCsv, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 2) continue;
            String client = p[0];
            List<String> ids = p.length == 1 || p[1].isBlank()
                    ? List.of()
                    : Arrays.asList(p[1].split(","));
            map.put(client, new Assignment(client, ids));
        }
        return map;
    }

    private void regenerateAssignmentsIfNeeded(List<Submission> submissions) throws IOException {
        Map<String, Assignment> current = loadAssignments();
        int n = ConfigService.requiredReviewsPerClient();
        boolean needs = false;
        if (current.size() != submissions.size()) {
            needs = true;
        } else {
            for (Submission s : submissions) {
                Assignment a = current.get(s.clientId());
                if (a == null || a.storyIds().size() < n) { needs = true; break; }
            }
        }
        if (!needs) return;

        Map<String, Assignment> regenerated = generateAssignments(submissions, n);
        saveAssignments(regenerated);
    }

    private Map<String, Assignment> generateAssignments(List<Submission> subs, int n) {
        Map<String, Assignment> result = new HashMap<>();
        if (subs.size() < 2) return result; // Нечего распределять

        List<Submission> sorted = subs.stream()
                .sorted(Comparator.comparing(Submission::clientId))
                .toList();

        int total = sorted.size();
        for (int i = 0; i < total; i++) {
            Submission reviewer = sorted.get(i);
            List<String> assigned = new ArrayList<>();
            int offset = 1;
            while (assigned.size() < n && offset < total + n) {
                Submission candidate = sorted.get((i + offset) % total);
                if (!candidate.clientId().equalsIgnoreCase(reviewer.clientId())
                        && !assigned.contains(candidate.clientId())) {
                    assigned.add(candidate.clientId());
                }
                offset++;
            }
            result.put(reviewer.clientId(), new Assignment(reviewer.clientId(), assigned));
        }
        return result;
    }

    private void saveAssignments(Map<String, Assignment> map) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Assignment a : map.values()) {
            lines.add(sanitize(a.clientId()) + "|" + String.join(",", a.storyIds()));
        }
        Files.write(assignmentsCsv, lines, StandardCharsets.UTF_8);
    }

    private void invalidateAssignmentsIfChanged(List<Submission> submissions) throws IOException {
        regenerateAssignmentsIfNeeded(submissions);
    }

    /* ===================== reviews ===================== */

    public record ReviewResult(int saved, List<String> errors) {}

    public synchronized ReviewResult acceptReviews(String clientId, List<Review> reviews) throws IOException {
        Assignment assignment = assignmentsFor(clientId);
        Set<String> allowed = new HashSet<>(assignment.storyIds());
        List<String> errors = new ArrayList<>();
        int required = ConfigService.requiredReviewsPerClient();

        if (allowed.isEmpty()) {
            errors.add("no assignments for client");
        }

        // базовая валидация
        for (Review r : reviews) {
            if (!allowed.contains(r.storyId())) {
                errors.add("story " + r.storyId() + " is not assigned to " + clientId);
            }
        }

        if (reviews.size() < required) {
            errors.add("need at least " + required + " reviews, got " + reviews.size());
        }

        if (!errors.isEmpty()) {
            return new ReviewResult(0, errors);
        }

        // сохраняем индекс рецензий
        List<Review> existing = loadReviewsIndex();
        List<Review> filtered = existing.stream()
                .filter(r -> !r.reviewerId().equalsIgnoreCase(clientId))
                .collect(Collectors.toCollection(ArrayList::new));
        filtered.addAll(reviews);
        saveReviewsIndex(filtered);

        return new ReviewResult(reviews.size(), List.of());
    }

    private List<Review> loadReviewsIndex() throws IOException {
        if (Files.notExists(reviewsIndexCsv)) return List.of();
        List<Review> list = new ArrayList<>();
        for (String line : Files.readAllLines(reviewsIndexCsv, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 4) continue;
            list.add(new Review(p[0], p[1], Integer.parseInt(p[2]), parseLong(p[3])));
        }
        return list;
    }

    private void saveReviewsIndex(List<Review> list) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Review r : list) {
            lines.add(String.join("|",
                    sanitize(r.reviewerId()),
                    sanitize(r.storyId()),
                    Integer.toString(r.score()),
                    Long.toString(r.receivedAtUtc())));
        }
        Files.write(reviewsIndexCsv, lines, StandardCharsets.UTF_8);
    }

    /* ===================== results ===================== */

    public synchronized Results generateResults() throws IOException {
        List<Submission> submissions = loadSubmissions();
        List<Review> reviews = loadReviewsIndex();

        Map<String, List<Review>> byStory = reviews.stream().collect(Collectors.groupingBy(Review::storyId));

        List<ResultItem> items = new ArrayList<>();
        for (Submission s : submissions) {
            List<Review> rs = byStory.getOrDefault(s.clientId(), List.of());
            double avg = rs.stream().mapToInt(Review::score).average().orElse(0.0);
            items.add(new ResultItem(s.clientId(), s.title(), avg, rs.size()));
        }

        items.sort(Comparator.comparing(ResultItem::avgScore).reversed());
        long generated = Instant.now().toEpochMilli();
        Results results = new Results(items, generated);
        writeResultsJson(results);
        return results;
    }

    private void writeResultsJson(Results r) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"generatedAt\": ").append(r.generatedAtUtc()).append(",\n  \"items\": [\n");
        for (int i = 0; i < r.items().size(); i++) {
            ResultItem it = r.items().get(i);
            if (i > 0) sb.append(",\n");
            sb.append("    {\"storyId\":\"").append(escape(it.storyId())).append("\",")
                    .append("\"title\":\"").append(escape(it.title())).append("\",")
                    .append("\"avg\":").append(String.format(Locale.US, "%.2f", it.avgScore())).append(',')
                    .append("\"count\":").append(it.reviewsCount()).append('}');
        }
        sb.append("\n  ]\n}\n");
        Files.writeString(resultsJson, sb.toString(), StandardCharsets.UTF_8);
    }

    /* ===================== util ===================== */

    private static void touch(Path file) throws IOException {
        if (Files.notExists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "", StandardCharsets.UTF_8);
        }
    }

    private static String safe(String s) { return s.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private static String sanitize(String s) { return s == null ? "" : s.replace("|", " ").replace("\n", " "); }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0L; } }
}
