import request from '@/utils/request'

// 查询用户知识点测评记录列表
export function listAssessment(query) {
  return request({
    url: '/project/assessment/list',
    method: 'get',
    params: query
  })
}

// 查询用户知识点测评记录详细
export function getAssessment(id) {
  return request({
    url: '/project/assessment/' + id,
    method: 'get'
  })
}

// 新增用户知识点测评记录
export function addAssessment(data) {
  return request({
    url: '/project/assessment',
    method: 'post',
    data: data
  })
}

// 修改用户知识点测评记录
export function updateAssessment(data) {
  return request({
    url: '/project/assessment',
    method: 'put',
    data: data
  })
}

// 删除用户知识点测评记录
export function delAssessment(id) {
  return request({
    url: '/project/assessment/' + id,
    method: 'delete'
  })
}
