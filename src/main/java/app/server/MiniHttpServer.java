package app.server;

import app.model.Task;
import app.model.TaskPriority;
import app.service.SuggestionService;
import app.model.Note;
import app.repo.FileNoteRepository;
import app.service.NoteService;
import app.service.TaskService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MiniHttpServer {
    private HttpServer server;
    private final TaskService taskService;
    private final SuggestionService suggestionService = new SuggestionService();
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault());

    public MiniHttpServer(TaskService taskService) {
        this.taskService = taskService;
    }

    public synchronized void start(int port) throws IOException {
        if (server != null) return;
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/tasks", new TasksHandler(taskService, suggestionService));
        // calendar notes
        NoteService noteService = new NoteService(new FileNoteRepository(Path.of("data","notes.csv")));
        server.createContext("/notes", new NotesHandler(noteService));
        server.createContext("/ics", new IcsHandler(taskService));
        server.createContext("/", new StaticHandler(Paths.get("public")));
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    static class TasksHandler implements HttpHandler {
        private final TaskService taskService;
        private final SuggestionService suggestionService;
        TasksHandler(TaskService taskService, SuggestionService suggestionService) {
            this.taskService = taskService;
            this.suggestionService = suggestionService;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if ("GET".equalsIgnoreCase(method)) {
                boolean suggested = "true".equalsIgnoreCase(getQueryParam(exchange, "suggested"));
                String status = getQueryParam(exchange, "status");
                String priority = getQueryParam(exchange, "priority");
                String tag = getQueryParam(exchange, "tag");
                String startStr = getQueryParam(exchange, "start");
                String endStr = getQueryParam(exchange, "end");
                List<Task> tasks = (status!=null||priority!=null||tag!=null) ? taskService.filter(status, priority, tag) : taskService.listAll();
                if (startStr != null && endStr != null) {
                    try {
                        java.time.LocalDate start = java.time.LocalDate.parse(startStr);
                        java.time.LocalDate end = java.time.LocalDate.parse(endStr);
                        tasks = tasks.stream().filter(t -> t.getDueDateTime()!=null &&
                                !t.getDueDateTime().toLocalDate().isBefore(start) && !t.getDueDateTime().toLocalDate().isAfter(end))
                                .toList();
                    } catch (Exception ignored) {}
                }
                if (suggested) tasks = suggestionService.sortBySmartHeuristics(tasks);
                sendJson(exchange, 200, JsonUtil.toJson(tasks));
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                if (path.matches("^/tasks/[a-zA-Z0-9\\-]+/complete$")) {
                    String id = path.substring("/tasks/".length(), path.length() - "/complete".length());
                    boolean ok = taskService.completeTask(id);
                    if (ok) sendJson(exchange, 200, "{\"ok\":true}");
                    else sendJson(exchange, 404, "{\"ok\":false,\"error\":\"not_found\"}");
                    return;
                }
                if (path.matches("^/tasks/[a-zA-Z0-9\\-]+/start$")) {
                    String id = path.substring("/tasks/".length(), path.length() - "/start".length());
                    boolean ok = taskService.startTask(id);
                    sendJson(exchange, ok?200:404, ok?"{\"ok\":true}":"{\"ok\":false}");
                    return;
                }
                if (path.matches("^/tasks/[a-zA-Z0-9\\-]+/snooze$")) {
                    String id = path.substring("/tasks/".length(), path.length() - "/snooze".length());
                    Map<String,String> form = parseForm(exchange);
                    int minutes = 15;
                    try { minutes = Integer.parseInt(form.getOrDefault("minutes","15")); } catch(Exception ignored){}
                    boolean ok = taskService.snoozeTask(id, minutes);
                    sendJson(exchange, ok?200:404, ok?"{\"ok\":true}":"{\"ok\":false}");
                    return;
                }

                // POST /tasks  (x-www-form-urlencoded)
                Map<String,String> form = parseForm(exchange);
                String title = form.getOrDefault("title", "").trim();
                String description = form.getOrDefault("description", "").trim();
                String priorityStr = form.getOrDefault("priority", "MEDIUM").trim().toUpperCase(Locale.ROOT);
                TaskPriority priority;
                try { priority = TaskPriority.valueOf(priorityStr); } catch (Exception e) { priority = TaskPriority.MEDIUM; }
                String dueStr = form.getOrDefault("dueDateTime", "").trim();
                LocalDateTime due = null;
                if (!dueStr.isEmpty()) {
                    try { due = LocalDateTime.parse(dueStr, DATE_TIME_FMT); } catch (Exception ignored) {}
                }
                int estimated = 30;
                try { estimated = Integer.parseInt(form.getOrDefault("estimatedMinutes", "30").trim()); } catch (Exception ignored) {}
                String tags = form.getOrDefault("tags", "");
                Task task = taskService.addTask(title, description, priority, due, estimated, tags);
                // optional: category, actualMinutes, reminderBeforeMinutes, sortOrder for future extension
                sendJson(exchange, 201, JsonUtil.toJson(task));
                return;
            }

            if ("PUT".equalsIgnoreCase(method)) {
                if (path.matches("^/tasks/[a-zA-Z0-9\\-]+$")) {
                    String id = path.substring("/tasks/".length());
                    Map<String,String> form = parseForm(exchange);
                    String title = form.get("title");
                    String description = form.get("description");
                    TaskPriority priority = null; if (form.containsKey("priority")) {
                        try { priority = TaskPriority.valueOf(form.get("priority").toUpperCase(Locale.ROOT)); } catch(Exception ignored) {}
                    }
                    LocalDateTime due = null; if (form.containsKey("dueDateTime")) {
                        try { due = LocalDateTime.parse(form.get("dueDateTime"), DATE_TIME_FMT); } catch(Exception ignored) {}
                    }
                    Integer est = null; if (form.containsKey("estimatedMinutes")) {
                        try { est = Integer.parseInt(form.get("estimatedMinutes")); } catch(Exception ignored) {}
                    }
                    String tags = form.get("tags");
                    boolean ok = taskService.updateTask(id, title, description, priority, due, est, tags);
                    sendJson(exchange, ok?200:404, ok?"{\"ok\":true}":"{\"ok\":false}");
                    return;
                }
                if (path.matches("^/tasks/[a-zA-Z0-9\\-]+/reschedule$")) {
                    String id = path.substring("/tasks/".length(), path.length()-"/reschedule".length());
                    Map<String,String> form = parseForm(exchange);
                    String dateStr = form.get("date");
                    if (dateStr==null || dateStr.isEmpty()) { sendJson(exchange,400,"{\"ok\":false,\"error\":\"date_required\"}"); return; }
                    LocalDate date = LocalDate.parse(dateStr);
                    boolean ok = taskService.rescheduleDate(id, date, null);
                    sendJson(exchange, ok?200:404, ok?"{\"ok\":true}":"{\"ok\":false}");
                    return;
                }
            }

            if ("DELETE".equalsIgnoreCase(method)) {
                if (path.matches("^/tasks/[a-zA-Z0-9\\-]+$")) {
                    String id = path.substring("/tasks/".length());
                    boolean ok = taskService.deleteTask(id);
                    sendJson(exchange, ok?200:404, ok?"{\"ok\":true}":"{\"ok\":false}");
                    return;
                }
            }

            if ("PATCH".equalsIgnoreCase(method)) {
                if (path.equals("/tasks/reorder")) {
                    Map<String,String> form = parseForm(exchange);
                    String fromId = form.get("from");
                    String toId = form.get("to");
                    boolean ok = taskService.reorder(fromId, toId);
                    sendJson(exchange, ok?200:400, ok?"{\"ok\":true}":"{\"ok\":false}");
                    return;
                }
                if (path.matches("^/tasks/[a-zA-Z0-9\\-]+/duration$")) {
                    String id = path.substring("/tasks/".length(), path.length()-"/duration".length());
                    Map<String,String> form = parseForm(exchange);
                    int m = 30; try { m = Integer.parseInt(form.getOrDefault("minutes","30")); } catch(Exception ignored){}
                    boolean ok = taskService.updateDuration(id, m);
                    sendJson(exchange, ok?200:404, ok?"{\"ok\":true}":"{\"ok\":false}");
                    return;
                }
            }

            exchange.sendResponseHeaders(405, -1);
        }

        private static String getQueryParam(HttpExchange exchange, String key) {
            String q = exchange.getRequestURI().getQuery();
            if (q == null || q.isEmpty()) return null;
            String[] parts = q.split("&");
            for (String p : parts) {
                int i = p.indexOf('=');
                if (i > 0) {
                    String k = URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8);
                    if (key.equals(k)) return URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
                } else if (key.equals(p)) {
                    return "";
                }
            }
            return null;
        }

        private static Map<String,String> parseForm(HttpExchange exchange) throws IOException {
            Headers headers = exchange.getRequestHeaders();
            String contentType = headers.getFirst("Content-Type");
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String,String> map = new HashMap<>();
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("application/x-www-form-urlencoded")) {
                String[] pairs = body.split("&");
                for (String pair : pairs) {
                    if (pair.isEmpty()) continue;
                    int i = pair.indexOf('=');
                    if (i >= 0) {
                        String k = URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8);
                        String v = URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
                        map.put(k, v);
                    } else {
                        map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                    }
                }
            }
            return map;
        }

        private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(code, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }
        }
    }

    static class StaticHandler implements HttpHandler {
        private final Path baseDir;
        StaticHandler(Path baseDir) { this.baseDir = baseDir; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            // Silence favicon.ico 404: return 204 No Content when not present
            if (path.equals("/favicon.ico")) {
                if (!Files.exists(baseDir.resolve("favicon.ico"))) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
            }
            Path file = baseDir.resolve(path.substring(1)).normalize();
            if (!file.startsWith(baseDir) || !Files.exists(file) || Files.isDirectory(file)) {
                byte[] nf = "Not Found".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(404, nf.length);
                try (OutputStream os = exchange.getResponseBody()) { os.write(nf); }
                return;
            }
            String contentType = guessContentType(file);
            byte[] bytes = Files.readAllBytes(file);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        }

        private String guessContentType(Path file) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".html")) return "text/html; charset=utf-8";
            if (name.endsWith(".css")) return "text/css; charset=utf-8";
            if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (name.endsWith(".svg")) return "image/svg+xml";
            if (name.endsWith(".png")) return "image/png";
            if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
            return "application/octet-stream";
        }
    }

    // Minimal JSON util (only for our Task fields)
    static class JsonUtil {
        static String toJsonNotes(List<Note> notes) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            boolean first = true;
            for (Note n : notes) {
                if (!first) sb.append(','); first=false;
                sb.append('{')
                        .append("\"id\":\"").append(escape(n.getId()==null?"":n.getId())).append('\"').append(',')
                        .append("\"date\":\"").append(n.getDate()==null?"":n.getDate().toString()).append('\"').append(',')
                        .append("\"content\":\"").append(escape(n.getContent()==null?"":n.getContent())).append('\"')
                        .append('}');
            }
            sb.append(']');
            return sb.toString();
        }
        static String toJson(List<Task> tasks) {
            StringBuilder sb = new StringBuilder();
            sb.append('[');
            boolean first = true;
            for (Task t : tasks) {
                if (!first) sb.append(',');
                first = false;
                sb.append(toJson(t));
            }
            sb.append(']');
            return sb.toString();
        }

        static String toJson(Task t) {
            StringBuilder sb = new StringBuilder();
            sb.append('{')
                    .append("\"id\":\"").append(escape(t.getId())).append('\"').append(',')
                    .append("\"title\":\"").append(escape(n(t.getTitle()))).append('\"').append(',')
                    .append("\"priority\":\"").append(t.getPriority() == null ? "" : t.getPriority().name()).append('\"').append(',')
                    .append("\"status\":\"").append(t.getStatus() == null ? "" : t.getStatus().name()).append('\"').append(',')
                    .append("\"dueDateTime\":\"").append(t.getDueDateTime() == null ? "" : t.getDueDateTime().format(DATE_TIME_FMT)).append('\"').append(',')
                    .append("\"estimatedMinutes\":").append(t.getEstimatedMinutes()).append(',')
                    .append("\"category\":\"").append(escape(n(t.getCategory()))).append('\"').append(',')
                    .append("\"actualMinutes\":").append(t.getActualMinutes()==null?"null":t.getActualMinutes()).append(',')
                    .append("\"reminderBeforeMinutes\":").append(t.getReminderBeforeMinutes()==null?"null":t.getReminderBeforeMinutes()).append(',')
                    .append("\"sortOrder\":").append(t.getSortOrder()==null?"null":t.getSortOrder()).append(',')
                    .append("\"createdAt\":\"").append(t.getCreatedAt()==null?"":t.getCreatedAt().format(DATE_TIME_FMT)).append('\"').append(',')
                    .append("\"completedAt\":\"").append(t.getCompletedAt()==null?"":t.getCompletedAt().format(DATE_TIME_FMT)).append('\"').append(',')
                    .append("\"tags\":[");
            boolean first = true;
            if (t.getTags() != null) {
                for (String tag : t.getTags()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append('\"').append(escape(tag)).append('\"');
                }
            }
            sb.append("]}");
            return sb.toString();
        }

        private static String n(String s) { return s == null ? "" : s; }

        private static String escape(String s) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int)c));
                        else sb.append(c);
                }
            }
            return sb.toString();
        }
    }

    // ICS export handler
    static class IcsHandler implements HttpHandler {
        private final TaskService taskService;
        IcsHandler(TaskService s){ this.taskService = s; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405,-1); return; }
            Map<String,String> q = new HashMap<>();
            String raw = exchange.getRequestURI().getQuery();
            if (raw!=null) for(String p: raw.split("&")){ int i=p.indexOf('='); if(i>0) q.put(URLDecoder.decode(p.substring(0,i), StandardCharsets.UTF_8), URLDecoder.decode(p.substring(i+1), StandardCharsets.UTF_8)); }
            LocalDate start = q.containsKey("start") ? LocalDate.parse(q.get("start")) : LocalDate.now().minusDays(7);
            LocalDate end = q.containsKey("end") ? LocalDate.parse(q.get("end")) : LocalDate.now().plusDays(35);
            List<Task> tasks = taskService.listAll().stream()
                    .filter(t -> t.getDueDateTime()!=null && !t.getDueDateTime().toLocalDate().isBefore(start) && !t.getDueDateTime().toLocalDate().isAfter(end))
                    .toList();
            String ics = buildIcs(tasks);
            byte[] body = ics.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/calendar; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=tasks.ics");
            exchange.sendResponseHeaders(200, body.length);
            try(OutputStream os = exchange.getResponseBody()){ os.write(body); }
        }

        private String buildIcs(List<Task> tasks){
            StringBuilder sb = new StringBuilder();
            sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//SmartTasks//EN\r\n");
            for (Task t : tasks) {
                java.time.LocalDateTime end = t.getDueDateTime();
                java.time.LocalDateTime start = end;
                if (end != null) start = end.minusMinutes(Math.max(1, t.getEstimatedMinutes()));
                String dtStart = fmtIcs(start);
                String dtEnd = fmtIcs(end);
                sb.append("BEGIN:VEVENT\r\n")
                        .append("UID:").append(t.getId()).append("\r\n")
                        .append("DTSTART:").append(dtStart).append("\r\n")
                        .append("DTEND:").append(dtEnd).append("\r\n")
                        .append("SUMMARY:").append(escapeIcs(t.getTitle()==null?"":t.getTitle())).append("\r\n")
                        .append("DESCRIPTION:").append(escapeIcs(t.getDescription()==null?"":t.getDescription())).append("\r\n")
                        .append("END:VEVENT\r\n");
            }
            sb.append("END:VCALENDAR\r\n");
            return sb.toString();
        }

        private String fmtIcs(java.time.LocalDateTime dt){
            if (dt == null) dt = java.time.LocalDateTime.now();
            java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
            return dt.format(f);
        }
        private String escapeIcs(String s){ return s.replace("\\", "\\\\").replace(";","\\;").replace(",","\\,").replace("\n","\\n"); }
    }
    // Notes handler
    static class NotesHandler implements HttpHandler {
        private final NoteService noteService;
        NotesHandler(NoteService s){ this.noteService = s; }
        @Override public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                Map<String,String> q = new HashMap<>();
                String raw = exchange.getRequestURI().getQuery();
                if (raw!=null) for(String p: raw.split("&")){ int i=p.indexOf('='); if(i>0) q.put(URLDecoder.decode(p.substring(0,i), StandardCharsets.UTF_8), URLDecoder.decode(p.substring(i+1), StandardCharsets.UTF_8)); }
                LocalDate start = LocalDate.parse(q.getOrDefault("start", LocalDate.now().minusDays(7).toString()));
                LocalDate end = LocalDate.parse(q.getOrDefault("end", LocalDate.now().plusDays(35).toString()));
                String json = JsonUtil.toJsonNotes(noteService.listByRange(start, end));
                TasksHandler.sendJson(exchange, 200, json); return;
            }
            if ("POST".equalsIgnoreCase(method)) {
                Map<String,String> form = TasksHandler.parseForm(exchange);
                LocalDate date = LocalDate.parse(form.get("date"));
                String content = form.getOrDefault("content","");
                String id = form.get("id");
                Note n = noteService.upsert(date, content, id);
                TasksHandler.sendJson(exchange, 201, "{\"ok\":true,\"id\":\""+n.getId()+"\"}"); return;
            }
            if ("DELETE".equalsIgnoreCase(method)) {
                Map<String,String> form = TasksHandler.parseForm(exchange);
                String id = form.get("id");
                if (id!=null) noteService.delete(id);
                TasksHandler.sendJson(exchange, 200, "{\"ok\":true}"); return;
            }
            exchange.sendResponseHeaders(405,-1);
        }
    }
}


