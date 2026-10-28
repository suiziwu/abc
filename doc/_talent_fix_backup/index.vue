<template>
  <div class="app-container talent-dashboard">
    <!-- 顶部：数据源 + 岗位方向 -->
    <el-card shadow="never" class="header-card">
      <div class="header-row">
        <div class="header-info">
          <div class="title">人才培养方案智能优化</div>
          <div class="subtitle">
            岗位动态分析 → 技能需求趋势 → 专业匹配预警 → 培养方案优化建议
          </div>
          <div class="meta">
            <span>最近入库：{{ overview.lastImportTime || '-' }}</span>
            <el-divider direction="vertical" />
            <span>采集批次：{{ overview.batchCount || 0 }} 期</span>
            <el-divider direction="vertical" />
            <span>课程库：{{ overview.courseCount || 0 }} 门 / {{ overview.pointCount || 0 }} 知识点</span>
            <el-divider direction="vertical" />
            <span>当前视图：{{ scopeLabel }} · {{ overview.postingCount || 0 }} 条岗位</span>
          </div>
        </div>
        <div class="header-actions">
          <el-select
            v-model="jobFamily"
            placeholder="全部岗位方向"
            clearable
            style="width: 220px"
            @change="loadAll"
          >
            <el-option
              v-for="item in families"
              :key="item.jobFamily"
              :value="item.jobFamily"
              :label="item.jobFamily + '（' + item.jobCount + '）'"
            />
          </el-select>
          <el-select
            v-model="batchDate"
            placeholder="全部时间"
            clearable
            style="width: 200px"
            @change="loadAll"
          >
            <el-option label="全部时间" value="" />
            <el-option
              v-for="item in batchList"
              :key="item.batchDate"
              :value="item.label"
              :label="item.label + '（' + item.jobCount + '）'"
            />
          </el-select>
          <el-button :loading="loading" @click="loadAll">刷新看板</el-button>
          <template v-if="!crawl.running">
            <el-button type="primary" plain :loading="crawlStarting" @click="startCrawl">爬取数据</el-button>
            <el-tooltip
              content="后台运行爬虫，爬取完成后自动导入并刷新看板；进度实时显示"
              placement="bottom"
            >
              <span class="import-help">?</span>
            </el-tooltip>
          </template>
          <div v-else class="crawl-box">
            <el-progress
              :percentage="crawl.percent"
              :stroke-width="16"
              :text-inside="true"
              style="width: 220px"
            />
            <span class="crawl-msg">{{ crawl.message }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- KPI -->
    <div class="kpi-grid" v-loading="loading">
      <div class="kpi-card">
        <div class="kpi-label">采集岗位数</div>
        <div class="kpi-value">{{ overview.postingCount || 0 }}</div>
        <div class="kpi-sub">{{ overviewSummary }}</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">岗位方向</div>
        <div class="kpi-value">{{ overview.familyCount || 0 }}</div>
        <div class="kpi-sub">覆盖 {{ overview.industryCount || 0 }} 个行业 · IT 与非 IT 方向</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">技能需求项</div>
        <div class="kpi-value">{{ overview.skillCount || 0 }}</div>
        <div class="kpi-sub">去重后的技能标签</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">开设专业</div>
        <div class="kpi-value">{{ overview.majorCount || 0 }}</div>
        <div class="kpi-sub">
          知识库课程 {{ overview.courseCount || 0 }} 门 · 知识点 {{ overview.pointCount || 0 }} 个
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-label">预警提示</div>
        <div class="kpi-value warn">{{ warningCount }}</div>
        <div class="kpi-sub">共 {{ warnings.length }} 条 · 重点（中/高）{{ warningCount }} 条</div>
      </div>
    </div>

    <!-- 图表区一 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>技能热度曲线</span>
              <span class="card-tip">{{ trendTip }}</span>
            </div>
          </template>
          <div class="chart-wrap" style="height: 320px">
            <div ref="trendRef" class="chart"></div>
            <el-empty
              v-if="!trendData.batches.length || !trendData.series.length"
              description="该方向暂无可用的岗位技能样本"
              :image-size="70"
              class="chart-empty"
            />
          </div>
          <div v-if="trendData.batches && trendData.batches.length === 1" class="chart-note">
            当前仅有「{{ trendData.batches[0] }}」一批数据，曲线暂无法体现趋势
          </div>
          <div v-if="trendData.lowSample" class="chart-note">
            该方向各批次样本均不足 20 条，已放宽阈值展示，曲线仅作趋势参考
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>技能类别分布</span>
              <span class="card-tip">所选时间维度的技能提及占比 · {{ scopeLabel }}</span>
            </div>
          </template>
          <div class="chart-wrap" style="height: 320px">
            <div ref="categoryRef" class="chart"></div>
            <el-empty
              v-if="!categoryList.length"
              description="暂无技能类别数据"
              :image-size="70"
              class="chart-empty"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区二 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="14">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>技能需求排行</span>
              <el-radio-group v-model="rankCategory" size="small" @change="loadRank">
                <el-radio-button value="">全部</el-radio-button>
                <el-radio-button v-for="cat in rankCategories" :key="cat.name" :value="cat.name">{{ cat.name }}</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div class="chart-wrap" style="height: 360px">
            <div ref="rankRef" class="chart"></div>
            <el-empty
              v-if="!rankList.length"
              description="暂无技能排行数据"
              :image-size="70"
              class="chart-empty"
            />
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="10">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>专业匹配度</span>
              <span class="card-tip">课程技能标签对自身方向需求前 30 技能的覆盖率</span>
            </div>
          </template>
          <div class="chart-wrap" style="height: 360px">
            <div ref="majorRef" class="chart"></div>
            <el-empty
              v-if="!majorList.length"
              description="该方向暂无对应专业"
              :image-size="70"
              class="chart-empty"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 预警 + 建议 -->
    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="card-header"><span>专业预警提示</span></div>
          </template>
          <div v-if="warnings.length" class="warn-list">
            <div v-for="(item, index) in warnings" :key="index" class="warn-item">
              <div class="warn-head">
                <el-tag :type="levelType(item.level)" size="small" effect="dark">
                  {{ levelText(item.level) }}
                </el-tag>
                <span class="warn-type">{{ item.type }}</span>
                <span class="warn-title">{{ item.title }}</span>
              </div>
              <div class="warn-content">{{ item.content }}</div>
              <div v-if="item.basis" class="warn-basis">{{ item.basis }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无预警数据" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="list-card">
          <template #header>
            <div class="card-header">
              <span>智能优化建议</span>
              <span class="card-tip">每条建议均标注数据依据与置信度</span>
            </div>
          </template>
          <div v-if="suggestions.length" class="suggest-list">
            <div v-for="(item, index) in suggestions" :key="index" class="suggest-item">
              <div class="suggest-index">{{ index + 1 }}</div>
              <div class="suggest-body">
                <div class="suggest-head">
                  <el-tag size="small" effect="plain">{{ item.type }}</el-tag>
                  <span class="suggest-title">{{ item.title }}</span>
                </div>
                <div class="suggest-content">{{ item.content }}</div>
                <div class="suggest-basis">
                  <el-tag size="small" :type="confidenceType(item.confidence)" effect="plain">
                    置信度 {{ item.confidence }}
                  </el-tag>
                  <span>{{ item.basis }}</span>
                </div>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无优化建议" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>

    <!-- 课程体系（知识库只读） -->
    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <span>课程体系 · 知识库课程库</span>
          <span class="card-tip">
            {{ curriculum.courseCount || 0 }} 门课程 · {{ curriculum.pointCount || 0 }} 个知识点
            {{ jobFamily ? '· ' + jobFamily + ' 方向命中课程优先' : '' }}
          </span>
        </div>
      </template>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="kb-alert"
        title="课程与知识点只读引用知识库（subject / knowledge_point）。在「知识库管理」新增课程后，看板自动同步，本模块不维护课程数据。"
      />

      <div class="section-title">
        专业基础课（{{ curriculum.basicCourses.length }} 门 · 各专业必修）
      </div>
      <div class="basic-grid">
        <div
          v-for="course in curriculum.basicCourses"
          :key="course.subjectId"
          class="basic-item"
        >
          <div class="basic-name" :title="course.description">{{ course.courseName }}</div>
          <div class="basic-meta">
            <el-tag size="small" type="danger" effect="plain">必修</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ course.pointCount }} 知识点</el-tag>
          </div>
        </div>
      </div>

      <div class="section-title">
        {{ courseTableTitle }}
        <span v-if="jobFamily" class="section-sub">· {{ jobFamily }} 方向命中技能已标注</span>
      </div>
      <el-alert
        v-if="jobFamily && !courseRows.length"
        type="warning"
        :closable="false"
        show-icon
        class="kb-alert"
        title="对照库暂无与该方向匹配的课程：可在「知识库管理」新增 subject（课程）后刷新看板。"
      />
      <el-table
        :data="courseRows"
        size="small"
        max-height="380"
        :empty-text="jobFamily ? '对照库暂无匹配课程' : '知识库暂无课程'"
      >
        <el-table-column prop="courseName" label="课程" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.basic ? 'danger' : 'success'" effect="plain">
              {{ row.courseGroup }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="pointCount" label="知识点" width="80" />
        <el-table-column label="当前方向命中技能" min-width="220">
          <template #default="{ row }">
            <template v-if="row.matchedSkills && row.matchedSkills.length">
              <el-tag
                v-for="skill in row.matchedSkills"
                :key="skill"
                size="small"
                effect="plain"
                class="skill-tag"
              >
                {{ skill }}
              </el-tag>
            </template>
            <span v-else class="muted">{{ jobFamily ? '未命中' : '选择方向后比对' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="课程说明" min-width="240" show-overflow-tooltip />
      </el-table>

      <div class="section-title">专业与岗位方向对照（{{ majorList.length }} 个专业）</div>
      <el-table :data="majorList" size="small" max-height="300">
        <el-table-column prop="majorName" label="专业" min-width="180" />
        <el-table-column label="面向岗位方向" width="140">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.jobFamily || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="graduate" label="主要就业岗位" min-width="190" show-overflow-tooltip />
        <el-table-column label="知识库关联课程" min-width="260">
          <template #default="{ row }">
            <span v-if="row.relatedCourses && row.relatedCourses.length">
              {{ row.relatedCourses.join('、') }}
              <span v-if="row.courseCount > row.relatedCourses.length"> 等 {{ row.courseCount }} 门</span>
            </span>
            <span v-else class="muted">知识库暂无匹配课程</span>
          </template>
        </el-table-column>
        <el-table-column prop="courseCount" label="课程数" width="80" />
      </el-table>
    </el-card>

    <!-- 采集时间维度 -->
    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <span>采集时间维度</span>
          <span class="card-tip">按爬取数据的采集时间分割 · 共 {{ batchList.length }} 个时间切片</span>
        </div>
      </template>
      <el-table
        :data="batchList"
        size="small"
        max-height="340"
        :row-class-name="batchRowClass"
      >
        <el-table-column label="采集时间" min-width="170">
          <template #default="{ row }">
            <span class="batch-label">{{ row.label }}</span>
            <el-tag v-if="row.snapshot" size="small" type="success" effect="plain" class="batch-tag">
              未标记批次
            </el-tag>
            <el-tag v-else size="small" type="info" effect="plain" class="batch-tag">
              历史批次
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="jobCount" label="岗位数" width="90" sortable />
        <el-table-column prop="skillCount" label="技能项" width="90" />
        <el-table-column prop="familyCount" label="覆盖方向" width="100" />
        <el-table-column label="岗位占比" width="150">
          <template #default="{ row }">
            <el-progress :percentage="row.share" :stroke-width="8" />
          </template>
        </el-table-column>
        <el-table-column label="入库时间" min-width="200">
          <template #default="{ row }">
            <span class="muted">{{ row.firstTime || '-' }} ~ {{ row.lastTime || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="toggleBatch(row.label)">
              {{ batchDate === row.label ? '已选中' : '查看' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="batch-note">
        时间维度取自岗位数据入库时的采集批次（batch_date）：填写了采集时间的导入会独立成一个时间切片，未填写的并入「当前快照」。
        数据量增长时，可用顶部「数据时间」或本表筛选，只看某一时间的数据。
      </div>
    </el-card>

    <!-- 数据来源与指标口径（看板） -->
    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="card-header">
          <span>数据来源与指标口径</span>
          <span class="card-tip">岗位数据入库即统计 · 课程只读引用知识库</span>
        </div>
      </template>

      <div class="ds-stat-grid">
        <div class="ds-stat">
          <div class="ds-stat-label">入库岗位量</div>
          <div class="ds-stat-value">{{ dataSource.postingCount || 0 }}</div>
          <div class="ds-stat-sub">
            快照 {{ dataSource.snapshotJobs || 0 }} · 批次 {{ dataSource.batchJobs || 0 }}
          </div>
        </div>
        <div class="ds-stat">
          <div class="ds-stat-label">采集批次</div>
          <div class="ds-stat-value">{{ dataSource.batchCount || 0 }}<span class="ds-unit">期</span></div>
          <div class="ds-stat-sub">最近批次 {{ lastBatch }}</div>
        </div>
        <div class="ds-stat">
          <div class="ds-stat-label">最近入库</div>
          <div class="ds-stat-value ds-stat-time">{{ dataSource.lastUpdate || '-' }}</div>
          <div class="ds-stat-sub">CSV 导入后自动刷新</div>
        </div>
        <div class="ds-stat">
          <div class="ds-stat-label">覆盖行业</div>
          <div class="ds-stat-value">{{ dataSource.industryCount || 0 }}<span class="ds-unit">个</span></div>
          <div class="ds-stat-sub">{{ dataSource.familyCount || 0 }} 个岗位方向</div>
        </div>
        <div class="ds-stat">
          <div class="ds-stat-label">技能需求项</div>
          <div class="ds-stat-value">{{ dataSource.skillCount || 0 }}</div>
          <div class="ds-stat-sub">去重技能标签</div>
        </div>
      </div>

      <el-row :gutter="16" class="ds-charts">
        <el-col :xs="24" :lg="12">
          <div class="ds-chart-title">行业岗位分布</div>
          <div ref="industryRef" class="ds-chart"></div>
        </el-col>
        <el-col :xs="24" :lg="12">
          <div class="ds-chart-title">岗位方向分布（Top 12）</div>
          <div ref="familyRef" class="ds-chart"></div>
        </el-col>
      </el-row>

      <el-tabs v-model="dsTab" class="ds-tabs">
        <el-tab-pane label="处理流程" name="flow">
          <el-steps :active="dataSource.flow?.length || 0" size="small" align-center>
            <el-step v-for="(item, index) in dataSource.flow" :key="'f' + index" :title="item" />
          </el-steps>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup name="Talent">
import * as echarts from 'echarts'
import {
  getTalentOverview,
  getTalentBatches,
  getSkillRank,
  getSkillTrend,
  getCategoryStat,
  getMajorMatch,
  getTalentWarnings,
  getTalentSuggestions,
  getCurriculum,
  getDataSource,
  importTalentCsv,
  startTalentCrawl,
  getTalentCrawlStatus
} from '@/api/project/talent'

const { proxy } = getCurrentInstance()

const loading = ref(false)
const importing = ref(false)
// 一键爬取状态
const crawl = ref({ running: false, percent: 0, message: '' })
const crawlStarting = ref(false)
let crawlTimer = null
const jobFamily = ref('')
const batchDate = ref('')
const importBatch = ref('')
const batchList = ref([])
const overview = ref({})
const rankCategory = ref('')
const rankList = ref([])
const trendData = ref({ batches: [], series: [] })
const categoryList = ref([])
// 类别筛选按钮：选中方向时展示该方向全部类别；全部方向时只取前 8 个，避免按钮过多
const rankCategories = computed(() => {
  const list = categoryList.value || []
  return jobFamily.value ? list : list.slice(0, 8)
})
const majorList = ref([])
const warnings = ref([])
const suggestions = ref([])
const curriculum = ref({ subjects: [], basicCourses: [], majorCourses: [] })
const dataSource = ref({
  families: [],
  industries: [],
  limitations: [],
  flow: []
})
const dsTab = ref('flow')

const trendRef = ref(null)
const categoryRef = ref(null)
const rankRef = ref(null)
const majorRef = ref(null)
const industryRef = ref(null)
const familyRef = ref(null)
let trendChart = null
let categoryChart = null
let rankChart = null
let majorChart = null
let industryChart = null
let familyChart = null

const families = computed(() => overview.value.families || [])
const scopeLabel = computed(() => batchDate.value || '全部时间')
// 兼容新旧两版 overview：新版按采集日期，旧版按快照/批次
const overviewSummary = computed(() => {
  const o = overview.value || {}
  if (o.dateCount !== undefined) {
    return `采集日期 ${o.dateCount} 天 · 最新 ${o.latestJobs || 0} 条 · 其余 ${o.otherJobs || 0} 条`
  }
  return `快照 ${o.snapshotJobs || 0} · 批次 ${o.batchJobs || 0}`
})
const trendTip = computed(
  () => '按「04-清洗后数据」中各文件的修改时间分段，岗位提及率（%）；样本量＜20 的文件不参与；虚线为当前筛选时点'
)
const warningCount = computed(() => warnings.value.filter((item) => item.level !== 'low').length)
const courseRows = computed(() => {
  const list = curriculum.value.subjects || []
  if (!jobFamily.value) return list
  return list.filter((item) => item.matchedCount > 0)
})
const courseTableTitle = computed(() => {
  const total = curriculum.value.courseCount || 0
  if (!jobFamily.value) return `知识库课程库（${total} 门）`
  return `知识库课程库（${courseRows.value.length} 门命中 · 共 ${total} 门）`
})
const lastBatch = computed(() => {
  const batches = dataSource.value.batches || []
  return batches.length ? batches[batches.length - 1].batchDate : '暂无'
})

function familyParams() {
  const params = {}
  if (jobFamily.value) params.jobFamily = jobFamily.value
  if (batchDate.value) params.batchDate = batchDate.value
  return params
}

function levelType(level) {
  if (level === 'high') return 'danger'
  if (level === 'medium') return 'warning'
  return 'info'
}

function levelText(level) {
  if (level === 'high') return '高'
  if (level === 'medium') return '中'
  return '低'
}

function confidenceType(level) {
  if (level === '高') return 'success'
  if (level === '中') return 'warning'
  return 'info'
}

function toggleBatch(batch) {
  batchDate.value = batchDate.value === batch ? '' : batch
  loadAll()
}

function batchRowClass({ row }) {
  return batchDate.value && row.label === batchDate.value ? 'batch-row-active' : ''
}

function renderTrend() {
  if (!trendRef.value) return
  if (!trendChart) trendChart = echarts.init(trendRef.value)
  const batches = trendData.value.batches || []
  const totals = trendData.value.totals || []
  const selectedDay = (trendData.value.selectedBatch || '').slice(0, 10)
  const selected = batches.find((item) => item.indexOf(selectedDay) === 0) || ''
  const showMark = selected && batches.includes(selected)
  const series = (trendData.value.series || []).map((item, index) => ({
    name: item.name,
    type: 'line',
    smooth: true,
    symbolSize: 6,
    data: item.data,
    ...(index === 0 && showMark
      ? {
          markLine: {
            symbol: 'none',
            silent: true,
            lineStyle: { type: 'dashed', color: '#e6a23c' },
            label: { formatter: '筛选时点', color: '#e6a23c', position: 'insideEndTop' },
            data: [{ xAxis: selected }]
          }
        }
      : {})
  }))
  trendChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        formatter: (params) => {
          if (!params.length) return ''
          const index = params[0].dataIndex
          const lines = [`${params[0].axisValue}（样本 ${totals[index] || 0} 条）`]
          params.forEach((item) => lines.push(`${item.marker}${item.seriesName}：${item.value}%`))
          return lines.join('<br/>')
        }
      },
      legend: { type: 'scroll', bottom: 0 },
      grid: { left: 45, right: 20, top: 20, bottom: 66 },
      xAxis: {
        type: 'category',
        data: batches,
        boundaryGap: false,
        axisLabel: {
          formatter: (value, index) => `${value}\n${totals[index] || 0} 条`,
          lineHeight: 15
        }
      },
      yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
      series
    },
    true
  )
}

function renderCategory() {
  if (!categoryRef.value) return
  if (!categoryChart) categoryChart = echarts.init(categoryRef.value)
  const all = categoryList.value || []
  const top = all.slice(0, 8)
  const rest = all.slice(8).reduce((sum, item) => sum + (item.value || 0), 0)
  const slices = top.map((item) => ({ name: item.name, value: item.value }))
  if (rest > 0) slices.push({ name: '其他类别', value: rest })
  categoryChart.setOption(
    {
      tooltip: {
        trigger: 'item',
        formatter: (p) => (p.name === '其他类别' ? `其余 ${all.length - 8} 类合计：${p.value} 次 (${p.percent}%)` : `${p.name}: ${p.value} 次 (${p.percent}%)`)
      },
      legend: { orient: 'vertical', right: 10, top: 'center', type: 'scroll' },
      color: ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#c0c4cc'],
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          center: ['40%', '50%'],
          label: { show: false },
          data: slices
        }
      ]
    },
    true
  )
}

function renderRank() {
  if (!rankRef.value) return
  if (!rankChart) rankChart = echarts.init(rankRef.value)
  const data = (rankList.value || []).slice(0, 15).slice().reverse()
  rankChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const item = data[params[0].dataIndex]
          return `${item.skillName}<br/>岗位数：${item.jobCount}<br/>占比：${item.rate}%`
        }
      },
      grid: { left: 100, right: 50, top: 10, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: { type: 'category', data: data.map((item) => item.skillName) },
      series: [
        {
          type: 'bar',
          data: data.map((item) => item.jobCount),
          barMaxWidth: 16,
          itemStyle: { borderRadius: [0, 4, 4, 0], color: '#409eff' },
          label: { show: true, position: 'right', formatter: '{c}' }
        }
      ]
    },
    true
  )
}

function renderMajor() {
  if (!majorRef.value) return
  if (!majorChart) majorChart = echarts.init(majorRef.value)
  const data = (majorList.value || []).slice(0, 10).slice().reverse()
  majorChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const item = data[params[0].dataIndex]
          return `${item.majorName}<br/>方向：${item.jobFamily}<br/>岗位样本：${item.jobCount} 条<br/>匹配度：${item.matchRate}%<br/>覆盖技能：${item.matchedCount}/${item.totalSkills}${item.sampleEnough === false ? '<br/><span style="color:#909399">样本不足，仅供参考</span>' : ''}`
        }
      },
      grid: { left: 150, right: 60, top: 10, bottom: 20 },
      xAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
      yAxis: {
        type: 'category',
        data: data.map((item) => item.majorName),
        axisLabel: { width: 140, overflow: 'truncate' }
      },
      series: [
        {
          type: 'bar',
          data: data.map((item) => item.matchRate),
          barMaxWidth: 16,
          itemStyle: {
            borderRadius: [0, 4, 4, 0],
            color: (params) => {
              const item = data[params.dataIndex]
              if (item && item.sampleEnough === false) return '#c0c4cc'
              const value = params.value
              if (value >= 55) return '#67c23a'
              if (value >= 35) return '#e6a23c'
              return '#f56c6c'
            }
          },
          label: { show: true, position: 'right', formatter: '{c}%' }
        }
      ]
    },
    true
  )
}

function renderIndustry() {
  if (!industryRef.value) return
  if (!industryChart) industryChart = echarts.init(industryRef.value)
  const data = (dataSource.value.industries || []).slice().reverse()
  industryChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const item = data[params[0].dataIndex]
          return `${item.industry}<br/>岗位：${item.jobCount} 条<br/>占比：${item.rate}%<br/>覆盖方向：${item.familyCount} 个`
        }
      },
      grid: { left: 110, right: 60, top: 10, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: data.map((item) => item.industry),
        axisLabel: { width: 100, overflow: 'truncate' }
      },
      series: [
        {
          type: 'bar',
          data: data.map((item) => item.jobCount),
          barMaxWidth: 14,
          itemStyle: { borderRadius: [0, 4, 4, 0], color: '#409eff' },
          label: { show: true, position: 'right', formatter: '{c}' }
        }
      ]
    },
    true
  )
}

function renderFamily() {
  if (!familyRef.value) return
  if (!familyChart) familyChart = echarts.init(familyRef.value)
  const data = (dataSource.value.families || []).slice(0, 12).slice().reverse()
  familyChart.setOption(
    {
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params) => {
          const item = data[params[0].dataIndex]
          return `${item.jobFamily}<br/>岗位：${item.jobCount} 条<br/>占比：${item.rate}%`
        }
      },
      grid: { left: 130, right: 60, top: 10, bottom: 20 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: data.map((item) => item.jobFamily),
        axisLabel: { width: 120, overflow: 'truncate' }
      },
      series: [
        {
          type: 'bar',
          data: data.map((item) => item.jobCount),
          barMaxWidth: 14,
          itemStyle: { borderRadius: [0, 4, 4, 0], color: '#67c23a' },
          label: { show: true, position: 'right', formatter: '{c}' }
        }
      ]
    },
    true
  )
}

async function loadOverview() {
  const res = await getTalentOverview(familyParams())
  overview.value = res.data || {}
}

async function loadRank() {
  const res = await getSkillRank({ ...familyParams(), category: rankCategory.value, limit: 30 })
  rankList.value = res.data || []
  renderRank()
}

async function loadTrend() {
  // 趋势图始终取该方向的全部批次：只看方向，不叠加日期过滤，
  // 否则方向与日期没有交集时整张图会空（日期只作为虚线标记）
  const params = { topN: 8 }
  if (jobFamily.value) params.jobFamily = jobFamily.value
  let data = (await getSkillTrend(params)).data || { batches: [], series: [] }
  // 默认只统计样本 >= 20 的批次；若该方向所有批次都低于阈值，放宽到 1 条再取一次
  if (!data.series || !data.series.length) {
    const relaxed = (await getSkillTrend({ ...params, minJobs: 1 })).data
    if (relaxed && relaxed.series && relaxed.series.length) {
      relaxed.lowSample = true
      data = relaxed
    }
  }
  trendData.value = data
  trendData.value.selectedBatch = batchDate.value || ''
  renderTrend()
}

async function loadCategory() {
  const res = await getCategoryStat(familyParams())
  categoryList.value = res.data || []
  renderCategory()
}

async function loadMajor() {
  const res = await getMajorMatch(familyParams())
  majorList.value = res.data || []
  renderMajor()
}

async function loadWarnings() {
  const res = await getTalentWarnings(familyParams())
  warnings.value = res.data || []
}

async function loadSuggestions() {
  const res = await getTalentSuggestions(familyParams())
  suggestions.value = res.data || []
}

async function loadCurriculum() {
  const res = await getCurriculum(familyParams())
  curriculum.value = res.data || { subjects: [], basicCourses: [], majorCourses: [] }
}

async function loadBatches() {
  const res = await getTalentBatches()
  batchList.value = res.data || []
}

async function loadDataSource() {
  const res = await getDataSource(familyParams())
  dataSource.value = res.data || {
    families: [],
    industries: [],
    limitations: [],
    flow: []
  }
  renderIndustry()
  renderFamily()
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([
      loadBatches(),
      loadOverview(),
      loadRank(),
      loadTrend(),
      loadCategory(),
      loadMajor(),
      loadWarnings(),
      loadSuggestions(),
      loadCurriculum(),
      loadDataSource()
    ])
  } catch (e) {
    proxy.$modal.msgError(e.message || '加载人才培养看板失败')
  } finally {
    loading.value = false
  }
}

async function handleImport(options) {
  importing.value = true
  try {
    const res = await importTalentCsv(options.file, importBatch.value)
    const data = res.data || {}
    proxy.$modal.msgSuccess(
      `导入完成：${data.postingCount} 条岗位、${data.skillCount} 条技能，共 ${data.familyCount} 个方向 / ${data.industryCount} 个行业`
    )
    await loadAll()
  } catch (e) {
    proxy.$modal.msgError(e.message || '导入 CSV 失败')
  } finally {
    importing.value = false
  }
}

function handleResize() {
  trendChart?.resize()
  categoryChart?.resize()
  rankChart?.resize()
  majorChart?.resize()
  industryChart?.resize()
  familyChart?.resize()
}

// ---------------- 一键爬取 ----------------
function stopCrawlPolling() {
  if (crawlTimer) {
    clearInterval(crawlTimer)
    crawlTimer = null
  }
}

function applyCrawl(data) {
  const status = (data && data.status) || 'IDLE'
  crawl.value = {
    running: status === 'RUNNING' || status === 'IMPORTING',
    percent: data.percent || 0,
    message: data.message || ''
  }
  if (status === 'DONE') {
    stopCrawlPolling()
    proxy.$modal.msgSuccess(crawl.value.message || '爬取完成')
    loadAll()
  } else if (status === 'ERROR') {
    stopCrawlPolling()
    proxy.$modal.msgError(crawl.value.message || '爬取失败')
  }
}

async function startCrawl() {
  crawlStarting.value = true
  try {
    const res = await startTalentCrawl()
    applyCrawl(res.data || {})
    if (crawl.value.running) {
      startCrawlPolling()
    }
  } catch (e) {
    proxy.$modal.msgError(e.message || '启动爬取失败')
  } finally {
    crawlStarting.value = false
  }
}

function startCrawlPolling() {
  stopCrawlPolling()
  crawlTimer = setInterval(async () => {
    try {
      const res = await getTalentCrawlStatus()
      applyCrawl(res.data || {})
    } catch (e) {
      stopCrawlPolling()
    }
  }, 2000)
}

onMounted(() => {
  loadAll()
  window.addEventListener('resize', handleResize)
  // 页面刷新后如果爬取还在后台跑，继续显示进度
  getTalentCrawlStatus()
    .then((res) => {
      const data = res.data || {}
      if (data.status === 'RUNNING' || data.status === 'IMPORTING') {
        crawl.value = { running: true, percent: data.percent || 0, message: data.message || '' }
        startCrawlPolling()
      }
    })
    .catch(() => {})
})

onBeforeUnmount(() => {
  stopCrawlPolling()
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  categoryChart?.dispose()
  rankChart?.dispose()
  majorChart?.dispose()
  industryChart?.dispose()
  familyChart?.dispose()
})
</script>

<style scoped>
.talent-dashboard {
  padding-bottom: 20px;
}
.header-card {
  margin-bottom: 16px;
  border: none;
  background: linear-gradient(90deg, #ecf5ff 0%, #f5f9ff 100%);
}
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.header-info .title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}
.header-info .subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
}
.header-info .meta {
  margin-top: 10px;
  font-size: 12px;
  color: #909399;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.import-upload :deep(.el-upload) {
  display: inline-block;
}
.import-help {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1px solid #c0c4cc;
  color: #909399;
  font-size: 12px;
  cursor: help;
}
.batch-label {
  font-weight: 600;
  color: #303133;
}
.batch-tag {
  margin-left: 8px;
}
.batch-note {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #a8abb2;
}
:deep(.batch-row-active) {
  background: #f0f7ff;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.kpi-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 16px 18px;
}
.kpi-label {
  font-size: 13px;
  color: #909399;
}
.kpi-value {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.kpi-value.warn {
  color: #e6a23c;
}
.kpi-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #b1b3b8;
}
.chart-card,
.list-card {
  margin-bottom: 16px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  gap: 12px;
  flex-wrap: wrap;
}
.card-tip {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.chart-wrap {
  position: relative;
}
.chart {
  width: 100%;
  height: 100%;
}
.chart-empty {
  position: absolute;
  inset: 0;
  background: #fff;
}
.section-title {
  margin: 16px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding-left: 8px;
  border-left: 3px solid #409eff;
}
.section-title:first-child {
  margin-top: 0;
}
.basic-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  max-height: 360px;
  overflow-y: auto;
  padding-right: 4px;
}
.basic-item {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fafcff;
}
.basic-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.basic-meta {
  margin-top: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
}
.basic-family {
  margin-top: 6px;
  font-size: 11px;
  color: #a8abb2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.warn-list,
.suggest-list {
  max-height: 460px;
  overflow-y: auto;
}
.warn-item {
  padding: 12px 0;
  border-bottom: 1px dashed #ebeef5;
}
.warn-item:last-child {
  border-bottom: none;
}
.warn-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.warn-type {
  font-size: 12px;
  color: #909399;
}
.warn-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.warn-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}
.warn-basis {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #a8abb2;
}
.suggest-item {
  display: flex;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px dashed #ebeef5;
}
.suggest-item:last-child {
  border-bottom: none;
}
.suggest-index {
  flex: 0 0 26px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #ecf5ff;
  color: #409eff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.suggest-body {
  flex: 1;
  min-width: 0;
}
.suggest-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.suggest-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.suggest-content {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}
.suggest-basis {
  margin-top: 8px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #a8abb2;
}
.family-bar-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.family-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
  color: #606266;
}
.family-name {
  flex: 0 0 110px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.family-progress {
  flex: 1;
}
.family-value {
  flex: 0 0 100px;
  text-align: right;
  color: #909399;
}
.note-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12px;
  line-height: 1.8;
  color: #606266;
}
.note-list li {
  margin-bottom: 2px;
}
.kb-alert {
  margin-bottom: 12px;
}
.section-sub {
  margin-left: 6px;
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}
.skill-tag {
  margin: 0 4px 4px 0;
}
.muted {
  font-size: 12px;
  color: #b1b3b8;
}
.chart-note {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
.ds-stat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 14px;
  margin-bottom: 18px;
}
.ds-stat {
  background: #fafcff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 14px 16px;
}
.ds-stat-label {
  font-size: 13px;
  color: #909399;
}
.ds-stat-value {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}
.ds-stat-time {
  font-size: 18px;
  font-weight: 600;
}
.ds-unit {
  margin-left: 4px;
  font-size: 13px;
  font-weight: 400;
  color: #909399;
}
.ds-stat-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #b1b3b8;
}
.ds-charts {
  margin-bottom: 8px;
}
.ds-chart-title {
  margin-bottom: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}
.ds-chart {
  width: 100%;
  height: 320px;
}
.ds-tabs {
  margin-top: 6px;
}
@media (max-width: 1200px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .basic-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .ds-stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.crawl-box {
  display: flex;
  align-items: center;
  gap: 10px;
}
.crawl-msg {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
