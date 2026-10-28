package com.ruoyi.web.controller.project;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.project.course.video.CourseVideoImportService;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.CourseTranscript;
import com.ruoyi.project.domain.CourseVideoImport;
import com.ruoyi.project.mapper.CourseChapterMapper;
import com.ruoyi.project.mapper.CourseLessonMapper;
import com.ruoyi.project.mapper.CourseTranscriptMapper;
import com.ruoyi.project.mapper.CourseVideoImportMapper;

/**
 * 视频课程自动导入：上传视频 → ASR → AI 结构化 → 生成课程学习数据。
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/project/course")
public class CourseVideoController extends BaseController
{
    @Autowired
    private CourseVideoImportService courseVideoImportService;

    @Autowired
    private CourseChapterMapper courseChapterMapper;

    @Autowired
    private CourseTranscriptMapper courseTranscriptMapper;

    @Autowired
    private CourseVideoImportMapper courseVideoImportMapper;

    @Autowired
    private CourseLessonMapper courseLessonMapper;

    /**
     * 上传视频并启动自动分析任务。
     *
     * <p>章节与课时两级：{@code chapterNo} 命中已有章时，本次视频作为该章的下一个课时；
     * 未命中则新建章。{@code chapterTitle} 一般传课程目录名（如「第一章 网络体系结构」），
     * {@code lessonCode}/{@code lessonTitle} 传课时编号与名称（如「1.1.1 概念和功能」）。</p>
     *
     * @param file 视频文件
     * @param subjectId 学科ID
     * @param chapterNo 章号（可空，自动生成）
     * @param chapterTitle 章节名称（可空，取课程目录名）
     * @param lessonCode 课时编号（可空，自动生成）
     * @param lessonTitle 课时名称（可空）
     * @param testId 题库ID（可空，默认每课时独立题库）
     */
    @PreAuthorize("@ss.hasPermi('project:point:edit')")
    @PostMapping("/video/upload")
    public AjaxResult uploadVideo(@RequestParam("file") MultipartFile file,
            @RequestParam("subjectId") Long subjectId,
            @RequestParam(value = "chapterNo", required = false) String chapterNo,
            @RequestParam(value = "chapterTitle", required = false) String chapterTitle,
            @RequestParam(value = "lessonCode", required = false) String lessonCode,
            @RequestParam(value = "lessonTitle", required = false) String lessonTitle,
            @RequestParam(value = "testId", required = false) Long testId)
    {
        try
        {
            Long taskId = courseVideoImportService.startImport(file, subjectId, chapterNo, chapterTitle,
                    lessonCode, lessonTitle, testId, getUsername());
            Map<String, Object> data = new LinkedHashMap<String, Object>();
            data.put("taskId", taskId);
            return success(data);
        }
        catch (IllegalArgumentException e)
        {
            return error(e.getMessage());
        }
        catch (Exception e)
        {
            logger.error("启动视频课程导入失败", e);
            return error("启动导入失败：" + e.getMessage());
        }
    }

    /**
     * 查询导入任务状态（阶段 / 进度 / 结果统计）。
     */
    @GetMapping("/video/status/{id}")
    public AjaxResult status(@PathVariable("id") Long id)
    {
        CourseVideoImport task = courseVideoImportService.getTask(id);
        if (task == null)
        {
            return error("任务不存在");
        }
        return success(task);
    }

    /**
     * 导入任务列表（最近 50 条）。
     */
    @GetMapping("/video/list")
    public AjaxResult list(@RequestParam(value = "subjectId", required = false) Long subjectId)
    {
        List<CourseVideoImport> list = courseVideoImportService.listTasks(subjectId);
        return success(list);
    }

    /**
     * 查询课程逐字稿（供课程学习页动态加载）。
     *
     * @param chapterId 章节ID（优先）
     * @param lessonId 课时ID（指定后只返回该课时逐字稿）
     * @param subjectId 学科ID
     * @param chapterNo 章号
     */
    @GetMapping("/transcript")
    public AjaxResult transcript(@RequestParam(value = "chapterId", required = false) Long chapterId,
            @RequestParam(value = "lessonId", required = false) Long lessonId,
            @RequestParam(value = "subjectId", required = false) Long subjectId,
            @RequestParam(value = "chapterNo", required = false) String chapterNo)
    {
        CourseChapter chapter = null;
        if (chapterId != null)
        {
            chapter = courseChapterMapper.selectCourseChapterById(chapterId);
        }
        else if (subjectId != null)
        {
            CourseChapter query = new CourseChapter();
            query.setSubjectId(subjectId);
            for (CourseChapter item : courseChapterMapper.selectCourseChapterList(query))
            {
                if (chapterNo == null || chapterNo.equals(item.getChapterNo()))
                {
                    chapter = item;
                    break;
                }
            }
        }
        if (chapter == null)
        {
            return error("未找到对应章节");
        }
        List<CourseTranscript> segments = lessonId != null
                ? courseTranscriptMapper.selectByLessonId(lessonId)
                : courseTranscriptMapper.selectByChapterId(chapter.getId());
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("chapter", chapter);
        data.put("segments", segments);
        // 该章节最近一次成功导入对应的题库，课程页据此加载随堂练习
        CourseVideoImport latest = courseVideoImportMapper.selectLatestSuccessByChapterId(chapter.getId());
        if (latest != null && latest.getTestId() != null)
        {
            data.put("testId", latest.getTestId());
        }
        if (lessonId != null)
        {
            data.put("lessonId", lessonId);
            CourseVideoImport lessonTask = courseVideoImportMapper.selectLatestSuccessByLessonId(lessonId);
            if (lessonTask != null && lessonTask.getTestId() != null)
            {
                data.put("testId", lessonTask.getTestId());
            }
        }
        return success(data);
    }

    /**
     * 查询章节下的课时列表（一章多课时）。
     */
    @GetMapping("/lessons/{chapterId}")
    public AjaxResult lessons(@PathVariable("chapterId") Long chapterId)
    {
        return success(courseLessonMapper.selectByChapterId(chapterId));
    }
}
