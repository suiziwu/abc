import request from '@/utils/request'

// 人才培养看板总览
export function getTalentOverview(params) {
  return request({
    url: '/project/talent/overview',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 采集时间维度清单（当前快照 + 各采集批次）
export function getTalentBatches() {
  return request({
    url: '/project/talent/batches',
    method: 'get',
    timeout: 60000
  })
}

// 技能需求排行
export function getSkillRank(params) {
  return request({
    url: '/project/talent/skill-rank',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 技能热度曲线
export function getSkillTrend(params) {
  return request({
    url: '/project/talent/skill-trend',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 技能类别分布
export function getCategoryStat(params) {
  return request({
    url: '/project/talent/category-stat',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 专业匹配度
export function getMajorMatch(params) {
  return request({
    url: '/project/talent/major-match',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 专业预警
export function getTalentWarnings(params) {
  return request({
    url: '/project/talent/warnings',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 智能优化建议
export function getTalentSuggestions(params) {
  return request({
    url: '/project/talent/suggestions',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 课程体系（基础必修课 + 专业课程）
export function getCurriculum(params) {
  return request({
    url: '/project/talent/curriculum',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 数据来源与指标口径
export function getDataSource(params) {
  return request({
    url: '/project/talent/data-source',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 行业编码对照表
export function getIndustryCodes() {
  return request({
    url: '/project/talent/industry-codes',
    method: 'get',
    timeout: 60000
  })
}

// 某行业已爬取的岗位（预览逐行业抓取结果.csv）
export function getIndustryJobs(params) {
  return request({
    url: '/project/talent/industry-jobs',
    method: 'get',
    params,
    timeout: 60000
  })
}

// 导入 CSV 岗位数据（同名文件自动覆盖，batchDate 留空表示纳入当前快照）
export function importTalentCsv(file, batchDate) {
  const formData = new FormData()
  formData.append('file', file)
  if (batchDate) {
    formData.append('batchDate', batchDate)
  }
  return request({
    url: '/project/talent/import',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}

// 一键爬取：启动后台爬虫
export function startTalentCrawl() {
  return request({
    url: '/project/talent/crawl/start',
    method: 'post',
    timeout: 30000
  })
}

// 一键爬取：查询进度（RUNNING / IMPORTING / DONE / ERROR）
export function getTalentCrawlStatus() {
  return request({
    url: '/project/talent/crawl/status',
    method: 'get',
    timeout: 30000
  })
}
