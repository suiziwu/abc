package com.ruoyi.project.talent.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 人才培养模块的本地路径解析。
 *
 * <p>爬虫、清洗数据等目录都是本机绝对路径，写死在配置里一旦换机器或移动工程就会失效
 * （「爬取数据」按钮因此报“找不到爬虫脚本”）。这里统一做两件事：</p>
 *
 * <ol>
 *   <li>配置的相对路径按「工程根目录」解析，而不是按进程当前目录；</li>
 *   <li>配置的绝对路径失效时，按候选相对路径在工程根目录、其上级目录里自动探测。</li>
 * </ol>
 */
public final class TalentPathResolver
{
    private static final Logger log = LoggerFactory.getLogger(TalentPathResolver.class);

    private TalentPathResolver()
    {
    }

    /** 工程根目录：优先「含 fiveEi 目录」的最近祖先，其次「含 pom.xml」的最近祖先，都找不到就用当前目录 */
    public static File projectRoot()
    {
        File start = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
        File hit = walkUp(start, "fiveEi", true);
        if (hit == null)
        {
            hit = walkUp(start, "pom.xml", false);
        }
        return hit == null ? start : hit;
    }

    /** 从 start 向上最多 5 级，找第一个满足条件（目录/文件）的祖先 */
    private static File walkUp(File start, String marker, boolean wantDir)
    {
        File dir = start;
        for (int i = 0; i < 5 && dir != null; i++)
        {
            File candidate = new File(dir, marker);
            if (wantDir ? candidate.isDirectory() : candidate.isFile())
            {
                return dir;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    /**
     * 解析一个文件：配置路径存在就直接用；否则在工程根目录及上级目录里按候选相对路径探测。
     *
     * @param configured 配置里的路径（可为绝对路径、相对路径或空）
     * @param candidates 候选相对路径，例如 {@code fiveEi/01-爬虫脚本/BOSS直聘爬虫.py}
     * @return 命中的文件；都找不到返回 {@code null}
     */
    public static File resolveFile(String configured, String... candidates)
    {
        File direct = asFile(configured);
        if (direct != null && direct.isFile())
        {
            return direct;
        }
        for (File base : searchBases())
        {
            for (String candidate : candidates)
            {
                File file = new File(base, candidate);
                if (file.isFile())
                {
                    if (configured != null && !configured.trim().isEmpty())
                    {
                        log.warn("配置路径不存在（{}），自动改用：{}", configured, file.getAbsolutePath());
                    }
                    return file;
                }
            }
        }
        return null;
    }

    /**
     * 解析一个目录：配置路径可用就直接用（不存在则尝试创建）；否则用工程根目录下的默认相对目录。
     *
     * @param configured      配置里的路径
     * @param defaultRelative 默认相对目录，例如 {@code fiveEi/新爬取}
     * @param create          true=目录不存在时创建
     * @return 可用目录；无法创建返回 {@code null}
     */
    public static File resolveDir(String configured, String defaultRelative, boolean create)
    {
        File direct = asFile(configured);
        if (direct != null && ensureDir(direct, create))
        {
            return direct;
        }
        for (File base : searchBases())
        {
            File dir = new File(base, defaultRelative);
            if (ensureDir(dir, create))
            {
                if (configured != null && !configured.trim().isEmpty())
                {
                    log.warn("配置目录不可用（{}），自动改用：{}", configured, dir.getAbsolutePath());
                }
                return dir;
            }
        }
        return null;
    }

    private static boolean ensureDir(File dir, boolean create)
    {
        if (dir.isDirectory())
        {
            return true;
        }
        return create && dir.mkdirs();
    }

    /** 依次尝试：配置路径（相对路径按工程根解析）→ 工程根 → 工程根的上级 */
    private static List<File> searchBases()
    {
        List<File> bases = new ArrayList<>();
        File root = projectRoot();
        bases.add(root);
        File parent = root.getParentFile();
        if (parent != null)
        {
            bases.add(parent);
        }
        return bases;
    }

    private static File asFile(String path)
    {
        if (path == null || path.trim().isEmpty())
        {
            return null;
        }
        File file = new File(path.trim());
        if (!file.isAbsolute())
        {
            file = new File(projectRoot(), path.trim());
        }
        return file;
    }
}
