import request from '@/utils/request'

// 查询用户学习记录列表
export function listRecord(query) {
  return request({
    url: '/project/record/list',
    method: 'get',
    params: query
  })
}

// 查询用户学习记录详细
export function getRecord(id) {
  return request({
    url: '/project/record/' + id,
    method: 'get'
  })
}

// 新增用户学习记录
export function addRecord(data) {
  return request({
    url: '/project/record',
    method: 'post',
    data: data
  })
}

// 修改用户学习记录
export function updateRecord(data) {
  return request({
    url: '/project/record',
    method: 'put',
    data: data
  })
}

// 删除用户学习记录
export function delRecord(id) {
  return request({
    url: '/project/record/' + id,
    method: 'delete'
  })
}
