package app.service;

import app.model.Task;
import app.model.TaskPriority;
import app.model.TaskStatus;
import app.repo.TaskRepository;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class TaskService {
    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    public List<Task> listAll() {
        List<Task> list = new ArrayList<>(repository.findAll());
        list.sort(Comparator
                .comparing((Task t) -> t.getSortOrder(), Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Task::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    public Task addTask(String title,
                        String description,
                        TaskPriority priority,
                        LocalDateTime due,
                        int estimatedMinutes,
                        String rawTags) {
        Task t = new Task();
        t.setId(UUID.randomUUID().toString());
        t.setTitle(title == null ? "" : title);
        t.setDescription(description == null ? "" : description);
        t.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
        t.setDueDateTime(due);
        t.setEstimatedMinutes(Math.max(1, estimatedMinutes));
        t.setStatus(TaskStatus.PENDING);
        t.setCreatedAt(LocalDateTime.now());
        t.setCompletedAt(null);
        List<String> tags = new ArrayList<>();
        if (rawTags != null && !rawTags.trim().isEmpty()) {
            for (String s : rawTags.split(";")) {
                String v = s.trim();
                if (!v.isEmpty()) tags.add(v.toLowerCase(Locale.ROOT));
            }
        }
        t.setTags(tags);
        t.setCategory(null);
        t.setActualMinutes(null);
        t.setReminderBeforeMinutes(null);
        t.setSortOrder(null);
        repository.upsert(t);
        return t;
    }

    public boolean completeTask(String id) {
        Optional<Task> opt = repository.findById(id);
        if (opt.isEmpty()) return false;
        Task t = opt.get();
        t.setStatus(TaskStatus.COMPLETED);
        t.setCompletedAt(LocalDateTime.now());
        repository.upsert(t);
        return true;
    }

    public boolean deleteTask(String id) {
        return repository.deleteById(id);
    }

    public boolean startTask(String id) {
        Optional<Task> opt = repository.findById(id);
        if (opt.isEmpty()) return false;
        Task t = opt.get();
        t.setStatus(TaskStatus.IN_PROGRESS);
        repository.upsert(t);
        return true;
    }

    public boolean snoozeTask(String id, int minutes) {
        Optional<Task> opt = repository.findById(id);
        if (opt.isEmpty()) return false;
        Task t = opt.get();
        LocalDateTime due = t.getDueDateTime();
        if (due == null) due = LocalDateTime.now();
        t.setDueDateTime(due.plusMinutes(Math.max(1, minutes)));
        repository.upsert(t);
        return true;
    }

    public boolean rescheduleDate(String id, LocalDate newDate, LocalTime keepOrUseTime) {
        Optional<Task> opt = repository.findById(id);
        if (opt.isEmpty()) return false;
        Task t = opt.get();
        LocalTime time = keepOrUseTime;
        if (time == null) {
            LocalDateTime due = t.getDueDateTime();
            time = (due != null) ? due.toLocalTime() : LocalTime.NOON;
        }
        t.setDueDateTime(LocalDateTime.of(newDate, time));
        repository.upsert(t);
        return true;
    }

    public boolean updateTask(String id,
                              String title,
                              String description,
                              TaskPriority priority,
                              LocalDateTime due,
                              Integer estimatedMinutes,
                              String rawTags) {
        Optional<Task> opt = repository.findById(id);
        if (opt.isEmpty()) return false;
        Task t = opt.get();
        if (title != null) t.setTitle(title);
        if (description != null) t.setDescription(description);
        if (priority != null) t.setPriority(priority);
        if (due != null) t.setDueDateTime(due);
        if (estimatedMinutes != null) t.setEstimatedMinutes(Math.max(1, estimatedMinutes));
        if (rawTags != null) {
            List<String> tags = new ArrayList<>();
            for (String s : rawTags.split(";")) {
                String v = s.trim();
                if (!v.isEmpty()) tags.add(v.toLowerCase(Locale.ROOT));
            }
            t.setTags(tags);
        }
        repository.upsert(t);
        return true;
    }

    public List<Task> filter(String status, String priority, String tag) {
        return repository.findAll().stream()
                .filter(t -> status == null || status.isEmpty() || t.getStatus().name().equalsIgnoreCase(status))
                .filter(t -> priority == null || priority.isEmpty() || (t.getPriority() != null && t.getPriority().name().equalsIgnoreCase(priority)))
                .filter(t -> tag == null || tag.isEmpty() || (t.getTags() != null && t.getTags().stream().anyMatch(x -> x.equalsIgnoreCase(tag))))
                .sorted(Comparator.comparing(Task::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    public int batchCompleteByTag(String tag) {
        int count = 0;
        for (Task t : repository.findAll()) {
            if (t.getStatus() != TaskStatus.COMPLETED && t.getTags() != null && t.getTags().stream().anyMatch(x -> x.equalsIgnoreCase(tag))) {
                t.setStatus(TaskStatus.COMPLETED);
                t.setCompletedAt(LocalDateTime.now());
                repository.upsert(t);
                count++;
            }
        }
        return count;
    }

    public List<Task> listPendingOrInProgress() {
        return repository.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS)
                .collect(Collectors.toList());
    }

    public List<Task> listOverdue() {
        LocalDateTime now = LocalDateTime.now();
        return listPendingOrInProgress().stream()
                .filter(t -> t.getDueDateTime() != null && t.getDueDateTime().isBefore(now))
                .collect(Collectors.toList());
    }

    public Optional<Task> getById(String id) {
        return repository.findById(id);
    }

    public boolean reorder(String fromId, String toId) {
        List<Task> tasks = listAll();
        int fromIdx = -1, toIdx = -1;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(fromId)) fromIdx = i;
            if (tasks.get(i).getId().equals(toId)) toIdx = i;
        }
        if (fromIdx < 0 || toIdx < 0) return false;
        Task moving = tasks.remove(fromIdx);
        if (toIdx > fromIdx) toIdx--; // adjust index after removal
        tasks.add(toIdx, moving);
        // reassign sortOrder sequentially
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            t.setSortOrder(i);
            repository.upsert(t);
        }
        return true;
    }

    public List<Task> listDueWithinMinutes(int minutes) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusMinutes(minutes);
        return listPendingOrInProgress().stream()
                .filter(t -> t.getDueDateTime() != null && !t.getDueDateTime().isBefore(now) && t.getDueDateTime().isBefore(threshold))
                .collect(Collectors.toList());
    }
}


