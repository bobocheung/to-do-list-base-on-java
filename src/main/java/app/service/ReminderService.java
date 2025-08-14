package app.service;

import app.model.Task;
import app.model.TaskStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ReminderService {
    private final TaskService taskService;
    private final ScheduledExecutorService scheduler;
    private volatile boolean started = false;

    // taskId -> last category (OVERDUE or DUE_SOON)
    private final Map<String, String> lastNotified = new HashMap<>();

    public ReminderService(TaskService taskService) {
        this.taskService = taskService;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reminder-thread");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        scheduler.scheduleAtFixedRate(this::tick, 5, 30, TimeUnit.SECONDS);
    }

    public synchronized void stop() {
        if (!started) return;
        scheduler.shutdownNow();
        started = false;
    }

    private void tick() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Task> tasks = taskService.listPendingOrInProgress();
            for (Task t : tasks) {
                if (t.getDueDateTime() == null) continue;
                if (t.getStatus() == TaskStatus.COMPLETED || t.getStatus() == TaskStatus.CANCELLED) continue;

                String category = null;
                if (t.getDueDateTime().isBefore(now)) {
                    category = "OVERDUE";
                } else {
                    int before = t.getReminderBeforeMinutes() == null ? 60 : Math.max(1, t.getReminderBeforeMinutes());
                    if (!t.getDueDateTime().isAfter(now.plusMinutes(before))) {
                        category = "DUE_SOON";
                    }
                }

                if (category != null) {
                    String prev = lastNotified.get(t.getId());
                    if (!Objects.equals(prev, category)) {
                        if ("OVERDUE".equals(category)) {
                            System.out.printf("[提醒][逾期] 任務 %s 截止 %s 已逾期！%n", t.getTitle(), t.getDueDateTime());
                        } else {
                            System.out.printf("[提醒][即將到期] 任務 %s 將於 %s 截止！%n", t.getTitle(), t.getDueDateTime());
                        }
                        lastNotified.put(t.getId(), category);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}


