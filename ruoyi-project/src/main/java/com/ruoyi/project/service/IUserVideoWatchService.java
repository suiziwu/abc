package com.ruoyi.project.service;

import java.util.List;
import com.ruoyi.project.domain.UserVideoWatch;

/**
 * 用户视频观看记录Service接口
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
public interface IUserVideoWatchService 
{
    /**
     * 查询用户视频观看记录
     * 
     * @param id 用户视频观看记录主键
     * @return 用户视频观看记录
     */
    public UserVideoWatch selectUserVideoWatchById(Long id);

    /**
     * 查询用户视频观看记录列表
     * 
     * @param userVideoWatch 用户视频观看记录
     * @return 用户视频观看记录集合
     */
    public List<UserVideoWatch> selectUserVideoWatchList(UserVideoWatch userVideoWatch);

    /**
     * 新增用户视频观看记录
     * 
     * @param userVideoWatch 用户视频观看记录
     * @return 结果
     */
    public int insertUserVideoWatch(UserVideoWatch userVideoWatch);

    /**
     * 修改用户视频观看记录
     * 
     * @param userVideoWatch 用户视频观看记录
     * @return 结果
     */
    public int updateUserVideoWatch(UserVideoWatch userVideoWatch);

    /**
     * 批量删除用户视频观看记录
     * 
     * @param ids 需要删除的用户视频观看记录主键集合
     * @return 结果
     */
    public int deleteUserVideoWatchByIds(Long[] ids);

    /**
     * 删除用户视频观看记录信息
     * 
     * @param id 用户视频观看记录主键
     * @return 结果
     */
    public int deleteUserVideoWatchById(Long id);
}
