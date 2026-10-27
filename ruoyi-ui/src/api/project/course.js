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

// 获取课程随堂练习
export function getCourseQuiz(params) {
  return request({
    url: '/project/course/quiz',
    method: 'get',
    params,
    timeout: 30000
  })
}
