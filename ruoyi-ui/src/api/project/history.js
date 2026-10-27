import request from '@/utils/request'

// 查询AI对话历史记录列表
export function listHistory(query) {
  return request({
    url: '/project/history/list',
    method: 'get',
    params: query
  })
}

// 查询AI对话历史记录详细
export function getHistory(id) {
  return request({
    url: '/project/history/' + id,
    method: 'get'
  })
}

// 新增AI对话历史记录
export function addHistory(data) {
  return request({
    url: '/project/history',
    method: 'post',
    data: data
  })
}

// 修改AI对话历史记录
export function updateHistory(data) {
  return request({
    url: '/project/history',
    method: 'put',
    data: data
  })
}

// 删除AI对话历史记录
export function delHistory(id) {
  return request({
    url: '/project/history/' + id,
    method: 'delete'
  })
}
