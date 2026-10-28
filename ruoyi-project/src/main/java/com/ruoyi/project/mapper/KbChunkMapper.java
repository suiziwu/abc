package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.KbChunk;

/**
 * 知识库分块Mapper接口
 *
 * @author ruoyi
 */
public interface KbChunkMapper
{
    /**
     * 批量新增知识库分块（同 docId+chunkIndex 覆盖）
     *
     * @param list 分块集合
     * @return 结果
     */
    public int batchInsertKbChunk(@Param("list") List<KbChunk> list);

    /**
     * 查询分块列表（用于本地向量检索 / 管理查看）
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @param sourceTypes 语料类型集合（可空）
     * @param limit 限制条数（可空）
     * @return 分块集合
     */
    public List<KbChunk> selectKbChunkList(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId,
            @Param("sourceTypes") List<String> sourceTypes,
            @Param("limit") Integer limit);

    /**
     * 按 docId 集合查询分块（Milvus 命中后回填原文用）
     *
     * @param docIds 源文档标识集合
     * @return 分块集合
     */
    public List<KbChunk> selectKbChunkByDocIds(@Param("docIds") List<String> docIds);

    /**
     * 按文档主键集合查询分块
     *
     * @param docPks 文档主键集合
     * @return 分块集合
     */
    public List<KbChunk> selectKbChunkByDocPks(@Param("docPks") List<Long> docPks);

    /**
     * 查询现有分块的内容指纹（增量入库比对用）
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @return docId + chunkIndex + contentHash
     */
    public List<java.util.Map<String, Object>> selectKbChunkHashes(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId);

    /**
     * 关键词召回（MySQL ngram 全文索引）
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @param sourceTypes 语料类型集合（可空）
     * @param keyword 查询词
     * @param limit 返回条数
     * @return 分块集合（keywordScore 已回填）
     */
    public List<KbChunk> searchKbChunkByKeyword(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId,
            @Param("sourceTypes") List<String> sourceTypes,
            @Param("keyword") String keyword,
            @Param("limit") int limit);

    /**
     * 按条件删除分块
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @param sourceType 语料类型（可空）
     * @param docId 源文档标识（可空）
     * @return 结果
     */
    public int deleteKbChunkByFilter(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId,
            @Param("sourceType") String sourceType,
            @Param("docId") String docId);

    /**
     * 按 docId 集合删除分块
     *
     * @param docIds 源文档标识集合
     * @return 结果
     */
    public int deleteKbChunkByDocIds(@Param("docIds") List<String> docIds);

    /**
     * 查询匹配条件下的分块主键（本地检索第一步，避免一次载入全部向量）
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @param sourceTypes 语料类型集合（可空）
     * @return 主键集合
     */
    public List<Long> selectKbChunkIds(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId,
            @Param("sourceTypes") List<String> sourceTypes);

    /**
     * 按主键集合查询分块（本地检索第二步，分批载入）
     *
     * @param ids 主键集合
     * @return 分块集合
     */
    public List<KbChunk> selectKbChunkByIds(@Param("ids") List<Long> ids);

    /**
     * 查询匹配条件下的全部 docId（重建后计算陈旧文档用）
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @return docId 集合
     */
    public List<String> selectKbChunkDocIds(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId);

    /**
     * 统计分块数量
     *
     * @param subjectId 学科ID（可空）
     * @return 数量
     */
    public int countKbChunk(@Param("subjectId") Long subjectId);

    /**
     * 按学科统计各语料类型的数量
     *
     * @param subjectId 学科ID（可空）
     * @return 统计结果（sourceType + total）
     */
    public List<java.util.Map<String, Object>> countGroupBySourceType(@Param("subjectId") Long subjectId);

    /**
     * 按学科 + 章节统计分块数量（用于列出可切换的知识库）
     *
     * @return 统计结果（subjectId + chapterId + total）
     */
    public List<java.util.Map<String, Object>> countGroupBySubjectAndChapter();
}