package com.ruoyi.project.talent.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 人才培养 · 一键爬取。
 *
 * <p>点击「爬取数据」后在本机后台启动 Python 爬虫进程，逐行读取其输出更新进度；
 * 进程正常结束后自动找到最新 CSV，复用 {@link TalentCsvImportService} 入库，前端刷新看板。</p>
 */
@Service
public class TalentCrawlService
{
    private static final Logger log = LoggerFactory.getLogger(TalentCrawlService.class);

    private static final Pattern SAVED = Pattern.compile("已保存\\s*(\\d+)\\s*/\\s*(\\d+)\\s*条");
    private static final Pattern DETAIL = Pattern.compile("第\\s*(\\d+)\\s*/\\s*(\\d+)\\s*个职位详情页");
    private static final Pattern INDUSTRY = Pattern.compile("行业进度\\s*(\\d+)\\s*/\\s*(\\d+)");

    @Value("${talent.crawl.python:python}")
    private String python;

    @Value("${talent.crawl.script:}")
    private String script;

    @Value("${talent.crawl.working-dir:}")
    private String workingDir;

    @Value("${talent.crawl.output-dir:}")
    private String outputDir;

    @Value("${talent.crawl.total-target:200}")
    private int totalTarget;

    @Value("${talent.crawl.timeout-minutes:90}")
    private int timeoutMinutes;

    @Value("${talent.cleaned-data-dir:D:/000/fiveEi/04-清洗后数据}")
    private String cleanedDataDir;

    @Autowired
    private TalentCsvImportService importService;

    private final Object lock = new Object();

    private volatile Task current;

    private volatile Process process;

    /** 启动爬取；已有任务在跑时直接返回当前进度 */
    public Map<String, Object> start()
    {
        synchronized (lock)
        {
            Task running = current;
            if (running != null && running.isRunning())
            {
                return running.toMap();
            }

            Task task = new Task();
            current = task;
            task.status = "RUNNING";
            task.percent = 1;
            task.message = "正在启动爬虫...";

            File scriptFile = new File(script == null ? "" : script);
            if (!scriptFile.isFile())
            {
                task.fail("找不到爬虫脚本：" + script);
                return task.toMap();
            }

            try
            {
                ProcessBuilder builder = new ProcessBuilder(python, "-u", scriptFile.getAbsolutePath(), "--once");
                if (workingDir != null && !workingDir.trim().isEmpty())
                {
                    builder.directory(new File(workingDir));
                }
                builder.redirectErrorStream(true);
                builder.environment().put("PYTHONIOENCODING", "utf-8");
                builder.environment().put("PYTHONUNBUFFERED", "1");
                process = builder.start();

                Thread pump = new Thread(() -> pumpOutput(task), "talent-crawl-output");
                pump.setDaemon(true);
                pump.start();

                Thread waiter = new Thread(() -> waitForExit(task), "talent-crawl-wait");
                waiter.setDaemon(true);
                waiter.start();

                Thread watchdog = new Thread(() -> watchdog(task), "talent-crawl-timeout");
                watchdog.setDaemon(true);
                watchdog.start();

                log.info("人才培养爬虫已启动：{} {}", python, scriptFile.getAbsolutePath());
            }
            catch (Exception e)
            {
                log.error("启动爬虫失败", e);
                task.fail("启动爬虫失败：" + e.getMessage());
            }
            return task.toMap();
        }
    }

    /** 查询当前任务状态；没有任务时返回 IDLE */
    public Map<String, Object> status()
    {
        Task task = current;
        if (task == null)
        {
            Map<String, Object> idle = new LinkedHashMap<>();
            idle.put("id", "");
            idle.put("status", "IDLE");
            idle.put("percent", 0);
            idle.put("message", "尚未执行爬取");
            return idle;
        }
        return task.toMap();
    }

    // ------------------------------------------------------------------ 进程与进度
    private void pumpOutput(Task task)
    {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.trim().isEmpty())
                {
                    continue;
                }
                task.log(line);
                updateProgress(task, line);
            }
        }
        catch (Exception e)
        {
            log.warn("读取爬虫输出失败", e);
        }
    }

    private void updateProgress(Task task, String line)
    {
        String text = line.replaceAll("[\\p{So}\\p{Cn}]", "").trim();
        if (text.length() > 120)
        {
            text = text.substring(0, 120) + "...";
        }
        if (!text.isEmpty())
        {
            task.message = text;
        }

        Matcher saved = SAVED.matcher(line);
        if (saved.find())
        {
            int count = Integer.parseInt(saved.group(1));
            int target = Math.max(1, totalTarget);
            int percent = (int) Math.round(count * 85.0 / target);
            task.percent = Math.max(task.percent, Math.min(85, Math.max(2, percent)));
            task.message = "已保存 " + count + " 条岗位（目标 " + target + " 条）";
        }

        Matcher detail = DETAIL.matcher(line);
        if (detail.find())
        {
            task.message = "正在爬取职位详情 " + detail.group(1) + "/" + detail.group(2);
        }

        Matcher industry = INDUSTRY.matcher(line);
        if (industry.find())
        {
            int idx = Integer.parseInt(industry.group(1));
            int total = Math.max(1, Integer.parseInt(industry.group(2)));
            int percent = 5 + (int) Math.round(idx * 80.0 / total);
            task.percent = Math.max(task.percent, Math.min(85, percent));
            task.message = "正在爬取行业 " + idx + "/" + total;
        }

        if (line.contains("已导出JSON") || line.contains("可视化页面已生成"))
        {
            task.percent = Math.max(task.percent, 88);
        }
    }

    private void waitForExit(Task task)
    {
        try
        {
            int code = process.waitFor();
            if (!task.isRunning())
            {
                return;
            }
            if (code != 0)
            {
                task.fail("爬虫进程退出码 " + code + "，最后输出：" + task.lastLine());
                return;
            }
            importLatest(task);
        }
        catch (Exception e)
        {
            log.error("等待爬虫结束失败", e);
            task.fail("爬虫执行异常：" + e.getMessage());
        }
    }

    private void watchdog(Task task)
    {
        try
        {
            Thread.sleep(Math.max(1, timeoutMinutes) * 60_000L);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return;
        }
        if (task.isRunning() && process != null && process.isAlive())
        {
            process.destroyForcibly();
            task.fail("爬取超时（" + timeoutMinutes + " 分钟），已终止进程");
        }
    }

    /** 找到本次爬取生成的 CSV 并复用导入服务入库 */
    private void importLatest(Task task)
    {
        task.status = "IMPORTING";
        task.percent = 92;
        task.message = "爬取完成，正在导入数据...";
        try
        {
            File dir = new File(outputDir == null ? "" : outputDir);
            File latest = null;
            File[] files = dir.listFiles();
            if (files != null)
            {
                for (File file : files)
                {
                    String name = file.getName().toLowerCase();
                    if (!name.endsWith(".csv") || name.startsWith("cleaned"))
                    {
                        continue;
                    }
                    if (file.lastModified() < task.startedAt.getTime() - 60_000L)
                    {
                        continue;
                    }
                    if (latest == null || file.lastModified() > latest.lastModified())
                    {
                        latest = file;
                    }
                }
            }
            if (latest == null)
            {
                task.fail("爬取结束，但没有在 " + outputDir + " 找到本次生成的 CSV");
                return;
            }

            task.message = "正在导入 " + latest.getName() + "...";
            Map<String, Object> result = importService.importCsv(new FileMultipartFile(latest), "");
            copyToCleanedDir(latest);
            task.status = "DONE";
            task.percent = 100;
            task.result = result;
            Object count = result.get("postingCount");
            task.message = "爬取并导入完成：新增 " + (count == null ? 0 : count) + " 条岗位";
            task.finishedAt = new Date();
            log.info("爬取导入完成：{} 条，文件 {}", count, latest.getAbsolutePath());
        }
        catch (Exception e)
        {
            log.error("爬取结果导入失败", e);
            task.fail("爬取完成但导入失败：" + e.getMessage());
        }
    }

    /** 把本次爬取结果复制到「04-清洗后数据」，供技能热度曲线按修改时间生成新时间点 */
    private void copyToCleanedDir(File latest)
    {
        try
        {
            File dir = new File(cleanedDataDir);
            if (!dir.isDirectory() && !dir.mkdirs())
            {
                log.warn("清洗后数据目录不存在且创建失败：{}", cleanedDataDir);
                return;
            }
            String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File target = new File(dir, "BOSS爬取_" + stamp + ".csv");
            Files.copy(latest.toPath(), target.toPath());
            log.info("爬取结果已复制到清洗目录：{}", target.getAbsolutePath());
        }
        catch (Exception e)
        {
            log.warn("复制爬取结果到清洗目录失败", e);
        }
    }

    // ------------------------------------------------------------------ 任务状态
    private static class Task
    {
        final String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        final Date startedAt = new Date();

        final Deque<String> tail = new ArrayDeque<>();

        volatile String status = "RUNNING";

        volatile int percent;

        volatile String message = "";

        volatile Date finishedAt;

        volatile Map<String, Object> result;

        boolean isRunning()
        {
            return "RUNNING".equals(status) || "IMPORTING".equals(status);
        }

        void fail(String reason)
        {
            status = "ERROR";
            message = reason;
            finishedAt = new Date();
        }

        synchronized void log(String line)
        {
            tail.addLast(line);
            while (tail.size() > 30)
            {
                tail.removeFirst();
            }
        }

        synchronized String lastLine()
        {
            return tail.isEmpty() ? "" : tail.peekLast();
        }

        Map<String, Object> toMap()
        {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", id);
            map.put("status", status);
            map.put("percent", percent);
            map.put("message", message);
            map.put("startedAt", startedAt);
            map.put("finishedAt", finishedAt);
            map.put("result", result);
            synchronized (this)
            {
                map.put("logs", new ArrayList<>(tail));
            }
            return map;
        }
    }

    /** 把爬虫生成的 CSV 包装成 MultipartFile，复用现有导入逻辑 */
    private static class FileMultipartFile implements MultipartFile
    {
        private final File file;

        private final byte[] bytes;

        FileMultipartFile(File file) throws IOException
        {
            this.file = file;
            this.bytes = Files.readAllBytes(file.toPath());
        }

        @Override
        public String getName()
        {
            return "file";
        }

        @Override
        public String getOriginalFilename()
        {
            return file.getName();
        }

        @Override
        public String getContentType()
        {
            return "text/csv";
        }

        @Override
        public boolean isEmpty()
        {
            return bytes.length == 0;
        }

        @Override
        public long getSize()
        {
            return bytes.length;
        }

        @Override
        public byte[] getBytes()
        {
            return bytes;
        }

        @Override
        public InputStream getInputStream()
        {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(File dest) throws IOException
        {
            try (OutputStream out = Files.newOutputStream(dest.toPath()))
            {
                out.write(bytes);
            }
        }
    }
}
