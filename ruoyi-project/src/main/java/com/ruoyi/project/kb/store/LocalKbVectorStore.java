package com.ruoyi.project.kb.store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.project.domain.KbChunk;
import com.ruoyi.project.kb.KbChunkTxService;
import com.ruoyi.project.kb.KbVectorStore;
import com.ruoyi.project.mapper.KbChunkMapper;

/**
 * 本地向量库实现：向量存 MySQL（kb_chunk.embedding），由 Java 计算余弦相似度。
 *
 * <p>面向 Windows 开发环境（Docker/Milvus 不可用时）的开箱即用方案；
 * 语料规模在数千 chunk 级别时，单次检索耗时为毫秒级。</p>
 *
 * @author ruoyi
 */
@Component
public class LocalKbVectorStore implements KbVectorStore
{
    private final KbChunkMapper kbChunkMapper;

    private final KbChunkTxService kbChunkTxService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 每次载入的分块数：控制单批 embedding 占用的堆内存 */
    private static final int SCAN_BATCH = 500;

    /** topK 缺省值 */
    private static final int DEFAULT_TOP_K = 10;

    public LocalKbVectorStore(KbChunkMapper kbChunkMapper, KbChunkTxService kbChunkTxService)
    {
        this.kbChunkMapper = kbChunkMapper;
        this.kbChunkTxService = kbChunkTxService;
    }

    @Override
    public String provider()
    {
        return "local";
    }

    @Override
    public boolean available()
    {
        return true;
    }

    @Override
    public String unavailableReason()
    {
        return null;
    }

    @Override
    public int upsert(List<KbChunk> chunks)
    {
        return kbChunkTxService.insertChunks(chunks);
    }

    @Override
    public List<KbChunk> search(float[] queryVector, Long subjectId, Long chapterId,
            List<String> sourceTypes, int topK)
    {
        int limit = topK > 0 ? topK : DEFAULT_TOP_K;
        // 第一步只取主键，避免一次性把整个学科的 embedding（每行约 10KB）载入堆内存
        List<Long> ids = kbChunkMapper.selectKbChunkIds(subjectId, chapterId, sourceTypes);
        if (ids.isEmpty())
        {
            return new ArrayList<>();
        }
        // 小顶堆维护 topK：堆顶是当前最差的候选，新候选优于堆顶才替换
        java.util.PriorityQueue<KbChunk> top = new java.util.PriorityQueue<>(
                Comparator.comparingDouble((KbChunk c) -> c.getScore() == null ? -1D : c.getScore()));
        for (int start = 0; start < ids.size(); start += SCAN_BATCH)
        {
            int end = Math.min(start + SCAN_BATCH, ids.size());
            for (KbChunk chunk : kbChunkMapper.selectKbChunkByIds(new ArrayList<>(ids.subList(start, end))))
            {
                if (chunk.getEmbedding() == null || chunk.getEmbedding().isEmpty())
                {
                    continue;
                }
                float[] vector = parse(chunk.getEmbedding());
                if (vector.length == 0 || vector.length != queryVector.length)
                {
                    continue;
                }
                // 向量已归一化，点积即余弦；无需回传向量体，减小响应体积
                chunk.setScore(dot(queryVector, vector));
                chunk.setEmbedding(null);
                if (top.size() < limit)
                {
                    top.offer(chunk);
                }
                else if (top.peek() != null && chunk.getScore() > top.peek().getScore())
                {
                    top.poll();
                    top.offer(chunk);
                }
            }
        }
        List<KbChunk> result = new ArrayList<>(top);
        result.sort(Comparator.comparingDouble((KbChunk c) -> c.getScore() == null ? -1D : c.getScore()).reversed());
        return result;
    }

    @Override
    public int clear(Long subjectId, Long chapterId, String sourceType)
    {
        return kbChunkMapper.deleteKbChunkByFilter(subjectId, chapterId, sourceType, null);
    }

    @Override
    public int deleteByDocIds(List<String> docIds)
    {
        return kbChunkTxService.deleteByDocIds(docIds);
    }

    @Override
    public int count(Long subjectId)
    {
        return kbChunkMapper.countKbChunk(subjectId);
    }

    private float[] parse(String json)
    {
        try
        {
            double[] values = objectMapper.readValue(json, double[].class);
            float[] vector = new float[values.length];
            for (int i = 0; i < values.length; i++)
            {
                vector[i] = (float) values[i];
            }
            return vector;
        }
        catch (Exception e)
        {
            return new float[0];
        }
    }

    /** 点积（输入均为 L2 归一化向量时等价于余弦相似度） */
    private double dot(float[] a, float[] b)
    {
        double sum = 0D;
        for (int i = 0; i < a.length; i++)
        {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }
}