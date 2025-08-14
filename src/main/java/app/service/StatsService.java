package app.service;

import app.model.Task;
import app.model.TaskStatus;

import java.time.Duration;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StatsService {
    public String buildStatsReport(List<Task> tasks) {
        int total = tasks.size();
        long completed = tasks.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count();
        long active = tasks.stream().filter(t -> t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS).count();
        long cancelled = tasks.stream().filter(t -> t.getStatus() == TaskStatus.CANCELLED).count();

        DoubleSummaryStatistics estStats = tasks.stream().mapToDouble(Task::getEstimatedMinutes).summaryStatistics();

        long overdue = tasks.stream()
                .filter(t -> (t.getStatus() == TaskStatus.PENDING || t.getStatus() == TaskStatus.IN_PROGRESS))
                .filter(t -> t.getDueDateTime() != null && t.getDueDateTime().isBefore(java.time.LocalDateTime.now()))
                .count();

        List<Duration> completionDurations = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.COMPLETED && t.getCreatedAt() != null && t.getCompletedAt() != null)
                .map(t -> Duration.between(t.getCreatedAt(), t.getCompletedAt()))
                .collect(Collectors.toList());

        String avgCompletion = completionDurations.isEmpty() ? "—" : humanizeAverage(completionDurations);

        StringBuilder sb = new StringBuilder();
        sb.append("統計報告\n");
        sb.append(String.format(Locale.getDefault(), "- 總任務數: %d%n", total));
        sb.append(String.format(Locale.getDefault(), "- 進行中/待辦: %d%n", active));
        sb.append(String.format(Locale.getDefault(), "- 已完成: %d%n", completed));
        sb.append(String.format(Locale.getDefault(), "- 已取消: %d%n", cancelled));
        sb.append(String.format(Locale.getDefault(), "- 逾期數量: %d%n", overdue));
        sb.append(String.format(Locale.getDefault(), "- 平均預估時間: %.1f 分鐘%n", estStats.getAverage()));
        sb.append(String.format(Locale.getDefault(), "- 平均完成時間: %s%n", avgCompletion));
        return sb.toString();
    }

    private String humanizeAverage(List<Duration> durations) {
        long avgMillis = (long) durations.stream().mapToLong(Duration::toMillis).average().orElse(0);
        long hours = TimeUnit.MILLISECONDS.toHours(avgMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(avgMillis) - hours * 60;
        if (hours > 0) return String.format(Locale.getDefault(), "%d 小時 %d 分鐘", hours, minutes);
        return String.format(Locale.getDefault(), "%d 分鐘", minutes);
    }
}


