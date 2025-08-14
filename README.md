# 智慧任務與時間管理系統（Java SE，手繪風 UI，Notion 風日曆）

本專案是一個以 Java SE 為核心、可直接以命令列啟動的任務與時間管理系統，著重於基礎計算機科學與軟體工程能力的展示與鍛鍊。它結合了下列重點：

- 核心 Java 能力：OOP 模型設計、泛型/集合、日期時間 API、檔案 I/O（CSV）、多執行緒（排程提醒）、輕量網路程式（內建 `HttpServer`）
- 前後端一體：內建靜態網站（手繪風 UI）與 REST 介面，無需外部框架即可體驗 Web 產品流程
- 應用場景完整：任務管理、提醒與統計、Notion 風日/週/月視圖、建議排序（規則式「AI」）、拖拽排序、批次操作、JSON 匯出、PWA 離線支援

本 README 以教學文件為導向，詳細說明架構、設計理念、關鍵實作、使用方式與擴充規劃，方便在課堂報告、履歷作品集、或實習面試中作為完整技術展示。

---

## 目錄

- 專案亮點與學習目標
- 快速開始（Windows/PowerShell 與 macOS/Linux）
- 專案結構與模組職責
- 資料模型與 CSV 格式
- 功能總覽與操作指南（前端與命令列）
- 規則式「AI 建議」排序
- 提醒系統與多執行緒設計
- 內建 HTTP 伺服器與 REST API 規格
- 前端：手繪風設計、日曆視圖、統計圖與使用性
- PWA 與離線快取策略
- 錯誤處理、執行效能與同步化策略
- 測試、維運與除錯建議
- 擴充規劃（Roadmap）
- 貢獻與開發流程建議
- 授權

---

## 專案亮點與學習目標

1. 教材級的 OOP 分層設計：`model`（領域模型）、`repo`（持久化）、`service`（商業邏輯）、`server`（網路層）、`util`（工具）。
2. 以 `Task` 為中心的資料模型，涵蓋優先級、狀態、截止時間、預估/實際工時、類別、提醒時間、排序欄位、標籤等欄位，貼近真實任務系統。
3. 檔案 I/O：使用 CSV 作為持久層，支援完整欄位序列化/反序列化與引號逸出。
4. 多執行緒：`ScheduledExecutorService` 週期檢查任務，逾時與即將到期發出提醒。
5. 網路程式：內建輕量 `HttpServer` 提供 REST API 與靜態檔案（前端 UI），零外部依賴即可啟動整個產品。
6. 前端體驗：
   - 手繪風 UI：以粗線條、紙張紋理、手寫字體、貼紙式標籤，建立溫暖、非正式、友好的產品氣質。
   - Notion 風日曆：支援日/週/月切換、前後導覽，在月視圖以 6x7 網格呈現。
   - 互動力：拖拽列表排序（後端 `sortOrder` 永續化）、雙擊彈窗編輯、批次完成/刪除、快捷鍵（r 刷新、n 聚焦標題）。
7. PWA：`manifest` 與 `service worker` 緩存靜態資源，弱網或離線亦可瀏覽 UI（API 不快取，確保資料一致）。

---

## 快速開始

### 依賴
- JDK 17（建議）或 JDK 11 以上
- Windows/macOS/Linux 皆可；本專案無外部依賴

### Windows（PowerShell）
```powershell
# 於專案根目錄
$files = Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (!(Test-Path out)) { New-Item -ItemType Directory -Path out | Out-Null }
javac -encoding UTF-8 -d out $files
java -cp out app.Main
# 進入主控台後輸入：
start-server
```
- 打開瀏覽器前往 `http://localhost:8080/`。
- 若 PowerShell 出現中文亂碼，可先執行 `chcp 65001` 並設定 `$OutputEncoding` 為 UTF-8。

### macOS/Linux（bash/zsh）
```bash
find src/main/java -name "*.java" | xargs javac -encoding UTF-8 -d out
java -cp out app.Main
# 進入主控台後輸入：
start-server
```

---

## 專案結構與模組職責

```
src/main/java/
  app/
    Main.java                     # 命令列主程式（互動式 CLI）
    model/
      Task.java                   # 任務模型（含排序、提醒等欄位）
      TaskPriority.java           # 優先級列舉（LOW/MEDIUM/HIGH/CRITICAL）
      TaskStatus.java             # 狀態列舉（PENDING/IN_PROGRESS/COMPLETED/CANCELLED）
      User.java                   # 使用者模型（可擴充多帳號）
    repo/
      TaskRepository.java         # 任務儲存介面
      FileTaskRepository.java     # CSV 實作（含引號逸出/讀寫）
    service/
      TaskService.java            # 任務商業邏輯（CRUD、篩選、排序、重排）
      SuggestionService.java      # 規則式建議排序（「AI」權重）
      ReminderService.java        # 定時提醒（多執行緒排程）
      StatsService.java           # 統計報告產生（CLI）
    server/
      MiniHttpServer.java         # 內建 HTTP 伺服器（REST + 靜態檔案）
    util/
      CsvUtil.java                # CSV join/parse/引號逸出工具
public/
  index.html                      # 手繪風前端頁面
  styles.css                      # 手繪風樣式與日曆/彈窗
  app.js                          # 任務互動、日曆、統計、PWA 註冊
  manifest.webmanifest            # PWA Manifest
  sw.js                           # Service Worker（快取靜態資源）
data/
  tasks.csv                       # 任務資料（啟動時自動建立含表頭）
```

- `Main`：
  - `help`/`list`/`add`/`done`/`suggest`/`stats`/`start-server`/`stop-server`/`exit`
- `MiniHttpServer`：
  - 靜態檔案（`/` -> `public/index.html`）
  - REST API（詳見「API 規格」章節）

---

## 資料模型與 CSV 格式

### Task 欄位
- `id`：UUID
- `title`：標題
- `description`：描述
- `priority`：`LOW|MEDIUM|HIGH|CRITICAL`
- `status`：`PENDING|IN_PROGRESS|COMPLETED|CANCELLED`
- `dueDateTime`：`yyyy-MM-dd HH:mm`（本地時區）
- `estimatedMinutes`：預估工時（分）
- `createdAt` / `completedAt`：建立與完成時間
- `tags`：以 `;` 分隔字串
- `category`：任務類別（work/personal/study…）
- `actualMinutes`：實際花費時間（分）
- `reminderBeforeMinutes`：提前提醒時間（分，覆蓋預設 60）
- `sortOrder`：後端儲存的顯示排序序號（拖拽後更新）

### CSV 表頭
```
id,title,description,priority,dueDateTime,estimatedMinutes,status,createdAt,completedAt,tags,category,actualMinutes,reminderBeforeMinutes,sortOrder
```
- 以 `CsvUtil` 確保含逗號/引號/換行的欄位會加引號並做 `"` 逸出。
- `FileTaskRepository` 以 `synchronized` 確保記憶體與檔案間的原子一致性。

---

## 功能總覽與操作指南

### 前端核心功能
- 任務清單：建立、開始、延後 15 分、完成、刪除；拖拽重排（後端持久化排序）。
- 批次動作：勾選多筆 -> 批次完成/刪除。
- 編輯彈窗：雙擊任務卡或日曆項目開啟；可編輯標題、描述、優先級、狀態、截止、估時、類別、實際時間、提前提醒與標籤。
- Notion 風日曆：
  - 視圖切換（日/週/月）與「上一頁/今天/下一頁」導覽。
  - 月視圖以 6x7 網格呈現；週視圖以當週；日視圖以當天。
  - 任務以截止日進行映射，點擊任務可開編輯彈窗。
- 統計圖（手繪棒圖）：近 7 天完成量；使用 SVG 手繪化描邊與些微抖動。
- 快捷鍵：`r` 重新整理、`n` 聚焦標題欄位。
- JSON 匯出：一鍵下載 `tasks.json`（目前為清單視圖結果）。
- PWA：`/`、`/index.html`、`/styles.css`、`/app.js`、`/manifest.webmanifest` 等靜態資源快取；API 不快取。

### CLI（命令列）
- 與早期版本一致；適合離線/純後端檢視與 Demo。

---

## 規則式「AI 建議」排序
`SuggestionService` 以權重計分的啟發式規則進行排序：
- 優先級：`CRITICAL/HIGH/MEDIUM/LOW` 依序給予較高分數。
- 截止：逾期給高權重；1 小時內/4 小時內/24 小時內遞減加權。
- 預估時長：短任務略為加分（促進完成動能）。
- 年齡：建立 3/7 天以上逐步加權（避免拖延）。
- 進行中：`IN_PROGRESS` 給予小幅加成（鼓勵先完成手上工作）。

此規則簡潔、可讀性高，便於在課堂中清楚展示設計思路；未來可替換為學習式模型或參數可配置化。

---

## 提醒系統與多執行緒設計
- `ReminderService` 使用 `ScheduledExecutorService` 每 30 秒檢查一次。
- 規則：
  - `dueDateTime < now` -> 逾期提醒（OVERDUE）
  - 否則若 `dueDateTime <= now + reminderBeforeMinutes（預設 60 分）` -> 即將到期提醒（DUE_SOON）
- 僅在狀態進入新階段時提醒一次，避免洗版。
- 線程安全：背景工作執行於單一 `daemon` 線程；Repository 操作 `synchronized`。

---

## 內建 HTTP 伺服器與 REST API 規格

- 靜態：`/` -> `public/index.html`；`/styles.css`、`/app.js`、`/manifest.webmanifest`、`/sw.js` 等
- JSON：所有 API 皆回傳 `application/json; charset=utf-8`

### 端點一覽
- 列表與篩選/建議
  - `GET /tasks`（可選 query：`suggested=true|false`、`status=`、`priority=`、`tag=`）
- 新增
  - `POST /tasks`（`x-www-form-urlencoded`：`title,description,priority,dueDateTime,estimatedMinutes,tags`）
- 編輯
  - `PUT /tasks/{id}`（`x-www-form-urlencoded`：上述欄位任意子集；缺省欄位不變）
- 狀態操作
  - `POST /tasks/{id}/start`、`POST /tasks/{id}/snooze`（`minutes`，預設 15）、`POST /tasks/{id}/complete`
- 刪除
  - `DELETE /tasks/{id}`
- 拖拽排序持久化
  - `PATCH /tasks/reorder`（`from`,`to`）

說明：
- 新增/編輯傳入的 `dueDateTime` 需符合 `yyyy-MM-dd HH:mm`；
- 伺服器將欄位序列化至 CSV；
- 重新啟動後資料仍在 `data/tasks.csv`。

---

## 前端：手繪風設計、日曆視圖、統計圖與使用性

### 手繪風設計理念
- 不規則線條：粗邊、輕微抖動、貼紙式陰影與外框，營造非機械化的人味。
- 有機形狀與紋理：紙張底紋、柔和配色；避免高飽和硬邊。
- 手寫字體：Google Fonts `Patrick Hand` / `Gloria Hallelujah`。
- 一致性與可用性：保持視覺語言一致；核心操作（新增、完成、編輯）始終清晰。

### Notion 風日曆
- 月視圖：6x7 網格，日期角標 + 任務條目；
- 週/日視圖：以一週/一天為單位展示；
- 與清單同步：同一資料源，互相反映；雙擊開彈窗編輯。

### 統計圖（手繪棒圖）
- 近 7 天完成任務數量；
- 使用 SVG 描邊 + 少許隨機偏移產生手繪感。

### 體驗補充
- 拖拽排序：HTML5 Drag-and-Drop + 後端 `PATCH /tasks/reorder` 永續化；
- 快捷鍵：`r` 刷新、`n` 聚焦標題；
- 批次操作：勾選多筆後執行完成/刪除；
- PWA：離線亦可載入 UI（API 不快取以確保資料一致性）。

---

## PWA 與離線快取策略
- `manifest.webmanifest`：`name/short_name/start_url/display/theme_color` 等；
- `sw.js`：安裝時快取首頁與主要靜態資產；讀取時優先取快取，若無則回源並回寫快取；
- 不快取 `/tasks` API 回應，以確保資料一致且可即時更新提醒。

---

## 錯誤處理、執行效能與同步化策略
- Repository 採 `synchronized` 確保讀寫一致；
- CSV 每次變更即覆寫保存（小型專案簡單可靠）；
- 伺服器端對不支援方法回傳 `405`；找不到資源 `404`；
- 提醒線程以守護線程執行，不會阻止 JVM 結束；
- 靜態檔案以白名單判定 Content-Type，避免錯誤解析。

---

## 測試、維運與除錯建議
- 單元測試建議：以 JUnit 撰寫 `CsvUtil` 與 `FileTaskRepository` round-trip 測試、`SuggestionService` 規則測試、`ReminderService` 邏輯測試（可注入時鐘）。
- 端對端：啟動 `start-server`，以瀏覽器自動化（Selenium/Playwright）驗證清單/日曆/彈窗全流程。
- 效能：CSV 適合個人或小組作業；若資料量上升，可替換為 SQLite/PostgreSQL。
- 部署：可用 `jlink` 打包最小 JRE，或將 `public/` 交由任意靜態主機託管，僅保留 API。

---

## 擴充規劃（Roadmap）
- 任務依日曆拖拽改期、跨日區間任務（開始/結束時間）。
- 進階統計：完成率儀表、週/月分布折線、類別分布圓環、前 N 標籤排行。
- 設定檔：可調整建議排序權重、提醒策略、預設估時與語言。
- 多使用者與授權：加上登入、分群與分享任務。
- WebSocket：即時同步提醒與多人協作。
- 測試完善：單元、整合、端對端與 CI。

---

## 貢獻與開發流程建議
- Commit 規範（建議）：`feat: ...`、`fix: ...`、`refactor: ...`、`docs: ...`、`style: ...`、`test: ...`、`chore: ...`。
- Code Style：清晰命名、早返回、淺層縮排、避免不必要的 catch。
- Issue/PR：描述動機、設計、測試要點與風險；
- 版本：以 Git tag 標記功能里程碑。

---

## 授權
- 建議採用 MIT License（可於企業/學術/個人專案自由使用與修改）。

> 備註：本專案的 UI 設計靈感包含手繪風界面與 Notion 類日曆呈現方式。若需深入參考，請依照學術規範與課堂要求標註來源概念；本專案實作為自行完成之程式碼與樣式。
