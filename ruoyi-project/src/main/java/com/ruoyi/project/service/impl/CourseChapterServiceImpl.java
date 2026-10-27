package com.ruoyi.project.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.project.domain.CourseChapter;
import com.ruoyi.project.domain.ChapterPoint;
import com.ruoyi.project.mapper.CourseChapterMapper;
import com.ruoyi.project.service.ICourseChapterService;

/**
 * 课程章节Service业务层处理
 *
 * @author ruoyi
 */
@Service
public class CourseChapterServiceImpl implements ICourseChapterService
{
    @Autowired
    private CourseChapterMapper courseChapterMapper;

    /**
     * 查询课程章节列表
     *
     * @param courseChapter 课程章节
     * @return 课程章节
     */
    @Override
    public List<CourseChapter> selectCourseChapterList(CourseChapter courseChapter)
    {
        return courseChapterMapper.selectCourseChapterList(courseChapter);
    }

    /**
     * 查询课程章节
     *
     * @param id 课程章节主键
     * @return 课程章节
     */
    @Override
    public CourseChapter selectCourseChapterById(Long id)
    {
        return courseChapterMapper.selectCourseChapterById(id);
    }

    /**
     * 校验同一学科下章号是否唯一
     *
     * @param courseChapter 课程章节
     * @return 结果
     */
    @Override
    public boolean checkChapterNoUnique(CourseChapter courseChapter)
    {
        if (courseChapter.getSubjectId() == null || courseChapter.getChapterNo() == null
                || courseChapter.getChapterNo().isEmpty())
        {
            return true;
        }
        CourseChapter exist = courseChapterMapper.checkChapterNoUnique(courseChapter.getSubjectId(), courseChapter.getChapterNo());
        return exist == null || exist.getId().equals(courseChapter.getId());
    }

    /**
     * 新增课程章节（可选同时保存章节-知识点关联）
     *
     * @param courseChapter 课程章节
     * @return 结果
     */
    @Override
    @Transactional
    public int insertCourseChapter(CourseChapter courseChapter)
    {
        courseChapter.setCreateTime(DateUtils.getNowDate());
        if (courseChapter.getStatus() == null)
        {
            courseChapter.setStatus("0");
        }
        int rows = courseChapterMapper.insertCourseChapter(courseChapter);
        saveChapterPoints(courseChapter);
        return rows;
    }

    /**
     * 修改课程章节（可选同时重置章节-知识点关联）
     *
     * @param courseChapter 课程章节
     * @return 结果
     */
    @Override
    @Transactional
    public int updateCourseChapter(CourseChapter courseChapter)
    {
        courseChapter.setUpdateTime(DateUtils.getNowDate());
        int rows = courseChapterMapper.updateCourseChapter(courseChapter);
        if (courseChapter.getPointIds() != null && courseChapter.getId() != null)
        {
            // 关联的重置（含 section 保留）统一由 saveChapterPoints 处理：
            // 必须先读取旧关联再删除，否则会丢失已回填的“小节”信息。
            saveChapterPoints(courseChapter);
        }
        return rows;
    }

    /**
     * 批量删除课程章节
     *
     * @param ids 课程章节主键集合
     * @return 结果
     */
    @Override
    @Transactional
    public int deleteCourseChapterByIds(Long[] ids)
    {
        int rows = 0;
        for (Long id : ids)
        {
            rows += courseChapterMapper.deleteCourseChapterById(id);
        }
        return rows;
    }

    /**
     * 查询章节下的知识点列表
     *
     * @param chapterId 章节主键
     * @return 章节知识点集合
     */
    @Override
    public List<ChapterPoint> selectChapterPointList(Long chapterId)
    {
        return courseChapterMapper.selectChapterPointList(chapterId);
    }

    /**
     * 根据知识点ID集合批量查询其所属章节
     *
     * @param pointIds 知识点ID集合
     * @return 章节知识点集合
     */
    @Override
    public List<ChapterPoint> selectChapterPointByPointIds(List<Long> pointIds)
    {
        if (pointIds == null || pointIds.isEmpty())
        {
            return new ArrayList<>();
        }
        return courseChapterMapper.selectChapterPointByPointIds(pointIds);
    }

    /**
     * 保存章节-知识点关联（pointIds 为 null 时不做任何处理）
     *
     * 注意：仅当调用方显式传入 pointIds 时才重置关联，避免“只改章节名称”也把关联清空。
     */
    private void saveChapterPoints(CourseChapter courseChapter)
    {
        List<Long> pointIds = courseChapter.getPointIds();
        if (pointIds == null || courseChapter.getId() == null)
        {
            return;
        }
        if (pointIds.isEmpty())
        {
            return;
        }
        // 保留已有 section，仅新增/移除关联，避免保存时丢失小节信息
        Map<Long, String> sectionMap = new HashMap<>();
        List<ChapterPoint> exists = courseChapterMapper.selectChapterPointList(courseChapter.getId());
        for (ChapterPoint cp : exists)
        {
            sectionMap.put(cp.getPointId(), cp.getSection());
        }

        List<ChapterPoint> toInsert = new ArrayList<>();
        int index = 0;
        for (Long pointId : pointIds)
        {
            index++;
            ChapterPoint cp = new ChapterPoint();
            cp.setChapterId(courseChapter.getId());
            cp.setPointId(pointId);
            cp.setSection(sectionMap.get(pointId));
            cp.setSortOrder((long) index * 10);
            toInsert.add(cp);
        }

        courseChapterMapper.deleteChapterPointByChapterId(courseChapter.getId());
        courseChapterMapper.batchChapterPoint(toInsert);
    }
}