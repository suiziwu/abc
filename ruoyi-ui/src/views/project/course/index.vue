<template>
  <div class="course-page">
    <div class="course-topbar">
      <div class="topbar-left">
        <el-icon class="back-icon" @click="goBack"><ArrowLeft /></el-icon>
        <div class="brand">
          <svg-icon icon-class="education" />
          <span>智程学途</span>
        </div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>课程学习</el-breadcrumb-item>
          <el-breadcrumb-item>{{ course.title }}</el-breadcrumb-item>
        </el-breadcrumb>
      </div>
      <div class="topbar-right">
        <el-button text @click="switchPanel('chat')">
          <el-icon><ChatDotRound /></el-icon>
          AI 对话
        </el-button>
        <el-tag type="success" effect="plain">课程进行中</el-tag>
      </div>
    </div>

    <div class="course-main">
      <section class="course-left">
        <div class="video-card">
          <div class="video-shell">
            <video
              ref="videoRef"
              :src="videoUrl"
              controls
              preload="metadata"
              @timeupdate="onTimeUpdate"
              @loadedmetadata="onLoadedMetadata"
            ></video>
            <div class="video-badge">课程视频</div>
          </div>
          <div class="video-info">
            <div class="video-info-main">
              <div class="video-title">{{ course.title }}</div>
              <div class="video-sub">{{ course.subtitle }}</div>
            </div>
            <div class="video-actions">
              <el-tag effect="plain">{{ formatTime(videoDuration) }}</el-tag>
              <el-tag type="info" effect="plain">{{ points.length }} 个知识点</el-tag>
            </div>
          </div>
          <div class="video-question">
            <el-input
              v-model="videoQuestion"
              placeholder="输入你想和视频说的话..."
              clearable
              @keyup.enter="askVideoQuestion"
            >
              <template #prefix>
                <el-icon><ChatDotRound /></el-icon>
              </template>
            </el-input>
            <el-button type="primary" :loading="chatLoading" @click="askVideoQuestion">发送</el-button>
          </div>
        </div>

        <el-card shadow="never" class="transcript-card">
          <div class="left-tabs">
            <button
              v-for="tab in leftTabs"
              :key="tab.key"
              class="left-tab"
              :class="{ active: leftTab === tab.key }"
              @click="leftTab = tab.key"
            >
              <el-icon><component :is="tab.icon" /></el-icon>
              <span>{{ tab.label }}</span>
            </button>
            <div class="left-tab-extra">
              <el-input
                v-if="leftTab === 'transcript'"
                v-model="transcriptSearch"
                placeholder="搜索文本"
                clearable
                size="small"
                style="width: 150px"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <span v-if="leftTab === 'transcript'" class="sentence-count">
                {{ filteredSegmentCount }} 句
              </span>
            </div>
          </div>

          <div v-if="leftTab === 'transcript'" ref="leftTranscriptRef" class="transcript-list">
            <div v-for="group in filteredTranscriptGroups" :key="group.key" class="transcript-group">
              <div class="transcript-group-head">
                <span class="chapter-dot" :style="{ background: group.color }"></span>
                <span class="group-title">{{ group.title }}</span>
                <span class="group-time">{{ formatTime(group.start) }}</span>
              </div>
              <div
                v-for="segment in group.segments"
                :key="segment.key"
                class="transcript-item sentence"
                :class="{ active: activeSegmentKey === segment.key }"
                :data-seg-key="segment.key"
                @click="seekTo(segment.time)"
              >
                <span class="time-chip">{{ formatTime(segment.time) }}</span>
                <div class="transcript-body">
                  <div class="transcript-text">{{ segment.text }}</div>
                </div>
              </div>
            </div>
            <el-empty v-if="filteredTranscriptGroups.length === 0" description="没有匹配的课程文本" />
          </div>

          <div v-else-if="leftTab === 'design'" class="design-list">
            <div v-for="chapter in chapters" :key="chapter.title" class="design-item">
              <span class="chapter-dot" :style="{ background: chapter.color }"></span>
              <div class="design-main">
                <div class="design-title">{{ chapter.title }}</div>
                <div class="design-desc">{{ chapter.desc }}</div>
              </div>
              <el-button text type="primary" @click="seekTo(chapter.start)">跳转</el-button>
            </div>
          </div>

          <div v-else-if="leftTab === 'tags'" class="tag-cloud">
            <el-tag
              v-for="point in points"
              :key="point.id"
              class="point-tag"
              :type="difficultyTag(point.difficulty)"
              effect="plain"
              @click="openPoint(point)"
            >
              {{ point.name }}
            </el-tag>
            <el-empty v-if="points.length === 0" description="暂无知识点标签" />
          </div>

          <div v-else class="quiz-entry">
            <div class="quiz-entry-title">随堂练习</div>
            <div class="quiz-entry-desc">基于本课程知识点生成选择题与判断题，支持智能判卷。</div>
            <el-button type="primary" @click="switchPanel('quiz')">开始练习</el-button>
          </div>
        </el-card>

        <el-card shadow="never" class="timeline-card">
          <div class="timeline-head">
            <span>课程时间轴</span>
            <span class="timeline-time">{{ formatTime(currentTime) }} / {{ formatTime(videoDuration) }}</span>
          </div>
          <div class="timeline-bar">
            <div
              v-for="chapter in chapters"
              :key="chapter.title"
              class="timeline-segment"
              :style="{ width: chapterWidth(chapter) + '%', background: chapter.color }"
              :title="chapter.title"
              @click="seekTo(chapter.start)"
            ></div>
            <div class="timeline-progress" :style="{ left: progressPercent + '%' }"></div>
          </div>
          <div class="chapter-list">
            <div v-for="chapter in chapters" :key="chapter.title" class="chapter-item">
              <span class="chapter-dot" :style="{ background: chapter.color }"></span>
              <div class="chapter-main">
                <div class="chapter-title">{{ chapter.title }}</div>
                <div class="chapter-desc">{{ chapter.desc }}</div>
              </div>
              <span class="chapter-time">{{ formatTime(chapter.start) }}</span>
            </div>
          </div>
        </el-card>
      </section>

      <aside class="course-right">
        <div class="panel-card">
          <div class="panel-head">
            <div class="panel-title">
              <el-icon><Reading /></el-icon>
              教学空间
            </div>
            <el-tag size="small" effect="plain">AI 学习</el-tag>
          </div>
          <div class="panel-tabs">
            <button
              v-for="panel in panels"
              :key="panel.key"
              class="panel-tab"
              :class="{ active: activePanel === panel.key }"
              @click="switchPanel(panel.key)"
            >
              <el-icon><component :is="panel.icon" /></el-icon>
              <span>{{ panel.short }}</span>
            </button>
          </div>

          <div class="panel-body">
            <!-- 教学空间概览 -->
            <div v-if="activePanel === 'overview'" class="overview">
              <div class="space-summary">
                <div class="summary-title">{{ course.title }}</div>
                <div class="summary-desc">{{ course.description }}</div>
              </div>
              <div class="feature-grid">
                <div
                  v-for="card in featureCards"
                  :key="card.key"
                  class="feature-card"
                  :class="card.key"
                  @click="switchPanel(card.key)"
                >
                  <div class="feature-icon">
                    <el-icon><component :is="card.icon" /></el-icon>
                  </div>
                  <div class="feature-title">{{ card.title }}</div>
                  <div class="feature-desc">{{ card.desc }}</div>
                </div>
              </div>
            </div>

            <!-- 视频文本 -->
            <div v-else-if="activePanel === 'transcript'" class="panel-transcript">
              <div class="panel-section-head">
                <span>视频文本</span>
                <el-tag size="small" effect="plain">{{ transcriptSegments.length }} 句 · 点击跳转</el-tag>
              </div>
              <div ref="panelTranscriptRef" class="transcript-list panel-scroll">
                <div v-for="group in transcriptGroups" :key="group.key" class="transcript-group">
                  <div class="transcript-group-head">
                    <span class="chapter-dot" :style="{ background: group.color }"></span>
                    <span class="group-title">{{ group.title }}</span>
                    <span class="group-time">{{ formatTime(group.start) }}</span>
                  </div>
                  <div
                    v-for="segment in group.segments"
                    :key="segment.key"
                    class="transcript-item sentence"
                    :class="{ active: activeSegmentKey === segment.key }"
                    :data-seg-key="segment.key"
                    @click="seekTo(segment.time)"
                  >
                    <span class="time-chip">{{ formatTime(segment.time) }}</span>
                    <div class="transcript-body">
                      <div class="transcript-text">{{ segment.text }}</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- AI 对话 -->
            <div v-else-if="activePanel === 'chat'" class="chat-panel">
              <div ref="chatListRef" class="chat-list panel-scroll" @click="handleChatClick">
                <div v-for="(message, index) in chatMessages" :key="index" class="chat-row" :class="message.role">
                  <div class="chat-avatar">{{ message.role === 'assistant' ? 'AI' : '我' }}</div>
                  <div class="chat-bubble">
                    <div
                      class="chat-text"
                      v-html="message.role === 'assistant' ? formatAnswer(message.text) : escapeHtml(message.text)"
                    ></div>
                  </div>
                </div>
                <div v-if="chatLoading" class="chat-row assistant">
                  <div class="chat-avatar">AI</div>
                  <div class="chat-bubble"><span class="typing">正在思考...</span></div>
                </div>
              </div>
              <div v-if="chatMessages.length <= 1" class="suggestion-list">
                <span class="suggestion-label">你可以这样问：</span>
                <el-tag
                  v-for="question in suggestedQuestions"
                  :key="question"
                  class="suggestion-tag"
                  effect="plain"
                  round
                  @click="sendChat(question)"
                >
                  {{ question }}
                </el-tag>
              </div>
              <div class="chat-model-bar">
                <span class="chat-model-label">回答模型</span>
                <el-select
                  v-model="selectedModel"
                  size="small"
                  class="chat-model-select"
                  placeholder="选择模型"
                  :disabled="chatLoading"
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
                    <span v-if="!item.available" class="model-unavailable">（未配置 Key）</span>
                  </el-option>
                </el-select>
                <span class="chat-model-tip">切换后生效</span>
              </div>
              <div class="chat-input">
                <el-input
                  v-model="chatInput"
                  type="textarea"
                  :rows="2"
                  resize="none"
                  placeholder="关于视频的提问..."
                  @keyup.enter.exact.prevent="sendChat()"
                />
                <el-button type="primary" :icon="Promotion" :loading="chatLoading" @click="sendChat()">
                  发送
                </el-button>
              </div>
            </div>

            <!-- 思维导图 -->
            <div v-else-if="activePanel === 'mindmap'" class="mindmap-panel">
              <div class="mindmap-toolbar">
                <div class="mindmap-title">
                  <span>课程思维导图</span>
                  <el-tag size="small" effect="plain">{{ points.length }} 个知识点</el-tag>
                </div>
                <div class="mindmap-actions">
                  <el-button circle size="small" title="放大" @click="zoomMindmap(1.2)">
                    <el-icon><Plus /></el-icon>
                  </el-button>
                  <el-button circle size="small" title="缩小" @click="zoomMindmap(0.8)">
                    <el-icon><Minus /></el-icon>
                  </el-button>
                  <el-button circle size="small" title="重置" @click="resetMindmap">
                    <el-icon><Refresh /></el-icon>
                  </el-button>
                </div>
              </div>
              <div ref="mindmapRef" class="mindmap-chart" v-loading="mindmapLoading"></div>
            </div>

            <!-- 随堂练习 -->
            <div v-else-if="activePanel === 'quiz'" class="quiz-panel">
              <div class="quiz-head">
                <div class="quiz-head-left">
                  <span class="quiz-kicker">QUESTION</span>
                  <span class="quiz-progress">{{ quizIndex + 1 }} / {{ quizList.length }}</span>
                </div>
                <el-switch v-model="smartGrading" size="small" active-text="智能判卷" />
              </div>
              <div v-if="currentQuiz" class="quiz-body panel-scroll">
                <div class="quiz-type">{{ quizTypeLabel(currentQuiz) }}</div>
                <div class="quiz-question">{{ currentQuiz.question }}</div>
                <el-radio-group
                  v-if="!currentQuiz.multiple"
                  v-model="currentSelection"
                  class="quiz-options"
                  @change="onQuizSelect"
                >
                  <el-radio
                    v-for="option in currentQuiz.options"
                    :key="option.label"
                    :value="option.label"
                    class="quiz-option"
                  >
                    <span class="option-label">{{ option.label }}</span>
                    <span>{{ option.text }}</span>
                  </el-radio>
                </el-radio-group>
                <el-checkbox-group
                  v-else
                  v-model="currentSelection"
                  class="quiz-options"
                  @change="onQuizSelect"
                >
                  <el-checkbox
                    v-for="option in currentQuiz.options"
                    :key="option.label"
                    :value="option.label"
                    class="quiz-option"
                  >
                    <span class="option-label">{{ option.label }}</span>
                    <span>{{ option.text }}</span>
                  </el-checkbox>
                </el-checkbox-group>
                <div v-if="quizChecked" class="quiz-feedback" :class="quizCorrect ? 'correct' : 'wrong'">
                  <div class="feedback-title">{{ quizCorrect ? '回答正确' : '回答错误' }}</div>
                  <div class="feedback-answer">正确答案：{{ currentQuiz.answer.join('、') }}</div>
                  <div v-if="currentQuiz.analysis" class="feedback-analysis">{{ currentQuiz.analysis }}</div>
                </div>
              </div>
              <el-empty v-else description="暂无随堂练习题目" />
              <div class="quiz-footer">
                <el-button text :disabled="quizIndex === 0" @click="prevQuiz">
                  <el-icon><ArrowLeft /></el-icon>
                  上一题
                </el-button>
                <el-button type="primary" :disabled="!currentQuiz || quizChecked" @click="checkQuiz">
                  提交答案
                </el-button>
                <el-button text :disabled="quizIndex >= quizList.length - 1" @click="nextQuiz">
                  下一题
                  <el-icon><ArrowRight /></el-icon>
                </el-button>
              </div>
            </div>
          </div>
        </div>
      </aside>
    </div>

    <!-- 知识点详情 -->
    <el-dialog v-model="pointDialogVisible" :title="currentPoint?.name" width="560px">
      <el-descriptions v-if="currentPoint" :column="2" border>
        <el-descriptions-item label="知识点" :span="2">{{ currentPoint.name }}</el-descriptions-item>
        <el-descriptions-item label="难度">{{ difficultyLabel(currentPoint.difficulty) }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentPoint.type || '-' }}</el-descriptions-item>
        <el-descriptions-item label="说明" :span="2">{{ currentPoint.content || '暂无说明' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup name="CourseStudy">
import * as echarts from 'echarts'
import {
  ArrowLeft, ArrowRight, ChatDotRound, Search, Promotion, Plus, Minus, Refresh,
  Document, Share, EditPen, DataAnalysis, Reading, VideoPlay, Grid, Clock
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter, useRoute } from 'vue-router'
import { courseChat, getCourseModels, getCourseQuiz } from '@/api/project/course'
import { listPoint } from '@/api/project/point'
import { listChapter, listChapterPoints } from '@/api/project/chapter'
import networkTranscript from './network-transcript.json'

const router = useRouter()
const route = useRoute()

// 课程上下文支持通过菜单 query 参数注入（?subjectId=&chapterNo=&testId=&courseTitle=&video=），
// 未传时回退到《计算机网络》第一章的默认配置。
const querySubjectId = Number(route.query.subjectId)
const queryTestId = Number(route.query.testId)

const course = {
  title: String(route.query.courseTitle || '计算机网络 · 第一章 概念和功能'),
  subtitle: String(route.query.subtitle || '第一章 计算机网络体系结构 · 概念、组成、功能与性能指标'),
  description: String(route.query.description || '以《计算机网络》第 1 章视频逐字稿为案例，学习计算机网络的概念、组成、功能、分类、标准化、性能指标以及体系结构与参考模型。'),
  subjectId: Number.isFinite(querySubjectId) && querySubjectId > 0 ? querySubjectId : 94,
  chapterNo: String(route.query.chapterNo || '第1章'),
  testId: Number.isFinite(queryTestId) && queryTestId > 0 ? queryTestId : 102
}

const videoUrl = route.query.video
  ? String(route.query.video)
  : import.meta.env.BASE_URL + 'videos/network-course.mp4'

const chapterMeta = [
  { desc: '概念、组成、功能与分类', color: '#14b8a6' },
  { desc: '资源子网、通信子网与资源共享', color: '#8b5cf6' },
  { desc: '数据通信、分布式处理与 Internet', color: '#ec4899' },
  { desc: '互联网、ISP 与网络层次结构', color: '#f59e0b' },
  { desc: '本节总结与下节预告', color: '#eab308' }
]

const chapters = networkTranscript.chapters.map((chapter, index) => ({
  start: chapter.start / 1000,
  end: chapter.end / 1000,
  title: chapter.title,
  desc: chapterMeta[index]?.desc || '',
  color: chapterMeta[index]?.color || '#14b8a6'
}))

// 逐句字幕：直接使用 ASR 输出的一句话级片段，避免把整章拼接成大段文字
const transcriptSegments = (networkTranscript.segments || [])
  .map(segment => ({
    key: segment.index,
    time: segment.start / 1000,
    end: segment.end / 1000,
    text: String(segment.text || '').trim()
  }))
  .filter(segment => segment.text.length > 0)
  .sort((a, b) => a.time - b.time)

// 章节分组：仅用于按小节折叠，句子本身仍保持独立、可点击跳转
const transcriptGroups = chapters.map(chapter => ({
  key: chapter.title,
  title: chapter.title,
  start: chapter.start,
  end: chapter.end,
  color: chapter.color,
  segments: []
}))

transcriptSegments.forEach(segment => {
  const group = transcriptGroups.find(item => segment.time >= item.start && segment.time < item.end)
    || transcriptGroups[transcriptGroups.length - 1]
  if (group) {
    group.segments.push(segment)
  }
})

const leftTabs = [
  { key: 'transcript', label: '视频文本', icon: Document },
  { key: 'design', label: '课程设计', icon: Grid },
  { key: 'tags', label: '重点标签', icon: DataAnalysis },
  { key: 'quiz', label: '课堂测验', icon: EditPen }
]

const panels = [
  { key: 'overview', short: '概览', icon: Grid },
  { key: 'transcript', short: '文本', icon: Document },
  { key: 'chat', short: 'AI', icon: ChatDotRound },
  { key: 'mindmap', short: '导图', icon: Share },
  { key: 'quiz', short: '练习', icon: EditPen }
]

const featureCards = [
  { key: 'transcript', title: '视频文本', desc: '边看边读，时间戳定位', icon: VideoPlay },
  { key: 'chat', title: 'AI 对话', desc: '基于课程内容自由提问', icon: ChatDotRound },
  { key: 'mindmap', title: '思维导图', desc: '梳理知识结构与关联', icon: Share },
  { key: 'quiz', title: '随堂练习', desc: '智能出题与即时判卷', icon: EditPen }
]

const suggestedQuestions = [
  '计算机网络由哪些部分组成？',
  '计算机网络有哪些性能指标？',
  'OSI 与 TCP/IP 模型有什么区别？'
]

const videoRef = ref(null)
const currentTime = ref(0)
const videoDuration = ref(networkTranscript.duration_ms / 1000)
const videoQuestion = ref('')
const leftTab = ref('transcript')
const transcriptSearch = ref('')

const activePanel = ref('overview')
const points = ref([])
const currentChapter = ref(null)
const pointDialogVisible = ref(false)
const currentPoint = ref(null)

const chatMessages = ref([
  { role: 'assistant', text: '你好，我是《计算机网络》课程 AI 助教。你可以问我本章的概念、组成、功能、性能指标和体系结构，我会结合视频逐字稿为你解答。' }
])
const chatInput = ref('')
const chatLoading = ref(false)
const chatListRef = ref(null)
const modelOptions = ref([])
const selectedModel = ref('')
const modelLoading = ref(false)

function loadModels() {
  modelLoading.value = true
  return getCourseModels().then(res => {
    const list = res?.data || res?.rows || []
    modelOptions.value = list.map(item => ({
      id: item.id,
      label: item.label || item.model || item.id,
      model: item.model,
      available: item.available !== false,
      isDefault: !!item.default
    }))
    if (!modelOptions.value.some(item => item.id === selectedModel.value && item.available)) {
      const preferred = modelOptions.value.find(item => item.isDefault && item.available)
        || modelOptions.value.find(item => item.available)
      selectedModel.value = preferred ? preferred.id : ''
    }
  }).catch(() => {
    // 模型列表加载失败时保持为空，后端会使用默认模型
    modelOptions.value = []
    selectedModel.value = ''
  }).finally(() => {
    modelLoading.value = false
  })
}

const mindmapRef = ref(null)
const mindmapLoading = ref(false)
const mindmapZoom = ref(1)
let mindmapChart = null

const quizList = ref([])
const quizIndex = ref(0)
const quizAnswers = ref({})
const quizCheckedMap = ref({})
const smartGrading = ref(true)

const filteredTranscriptGroups = computed(() => {
  const keyword = transcriptSearch.value.trim().toLowerCase()
  if (!keyword) return transcriptGroups
  return transcriptGroups
    .map(group => ({
      ...group,
      segments: group.title.toLowerCase().includes(keyword)
        ? group.segments
        : group.segments.filter(segment => segment.text.toLowerCase().includes(keyword))
    }))
    .filter(group => group.segments.length > 0)
})

const filteredSegmentCount = computed(() =>
  filteredTranscriptGroups.value.reduce((total, group) => total + group.segments.length, 0)
)

const activeSegmentKey = computed(() => {
  let key = transcriptSegments.length ? transcriptSegments[0].key : null
  for (const segment of transcriptSegments) {
    if (currentTime.value >= segment.time) key = segment.key
    else break
  }
  return key
})

const progressPercent = computed(() => {
  if (!videoDuration.value) return 0
  return Math.min(100, Math.max(0, (currentTime.value / videoDuration.value) * 100))
})

const currentQuiz = computed(() => quizList.value[quizIndex.value] || null)

const currentSelection = computed({
  get() {
    const value = quizAnswers.value[quizIndex.value]
    if (value !== undefined) return value
    return currentQuiz.value?.multiple ? [] : ''
  },
  set(value) {
    quizAnswers.value = { ...quizAnswers.value, [quizIndex.value]: value }
    if (smartGrading.value && !currentQuiz.value?.multiple && value) {
      checkQuiz()
    }
  }
})

const quizChecked = computed(() => !!quizCheckedMap.value[quizIndex.value])

const quizCorrect = computed(() => {
  if (!currentQuiz.value) return false
  const selected = normalizeSelection(quizAnswers.value[quizIndex.value])
  const answer = [...currentQuiz.value.answer].sort()
  return selected.length === answer.length && selected.every((item, index) => item === answer[index])
})

const transcriptContext = computed(() => {
  const current = transcriptGroups.find(item => currentTime.value >= item.start && currentTime.value < item.end)
    || transcriptGroups[0]
  if (!current) return `课程：${networkTranscript.video}`
  const currentSegments = current.segments
    .map(segment => `[${formatTime(segment.time)}] ${segment.text}`)
    .join('\n')
  const otherChapters = transcriptGroups
    .filter(item => item !== current)
    .map(item => `【${item.title}】${item.segments.slice(0, 3)
      .map(segment => `[${formatTime(segment.time)}] ${segment.text}`).join('\n')}`)
    .join('\n')
  return `课程：${networkTranscript.video}\n当前章节：${current.title}\n当前章节文本：\n${currentSegments}\n其他章节概要：\n${otherChapters}`
})

const leftTranscriptRef = ref(null)
const panelTranscriptRef = ref(null)

// 播放时高亮当前句，并在列表内自动滚动到可视区域，保证长字幕可读
watch(activeSegmentKey, () => {
  nextTick(() => {
    ;[leftTranscriptRef.value, panelTranscriptRef.value].forEach(container => {
      if (!container) return
      const item = container.querySelector('.transcript-item.active')
      if (item) item.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
    })
  })
})

onMounted(() => {
  const panel = String(route.query.panel || '')
  if (['overview', 'transcript', 'chat', 'mindmap', 'quiz'].includes(panel)) {
    activePanel.value = panel
  }
  loadPoints()
  loadQuiz()
  loadModels()
})

onBeforeUnmount(() => {
  if (mindmapChart) {
    mindmapChart.dispose()
    mindmapChart = null
  }
})

function goBack() {
  router.back()
}

function onLoadedMetadata() {
  if (videoRef.value?.duration) {
    videoDuration.value = videoRef.value.duration
  }
}

function onTimeUpdate() {
  if (videoRef.value) currentTime.value = videoRef.value.currentTime
}

function seekTo(seconds) {
  currentTime.value = seconds
  if (videoRef.value) {
    videoRef.value.currentTime = Math.min(seconds, videoRef.value.duration || seconds)
    videoRef.value.play().catch(() => {})
  }
}

function chapterWidth(chapter) {
  const total = videoDuration.value || 213
  const width = ((Math.min(chapter.end, total) - chapter.start) / total) * 100
  return Math.max(2, width)
}

function formatTime(seconds) {
  const value = Math.max(0, Math.floor(seconds || 0))
  const minute = String(Math.floor(value / 60)).padStart(2, '0')
  const second = String(value % 60).padStart(2, '0')
  return `${minute}:${second}`
}

function difficultyTag(difficulty) {
  const map = { 1: 'success', 2: 'primary', 3: 'warning', 4: 'danger', 5: 'danger' }
  return map[difficulty] || 'info'
}

function difficultyLabel(difficulty) {
  const map = { 1: '简单', 2: '较易', 3: '中等', 4: '较难', 5: '困难' }
  return map[difficulty] || '未知'
}

function openPoint(point) {
  currentPoint.value = point
  pointDialogVisible.value = true
}

function switchPanel(key) {
  activePanel.value = key
  if (key === 'mindmap') {
    nextTick(initMindmap)
  }
  if (key === 'transcript' || key === 'chat') {
    nextTick(scrollChat)
  }
}

function askVideoQuestion() {
  const question = videoQuestion.value.trim()
  if (!question) {
    ElMessage.warning('请输入你想问的问题')
    return
  }
  videoQuestion.value = ''
  chatInput.value = question
  activePanel.value = 'chat'
  nextTick(() => sendChat())
}

function sendChat(text) {
  const question = (text || chatInput.value || '').trim()
  if (!question || chatLoading.value) return
  chatMessages.value.push({ role: 'user', text: question })
  chatInput.value = ''
  chatLoading.value = true
  scrollChat()
  courseChat({
    question,
    context: transcriptContext.value,
    model: selectedModel.value || undefined
  }).then(res => {
    const answer = res?.data?.answer || ''
    chatMessages.value.push({
      role: 'assistant',
      text: answer || '我暂时没有找到对应的课程内容，你可以换一种问法再试试。'
    })
    scrollChat()
  }).catch(error => {
    chatMessages.value.push({
      role: 'assistant',
      text: 'AI 助教暂时不可用：' + (error.message || '请稍后重试')
    })
    scrollChat()
  }).finally(() => {
    chatLoading.value = false
  })
}

function scrollChat() {
  nextTick(() => {
    if (chatListRef.value) {
      chatListRef.value.scrollTop = chatListRef.value.scrollHeight
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
  return escaped.replace(/\[(\d{1,2}:\d{2})\]/g,
    '<span class="time-link" data-time="$1">[$1]</span>')
}

function handleChatClick(event) {
  const target = event.target
  if (target && target.classList.contains('time-link')) {
    const time = target.getAttribute('data-time') || '00:00'
    const parts = time.split(':')
    seekTo(Number(parts[0]) * 60 + Number(parts[1]))
  }
}

function loadPoints() {
  return loadPointsByChapter().then(loaded => {
    if (!loaded) return loadPointsByContent()
  }).catch(() => {
    points.value = []
  }).finally(() => {
    if (activePanel.value === 'mindmap') {
      nextTick(initMindmap)
    }
  })
}

// 优先按章节查询（course_chapter / chapter_point 结构化关联）
function loadPointsByChapter() {
  return listChapter({ subjectId: course.subjectId, chapterNo: course.chapterNo }).then(res => {
    const chapterList = res.data || res.rows || []
    const chapter = chapterList[0]
    if (!chapter) return false
    currentChapter.value = chapter
    return listChapterPoints(chapter.id).then(pointRes => {
      const rows = pointRes.data || pointRes.rows || []
      points.value = rows.map(row => ({
        id: row.pointId,
        name: row.pointName,
        content: row.pointContent,
        subjectId: row.subjectId,
        difficulty: row.difficulty,
        importance: row.importance,
        type: row.type,
        video: row.video,
        section: row.section
      }))
      return true
    })
  }).catch(() => false)
}

// 兜底：章节数据尚未建立时，沿用按学科 + 内容关键字匹配（并排除已失效章节的记录）
function loadPointsByContent() {
  return listPoint({ subjectId: course.subjectId, pageSize: 9999, pageNum: 1 }).then(res => {
    const all = res.rows || res.data || []
    const chapterPoints = all.filter(point => (point.content || '').includes(course.chapterNo))
    points.value = chapterPoints.length ? chapterPoints : all.slice(0, 12)
  }).catch(() => {
    points.value = []
  })
}

function initMindmap() {
  if (!mindmapRef.value) return
  if (mindmapChart) {
    mindmapChart.dispose()
  }
  mindmapLoading.value = true
  mindmapChart = echarts.init(mindmapRef.value)
  const sectionMap = new Map()
  points.value.forEach(point => {
    const content = point.content || ''
    const match = content.match(/第[0-9]+章[^·]*·\s*([^\n【]+)/)
    const section = (point.section || '').trim() || (match ? match[1].trim() : '本章其他')
    if (!sectionMap.has(section)) sectionMap.set(section, [])
    sectionMap.get(section).push(point)
  })
  const palette = ['#14b8a6', '#8b5cf6', '#ec4899', '#f59e0b', '#eab308', '#3b82f6']
  const groups = Array.from(sectionMap.entries()).map(([name, list], index) => ({
    name,
    color: palette[index % palette.length],
    points: list.slice(0, 4)
  }))
  const shorten = (text, maxLength) => {
    const value = String(text || '')
    return value.length > maxLength ? value.slice(0, maxLength) + '…' : value
  }
  const children = groups.map(group => ({
    name: group.name,
    symbolSize: 11,
    itemStyle: { color: group.color },
    label: {
      fontWeight: 'bold',
      color: '#303133',
      fontSize: 10,
      formatter: params => shorten(params.name, 10)
    },
    children: group.points.map(point => ({
      name: point.name,
      value: point.id,
      point,
      symbolSize: 7,
      itemStyle: { color: group.color },
      label: {
        fontWeight: 'normal',
        color: '#475569',
        fontSize: 10,
        formatter: params => shorten(params.name, 7)
      }
    }))
  }))
  const chapterLabel = currentChapter.value?.chapterNo || course.chapterNo || '第1章'
  const chapterTitle = currentChapter.value?.title || '计算机网络概论'
  const root = {
    name: `${chapterLabel} ${chapterTitle}`,
    symbolSize: 48,
    itemStyle: { color: '#0f766e' },
    label: {
      show: true,
      position: 'inside',
      align: 'center',
      verticalAlign: 'middle',
      formatter: chapterLabel,
      fontWeight: 'bold',
      color: '#ffffff',
      fontSize: 12
    },
    children
  }
  mindmapChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: params => {
        const point = params.data?.point
        return point
          ? `<strong>${point.name}</strong><br/>${point.content || ''}`
          : `<strong>${params.name}</strong>`
      }
    },
    series: [{
      type: 'tree',
      data: [root],
      left: 30,
      right: 70,
      top: 20,
      bottom: 20,
      orient: 'LR',
      symbol: 'circle',
      symbolSize: 8,
      initialTreeDepth: 4,
      roam: true,
      expandAndCollapse: false,
      zoom: mindmapZoom.value,
      lineStyle: { color: '#cbd5e1', width: 1.2, curveness: 0.2 },
      label: {
        position: 'left',
        align: 'right',
        verticalAlign: 'middle',
        color: '#303133'
      },
      leaves: {
        label: { position: 'right', align: 'left', verticalAlign: 'middle' }
      },
      emphasis: { focus: 'descendant' }
    }]
  })
  mindmapLoading.value = false
}

function zoomMindmap(factor) {
  mindmapZoom.value = Math.min(1.8, Math.max(0.5, mindmapZoom.value * factor))
  if (mindmapChart) {
    mindmapChart.setOption({ series: [{ zoom: mindmapZoom.value }] })
  }
}

function resetMindmap() {
  mindmapZoom.value = 1
  initMindmap()
}

function loadQuiz() {
  getCourseQuiz({ testId: course.testId, limit: 10 }).then(res => {
    const list = res?.data || []
    quizList.value = list.length ? list : fallbackQuiz()
  }).catch(() => {
    quizList.value = fallbackQuiz()
  })
}

function fallbackQuiz() {
  return [
    {
      id: 1,
      type: 'choice',
      question: '从逻辑结构上看，计算机网络通常由哪两部分组成？',
      options: [
        { label: 'A', text: '资源子网和通信子网' },
        { label: 'B', text: '硬件和软件' },
        { label: 'C', text: '主机和终端' },
        { label: 'D', text: '局域网和广域网' }
      ],
      answer: ['A'],
      analysis: '计算机网络从逻辑上分为资源子网和通信子网。',
      multiple: false
    },
    {
      id: 2,
      type: 'choice',
      question: 'OSI 参考模型一共有多少层？',
      options: [
        { label: 'A', text: '4 层' },
        { label: 'B', text: '7 层' },
        { label: 'C', text: '5 层' },
        { label: 'D', text: '6 层' }
      ],
      answer: ['B'],
      analysis: 'OSI 参考模型共 7 层。',
      multiple: false
    },
    {
      id: 3,
      type: 'judge',
      question: 'Internet 就是互联网的简称，二者完全等同。',
      options: [
        { label: 'A', text: '正确' },
        { label: 'B', text: '错误' }
      ],
      answer: ['B'],
      analysis: 'Internet 是当前全球最大的特定互联网，互联网是更广义的概念。',
      multiple: false
    }
  ]
}

function quizTypeLabel(quiz) {
  if (quiz.multiple) return '多选题'
  if (quiz.type === 'judge') return '判断题'
  return '单选题'
}

function onQuizSelect() {
  // 单选题开启智能判卷时立即判卷；多选题等待提交
  if (smartGrading.value && currentQuiz.value && !currentQuiz.value.multiple) {
    checkQuiz()
  }
}

function checkQuiz() {
  if (!currentQuiz.value) return
  const selected = normalizeSelection(quizAnswers.value[quizIndex.value])
  if (selected.length === 0) {
    ElMessage.warning('请先选择答案')
    return
  }
  quizCheckedMap.value = { ...quizCheckedMap.value, [quizIndex.value]: true }
}

function normalizeSelection(value) {
  if (value === undefined || value === null || value === '') return []
  return (Array.isArray(value) ? value : [value]).map(item => String(item).toUpperCase()).sort()
}

function prevQuiz() {
  if (quizIndex.value > 0) quizIndex.value -= 1
}

function nextQuiz() {
  if (quizIndex.value < quizList.value.length - 1) quizIndex.value += 1
}
</script>

<style scoped>
.course-page {
  min-height: 100vh;
  background: #f4f7fb;
  padding: 0 0 24px;
  color: #1f2937;
}

.course-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  background: #ffffff;
  border-bottom: 1px solid #edf1f7;
  position: sticky;
  top: 0;
  z-index: 20;
}

.topbar-left,
.topbar-right,
.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.back-icon {
  font-size: 18px;
  color: #64748b;
  cursor: pointer;
}

.brand {
  font-weight: 700;
  color: #0f766e;
  font-size: 17px;
  padding-right: 10px;
  border-right: 1px solid #e5e7eb;
}

.brand :deep(.svg-icon) {
  width: 24px;
  height: 24px;
}

.course-main {
  max-width: 1600px;
  margin: 0 auto;
  padding: 18px 24px;
  display: flex;
  gap: 18px;
  align-items: flex-start;
}

.course-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.video-card,
.transcript-card,
.timeline-card,
.panel-card {
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e8eef7;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
  overflow: hidden;
}

.video-shell {
  position: relative;
  background: #0b1120;
  aspect-ratio: 16 / 9;
  max-height: 560px;
  overflow: hidden;
}

.video-shell video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: #0b1120;
  display: block;
}

.video-badge {
  position: absolute;
  top: 14px;
  left: 14px;
  padding: 4px 10px;
  border-radius: 20px;
  background: rgba(15, 23, 42, 0.72);
  color: #ffffff;
  font-size: 12px;
  letter-spacing: 0.4px;
  backdrop-filter: blur(8px);
  pointer-events: none;
}

.video-info {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 10px;
}

.video-title {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.4;
}

.video-sub {
  margin-top: 6px;
  font-size: 13px;
  color: #64748b;
}

.video-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.video-question {
  display: flex;
  gap: 10px;
  padding: 10px 20px 18px;
}

.video-question .el-input {
  flex: 1;
}

.transcript-card,
.timeline-card {
  padding: 0;
}

.left-tabs {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px 0;
  border-bottom: 1px solid #eef2f7;
  overflow-x: auto;
}

.left-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  color: #64748b;
  font-size: 13px;
  padding: 9px 12px;
  border-radius: 10px 10px 0 0;
  cursor: pointer;
  white-space: nowrap;
}

.left-tab:hover {
  color: #0f766e;
  background: #f0fdfa;
}

.left-tab.active {
  color: #0f766e;
  background: #ccfbf1;
  font-weight: 600;
}

.left-tab-extra {
  margin-left: auto;
  padding: 0 4px 6px;
}

.transcript-list {
  max-height: 300px;
  overflow-y: auto;
  padding: 8px 12px 12px;
}

.sentence-count {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.transcript-group + .transcript-group {
  margin-top: 6px;
}

.transcript-group-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px 4px;
  position: sticky;
  top: 0;
  z-index: 2;
  background: #ffffff;
}

.group-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.group-time {
  font-size: 11px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.transcript-item.sentence {
  padding: 7px 10px;
  align-items: flex-start;
}

.transcript-item.sentence .transcript-text {
  color: #475569;
  line-height: 1.65;
}

.transcript-item.sentence.active .transcript-text {
  color: #0f766e;
  font-weight: 600;
}

.panel-transcript .transcript-list {
  max-height: none;
  padding: 4px 0 0;
}

.transcript-item {
  display: flex;
  gap: 12px;
  padding: 12px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background 0.2s;
  border: 1px solid transparent;
}

.transcript-item:hover {
  background: #f8fafc;
}

.transcript-item.active {
  background: #ecfdf5;
  border-color: #99f6e4;
}

.time-chip {
  flex-shrink: 0;
  height: 24px;
  padding: 0 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #475569;
  font-size: 12px;
  line-height: 24px;
  font-variant-numeric: tabular-nums;
}

.transcript-item.active .time-chip {
  background: #14b8a6;
  color: #ffffff;
}

.transcript-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 4px;
}

.transcript-text {
  font-size: 13px;
  line-height: 1.75;
  color: #64748b;
}

.design-list {
  padding: 8px 14px 14px;
}

.design-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 4px;
  border-bottom: 1px dashed #eef2f7;
}

.design-item:last-child {
  border-bottom: none;
}

.design-main {
  flex: 1;
  min-width: 0;
}

.design-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.design-desc {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 3px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.point-tag {
  cursor: pointer;
}

.quiz-entry {
  padding: 28px 20px;
  text-align: center;
}

.quiz-entry-title {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.quiz-entry-desc {
  margin: 8px 0 16px;
  color: #64748b;
  font-size: 13px;
}

.chapter-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  flex-shrink: 0;
}

.timeline-card {
  padding: 16px 18px 18px;
}

.timeline-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  margin-bottom: 12px;
}

.timeline-time {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
}

.timeline-bar {
  position: relative;
  display: flex;
  gap: 4px;
  height: 10px;
  border-radius: 999px;
  overflow: visible;
  background: #f1f5f9;
}

.timeline-segment {
  height: 10px;
  border-radius: 999px;
  cursor: pointer;
  opacity: 0.9;
  transition: opacity 0.2s;
}

.timeline-segment:hover {
  opacity: 1;
}

.timeline-progress {
  position: absolute;
  top: -5px;
  width: 3px;
  height: 20px;
  border-radius: 4px;
  background: #ef4444;
  transform: translateX(-50%);
  transition: left 0.2s linear;
  box-shadow: 0 0 0 2px #ffffff;
  pointer-events: none;
}

.chapter-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
  margin-top: 14px;
}

.chapter-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f8fafc;
}

.chapter-main {
  flex: 1;
  min-width: 0;
}

.chapter-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.chapter-desc {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chapter-time {
  font-size: 11px;
  color: #94a3b8;
  font-variant-numeric: tabular-nums;
}

.course-right {
  width: 430px;
  flex-shrink: 0;
  position: sticky;
  top: 78px;
}

.panel-card {
  display: flex;
  flex-direction: column;
  max-height: calc(100vh - 100px);
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px 10px;
  flex-shrink: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.panel-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 4px 12px 10px;
  border-bottom: 1px solid #eef2f7;
  flex-shrink: 0;
  overflow: visible;
}

.panel-tab {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-width: 64px;
  padding: 7px 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  font-size: 12px;
  line-height: 1.2;
  white-space: nowrap;
}

.panel-tab .el-icon {
  font-size: 13px;
  flex-shrink: 0;
}

.panel-tab:hover {
  background: #f8fafc;
  color: #0f766e;
}

.panel-tab.active {
  background: #ccfbf1;
  color: #0f766e;
  font-weight: 600;
}

.panel-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.overview {
  padding: 16px 18px 20px;
  overflow-y: auto;
}

.space-summary {
  padding: 14px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f0fdfa 0%, #f5f3ff 100%);
  border: 1px solid #e2e8f0;
  margin-bottom: 14px;
}

.summary-title {
  font-size: 15px;
  font-weight: 700;
  color: #0f172a;
}

.summary-desc {
  font-size: 12px;
  line-height: 1.7;
  color: #64748b;
  margin-top: 6px;
}

.feature-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.feature-card {
  padding: 14px;
  border-radius: 12px;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform 0.2s, box-shadow 0.2s;
  min-height: 108px;
}

.feature-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 18px rgba(15, 23, 42, 0.08);
}

.feature-card.transcript {
  background: #eef7ff;
  border-color: #d6eaff;
  color: #2563eb;
}

.feature-card.chat {
  background: #f0fdf4;
  border-color: #d1fae5;
  color: #059669;
}

.feature-card.mindmap {
  background: #f5f3ff;
  border-color: #ede9fe;
  color: #7c3aed;
}

.feature-card.quiz {
  background: #fff7ed;
  border-color: #ffedd5;
  color: #ea580c;
}

.feature-icon {
  font-size: 20px;
  margin-bottom: 8px;
}

.feature-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}

.feature-desc {
  font-size: 11px;
  color: #64748b;
  margin-top: 4px;
  line-height: 1.5;
}

.panel-section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.panel-scroll {
  overflow-y: auto;
}

.panel-transcript {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.panel-transcript .transcript-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 4px 18px 18px;
}

.chat-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.chat-list {
  flex: 1;
  min-height: 260px;
  padding: 14px 16px;
  overflow-y: auto;
  background: #f8fafc;
}

.chat-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 14px;
}

.chat-row.user {
  flex-direction: row-reverse;
}

.chat-avatar {
  width: 28px;
  height: 28px;
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

.chat-row.user .chat-avatar {
  background: #f59e0b;
}

.chat-bubble {
  max-width: calc(100% - 44px);
  padding: 10px 12px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.03);
}

.chat-row.user .chat-bubble {
  background: #ecfdf5;
  border-color: #a7f3d0;
}

.chat-text {
  font-size: 13px;
  line-height: 1.75;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-text :deep(.time-link) {
  color: #0d9488;
  background: #ccfbf1;
  border-radius: 4px;
  padding: 0 4px;
  cursor: pointer;
  font-variant-numeric: tabular-nums;
}

.typing {
  color: #94a3b8;
  font-size: 13px;
}

.suggestion-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 16px;
  border-top: 1px solid #eef2f7;
}

.suggestion-label {
  width: 100%;
  font-size: 11px;
  color: #94a3b8;
}

.suggestion-tag {
  cursor: pointer;
  margin: 0 !important;
}

.chat-model-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 0;
  background: #ffffff;
}

.chat-model-label {
  font-size: 12px;
  color: #64748b;
  white-space: nowrap;
}

.chat-model-select {
  width: 160px;
}

.chat-model-tip {
  font-size: 11px;
  color: #94a3b8;
}

.model-unavailable {
  color: #c0c4cc;
  font-size: 11px;
}

.chat-input {
  display: flex;
  gap: 8px;
  padding: 10px 14px 14px;
  border-top: 1px solid #eef2f7;
  background: #ffffff;
}

.chat-input .el-input {
  flex: 1;
}

.mindmap-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.mindmap-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px 6px;
}

.mindmap-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.mindmap-actions {
  display: flex;
  gap: 4px;
}

.mindmap-chart {
  flex: 1;
  min-height: 460px;
  width: 100%;
}

.quiz-panel {
  display: flex;
  flex-direction: column;
  min-height: 0;
  height: 100%;
}

.quiz-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px 8px;
}

.quiz-head-left {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.quiz-kicker {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: #94a3b8;
  font-weight: 700;
}

.quiz-progress {
  font-size: 13px;
  color: #475569;
  font-variant-numeric: tabular-nums;
}

.quiz-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 6px 16px 12px;
}

.quiz-type {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 6px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
  margin-bottom: 10px;
}

.quiz-question {
  font-size: 15px;
  line-height: 1.7;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 14px;
}

.quiz-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.quiz-option {
  display: flex;
  align-items: flex-start;
  width: 100%;
  height: auto;
  padding: 10px 12px;
  margin: 0 !important;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #ffffff;
  white-space: normal;
}

.quiz-option:hover {
  border-color: #99f6e4;
  background: #f0fdfa;
}

.quiz-option :deep(.el-radio__label),
.quiz-option :deep(.el-checkbox__label) {
  display: flex;
  gap: 8px;
  font-size: 13px;
  color: #334155;
  white-space: normal;
  line-height: 1.6;
}

.option-label {
  font-weight: 700;
  color: #0f766e;
}

.quiz-feedback {
  margin-top: 14px;
  padding: 12px;
  border-radius: 10px;
  font-size: 13px;
  line-height: 1.7;
}

.quiz-feedback.correct {
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #047857;
}

.quiz-feedback.wrong {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #b91c1c;
}

.feedback-title {
  font-weight: 700;
  margin-bottom: 4px;
}

.feedback-analysis {
  margin-top: 6px;
  color: #475569;
}

.quiz-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px 14px;
  border-top: 1px solid #eef2f7;
}

@media (max-width: 1180px) {
  .course-main {
    flex-direction: column;
  }

  .course-right {
    width: 100%;
    position: static;
    max-height: none;
  }

  .panel-card {
    max-height: none;
  }

  .panel-body {
    min-height: 520px;
  }
}
</style>
