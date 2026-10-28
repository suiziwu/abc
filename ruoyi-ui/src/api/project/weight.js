import request from '@/utils/request'

// 查询岗位能力权重列表
export function listWeight(query) {
  return request({
    url: '/project/weight/list',
    method: 'get',
    params: query
  })
}

// 查询岗位能力权重详细
export function getWeight(id) {
  return request({
    url: '/project/weight/' + id,
    method: 'get'
  })
}

// 新增岗位能力权重
export function addWeight(data) {
  return request({
    url: '/project/weight',
    method: 'post',
    data: data
  })
}

// 修改岗位能力权重
export function updateWeight(data) {
  return request({
    url: '/project/weight',
    method: 'put',
    data: data
  })
}

// 删除岗位能力权重
export function delWeight(id) {
  return request({
    url: '/project/weight/' + id,
    method: 'delete'
  })
}
