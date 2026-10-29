import request from '@/utils/request'

// 课程 AI 助教问答
export function courseChat(data) {
  return request({
    url: '/project/course/chat',
    method: 'post',
    data,
    timeout: 180000
  })
}

// 获取 AI 助教可选的模型列表
export function getCourseModels() {
  return request({
    url: '/project/course/models',
    method: 'get',
    timeout: 30000
  })
}

// 知识库增强问答（RAG）：基于整门课程/章节的知识库检索后作答
// data: { question, context?, model?, subjectId?, chapterId? }
// 返回: { answer, model, strategy, topScore, sources }
export function courseAsk(data) {
  return request({
    url: '/project/course/ask',
    method: 'post',
    data,
    timeout: 180000
  })
}

// 上传课程视频（自动分析生成课程学习数据）
export function uploadCourseVideo(file, data = {}) {
  const formData = new FormData()
  formData.append('file', file)
  Object.keys(data).forEach(key => {
    const value = data[key]
    if (value !== undefined && value !== null && value !== '') {
      formData.append(key, value)
    }
  })
  return request({
    url: '/project/course/video/upload',
    method: 'post',
    data: formData,
    // 不手动指定 Content-Type：交给浏览器/axios 生成带 boundary 的 multipart 头
    timeout: 600000
  })
}

// 仅上传视频（不做 AI 解析）：保存文件并挂到章/课时
export function attachCourseVideo(file, data = {}) {
  const formData = new FormData()
  formData.append('file', file)
  Object.keys(data).forEach(key => {
    const value = data[key]
    if (value !== undefined && value !== null && value !== '') {
      formData.append(key, value)
    }
  })
  return request({
    url: '/project/course/video/attach',
    method: 'post',
    data: formData,
    timeout: 600000
  })
}

// 查询视频分析任务状态
export function getVideoImportStatus(id) {
  return request({
    url: '/project/course/video/status/' + id,
    method: 'get',
    timeout: 30000
  })
}

// 视频分析任务列表
export function listVideoImports(subjectId) {
  return request({
    url: '/project/course/video/list',
    method: 'get',
    params: { subjectId }
  })
}

// 查询课程逐字稿（上传视频分析后动态加载）
export function getCourseTranscript(params) {
  return request({
    url: '/project/course/transcript',
    method: 'get',
    params,
    timeout: 30000
  })
}

// AI 分析视频逐字稿，生成结构化知识导图（不写库）
export function analyzeCourseMindmap(data) {
  return request({
    url: '/project/course/mindmap/analyze',
    method: 'post',
    data,
    timeout: 360000
  })
}

// 将结构化结果同步到知识图谱（新增/映射知识点、关系、章节绑定）
export function syncCourseMindmap(data) {
  return request({
    url: '/project/course/mindmap/sync',
    method: 'post',
    data,
    timeout: 180000
  })
}

// 课本 → 知识拓扑：读取已导入课本正文，AI 抽取知识点与关系，可选直接入库
export function generateTextbookTopology(data) {
  return request({
    url: '/project/course/mindmap/textbook',
    method: 'post',
    data,
    timeout: 600000
  })
}

// 查询某学科最近的结构化梳理记录（可追溯）
export function listCourseMindmapExtractions(params) {
  return request({
    url: '/project/course/mindmap/extractions',
    method: 'get',
    params,
    timeout: 30000
  })
}

// 获取课程随堂练习
export function getCourseQuiz(params) {
  return request({
    url: '/project/course/quiz',
    method: 'get',
    params,
    timeout: 30000
  })
}

// 查询章节下的课时列表（一章多课时）
export function listCourseLessons(chapterId) {
  return request({
    url: '/project/course/lessons/' + chapterId,
    method: 'get',
    timeout: 30000
  })
}

// 删除课时（连同其逐字稿；磁盘视频文件保留）
export function delCourseLesson(lessonId) {
  return request({
    url: '/project/course/lessons/' + lessonId,
    method: 'delete'
  })
}
