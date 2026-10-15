package com.console.demo.tools.agent;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class MentionCompleter implements Completer {

    private static final int MAX_CANDIDATES = 50;

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String word = line.word();
        if (!word.startsWith("@")) return;

        String typed = word.substring(1);
        int slash = typed.lastIndexOf('/');
        String dirPart = slash >= 0 ? typed.substring(0, slash + 1) : "";
        String prefix = slash >= 0 ? typed.substring(slash + 1) : typed;

        if (typed.equals("~")) {
            candidates.add(new Candidate("@~/", "~/", null, null, null, null, false));
            return;
        }

        Path dir = ManipulateFile.toPath(dirPart.isEmpty() ? "." : dirPart);
        if (!Files.isDirectory(dir)) return;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            int count = 0;
            for (Path p : stream) {
                String name = p.getFileName().toString();

                if (!name.startsWith(prefix)) continue;
                if (name.startsWith(".") && !prefix.startsWith(".")) continue;

                boolean isDir = Files.isDirectory(p);
                String shown = name + (isDir ? "/" : "");
                String value = "@" + dirPart + shown;

                candidates.add(new Candidate(value, shown, null, null, null, null, !isDir));

                if (++count >= MAX_CANDIDATES) break;
            }
        } catch (IOException ignored) {
        }
    }
}