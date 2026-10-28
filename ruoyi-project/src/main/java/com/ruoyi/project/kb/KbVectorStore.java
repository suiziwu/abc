package com.ruoyi.project.kb;

import java.util.List;

import com.ruoyi.project.domain.KbChunk;

/**
 * 向量库抽象：屏蔽 local（MySQL 存储 + Java 余弦）与 milvus（Milvus RESTful）差异。
 *
 * <p>新增向量库只需实现本接口并在 {@code KbVectorStoreFactory} 注册即可，业务代码零改动。</p>
 *
 * @author ruoyi
 */
public interface KbVectorStore
{
    /**
     * 实现标识：local / milvus
     */
    String provider();

    /**
     * 当前实现是否可用（配置齐全且服务可达）。
     */
    boolean available();

    /**
     * 不可用原因（用于前端展示/日志）；可用时返回 null。
     */
    String unavailableReason();

    /**
     * 写入/覆盖分块（含向量）。
     *
     * @param chunks 分块集合（embedding 已填好）
     * @return 写入条数
     */
    int upsert(List<KbChunk> chunks);

    /**
     * 语义检索。
     *
     * @param queryVector 查询向量（已归一化）
     * @param subjectId 学科过滤（可空）
     * @param chapterId 章节过滤（可空）
     * @param sourceTypes 语料类型过滤（可空）
     * @param topK 返回条数
     * @return 命中分块（按相关性降序，score 已回填）
     */
    List<KbChunk> search(float[] queryVector, Long subjectId, Long chapterId,
            List<String> sourceTypes, int topK);

    /**
     * 清空分块。
     *
     * @param subjectId 学科过滤（可空）
     * @param chapterId 章节过滤（可空）
     * @param sourceType 语料类型过滤（可空）
     * @return 删除条数
     */
    int clear(Long subjectId, Long chapterId, String sourceType);

    /**
     * 按 docId 集合删除分块（含向量）。
     *
     * @param docIds 源文档标识集合
     * @return 删除条数
     */
    int deleteByDocIds(List<String> docIds);

    /**
     * 统计分块数量。
     */
    int count(Long subjectId);
}