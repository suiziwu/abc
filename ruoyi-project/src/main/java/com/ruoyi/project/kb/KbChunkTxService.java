package com.ruoyi.project.kb;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.project.domain.KbChunk;
import com.ruoyi.project.mapper.KbChunkMapper;

/**
 * 知识库分块的写库事务边界。
 *
 * <p>独立成 Bean 的原因：向量库实现（local/milvus）需要「一次写入要么全成、要么全不成」，
 * 而同类内部调用 {@code @Transactional} 方法不会经过代理，事务不生效。</p>
 *
 * @author ruoyi
 */
@Service
public class KbChunkTxService
{
    /** 单条 SQL 的最大行数，避免超过 max_allowed_packet */
    private static final int BATCH_SIZE = 50;

    private final KbChunkMapper kbChunkMapper;

    public KbChunkTxService(KbChunkMapper kbChunkMapper)
    {
        this.kbChunkMapper = kbChunkMapper;
    }

    /**
     * 批量写入分块（同 docId + chunkIndex 覆盖）。
     *
     * @param chunks 分块集合
     * @return 写入条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int insertChunks(List<KbChunk> chunks)
    {
        if (chunks == null || chunks.isEmpty())
        {
            return 0;
        }
        int total = 0;
        for (int start = 0; start < chunks.size(); start += BATCH_SIZE)
        {
            int end = Math.min(start + BATCH_SIZE, chunks.size());
            total += kbChunkMapper.batchInsertKbChunk(new ArrayList<>(chunks.subList(start, end)));
        }
        return total;
    }

    /**
     * 按 docId 集合删除分块（事务内）。
     *
     * @param docIds 源文档标识集合
     * @return 删除条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByDocIds(List<String> docIds)
    {
        if (docIds == null || docIds.isEmpty())
        {
            return 0;
        }
        return kbChunkMapper.deleteKbChunkByDocIds(docIds);
    }
}
