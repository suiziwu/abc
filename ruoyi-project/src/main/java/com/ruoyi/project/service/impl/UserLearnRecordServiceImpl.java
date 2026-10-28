package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.UserLearnRecordMapper;
import com.ruoyi.project.domain.UserLearnRecord;
import com.ruoyi.project.service.IUserLearnRecordService;

/**
 * 用户学习记录Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
@Service
public class UserLearnRecordServiceImpl implements IUserLearnRecordService 
{
    @Autowired
    private UserLearnRecordMapper userLearnRecordMapper;

    /**
     * 查询用户学习记录
     * 
     * @param id 用户学习记录主键
     * @return 用户学习记录
     */
    @Override
    public UserLearnRecord selectUserLearnRecordById(Long id)
    {
        return userLearnRecordMapper.selectUserLearnRecordById(id);
    }

    /**
     * 查询用户学习记录列表
     * 
     * @param userLearnRecord 用户学习记录
     * @return 用户学习记录
     */
    @Override
    public List<UserLearnRecord> selectUserLearnRecordList(UserLearnRecord userLearnRecord)
    {
        return userLearnRecordMapper.selectUserLearnRecordList(userLearnRecord);
    }

    /**
     * 新增用户学习记录
     * 
     * @param userLearnRecord 用户学习记录
     * @return 结果
     */
    @Override
    public int insertUserLearnRecord(UserLearnRecord userLearnRecord)
    {
        userLearnRecord.setCreateTime(DateUtils.getNowDate());
        return userLearnRecordMapper.insertUserLearnRecord(userLearnRecord);
    }

    /**
     * 修改用户学习记录
     * 
     * @param userLearnRecord 用户学习记录
     * @return 结果
     */
    @Override
    public int updateUserLearnRecord(UserLearnRecord userLearnRecord)
    {
        userLearnRecord.setUpdateTime(DateUtils.getNowDate());
        return userLearnRecordMapper.updateUserLearnRecord(userLearnRecord);
    }

    /**
     * 批量删除用户学习记录
     * 
     * @param ids 需要删除的用户学习记录主键
     * @param userId 归属用户，为 null 时不限制
     * @return 结果
     */
    @Override
    public int deleteUserLearnRecordByIds(Long[] ids, Long userId)
    {
        return userLearnRecordMapper.deleteUserLearnRecordByIds(ids, userId);
    }

    /**
     * 删除用户学习记录信息
     * 
     * @param id 用户学习记录主键
     * @return 结果
     */
    @Override
    public int deleteUserLearnRecordById(Long id)
    {
        return userLearnRecordMapper.deleteUserLearnRecordById(id);
    }
}
