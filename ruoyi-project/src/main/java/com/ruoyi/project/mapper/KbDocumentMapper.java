package com.ruoyi.project.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Param;

import com.ruoyi.project.domain.KbDocument;

/**
 * 知识库文档 Mapper 接口
 *
 * @author ruoyi
 */
public interface KbDocumentMapper
{
    /**
     * 写入或更新文档（按 doc_key 唯一）。
     *
     * <p>使用 {@code id = LAST_INSERT_ID(id)} 让冲突时也能回填主键，
     * 便于入库后立即把切片挂到文档上。</p>
     *
     * @param document 文档
     * @return 影响行数
     */
    int upsertKbDocument(KbDocument document);

    /**
     * 按 doc_key 查询文档
     *
     * @param docKey 源文档标识
     * @return 文档
     */
    KbDocument selectKbDocumentByKey(@Param("docKey") String docKey);

    /**
     * 按主键查询文档
     *
     * @param id 文档主键
     * @return 文档
     */
    KbDocument selectKbDocumentById(@Param("id") Long id);

    /**
     * 查询文档列表
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @param sourceType 语料类型（可空）
     * @return 文档集合
     */
    List<KbDocument> selectKbDocumentList(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId,
            @Param("sourceType") String sourceType);

    /**
     * 按 doc_key 集合删除文档
     *
     * @param docKeys 源文档标识集合
     * @return 影响行数
     */
    int deleteKbDocumentByKeys(@Param("docKeys") List<String> docKeys);

    /**
     * 按条件删除文档
     *
     * @param subjectId 学科ID（可空）
     * @param chapterId 章节ID（可空）
     * @param sourceType 语料类型（可空）
     * @return 影响行数
     */
    int deleteKbDocumentByFilter(@Param("subjectId") Long subjectId,
            @Param("chapterId") Long chapterId,
            @Param("sourceType") String sourceType);

    /**
     * 统计学科下的文档数
     *
     * @param subjectId 学科ID（可空）
     * @return 文档数
     */
    int countKbDocument(@Param("subjectId") Long subjectId);
}
