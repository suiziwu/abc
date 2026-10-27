import request from '@/utils/request'

// 查询用户视频观看记录列表
export function listWatch(query) {
  return request({
    url: '/project/watch/list',
    method: 'get',
    params: query
  })
}

// 查询用户视频观看记录详细
export function getWatch(id) {
  return request({
    url: '/project/watch/' + id,
    method: 'get'
  })
}

// 新增用户视频观看记录
export function addWatch(data) {
  return request({
    url: '/project/watch',
    method: 'post',
    data: data
  })
}

// 修改用户视频观看记录
export function updateWatch(data) {
  return request({
    url: '/project/watch',
    method: 'put',
    data: data
  })
}

// 删除用户视频观看记录
export function delWatch(id) {
  return request({
    url: '/project/watch/' + id,
    method: 'delete'
  })
}
