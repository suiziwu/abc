import request from '@/utils/request'

// AI对话
export function chatWithAI(data) {
  return request({
    url: '/project/learn/chat',
    method: 'post',
    data: data,
    timeout: 120000 // AI接口可能需要较长时间
  })
}
