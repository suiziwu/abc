import request from '@/utils/request'

// 知识库状态（分块总数 / 各语料类型数量 / 向量库实现与可用性）
export function getKbStatus(subjectId) {
  return request({
    url: '/project/kb/status',
    method: 'get',
    params: { subjectId }
  })
}

// 知识库列表（全局 / 学科 / 章节，含分块数）
export function listKbBases() {
  return request({
    url: '/project/kb/list',
    method: 'get'
  })
}

// 重建知识库（可附带逐字稿）
export function rebuildKb(data) {
  return request({
    url: '/project/kb/rebuild',
    method: 'post',
    data,
    timeout: 300000
  })
}

// 语义检索（调试用）
export function searchKb(params) {
  return request({
    url: '/project/kb/search',
    method: 'get',
    params
  })
}

// 清空知识库
export function clearKb(params) {
  return request({
    url: '/project/kb/clear',
    method: 'delete',
    params
  })
}

// 导入课本文档（Markdown / Word / PDF 自动解析 + LLM 语义切片）
export function importDocument(formData) {
  return request({
    url: '/project/kb/import-document',
    method: 'post',
    data: formData,
    timeout: 600000
  })
}

// 浏览知识库切片（按学科 / 章节 / 类型过滤分页）
export function listKbChunks(params) {
  return request({
    url: '/project/kb/chunks',
    method: 'get',
    params
  })
}