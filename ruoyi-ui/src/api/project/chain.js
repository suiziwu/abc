import request from '@/utils/request'

// 查询岗位产业链/岗位群列表
export function listChain(query) {
  return request({
    url: '/project/chain/list',
    method: 'get',
    params: query
  })
}

// 查询岗位产业链/岗位群详细
export function getChain(id) {
  return request({
    url: '/project/chain/' + id,
    method: 'get'
  })
}

// 按岗位查询产业链/岗位群
export function getChainByJob(params) {
  return request({
    url: '/project/chain/byJob',
    method: 'get',
    params
  })
}

// 新增岗位产业链/岗位群
export function addChain(data) {
  return request({
    url: '/project/chain',
    method: 'post',
    data: data
  })
}

// 修改岗位产业链/岗位群
export function updateChain(data) {
  return request({
    url: '/project/chain',
    method: 'put',
    data: data
  })
}

// 删除岗位产业链/岗位群
export function delChain(ids) {
  return request({
    url: '/project/chain/' + ids,
    method: 'delete'
  })
}
