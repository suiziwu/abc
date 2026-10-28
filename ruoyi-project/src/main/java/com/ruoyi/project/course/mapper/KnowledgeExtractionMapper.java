package com.ruoyi.project.course.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.ruoyi.project.course.domain.KnowledgeExtraction;

/**
 * 视频知识结构化梳理记录 Mapper。
 *
 * @author ruoyi
 */
@Mapper
public interface KnowledgeExtractionMapper
{
    @Insert("insert into knowledge_extraction (subject_id, chapter_no, source_type, model, transcript_chars, "
            + "raw_response, analysis_json, node_count, relation_count, status, error_msg, create_by, create_time) "
            + "values (#{subjectId}, #{chapterNo}, #{sourceType}, #{model}, #{transcriptChars}, "
            + "#{rawResponse}, #{analysisJson}, #{nodeCount}, #{relationCount}, #{status}, #{errorMsg}, "
            + "#{createBy}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertExtraction(KnowledgeExtraction extraction);

    @Select("select id, subject_id as subjectId, chapter_no as chapterNo, source_type as sourceType, model, "
            + "transcript_chars as transcriptChars, raw_response as rawResponse, analysis_json as analysisJson, "
            + "mapping_json as mappingJson, node_count as nodeCount, relation_count as relationCount, "
            + "created_points as createdPoints, matched_points as matchedPoints, created_relations as createdRelations, "
            + "status, error_msg as errorMsg, create_by as createBy, create_time as createTime, "
            + "sync_by as syncBy, sync_time as syncTime from knowledge_extraction where id = #{id}")
    KnowledgeExtraction selectExtractionById(@Param("id") Long id);

    @Select("select id, subject_id as subjectId, chapter_no as chapterNo, source_type as sourceType, model, "
            + "transcript_chars as transcriptChars, node_count as nodeCount, relation_count as relationCount, "
            + "created_points as createdPoints, matched_points as matchedPoints, created_relations as createdRelations, "
            + "status, error_msg as errorMsg, create_by as createBy, create_time as createTime, "
            + "sync_by as syncBy, sync_time as syncTime from knowledge_extraction "
            + "where subject_id = #{subjectId} order by id desc limit 20")
    List<KnowledgeExtraction> selectRecentBySubject(@Param("subjectId") Long subjectId);

    @Update("update knowledge_extraction set status = #{status}, mapping_json = #{mappingJson}, "
            + "created_points = #{createdPoints}, matched_points = #{matchedPoints}, "
            + "created_relations = #{createdRelations}, sync_by = #{syncBy}, sync_time = #{syncTime} "
            + "where id = #{id}")
    int updateSyncResult(KnowledgeExtraction extraction);
}
