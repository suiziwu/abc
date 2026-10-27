package com.ruoyi.project.service.impl;

import java.util.List;
import com.ruoyi.common.utils.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.project.mapper.UserVideoWatchMapper;
import com.ruoyi.project.domain.UserVideoWatch;
import com.ruoyi.project.service.IUserVideoWatchService;

/**
 * 用户视频观看记录Service业务层处理
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
@Service
public class UserVideoWatchServiceImpl implements IUserVideoWatchService 
{
    @Autowired
    private UserVideoWatchMapper userVideoWatchMapper;

    /**
     * 查询用户视频观看记录
     * 
     * @param id 用户视频观看记录主键
     * @return 用户视频观看记录
     */
    @Override
    public UserVideoWatch selectUserVideoWatchById(Long id)
    {
        return userVideoWatchMapper.selectUserVideoWatchById(id);
    }

    /**
     * 查询用户视频观看记录列表
     * 
     * @param userVideoWatch 用户视频观看记录
     * @return 用户视频观看记录
     */
    @Override
    public List<UserVideoWatch> selectUserVideoWatchList(UserVideoWatch userVideoWatch)
    {
        return userVideoWatchMapper.selectUserVideoWatchList(userVideoWatch);
    }

    /**
     * 新增用户视频观看记录
     * 
     * @param userVideoWatch 用户视频观看记录
     * @return 结果
     */
    @Override
    public int insertUserVideoWatch(UserVideoWatch userVideoWatch)
    {
        userVideoWatch.setCreateTime(DateUtils.getNowDate());
        return userVideoWatchMapper.insertUserVideoWatch(userVideoWatch);
    }

    /**
     * 修改用户视频观看记录
     * 
     * @param userVideoWatch 用户视频观看记录
     * @return 结果
     */
    @Override
    public int updateUserVideoWatch(UserVideoWatch userVideoWatch)
    {
        userVideoWatch.setUpdateTime(DateUtils.getNowDate());
        return userVideoWatchMapper.updateUserVideoWatch(userVideoWatch);
    }

    /**
     * 批量删除用户视频观看记录
     * 
     * @param ids 需要删除的用户视频观看记录主键
     * @return 结果
     */
    @Override
    public int deleteUserVideoWatchByIds(Long[] ids)
    {
        return userVideoWatchMapper.deleteUserVideoWatchByIds(ids);
    }

    /**
     * 删除用户视频观看记录信息
     * 
     * @param id 用户视频观看记录主键
     * @return 结果
     */
    @Override
    public int deleteUserVideoWatchById(Long id)
    {
        return userVideoWatchMapper.deleteUserVideoWatchById(id);
    }
}
