<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    :width="dialogWidth"
    top="6vh"
    append-to-body
    destroy-on-close
    class="course-chat-dialog"
    @update:model-value="handleClose"
  >
    <div class="ccd-body">
      <!-- 顶部工具条：检索范围 / 模型 -->
      <div class="ccd-toolbar">
        <div class="ccd-scope" v-if="scope === 'course'">
          <el-radio-group v-model="searchScope" size="small">
            <el-radio-button label="course">整门课程</el-radio-button>
            <el-radio-button label="chapter" :disabled="!chapterId">当前章节</el-radio-button>
          </el-radio-group>
          <span class="ccd-scope-tip">{{ scopeTip }}</span>
        </div>
        <div class="ccd-scope" v-else>
          <el-tag size="small" type="info" effect="plain">
            <el-icon><VideoPlay /></el-icon>
            当前视频逐字稿
          </el-tag>
          <span class="ccd-scope-tip">{{ scopeTip }}</span>
        </div>

        <div class="ccd-model">
          <span class="ccd-model-label">回答模型</span>
          <el-select
            v-model="selectedModel"
            size="small"
            class="ccd-model-select"
            placeholder="选择模型"
            :disabled="loading"
            :loading="modelLoading"
          >
            <el-option
              v-for="item in modelOptions"
              :key="item.id"
              :label="item.label"
              :value="item.id"
              :disabled="!item.available"
            >
              <span>{{ item.label }}</span>
              <span v-if="!item.available" class="ccd-model-off">（未配置 Key）</span>
            </el-option>
          </el-select>
        </div>
      </div>

      <!-- 消息区 -->
      <div ref="listRef" class="ccd-list">
        <div
          v-for="(message, index) in messages"
          :key="index"
          class="ccd-row"
          :class="message.role"
        >
          <div class="ccd-avatar">{{ message.role === 'assistant' ? 'AI' : '我' }}</div>
          <div class="ccd-bubble">
            <div
              class="ccd-text"
              v-html="message.role === 'assistant' ? formatAnswer(message.text) : escapeHtml(message.text)"
            ></div>

            <!-- 知识库引用来源 -->
            <div v-if="message.sources && message.sources.length" class="ccd-sources">
              <div class="ccd-sources-head">
                <el-icon><Link /></el-icon>
                引用来源
                <span class="ccd-sources-count">{{ message.sources.length }} 条</span>
                <el-tag v-if="message.strategy === 'rag'" size="small" type="success" effect="plain">知识库检索</el-tag>
                <el-tag v-else-if="message.strategy === 'context'" size="small" type="warning" effect="plain">视频文本兜底</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">无上下文</el-tag>
              </div>
              <div
                v-for="(source, sIndex) in message.sources"
                :key="sIndex"
                class="ccd-source"
                :class="{ clickable: source.startTime !== null && source.startTime !== undefined }"
                @click="handleSourceClick(source)"
              >
                <div class="ccd-source-main">
                  <span class="ccd-source-type">{{ sourceTypeLabel(source.sourceType) }}</span>
                  <span class="ccd-source-title">{{ source.title || '未命名片段' }}</span>
                  <span v-if="source.startTime !== null && source.startTime !== undefined" class="ccd-source-time">
                    [{{ formatTime(source.startTime) }}]
                  </span>
                  <span v-if="source.score !== null && source.score !== undefined" class="ccd-source-score">
                    {{ Number(source.score).toFixed(3) }}
                  </span>
                </div>
                <div v-if="source.snippet" class="ccd-source-snippet">{{ source.snippet }}</div>
              </div>
            </div>
          </div>
        </div>

        <div v-if="loading" class="ccd-row assistant">
          <div class="ccd-avatar">AI</div>
          <div class="ccd-bubble"><span class="ccd-typing">正在思考...</span></div>
        </div>
      </div>

      <!-- 推荐问题 -->
      <div v-if="messages.length <= 1" class="ccd-suggestions">
        <span class="ccd-suggestions-label">你可以这样问：</span>
        <el-tag
          v-for="item in suggestions"
          :key="item"
          class="ccd-suggestion"
          effect="plain"
          round
          @click="send(item)"
        >
          {{ item }}
        </el-tag>
      </div>

      <!-- 输入区 -->
      <div class="ccd-input">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          resize="none"
          :placeholder="inputPlaceholder"
          @keyup.enter.exact.prevent="send()"
        />
        <el-button type="primary" :icon="Promotion" :loading="loading" @click="send()">
          发送
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Link, Promotion, VideoPlay } from '@element-plus/icons-vue'
import { courseAsk, courseChat, getCourseModels } from '@/api/project/course'

const props = defineProps({
  /** 弹窗可见性 */
  modelValue: { type: Boolean, default: false },
  /** 对话作用域：course=整门课程（知识库 RAG） / video=当前视频（逐字稿） */
  scope: { type: String, default: 'course' },
  /** 学科 ID（course 作用域用于检索范围） */
  subjectId: { type: [Number, String], default: null },
  /** 章节 ID */
  chapterId: { type: [Number, String], default: null },
  /** 课程标题 */
  courseTitle: { type: String, default: '' },
  /** 章节标题 */
  chapterTitle: { type: String, default: '' },
  /** 兜底上下文提供者，返回当前视频逐字稿文本 */
  contextProvider: { type: Function, default: null }
})

const emit = defineEmits(['update:modelValue', 'seek'])

const listRef = ref(null)
const input = ref('')
const loading = ref(false)
const messages = ref([])
const modelOptions = ref([])
const selectedModel = ref('')
const modelLoading = ref(false)
const searchScope = ref('course')

const COURSE_SUGGESTIONS = [
  '计算机网络由哪些部分组成？',
  '计算机网络有哪些性能指标？',
  'OSI 与 TCP/IP 模型有什么区别？'
]
const VIDEO_SUGGESTIONS = [
  '这节课主要讲了什么内容？',
  '老师提到的第一个要点是什么？',
  '帮我总结这节课的知识点'
]

const dialogTitle = computed(() => {
  if (props.scope === 'course') return `AI 课程助教 · 整门课程`
  return `AI 视频助教 · ${props.chapterTitle || '当前视频'}`
})

const dialogWidth = computed(() => (props.scope === 'course' ? '900px' : '820px'))

const suggestions = computed(() =>
  props.scope === 'course' ? COURSE_SUGGESTIONS : VIDEO_SUGGESTIONS
)

const inputPlaceholder = computed(() => {
  if (props.scope === 'course') {
    return searchScope.value === 'course'
      ? '基于整门课程的知识库提问，如：计算机网络由哪些部分组成？'
      : '基于当前章节的知识库提问...'
  }
  return '关于当前视频的提问...'
})

const scopeTip = computed(() => {
  if (props.scope === 'course') {
    return searchScope.value === 'course'
      ? `检索范围：${props.courseTitle || '本课程'} 全部章节知识库`
      : `检索范围：${props.chapterTitle || '当前章节'}`
  }
  return '仅依据当前视频逐字稿作答'
})

function welcomeText() {
  if (props.scope === 'course') {
    return `你好，我是《${props.courseTitle || '本课程'}》的 AI 课程助教。我会检索整门课程的知识库（课本切片、知识点、章节与视频逐字稿）来回答你的问题，并标注引用来源。`
  }
  return `你好，我是《${props.chapterTitle || '本视频'}》的 AI 视频助教。我会结合当前视频的逐字稿为你解答课程内容，引用时会标注时间戳。`
}

function resetMessages() {
  messages.value = [{ role: 'assistant', text: welcomeText() }]
  input.value = ''
  scrollToBottom()
}

function loadModels() {
  modelLoading.value = true
  return getCourseModels()
    .then(res => {
      const list = res?.data || res?.rows || []
      modelOptions.value = list.map(item => ({
        id: item.id,
        label: item.label || item.model || item.id,
        available: item.available !== false,
        isDefault: !!item.default
      }))
      if (!modelOptions.value.some(item => item.id === selectedModel.value && item.available)) {
        const preferred =
          modelOptions.value.find(item => item.isDefault && item.available) ||
          modelOptions.value.find(item => item.available)
        selectedModel.value = preferred ? preferred.id : ''
      }
    })
    .catch(() => {
      modelOptions.value = []
      selectedModel.value = ''
    })
    .finally(() => {
      modelLoading.value = false
    })
}

watch(
  () => props.modelValue,
  visible => {
    if (!visible) return
    searchScope.value = 'course'
    resetMessages()
    loadModels()
  }
)

watch(
  () => props.scope,
  () => {
    if (props.modelValue) {
      resetMessages()
    }
  }
)

function handleClose(val) {
  emit('update:modelValue', val)
}

function scrollToBottom() {
  nextTick(() => {
    if (listRef.value) {
      listRef.value.scrollTop = listRef.value.scrollHeight
    }
  })
}

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function formatAnswer(text) {
  const escaped = escapeHtml(text)
  return escaped.replace(/\[(\d{1,2}:\d{2})\]/g, '<span class="ccd-time-link" data-time="$1">[$1]</span>')
}

function formatTime(seconds) {
  const value = seconds === null || seconds === undefined ? 0 : Math.max(0, Math.floor(seconds))
  const mm = String(Math.floor(value / 60)).padStart(2, '0')
  const ss = String(value % 60).padStart(2, '0')
  return `${mm}:${ss}`
}

function sourceTypeLabel(sourceType) {
  const map = {
    transcript: '逐字稿',
    point: '知识点',
    chapter: '章节',
    quiz: '练习',
    document: '课本'
  }
  return map[sourceType] || sourceType || '片段'
}

function handleSourceClick(source) {
  if (source.startTime === null || source.startTime === undefined) return
  emit('seek', source.startTime)
}

function send(text) {
  const question = String(text || input.value || '').trim()
  if (!question || loading.value) return
  messages.value.push({ role: 'user', text: question })
  input.value = ''
  loading.value = true
  scrollToBottom()

  const context = props.contextProvider ? props.contextProvider() : ''
  const model = selectedModel.value || undefined

  const request = props.scope === 'course'
    ? courseAsk({
        question,
        context,
        model,
        subjectId: props.subjectId ?? undefined,
        chapterId: searchScope.value === 'chapter' ? props.chapterId ?? undefined : undefined
      })
    : courseChat({ question, context, model })

  request
    .then(res => {
      const data = res?.data || {}
      const answer = data.answer || ''
      messages.value.push({
        role: 'assistant',
        text: answer || '我暂时没有找到对应的课程内容，可以换一种问法再试试。',
        sources: data.sources || [],
        strategy: data.strategy || ''
      })
      scrollToBottom()
    })
    .catch(error => {
      messages.value.push({
        role: 'assistant',
        text: 'AI 助教暂时不可用：' + (error.message || '请稍后重试')
      })
      scrollToBottom()
    })
    .finally(() => {
      loading.value = false
    })
}
</script>

<style scoped>
.ccd-body {
  display: flex;
  flex-direction: column;
  max-height: 72vh;
}

.ccd-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 0 10px;
  border-bottom: 1px solid #eef2f7;
  flex-wrap: wrap;
}

.ccd-scope {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.ccd-scope-tip {
  font-size: 12px;
  color: #94a3b8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ccd-model {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ccd-model-label {
  font-size: 12px;
  color: #64748b;
}

.ccd-model-select {
  width: 150px;
}

.ccd-model-off {
  color: #f56c6c;
  font-size: 12px;
}

.ccd-list {
  flex: 1;
  min-height: 300px;
  padding: 14px 4px;
  overflow-y: auto;
  background: #f8fafc;
}

.ccd-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
}

.ccd-row.user {
  flex-direction: row-reverse;
}

.ccd-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: #0f766e;
  color: #ffffff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ccd-row.user .ccd-avatar {
  background: #f59e0b;
}

.ccd-bubble {
  max-width: calc(100% - 48px);
  padding: 10px 14px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.03);
}

.ccd-row.user .ccd-bubble {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.ccd-text {
  font-size: 13px;
  line-height: 1.8;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.ccd-text :deep(.ccd-time-link) {
  color: #0d9488;
  background: #ccfbf1;
  border-radius: 4px;
  padding: 0 4px;
  cursor: pointer;
  font-variant-numeric: tabular-nums;
}

.ccd-typing {
  color: #94a3b8;
  font-size: 13px;
}

.ccd-sources {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px dashed #e2e8f0;
}

.ccd-sources-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 8px;
}

.ccd-sources-count {
  color: #94a3b8;
}

.ccd-source {
  padding: 7px 9px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  margin-bottom: 6px;
}

.ccd-source.clickable {
  cursor: pointer;
}

.ccd-source.clickable:hover {
  background: #f0fdfa;
  border-color: #99f6e4;
}

.ccd-source-main {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
}

.ccd-source-type {
  padding: 1px 6px;
  border-radius: 4px;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 11px;
  flex-shrink: 0;
}

.ccd-source-title {
  color: #334155;
  font-weight: 600;
}

.ccd-source-time {
  color: #0d9488;
  font-variant-numeric: tabular-nums;
}

.ccd-source-score {
  color: #94a3b8;
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}

.ccd-source-snippet {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  color: #64748b;
  word-break: break-word;
}

.ccd-suggestions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 10px 4px 0;
}

.ccd-suggestions-label {
  font-size: 12px;
  color: #94a3b8;
}

.ccd-suggestion {
  cursor: pointer;
}

.ccd-input {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 4px 0;
  border-top: 1px solid #eef2f7;
  margin-top: 10px;
}

.ccd-input .el-input {
  flex: 1;
}
</style>
