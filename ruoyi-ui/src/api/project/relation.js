import request from '@/utils/request'

// 查询知识点关联（拓扑图边）列表
export function listRelation(query) {
  return request({
    url: '/project/relation/list',
    method: 'get',
    params: query
  })
}

// 查询知识点关联（拓扑图边）详细
export function getRelation(id) {
  return request({
    url: '/project/relation/' + id,
    method: 'get'
  })
}

// 新增知识点关联（拓扑图边）
export function addRelation(data) {
  return request({
    url: '/project/relation',
    method: 'post',
    data: data
  })
}

// 修改知识点关联（拓扑图边）
export function updateRelation(data) {
  return request({
    url: '/project/relation',
    method: 'put',
    data: data
  })
}

// 删除知识点关联（拓扑图边）
export function delRelation(id) {
  return request({
    url: '/project/relation/' + id,
    method: 'delete'
  })
}
