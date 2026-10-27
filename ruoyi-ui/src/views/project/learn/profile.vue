<template>
  <div class="profile-page">
    <!-- 页头 -->
    <header class="page-head">
      <div class="head-left">
        <h1 class="page-title">人才画像</h1>
        <p class="page-desc">全方位追踪你的学习轨迹与岗位胜任力</p>
      </div>
      <div class="page-meta" v-if="updatedAt">
        <span class="meta-dot"></span>
        <span>{{ updatedAt }} 更新</span>
      </div>
    </header>

    <!-- 核心指标 -->
    <section class="metrics">
      <div class="metric-item" v-for="(stat, idx) in statCards" :key="idx">
        <div class="metric-label">
          <svg-icon :icon-class="stat.icon" class="metric-icon" />
          <span class="metric-label-text">{{ stat.label }}</span>
        </div>
        <div class="metric-value" :style="{ color: stat.color }">{{ stat.value }}</div>
        <div class="metric-track">
          <span
            class="metric-fill"
            :style="{ width: (stat.percent || 0) + '%', background: stat.barColor || stat.color }"
          ></span>
        </div>
      </div>
    </section>

    <!-- 图表 -->
    <section class="grid-2">
      <div class="panel">
        <div class="panel-head">
          <h2 class="panel-title">学习状态分布</h2>
        </div>
        <div ref="pieChartRef" class="chart-box"></div>
      </div>

      <div class="panel">
        <div class="panel-head">
          <h2 class="panel-title">岗位胜任力分析</h2>
        </div>
        <div ref="radarChartRef" class="chart-box"></div>
      </div>
    </section>

    <!-- 明细 -->
    <section class="grid-2">
      <!-- 各岗位学习进度 -->
      <div class="panel">
        <div class="panel-head">
          <h2 class="panel-title">各岗位学习进度</h2>
          <span class="panel-count">{{ positionList.length }} 个岗位</span>
        </div>
        <div class="panel-body">
          <div v-if="positionList.length === 0" class="empty">
            <el-empty description="暂无岗位数据" :image-size="56" />
          </div>
          <ul v-else class="progress-list">
            <li class="progress-item" v-for="(pos, idx) in positionList" :key="idx">
              <div class="progress-head">
                <span class="progress-name" :title="pos.userInput">{{ pos.userInput }}</span>
                <span class="progress-num">{{ pos.percent }}%</span>
              </div>
              <div class="progress-track">
                <span
                  class="progress-fill"
                  :style="{ width: pos.percent + '%', background: barColor(pos.percent) }"
                ></span>
              </div>
              <div class="progress-foot">
                <span>{{ pos.doneCount }} 已学 · {{ pos.totalCount - pos.doneCount }} 待学</span>
                <span v-if="pos.recentLearn">最近学习 {{ pos.recentLearn }}</span>
              </div>
            </li>
          </ul>
        </div>
      </div>

      <!-- 最近动态 -->
      <div class="panel">
        <div class="panel-head">
          <h2 class="panel-title">最近学习动态</h2>
          <span class="panel-count">{{ recentActivities.length }} 条</span>
        </div>
        <div class="panel-body">
          <div v-if="recentActivities.length === 0" class="empty">
            <el-empty description="暂无学习记录" :image-size="56" />
          </div>
          <ul v-else class="activity-list">
            <li class="activity-item" v-for="(act, idx) in recentActivities" :key="idx">
              <span class="activity-dot" :class="act.type"></span>
              <div class="activity-main">
                <p class="activity-text">{{ act.text }}</p>
                <span class="activity-time">{{ act.time }}</span>
              </div>
            </li>
          </ul>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { listHistory } from '@/api/project/history'
import { listPoint } from '@/api/project/point'
import { listRecord } from '@/api/project/record'

// ====== Chart refs ======
const pieChartRef = ref(null)
const radarChartRef = ref(null)
let pieChart = null
let radarChart = null

// ====== Data ======
const statCards = ref([])
const positionList = ref([])
const recentActivities = ref([])
const updatedAt = ref('')

// 统一色板，克制、低饱和
const THEME = {
  green: '#22a06b',
  blue: '#3b7ef2',
  orange: '#e8a33d',
  red: '#f54a45',
  gray: '#4e5969',
  muted: '#c9cdd4'
}

onMounted(() => {
  loadAllData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  radarChart?.dispose()
})

function handleResize() {
  pieChart?.resize()
  radarChart?.resize()
}

function barColor(percent) {
  if (percent >= 80) return THEME.green
  if (percent >= 50) return THEME.blue
  return THEME.orange
}

function scoreColor(score) {
  if (score >= 80) return THEME.green
  if (score >= 60) return THEME.blue
  if (score >= 40) return THEME.orange
  return THEME.red
}

async function loadAllData() {
  try {
    // 1. 学习记录
    const recordRes = await listRecord({ pageSize: 9999, pageNum: 1 })
    const allRecords = recordRes.rows || []
    const doneCount = allRecords.filter(r => r.status === '2').length
    const learningCount = allRecords.filter(r => r.status === '1').length
    const totalCount = allRecords.length
    const pendingCount = Math.max(0, totalCount - doneCount - learningCount)

    // 2. 历史查询（岗位）
    const historyRes = await listHistory({ pageSize: 9999, pageNum: 1 })
    const allHistory = historyRes.rows || []

    // 3. 按岗位统计知识点
    let globalTotalPoints = 0
    const positions = []

    for (const h of allHistory) {
      if (!h.selectedSubjects) continue
      let subjects = []
      try { subjects = JSON.parse(h.selectedSubjects) || [] } catch { continue }
      if (!subjects.length) continue

      let posTotal = 0
      for (const s of subjects) {
        try {
          const pr = await listPoint({ subjectId: s.id, pageSize: 9999, pageNum: 1 })
          posTotal += (pr.rows || []).length
        } catch {}
      }

      const hr = allRecords.filter(r => String(r.historyId) === String(h.id))
      const hDone = hr.filter(r => r.status === '2').length
      const hLearning = hr.filter(r => r.status === '1').length

      if (posTotal > 0) {
        positions.push({
          id: h.id,
          userInput: h.userInput,
          totalCount: posTotal,
          doneCount: hDone,
          learningCount: hLearning,
          percent: Math.round((hDone / posTotal) * 100),
          recentLearn: hr.length > 0 ? formatTime(hr[hr.length - 1].updateTime) : null
        })
        globalTotalPoints += posTotal
      }
    }

    // 去重
    const seen = new Set()
    positionList.value = positions.filter(p => {
      const k = (p.userInput || '').trim().toLowerCase()
      if (seen.has(k)) return false
      seen.add(k)
      return true
    })

    const pct = n => globalTotalPoints > 0 ? Math.round((n / globalTotalPoints) * 100) : 0

    // 测评平均分
    const scoredRecords = allRecords.filter(
      r => r.pointQuizScore !== null && r.pointQuizScore !== undefined
    )
    const quizAvgScore = scoredRecords.length > 0
      ? Math.round(scoredRecords.reduce((s, r) => s + Number(r.pointQuizScore), 0) / scoredRecords.length)
      : 0

    const pendingPoints = Math.max(0, globalTotalPoints - doneCount - learningCount)

    // 4. 指标卡
    statCards.value = [
      {
        label: '岗位总知识点',
        value: globalTotalPoints || 0,
        icon: 'education',
        color: '#1f2329',
        barColor: '#e5e6eb',
        percent: globalTotalPoints > 0 ? 100 : 0
      },
      {
        label: '已学完成',
        value: doneCount,
        icon: 'checkbox',
        color: THEME.green,
        percent: pct(doneCount)
      },
      {
        label: '学习中',
        value: learningCount,
        icon: 'edit',
        color: THEME.blue,
        percent: pct(learningCount)
      },
      {
        label: '待学习',
        value: pendingPoints,
        icon: 'time',
        color: THEME.orange,
        percent: pct(pendingPoints)
      },
      {
        label: scoredRecords.length > 0 ? `测评平均分 · ${scoredRecords.length}项` : '测评平均分',
        value: scoredRecords.length > 0 ? quizAvgScore : '—',
        icon: 'star',
        color: scoredRecords.length === 0 ? THEME.muted : scoreColor(quizAvgScore),
        percent: scoredRecords.length > 0 ? quizAvgScore : 0
      }
    ]

    // 5. 知识点名称映射
    const pointNames = {}
    try {
      const allPointRes = await listPoint({ pageSize: 9999, pageNum: 1 })
      ;(allPointRes.rows || []).forEach(p => { pointNames[p.id] = p.name })
    } catch {}

    // 6. 最近动态
    const acts = []
    allRecords.slice(0, 50).forEach(r => {
      const pName = pointNames[r.pointId] || `知识点（ID: ${r.pointId}）`
      if (r.status === '2') {
        acts.push({
          type: 'done',
          text: `完成「${pName}」学习`,
          time: formatTime(r.completeTime || r.updateTime)
        })
      } else if (r.status === '1') {
        acts.push({
          type: 'learning',
          text: `开始学习「${pName}」`,
          time: formatTime(r.startTime || r.updateTime)
        })
      }
      if (r.pointQuizScore !== null && r.pointQuizScore !== undefined) {
        acts.push({
          type: 'quiz',
          text: `「${pName}」测评得分 ${r.pointQuizScore} 分`,
          time: formatTime(r.updateTime)
        })
      }
    })
    acts.sort((a, b) => new Date(b.time) - new Date(a.time))
    recentActivities.value = acts.slice(0, 20)

    updatedAt.value = formatTime(new Date())

    // 7. 渲染图表
    await nextTick()
    renderPieChart(doneCount, learningCount, pendingCount)
    renderRadarChart(positionList.value)
  } catch (e) {
    console.error('加载画像数据失败', e)
  }
}

function formatTime(timeStr) {
  if (!timeStr) return '-'
  const d = new Date(timeStr)
  if (isNaN(d.getTime())) return '-'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function truncate(str, len) {
  if (!str) return ''
  return str.length > len ? str.slice(0, len) + '…' : str
}

// ====== 饼图 ======
function renderPieChart(done, learning, pending) {
  if (!pieChartRef.value) return
  if (!pieChart) pieChart = echarts.init(pieChartRef.value)

  const total = done + learning + pending

  if (total <= 0) {
    pieChart.clear()
    pieChart.setOption({
      title: {
        text: '暂无学习数据',
        left: 'center',
        top: 'middle',
        textStyle: { color: '#a5a9b0', fontSize: 13, fontWeight: 'normal' }
      }
    })
    return
  }

  pieChart.setOption({
    color: [THEME.green, THEME.blue, THEME.orange],
    title: {
      text: String(total),
      subtext: '知识点总数',
      left: 'center',
      top: '36%',
      itemGap: 4,
      textStyle: { fontSize: 24, fontWeight: 600, color: '#1f2329' },
      subtextStyle: { fontSize: 12, color: '#8f959e' }
    },
    tooltip: {
      trigger: 'item',
      formatter: '{b}：{c} 项（{d}%）',
      backgroundColor: 'rgba(31,35,41,0.88)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 }
    },
    legend: {
      bottom: 4,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      itemGap: 20,
      textStyle: { color: '#4e5969', fontSize: 12 }
    },
    series: [{
      type: 'pie',
      radius: ['54%', '74%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: true,
      padAngle: 1.5,
      itemStyle: {
        borderRadius: 4,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false },
      labelLine: { show: false },
      emphasis: {
        scale: true,
        scaleSize: 5,
        itemStyle: { shadowBlur: 12, shadowColor: 'rgba(0,0,0,0.08)' }
      },
      data: [
        { value: done, name: '已学完成' },
        { value: learning, name: '学习中' },
        { value: pending, name: '待学习' }
      ]
    }]
  }, true)
}

// ====== 雷达图 ======
function renderRadarChart(positions) {
  if (!radarChartRef.value) return
  if (!radarChart) radarChart = echarts.init(radarChartRef.value)

  if (!positions.length) {
    radarChart.clear()
    radarChart.setOption({
      title: {
        text: '暂无岗位数据',
        left: 'center',
        top: 'middle',
        textStyle: { color: '#a5a9b0', fontSize: 13, fontWeight: 'normal' }
      }
    })
    return
  }

  const top = positions.slice(0, 6)

  radarChart.setOption({
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(31,35,41,0.88)',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 }
    },
    radar: {
      indicator: top.map(p => ({ name: truncate(p.userInput, 8), max: 100 })),
      radius: '62%',
      center: ['50%', '52%'],
      splitNumber: 4,
      axisName: { color: '#4e5969', fontSize: 12 },
      axisLine: { lineStyle: { color: '#eceef1' } },
      splitLine: { lineStyle: { color: '#eceef1' } },
      splitArea: { show: false }
    },
    series: [{
      type: 'radar',
      symbol: 'circle',
      symbolSize: 5,
      data: [{
        value: top.map(p => p.percent),
        name: '完成度',
        areaStyle: { color: 'rgba(59,126,242,0.10)' },
        lineStyle: { color: THEME.blue, width: 2 },
        itemStyle: { color: THEME.blue }
      }]
    }]
  }, true)
}
</script>

<style scoped>
.profile-page {
  max-width: 1280px;
  margin: 0 auto;
  padding: 8px 4px 48px;
  color: #1f2329;
  font-size: 14px;
}

/* ====== 页头 ====== */
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid #e5e6eb;
  margin-bottom: 24px;
}

.page-title {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.page-desc {
  margin: 0;
  font-size: 13px;
  color: #8f959e;
}

.page-meta {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: #8f959e;
  padding-bottom: 2px;
  white-space: nowrap;
}

.meta-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22a06b;
  box-shadow: 0 0 0 3px rgba(34, 160, 107, 0.12);
}

/* ====== 指标带 ====== */
.metrics {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 20px;
}

.metric-item {
  padding: 20px 22px;
  border-right: 1px solid #f0f1f3;
  min-width: 0;
}

.metric-item:last-child {
  border-right: none;
}

.metric-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: #8f959e;
  margin-bottom: 12px;
  min-width: 0;
}

.metric-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.metric-label-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metric-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
  letter-spacing: -0.5px;
  font-variant-numeric: tabular-nums;
}

.metric-track {
  height: 3px;
  border-radius: 2px;
  background: #f0f1f3;
  margin-top: 16px;
  overflow: hidden;
}

.metric-fill {
  display: block;
  height: 100%;
  border-radius: 2px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ====== 面板 ====== */
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}

.panel {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e5e6eb;
  border-radius: 10px;
  overflow: hidden;
  min-width: 0;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 20px;
  border-bottom: 1px solid #f0f1f3;
}

.panel-title {
  position: relative;
  margin: 0;
  padding-left: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2329;
}

.panel-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: #3b7ef2;
}

.panel-count {
  font-size: 12px;
  color: #8f959e;
  flex-shrink: 0;
}

.panel-body {
  flex: 1;
  padding: 6px 20px 14px;
}

.chart-box {
  height: 320px;
  width: 100%;
}

.empty {
  display: flex;
  justify-content: center;
  padding: 24px 0;
}

/* ====== 进度列表 ====== */
.progress-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.progress-item {
  padding: 14px 0;
  border-bottom: 1px solid #f5f6f7;
}

.progress-item:last-child {
  border-bottom: none;
}

.progress-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;
}

.progress-name {
  font-size: 13.5px;
  font-weight: 500;
  color: #1f2329;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-num {
  font-size: 13px;
  font-weight: 600;
  color: #4e5969;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.progress-track {
  height: 6px;
  border-radius: 3px;
  background: #f0f1f3;
  overflow: hidden;
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.progress-foot {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
  font-size: 12px;
  color: #a5a9b0;
}

.progress-foot span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ====== 动态列表 ====== */
.activity-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 420px;
  overflow-y: auto;
}

.activity-list::-webkit-scrollbar {
  width: 4px;
}

.activity-list::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 2px;
}

.activity-item {
  display: flex;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid #f5f6f7;
}

.activity-item:last-child {
  border-bottom: none;
}

.activity-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  margin-top: 6px;
  flex-shrink: 0;
  background: #c9cdd4;
}

.activity-dot.done { background: #22a06b; }
.activity-dot.learning { background: #3b7ef2; }
.activity-dot.quiz { background: #e8a33d; }

.activity-main {
  flex: 1;
  min-width: 0;
}

.activity-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.5;
  color: #1f2329;
}

.activity-time {
  font-size: 12px;
  color: #a5a9b0;
}

/* ====== 响应式 ====== */
@media (max-width: 1200px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }

  .metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .metric-item:nth-child(3n) {
    border-right: none;
  }

  .metric-item:nth-child(n + 4) {
    border-top: 1px solid #f0f1f3;
  }
}

@media (max-width: 768px) {
  .page-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }

  .metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .metric-item:nth-child(3n) {
    border-right: 1px solid #f0f1f3;
  }

  .metric-item:nth-child(2n) {
    border-right: none;
  }

  .metric-value {
    font-size: 22px;
  }

  .chart-box {
    height: 280px;
  }
}
</style>