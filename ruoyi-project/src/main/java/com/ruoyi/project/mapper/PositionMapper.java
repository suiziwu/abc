package com.ruoyi.project.mapper;

import java.util.List;
import com.ruoyi.project.domain.Position;

/**
 * 岗位Mapper接口
 * 
 * @author ruoyi
 * @date 2026-10-27
 */
public interface PositionMapper 
{
    /**
     * 查询岗位
     * 
     * @param id 岗位主键
     * @return 岗位
     */
    public Position selectPositionById(Long id);

    /**
     * 查询岗位列表
     * 
     * @param position 岗位
     * @return 岗位集合
     */
    public List<Position> selectPositionList(Position position);

    /**
     * 新增岗位
     * 
     * @param position 岗位
     * @return 结果
     */
    public int insertPosition(Position position);

    /**
     * 修改岗位
     * 
     * @param position 岗位
     * @return 结果
     */
    public int updatePosition(Position position);

    /**
     * 删除岗位
     * 
     * @param id 岗位主键
     * @return 结果
     */
    public int deletePositionById(Long id);

    /**
     * 批量删除岗位
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deletePositionByIds(Long[] ids);
}
