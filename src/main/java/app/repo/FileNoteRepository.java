package app.repo;

import app.model.Note;
import app.util.CsvUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

public class FileNoteRepository implements NoteRepository {
    private static final String[] HEADER = new String[]{"id","date","content","createdAt","updatedAt"};
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.getDefault());
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault());

    private final Path csvPath;
    private final List<Note> notes = new ArrayList<>();

    public FileNoteRepository(Path csvPath) throws IOException {
        this.csvPath = csvPath;
        ensureFileWithHeader();
        load();
    }

    private void ensureFileWithHeader() throws IOException {
        if (!Files.exists(csvPath)) {
            if (!Files.exists(csvPath.getParent())) Files.createDirectories(csvPath.getParent());
            try (BufferedWriter w = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
                w.write(CsvUtil.join(HEADER)); w.newLine();
            }
        }
    }

    private synchronized void load() throws IOException {
        notes.clear();
        try (BufferedReader r = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line; boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.trim().isEmpty()) continue;
                var cols = CsvUtil.parse(line);
                Note n = new Note();
                n.setId(cols.get(0));
                n.setDate(cols.get(1).isEmpty()? null : LocalDate.parse(cols.get(1), DATE_FMT));
                n.setContent(cols.get(2));
                n.setCreatedAt(cols.get(3).isEmpty()? null : LocalDateTime.parse(cols.get(3), DATE_TIME_FMT));
                n.setUpdatedAt(cols.get(4).isEmpty()? null : LocalDateTime.parse(cols.get(4), DATE_TIME_FMT));
                notes.add(n);
            }
        }
    }

    private synchronized void save() {
        try (BufferedWriter w = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            w.write(CsvUtil.join(HEADER)); w.newLine();
            for (Note n : notes) {
                List<String> cols = new ArrayList<>();
                cols.add(n.getId());
                cols.add(n.getDate()==null?"":n.getDate().format(DATE_FMT));
                cols.add(n.getContent()==null?"":n.getContent());
                cols.add(n.getCreatedAt()==null?"":n.getCreatedAt().format(DATE_TIME_FMT));
                cols.add(n.getUpdatedAt()==null?"":n.getUpdatedAt().format(DATE_TIME_FMT));
                w.write(CsvUtil.join(cols)); w.newLine();
            }
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    @Override
    public synchronized List<Note> findByRange(LocalDate start, LocalDate end) {
        return notes.stream().filter(n -> n.getDate()!=null && !n.getDate().isBefore(start) && !n.getDate().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public synchronized void upsert(Note note) {
        if (note.getId()==null) note.setId(UUID.randomUUID().toString());
        for (int i=0;i<notes.size();i++) if (notes.get(i).getId().equals(note.getId())) { notes.set(i, note); save(); return; }
        notes.add(note); save();
    }

    @Override
    public synchronized void deleteById(String id) {
        notes.removeIf(n -> n.getId().equals(id)); save();
    }
}


