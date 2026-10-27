import request from '@/utils/request'

// 查询知识点列表
export function listPoint(query) {
  return request({
    url: '/project/point/list',
    method: 'get',
    params: query
  })
}

// 查询知识点详细
export function getPoint(id) {
  return request({
    url: '/project/point/' + id,
    method: 'get'
  })
}

// 新增知识点
export function addPoint(data) {
  return request({
    url: '/project/point',
    method: 'post',
    data: data
  })
}

// 修改知识点
export function updatePoint(data) {
  return request({
    url: '/project/point',
    method: 'put',
    data: data
  })
}

// 删除知识点
export function delPoint(id) {
  return request({
    url: '/project/point/' + id,
    method: 'delete'
  })
}
