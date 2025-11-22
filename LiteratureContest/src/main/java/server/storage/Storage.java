package server.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class Storage {
    private Storage() {}
    public static final Path ROOT = Paths.get("storage");

    public static void ensure() throws IOException {
        createDirs("submissions","reviews","assignments","packs","registry","results","tmp");
        touchJson(ROOT.resolve("registry/clients.json"),       "[]\n");
        touchJson(ROOT.resolve("registry/submissions.json"),   "[]\n");
        touchJson(ROOT.resolve("registry/assignments.json"),   "[]\n");
        touchJson(ROOT.resolve("registry/reviews_index.json"), "[]\n");
        touchJson(ROOT.resolve("results/final.json"),
                "{\"status\":\"empty\",\"generatedAt\":0,\"items\":[]}\n");
    }

    private static void createDirs(String... names) throws IOException {
        for (String n : names) Files.createDirectories(ROOT.resolve(n));
    }
    private static void touchJson(Path file, String defaultContent) throws IOException {
        if (Files.notExists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
        }
    }
}
