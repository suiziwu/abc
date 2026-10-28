package com.ruoyi.project.service;

import java.util.List;
import com.ruoyi.project.domain.UserLearnRecord;

/**
 * 用户学习记录Service接口
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
public interface IUserLearnRecordService 
{
    /**
     * 查询用户学习记录
     * 
     * @param id 用户学习记录主键
     * @return 用户学习记录
     */
    public UserLearnRecord selectUserLearnRecordById(Long id);

    /**
     * 查询用户学习记录列表
     * 
     * @param userLearnRecord 用户学习记录
     * @return 用户学习记录集合
     */
    public List<UserLearnRecord> selectUserLearnRecordList(UserLearnRecord userLearnRecord);

    /**
     * 新增用户学习记录
     * 
     * @param userLearnRecord 用户学习记录
     * @return 结果
     */
    public int insertUserLearnRecord(UserLearnRecord userLearnRecord);

    /**
     * 修改用户学习记录
     * 
     * @param userLearnRecord 用户学习记录
     * @return 结果
     */
    public int updateUserLearnRecord(UserLearnRecord userLearnRecord);

    /**
     * 批量删除用户学习记录
     * 
     * @param ids 需要删除的用户学习记录主键集合
     * @param userId 归属用户，为 null 时不限制（仅内部调用）
     * @return 结果
     */
    public int deleteUserLearnRecordByIds(Long[] ids, Long userId);

    /**
     * 删除用户学习记录信息
     * 
     * @param id 用户学习记录主键
     * @return 结果
     */
    public int deleteUserLearnRecordById(Long id);
}
