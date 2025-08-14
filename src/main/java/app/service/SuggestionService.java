package app.service;

import app.model.Task;
import app.model.TaskPriority;
import app.model.TaskStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class SuggestionService {

    public List<Task> sortBySmartHeuristics(List<Task> tasks) {
        LocalDateTime now = LocalDateTime.now();
        return tasks.stream()
                .sorted(Comparator
                        .comparingInt((Task t) -> -score(t, now))
                        .thenComparing(t -> t.getDueDateTime(), Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing((Task t) -> priorityRank(t.getPriority()))
                        .thenComparingInt(Task::getEstimatedMinutes))
                .collect(Collectors.toList());
    }

    private int score(Task t, LocalDateTime now) {
        int s = 0;
        if (t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED) return -1000;

        // Priority weight
        switch (t.getPriority() == null ? TaskPriority.MEDIUM : t.getPriority()) {
            case CRITICAL: s += 100; break;
            case HIGH: s += 70; break;
            case MEDIUM: s += 30; break;
            case LOW: default: s += 0; break;
        }

        // Due-date weight
        if (t.getDueDateTime() != null) {
            Duration d = Duration.between(now, t.getDueDateTime());
            long minutes = d.toMinutes();
            if (minutes < 0) s += 100; // overdue
            else if (minutes <= 60) s += 40;
            else if (minutes <= 240) s += 25;
            else if (minutes <= 24 * 60) s += 10;
        }

        // Estimated duration weight (short tasks first, to get momentum)
        int est = Math.max(1, t.getEstimatedMinutes());
        if (est <= 30) s += 10; else if (est <= 60) s += 5;

        // Age weight (avoid procrastination)
        if (t.getCreatedAt() != null) {
            Duration age = Duration.between(t.getCreatedAt(), now);
            long days = age.toDays();
            if (days >= 7) s += 20; else if (days >= 3) s += 10;
        }

        // In-progress gets a small boost
        if (t.getStatus() == TaskStatus.IN_PROGRESS) s += 15;

        return s;
    }

    private int priorityRank(TaskPriority p) {
        if (p == null) return 2;
        switch (p) {
            case CRITICAL: return 0;
            case HIGH: return 1;
            case MEDIUM: return 2;
            default: return 3;
        }
    }
}


