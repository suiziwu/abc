<template>
  <div class="app-container">
    <!-- 查询区 -->
    <el-card shadow="never" class="query-card">
      <div class="query-header">
        <h3>
          <svg-icon icon-class="education" />
          AI 学习路径生成
        </h3>
        <span class="query-desc">输入目标岗位及职责描述，AI将为你推荐匹配的学习路径</span>
      </div>
      <el-input
        v-model="inputMessage"
        type="textarea"
        :rows="4"
        placeholder="请输入目标岗位名称及职责描述，如：Java后端开发工程师，负责微服务架构设计和API开发..."
        :disabled="loading"
        resize="none"
      />
      <div class="query-actions">
        <div class="suggest-questions">
          <span class="suggest-label">示例：</span>
          <el-tag
            v-for="(q, idx) in suggestQuestions"
            :key="idx"
            class="suggest-tag"
            @click="inputMessage = q"
            effect="plain"
            round
            size="small"
          >
            {{ q }}
          </el-tag>
        </div>
        <div class="query-buttons">
          <input
            ref="audioFileInput"
            class="audio-file-input"
            type="file"
            accept=".wav,audio/wav"
            @change="handleAudioFile"
          />
          <el-button
            :icon="Upload"
            :loading="asrLoading"
            :disabled="loading"
            size="large"
            title="上传 WAV 音频，仅做文本提取"
            @click="triggerAudioUpload"
          >
            音频识别
          </el-button>
          <el-button type="primary" :loading="loading" @click="sendMessage" :icon="Promotion" size="large">
            {{ loading ? 'AI 分析中...' : '生成学习路径' }}
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 人才画像入口 -->
    <el-card shadow="never" class="profile-card">
      <template #header>
        <div class="section-header">
          <span><svg-icon icon-class="chart" /> 人才画像 · 学习总览</span>
          <div>
            <el-button text type="primary" :icon="Refresh" @click="loadProfile" :loading="profileLoading" style="margin-right:8px">刷新</el-button>
            <el-button type="primary" :icon="TrendCharts" @click="goToProfile">查看完整画像</el-button>
          </div>
        </div>
      </template>

      <div v-loading="profileLoading" class="profile-body">
        <div class="profile-quick-grid">
          <div class="quick-item">
            <div class="quick-icon done"><svg-icon icon-class="checkbox" /></div>
            <div class="quick-info">
              <span class="quick-value">{{ profileStats.doneCount }}</span>
              <span class="quick-label">已学完</span>
            </div>
          </div>
          <div class="quick-item">
            <div class="quick-icon learning"><svg-icon icon-class="edit" /></div>
            <div class="quick-info">
              <span class="quick-value">{{ profileStats.learningCount }}</span>
              <span class="quick-label">学习中</span>
            </div>
          </div>
          <div class="quick-item">
            <div class="quick-icon pending"><svg-icon icon-class="time" /></div>
            <div class="quick-info">
              <span class="quick-value">{{ Math.max(0, profileStats.totalPoints - profileStats.doneCount - profileStats.learningCount) }}</span>
              <span class="quick-label">待学</span>
            </div>
          </div>
          <div class="quick-item">
            <div class="quick-icon total"><svg-icon icon-class="education" /></div>
            <div class="quick-info">
              <span class="quick-value">{{ profileStats.totalPoints }}</span>
              <span class="quick-label">总知识点</span>
            </div>
          </div>
          <div class="quick-item quick-item-wide">
            <div class="quick-label">综合完成度</div>
            <el-progress
              :percentage="profileStats.overallPercent"
              :stroke-width="14"
              :text-inside="true"
              :color="profileStats.overallPercent >= 80 ? '#67c23a' : profileStats.overallPercent >= 50 ? '#409eff' : '#e6a23c'"
            />
          </div>
        </div>

        <CourseLearningSection
          v-if="learningCourses.length > 0"
          class="profile-course-section"
          title="学习中课程"
          subtitle="继续上次的学习进度，点击课程卡片进入课程学习"
          :courses="learningCourses"
          embedded
          @open="openCourse"
        />
        <div v-else-if="!profileLoading && profileStats.totalPoints > 0" class="learning-empty">
          <svg-icon icon-class="education" />
          <span>暂无学习中课程，开启知识架构管理的「学习模式」后会自动同步</span>
        </div>
      </div>
    </el-card>

    <!-- AI 薄弱知识点诊断 -->
    <el-card shadow="never" class="assess-card">
      <template #header>
        <div class="section-header">
          <div class="section-header-left">
            <span><svg-icon icon-class="edit" /> AI 薄弱知识点诊断</span>
            <el-tag type="warning" effect="dark" size="small">BETA</el-tag>
          </div>
          <el-button v-if="historyList.length > 0" type="primary" :icon="Aim" :loading="assessLoading" @click="showDiagnosisDialog" plain>
            {{ assessLoading ? 'AI 组卷中...' : '开始薄弱诊断' }}
          </el-button>
        </div>
      </template>

      <div v-if="historyList.length === 0" class="assess-empty">
        <span>暂无岗位记录，请先在上方生成了学习路径后再进行薄弱知识点诊断</span>
      </div>
      <template v-else>
        <div class="assess-info">
          <div class="assess-desc">
            <svg-icon icon-class="question" />
            根据你的学习记录，AI将针对匹配岗位自动出题，检测你对已学知识的掌握程度，找出薄弱环节
          </div>
          <div class="assess-target">
            <span class="target-label">诊断目标岗位：</span>
            <el-select v-model="assessHistoryId" placeholder="请选择要诊断的岗位" filterable style="width: 400px">
              <el-option
                v-for="item in historyList"
                :key="item.id"
                :label="historyOptionLabel(item)"
                :value="item.id"
              />
            </el-select>
          </div>
        </div>

        <el-divider content-position="left">历史诊断记录</el-divider>
        <div v-if="assessList.length === 0" class="assess-empty">
          <el-empty description="暂无诊断记录" :image-size="60" />
        </div>
        <el-table v-else :data="assessList" style="width: 100%" @row-click="handleViewAssess">
          <el-table-column label="诊断目标" min-width="200">
            <template #default="scope">
              <span class="assess-title">{{ scope.row.assessTitle || scope.row.historyId }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120" align="center">
            <template #default="scope">
              <el-tag :type="assessStatusTag(scope.row.status)" size="small" effect="dark">
                {{ assessStatusLabel(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="得分" width="90" align="center">
            <template #default="scope">
              <span v-if="scope.row.status === '2'" class="assess-score" :class="scoreColor(scope.row.totalScore)">{{ scope.row.totalScore }}分</span>
              <span v-else class="text-secondary">-</span>
            </template>
          </el-table-column>
          <el-table-column label="诊断时间" width="170" align="center">
            <template #default="scope">
              <span class="text-secondary">{{ scope.row.createTime }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center">
            <template #default="scope">
              <!-- <el-button link type="primary" :icon="View" @click.stop="handleViewAssess(scope.row)">查看</el-button> -->
              <el-button v-if="scope.row.aiResponse || String(scope.row.status) === '2'" link type="success" :icon="Promotion" @click.stop="goToAiResult(scope.row)">学习</el-button>
              <el-button link type="danger" :icon="Delete" @click.stop="handleDeleteAssess(scope.row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <pagination
          v-show="assessTotal > 0"
          :total="assessTotal"
          v-model:page="assessQuery.pageNum"
          v-model:limit="assessQuery.pageSize"
          @pagination="loadAssessList"
        />
      </template>
    </el-card>

    <!-- 历史记录 -->
    <el-card shadow="never" class="history-card">
      <template #header>
        <div class="history-header">
          <span><svg-icon icon-class="time" /> 查询历史</span>
          <el-button text type="primary" :icon="Delete" @click="handleClear" v-if="historyList.length > 0">清空记录</el-button>
        </div>
      </template>

      <div v-if="historyList.length === 0" class="history-empty">
        <el-empty description="暂无查询记录" :image-size="80" />
      </div>

      <el-table v-else :data="historyList" style="width: 100%" @row-click="handleView">
        <el-table-column label="目标岗位" min-width="300">
          <template #default="scope">
            <div class="history-cell">
              <div class="history-input">{{ scope.row.userInput }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分析结果" min-width="120">
          <template #default="scope">
            <el-tag
              :type="hasSelectedSubjects(scope.row) ? 'success' : 'warning'"
              size="small"
              effect="plain"
            >
              {{ hasSelectedSubjects(scope.row) ? '已匹配' : '未匹配' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="查询时间" width="180" align="center">
          <template #default="scope">
            <span class="history-time">{{ scope.row.createTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="scope">
            <el-button link type="primary" :icon="View" @click.stop="handleView(scope.row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 诊断范围选择弹窗 -->
    <el-dialog v-model="diagnosisDialogVisible" title="选择诊断范围" width="420px" :close-on-click-modal="false">
      <el-form label-width="0">
        <el-form-item>
          <el-select v-model="selectedDiagnosisSubject" placeholder="请选择诊断范围" style="width:100%">
            <el-option label="综合诊断（所有学科）" value="" />
            <el-option v-for="s in diagnosisSubjects" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="diagnosisDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assessLoading" @click="confirmDiagnosis">开始诊断</el-button>
      </template>
    </el-dialog>

    <!-- AI原始返回结果弹窗（查看历史记录/学习路径用） -->
    <el-dialog v-model="rawDialogVisible" :title="rawDialogTitle" width="800px" destroy-on-close>
      <div v-loading="rawDialogLoading">
        <pre class="raw-response">{{ rawResponse }}</pre>
      </div>
      <template #footer>
        <el-button @click="rawDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 做题弹窗 -->
    <el-dialog v-model="quizDialogVisible" :title="quizTitle" width="800px" destroy-on-close :close-on-click-modal="false">
      <!-- Loading -->
      <div v-if="quizStep === 'loading'" class="quiz-loading" v-loading="true">
        <span>{{ quizTitle }}</span>
      </div>

      <!-- 做题界面 -->
      <div v-if="quizStep === 'quiz'" class="quiz-content">
        <div class="quiz-progress">
          <div class="quiz-progress-text">已完成 {{ answeredCount }} / {{ questions.length }} 题</div>
          <el-progress :percentage="answeredPercent" :stroke-width="8" />
        </div>

        <div class="quiz-body">
          <div
            v-for="(q, idx) in questions"
            :key="q.id"
            class="quiz-question"
            :class="{ 'is-answered': userAnswers[q.id] !== undefined }"
          >
            <div class="question-header">
              <span class="question-number">第 {{ idx + 1 }} 题</span>
              <el-tag :type="questionTypeTag(q.type)" size="small" effect="dark">
                {{ q.type }}
              </el-tag>
            </div>
            <div class="question-text">{{ q.question }}</div>

            <!-- 单选 / 判断 -->
            <template v-if="q.type === '单选' || q.type === '判断'">
              <div class="options-list">
                <div
                  v-for="(opt, oIdx) in q.options"
                  :key="oIdx"
                  class="option-item"
                  :class="{ selected: userAnswers[q.id] === oIdx }"
                  @click="selectOption(q.id, oIdx)"
                >
                  <span class="option-radio" :class="{ checked: userAnswers[q.id] === oIdx }">
                    <span v-if="userAnswers[q.id] === oIdx" class="radio-dot"></span>
                  </span>
                  <span class="option-text">{{ opt }}</span>
                </div>
              </div>
            </template>

            <!-- 多选 -->
            <template v-else-if="q.type === '多选'">
              <div class="options-list">
                <div
                  v-for="(opt, oIdx) in q.options"
                  :key="oIdx"
                  class="option-item"
                  :class="{ selected: isMultiSelected(q.id, oIdx) }"
                  @click="toggleMultiOption(q.id, oIdx)"
                >
                  <span class="option-checkbox" :class="{ checked: isMultiSelected(q.id, oIdx) }">
                    <el-icon v-if="isMultiSelected(q.id, oIdx)" color="#fff" :size="12"><Check /></el-icon>
                  </span>
                  <span class="option-text">{{ opt }}</span>
                </div>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 结果界面 -->
      <div v-if="quizStep === 'result'" class="result-content">
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
                <el-tag :type="questionTypeTag(item.type)" size="small" effect="plain">{{ item.type }}</el-tag>
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
          <span class="quiz-footer-hint">{{ unansweredCount > 0 ? `还有 ${unansweredCount} 题未作答` : '全部已作答' }}</span>
          <el-button type="primary" :loading="submitting" @click="handleSubmitQuiz" :disabled="unansweredCount > 0">
            {{ submitting ? '提交分析中...' : '提交答案并分析薄弱点' }}
          </el-button>
        </div>
        <div v-if="quizStep === 'result'" class="quiz-footer">
          <el-button @click="closeQuiz">关闭</el-button>
          <el-button type="primary" :loading="savingAndGenerating" @click="handleSaveResult">保存并生成学习路径</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { chatWithAI } from '@/api/project/learn'
import { transcribeAudio } from '@/api/project/asr'
import { listSubject } from '@/api/project/subject'
import { listPoint } from '@/api/project/point'
import { listHistory, addHistory, delHistory } from '@/api/project/history'
import { listPosition, addPosition } from '@/api/project/position'
import { listRecord } from '@/api/project/record'
import { listAssessment, addAssessment, getAssessment, delAssessment } from '@/api/project/assessment'
import { Delete, Promotion, View, Refresh, TrendCharts, Aim, Check, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CourseLearningSection from './components/CourseLearningSection.vue'
import { buildLearningCourses } from './courseLearning'

const router = useRouter()
const route = useRoute()

const inputMessage = ref('')
const loading = ref(false)
const asrLoading = ref(false)
const audioFileInput = ref(null)
const historyList = ref([])
const total = ref(0)
const queryParams = ref({
  pageNum: 1,
  pageSize: 10
})

const profileLoading = ref(false)
const profileStats = ref({
  totalPoints: 0,
  doneCount: 0,
  learningCount: 0,
  overallPercent: 0,
  positionList: []
})
const learningCourses = ref([])

// ====== 薄弱知识点诊断 ======
const assessHistoryId = ref(null)
const assessList = ref([])
const assessTotal = ref(0)
const assessQuery = ref({
  pageNum: 1,
  pageSize: 10
})
const assessLoading = ref(false)

// 原始返回弹窗
const rawDialogVisible = ref(false)
const rawDialogLoading = ref(false)
const rawDialogTitle = ref('AI 薄弱诊断原始返回')
const rawResponse = ref('')
const pendingAssessResult = ref(null)

const suggestQuestions = [
  'Java后端开发工程师',
  '前端开发工程师',
  '数据分析师',
  '算法工程师'
]

// ====== 做题弹窗状态 ======
const quizDialogVisible = ref(false)
const quizStep = ref('loading') // loading / quiz / result
const quizTitle = ref('薄弱知识诊断')
const questions = ref([])
const userAnswers = ref({})
const submitting = ref(false)
const savingAndGenerating = ref(false)
const quizResult = ref({
  score: 0,
  wrongAnswers: [],
  overall_analysis: ''
})
const pendingAssessId = ref(null)
const knowledgePoints = ref([])
const parsedAiPoints = ref([])
const savedAssessmentId = ref(null)

// 诊断范围选择
const diagnosisDialogVisible = ref(false)
const diagnosisSubjects = ref([])
const selectedDiagnosisSubject = ref('')

const answeredCount = computed(() => questions.value.filter(q => userAnswers.value[q.id] !== undefined).length)
const unansweredCount = computed(() => questions.value.length - answeredCount.value)
const answeredPercent = computed(() => questions.value.length > 0 ? Math.round((answeredCount.value / questions.value.length) * 100) : 0)

onMounted(() => {
  // 从知识点学习模式跳转过来时，预填提示语（不自动发送）
  const { subjectName, pointName } = route.query
  if (pointName) {
    const subjectPart = subjectName ? `「${subjectName}」中的` : ''
    inputMessage.value = `我想学习${subjectPart}知识点「${pointName}」，请为我生成学习路径`
  } else if (subjectName) {
    inputMessage.value = `我想学习「${subjectName}」相关知识点，请为我生成学习路径`
  }
  getList()
  loadProfile()
  loadAssessList()
})

// 从其他页面返回时刷新画像与课程卡片，保证「学习模式」写入的数据能及时展示
let profileActivated = false
onActivated(() => {
  if (profileActivated) {
    getList()
    loadProfile()
    loadAssessList()
  }
  profileActivated = true
})

// 查询历史列表
function getList() {
  return listHistory(queryParams.value).then(res => {
    historyList.value = res.rows || []
    total.value = res.total || 0
  })
}

// 判断是否有选中的学科
function hasSelectedSubjects(row) {
  if (!row.selectedSubjects) return false
  try {
    const subjects = JSON.parse(row.selectedSubjects)
    return Array.isArray(subjects) && subjects.length > 0
  } catch {
    return false
  }
}

// 加载人才画像数据
async function loadProfile() {
  profileLoading.value = true
  try {
    // 获取学习记录、岗位历史、学科与知识点，用于画像统计和课程卡片
    const [recordRes, historyRes, subjectRes, pointRes] = await Promise.all([
      listRecord({ pageSize: 9999, pageNum: 1 }),
      listHistory({ pageSize: 9999, pageNum: 1 }),
      listSubject({ pageSize: 9999, pageNum: 1 }),
      listPoint({ pageSize: 9999, pageNum: 1 })
    ])
    const allRecords = recordRes.rows || []
    const allHistory = historyRes.rows || []
    const allSubjects = subjectRes.rows || subjectRes.data || []
    const allPoints = pointRes.rows || pointRes.data || []

    const doneCount = allRecords.filter(r => r.status === '2').length
    const learningCount = allRecords.filter(r => r.status === '1').length

    const pointsBySubject = new Map()
    allPoints.forEach(point => {
      if (!point || point.id === undefined || point.id === null) return
      const subjectKey = String(point.subjectId)
      if (!pointsBySubject.has(subjectKey)) pointsBySubject.set(subjectKey, [])
      pointsBySubject.get(subjectKey).push(point)
    })

    let totalSubjectPoints = 0
    const positionList = []

    for (const h of allHistory) {
      if (!h.selectedSubjects) continue
      let subjects = []
      try { subjects = JSON.parse(h.selectedSubjects) || [] } catch { continue }
      if (!Array.isArray(subjects) || subjects.length === 0) continue

      let positionTotal = 0
      for (const s of subjects) {
        positionTotal += (pointsBySubject.get(String(s.id)) || []).length
      }
      totalSubjectPoints += positionTotal

      // 该岗位历史记录关联的学习记录完成数
      const historyRecords = allRecords.filter(r => String(r.historyId) === String(h.id))
      const historyDone = historyRecords.filter(r => r.status === '2').length

      if (positionTotal > 0) {
        positionList.push({
          id: h.id,
          userInput: h.userInput,
          totalCount: positionTotal,
          doneCount: historyDone,
          percent: Math.round((historyDone / positionTotal) * 100)
        })
      }
    }

    // 去重：如果多个历史记录选了相同岗位，保留最新的
    const seen = new Set()
    const uniquePositions = positionList.filter(p => {
      const key = p.userInput.trim().toLowerCase()
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })

    profileStats.value = {
      totalPoints: totalSubjectPoints || 0,
      doneCount,
      learningCount,
      overallPercent: totalSubjectPoints > 0 ? Math.round((doneCount / totalSubjectPoints) * 100) : 0,
      positionList: uniquePositions.slice(0, 10)
    }
    learningCourses.value = buildLearningCourses(allRecords, allSubjects, allPoints)
  } catch (e) {
    console.error('加载人才画像失败', e)
  }
  profileLoading.value = false
}

// 从用户输入中解析岗位名：取第一个非空行，trim 后截断 200 字
function parsePositionName(text) {
  const firstLine = (text || '').split(/\r?\n/).map(line => line.trim()).find(line => line)
  return (firstLine || '').slice(0, 200)
}

// 确保岗位存在并返回岗位 id：失败时返回 null，不阻断学习路径生成
async function ensurePosition(text) {
  try {
    const name = parsePositionName(text)
    if (!name) return null

    // 先按名称查询，找到名称完全相等的岗位直接复用
    const queryRes = await listPosition({ name, pageSize: 50, pageNum: 1 })
    const rows = queryRes.rows || []
    const matched = rows.find(item => item.name === name)
    if (matched && matched.id) return matched.id

    // 未找到则新增岗位（新增接口不返回 id）
    await addPosition({ name, description: (text || '').slice(0, 1000) })

    // 重新查询，按 id 倒序取同名记录拿回新增的岗位 id
    const againRes = await listPosition({ name, pageSize: 50, pageNum: 1 })
    const created = (againRes.rows || [])
      .slice()
      .sort((a, b) => Number(b.id) - Number(a.id))
      .find(item => item.name === name)
    return created ? created.id : null
  } catch (error) {
    console.error('岗位处理失败，学习路径生成继续:', error)
    return null
  }
}

// 发送消息
async function sendMessage() {
  const text = inputMessage.value.trim()
  if (!text || loading.value) return

  loading.value = true

  try {
    // 1. 从数据库拉取学科列表
    const subjectRes = await listSubject({ pageSize: 9999, pageNum: 1 })
    let subjectData = []
    if (subjectRes.code === 200 && subjectRes.rows) {
      subjectData = subjectRes.rows.map(s => ({ id: s.id, name: s.name }))
    } else if (Array.isArray(subjectRes)) {
      subjectData = subjectRes.map(s => ({ id: s.id, name: s.name }))
    }

    // 2. 构建 Prompt 模板（加 [0] 前缀区分业务类型）
    const promptTemplate = `[0]
# Input Data

## 目标岗位
${text}
*(注：此处包含用户输入的岗位名称及岗位职责/描述)*

## 学科池（可选项）
${JSON.stringify(subjectData, null, 2)}
*(注：此处包含从数据库拉取的学科ID和名称)*`

    // 3. 调用AI工作流
    const res = await chatWithAI({ message: promptTemplate })

    if (res.code === 200 && res.data) {
      const reply = res.data.reply || '{}'

      // 4. 解析AI返回的JSON
      let parsedReply
      try {
        parsedReply = typeof reply === 'string' ? JSON.parse(reply) : reply
      } catch {
        parsedReply = { selected_subjects: [], overall_analysis: reply }
      }

      const selectedSubjects = parsedReply.selected_subjects || []
      const overallAnalysis = parsedReply.overall_analysis || ''

      // 5. 保存到历史记录（先确保岗位存在，并把岗位 id 一并写入）
      const positionId = await ensurePosition(text)
      const saveRes = await addHistory({
        userInput: text,
        subjectContext: JSON.stringify(subjectData),
        selectedSubjects: JSON.stringify(selectedSubjects),
        overallAnalysis: overallAnalysis,
        rawResponse: typeof reply === 'string' ? reply : JSON.stringify(reply),
        positionId: positionId,
        status: '0'
      })

      if (saveRes.code === 200) {
        ElMessage.success('学习路径生成成功')
      }

      // 6. 等待列表刷新后再取最新记录的ID跳转
      await getList()
      loadProfile()
      if (historyList.value.length > 0) {
        router.push({ name: 'LearnResult', params: { id: historyList.value[0].id } })
      }
    } else {
      ElMessage.error(res.msg || 'AI服务返回异常')
    }
  } catch (error) {
    console.error('AI对话失败:', error)
    ElMessage.error('网络或服务异常：' + (error.message || ''))
  }

  loading.value = false
}

// 查看历史记录
function handleView(row) {
  router.push({ name: 'LearnResult', params: { id: row.id } })
}

// 查看完整人才画像
function goToProfile() {
  if (profileStats.value.totalPoints === 0) {
    ElMessage.warning('暂无学习数据，请先生成学习路径')
    return
  }
  router.push({ name: 'LearnProfile' })
}

// 点击学习中课程卡片，进入课程学习页
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

// 从历史诊断记录跳转到 AI 学习页面
function goToAiResult(row) {
  router.push({ name: 'LearnAiResult', params: { id: row.id } })
}

// ====== 薄弱知识点诊断函数 ======

// 岗位下拉项：同名岗位追加生成时间，方便区分
function historyOptionLabel(item) {
  const title = item?.userInput || `记录 ${item?.id ?? ''}`
  const time = item?.createTime ? String(item.createTime).replace('T', ' ').slice(0, 19) : ''
  return time ? `${title}（${time}）` : title
}

// 加载诊断记录列表
function loadAssessList() {
  listAssessment(assessQuery.value).then(res => {
    assessList.value = res.rows || []
    assessTotal.value = res.total || 0
  })
}

// 显示诊断范围选择弹窗
function showDiagnosisDialog() {
  if (!assessHistoryId.value) {
    ElMessage.warning('请先选择要诊断的岗位')
    return
  }

  const history = historyList.value.find(h => String(h.id) === String(assessHistoryId.value))
  if (!history) {
    ElMessage.warning('未找到选中的岗位记录')
    return
  }

  let subjects = []
  try { subjects = JSON.parse(history.selectedSubjects || '[]') } catch {}

  diagnosisSubjects.value = subjects
  selectedDiagnosisSubject.value = ''
  diagnosisDialogVisible.value = true
}

// 确认诊断范围并开始
async function confirmDiagnosis() {
  diagnosisDialogVisible.value = false
  await handleStartAssess(selectedDiagnosisSubject.value)
}

// 开始诊断 - AI出题 → 做题
async function handleStartAssess(subjectFilter) {
  if (!assessHistoryId.value) {
    ElMessage.warning('请先选择要诊断的岗位')
    return
  }

  assessLoading.value = true
  quizTitle.value = '薄弱知识诊断'
  quizStep.value = 'loading'
  quizDialogVisible.value = true

  try {
    // 找到选中的历史记录
    const history = historyList.value.find(h => String(h.id) === String(assessHistoryId.value))
    if (!history) {
      throw new Error('未找到选中的岗位记录')
    }

    // 解析学科数据
    let subjects = []
    try { subjects = JSON.parse(history.selectedSubjects || '[]') } catch {}

    // 根据筛选条件过滤学科
    if (subjectFilter) {
      subjects = subjects.filter(s => String(s.id) === String(subjectFilter))
    }

    // 查询各学科下的知识点列表
    let allPoints = []
    for (const s of subjects) {
      try {
        const pointRes = await listPoint({ subjectId: s.id, pageSize: 9999, pageNum: 1 })
        const rows = pointRes.rows || []
        allPoints = allPoints.concat(rows.map(r => ({ subjectName: s.name, pointName: r.name, pointId: r.id })))
      } catch { /* 单个学科查询失败不影响整体 */ }
    }
    knowledgePoints.value = allPoints

    // 构建 Prompt
    const promptTemplate = `[1]
## 目标岗位
${history.userInput}

## 匹配学科及知识点范围
${JSON.stringify(subjects, null, 2)}

## 可出题的知识点列表
${JSON.stringify(allPoints, null, 2)}`

    // 调用AI
    const res = await chatWithAI({ message: promptTemplate })

    if (res.code === 200 && res.data) {
      const reply = res.data.reply || '{}'

      // 解析AI返回的JSON
      let parsedReply
      try {
        parsedReply = typeof reply === 'string' ? JSON.parse(reply) : reply
      } catch {
        // 尝试从返回文本中提取JSON
        const match = reply.match(/\{[\s\S]*\}/)
        if (match) {
          parsedReply = JSON.parse(match[0])
        } else {
          throw new Error('AI返回格式异常，无法解析题目')
        }
      }

      const rawQuestions = parsedReply.questions || []
      if (rawQuestions.length === 0) {
        throw new Error('AI未生成有效题目，请重试')
      }

      // 格式化题目数据
      questions.value = rawQuestions.map((q, idx) => ({
        ...q,
        id: q.id || idx + 1,
        answer: Array.isArray(q.answer) ? q.answer : [q.answer]
      }))

      // 重置作答状态
      userAnswers.value = {}
      quizResult.value = { score: 0, wrongAnswers: [], overall_analysis: '' }
      submitting.value = false
      pendingAssessResult.value = {
        rawResponse: typeof reply === 'string' ? reply : JSON.stringify(reply),
        historyId: assessHistoryId.value,
        assessTitle: history.userInput
      }

      // 切换到做题界面
      quizStep.value = 'quiz'
    } else {
      throw new Error(res.msg || 'AI接口返回异常')
    }
  } catch (error) {
    console.error('AI出题失败:', error)
    ElMessage.error('AI出题失败：' + (error.message || ''))
    quizDialogVisible.value = false
    quizStep.value = 'loading'
  } finally {
    assessLoading.value = false
  }
}

// 做题交互方法
function selectOption(qId, oIdx) {
  userAnswers.value = { ...userAnswers.value, [qId]: oIdx }
}

function toggleMultiOption(qId, oIdx) {
  const current = userAnswers.value[qId] || []
  const arr = Array.isArray(current) ? [...current] : []
  const idx = arr.indexOf(oIdx)
  if (idx >= 0) {
    arr.splice(idx, 1)
  } else {
    arr.push(oIdx)
  }
  userAnswers.value = { ...userAnswers.value, [qId]: arr }
}

function isMultiSelected(qId, oIdx) {
  const val = userAnswers.value[qId]
  return Array.isArray(val) && val.includes(oIdx)
}

function questionTypeTag(type) {
  const map = { '单选': '', '多选': 'success', '判断': 'warning' }
  return map[type] || ''
}

function closeQuiz() {
  quizDialogVisible.value = false
  quizStep.value = 'loading'
}

// 提交答案并显示结果
async function handleSubmitQuiz() {
  submitting.value = true

  try {
    // 获取选项文本
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
    const totalCount = questions.value.length
    const wrongAnswers = []

    for (const q of questions.value) {
      const userAns = userAnswers.value[q.id]
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
        const userAnsArr = q.type === '多选'
          ? (Array.isArray(userAns) ? userAns : [])
          : [userAns]
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
        ? `表现优秀！共 ${totalCount} 题，答对 ${correctCount} 题，正确率 ${score}%。`
        : score >= 60
          ? `表现良好，仍有提升空间。共 ${totalCount} 题，答对 ${correctCount} 题，正确率 ${score}%。`
          : `需要加强学习。共 ${totalCount} 题，答对 ${correctCount} 题，正确率 ${score}%。`
    }

    quizStep.value = 'result'
  } catch (error) {
    console.error('提交分析失败:', error)
    ElMessage.error('提交分析失败：' + (error.message || ''))
  } finally {
    submitting.value = false
  }
}

// 保存诊断结果
async function handleSaveResult() {
  savingAndGenerating.value = true

  try {
    const history = historyList.value.find(h => String(h.id) === String(assessHistoryId.value))
    const wrongAnswers = quizResult.value.wrongAnswers || []
    const totalScore = quizResult.value.score || 0

    // 1. 保持对话框可见，切换到 loading 状态让用户感知
    quizTitle.value = '正在生成学习路径...'
    quizStep.value = 'loading'

    // 2. 向 AI 发送 [2] + 错题信息 + 知识点，生成学习路径
    const wrongInfo = wrongAnswers.map(w => ({
      type: w.type,
      question: w.question,
      userAnswer: w.userAnswerText,
      correctAnswer: w.correctAnswerText
    }))

    const pointsInfo = knowledgePoints.value.length > 0
      ? knowledgePoints.value
      : (() => { try { return JSON.parse(history?.selectedSubjects || '[]') } catch { return [] } })()

    const aiRes = await chatWithAI({ message: `[2]
## 错题信息
${JSON.stringify(wrongInfo, null, 2)}

## 相关知识点
${JSON.stringify(pointsInfo, null, 2)}` })

    let aiReply = ''
    if (aiRes.code === 200 && aiRes.data) {
      aiReply = aiRes.data.reply || ''
    }

    // 3. 保存诊断结果（含 AI 学习路径）
    const saveRes = await addAssessment({
      userId: 1,
      historyId: assessHistoryId.value,
      assessTitle: history ? history.userInput : '薄弱知识点诊断',
      questions: JSON.stringify(questions.value),
      answers: JSON.stringify(userAnswers.value),
      weakPoints: JSON.stringify(wrongAnswers),
      overallAnalysis: quizResult.value.overall_analysis,
      aiResponse: aiReply,
      totalScore: totalScore,
      status: '2'
    })

    let assessId = null
    if (saveRes.data && saveRes.data.id) {
      assessId = saveRes.data.id
    } else if (saveRes.id) {
      assessId = saveRes.id
    }

    // 4. 关闭对话框，跳转到 AI 学习页面
    closeQuiz()
    loadAssessList()

    if (assessId) {
      router.push({ name: 'LearnAiResult', params: { id: assessId } })
    } else {
      ElMessage.success('诊断结果已保存')
    }
  } catch (error) {
    console.error('保存失败:', error)
    closeQuiz()
    ElMessage.error('保存失败：' + (error.message || ''))
  } finally {
    savingAndGenerating.value = false
  }
}

// 保存诊断结果
async function handleSaveAssessment() {
  if (!pendingAssessResult.value) return

  try {
    // 解析AI返回，提取薄弱知识点
    let weakPoints = []
    let overallAnalysis = ''
    let totalScore = 0
    try {
      const parsed = JSON.parse(pendingAssessResult.value.rawResponse)
      weakPoints = parsed.weak_points || []
      overallAnalysis = parsed.overall_analysis || ''
      totalScore = parsed.total_score || 0
      if (!Array.isArray(weakPoints)) weakPoints = []
    } catch {
      // 非JSON格式，整个返回作为分析文字
      overallAnalysis = pendingAssessResult.value.rawResponse
    }

    await addAssessment({
      userId: 1,
      historyId: pendingAssessResult.value.historyId,
      assessTitle: pendingAssessResult.value.assessTitle,
      questions: '[]',
      answers: '{}',
      weakPoints: JSON.stringify(weakPoints),
      overallAnalysis: overallAnalysis,
      totalScore: totalScore,
      rawResponse: pendingAssessResult.value.rawResponse,
      status: '2'
    })

    ElMessage.success('诊断结果已保存')
    rawDialogVisible.value = false
    pendingAssessResult.value = null
    loadAssessList()
  } catch (error) {
    console.error('保存诊断结果失败:', error)
    ElMessage.error('保存失败：' + (error.message || ''))
  }
}

// 查看诊断结果
async function handleViewAssess(row) {
  if (row.aiResponse) {
    router.push({ name: 'LearnAiResult', params: { id: row.id } })
  } else {
    ElMessage.info('该诊断记录暂无学习路径数据')
  }
}

// 删除诊断记录
function handleDeleteAssess(row) {
  ElMessageBox.confirm('确认删除该诊断记录？', '提示', {
    type: 'warning'
  }).then(() => {
    delAssessment(row.id).then(() => {
      ElMessage.success('删除成功')
      loadAssessList()
    })
  }).catch(() => {})
}

// 诊断状态工具函数
function assessStatusTag(status) {
  const map = { '0': 'info', '1': 'warning', '2': 'success' }
  return map[status] || 'info'
}

function assessStatusLabel(status) {
  const map = { '0': '待答题', '1': '待分析', '2': '已完成' }
  return map[status] || '未知'
}

function scoreColor(score) {
  if (score >= 80) return 'score-excellent'
  if (score >= 60) return 'score-good'
  if (score >= 40) return 'score-fair'
  return 'score-poor'
}

// 清空记录
function handleClear() {
  ElMessageBox.confirm('确认清空所有查询历史记录？', '提示', {
    type: 'warning'
  }).then(() => {
    const ids = historyList.value.map(item => item.id)
    delHistory(ids.join(',')).then(() => {
      ElMessage.success('清空成功')
      getList()
    })
  }).catch(() => {})
}

// ====== 音频文件文本提取（非实时，仅上传 WAV 识别） ======
function triggerAudioUpload() {
  if (asrLoading.value || loading.value) return
  if (audioFileInput.value) {
    audioFileInput.value.value = ''
    audioFileInput.value.click()
  }
}

async function handleAudioFile(event) {
  const file = event.target.files && event.target.files[0]
  event.target.value = ''
  if (!file) return
  if (!file.name.toLowerCase().endsWith('.wav')) {
    ElMessage.warning('仅支持 WAV 音频文件')
    return
  }
  if (file.size > 25 * 1024 * 1024) {
    ElMessage.warning('音频文件不能超过 25MB')
    return
  }
  asrLoading.value = true
  try {
    const res = await transcribeAudio(file)
    const text = res && res.data ? (res.data.text || '') : ''
    if (text.trim()) {
      inputMessage.value = inputMessage.value.trim()
        ? inputMessage.value.trim() + ' ' + text.trim()
        : text.trim()
      ElMessage.success('音频文本提取完成')
    } else {
      ElMessage.warning('未提取到有效文本')
    }
  } catch (error) {
    ElMessage.error('文本提取失败：' + (error.message || '请稍后重试'))
  } finally {
    asrLoading.value = false
  }
}
</script>

<style scoped>
.query-card {
  margin-bottom: 16px;
}

.query-header {
  margin-bottom: 16px;
}

.query-header h3 {
  margin: 0 0 6px 0;
  font-size: 18px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.query-desc {
  font-size: 13px;
  color: #909399;
}

.query-actions {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 12px;
}

.audio-file-input {
  display: none;
}

.query-buttons {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.suggest-questions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.suggest-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.suggest-tag {
  cursor: pointer;
}

.history-card {
  min-height: 300px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.history-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 200px;
}

.history-cell {
  padding: 4px 0;
}

.history-input {
  font-size: 14px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-all;
}

.history-time {
  font-size: 13px;
  color: #909399;
}

/* 人才画像入口 */
.profile-card {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.profile-body {
  min-height: 80px;
}

.profile-quick-grid {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.quick-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 120px;
}

.quick-item-wide {
  flex: 1;
  min-width: 200px;
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
}

.quick-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}

.quick-icon.done { background: #f0f9eb; }
.quick-icon.learning { background: #ecf5ff; }
.quick-icon.pending { background: #fdf6ec; }
.quick-icon.total { background: #f4f4f5; }

.quick-info {
  display: flex;
  flex-direction: column;
}

.quick-value {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.quick-label {
  font-size: 12px;
  color: #909399;
}

.profile-course-section {
  margin-top: 18px;
}

.learning-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 14px;
  padding: 12px 14px;
  color: #a5a9b0;
  font-size: 12px;
  background: #fafbfc;
  border: 1px dashed #e5e8ed;
  border-radius: 8px;
}

.learning-empty .svg-icon {
  font-size: 16px;
}

.profile-empty {
  text-align: center;
  padding: 20px 0;
  color: #909399;
}

/* AI 薄弱知识点诊断 */
.assess-card {
  margin-bottom: 16px;
}

.section-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.assess-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100px;
}

.assess-info {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: center;
}

.assess-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  flex: 1;
  min-width: 200px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
  background: #f0f5ff;
  border-radius: 8px;
}

.assess-target {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.target-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.assess-title {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
  display: inline-block;
}

.assess-score {
  font-size: 15px;
  font-weight: 600;
}

.assess-score.score-excellent { color: #67c23a; }
.assess-score.score-good { color: #409eff; }
.assess-score.score-fair { color: #e6a23c; }
.assess-score.score-poor { color: #f56c6c; }

.text-secondary {
  color: #909399;
  font-size: 13px;
}

/* ====== 做题弹窗样式 ====== */
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

.quiz-body {
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
.result-section {
  padding: 0 4px;
}

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

/* AI原始返回 */
.raw-response {
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  line-height: 1.6;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 16px;
  max-height: 500px;
  overflow-y: auto;
  margin: 0;
  font-family: 'Courier New', monospace;
}

/* 1. 页面容器背景 – 极浅灰 */
.app-container {
  background-color: #f7f8fa;  /* 更淡，接近白 */
  padding: 20px;
  min-height: 100vh;
}

/* 2. 搜索表单区域 – 白色，加极淡阴影 */
.el-form--inline {
  background-color: #ffffff;
  padding: 20px 20px 0 20px;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
}

/* 3. 表格区域 – 白色，极淡阴影 */
.el-table {
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

/* 4. 表格头部 – 极浅灰 */
.el-table th.el-table__cell {
  background-color: #fafbfc !important;
}

/* 5. 分页 – 白色，与表格无缝 */
.pagination-container {
  background-color: #ffffff;
  padding: 10px 16px;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  margin-top: -1px;
}

/* 6. 对话框 – 整体极浅灰背景，内容白色 */
.el-dialog {
  background-color: #f7f8fa;
}
.el-dialog .el-dialog__header {
  background-color: #ffffff;
  border-radius: 8px 8px 0 0;
  padding: 20px 20px 10px;
}
.el-dialog .el-dialog__body {
  background-color: #ffffff;
  padding: 20px;
}
.el-dialog .el-dialog__footer {
  background-color: #ffffff;
  border-radius: 0 0 8px 8px;
  padding: 10px 20px 20px;
}

/* 7. 按钮行背景透明 */
.mb8 {
  background: transparent;
}

/* （可选）输入框边框颜色稍微变淡 */
.el-input__wrapper {
  box-shadow: 0 0 0 1px #e7e7e7 inset !important;
}
.el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #d2d3d4 inset !important;
}
</style>
