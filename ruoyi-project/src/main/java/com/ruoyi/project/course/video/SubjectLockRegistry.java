package com.ruoyi.project.course.video;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

/**
 * 按学科粒度的进程内互斥锁。
 *
 * <p>视频导入的章号、题号、知识点都是「先查后写」，同一学科并发导入会撞号。
 * 这里按 subjectId 分段加锁串行化；跨实例的最终防线是数据库唯一索引
 * （{@code uk_subject_chapter}、{@code uk_kb_doc_chunk}）。</p>
 *
 * <p>锁只包住「写库 + 重建知识库」这一段，不包 ASR/LLM 等耗时数分钟的远程调用。</p>
 *
 * @author ruoyi
 */
@Component
public class SubjectLockRegistry
{
    private final ConcurrentHashMap<Long, LockRef> locks = new ConcurrentHashMap<>();

    /**
     * 在学科锁内执行。
     *
     * @param subjectId 学科ID（为空时直接执行）
     * @param action 业务逻辑
     * @return 业务返回值
     */
    public <T> T withLock(Long subjectId, Supplier<T> action)
    {
        if (subjectId == null)
        {
            return action.get();
        }
        LockRef ref = locks.compute(subjectId, (key, existing) -> {
            LockRef target = existing == null ? new LockRef() : existing;
            target.users++;
            return target;
        });
        try
        {
            ref.lock.lock();
            return action.get();
        }
        finally
        {
            ref.lock.unlock();
            // 无人使用时移除，避免学科数量增长导致 Map 常驻
            locks.computeIfPresent(subjectId, (key, existing) -> {
                existing.users--;
                return existing.users <= 0 ? null : existing;
            });
        }
    }

    private static final class LockRef
    {
        private final ReentrantLock lock = new ReentrantLock();

        /** 仅由 ConcurrentHashMap.compute/computeIfPresent 串行修改 */
        private int users;
    }
}
