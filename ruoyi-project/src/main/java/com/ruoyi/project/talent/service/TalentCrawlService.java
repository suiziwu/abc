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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
 *
 * <p>爬虫脚本、工作目录、输出目录都通过 {@link TalentPathResolver} 解析：
 * 配置里写相对路径即可（相对工程根目录），配置的绝对路径失效时也会自动在工程目录里找，
 * 避免换机器/移动工程后按钮直接报“找不到爬虫脚本”。</p>
 */
@Service
public class TalentCrawlService
{
    private static final Logger log = LoggerFactory.getLogger(TalentCrawlService.class);

    private static final Pattern SAVED = Pattern.compile("已保存\\s*(\\d+)\\s*/\\s*(\\d+)\\s*条");
    private static final Pattern DETAIL = Pattern.compile("第\\s*(\\d+)\\s*/\\s*(\\d+)\\s*个职位详情页");
    private static final Pattern INDUSTRY = Pattern.compile("行业进度\\s*(\\d+)\\s*/\\s*(\\d+)");

    /**
     * 爬虫打印的落盘路径：{@code 💾 原始数据已保存到本地：<path> (共N条)}。
     *
     * <p>爬虫脚本里的 DATA_DIR 有可能被改回硬编码路径（历史上就发生过一次），
     * 那样 CSV 会写到别的目录、后端按 output-dir 找不到，任务白白跑完却导不进看板。
     * 所以这里直接以爬虫自己打印的路径为准，不依赖两边配置一致。</p>
     */
    private static final Pattern CSV_SAVED = Pattern.compile("原始数据已保存到本地[：:]\\s*(.+?)\\s*(?:\\(共|$)");

    /** 爬虫脚本候选相对路径（工程根目录下） */
    private static final String SCRIPT_RELATIVE = "fiveEi/01-爬虫脚本/BOSS直聘爬虫.py";

    private static final String WORK_DIR_RELATIVE = "fiveEi/01-爬虫脚本";

    private static final String OUTPUT_DIR_RELATIVE = "fiveEi/新爬取";

    private static final String CLEANED_DIR_RELATIVE = "fiveEi/04-清洗后数据";

    @Value("${talent.crawl.python:python}")
    private String python;

    @Value("${talent.crawl.script:" + SCRIPT_RELATIVE + "}")
    private String script;

    @Value("${talent.crawl.working-dir:" + WORK_DIR_RELATIVE + "}")
    private String workingDir;

    @Value("${talent.crawl.output-dir:" + OUTPUT_DIR_RELATIVE + "}")
    private String outputDir;

    @Value("${talent.crawl.total-target:200}")
    private int totalTarget;

    @Value("${talent.crawl.timeout-minutes:90}")
    private int timeoutMinutes;

    @Value("${talent.cleaned-data-dir:" + CLEANED_DIR_RELATIVE + "}")
    private String cleanedDataDir;

    @Autowired
    private TalentCsvImportService importService;

    private final Object lock = new Object();

    private volatile Task current;

    private volatile Process process;

    /** 探测成功后缓存可用解释器，避免每次点击都重新试一遍 */
    private volatile List<String> workingPython;

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

            File scriptFile = TalentPathResolver.resolveFile(script, SCRIPT_RELATIVE);
            if (scriptFile == null)
            {
                task.fail("找不到爬虫脚本：" + script + "（已尝试工程目录下的 " + SCRIPT_RELATIVE + "）");
                return task.toMap();
            }

            File outDir = TalentPathResolver.resolveDir(outputDir, OUTPUT_DIR_RELATIVE, true);
            if (outDir == null)
            {
                task.fail("爬取输出目录不可用：" + outputDir + "，请检查 talent.crawl.output-dir 配置或目录权限");
                return task.toMap();
            }
            task.outputDir = outDir;

            File workDir = TalentPathResolver.resolveDir(workingDir, WORK_DIR_RELATIVE, false);
            if (workDir == null)
            {
                workDir = scriptFile.getParentFile();
            }

            // 先确认 Python 能跑、依赖齐全，避免进程起来后立刻死在 import 上，只留一行看不懂的报错
            List<String> pythonCmd = pythonCommand();
            if (!probePython(pythonCmd))
            {
                task.fail("没有找到可用的 Python 环境：已尝试「" + python + "」以及 python/python3/py -3 和常见安装目录，"
                        + "都缺少爬虫依赖 DrissionPage / pandas。请在装有依赖的解释器上执行 "
                        + "pip install DrissionPage pandas，再把 talent.crawl.python 指向它");
                return task.toMap();
            }

            try
            {
                List<String> command = new ArrayList<>(pythonCmd);
                command.add("-u");
                command.add(scriptFile.getAbsolutePath());
                command.add("--once");
                ProcessBuilder builder = new ProcessBuilder(command);
                if (workDir != null)
                {
                    builder.directory(workDir);
                }
                builder.redirectErrorStream(true);
                builder.environment().put("PYTHONIOENCODING", "utf-8");
                builder.environment().put("PYTHONUNBUFFERED", "1");
                builder.environment().put("PYTHONUTF8", "1");
                // 让爬虫把 CSV 写到这里，与下面的 importLatest 保持一致
                builder.environment().put("BOSS_DATA_DIR", outDir.getAbsolutePath());
                // 后台运行时不需要弹出数据看板页面
                builder.environment().put("BOSS_NO_BROWSER", "1");
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

                log.info("人才培养爬虫已启动：{} {}（工作目录 {}，输出目录 {}）", pythonCmd,
                        scriptFile.getAbsolutePath(), workDir, outDir.getAbsolutePath());
            }
            catch (Exception e)
            {
                log.error("启动爬虫失败", e);
                task.fail("启动爬虫失败：" + e.getMessage());
            }
            return task.toMap();
        }
    }

    /**
     * 预检并挑选可用的 Python 解释器。
     *
     * <p>配置的 {@code talent.crawl.python} 只是一个候选：机器上常同时存在多个 Python
     * （系统装的、虚拟环境的、终端临时加到 PATH 的），配置值不一定就是装了 DrissionPage 的那个。
     * 这里按「配置值 → python → python3 → py -3 → 常见安装目录」依次探测，
     * 第一个能 import DrissionPage 和 pandas 的就拿来跑爬虫。</p>
     *
     * @return 可执行命令（单元素为解释器路径）；都不行时返回配置值，让后续报错指向配置
     */
    private List<String> pythonCommand()
    {
        List<String> cached = workingPython;
        if (cached != null)
        {
            return cached;
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (python != null && !python.trim().isEmpty())
        {
            candidates.add(python.trim());
        }
        candidates.add("python");
        candidates.add("python3");
        candidates.addAll(commonPythonPaths());

        for (String candidate : candidates)
        {
            if (probePython(Collections.singletonList(candidate)))
            {
                if (!candidate.equals(python))
                {
                    log.warn("配置的 Python（{}）缺少爬虫依赖，自动改用：{}", python, candidate);
                }
                workingPython = Collections.singletonList(candidate);
                return workingPython;
            }
        }

        // 最后试 py 启动器（Windows 上装多版本时常见）
        String viaLauncher = queryPyLauncher();
        if (viaLauncher != null && probePython(Collections.singletonList(viaLauncher)))
        {
            log.warn("配置的 Python（{}）缺少爬虫依赖，自动改用 py 启动器指向的：{}", python, viaLauncher);
            workingPython = Collections.singletonList(viaLauncher);
            return workingPython;
        }

        workingPython = Collections.singletonList(python);
        return workingPython;
    }

    /** 探测某个解释器是否能 import DrissionPage + pandas */
    private boolean probePython(List<String> command)
    {
        Process probe = null;
        try
        {
            List<String> cmd = new ArrayList<>(command);
            cmd.add("-c");
            cmd.add("import DrissionPage, pandas");
            ProcessBuilder builder = new ProcessBuilder(cmd);
            builder.redirectErrorStream(true);
            probe = builder.start();
            // 读完输出，避免管道缓冲区满导致子进程阻塞
            readAll(probe.getInputStream());
            return probe.waitFor(25, TimeUnit.SECONDS) && probe.exitValue() == 0;
        }
        catch (Exception e)
        {
            return false;
        }
        finally
        {
            if (probe != null && probe.isAlive())
            {
                probe.destroyForcibly();
            }
        }
    }

    /** 用 py 启动器问出它实际指向的解释器路径 */
    private String queryPyLauncher()
    {
        Process probe = null;
        try
        {
            ProcessBuilder builder = new ProcessBuilder("py", "-3", "-c", "import sys;print(sys.executable)");
            builder.redirectErrorStream(true);
            probe = builder.start();
            String output = readAll(probe.getInputStream()).trim();
            if (!probe.waitFor(20, TimeUnit.SECONDS) || probe.exitValue() != 0 || output.isEmpty())
            {
                return null;
            }
            String lastLine = output.contains("\n")
                    ? output.substring(output.lastIndexOf('\n') + 1).trim() : output;
            return new File(lastLine).isFile() ? lastLine : null;
        }
        catch (Exception e)
        {
            return null;
        }
        finally
        {
            if (probe != null && probe.isAlive())
            {
                probe.destroyForcibly();
            }
        }
    }

    /** 常见 Python 安装位置（按版本号从新到旧），避免 PATH 里没有 python 时找不到 */
    private List<String> commonPythonPaths()
    {
        List<String> paths = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null)
        {
            File[] dirs = new File(localAppData, "Programs" + File.separator + "Python").listFiles();
            if (dirs != null)
            {
                Arrays.sort(dirs, Comparator.comparing(File::getName).reversed());
                for (File dir : dirs)
                {
                    File exe = new File(dir, "python.exe");
                    if (exe.isFile())
                    {
                        paths.add(exe.getAbsolutePath());
                    }
                }
            }
        }
        for (String root : new String[] { "C:/Python312", "C:/Python311", "C:/Python310", "C:/Python39" })
        {
            File exe = new File(root, "python.exe");
            if (exe.isFile())
            {
                paths.add(exe.getAbsolutePath());
            }
        }
        return paths;
    }

    private static String readAll(InputStream in) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (sb.length() > 0)
                {
                    sb.append('\n');
                }
                sb.append(line);
            }
        }
        return sb.toString();
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

        Matcher csv = CSV_SAVED.matcher(line);
        if (csv.find())
        {
            task.savedCsv = new File(csv.group(1).trim());
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
            // 优先用爬虫自己打印的落盘路径：即使脚本的 DATA_DIR 被改回别的目录，也能把数据导进看板
            File latest = task.savedCsv;
            if (latest != null && latest.isFile())
            {
                log.info("使用爬虫输出的 CSV：{}", latest.getAbsolutePath());
            }
            else
            {
                latest = findLatestCsv(task);
            }
            if (latest == null)
            {
                File dir = task.outputDir != null ? task.outputDir
                        : TalentPathResolver.resolveDir(outputDir, OUTPUT_DIR_RELATIVE, true);
                task.fail("爬取结束，但没有找到本次生成的 CSV（已查找 "
                        + (dir == null ? outputDir : dir.getAbsolutePath())
                        + "，爬虫也没有打印落盘路径）。请检查爬虫是否真的抓到了数据");
                return;
            }

            task.message = "正在导入 " + latest.getName() + "...";
            Map<String, Object> result = importService.importCsv(new FileMultipartFile(latest), "");
            copyToCleanedDir(latest);
            task.status = "DONE";
            task.percent = 100;
            task.result = result;
            Object count = result.get("postingCount");
            task.message = "爬取并导入完成：新增 " + (count == null ? 0 : count) + " 条岗位，看板已刷新";
            task.finishedAt = new Date();
            log.info("爬取导入完成：{} 条，文件 {}", count, latest.getAbsolutePath());
        }
        catch (Exception e)
        {
            log.error("爬取结果导入失败", e);
            task.fail("爬取完成但导入失败：" + e.getMessage());
        }
    }

    /** 在输出目录里找本次任务生成的 CSV（按修改时间取最新，跳过清洗产物） */
    private File findLatestCsv(Task task)
    {
        File dir = task.outputDir != null ? task.outputDir
                : TalentPathResolver.resolveDir(outputDir, OUTPUT_DIR_RELATIVE, true);
        File latest = null;
        File[] files = dir == null ? null : dir.listFiles();
        if (files == null)
        {
            return null;
        }
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
        return latest;
    }

    /** 把本次爬取结果复制到「04-清洗后数据」，供技能热度曲线按修改时间生成新时间点 */
    private void copyToCleanedDir(File latest)
    {
        try
        {
            File dir = TalentPathResolver.resolveDir(cleanedDataDir, CLEANED_DIR_RELATIVE, true);
            if (dir == null)
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

        /** 本次任务实际使用的输出目录，导入时按它找 CSV */
        volatile File outputDir;

        /** 爬虫自己打印的落盘 CSV（优先用它，避免脚本 DATA_DIR 与配置不一致） */
        volatile File savedCsv;

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
