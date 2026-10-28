﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="app-container">
    <div v-if="loading" v-loading="loading" class="result-loading" />

    <template v-else-if="assessment">
      <!-- 顶部导航 -->
      <div class="result-header">
        <div class="result-header-left">
          <el-button text :icon="ArrowLeft" @click="goBack">返回</el-button>
          <span class="header-title">{{ assessment.assessTitle || 'AI 推荐学习路径' }}</span>
        </div>
        <div class="result-header-right">
          <span v-if="assessment.createTime" class="header-time">生成于 {{ assessment.createTime }}</span>
          <el-tag type="success" effect="dark" size="small">
            AI 推荐 · {{ chapterCount }} 个{{ hasChapterPath ? '章节' : '学科' }} · {{ validPoints.length }} 个知识点
          </el-tag>
          <el-button
            type="primary"
            plain
            :icon="Refresh"
            :loading="regenerating"
            @click="regeneratePath"
          >重新生成路径</el-button>
        </div>
      </div>

      <!-- 学习路径过期提示 -->
      <el-alert
        v-if="invalidPoints.length > 0"
        type="warning"
        show-icon
        :closable="false"
        class="stale-alert"
      >
        <template #title>
          <span class="stale-text">
            有 {{ invalidPoints.length }} 个推荐知识点已失效（可能已被删除或停用），当前学习路径可能已过期。
          </span>
          <el-button link type="primary" :loading="regenerating" @click="regeneratePath">立即重新生成</el-button>
        </template>
      </el-alert>

      <!-- 知识点池变化提示（生成时的候选池大小 vs 当前） -->
      <el-alert
        v-else-if="poolGrowth !== null && poolGrowth > 0"
        type="info"
        show-icon
        :closable="false"
        class="stale-alert"
      >
        <template #title>
          <span class="stale-text">
            当前学科知识点较生成路径时新增 {{ poolGrowth }} 个（生成时 {{ generatedPointCount }} 个，现 {{ poolPointCount }} 个），建议重新生成以纳入最新知识点。
          </span>
          <el-button link type="primary" :loading="regenerating" @click="regeneratePath">立即重新生成</el-button>
        </template>
      </el-alert>

      <!-- 学习概览 -->
      <el-card shadow="never" class="profile-card">
        <template #header>
          <div class="section-header">
            <span><svg-icon icon-class="chart" /> 学习概览</span>
          </div>
        </template>
        <div class="profile-grid">
          <div class="profile-item">
            <div class="profile-label">推荐知识点</div>
            <div class="profile-value">{{ validPoints.length }}</div>
          </div>
          <div class="profile-item">
            <div class="profile-label">{{ hasChapterPath ? '涉及章节' : '涉及学科' }}</div>
            <div class="profile-value">{{ chapterCount }}</div>
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
            <div class="profile-value pending">{{ validPoints.length - learnedCount - learningCount }}</div>
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
              <p class="placeholder-text">请从右侧选择一个章节或知识点开始学习</p>
            </div>
          </el-card>
        </div>

        <!-- 右侧知识点列表 -->
        <div class="learn-points">
          <el-card shadow="never" class="points-card">
            <template #header>
              <div class="section-header">
                <span><svg-icon icon-class="list" /> {{ hasChapterPath ? '推荐学习路径（按章节）' : '推荐知识点' }}</span>
                <span v-if="poolPointCount > 0" class="points-count-hint">
                  学科共 {{ poolPointCount }} 个 · 本路径 {{ validPoints.length }} 个
                </span>
              </div>
            </template>

            <div class="subject-points-list">
              <div
                v-for="(group, gIdx) in chapterGroups"
                :key="group.groupName + gIdx"
                class="subject-group"
              >
                <div class="subject-group-header" @click="toggleSubject(gIdx)">
                  <div class="subject-group-title">
                    <el-icon :class="group._expanded ? 'expand-rotate' : ''">
                      <CaretRight />
                    </el-icon>
                    <el-tag v-if="group._isChapter" size="small" effect="plain" class="chapter-badge">章节</el-tag>
                    <span :title="group.groupName">{{ group.groupName }}</span>
                    <el-progress
                      :percentage="group._completionRate"
                      :stroke-width="8"
                      :text-inside="false"
                      style="width:100px;margin-left:8px"
                    />
                  </div>
                  <div class="group-header-right">
                    <el-button
                      v-if="group._chapterVideo"
                      link
                      type="primary"
                      :icon="VideoPlay"
                      title="播放本章视频"
                      @click.stop="selectChapterVideo(group)"
                    />
                    <el-tag size="small" type="info">
                      {{ group._doneCount }}/{{ group._points.length }}
                    </el-tag>
                  </div>
                </div>

                <div v-show="group._expanded" class="point-list">
                  <div
                    v-for="(point, pIdx) in group._points"
                    :key="point.id"
                    class="point-item"
                    :class="{ active: currentVideo && currentVideo.pointId === point.id, invalid: point._invalid }"
                    @click="!point._invalid && selectPoint(point, group)"
                  >
                    <div class="point-status">
                      <el-icon v-if="point._invalid" class="status-invalid" color="#f56c6c">
                        <WarningFilled />
                      </el-icon>
                      <el-icon v-else-if="point._status === '2'" class="status-done" color="#67c23a">
                        <CircleCheck />
                      </el-icon>
                      <el-icon v-else-if="point._status === '1'" class="status-learning" color="#409eff">
                        <VideoPlay />
                      </el-icon>
                      <span v-if="!point._invalid" class="status-dot status-pending-dot"></span>
                    </div>
                    <div class="point-info">
                      <div class="point-name">{{ point.name }}</div>
                      <div class="point-meta">
                        <template v-if="point._invalid">
                          <el-tag size="small" type="danger" effect="plain">已失效</el-tag>
                          <span class="invalid-tip">该知识点已不存在，请重新生成路径</span>
                        </template>
                        <template v-else>
                          <el-tag v-if="point.difficulty" size="small" :type="difficultyTag(point.difficulty)" style="margin-right:4px">
                            {{ difficultyLabel(point.difficulty) }}
                          </el-tag>
                          <el-tag v-if="point.type" size="small" type="info">{{ point.type }}</el-tag>
                        </template>
                      </div>
                    </div>
                    <div v-if="!point._invalid" class="point-actions">
                      <el-tag v-if="point._quizScore !== undefined && point._quizScore !== null" :type="point._quizScore >= 80 ? 'success' : point._quizScore >= 60 ? 'primary' : 'warning'" size="small" effect="dark" style="margin-right:4px">
                        {{ point._quizScore }}分
                      </el-tag>
                      <el-icon v-if="point.video" color="#409eff" title="有视频"><VideoCamera /></el-icon>
                      <el-icon v-else color="#c0c4cc" title="无视频"><VideoCameraFilled /></el-icon>
                    </div>
                  </div>
                </div>
              </div>

              <div v-if="chapterGroups.length === 0" class="no-points">
                <el-empty description="暂无推荐知识点" :image-size="60" />
              </div>
            </div>
          </el-card>
        </div>
      </div>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getAssessment, updateAssessment } from '@/api/project/assessment'
import { getPoint, listPoint } from '@/api/project/point'
import { listPointChapters } from '@/api/project/chapter'
import { getHistory } from '@/api/project/history'
import { listRecord, updateRecord, addRecord } from '@/api/project/record'
import { listWatch, addWatch, updateWatch } from '@/api/project/watch'
import { chatWithAI } from '@/api/project/learn'
import { ArrowLeft, CaretRight, CircleCheck, VideoPlay, VideoCamera, VideoCameraFilled, Check, Refresh, WarningFilled } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const assessment = ref(null)
const chapterGroups = ref([])
const allPoints = ref([])
const records = ref([])
const videoRef = ref(null)
const currentVideo = ref(null)
const regenerating = ref(false)
const poolPointCount = ref(0)

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

  loading.value = true
  try {
    const res = await getAssessment(id)
    if (res.code === 200 && res.data) {
      assessment.value = res.data
      await parseAiPoints()
      await loadUserRecords()
      await buildPathTree()
      computeStaleInfo()
    } else {
      assessment.value = null
    }
  } catch {
    assessment.value = null
  }
  loading.value = false
}

// 从 aiResponse 中提取知识点列表，兼容两种格式：
//   1) 章节分组（新版）：[{subjectName, chapterNo, chapterTitle, points:[{pointId, pointName}]}]
//   2) 扁平列表（旧版）：[{subjectName, pointName, pointId}]
// 统一归一化为 [{pointId, pointName, subjectName, chapterNo, chapterTitle}]
function extractRawPoints(aiResponse) {
  if (!aiResponse) return []
  let parsed
  try {
    parsed = typeof aiResponse === 'string' ? JSON.parse(aiResponse) : aiResponse
  } catch {
    return []
  }
  if (!Array.isArray(parsed) || parsed.length === 0) return []

  const result = []
  parsed.forEach(item => {
    if (!item) return
    if (Array.isArray(item.points)) {
      item.points.forEach(p => {
        if (p && p.pointId) {
          result.push({
            pointId: p.pointId,
            pointName: p.pointName || p.name || '',
            subjectName: item.subjectName || '',
            chapterNo: item.chapterNo || '',
            chapterTitle: item.chapterTitle || ''
          })
        }
      })
    } else if (item.pointId) {
      result.push({
        pointId: item.pointId,
        pointName: item.pointName || item.name || '',
        subjectName: item.subjectName || '',
        chapterNo: item.chapterNo || '',
        chapterTitle: item.chapterTitle || ''
      })
    }
  })
  return result
}

// 解析 AI 返回的知识点列表
async function parseAiPoints() {
  const rawPoints = extractRawPoints(assessment.value.aiResponse)
  if (rawPoints.length === 0) return

  // 按 pointId 查完整信息；查不到则标记为失效，保留原条目以便提示用户
  const pointPromises = rawPoints.map(async (rp) => {
    try {
      const res = await getPoint(rp.pointId)
      if (res.code === 200 && res.data) {
        return {
          ...res.data,
          _aiSubjectName: rp.subjectName || '',
          _aiChapterNo: rp.chapterNo || '',
          _aiChapterTitle: rp.chapterTitle || '',
          _invalid: false
        }
      }
    } catch {}
    return {
      id: rp.pointId,
      name: rp.pointName || rp.name || `已失效知识点 #${rp.pointId}`,
      _aiSubjectName: rp.subjectName || '未分类',
      _aiChapterNo: rp.chapterNo || '',
      _aiChapterTitle: rp.chapterTitle || '',
      _invalid: true
    }
  })

  const results = await Promise.all(pointPromises)
  allPoints.value = results.filter(Boolean)
}

// 计算“学科知识点池”规模，用于体现路径覆盖率（知识点池变大导致旧路径偏少）
async function computeStaleInfo() {
  poolPointCount.value = 0
  const subjectIds = new Set()
  chapterGroups.value.forEach(g => {
    g._points.forEach(p => {
      if (p.subjectId) subjectIds.add(p.subjectId)
    })
  })
  const ids = Array.from(subjectIds)
  if (ids.length === 0) return

  try {
    const results = await Promise.all(ids.map(id =>
      listPoint({ subjectId: id, pageSize: 1, pageNum: 1 }).catch(() => null)
    ))
    poolPointCount.value = results.reduce((sum, res) => sum + (res?.total || 0), 0)
  } catch {
    poolPointCount.value = 0
  }
}

// 重新生成学习路径：复用 [2] 工作流（错题信息 + 当前知识点池），并覆盖原记录
async function regeneratePath() {
  if (regenerating.value) return
  if (!assessment.value) return

  const rawPoints = extractRawPoints(assessment.value.aiResponse)
  if (rawPoints.length === 0) {
    ElMessage.warning('该记录没有可用的学习路径数据，无法重新生成')
    return
  }

  regenerating.value = true
  try {
    // 1. 还原错题信息（诊断时写入 weakPoints）
    let wrongInfo = []
    if (assessment.value.weakPoints) {
      try {
        const parsed = JSON.parse(assessment.value.weakPoints)
        if (Array.isArray(parsed)) {
          wrongInfo = parsed.map(w => ({
            type: w.type,
            question: w.question,
            userAnswer: w.userAnswerText || w.userAnswer,
            correctAnswer: w.correctAnswerText || w.correctAnswer
          }))
        }
      } catch {}
    }

    // 2. 按原路径涉及的学科重建“可出题知识点池”（取当前最新数据）
    const subjectNames = Array.from(new Set(
      rawPoints.map(p => p.subjectName).filter(Boolean)
    ))
    let historySubjects = []
    if (assessment.value.historyId) {
      try {
        const historyRes = await getHistory(assessment.value.historyId)
        const history = historyRes.data || historyRes.rows?.[0]
        if (history?.selectedSubjects) {
          const parsed = JSON.parse(history.selectedSubjects)
          if (Array.isArray(parsed)) historySubjects = parsed
        }
      } catch {}
    }

    const scopedSubjects = subjectNames.length > 0
      ? historySubjects.filter(s => subjectNames.includes(s.name))
      : historySubjects
    const poolSubjects = scopedSubjects.length > 0 ? scopedSubjects : historySubjects

    let pointsPool = []
    const chapterIndex = new Map()   // pointId -> {subjectName, chapterNo, chapterTitle}
    const chapterStructMap = new Map() // chapterKey -> {subjectName, chapterNo, chapterTitle, points:[]}
    for (const s of poolSubjects) {
      if (!s?.id) continue
      try {
        const res = await listPoint({ subjectId: s.id, pageSize: 9999, pageNum: 1 })
        const rows = res.rows || res.data || []
        pointsPool = pointsPool.concat(rows.map(r => ({
          subjectName: s.name,
          pointName: r.name,
          pointId: r.id
        })))
      } catch {}
    }

    // 构建“章节结构”，让 AI 直接按章节输出路径
    if (pointsPool.length > 0) {
      try {
        const chapterRes = await listPointChapters(pointsPool.map(p => p.pointId))
        const chapterRows = chapterRes.data || chapterRes.rows || []
        chapterRows.forEach(r => {
          const subjectName = pointsPool.find(p => p.pointId === r.pointId)?.subjectName || ''
          const chapterNo = r.chapterNo || ''
          const chapterTitle = r.chapterTitle || ''
          if (!chapterNo && !chapterTitle) return
          const key = `${subjectName}::${chapterNo}`
          if (!chapterStructMap.has(key)) {
            chapterStructMap.set(key, {
              subjectName,
              chapterNo,
              chapterTitle,
              points: []
            })
          }
          chapterStructMap.get(key).points.push({
            pointId: r.pointId,
            pointName: r.pointName || ''
          })
          chapterIndex.set(r.pointId, { subjectName, chapterNo, chapterTitle })
        })
      } catch {}
    }

    // 知识点池附带所属章节，AI 才能把知识点归入章节
    const pointsPoolWithChapter = pointsPool.map(p => ({
      ...p,
      ...(chapterIndex.get(p.pointId) || {})
    }))
    const chapterStructure = Array.from(chapterStructMap.values())

    // 3. 调用 AI 工作流重新生成
    const aiRes = await chatWithAI({ message: `[2]
## 错题信息
${JSON.stringify(wrongInfo, null, 2)}

## 章节结构
${JSON.stringify(chapterStructure, null, 2)}

## 相关知识点
${JSON.stringify(pointsPoolWithChapter, null, 2)}` })

    if (aiRes.code !== 200 || !aiRes.data) {
      throw new Error(aiRes.msg || 'AI 服务返回异常')
    }
    const aiReply = aiRes.data.reply || ''
    const newRawPoints = extractRawPoints(aiReply)
    if (newRawPoints.length === 0) {
      throw new Error('AI 未生成有效的学习路径，请稍后重试')
    }

    // 4. 覆盖保存并重新加载（同步新的候选池大小，便于后续过期判定）
    const saveRes = await updateAssessment({
      id: assessment.value.id,
      aiResponse: aiReply,
      pointCount: pointsPool.length
    })
    if (saveRes.code !== 200) {
      throw new Error(saveRes.msg || '保存失败')
    }

    await loadData()
    ElMessage.success(`学习路径已重新生成（${newRawPoints.length} 个知识点）`)
  } catch (error) {
    console.error('重新生成学习路径失败:', error)
    ElMessage.error('重新生成失败：' + (error.message || '请稍后重试'))
  } finally {
    regenerating.value = false
  }
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

// 构建「章节 → 知识点」学习路径树
// 章节来源优先级：AI 直接给出的章节归属 > 库表 chapter_point 归属 > 学科名兜底
async function buildPathTree() {
  const recordMap = {}
  records.value.forEach(r => {
    recordMap[r.pointId] = r
  })

  // 批量取知识点所属章节（一次请求）；AI 已给出章节时仅用于补齐章节视频
  const chapterMap = {}
  const validIds = allPoints.value.filter(p => !p._invalid).map(p => p.id)
  if (validIds.length > 0) {
    try {
      const res = await listPointChapters(validIds)
      const rows = res.data || res.rows || []
      rows.forEach(r => {
        if (!chapterMap[r.pointId]) {
          chapterMap[r.pointId] = {
            key: `chapter:${r.chapterId}`,
            chapterNo: r.chapterNo,
            title: r.chapterTitle,
            video: r.chapterVideo
          }
        }
      })
    } catch { /* 章节查询失败则仅依赖 AI 章节或学科名 */ }
  }

  const groupMap = new Map()
  allPoints.value.forEach(p => {
    const dbChapter = chapterMap[p.id]
    let key
    let name
    let chapter

    if (p._aiChapterNo || p._aiChapterTitle) {
      // AI 明确给出的章节分组（章节顺序由 AI 路径决定）
      key = `aiChapter:${p._aiSubjectName}:${p._aiChapterNo}`
      name = `${p._aiChapterNo || ''} ${p._aiChapterTitle || ''}`.trim() || '未命名章节'
      chapter = { video: dbChapter?.video || '' }
    } else if (dbChapter) {
      key = dbChapter.key
      name = `${dbChapter.chapterNo || ''} ${dbChapter.title || ''}`.trim() || '未命名章节'
      chapter = { video: dbChapter.video || '' }
    } else {
      key = `subject:${p._aiSubjectName || '未分类'}`
      name = p._aiSubjectName || '未分类'
      chapter = null
    }

    if (!groupMap.has(key)) {
      groupMap.set(key, { key, groupName: name, chapter, points: [] })
    }
    groupMap.get(key).points.push(p)
  })

  const groups = Array.from(groupMap.values()).map(g => {
    const enriched = g.points.map(p => ({
      ...p,
      _status: recordMap[p.id]?.status || '0',
      _progress: recordMap[p.id]?.progress || 0,
      _quizScore: recordMap[p.id]?.pointQuizScore,
      _recordId: recordMap[p.id]?.id || null
    }))
    const done = enriched.filter(p => p._status === '2').length
    const validCount = enriched.filter(p => !p._invalid).length
    return {
      groupName: g.groupName,
      _isChapter: !!g.chapter,
      _chapterVideo: g.chapter?.video || '',
      _points: enriched,
      _expanded: false,
      _doneCount: done,
      _completionRate: validCount > 0 ? Math.round((done / validCount) * 100) : 0
    }
  })

  // 章节路径默认展开第一个，便于直接开始学习
  if (groups.length > 0) {
    groups[0]._expanded = true
  }
  chapterGroups.value = groups
}

// ====== 统计 ======

const validPoints = computed(() => allPoints.value.filter(p => !p._invalid))
const invalidPoints = computed(() => allPoints.value.filter(p => p._invalid))

// 路径涉及的分组数（章节优先，无章节数据时为学科数）
const chapterCount = computed(() => chapterGroups.value.length)
const hasChapterPath = computed(() => chapterGroups.value.some(g => g._isChapter))

// 生成学习路径时的候选知识点池大小（旧记录可能为空）
const generatedPointCount = computed(() => {
  const v = assessment.value?.pointCount
  if (v === null || v === undefined || v === '') return null
  return Number(v)
})

// 当前学科知识点池相对生成时的增量（仅在两者都有值时有效）
const poolGrowth = computed(() => {
  if (generatedPointCount.value === null) return null
  if (poolPointCount.value === 0) return null
  return poolPointCount.value - generatedPointCount.value
})

const learnedCount = computed(() => {
  const pointIds = new Set(validPoints.value.map(p => p.id))
  return records.value.filter(r => r.status === '2' && pointIds.has(r.pointId)).length
})

const learningCount = computed(() => {
  const pointIds = new Set(validPoints.value.map(p => p.id))
  return records.value.filter(r => r.status === '1' && pointIds.has(r.pointId)).length
})

const profilePercent = computed(() => {
  if (validPoints.value.length === 0) return 0
  return Math.round((learnedCount.value / validPoints.value.length) * 100)
})

const profileColor = computed(() => {
  if (profilePercent.value >= 80) return '#67c23a'
  if (profilePercent.value >= 50) return '#409eff'
  if (profilePercent.value >= 20) return '#e6a23c'
  return '#f56c6c'
})

const avgScore = computed(() => {
  // 获取当前测评的所有知识点ID列表
  let pointIds = new Set()
  try {
    const aiResponse = assessment.value?.aiResponse
    if (aiResponse) {
      const points = typeof aiResponse === 'string' ? JSON.parse(aiResponse) : aiResponse
      if (Array.isArray(points)) {
        points.forEach(p => {
          if (p.pointId) pointIds.add(String(p.pointId))
        })
      }
    }
  } catch {}

  // 筛选属于当前测评且有测评分数的记录
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

function toggleSubject(idx) {
  const item = chapterGroups.value[idx]
  if (item) item._expanded = !item._expanded
}

// 播放整章视频（章节级视频，作为该章知识点的合集入口）
function selectChapterVideo(group) {
  if (!group?._chapterVideo) return
  const path = group._chapterVideo
  currentVideo.value = {
    pointId: null,
    pointName: group.groupName,
    subjectName: group.groupName,
    videoUrl: path.startsWith('http') ? path : import.meta.env.VITE_APP_BASE_API + path
  }
  group._expanded = true
  if (videoRef.value) {
    videoRef.value.load()
  }
}

async function selectPoint(point, group) {
  if (!point || point._invalid) return
  let videoUrl = ''
  if (point.video) {
    videoUrl = point.video.startsWith('http') ? point.video : import.meta.env.VITE_APP_BASE_API + point.video
  }

  currentVideo.value = {
    pointId: point.id,
    pointName: point.name,
    subjectName: group?.groupName || '',
    videoUrl: videoUrl
  }

  if (videoRef.value && videoUrl) {
    videoRef.value.load()
  }

  await ensureLearnRecord(point)
}

async function ensureLearnRecord(point) {
  const existing = records.value.find(r => String(r.pointId) === String(point.id))
  if (!existing) {
    try {
      const res = await addRecord({
        pointId: point.id,
        historyId: assessment.value?.historyId || null,
        status: '1',
        progress: 0
      })
      if (res.code === 200) {
        await loadUserRecords()
        await buildPathTree()
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
            await buildPathTree()
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
      await buildPathTree()
    } catch (e) {
      console.error('更新学习记录失败', e)
    }
  }
}

// 视频进度
let watchTimer = null
function onVideoTimeUpdate() {
  if (!videoRef.value || !currentVideo.value) return
  // 章节级视频不记录知识点学习进度
  if (currentVideo.value.pointId === null || currentVideo.value.pointId === undefined) return
  const v = videoRef.value
  const progress = v.duration ? Math.round((v.currentTime / v.duration) * 100) : 0

  if (!watchTimer) {
    // 排程时快照 pointId：回调在 5s 后执行，期间用户可能已切到别的知识点，
    // 若那时再读 currentVideo 会把 A 的进度写到 B 上
    const pointId = currentVideo.value.pointId
    const scheduledProgress = progress
    watchTimer = setTimeout(async () => {
      watchTimer = null
      if (!currentVideo.value || String(currentVideo.value.pointId) !== String(pointId)) return
      const latest = videoRef.value
      const currentProgress = latest && latest.duration
        ? Math.round((latest.currentTime / latest.duration) * 100)
        : scheduledProgress
      const currentSeconds = latest ? Math.round(latest.currentTime) : 0
      const existing = records.value.find(r => String(r.pointId) === String(pointId))

      if (existing && existing.status !== '2') {
        try {
          await updateRecord({ id: existing.id, progress: currentProgress })
        } catch {}
      }

      try {
        const watchRes = await listWatch({ pointId, pageSize: 1, pageNum: 1 })
        const watchList = watchRes.rows || []
        const existingWatch = watchList.find(w => String(w.pointId) === String(pointId))
        if (existingWatch) {
          await updateWatch({
            id: existingWatch.id,
            watchProgress: currentProgress,
            watchDuration: currentSeconds,
            lastWatchTime: new Date().toISOString().slice(0, 19).replace('T', ' '),
            completed: currentProgress >= 95 ? '1' : existingWatch.completed
          })
        } else {
          await addWatch({
            pointId,
            watchProgress: currentProgress,
            watchDuration: currentSeconds,
            completed: currentProgress >= 95 ? '1' : '0'
          })
        }
      } catch {}
    }, 5000)
  }
}

async function onVideoEnded() {
  if (!currentVideo.value) return
  // 章节级视频播放结束不做知识点完成的记录与测评（等待用户选择具体知识点）
  if (currentVideo.value.pointId === null || currentVideo.value.pointId === undefined) return
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
      await buildPathTree()
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
    const record = records.value.find(r => String(r.pointId) === String(currentVideo.value?.pointId))
    if (record && record.id) {
      const existingScore = record.pointQuizScore !== null && record.pointQuizScore !== undefined ? Number(record.pointQuizScore) : -1
      if (score > existingScore) {
        try {
          await updateRecord({ id: record.id, pointQuizScore: score })
          await loadUserRecords()
          await buildPathTree() // 重新构建章节路径树，刷新显示分数
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

// 路由切走后回调仍会触发，必须清理，否则会对已卸载组件发请求
onBeforeUnmount(() => {
  if (watchTimer) {
    clearTimeout(watchTimer)
    watchTimer = null
  }
})
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

.result-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.header-time {
  font-size: 12px;
  color: #909399;
}

.stale-alert {
  margin-bottom: 16px;
}

.stale-alert :deep(.el-alert__title) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  line-height: 1.6;
}

.stale-text {
  flex: 1;
}

.points-count-hint {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}

.chapter-badge {
  margin-right: 2px;
  flex-shrink: 0;
}

.group-header-right {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.subject-group-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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

.learn-layout {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
}

.learn-video { flex: 3; min-width: 0; }

.learn-points {
  flex: 2;
  min-width: 300px;
  display: flex;
  flex-direction: column;
}

.video-card { height: 100%; }

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

.points-card { height: 100%; }

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

.subject-points-list::-webkit-scrollbar { width: 4px; }
.subject-points-list::-webkit-scrollbar-thumb { background: #d0d5dd; border-radius: 2px; }

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

.subject-group-header:hover { background: #ecf5ff; }

.subject-group-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  flex: 1;
  min-width: 0;
}

.expand-rotate { transform: rotate(90deg); transition: transform 0.2s; }

.point-list { border-top: 1px solid #ebeef5; }

.point-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s;
  border-bottom: 1px solid #f2f2f2;
}

.point-item:last-child { border-bottom: none; }
.point-item:hover { background: #f0f5ff; }

.point-item.active {
  background: #ecf5ff;
  border-left: 3px solid #409eff;
  padding-left: 9px;
}

.point-item.invalid {
  cursor: not-allowed;
  background: #fef7f7;
}

.point-item.invalid:hover {
  background: #fef7f7;
}

.point-item.invalid .point-name {
  color: #b1b3b8;
  text-decoration: line-through;
}

.invalid-tip {
  font-size: 12px;
  color: #f56c6c;
  margin-left: 4px;
}

.status-invalid {
  color: #f56c6c;
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

.status-pending-dot { background: #c0c4cc; }

.point-info { flex: 1; min-width: 0; }

.point-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.point-meta { margin-top: 4px; display: flex; align-items: center; flex-wrap: wrap; }

.point-actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}

.no-points { padding: 20px 0; }

.result-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 400px;
  gap: 16px;
}

@media (max-width: 900px) {
  .learn-layout { flex-direction: column; }
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
