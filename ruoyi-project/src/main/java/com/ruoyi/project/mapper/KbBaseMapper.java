package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.KbBase;

/**
 * 知识库Mapper接口（与学科一对一）。
 *
 * @author ruoyi
 */
public interface KbBaseMapper
{
    /**
     * 查询知识库列表（含分块数量）
     *
     * @return 知识库集合
     */
    public List<KbBase> selectKbBaseList();

    /**
     * 按学科查询知识库
     *
     * @param subjectId 学科ID
     * @return 知识库
     */
    public KbBase selectKbBaseBySubjectId(@Param("subjectId") Long subjectId);

    /**
     * 新增知识库
     *
     * @param kbBase 知识库
     * @return 结果
     */
    public int insertKbBase(KbBase kbBase);

    /**
     * 按学科同步知识库名称
     *
     * @param subjectId 学科ID
     * @param name 知识库名称
     * @param updateBy 更新者
     * @return 结果
     */
    public int updateKbBaseNameBySubjectId(@Param("subjectId") Long subjectId,
            @Param("name") String name, @Param("updateBy") String updateBy);

    /**
     * 按学科批量删除知识库
     *
     * @param subjectIds 学科ID集合
     * @return 结果
     */
    public int deleteKbBaseBySubjectIds(@Param("subjectIds") Long[] subjectIds);
}
