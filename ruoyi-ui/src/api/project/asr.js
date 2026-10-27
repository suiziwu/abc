import request from '@/utils/request'

// Fun-ASR 语音识别
export function transcribeAudio(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/project/asr/transcribe',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 180000
  })
}
