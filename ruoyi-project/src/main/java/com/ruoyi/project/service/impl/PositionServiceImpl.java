package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.PositionMapper;
import com.ruoyi.project.domain.Position;
import com.ruoyi.project.service.IPositionService;

/**
 * 岗位Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-10-27
 */
@Service
public class PositionServiceImpl implements IPositionService 
{
    @Autowired
    private PositionMapper positionMapper;

    /**
     * 查询岗位
     * 
     * @param id 岗位主键
     * @return 岗位
     */
    @Override
    public Position selectPositionById(Long id)
    {
        return positionMapper.selectPositionById(id);
    }

    /**
     * 查询岗位列表
     * 
     * @param position 岗位
     * @return 岗位
     */
    @Override
    public List<Position> selectPositionList(Position position)
    {
        return positionMapper.selectPositionList(position);
    }

    /**
     * 新增岗位
     * 
     * @param position 岗位
     * @return 结果
     */
    @Override
    public int insertPosition(Position position)
    {
        position.setCreateTime(DateUtils.getNowDate());
        return positionMapper.insertPosition(position);
    }

    /**
     * 修改岗位
     * 
     * @param position 岗位
     * @return 结果
     */
    @Override
    public int updatePosition(Position position)
    {
        position.setUpdateTime(DateUtils.getNowDate());
        return positionMapper.updatePosition(position);
    }

    /**
     * 批量删除岗位
     * 
     * @param ids 需要删除的岗位主键
     * @return 结果
     */
    @Override
    public int deletePositionByIds(Long[] ids)
    {
        return positionMapper.deletePositionByIds(ids);
    }

    /**
     * 删除岗位信息
     * 
     * @param id 岗位主键
     * @return 结果
     */
    @Override
    public int deletePositionById(Long id)
    {
        return positionMapper.deletePositionById(id);
    }
}
