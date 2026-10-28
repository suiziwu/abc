package com.ruoyi.project.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.project.domain.UserVideoWatch;

/**
 * 用户视频观看记录Mapper接口
 * 
 * @author ruoyi
 * @date 2026-07-01
 */
public interface UserVideoWatchMapper 
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
     * 删除用户视频观看记录
     * 
     * @param id 用户视频观看记录主键
     * @return 结果
     */
    public int deleteUserVideoWatchById(Long id);

    /**
     * 批量删除用户视频观看记录（限定归属用户）
     * 
     * @param ids 需要删除的数据主键集合
     * @param userId 归属用户，为 null 时不限制（仅内部调用）
     * @return 结果
     */
    public int deleteUserVideoWatchByIds(@Param("ids") Long[] ids, @Param("userId") Long userId);
}
