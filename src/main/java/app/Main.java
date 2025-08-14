package app;

import app.model.Task;
import app.model.TaskPriority;
import app.model.TaskStatus;
import app.repo.FileTaskRepository;
import app.repo.TaskRepository;
import app.server.MiniHttpServer;
import app.service.ReminderService;
import app.service.SuggestionService;
import app.service.TaskService;
import app.service.StatsService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class Main {
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault());

    public static void main(String[] args) throws Exception {
        ensureDataDirectory();

        Path csvPath = Paths.get("data", "tasks.csv");
        TaskRepository repository = new FileTaskRepository(csvPath);
        TaskService taskService = new TaskService(repository);
        SuggestionService suggestionService = new SuggestionService();
        StatsService statsService = new StatsService();
        ReminderService reminderService = new ReminderService(taskService);
        reminderService.start();

        MiniHttpServer httpServer = new MiniHttpServer(taskService);

        System.out.println("智慧任務與時間管理系統");
        System.out.println("輸入 help 以查看指令");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n> ");
                String line = scanner.nextLine().trim();
                if (line.isEmpty()) continue;

                switch (line) {
                    case "help":
                        printHelp();
                        break;
                    case "list":
                        List<Task> tasks = taskService.listAll();
                        if (tasks.isEmpty()) {
                            System.out.println("沒有任務。");
                        } else {
                            tasks.forEach(Main::printTask);
                        }
                        break;
                    case "add":
                        addTaskInteractively(scanner, taskService);
                        break;
                    case "done":
                        System.out.print("輸入要完成的任務 ID: ");
                        String id = scanner.nextLine().trim();
                        boolean ok = taskService.completeTask(id);
                        System.out.println(ok ? "已完成。" : "找不到該任務。");
                        break;
                    case "suggest":
                        List<Task> suggested = suggestionService.sortBySmartHeuristics(taskService.listAll());
                        if (suggested.isEmpty()) {
                            System.out.println("沒有任務。");
                        } else {
                            System.out.println("建議處理順序：");
                            suggested.forEach(Main::printTask);
                        }
                        break;
                    case "stats":
                        System.out.println(statsService.buildStatsReport(taskService.listAll()));
                        break;
                    case "start-server":
                        httpServer.start(8080);
                        System.out.println("HTTP 伺服器啟動於 http://localhost:8080/tasks");
                        break;
                    case "stop-server":
                        httpServer.stop();
                        System.out.println("HTTP 伺服器已停止");
                        break;
                    case "exit":
                        httpServer.stop();
                        reminderService.stop();
                        System.out.println("再見！");
                        return;
                    default:
                        System.out.println("未知指令，輸入 help 查看。");
                }
            }
        }
    }

    private static void printHelp() {
        System.out.println("指令：");
        System.out.println("  help           顯示此說明");
        System.out.println("  list           列出所有任務");
        System.out.println("  add            新增一個任務");
        System.out.println("  done           將任務標記為完成");
        System.out.println("  suggest        依建議排序顯示任務");
        System.out.println("  stats          顯示統計報告");
        System.out.println("  start-server   啟動內建 HTTP 伺服器");
        System.out.println("  stop-server    停止 HTTP 伺服器");
        System.out.println("  exit           離開程式");
    }

    private static void addTaskInteractively(Scanner scanner, TaskService taskService) {
        System.out.print("標題: ");
        String title = scanner.nextLine().trim();

        System.out.print("描述(可空白): ");
        String description = scanner.nextLine().trim();

        System.out.print("優先級(LOW/MEDIUM/HIGH/CRITICAL): ");
        TaskPriority priority;
        try {
            priority = TaskPriority.valueOf(scanner.nextLine().trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            priority = TaskPriority.MEDIUM;
        }

        System.out.print("預估分鐘數(整數，可空白預設30): ");
        String estStr = scanner.nextLine().trim();
        int estimated = 30;
        if (!estStr.isEmpty()) {
            try { estimated = Integer.parseInt(estStr); } catch (Exception ignored) {}
        }

        System.out.print("截止時間 yyyy-MM-dd HH:mm（可空白略過）: ");
        String dueStr = scanner.nextLine().trim();
        LocalDateTime due = null;
        if (!dueStr.isEmpty()) {
            try { due = LocalDateTime.parse(dueStr, DATE_TIME_FMT); } catch (Exception ignored) {}
        }

        System.out.print("標籤（以分號 ; 分隔，可空白）: ");
        String tagStr = scanner.nextLine().trim();

        Task task = taskService.addTask(title, description, priority, due, estimated, tagStr);
        System.out.println("已新增任務：" + task.getId());
    }

    private static void printTask(Task t) {
        String due = t.getDueDateTime() == null ? "(無)" : t.getDueDateTime().format(DATE_TIME_FMT);
        System.out.printf("[%s] %s | 優先:%s | 截止:%s | 估時:%d | 狀態:%s | 標籤:%s%n",
                t.getId(), t.getTitle(), t.getPriority(), due, t.getEstimatedMinutes(), t.getStatus(), String.join(";", t.getTags()));
    }

    private static void ensureDataDirectory() throws Exception {
        Path dataDir = Paths.get("data");
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir);
        }
    }
}


