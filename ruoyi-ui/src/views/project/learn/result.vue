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
            已匹配 {{ subjects.length }} 个学科 · {{ allPointsCount }} 个知识点
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

      <!-- 主区域：左视频 + 右知识点 -->
      <div class="learn-layout">
        <!-- 左侧视频区 -->
        <div class="learn-video">
          <el-card shadow="never" class="video-card">
            <template #header>
              <div class="section-header">
                <span><svg-icon icon-class="video" /> {{ currentVideo ? currentVideo.pointName : '视频播放' }}</span>
              </div>
            </template>
            <div v-if="currentVideo" class="video-wrapper">
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
            <div v-else class="video-placeholder">
              <svg-icon icon-class="video" style="font-size:64px;color:#d0d5dd" />
              <p class="placeholder-text">请从右侧选择一个知识点开始学习</p>
            </div>
          </el-card>
        </div>

        <!-- 右侧知识点列表 -->
        <div class="learn-points">
          <el-card shadow="never" class="points-card">
            <template #header>
              <div class="section-header">
                <span><svg-icon icon-class="list" /> 知识点列表</span>
                <el-input
                  v-model="pointSearch"
                  placeholder="搜索知识点..."
                  prefix-icon="Search"
                  size="small"
                  style="width:180px"
                  clearable
                />
              </div>
            </template>

            <div class="subject-points-list">
              <div
                v-for="(subjectItem, sIdx) in filteredSubjects"
                :key="sIdx"
                class="subject-group"
              >
                <div class="subject-group-header" @click="toggleSubject(sIdx)">
                  <div class="subject-group-title">
                    <el-icon :class="subjectItem._expanded ? 'expand-rotate' : ''">
                      <CaretRight />
                    </el-icon>
                    <span>{{ subjectItem.subjectName }}</span>
                    <el-progress
                      :percentage="subjectItem._completionRate"
                      :stroke-width="8"
                      :text-inside="false"
                      style="width:100px;margin-left:8px"
                    />
                  </div>
                  <el-tag size="small" type="info">
                    {{ subjectItem._doneCount }}/{{ subjectItem._points.length }}
                  </el-tag>
                </div>

                <div v-show="subjectItem._expanded" class="point-list">
                  <div
                    v-for="(point, pIdx) in subjectItem._points"
                    :key="point.id"
                    class="point-item"
                    :class="{ active: currentVideo && currentVideo.pointId === point.id }"
                    @click="selectPoint(point, subjectItem)"
                  >
                    <div class="point-status">
                      <el-icon v-if="point._status === '2'" class="status-done" color="#67c23a">
                        <CircleCheck />
                      </el-icon>
                      <el-icon v-else-if="point._status === '1'" class="status-learning" color="#409eff">
                        <VideoPlay />
                      </el-icon>
                      <span class="status-dot status-pending-dot"></span>
                    </div>
                    <div class="point-info">
                      <div class="point-name">{{ point.name }}</div>
                      <div class="point-meta">
                        <el-tag v-if="point.difficulty" size="small" :type="difficultyTag(point.difficulty)" style="margin-right:4px">
                          {{ difficultyLabel(point.difficulty) }}
                        </el-tag>
                        <el-tag v-if="point.type" size="small" type="info">{{ point.type }}</el-tag>
                      </div>
                    </div>
                    <div class="point-actions">
                      <el-icon v-if="point.video" color="#409eff" title="有视频"><VideoCamera /></el-icon>
                      <el-icon v-else color="#c0c4cc" title="无视频"><VideoCameraFilled /></el-icon>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="filteredSubjects.length === 0" class="no-points">
                <el-empty description="暂无匹配的知识点" :image-size="60" />
              </div>
            </div>
          </el-card>
        </div>
      </div>

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
import { listPoint } from '@/api/project/point'
import { listRecord, updateRecord, addRecord } from '@/api/project/record'
import { listWatch, addWatch, updateWatch } from '@/api/project/watch'
import { ArrowLeft, Delete, CaretRight, CircleCheck, VideoPlay, VideoCamera, VideoCameraFilled, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const history = ref(null)
const subjects = ref([])
const allPoints = ref([])
const records = ref([])
const videoRef = ref(null)
const currentVideo = ref(null)
const pointSearch = ref('')

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

// 构建带状态的知识点树
function buildSubjectTree() {
  // 构建 pointId → record map
  const recordMap = {}
  records.value.forEach(r => {
    recordMap[r.pointId] = r
  })

  // 构建所有pointId集合
  const allPointIds = new Set(allPoints.value.map(p => p.id))

  // 为每个学科构建
  subjects.value.forEach(s => {
    const subjectResult = allPoints.value.filter(p => String(p.subjectId) === String(s.id))
    s._points = subjectResult.map(p => ({
      ...p,
      _status: recordMap[p.id]?.status || '0',
      _progress: recordMap[p.id]?.progress || 0,
      _recordId: recordMap[p.id]?.id || null
    }))
    s._expanded = true
    const done = s._points.filter(p => p._status === '2').length
    s._doneCount = done
    s._completionRate = s._points.length > 0 ? Math.round((done / s._points.length) * 100) : 0
    s.subjectName = s.name || `学科 ${s.id}`
  })
}

// 搜索过滤
const filteredSubjects = computed(() => {
  if (!pointSearch.value.trim()) return subjects.value
  const q = pointSearch.value.trim().toLowerCase()
  return subjects.value.map(s => {
    const filtered = s._points.filter(p => p.name.toLowerCase().includes(q))
    return { ...s, _points: filtered, _expanded: filtered.length > 0 }
  }).filter(s => s._points.length > 0)
})

// ====== 统计 ======

const allPointsCount = computed(() => allPoints.value.length)

const learnedCount = computed(() => {
  return records.value.filter(r => r.status === '2').length
})

const learningCount = computed(() => {
  return records.value.filter(r => r.status === '1').length
})

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

function toggleSubject(idx) {
  const item = subjects.value[idx]
  if (item) item._expanded = !item._expanded
}

async function selectPoint(point, subjectItem) {
  // 获取完整视频URL
  let videoUrl = ''
  if (point.video) {
    videoUrl = point.video.startsWith('http') ? point.video : import.meta.env.VITE_APP_BASE_API + point.video
  }

  currentVideo.value = {
    pointId: point.id,
    pointName: point.name,
    subjectName: subjectItem.subjectName,
    videoUrl: videoUrl
  }

  // 如果视频播放器已就绪，重新加载
  if (videoRef.value && videoUrl) {
    videoRef.value.load()
  }

  // 自动创建/更新学习记录
  await ensureLearnRecord(point)
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

/* 学习布局 */
.learn-layout {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.learn-video {
  flex: 3;
  min-width: 0;
}

.learn-points {
  flex: 2;
  min-width: 300px;
  display: flex;
  flex-direction: column;
}

/* 视频区 */
.video-card {
  height: 100%;
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

.video-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 300px;
  background: #fafafa;
  border-radius: 4px;
}

.placeholder-text {
  color: #909399;
  font-size: 14px;
  margin-top: 12px;
}

/* 知识点列表 */
.points-card {
  height: 100%;
}

.points-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 0;
  min-height: 100%;
  padding-bottom: 12px;
}

.subject-points-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.subject-points-list::-webkit-scrollbar {
  width: 4px;
}
.subject-points-list::-webkit-scrollbar-thumb {
  background: #d0d5dd;
  border-radius: 2px;
}

.subject-group {
  margin-bottom: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  overflow: hidden;
}

.subject-group-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #f5f7fa;
  cursor: pointer;
  transition: background 0.2s;
}

.subject-group-header:hover {
  background: #ecf5ff;
}

.subject-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.expand-rotate {
  transform: rotate(90deg);
  transition: transform 0.2s;
}

.point-list {
  border-top: 1px solid #ebeef5;
}

.point-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s;
  border-bottom: 1px solid #f2f2f2;
}

.point-item:last-child {
  border-bottom: none;
}

.point-item:hover {
  background: #f0f5ff;
}

.point-item.active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  padding-left: 9px;
}

.point-status {
  flex-shrink: 0;
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.status-pending-dot {
  background: #c0c4cc;
}

.point-info {
  flex: 1;
  min-width: 0;
}

.point-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-meta {
  margin-top: 4px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.point-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.no-points {
  padding: 20px 0;
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
  .learn-layout {
    flex-direction: column;
  }
  .profile-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .profile-item-wide {
    grid-column: span 2;
  }
}
</style>
