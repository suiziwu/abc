import request from '@/utils/request'

// 查询课程章节列表
export function listChapter(query) {
  return request({
    url: '/project/chapter/list',
    method: 'get',
    params: query
  })
}

// 查询课程章节详细
export function getChapter(id) {
  return request({
    url: '/project/chapter/' + id,
    method: 'get'
  })
}

// 查询章节下的知识点列表
export function listChapterPoints(chapterId) {
  return request({
    url: '/project/chapter/' + chapterId + '/points',
    method: 'get'
  })
}

// 批量查询知识点所属章节（把扁平知识点列表还原为“章节 → 知识点”路径）
export function listPointChapters(pointIds) {
  return request({
    url: '/project/chapter/pointChapters',
    method: 'get',
    params: { pointIds: (pointIds || []).join(',') }
  })
}

// 新增课程章节
export function addChapter(data) {
  return request({
    url: '/project/chapter',
    method: 'post',
    data: data
  })
}

// 修改课程章节
export function updateChapter(data) {
  return request({
    url: '/project/chapter',
    method: 'put',
    data: data
  })
}

// 删除课程章节
export function delChapter(id) {
  return request({
    url: '/project/chapter/' + id,
    method: 'delete'
  })
}