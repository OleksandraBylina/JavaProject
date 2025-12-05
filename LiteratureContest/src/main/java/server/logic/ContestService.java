package server.logic;

import server.format.DocxUtil;
import server.format.XlsxUtil;
import server.storage.Storage;
import server.time.ConfigService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class ContestService {

    public record Submission(String clientId, String submissionId, String title,
                             String fileName, String normalizedDocx, long receivedAtUtc) {
    }

    public record Assignment(String clientId, List<String> submissionIds) {
    }

    public record Review(String reviewerId, String storyId, int score, long receivedAtUtc) {
    }

    public record Results(List<ResultItem> items, long generatedAtUtc, List<String> disqualified,
                          Protocol protocol) {
    }

    public record ResultItem(String storyId, String title, double avgScore, int reviewsCount,
                             boolean insufficientReviews) {
    }

    public record Protocol(int totalSubmissions, int totalReviewers, int requiredReviews,
                           int submittedReviews, List<String> insufficientStories,
                           List<String> disqualifiedAuthors) {}

    public record Attachment(String fileName, String contentType, byte[] content) {}
    public record MailIngestResult(List<Submission> accepted, List<String> errors) {}

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
        String fileName = "story-" + UUID.randomUUID() + ".txt";
        Path dir = Storage.ROOT.resolve("submissions").resolve(safe(clientId));
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(fileName), text, StandardCharsets.UTF_8);
        return addSubmissionRecord(clientId, title, fileName, text, Instant.now().toEpochMilli());
    }

    public synchronized Submission registerBinarySubmission(String clientId, String title, String ext, byte[] body) throws IOException {
        Path dir = Storage.ROOT.resolve("submissions").resolve(safe(clientId));
        Files.createDirectories(dir);
        String fileName = "story-" + UUID.randomUUID() + ext;
        Files.write(dir.resolve(fileName), body);
        String text = ext.toLowerCase().contains("doc") ? DocxUtil.extractPlainText(body) : new String(body, StandardCharsets.UTF_8);
        return addSubmissionRecord(clientId, title, fileName, text, Instant.now().toEpochMilli());
    }

    public synchronized Submission addSubmissionRecord(String clientId, String title, String fileName, String plainText, long receivedAt) throws IOException {
        List<Submission> all = loadSubmissions();
        String submissionId = UUID.randomUUID().toString();
        String normalizedRel = "normalized/" + safe(submissionId) + ".docx";
        Path normalizedPath = Storage.ROOT.resolve("packs").resolve(normalizedRel);
        DocxUtil.writeNormalizedDocx(title, plainText, normalizedPath);

        Submission newSub = new Submission(clientId, submissionId, sanitize(title), fileName, normalizedRel, receivedAt);
        all.add(newSub);
        saveSubmissions(all);
        regenerateAssignmentsIfNeeded(all);
        return newSub;
    }

    public List<Submission> loadSubmissions() throws IOException {
        if (Files.notExists(submissionsCsv)) return List.of();
        List<Submission> list = new ArrayList<>();
        for (String line : Files.readAllLines(submissionsCsv, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            String[] p = line.split("\\|", -1);
            if (p.length < 6) {
                // backward compatibility: missing normalizedDocx
                if (p.length >= 5) {
                    list.add(new Submission(p[0], p[1], p[2], p[3], "", parseLong(p[4])));
                }
                continue;
            }
            list.add(new Submission(p[0], p[1], p[2], p[3], p[4], parseLong(p[5])));
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
                    sanitize(s.normalizedDocx()),
                    Long.toString(s.receivedAtUtc())));
        }
        Files.write(submissionsCsv, lines, StandardCharsets.UTF_8);
    }

    public synchronized MailIngestResult ingestMail(String clientId, String subject, Instant receivedAt, List<Attachment> attachments) throws IOException {
        List<Submission> accepted = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (subject == null || !subject.trim().equalsIgnoreCase(ConfigService.expectedMailSubject())) {
            errors.add("invalid subject");
            return new MailIngestResult(accepted, errors);
        }
        if (receivedAt.isBefore(ConfigService.submitFrom()) || receivedAt.isAfter(ConfigService.submitTo())) {
            errors.add("submission window closed");
            return new MailIngestResult(accepted, errors);
        }

        int min = ConfigService.minChars();
        int max = ConfigService.maxChars();
        for (Attachment a : attachments) {
            String name = a.fileName() == null ? "untitled" : a.fileName();
            String lower = name.toLowerCase();
            if (!(lower.endsWith(".txt") || lower.endsWith(".doc") || lower.endsWith(".docx"))) {
                errors.add(name + ": unsupported attachment type");
                continue;
            }

            String text;
            if (lower.endsWith(".txt")) {
                text = new String(a.content(), StandardCharsets.UTF_8);
            } else {
                try {
                    text = DocxUtil.extractPlainText(a.content());
                } catch (Exception e) {
                    errors.add(name + ": failed to read docx text");
                    continue;
                }
            }

            int chars = text.codePointCount(0, text.length());
            if (chars <= min || chars >= max) {
                errors.add(name + ": length must be between " + min + " and " + max);
                continue;
            }
            String title = stripExtension(name);
            accepted.add(addSubmissionRecord(clientId, title, name, text, receivedAt.toEpochMilli()));
        }
        return new MailIngestResult(accepted, errors);
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
        Set<String> clients = submissions.stream().map(Submission::clientId).collect(Collectors.toCollection(TreeSet::new));
        boolean needs = current.keySet().size() != clients.size();
        if (!needs) {
            for (String c : clients) {
                Assignment a = current.get(c);
                if (a == null || a.submissionIds().size() < n) { needs = true; break; }
            }
        }
        if (!needs) return;

        Map<String, Assignment> regenerated = generateAssignments(submissions, n);
        saveAssignments(regenerated);
    }

    private Map<String, Assignment> generateAssignments(List<Submission> subs, int n) {
        Map<String, Assignment> result = new HashMap<>();
        if (subs.isEmpty()) return result;

        List<String> reviewers = subs.stream().map(Submission::clientId).distinct().sorted().toList();
        Map<String, List<String>> assignments = new HashMap<>();
        Map<String, Integer> load = new HashMap<>();
        for (String r : reviewers) { assignments.put(r, new ArrayList<>()); load.put(r, 0); }

        List<Submission> sortedSubs = subs.stream().sorted(Comparator.comparing(Submission::submissionId)).toList();
        for (Submission target : sortedSubs) {
            PriorityQueue<String> pq = new PriorityQueue<>((a, b) -> {
                int cmp = Integer.compare(load.get(a), load.get(b));
                if (cmp != 0) return cmp;
                return a.compareToIgnoreCase(b);
            });
            for (String reviewer : reviewers) {
                if (!reviewer.equalsIgnoreCase(target.clientId())) {
                    pq.add(reviewer);
                }
            }

            int assigned = 0;
            while (!pq.isEmpty() && assigned < n) {
                String reviewer = pq.poll();
                List<String> bucket = assignments.get(reviewer);
                if (bucket.contains(target.submissionId())) continue;
                bucket.add(target.submissionId());
                load.put(reviewer, load.get(reviewer) + 1);
                assigned++;
            }

            if (assigned < n && !reviewers.isEmpty()) {
                // fallback: reuse reviewers with the lightest load (even если уже назначены другим историям)
                List<String> fallback = reviewers.stream()
                        .filter(r -> !r.equalsIgnoreCase(target.clientId()))
                        .sorted((a, b) -> {
                            int cmp = Integer.compare(load.get(a), load.get(b));
                            if (cmp != 0) return cmp;
                            return a.compareToIgnoreCase(b);
                        })
                        .toList();
                for (String reviewer : fallback) {
                    if (assigned >= n) break;
                    List<String> bucket = assignments.get(reviewer);
                    if (!bucket.contains(target.submissionId())) {
                        bucket.add(target.submissionId());
                        load.put(reviewer, load.get(reviewer) + 1);
                        assigned++;
                    }
                }
            }
        }

        for (Map.Entry<String, List<String>> e : assignments.entrySet()) {
            result.put(e.getKey(), new Assignment(e.getKey(), e.getValue()));
        }
        return result;
    }

    private void saveAssignments(Map<String, Assignment> map) throws IOException {
        List<String> lines = new ArrayList<>();
        for (Assignment a : map.values()) {
            lines.add(sanitize(a.clientId()) + "|" + String.join(",", a.submissionIds()));
        }
        Files.write(assignmentsCsv, lines, StandardCharsets.UTF_8);
    }

    /* ===================== reviews ===================== */

    public record ReviewResult(int saved, List<String> errors) {}

    public synchronized ReviewResult acceptReviews(String clientId, List<Review> reviews) throws IOException {
        Assignment assignment = assignmentsFor(clientId);
        Set<String> allowed = new HashSet<>(assignment.submissionIds());
        List<Submission> submissions = loadSubmissions();
        Set<String> knownIds = submissions.stream().map(Submission::submissionId).collect(Collectors.toSet());
        List<Review> existing = loadReviewsIndex();
        Set<String> existingPairs = existing.stream()
                .filter(r -> r.reviewerId().equalsIgnoreCase(clientId))
                .map(r -> r.reviewerId().toLowerCase(Locale.ROOT) + "|" + r.storyId().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        List<String> errors = new ArrayList<>();
        int required = ConfigService.requiredReviewsPerClient();
        Instant from = ConfigService.reviewFrom();
        Instant to   = ConfigService.reviewTo();

        if (allowed.isEmpty()) {
            errors.add("no assignments for client");
        }

        Set<String> seen = new HashSet<>();
        for (Review r : reviews) {
            String key = r.reviewerId().toLowerCase(Locale.ROOT) + "|" + r.storyId().toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                errors.add("duplicate review in payload for story " + r.storyId());
            }
            if (!knownIds.contains(r.storyId())) {
                errors.add("story " + r.storyId() + " does not exist");
                continue;
            }
            if (!allowed.contains(r.storyId())) {
                errors.add("story " + r.storyId() + " is not assigned to " + clientId);
            }
            if (existingPairs.contains(key)) {
                errors.add("story " + r.storyId() + " already reviewed by " + clientId);
            }
            Instant ts = Instant.ofEpochMilli(r.receivedAtUtc());
            if (ts.isBefore(from) || ts.isAfter(to)) {
                errors.add("review for story " + r.storyId() + " is outside review window");
            }
        }

        if (reviews.size() < required) {
            errors.add("need at least " + required + " reviews, got " + reviews.size());
        }

        if (!errors.isEmpty()) {
            return new ReviewResult(0, errors);
        }

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

    /* ===================== downloadable helper files ===================== */

    public byte[] assignmentsWorkbook(String clientId) throws IOException {
        Assignment assignment = assignmentsFor(clientId);
        Map<String, Submission> byId = loadSubmissions().stream()
                .collect(Collectors.toMap(Submission::submissionId, s -> s, (a,b)->a));
        List<XlsxUtil.Row> rows = new ArrayList<>();
        for (String id : assignment.submissionIds()) {
            Submission s = byId.get(id);
            if (s != null && !s.clientId().equalsIgnoreCase(clientId)) {
                rows.add(new XlsxUtil.Row(id, s.title(), s.clientId()));
            }
        }
        return XlsxUtil.buildAssignmentsSheet(rows);
    }

    public byte[] assignmentsArchive(String clientId) throws IOException {
        Assignment assignment = assignmentsFor(clientId);
        Map<String, Submission> byId = loadSubmissions().stream()
                .collect(Collectors.toMap(Submission::submissionId, s -> s, (a,b)->a));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (var zip = new java.util.zip.ZipOutputStream(bos)) {
            for (String id : assignment.submissionIds()) {
                Submission s = byId.get(id);
                if (s == null) continue;
                if (s.clientId().equalsIgnoreCase(clientId)) continue;
                Path normalized = Storage.ROOT.resolve("packs").resolve(s.normalizedDocx());
                if (Files.notExists(normalized)) continue;
                zip.putNextEntry(new java.util.zip.ZipEntry(id + ".docx"));
                Files.copy(normalized, zip);
                zip.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    /* ===================== results ===================== */

    public synchronized Results generateResults() throws IOException {
        List<Submission> submissions = loadSubmissions();
        List<Review> reviews = loadReviewsIndex();
        Map<String, Assignment> assignments = loadAssignments();

        Map<String, List<Review>> byStory = reviews.stream().collect(Collectors.groupingBy(Review::storyId));
        Map<String, Long> reviewsByReviewer = reviews.stream()
                .collect(Collectors.groupingBy(Review::reviewerId, Collectors.counting()));

        int required = ConfigService.requiredReviewsPerClient();
        Set<String> disqualifiedReviewers = new HashSet<>();
        for (Map.Entry<String, Assignment> e : assignments.entrySet()) {
            long count = reviewsByReviewer.getOrDefault(e.getKey(), 0L);
            if (count < required) {
                disqualifiedReviewers.add(e.getKey());
            }
        }

        List<ResultItem> items = new ArrayList<>();
        List<String> insufficientStories = new ArrayList<>();
        for (Submission s : submissions) {
            boolean authorDQ = disqualifiedReviewers.contains(s.clientId());
            List<Review> rs = byStory.getOrDefault(s.submissionId(), List.of());
            double avg = rs.stream().mapToInt(Review::score).average().orElse(0.0);
            boolean insufficient = rs.size() < required;
            if (insufficient) insufficientStories.add(s.submissionId());
            if (!authorDQ) {
                items.add(new ResultItem(s.submissionId(), s.title(), avg, rs.size(), insufficient));
            }
        }

        items.sort(Comparator.comparing(ResultItem::avgScore).reversed());
        long generated = Instant.now().toEpochMilli();
        var disqSorted = disqualifiedReviewers.stream().sorted().toList();
        Protocol protocol = new Protocol(
                submissions.size(),
                assignments.size(),
                required,
                reviews.size(),
                insufficientStories.stream().sorted().toList(),
                disqSorted
        );
        Results results = new Results(items, generated, disqSorted, protocol);
        writeResultsJson(results);
        writeProtocol(results);
        return results;


    }

    private void writeResultsJson(Results r) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"generatedAt\": ").append(r.generatedAtUtc()).append(",\n  \"disqualified\": [");
        for (int i = 0; i < r.disqualified().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\"').append(escape(r.disqualified().get(i))).append('\"');
        }
        sb.append("],\n  \"protocol\": {\n    \"totalSubmissions\": ").append(r.protocol().totalSubmissions()).append(',');
        sb.append("\n    \"totalReviewers\": ").append(r.protocol().totalReviewers()).append(',');
        sb.append("\n    \"requiredReviews\": ").append(r.protocol().requiredReviews()).append(',');
        sb.append("\n    \"submittedReviews\": ").append(r.protocol().submittedReviews()).append(',');
        sb.append("\n    \"insufficientStories\": [");
        for (int i = 0; i < r.protocol().insufficientStories().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\"').append(escape(r.protocol().insufficientStories().get(i))).append('\"');
        }
        sb.append("],\n    \"disqualifiedAuthors\": [");
        for (int i = 0; i < r.protocol().disqualifiedAuthors().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('\"').append(escape(r.protocol().disqualifiedAuthors().get(i))).append('\"');
        }
        sb.append("]\n  },\n  \"items\": [\n");
        for (int i = 0; i < r.items().size(); i++) {
            ResultItem it = r.items().get(i);
            if (i > 0) sb.append(",\n");
            sb.append("    {\"storyId\":\"").append(escape(it.storyId())).append("\",")
                    .append("\"title\":\"").append(escape(it.title())).append("\",")
                    .append("\"avg\":").append(String.format(Locale.US, "%.2f", it.avgScore())).append(',')
                    .append("\"count\":").append(it.reviewsCount()).append(',')
                    .append("\"insufficientReviews\":").append(it.insufficientReviews()).append('}');
        }
        sb.append("\n  ]\n}\n");
        Files.writeString(resultsJson, sb.toString(), StandardCharsets.UTF_8);
    }

    private void writeProtocol(Results r) throws IOException {
        Path protocolFile = resultsJson.getParent().resolve("protocol.txt");
        StringBuilder sb = new StringBuilder();

        Protocol protocol = r.protocol();

        // Общая информация
        sb.append("Protocol generated at ")
                .append(Instant.ofEpochMilli(r.generatedAtUtc())).append('\n');
        sb.append("Total submissions: ").append(protocol.totalSubmissions()).append('\n');
        sb.append("Total reviewers: ").append(protocol.totalReviewers()).append('\n');
        sb.append("Required reviews per reviewer: ").append(protocol.requiredReviews()).append('\n');
        sb.append("Submitted reviews: ").append(protocol.submittedReviews()).append('\n');
        sb.append("Stories with insufficient reviews: ").append(protocol.insufficientStories()).append('\n');
        sb.append("Disqualified authors: ").append(protocol.disqualifiedAuthors()).append('\n');

        // Призёры (топ-3)
        sb.append("\nPrize winners (top 3):\n");
        for (int i = 0; i < Math.min(3, r.items().size()); i++) {
            ResultItem it = r.items().get(i);
            int place = i + 1;
            sb.append(place).append(". ")
                    .append(it.title())
                    .append(" [storyId=").append(it.storyId()).append("]")
                    .append(", avg=").append(String.format(Locale.US, "%.2f", it.avgScore()))
                    .append(", reviews=").append(it.reviewsCount());
            if (it.insufficientReviews()) sb.append(" (INSUFFICIENT REVIEWS)");
            sb.append('\n');
        }

        // Полный рейтинг
        sb.append("\nFull ranking:\n");
        for (int i = 0; i < r.items().size(); i++) {
            ResultItem it = r.items().get(i);
            int place = i + 1;
            sb.append(place).append(". ")
                    .append(it.title())
                    .append(" [storyId=").append(it.storyId()).append("]")
                    .append(", avg=").append(String.format(Locale.US, "%.2f", it.avgScore()))
                    .append(", reviews=").append(it.reviewsCount());
            if (it.insufficientReviews()) sb.append(" (INSUFFICIENT REVIEWS)");
            sb.append('\n');
        }

        Files.writeString(protocolFile, sb.toString(), StandardCharsets.UTF_8);
    }



    /* ===================== util ===================== */

    private static void touch(Path file) throws IOException {
        if (Files.notExists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "", StandardCharsets.UTF_8);
        }
    }

    private static String safe(String s) { return s == null ? "" : s.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private static String sanitize(String s) { return s == null ? "" : s.replace("|", " ").replace("\n", " "); }
    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0L; } }
    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
