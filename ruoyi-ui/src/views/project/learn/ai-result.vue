<template>
  <div class="app-container">
    <div v-if="loading" v-loading="loading" class="result-loading" />

    <template v-else-if="assessment">
      <!-- 顶部导航 -->
      <div class="result-header">
        <div class="result-header-left">
          <el-button text :icon="ArrowLeft" @click="goBack">返回</el-button>
          <span class="header-title">{{ assessment.assessTitle || 'AI 推荐学习路径' }}</span>
        </div>
        <div>
          <el-tag type="success" effect="dark" size="small">
            AI 推荐 · {{ courses.length }} 门课程 · {{ allPoints.length }} 个知识点
          </el-tag>
        </div>
      </div>

      <!-- 学习概览 -->
      <el-card shadow="never" class="profile-card">
        <template #header>
          <div class="section-header">
            <span><svg-icon icon-class="chart" /> 学习概览</span>
          </div>
        </template>
        <div class="profile-grid">
          <div class="profile-item">
            <div class="profile-label">岗位知识点</div>
            <div class="profile-value">{{ allPoints.length }}</div>
          </div>
          <div class="profile-item">
            <div class="profile-label">已学完</div>
            <div class="profile-value done">{{ learnedCount }}</div>
          </div>
          <div class="profile-item">
            <div class="profile-label">学习中</div>
            <div class="profile-value learning">{{ learningCount }}</div>
          </div>
          <div class="profile-item">
            <div class="profile-label">待学习</div>
            <div class="profile-value pending">{{ allPoints.length - learnedCount - learningCount }}</div>
          </div>
          <div class="profile-item profile-item-wide">
            <div class="profile-label">学习完成度</div>
            <el-progress
              :percentage="profilePercent"
              :stroke-width="16"
              :text-inside="true"
              :color="profileColor"
              style="width:100%"
            />
          </div>
          <div class="profile-item">
            <div class="profile-label">测评平均分</div>
            <div class="profile-value" :style="{ color: avgScoreColor }">{{ avgScore }}</div>
          </div>
        </div>
      </el-card>

      <!-- 课程学习卡片：点击进入完整课程学习页 -->
      <CourseLearningSection
        title="课程学习"
        subtitle="点击课程卡片进入完整课程学习页"
        :courses="courseCards"
        empty-text="暂无岗位课程"
        compact
        @open="openCourse"
      />

      <!-- 岗位课程 → 知识点 → 视频，全部在当前页面内完成学习 -->
      <CoursePointPanel
        :courses="courses"
        :active-course-id="activeCourseId"
        :active-point-id="currentVideo ? currentVideo.pointId : null"
        :weak-point-ids="weakPointIds"
        :loading="loading"
        :generating-course-id="generatingCourseId"
        @select-course="selectCourse"
        @select-point="selectPoint"
        @generate-points="generateCoursePoints"
      />

      <el-card v-if="currentVideo" ref="videoCardRef" shadow="never" class="video-card">
        <template #header>
          <div class="section-header">
            <div class="section-header-left">
              <span><el-icon class="header-icon"><VideoCamera /></el-icon> {{ currentVideo.pointName }}</span>
              <el-tag size="small" type="info" effect="plain">
                {{ currentVideo.subjectName }}
              </el-tag>
            </div>
            <el-button
              v-if="currentVideo && currentVideo.point"
              type="primary"
              plain
              size="small"
              :icon="Aim"
              @click="startQuizForPoint(currentVideo.point)"
            >
              知识点测评
            </el-button>
          </div>
        </template>

        <div v-if="currentVideo && currentVideo.videoUrl" class="video-wrapper">
          <video
            :src="currentVideo.videoUrl"
            controls
            autoplay
            class="video-player"
            @timeupdate="onVideoTimeUpdate"
            @ended="onVideoEnded"
            ref="videoRef"
          >
            您的浏览器不支持视频播放
          </video>
        </div>

        <div v-else-if="currentVideo" class="point-detail-panel">
          <div class="point-detail-head">
            <div class="point-detail-title">
              <span class="point-detail-name">{{ currentVideo.pointName }}</span>
              <div class="point-detail-tags">
                <el-tag v-if="currentVideo.point?.difficulty" size="small" :type="difficultyTag(currentVideo.point.difficulty)">
                  {{ difficultyLabel(currentVideo.point.difficulty) }}
                </el-tag>
                <el-tag v-if="currentVideo.point?.type" size="small" type="info" effect="plain">
                  {{ currentVideo.point.type }}
                </el-tag>
                <el-tag v-if="currentVideo.point?._quizScore !== undefined && currentVideo.point?._quizScore !== null" size="small" type="warning">
                  测评 {{ currentVideo.point._quizScore }} 分
                </el-tag>
              </div>
            </div>
            <el-tag type="info" effect="plain">该知识点暂无视频，已展示知识点内容</el-tag>
          </div>
          <div class="point-detail-content">{{ currentVideo.point?.content || '暂无知识点内容说明' }}</div>
        </div>
      </el-card>
    </template>

    <div v-else class="result-empty">
      <el-empty description="未找到该记录" :image-size="100" />
      <el-button type="primary" @click="goBack">返回</el-button>
    </div>

    <!-- 知识点测评弹窗 -->
    <el-dialog v-model="quizVisible" :title="quizTitleText" width="800px" destroy-on-close :close-on-click-modal="false">
      <!-- Loading -->
      <div v-if="quizStep === 'loading'" class="quiz-loading" v-loading="true">
        <span>{{ quizTitleText }}</span>
      </div>

      <!-- 做题界面 -->
      <div v-if="quizStep === 'quiz'" class="quiz-content">
        <div class="quiz-progress">
          <div class="quiz-progress-text">已完成 {{ quizAnsweredCount }} / {{ quizQuestions.length }} 题</div>
          <el-progress :percentage="quizAnsweredPercent" :stroke-width="8" />
        </div>
        <div class="quiz-body">
          <div v-for="(q, idx) in quizQuestions" :key="q.id" class="quiz-question">
            <div class="question-header">
              <span class="question-number">第 {{ idx + 1 }} 题</span>
              <el-tag :type="quizQuestionTypeTag(q.type)" size="small" effect="dark">{{ q.type }}</el-tag>
            </div>
            <div class="question-text">{{ q.question }}</div>

            <!-- 单选 / 判断 -->
            <template v-if="q.type === '单选' || q.type === '判断'">
              <div class="options-list">
                <div v-for="(opt, oIdx) in q.options" :key="oIdx" class="option-item"
                  :class="{ selected: quizUserAnswers[q.id] === oIdx }"
                  @click="quizSelectOption(q.id, oIdx)">
                  <span class="option-radio" :class="{ checked: quizUserAnswers[q.id] === oIdx }">
                    <span v-if="quizUserAnswers[q.id] === oIdx" class="radio-dot"></span>
                  </span>
                  <span class="option-text">{{ opt }}</span>
                </div>
              </div>
            </template>

            <!-- 多选 -->
            <template v-else-if="q.type === '多选'">
              <div class="options-list">
                <div v-for="(opt, oIdx) in q.options" :key="oIdx" class="option-item"
                  :class="{ selected: quizIsMultiSelected(q.id, oIdx) }"
                  @click="quizToggleMultiOption(q.id, oIdx)">
                  <span class="option-checkbox" :class="{ checked: quizIsMultiSelected(q.id, oIdx) }">
                    <el-icon v-if="quizIsMultiSelected(q.id, oIdx)" color="#fff" :size="12"><Check /></el-icon>
                  </span>
                  <span class="option-text">{{ opt }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 结果界面 -->
      <div v-if="quizStep === 'result'" class="quiz-result-content">
        <div class="result-section">
          <div class="result-header-area">
            <div class="result-score-circle">
              <el-progress type="circle" :percentage="quizResult.score" :width="100" :stroke-width="8"
                :color="quizResult.score >= 80 ? '#67c23a' : quizResult.score >= 60 ? '#409eff' : '#e6a23c'" />
              <div class="result-score-label">正确率</div>
            </div>
            <div class="result-summary">
              <div class="result-summary-title">{{ quizResult.overall_analysis }}</div>
            </div>
          </div>
          <el-divider content-position="left">错题回顾</el-divider>
          <div v-if="quizResult.wrongAnswers && quizResult.wrongAnswers.length > 0" class="wrong-answers">
            <div v-for="(item, idx) in quizResult.wrongAnswers" :key="idx" class="wrong-answer-card"
              :style="{ '--delay': idx * 0.1 + 's' }">
              <div class="wrong-answer-header">
                <el-tag type="danger" size="small" effect="dark">第 {{ item.index + 1 }} 题</el-tag>
                <el-tag :type="quizQuestionTypeTag(item.type)" size="small" effect="plain">{{ item.type }}</el-tag>
              </div>
              <div class="wrong-answer-text">{{ item.question }}</div>
              <div class="wrong-answer-detail">
                <div class="wrong-item">
                  <span class="wrong-label">你的答案：</span>
                  <span class="wrong-value user-answer">{{ item.userAnswerText }}</span>
                </div>
                <div class="wrong-item">
                  <span class="wrong-label">正确答案：</span>
                  <span class="wrong-value correct-answer">{{ item.correctAnswerText }}</span>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="wrong-empty">
            <el-empty description="全部答对，太棒了！" :image-size="60" />
          </div>
        </div>
      </div>

      <!-- Footer -->
      <template #footer>
        <div v-if="quizStep === 'quiz'" class="quiz-footer">
          <span class="quiz-footer-hint">{{ quizUnansweredCount > 0 ? '还有 ' + quizUnansweredCount + ' 题未作答' : '全部已作答' }}</span>
          <el-button type="primary" :loading="quizSubmitting" @click="quizSubmit" :disabled="quizUnansweredCount > 0">
            {{ quizSubmitting ? '提交中...' : '提交答案' }}
          </el-button>
        </div>
        <div v-if="quizStep === 'result'" class="quiz-footer">
          <el-button @click="quizClose">关闭</el-button>
        </div>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAssessment } from '@/api/project/assessment'
import { getPoint, listPoint, addPoint, aiGeneratePointFromCourse } from '@/api/project/point'
import { listSubject } from '@/api/project/subject'
import { getHistory } from '@/api/project/history'
import { listChapter } from '@/api/project/chapter'
import { listRecord, updateRecord, addRecord } from '@/api/project/record'
import { listWatch, addWatch, updateWatch } from '@/api/project/watch'
import { chatWithAI } from '@/api/project/learn'
import CoursePointPanel from './components/CoursePointPanel.vue'
import CourseLearningSection from './components/CourseLearningSection.vue'
import { ArrowLeft, Check, Aim, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const assessment = ref(null)
const history = ref(null)
const courses = ref([])
const rawCourseData = ref([])
const allPoints = ref([])
const records = ref([])
const videoRef = ref(null)
const videoCardRef = ref(null)
const currentVideo = ref(null)
const activeCourseId = ref(null)
const weakPointIds = ref([])
const generatingCourseId = ref(null)
const quizPointId = ref(null)
// 岗位下各学科的章列表：subjectId → 章数组
const subjectChapterMap = ref({})

// ====== 数据加载 ======

onMounted(() => {
  loadData()
})

async function loadData() {
  const id = route.params.id
  if (!id) {
    loading.value = false
    return
  }

  try {
    const res = await getAssessment(id)
    if (res.code === 200 && res.data) {
      assessment.value = res.data
      await loadLearningContext()
      await loadUserRecords()
      buildCourses()
    } else {
      assessment.value = null
    }
  } catch (e) {
    console.error('加载测评记录失败', e)
    assessment.value = null
  }
  loading.value = false
}

function parseWeakPoints() {
  const raw = assessment.value?.aiResponse
  if (!raw) return []
  try {
    const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

// 加载岗位、岗位课程（学科）、章和知识点：学习页以岗位所需课程为入口
async function loadLearningContext() {
  const weakPoints = parseWeakPoints()
  weakPointIds.value = weakPoints.map(item => String(item.pointId))

  // 测评关联的历史记录：岗位和匹配学科
  if (assessment.value?.historyId) {
    try {
      const historyRes = await getHistory(assessment.value.historyId)
      history.value = historyRes?.data || null
    } catch {
      history.value = null
    }
  }

  let selectedSubjects = []
  try {
    selectedSubjects = JSON.parse(history.value?.selectedSubjects || '[]')
  } catch {
    selectedSubjects = []
  }
  if (!Array.isArray(selectedSubjects)) selectedSubjects = []
  // 兼容历史数据只存学科 id 的情况
  selectedSubjects = selectedSubjects
    .map(item => item && typeof item === 'object' ? item : { id: item })
    .filter(item => item.id !== null && item.id !== undefined)

  // 兼容旧数据：没有匹配学科时，从诊断知识点反推课程
  if (selectedSubjects.length === 0 && weakPoints.length > 0) {
    const pointResults = await Promise.all(weakPoints.map(async item => {
      try {
        const res = await getPoint(item.pointId)
        return res?.data || null
      } catch {
        return null
      }
    }))
    const groupMap = new Map()
    pointResults.filter(Boolean).forEach(point => {
      const key = String(point.subjectId)
      if (!groupMap.has(key)) {
        const item = weakPoints.find(w => String(w.pointId) === String(point.id))
        groupMap.set(key, {
          id: point.subjectId,
          name: item?.subjectName || `课程 ${point.subjectId}`,
          description: '',
          points: []
        })
      }
      groupMap.get(key).points.push(point)
    })
    rawCourseData.value = Array.from(groupMap.values())
  }

  // 学科元数据（课程简介/名称）
  const subjectMeta = new Map()
  try {
    const subjectRes = await listSubject({ pageSize: 9999, pageNum: 1 })
    const subjectRows = subjectRes.rows || subjectRes.data || []
    subjectRows.forEach(item => subjectMeta.set(String(item.id), item))
  } catch {}

  // 岗位所需课程：加载每门课程下的全部知识点
  if (rawCourseData.value.length === 0) {
    const uniqueSubjects = []
    const seen = new Set()
    selectedSubjects.forEach(item => {
      const key = String(item.id)
      if (!item.id || seen.has(key)) return
      seen.add(key)
      const meta = subjectMeta.get(key)
      uniqueSubjects.push({
        id: item.id,
        name: meta?.name || item.name || `课程 ${item.id}`,
        code: meta?.code || item.code || '',
        description: meta?.description || item.reason || ''
      })
    })

    const courseResults = await Promise.all(uniqueSubjects.map(async item => {
      try {
        const pointRes = await listPoint({ subjectId: item.id, pageSize: 9999, pageNum: 1 })
        return { ...item, points: pointRes.rows || pointRes.data || [] }
      } catch {
        return { ...item, points: [] }
      }
    }))
    rawCourseData.value = courseResults
  } else {
    // 补全课程名称和简介
    rawCourseData.value = rawCourseData.value.map(item => {
      const meta = subjectMeta.get(String(item.id))
      return {
        ...item,
        name: meta?.name || item.name,
        code: meta?.code || item.code || '',
        description: meta?.description || item.description || ''
      }
    })
  }

  // 岗位下的章：学科 → 章
  await loadChapters()
}

// 通过测评关联的历史记录拿到岗位，加载该岗位下的章并按学科分组
async function loadChapters() {
  const chapterMap = {}
  try {
    const positionId = history.value?.positionId
    if (positionId) {
      const chapterRes = await listChapter({ positionId })
      const chapterList = Array.isArray(chapterRes.data) ? chapterRes.data : []
      chapterList.forEach(c => {
        const key = String(c.subjectId)
        if (!chapterMap[key]) chapterMap[key] = []
        chapterMap[key].push(c)
      })
    }
  } catch (e) {
    console.error('加载章列表失败', e)
  }
  subjectChapterMap.value = chapterMap
}

// 将知识点按章分组：章列表为空时返回单个平铺分组（不渲染章标题，保持原平铺展示）
function groupPointsByChapter(points, chapterList) {
  if (!chapterList || chapterList.length === 0) {
    return points.length > 0 ? [{ id: null, name: '', _flat: true, _points: points }] : []
  }

  const sorted = chapterList.slice().sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  const chapterIds = new Set(sorted.map(c => String(c.id)))
  const chapters = sorted.map(c => ({
    id: c.id,
    name: c.name || c.title,
    _flat: false,
    _points: points.filter(p => String(p.chapterId) === String(c.id))
  }))

  // 没有 chapterId 的知识点归入「未分章」虚拟章，排在最后
  const unassigned = points.filter(p => !p.chapterId || !chapterIds.has(String(p.chapterId)))
  if (unassigned.length > 0) {
    chapters.push({ id: null, name: '未分章', _flat: false, _points: unassigned })
  }

  return chapters.filter(c => c._points.length > 0)
}

// 按岗位课程构建知识点树
function buildCourses() {
  const recordMap = {}
  records.value.forEach(r => {
    recordMap[String(r.pointId)] = r
  })
  const seenPointIds = new Set()

  courses.value = rawCourseData.value.map(course => {
    const points = (course.points || [])
      .filter(point => {
        const key = String(point.id)
        if (seenPointIds.has(key)) return false
        seenPointIds.add(key)
        return true
      })
      .map(point => {
        const record = recordMap[String(point.id)]
        return {
          ...point,
          _status: record?.status || '0',
          _progress: record?.progress || 0,
          _quizScore: record?.pointQuizScore,
          _recordId: record?.id || null
        }
      })
    const done = points.filter(p => p._status === '2').length
    const chapterList = subjectChapterMap.value[String(course.id)] || []
    return {
      id: course.id,
      name: course.name,
      code: course.code || '',
      description: course.description,
      _points: points,
      _chapterList: chapterList,
      _chapters: groupPointsByChapter(points, chapterList),
      _doneCount: done,
      _completionRate: points.length > 0 ? Math.round((done / points.length) * 100) : 0
    }
  })

  allPoints.value = courses.value.flatMap(course => course._points)
}

// 学习记录变化后，只刷新状态和完成度，保留当前学习位置
function applyRecordsToCourses() {
  const recordMap = {}
  records.value.forEach(r => {
    recordMap[String(r.pointId)] = r
  })
  courses.value.forEach(course => {
    course._points.forEach(point => {
      const record = recordMap[String(point.id)]
      point._status = record?.status || '0'
      point._progress = record?.progress || 0
      point._quizScore = record?.pointQuizScore
      point._recordId = record?.id || null
    })
    const done = course._points.filter(p => p._status === '2').length
    course._doneCount = done
    course._completionRate = course._points.length > 0
      ? Math.round((done / course._points.length) * 100)
      : 0
  })
}

// 加载学习记录
async function loadUserRecords() {
  try {
    const res = await listRecord({ pageSize: 9999, pageNum: 1 })
    if (res.code === 200 && res.rows) {
      records.value = res.rows
    }
  } catch {
    records.value = []
  }
}

// ====== 统计 ======

const learnedCount = computed(() => {
  const pointIds = new Set(allPoints.value.map(p => p.id))
  return records.value.filter(r => r.status === '2' && pointIds.has(r.pointId)).length
})

const learningCount = computed(() => {
  const pointIds = new Set(allPoints.value.map(p => p.id))
  return records.value.filter(r => r.status === '1' && pointIds.has(r.pointId)).length
})

// 岗位课程卡片：与左侧课程知识点面板共用同一份课程数据
const courseCards = computed(() => courses.value.map((course, index) => {
  const points = course._points || []
  const learning = points.filter(point => point._status === '1').length
  const done = course._doneCount || 0
  const progress = course._completionRate || 0
  return {
    id: course.id,
    name: course.name,
    code: course.code || '',
    description: course.description,
    pointCount: points.length,
    doneCount: done,
    learningCount: learning,
    progress,
    statusLabel: progress >= 100 ? '已完成' : (learning > 0 ? '学习中' : '待学习'),
    themeIndex: index
  }
}))

const profilePercent = computed(() => {
  if (allPoints.value.length === 0) return 0
  return Math.round((learnedCount.value / allPoints.value.length) * 100)
})

const profileColor = computed(() => {
  if (profilePercent.value >= 80) return '#67c23a'
  if (profilePercent.value >= 50) return '#409eff'
  if (profilePercent.value >= 20) return '#e6a23c'
  return '#f56c6c'
})

const avgScore = computed(() => {
  // 统计当前岗位课程下所有已有测评分数的知识点
  const pointIds = new Set(allPoints.value.map(p => String(p.id)))
  const scored = records.value.filter(r =>
    pointIds.has(String(r.pointId)) &&
    r.pointQuizScore !== null &&
    r.pointQuizScore !== undefined
  )

  if (scored.length === 0) return '暂无'
  const total = scored.reduce((sum, r) => sum + Number(r.pointQuizScore), 0)
  return Math.round(total / scored.length)
})

const avgScoreColor = computed(() => {
  const s = Number(avgScore.value)
  if (isNaN(s)) return '#c0c4cc'
  if (s >= 80) return '#67c23a'
  if (s >= 60) return '#409eff'
  if (s >= 40) return '#e6a23c'
  return '#f56c6c'
})

// ====== 交互 ======

// 点击课程卡片，进入完整课程学习页
function openCourse(course) {
  if (!course?.id) return
  router.push({
    path: '/course',
    query: {
      subjectId: course.id,
      subjectName: course.name || '',
      from: 'card'
    }
  })
}

function selectCourse(course) {
  activeCourseId.value = course.id
  // 点击课程只切换课程，视频在点击具体知识点后再展示
  currentVideo.value = null
}

async function selectPoint(point, course) {
  activeCourseId.value = course.id

  let videoUrl = ''
  if (point.video) {
    videoUrl = point.video.startsWith('http') ? point.video : import.meta.env.VITE_APP_BASE_API + point.video
  }

  currentVideo.value = {
    pointId: point.id,
    pointName: point.name,
    subjectName: course.name,
    videoUrl: videoUrl,
    point
  }

  if (videoRef.value && videoUrl) {
    videoRef.value.load()
  }

  await ensureLearnRecord(point)

  // 视频/知识点内容在当前页面下方展示，不跳转课程学习页
  await nextTick()
  const videoElement = videoCardRef.value?.$el || videoCardRef.value
  videoElement?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
}

// 课程暂无知识点时，按课程视频内容/课程简介生成并保存知识点
async function generateCoursePoints(course) {
  if (generatingCourseId.value) return
  const transcript = course._points
    .slice(0, 30)
    .map(point => [point.name, point.content].filter(Boolean).join('：'))
    .filter(Boolean)
    .join('\n') || [course.name, course.description].filter(Boolean).join('\n')

  if (!transcript) {
    ElMessage.warning('该课程暂无可分析的视频内容或课程简介')
    return
  }

  generatingCourseId.value = course.id
  try {
    const res = await aiGeneratePointFromCourse({
      subjectId: course.id,
      chapterId: null,
      courseTitle: course.name,
      transcript
    })
    const drafts = Array.isArray(res.data) ? res.data : []
    if (drafts.length === 0) {
      ElMessage.warning('未从视频内容中提取到知识点，请稍后重试')
      return
    }

    await ElMessageBox.confirm(
      `已根据课程视频内容提取 ${drafts.length} 个知识点，是否保存到课程「${course.name}」？`,
      '生成知识点',
      { confirmButtonText: '保存', cancelButtonText: '取消', type: 'success' }
    )

    const existingNames = new Set(course._points.map(point => String(point.name || '').trim()))
    let saved = 0
    for (const draft of drafts) {
      const name = String(draft.name || '').trim()
      if (!name || existingNames.has(name)) continue
      await addPoint({
        subjectId: course.id,
        chapterId: null,
        name,
        content: draft.content || '',
        difficulty: Number(draft.difficulty) || 3,
        importance: Number(draft.importance) || 2,
        type: draft.type || '概念',
        status: '0'
      })
      existingNames.add(name)
      saved += 1
    }

    if (saved > 0) {
      ElMessage.success(`已生成并保存 ${saved} 个知识点`)
      await reloadCoursePoints(course)
    } else {
      ElMessage.info('没有新增知识点（生成结果已存在）')
    }
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('生成知识点失败：' + (error?.message || '请稍后重试'))
    }
  } finally {
    generatingCourseId.value = null
  }
}

// 重新读取指定课程的知识点，并保持当前课程/知识点选择
async function reloadCoursePoints(course) {
  try {
    const res = await listPoint({ subjectId: course.id, pageSize: 9999, pageNum: 1 })
    const points = res.rows || res.data || []
    const index = rawCourseData.value.findIndex(item => String(item.id) === String(course.id))
    if (index >= 0) {
      rawCourseData.value[index] = { ...rawCourseData.value[index], points }
    }
    const currentPointId = currentVideo.value?.pointId
    const currentCourseId = activeCourseId.value
    buildCourses()
    if (currentCourseId) activeCourseId.value = currentCourseId
    if (currentPointId) {
      const point = allPoints.value.find(item => String(item.id) === String(currentPointId))
      const pointCourse = courses.value.find(item => String(item.id) === String(currentCourseId))
      if (point && pointCourse) {
        await selectPoint(point, pointCourse)
      }
    }
  } catch (error) {
    console.error('刷新课程知识点失败', error)
  }
}

async function ensureLearnRecord(point) {
  const existing = records.value.find(r => String(r.pointId) === String(point.id))
  if (!existing) {
    try {
      const res = await addRecord({
        userId: 1,
        pointId: point.id,
        historyId: assessment.value?.historyId || null,
        status: '1',
        progress: 0
      })
      if (res.code === 200) {
        await loadUserRecords()
        applyRecordsToCourses()
      }
    } catch (e) {
      const msg = e.message || ''
      if (msg.includes('Duplicate entry') || msg.includes('uk_user_point')) {
        try {
          const searchRes = await listRecord({ pointId: point.id, pageSize: 1, pageNum: 1 })
          const found = searchRes.rows && searchRes.rows[0]
          if (found) {
            await updateRecord({
              id: found.id,
              status: '1',
              startTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
            })
            await loadUserRecords()
            applyRecordsToCourses()
          }
        } catch (e2) {
          console.error('更新学习记录失败', e2)
        }
      } else {
        console.error('创建学习记录失败', e)
      }
    }
  } else if (existing.status === '0') {
    try {
      await updateRecord({
        id: existing.id,
        status: '1',
        startTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
      })
      await loadUserRecords()
      applyRecordsToCourses()
    } catch (e) {
      console.error('更新学习记录失败', e)
    }
  }
}

// 视频进度
let watchTimer = null
function onVideoTimeUpdate() {
  if (!videoRef.value || !currentVideo.value) return
  const v = videoRef.value
  const progress = v.duration ? Math.round((v.currentTime / v.duration) * 100) : 0

  if (!watchTimer) {
    watchTimer = setTimeout(async () => {
      watchTimer = null
      const pointId = currentVideo.value.pointId
      const existing = records.value.find(r => String(r.pointId) === String(pointId))

      if (existing && existing.status !== '2') {
        try {
          await updateRecord({ id: existing.id, progress })
        } catch {}
      }

      try {
        const watchRes = await listWatch({ pointId, pageSize: 1, pageNum: 1 })
        const watchList = watchRes.rows || []
        const existingWatch = watchList.find(w => String(w.pointId) === String(pointId))
        if (existingWatch) {
          await updateWatch({
            id: existingWatch.id,
            watchProgress: progress,
            watchDuration: Math.round(v.currentTime),
            lastWatchTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
            completed: progress >= 95 ? '1' : existingWatch.completed
          })
        } else {
          await addWatch({
            userId: 1,
            pointId,
            watchProgress: progress,
            watchDuration: Math.round(v.currentTime),
            completed: progress >= 95 ? '1' : '0'
          })
        }
      } catch {}
    }, 5000)
  }
}

async function onVideoEnded() {
  if (!currentVideo.value) return
  const pointId = currentVideo.value.pointId
  const pointName = currentVideo.value.pointName
  const existing = records.value.find(r => String(r.pointId) === String(pointId))

  if (existing) {
    try {
      await updateRecord({
        id: existing.id,
        status: '2',
        progress: 100,
        completeTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
      })
      await loadUserRecords()
      applyRecordsToCourses()
      ElMessage.success(`已完成知识点「${pointName}」`)
    } catch {}
  }

  try {
    const watchRes = await listWatch({ pointId, pageSize: 1, pageNum: 1 })
    const watchList = watchRes.rows || []
    const existingWatch = watchList.find(w => String(w.pointId) === String(pointId))
    if (existingWatch) {
      await updateWatch({
        id: existingWatch.id,
        completed: '1',
        watchProgress: 100,
        watchDuration: Math.round(videoRef.value?.duration || 0)
      })
    }
  } catch {}

  // 弹出测评确认
  ElMessageBox.confirm(
    `已学完「${pointName}」，是否立即进行知识点测评？`,
    '知识点测评',
    {
      confirmButtonText: '开始测评',
      cancelButtonText: '稍后再说',
      type: 'question',
      distinguishCancelAndClose: true
    }
  ).then(() => {
    const point = allPoints.value.find(p => String(p.id) === String(pointId))
    if (point) startQuizForPoint(point)
  }).catch(() => {
    // 取消则什么都不做
  })
}

function difficultyLabel(d) {
  return { 1: '简单', 2: '较易', 3: '中等', 4: '较难', 5: '困难' }[d] || '未知'
}

function difficultyTag(d) {
  return { 1: 'success', 2: 'primary', 3: 'warning', 4: 'danger', 5: 'danger' }[d] || 'info'
}

// ====== 知识点测评 ======
const quizVisible = ref(false)
const quizTitleText = ref('')
const quizStep = ref('loading')
const quizQuestions = ref([])
const quizUserAnswers = ref({})
const quizSubmitting = ref(false)
const quizResult = ref({ score: 0, wrongAnswers: [], overall_analysis: '' })

const quizAnsweredCount = computed(() => quizQuestions.value.filter(q => quizUserAnswers.value[q.id] !== undefined).length)
const quizUnansweredCount = computed(() => quizQuestions.value.length - quizAnsweredCount.value)
const quizAnsweredPercent = computed(() => {
  if (quizQuestions.value.length === 0) return 0
  return Math.round((quizAnsweredCount.value / quizQuestions.value.length) * 100)
})

function quizSelectOption(qId, oIdx) {
  quizUserAnswers.value = { ...quizUserAnswers.value, [qId]: oIdx }
}

function quizToggleMultiOption(qId, oIdx) {
  const current = quizUserAnswers.value[qId] || []
  const arr = Array.isArray(current) ? [...current] : []
  const idx = arr.indexOf(oIdx)
  if (idx >= 0) arr.splice(idx, 1); else arr.push(oIdx)
  quizUserAnswers.value = { ...quizUserAnswers.value, [qId]: arr }
}

function quizIsMultiSelected(qId, oIdx) {
  const val = quizUserAnswers.value[qId]
  return Array.isArray(val) && val.includes(oIdx)
}

function quizQuestionTypeTag(type) {
  const map = { '单选': '', '多选': 'success', '判断': 'warning' }
  return map[type] || ''
}

function quizClose() {
  quizVisible.value = false
  quizStep.value = 'loading'
}

async function startQuizForPoint(point) {
  if (!point) return
  quizPointId.value = point.id
  quizTitleText.value = `「${point.name}」知识点测评`
  quizStep.value = 'loading'
  quizVisible.value = true

  try {
    const res = await chatWithAI({ message: `[1]
## 任务
请根据以下知识点，生成 3-5 道题用于检测掌握程度，包含"单选"、"多选"和"判断"三种题型。

## 知识点名称
${point.name}
` })

    if (res.code === 200 && res.data) {
      let reply = res.data.reply || '{}'
      let parsedReply
      try {
        parsedReply = typeof reply === 'string' ? JSON.parse(reply) : reply
      } catch {
        const match = reply.match(/\{[\s\S]*\}/)
        if (match) parsedReply = JSON.parse(match[0])
        else throw new Error('AI返回格式异常')
      }

      const rawQuestions = parsedReply.questions || []
      if (rawQuestions.length === 0) throw new Error('AI未生成有效题目')

      quizQuestions.value = rawQuestions.map((q, idx) => ({
        ...q,
        id: q.id || idx + 1,
        answer: Array.isArray(q.answer) ? q.answer : [q.answer]
      }))

      quizUserAnswers.value = {}
      quizResult.value = { score: 0, wrongAnswers: [], overall_analysis: '' }
      quizSubmitting.value = false
      quizStep.value = 'quiz'
    } else {
      throw new Error(res.msg || 'AI接口返回异常')
    }
  } catch (error) {
    console.error('AI出题失败:', error)
    ElMessage.error('测评加载失败：' + (error.message || ''))
    quizVisible.value = false
    quizStep.value = 'loading'
  }
}

async function quizSubmit() {
  quizSubmitting.value = true

  try {
    function getOptionText(q, idx) {
      if (idx === undefined || idx === null) return '未作答'
      return q.options[idx] || ('选项' + idx)
    }
    function getAnswerText(q, ansArr) {
      if (!ansArr || ansArr.length === 0) return '未作答'
      if (q.type === '判断') return getOptionText(q, ansArr[0])
      return ansArr.map(i => getOptionText(q, i)).join('、')
    }

    let correctCount = 0
    const totalCount = quizQuestions.value.length
    const wrongAnswers = []

    for (const q of quizQuestions.value) {
      const userAns = quizUserAnswers.value[q.id]
      let isCorrect = false

      if (q.type === '单选' || q.type === '判断') {
        const correctAns = Array.isArray(q.answer) ? q.answer[0] : q.answer
        isCorrect = userAns === correctAns
      } else if (q.type === '多选') {
        const userArr = Array.isArray(userAns) ? userAns.sort() : []
        const correctArr = (Array.isArray(q.answer) ? q.answer : []).sort()
        isCorrect = userArr.length === correctArr.length && userArr.every((v, i) => v === correctArr[i])
      }

      if (isCorrect) {
        correctCount++
      } else {
        const userAnsArr = q.type === '多选' ? (Array.isArray(userAns) ? userAns : []) : [userAns]
        wrongAnswers.push({
          index: q.id - 1,
          type: q.type,
          question: q.question,
          options: q.options,
          userAnswerText: getAnswerText(q, userAnsArr),
          correctAnswerText: getAnswerText(q, Array.isArray(q.answer) ? q.answer : [q.answer]),
          correctAnswer: q.answer
        })
      }
    }

    const score = Math.round((correctCount / totalCount) * 100)
    quizResult.value = {
      score,
      wrongAnswers,
      overall_analysis: score >= 80
        ? `表现优秀！共 ${totalCount} 题，答对 ${correctCount} 题。`
        : score >= 60
          ? `表现良好。共 ${totalCount} 题，答对 ${correctCount} 题。`
          : `需要加强。共 ${totalCount} 题，答对 ${correctCount} 题。`
    }

    quizStep.value = 'result'

    // 保存测评最高分到学习记录
    const record = records.value.find(r => String(r.pointId) === String(quizPointId.value))
    if (record && record.id) {
      const existingScore = record.pointQuizScore !== null && record.pointQuizScore !== undefined ? Number(record.pointQuizScore) : -1
      if (score > existingScore) {
        try {
          await updateRecord({ id: record.id, pointQuizScore: score })
          await loadUserRecords()
          applyRecordsToCourses() // 重新构建知识点树，刷新显示分数
        } catch {}
      }
    }
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('提交失败：' + (error.message || ''))
  } finally {
    quizSubmitting.value = false
  }
}

function goBack() {
  router.push({ name: 'Learn' })
}
</script>

<style scoped>
.result-loading { min-height: 300px; }

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 8px;
}

.result-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-card {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  align-items: center;
}

.profile-item { text-align: center; }
.profile-item-wide { grid-column: span 2; }

.profile-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}

.profile-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.profile-value.done { color: #67c23a; }
.profile-value.learning { color: #409eff; }
.profile-value.pending { color: #e6a23c; }

.section-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.section-header-left > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.video-card {
  margin-bottom: 16px;
}

.point-detail-panel {
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 18px 20px;
  min-height: 180px;
}

.point-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.point-detail-title {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.point-detail-name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}

.point-detail-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.point-detail-content {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
  word-break: break-word;
}

.video-wrapper {
  background: #000;
  border-radius: 4px;
  overflow: hidden;
}

.video-player {
  width: 100%;
  max-height: 480px;
  display: block;
  outline: none;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 16px;
}

@media (max-width: 900px) {
  .profile-grid { grid-template-columns: repeat(2, 1fr); }
  .profile-item-wide { grid-column: span 2; }
}

/* ====== 测评弹窗样式 ====== */
.quiz-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  font-size: 15px;
  color: #909399;
}

.quiz-progress {
  padding: 0 4px 16px;
}

.quiz-progress-text {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.quiz-body,
.quiz-result-content {
  max-height: 500px;
  overflow-y: auto;
  padding: 0 4px;
}

.quiz-question {
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid #f0f0f0;
}

.quiz-question:last-child {
  border-bottom: none;
  margin-bottom: 0;
}

.question-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.question-number {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.question-text {
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  margin-bottom: 14px;
  padding: 10px 14px;
  background: #f8f9fc;
  border-radius: 8px;
  border-left: 3px solid #409eff;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-left: 4px;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
}

.option-item:hover {
  border-color: #409eff;
  background: #f0f5ff;
}

.option-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.option-radio,
.option-checkbox {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  border: 2px solid #c0c4cc;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  transition: all 0.2s ease;
}

.option-checkbox {
  border-radius: 4px;
}

.option-radio.checked,
.option-checkbox.checked {
  border-color: #409eff;
  background: #409eff;
}

.radio-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #fff;
}

.option-text {
  font-size: 14px;
  line-height: 1.5;
  color: #606266;
  flex: 1;
}

.option-item.selected .option-text {
  color: #303133;
}

.quiz-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.quiz-footer-hint {
  font-size: 13px;
  color: #909399;
}

/* 结果界面 */
.result-header-area {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 16px 0;
}

.result-score-circle {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.result-score-label {
  font-size: 13px;
  color: #909399;
}

.result-summary {
  flex: 1;
}

.result-summary-title {
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  font-weight: 500;
}

.wrong-answers {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 8px 0;
}

.wrong-answer-card {
  border: 1px solid #fde2e2;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fef0f0;
  animation: fadeInUp 0.3s ease both;
  animation-delay: var(--delay, 0s);
}

@keyframes fadeInUp {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.wrong-answer-header {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.wrong-answer-text {
  font-size: 14px;
  line-height: 1.6;
  color: #303133;
  margin-bottom: 10px;
  padding: 8px 10px;
  background: #fff;
  border-radius: 6px;
}

.wrong-answer-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.wrong-item {
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.wrong-label {
  color: #909399;
  flex-shrink: 0;
}

.wrong-value {
  font-weight: 500;
}

.wrong-value.user-answer {
  color: #f56c6c;
}

.wrong-value.correct-answer {
  color: #67c23a;
}

.wrong-empty {
  padding: 20px 0;
}
</style>