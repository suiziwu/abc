import request from '@/utils/request'

// 历史图谱列表
export function listCompetencyGraph(query) {
  return request({
    url: '/project/competency/list',
    method: 'get',
    params: query
  })
}

// 岗位输入联想（优先已入库岗位，再补充职业目录）
export function suggestCompetencyJobs(keyword) {
  return request({
    url: '/project/competency/suggest',
    method: 'get',
    params: { keyword: keyword }
  })
}

// 图谱详情（含全文）
export function getCompetencyGraph(id) {
  return request({
    url: '/project/competency/' + id,
    method: 'get'
  })
}

// 生成岗位能力图谱（已存在则直接返回库中记录）
export function generateCompetencyGraph(job) {
  return request({
    url: '/project/competency/generate',
    method: 'post',
    data: { job: job },
    timeout: 900000
  })
}

// KB 模式已收录岗位
export function listCompetencyJobs() {
  return request({
    url: '/project/competency/jobs',
    method: 'get'
  })
}

// 删除图谱
export function delCompetencyGraph(ids) {
  return request({
    url: '/project/competency/' + ids,
    method: 'delete'
  })
}

// 更新审核状态（approved / pending）
export function reviewCompetencyGraph(id, status) {
  return request({
    url: '/project/competency/review/' + id,
    method: 'put',
    params: { status: status }
  })
}
