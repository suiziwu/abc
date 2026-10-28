package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.CourseTranscript;

/**
 * 课程逐字稿 Mapper。
 *
 * @author ruoyi
 */
public interface CourseTranscriptMapper
{
    /**
     * 按章节查询逐字稿分句
     */
    public List<CourseTranscript> selectByChapterId(@Param("chapterId") Long chapterId);

    /**
     * 按课时查询逐字稿分句
     */
    public List<CourseTranscript> selectByLessonId(@Param("lessonId") Long lessonId);

    /**
     * 删除章节逐字稿
     */
    public int deleteByChapterId(@Param("chapterId") Long chapterId);

    /**
     * 删除课时逐字稿
     */
    public int deleteByLessonId(@Param("lessonId") Long lessonId);

    /**
     * 批量新增逐字稿分句
     */
    public int batchInsert(@Param("list") List<CourseTranscript> list);

    /** 单条 SQL 的最大行数，避免长视频逐字稿超过 max_allowed_packet */
    int MAX_BATCH_ROWS = 500;

    /**
     * 分批写入逐字稿：长视频可能有两三千句，单条 INSERT 会超出 max_allowed_packet。
     *
     * @param list 逐字稿分句
     * @return 写入条数
     */
    default int batchInsertInBatches(List<CourseTranscript> list)
    {
        if (list == null || list.isEmpty())
        {
            return 0;
        }
        int total = 0;
        for (int start = 0; start < list.size(); start += MAX_BATCH_ROWS)
        {
            int end = Math.min(start + MAX_BATCH_ROWS, list.size());
            total += batchInsert(new java.util.ArrayList<>(list.subList(start, end)));
        }
        return total;
    }
}
