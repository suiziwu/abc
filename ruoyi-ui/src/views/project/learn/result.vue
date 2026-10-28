<template>
  <div class="app-container">
    <div v-if="loading" v-loading="loading" class="result-loading" />

    <template v-else-if="history">
      <!-- 顶部导航 -->
      <div class="result-header">
        <div class="result-header-left">
          <el-button text :icon="ArrowLeft" @click="goBack">返回</el-button>
          <span class="header-title">{{ history.userInput }}</span>
        </div>
        <div>
          <el-tag v-if="subjects.length > 0" type="success" effect="dark" size="small">
            岗位所需 {{ subjects.length }} 门课程 · {{ allPointsCount }} 个知识点
          </el-tag>
          <el-button text :icon="Delete" type="danger" @click="handleDelete" style="margin-left:12px">删除</el-button>
        </div>
      </div>

      <!-- 人才画像概览 -->
      <el-card shadow="never" class="profile-card">
        <template #header>
          <div class="section-header">
            <span><svg-icon icon-class="chart" /> 人才画像</span>
          </div>
        </template>
        <div class="profile-grid">
          <div class="profile-item">
            <div class="profile-label">岗位总知识点</div>
            <div class="profile-value">{{ allPointsCount }}</div>
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
            <div class="profile-value pending">{{ allPointsCount - learnedCount - learningCount }}</div>
          </div>
          <div class="profile-item profile-item-wide">
            <div class="profile-label">岗位匹配完成度</div>
            <el-progress
              :percentage="profilePercent"
              :stroke-width="16"
              :text-inside="true"
              :color="profileColor"
              style="width:100%"
            />
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
        :courses="subjects"
        :active-course-id="activeCourseId"
        :active-point-id="currentVideo ? currentVideo.pointId : null"
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
              </div>
            </div>
            <el-tag type="info" effect="plain">该知识点暂无视频，已展示知识点内容</el-tag>
          </div>
          <div class="point-detail-content">{{ currentVideo.point?.content || '暂无知识点内容说明' }}</div>
        </div>
      </el-card>

      <!-- 总体分析 -->
      <el-card shadow="never" class="section-card" v-if="history.overallAnalysis">
        <template #header>
          <span><svg-icon icon-class="edit" /> 总体分析</span>
        </template>
        <div class="analysis-content">{{ history.overallAnalysis }}</div>
      </el-card>
    </template>

    <div v-else class="result-empty">
      <el-empty description="未找到该记录" :image-size="100" />
      <el-button type="primary" @click="goBack">返回查询页</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHistory, delHistory } from '@/api/project/history'
import { listPoint, addPoint, aiGeneratePointFromCourse } from '@/api/project/point'
import { listSubject } from '@/api/project/subject'
import { listChapter } from '@/api/project/chapter'
import { listRecord, updateRecord, addRecord } from '@/api/project/record'
import { listWatch, addWatch, updateWatch } from '@/api/project/watch'
import CoursePointPanel from './components/CoursePointPanel.vue'
import CourseLearningSection from './components/CourseLearningSection.vue'
import { ArrowLeft, Delete, VideoCamera } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const history = ref(null)
const subjects = ref([])
const allPoints = ref([])
const records = ref([])
const videoRef = ref(null)
const videoCardRef = ref(null)
const currentVideo = ref(null)
const activeCourseId = ref(null)
const generatingCourseId = ref(null)
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
    const res = await getHistory(id)
    if (res.code === 200 && res.data) {
      history.value = res.data
      await loadSubjectPoints()
      await loadUserRecords()
      buildSubjectTree()
    } else {
      history.value = null
    }
  } catch {
    history.value = null
  }
  loading.value = false
}

// 解析AI选中学科，加载知识点
async function loadSubjectPoints() {
  let subjectList = []
  try {
    const parsed = JSON.parse(history.value.selectedSubjects || '[]')
    subjectList = Array.isArray(parsed) ? parsed : []
  } catch {
    subjectList = []
  }

  // 用学科表补全课程名称和简介，避免历史数据只存 id
  const subjectMeta = new Map()
  try {
    const subjectRes = await listSubject({ pageSize: 9999, pageNum: 1 })
    ;(subjectRes.rows || subjectRes.data || []).forEach(item => {
      subjectMeta.set(String(item.id), item)
    })
  } catch (e) {
    console.error('加载学科信息失败', e)
  }

  subjectList = subjectList.map(item => {
    const entry = item && typeof item === 'object' ? item : { id: item }
    const meta = subjectMeta.get(String(entry.id))
    return {
      ...entry,
      name: meta?.name || entry.name || entry.subjectName || `课程 ${entry.id}`,
      code: meta?.code || entry.code || '',
      description: meta?.description || entry.description || entry.reason || ''
    }
  }).filter(item => item.id !== null && item.id !== undefined)

  // 去重，避免同一课程重复展示
  const seenSubjectIds = new Set()
  subjectList = subjectList.filter(item => {
    const key = String(item.id)
    if (seenSubjectIds.has(key)) return false
    seenSubjectIds.add(key)
    return true
  })

  subjects.value = subjectList

  // 并行加载每个学科的知识点
  const pointPromises = subjectList.map(async (s) => {
    try {
      const res = await listPoint({ subjectId: s.id, pageSize: 9999, pageNum: 1 })
      let points = []
      if (res.code === 200 && res.rows) {
        points = res.rows
      } else if (Array.isArray(res)) {
        points = res
      }
      return { subjectId: s.id, name: s.name, points }
    } catch {
      return { subjectId: s.id, name: s.name, points: [] }
    }
  })

  const results = await Promise.all(pointPromises)
  const all = []
  results.forEach(r => {
    all.push(...r.points)
  })
  allPoints.value = all

  // 存在岗位时加载该岗位下的章，按学科分组，用于「学科 → 章 → 知识点」三层展示
  const chapterMap = {}
  if (history.value && history.value.positionId) {
    try {
      const chapterRes = await listChapter({ positionId: history.value.positionId })
      const chapterList = Array.isArray(chapterRes.data) ? chapterRes.data : []
      chapterList.forEach(c => {
        const key = String(c.subjectId)
        if (!chapterMap[key]) chapterMap[key] = []
        chapterMap[key].push(c)
      })
    } catch (e) {
      console.error('加载章列表失败', e)
    }
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

// 加载用户学习记录（加载当前用户全部记录，不限historyId）
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

// 构建带状态的课程知识点树
function buildSubjectTree() {
  // 构建 pointId → record map
  const recordMap = {}
  records.value.forEach(r => {
    recordMap[r.pointId] = r
  })

  // 为每门岗位课程构建知识点
  subjects.value.forEach(s => {
    const subjectResult = allPoints.value.filter(p => String(p.subjectId) === String(s.id))
    s.name = s.name || s.subjectName || `课程 ${s.id}`
    s.subjectName = s.name
    s.description = s.description || s.reason || ''
    s._points = subjectResult.map(p => ({
      ...p,
      _status: recordMap[p.id]?.status || '0',
      _progress: recordMap[p.id]?.progress || 0,
      _quizScore: recordMap[p.id]?.pointQuizScore,
      _recordId: recordMap[p.id]?.id || null
    }))
    // 学科下的章及其知识点分组（无章时退化为平铺分组）
    s._chapterList = subjectChapterMap.value[String(s.id)] || []
    s._chapters = groupPointsByChapter(s._points, s._chapterList)
    const done = s._points.filter(p => p._status === '2').length
    s._doneCount = done
    s._completionRate = s._points.length > 0 ? Math.round((done / s._points.length) * 100) : 0
  })
}

// ====== 统计 ======

const allPointsCount = computed(() => allPoints.value.length)

const learnedCount = computed(() => {
  const pointIds = new Set(allPoints.value.map(p => String(p.id)))
  return records.value.filter(r => r.status === '2' && pointIds.has(String(r.pointId))).length
})

const learningCount = computed(() => {
  const pointIds = new Set(allPoints.value.map(p => String(p.id)))
  return records.value.filter(r => r.status === '1' && pointIds.has(String(r.pointId))).length
})

// 岗位课程卡片：与左侧课程知识点面板共用同一份课程数据
const courseCards = computed(() => subjects.value.map((subject, index) => {
  const points = subject._points || []
  const learning = points.filter(point => point._status === '1').length
  const done = subject._doneCount || 0
  const progress = subject._completionRate || 0
  return {
    id: subject.id,
    name: subject.name,
    code: subject.code || '',
    description: subject.description,
    pointCount: points.length,
    doneCount: done,
    learningCount: learning,
    progress,
    statusLabel: progress >= 100 ? '已完成' : (learning > 0 ? '学习中' : '待学习'),
    themeIndex: index
  }
}))

const profilePercent = computed(() => {
  if (allPointsCount.value === 0) return 0
  return Math.round((learnedCount.value / allPointsCount.value) * 100)
})

const profileColor = computed(() => {
  if (profilePercent.value >= 80) return '#67c23a'
  if (profilePercent.value >= 50) return '#409eff'
  if (profilePercent.value >= 20) return '#e6a23c'
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

  // 获取完整视频URL
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

  // 如果视频播放器已就绪，重新加载
  if (videoRef.value && videoUrl) {
    videoRef.value.load()
  }

  // 自动创建/更新学习记录
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
    allPoints.value = [
      ...allPoints.value.filter(p => String(p.subjectId) !== String(course.id)),
      ...points
    ]
    const currentPointId = currentVideo.value?.pointId
    buildSubjectTree()
    if (currentPointId) {
      const point = allPoints.value.find(item => String(item.id) === String(currentPointId))
      const pointCourse = subjects.value.find(item => String(item.id) === String(course.id))
      if (point && pointCourse) {
        await selectPoint(point, pointCourse)
      }
    }
  } catch (error) {
    console.error('刷新课程知识点失败', error)
  }
}

// 确保有学习记录
async function ensureLearnRecord(point) {
  const existing = records.value.find(r => String(r.pointId) === String(point.id))
  if (!existing) {
    try {
      const res = await addRecord({
        userId: 1, // 实际应该从store取
        pointId: point.id,
        historyId: parseInt(route.params.id),
        status: '1',
        progress: 0
      })
      if (res.code === 200) {
        // 重新加载记录
        await loadUserRecords()
        buildSubjectTree()
      }
    } catch (e) {
      // 如果是唯一键冲突，说明已有记录，尝试直接更新
      const msg = e.message || ''
      if (msg.includes('Duplicate entry') || msg.includes('uk_user_point')) {
        try {
          // 按pointId查询已有记录并更新
          const searchRes = await listRecord({ pointId: point.id, pageSize: 1, pageNum: 1 })
          const found = searchRes.rows && searchRes.rows[0]
          if (found) {
            await updateRecord({
              id: found.id,
              status: '1',
              startTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
            })
            await loadUserRecords()
            buildSubjectTree()
          }
        } catch (e2) {
          console.error('更新学习记录失败', e2)
        }
      } else {
        console.error('创建学习记录失败', e)
      }
    }
  } else if (existing.status === '0') {
    // 从未学改为学习中
    try {
      await updateRecord({
        id: existing.id,
        status: '1',
        startTime: new Date().toISOString().slice(0, 19).replace('T', ' ')
      })
      await loadUserRecords()
      buildSubjectTree()
    } catch (e) {
      console.error('更新学习记录失败', e)
    }
  }
}

// 视频播放进度更新
let watchTimer = null
function onVideoTimeUpdate() {
  if (!videoRef.value || !currentVideo.value) return

  const v = videoRef.value
  const progress = v.duration ? Math.round((v.currentTime / v.duration) * 100) : 0

  // 每10秒或每10%更新一次，避免频繁请求
  if (!watchTimer) {
    watchTimer = setTimeout(async () => {
      watchTimer = null
      const pointId = currentVideo.value.pointId
      const existing = records.value.find(r => String(r.pointId) === String(pointId))

      if (existing && existing.status !== '2') {
        try {
          await updateRecord({
            id: existing.id,
            progress: progress
          })
        } catch (e) {}
      }

      // 更新视频观看记录
      try {
        const watchRes = await listWatch({ pointId, pageSize: 1, pageNum: 1 })
        const watchList = watchRes.rows || []
        const existingWatch = watchList.find(w => String(w.pointId) === String(pointId))
        if (existingWatch) {
          const completed = progress >= 95 ? '1' : existingWatch.completed
          await updateWatch({
            id: existingWatch.id,
            watchProgress: progress,
            watchDuration: Math.round(v.currentTime),
            lastWatchTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
            completed
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
      } catch (e) {}
    }, 5000)
  }
}

// 视频播放结束
async function onVideoEnded() {
  if (!currentVideo.value) return
  const pointId = currentVideo.value.pointId
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
      buildSubjectTree()
      ElMessage.success(`已完成知识点「${currentVideo.value.pointName}」`)
    } catch (e) {}
  }

  // 更新视频观看记录为完成
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
  } catch (e) {}
}

function difficultyLabel(d) {
  return { 1: '简单', 2: '较易', 3: '中等', 4: '较难', 5: '困难' }[d] || '未知'
}
function difficultyTag(d) {
  return { 1: 'success', 2: 'primary', 3: 'warning', 4: 'danger', 5: 'danger' }[d] || 'info'
}

function goBack() {
  router.push({ name: 'Learn' })
}

function handleDelete() {
  ElMessageBox.confirm('确认删除该记录？', '提示', { type: 'warning' }).then(() => {
    delHistory(history.value.id).then(() => {
      ElMessage.success('删除成功')
      goBack()
    })
  }).catch(() => {})
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

/* 人才画像 */
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

.profile-item {
  text-align: center;
}

.profile-item-wide {
  grid-column: span 2;
}

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

/* 视频/知识点内容区 */
.section-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.section-header-left > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.video-card {
  margin-bottom: 16px;
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

/* 无视频知识点内容 */
.point-detail-panel {
  padding: 4px 2px;
}

.point-detail-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #ebeef5;
}

.point-detail-title {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.point-detail-name {
  font-size: 18px;
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
  line-height: 1.9;
  color: #303133;
  white-space: pre-wrap;
}

/* 分析 */
.section-card {
  margin-bottom: 16px;
}

.analysis-content {
  font-size: 15px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
}

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 16px;
}

/* 响应式 */
@media (max-width: 900px) {
  .profile-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .profile-item-wide {
    grid-column: span 2;
  }
}
</style>
