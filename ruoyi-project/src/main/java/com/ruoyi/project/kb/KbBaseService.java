package com.ruoyi.project.kb;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.project.domain.KbBase;
import com.ruoyi.project.mapper.KbBaseMapper;
import com.ruoyi.project.mapper.KbChunkMapper;

/**
 * 知识库基础服务：知识库与学科一对一。
 *
 * <p>学科新增时自动创建知识库，学科改名时同步知识库名称，
 * 学科删除时一并清理知识库与其分块数据。</p>
 *
 * @author ruoyi
 */
@Service
public class KbBaseService
{
    private static final Logger log = LoggerFactory.getLogger(KbBaseService.class);

    private final KbBaseMapper kbBaseMapper;

    private final KbChunkMapper kbChunkMapper;

    private final KbVectorStoreFactory kbVectorStoreFactory;

    public KbBaseService(KbBaseMapper kbBaseMapper, KbChunkMapper kbChunkMapper,
            KbVectorStoreFactory kbVectorStoreFactory)
    {
        this.kbBaseMapper = kbBaseMapper;
        this.kbChunkMapper = kbChunkMapper;
        this.kbVectorStoreFactory = kbVectorStoreFactory;
    }

    /**
     * 确保学科存在对应的知识库（不存在则创建，名称不一致则同步）。
     *
     * @param subjectId 学科ID
     * @param subjectName 学科名称
     * @param operName 操作人
     * @return 知识库
     */
    public KbBase ensureForSubject(Long subjectId, String subjectName, String operName)
    {
        if (subjectId == null || StringUtils.isEmpty(subjectName))
        {
            return null;
        }
        String name = subjectName.trim();
        KbBase existing = kbBaseMapper.selectKbBaseBySubjectId(subjectId);
        if (existing == null)
        {
            KbBase kbBase = new KbBase();
            kbBase.setSubjectId(subjectId);
            kbBase.setName(name);
            kbBase.setStatus("0");
            kbBase.setDelFlag("0");
            kbBase.setCreateBy(StringUtils.isEmpty(operName) ? "system" : operName);
            kbBase.setCreateTime(DateUtils.getNowDate());
            try
            {
                kbBaseMapper.insertKbBase(kbBase);
            }
            catch (DuplicateKeyException e)
            {
                // 并发创建撞唯一键：回查已存在的那条
                KbBase created = kbBaseMapper.selectKbBaseBySubjectId(subjectId);
                if (created != null)
                {
                    return created;
                }
                throw e;
            }
            log.info("已为学科 {} 创建知识库：{}", subjectId, name);
            return kbBase;
        }
        if (!name.equals(existing.getName()))
        {
            kbBaseMapper.updateKbBaseNameBySubjectId(subjectId, name,
                    StringUtils.isEmpty(operName) ? "system" : operName);
            existing.setName(name);
        }
        return existing;
    }

    /**
     * 学科删除时清理知识库、分块与向量数据。
     *
     * @param subjectIds 学科ID集合
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeBySubjectIds(Long[] subjectIds)
    {
        if (subjectIds == null || subjectIds.length == 0)
        {
            return;
        }
        for (Long subjectId : subjectIds)
        {
            if (subjectId == null)
            {
                continue;
            }
            // 先清向量，否则 Milvus 里会残留指向已删原文的空向量
            try
            {
                kbVectorStoreFactory.active().clear(subjectId, null, null);
            }
            catch (Exception e)
            {
                // 向量库不可用时不阻塞学科删除；检索侧会丢弃回填不到原文的命中
                log.warn("清理学科 {} 的向量失败: {}", subjectId, e.getMessage());
            }
        }
        kbBaseMapper.deleteKbBaseBySubjectIds(subjectIds);
        for (Long subjectId : subjectIds)
        {
            if (subjectId != null)
            {
                kbChunkMapper.deleteKbChunkByFilter(subjectId, null, null, null);
            }
        }
    }

    /**
     * 查询知识库列表（含分块数量）。
     *
     * @return 知识库集合
     */
    public List<KbBase> list()
    {
        return kbBaseMapper.selectKbBaseList();
    }

    /**
     * 按学科查询知识库（不存在返回 null）。
     *
     * @param subjectId 学科ID
     * @return 知识库
     */
    public KbBase getBySubjectId(Long subjectId)
    {
        if (subjectId == null)
        {
            return null;
        }
        return kbBaseMapper.selectKbBaseBySubjectId(subjectId);
    }
}
