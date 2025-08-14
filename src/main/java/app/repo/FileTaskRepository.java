package app.repo;

import app.model.Task;
import app.model.TaskPriority;
import app.model.TaskStatus;
import app.util.CsvUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class FileTaskRepository implements TaskRepository {
    private static final String[] HEADER = new String[]{
            "id","title","description","priority","dueDateTime","estimatedMinutes","status","createdAt","completedAt","tags","category","actualMinutes","reminderBeforeMinutes","sortOrder","recurrence"
    };
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault());

    private final Path csvPath;
    private final List<Task> tasks;

    public FileTaskRepository(Path csvPath) throws IOException {
        this.csvPath = csvPath;
        ensureFileWithHeader();
        this.tasks = loadFromDisk();
    }

    private synchronized void ensureFileWithHeader() throws IOException {
        if (!Files.exists(csvPath)) {
            if (!Files.exists(csvPath.getParent())) {
                Files.createDirectories(csvPath.getParent());
            }
            try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
                writer.write(CsvUtil.join(HEADER));
                writer.newLine();
            }
        }
    }

    private synchronized List<Task> loadFromDisk() throws IOException {
        List<Task> list = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) { first = false; continue; }
                if (line.trim().isEmpty()) continue;
                List<String> cols = CsvUtil.parse(line);
                Task t = fromCsv(cols);
                if (t != null) list.add(t);
            }
        }
        return list;
    }

    private Task fromCsv(List<String> cols) {
        try {
            Task t = new Task();
            t.setId(cols.get(0));
            t.setTitle(cols.get(1));
            t.setDescription(cols.get(2));
            t.setPriority(cols.get(3).isEmpty() ? TaskPriority.MEDIUM : TaskPriority.valueOf(cols.get(3)));
            t.setDueDateTime(cols.get(4).isEmpty() ? null : LocalDateTime.parse(cols.get(4), DATE_TIME_FMT));
            t.setEstimatedMinutes(cols.get(5).isEmpty() ? 30 : Integer.parseInt(cols.get(5)));
            t.setStatus(cols.get(6).isEmpty() ? TaskStatus.PENDING : TaskStatus.valueOf(cols.get(6)));
            t.setCreatedAt(cols.get(7).isEmpty() ? null : LocalDateTime.parse(cols.get(7), DATE_TIME_FMT));
            t.setCompletedAt(cols.get(8).isEmpty() ? null : LocalDateTime.parse(cols.get(8), DATE_TIME_FMT));
            List<String> tags = new ArrayList<>();
            if (cols.size() > 9 && !cols.get(9).isEmpty()) {
                for (String s : cols.get(9).split(";")) {
                    String v = s.trim();
                    if (!v.isEmpty()) tags.add(v);
                }
            }
            t.setTags(tags);
            t.setCategory(cols.size()>10 ? emptyToNull(cols.get(10)) : null);
            t.setActualMinutes(cols.size()>11 && !cols.get(11).isEmpty() ? Integer.parseInt(cols.get(11)) : null);
            t.setReminderBeforeMinutes(cols.size()>12 && !cols.get(12).isEmpty() ? Integer.parseInt(cols.get(12)) : null);
            t.setSortOrder(cols.size()>13 && !cols.get(13).isEmpty() ? Integer.parseInt(cols.get(13)) : null);
            t.setRecurrence(cols.size()>14 ? emptyToNull(cols.get(14)) : null);
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private String emptyToNull(String s) { return (s == null || s.isEmpty()) ? null : s; }

    private synchronized void saveToDisk() {
        try (BufferedWriter writer = Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8)) {
            writer.write(CsvUtil.join(HEADER));
            writer.newLine();
            for (Task t : tasks) {
                List<String> cols = new ArrayList<>();
                cols.add(t.getId());
                cols.add(t.getTitle() == null ? "" : t.getTitle());
                cols.add(t.getDescription() == null ? "" : t.getDescription());
                cols.add(t.getPriority() == null ? TaskPriority.MEDIUM.name() : t.getPriority().name());
                cols.add(t.getDueDateTime() == null ? "" : t.getDueDateTime().format(DATE_TIME_FMT));
                cols.add(String.valueOf(t.getEstimatedMinutes()));
                cols.add(t.getStatus() == null ? TaskStatus.PENDING.name() : t.getStatus().name());
                cols.add(t.getCreatedAt() == null ? "" : t.getCreatedAt().format(DATE_TIME_FMT));
                cols.add(t.getCompletedAt() == null ? "" : t.getCompletedAt().format(DATE_TIME_FMT));
                cols.add(t.getTags() == null ? "" : String.join(";", t.getTags()));
                cols.add(t.getCategory()==null?"":t.getCategory());
                cols.add(t.getActualMinutes()==null?"":String.valueOf(t.getActualMinutes()));
                cols.add(t.getReminderBeforeMinutes()==null?"":String.valueOf(t.getReminderBeforeMinutes()));
                cols.add(t.getSortOrder()==null?"":String.valueOf(t.getSortOrder()));
                cols.add(t.getRecurrence()==null?"":t.getRecurrence());
                writer.write(CsvUtil.join(cols));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save tasks.csv", e);
        }
    }

    @Override
    public synchronized List<Task> findAll() {
        return new ArrayList<>(tasks);
    }

    @Override
    public synchronized Optional<Task> findById(String id) {
        return tasks.stream().filter(t -> t.getId().equals(id)).findFirst();
    }

    @Override
    public synchronized void upsert(Task task) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(task.getId())) {
                tasks.set(i, task);
                saveToDisk();
                return;
            }
        }
        tasks.add(task);
        saveToDisk();
    }

    @Override
    public synchronized boolean deleteById(String id) {
        boolean removed = tasks.removeIf(t -> t.getId().equals(id));
        if (removed) saveToDisk();
        return removed;
    }
}


