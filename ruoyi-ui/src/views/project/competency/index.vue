<template>
  <div class="app-container competency-page">
    <el-card shadow="never" class="toolbar-card">
      <el-row :gutter="12" align="middle">
        <el-col :xs="24" :sm="10" :md="8">
          <el-autocomplete
            v-model="jobInput"
            :fetch-suggestions="queryJobSuggestions"
            value-key="name"
            clearable
            placeholder="输入岗位名称，如 计算机程序设计员"
            size="large"
            :trigger-on-focus="false"
            style="width: 100%"
            @keyup.enter="handleGenerate"
          >
            <template #default="{ item }">
              <div class="suggest-item">
                <span class="suggest-name">{{ item.name }}</span>
                <span v-if="item.code" class="suggest-code">{{ item.code }}</span>
              </div>
            </template>
          </el-autocomplete>
        </el-col>
        <el-col :xs="24" :sm="8" :md="6">
          <div class="action-bar">
            <el-button type="primary" size="large" :loading="generating" @click="handleGenerate">
              生成图谱
            </el-button>
          </div>
        </el-col>
        <el-col :xs="24" :md="10">
          <span class="hint">
            已生成过的岗位会直接读取数据库记录，不会重复调用模型；首次生成约 2–3 分钟，结果待审核
          </span>
        </el-col>
      </el-row>
    </el-card>

    <el-row :gutter="16">
      <el-col :span="24">
        <el-card
          ref="reportRef"
          v-loading="detailLoading"
          element-loading-text="正在加载图谱..."
          shadow="never"
          class="graph-card"
        >
          <template #header>
            <div class="card-header">
              <div>
                <span class="title">{{ currentJobName || '岗位能力图谱' }}</span>
                <span v-if="currentMeta" class="meta">{{ currentMeta }}</span>
              </div>
              <span v-if="exportStatus" class="export-status">{{ exportStatus }}</span>
              <el-button
                v-if="currentGraph"
                v-show="!exporting"
                size="small"
                :loading="exporting"
                @click="exportPdf"
              >
                导出 PDF
              </el-button>
            </div>
          </template>

          <div v-if="!currentGraph" class="empty">
            输入岗位名称并点击「生成图谱」
          </div>

          <template v-else>
            <el-alert
              v-if="!isV3"
              type="error"
              :closable="false"
              show-icon
              title="该记录不是 v3 契约，页面不再做静默兼容，请用 KB 或 LLM 模式重新生成"
            />
            <template v-if="isV3">
              <div class="chain-panel">
                <div class="chain-line">
                  <span class="chain-label">产业口径</span>
                  <template v-if="chainDetail">
                    <el-tag
                      size="small"
                      type="success"
                      class="chain-tag"
                      title="点击查看产业链与岗位群详情"
                      @click="openChainDetail"
                    >{{ chainDetail.chainName }}</el-tag>
                    <span v-if="chainDetail.stageName" class="arrow">→</span>
                    <el-tag
                      v-if="chainDetail.stageName"
                      size="small"
                      type="warning"
                      class="chain-tag"
                      title="点击查看产业链与岗位群详情"
                      @click="openChainDetail"
                    >{{ chainDetail.stageName }}</el-tag>
                    <span v-if="chainDetail.clusterName" class="arrow">→</span>
                    <el-tag
                      v-if="chainDetail.clusterName"
                      size="small"
                      class="chain-tag"
                      title="点击查看产业链与岗位群详情"
                      @click="openChainDetail"
                    >{{ chainDetail.clusterName }}</el-tag>
                    <span class="chain-hint">（点击查看官方分类详情）</span>
                  </template>
                  <template v-else>
                    <template v-for="(item, index) in (currentGraph.industry_path || [])" :key="item.id">
                      <el-tag
                        size="small"
                        :type="item.mapping_type === 'teaching' ? 'info' : 'success'"
                        class="chain-tag"
                        title="点击查看产业链与岗位群详情"
                        @click="openChainDetail"
                      >
                        {{ item.name }}
                      </el-tag>
                      <span v-if="index < currentGraph.industry_path.length - 1" class="arrow">→</span>
                    </template>
                    <span class="chain-hint">（点击查看模型补充的产业链/岗位群）</span>
                  </template>
                </div>
                <div class="chain-line">
                  <span class="chain-label">标准口径</span>
                  <template v-for="(item, index) in (currentGraph.classification_path || [])" :key="item.name">
                    <el-tag size="small">{{ item.name }}</el-tag>
                    <span v-if="index < currentGraph.classification_path.length - 1" class="arrow">→</span>
                  </template>
                </div>
              </div>

              <el-row :gutter="10" class="stats">
              <el-col v-for="item in statCards" :key="item.label" :xs="8" :sm="4">
                <div class="stat">
                  <b>{{ item.value }}</b>
                  <span>{{ item.label }}</span>
                </div>
              </el-col>
            </el-row>

            <el-tabs v-model="activeTab" @tab-change="renderActive">
              <el-tab-pane label="结构视图" name="structure">
                <div class="view-switch">
                  <el-radio-group v-model="structureView" size="small" @change="renderStructure">
                    <el-radio-button value="mindmap">思维导图</el-radio-button>
                    <el-radio-button value="relation">关系图</el-radio-button>
                  </el-radio-group>
                  <span class="chain-title">产业链 → 岗位群 → 能力项 → 能力单元 → 技能 / 知识</span>
                </div>
                <div v-if="structureView === 'mindmap'" class="legend-bar">
                  <span class="legend-item"><i class="dot dot-job"></i>{{ currentJobName || '岗位' }}（当前岗位，高亮）</span>
                  <span class="legend-item"><i class="dot dot-industry"></i>产业链</span>
                  <span class="legend-item"><i class="dot dot-cluster"></i>岗位群</span>
                  <span class="legend-item"><i class="dot dot-competency"></i>能力项</span>
                  <span class="legend-item"><i class="dot dot-unit"></i>能力单元</span>
                  <span class="legend-item"><i class="dot dot-skill"></i>技能点</span>
                  <span class="legend-item"><i class="dot dot-knowledge"></i>知识点</span>
                </div>
                <div
                  v-show="structureView === 'mindmap'"
                  ref="mindmapRef"
                  class="chart tall"
                  :style="{ height: structureHeight + 'px' }"
                ></div>
                <div
                  v-show="structureView === 'relation'"
                  ref="relationRef"
                  class="chart tall relation-chart"
                ></div>
              </el-tab-pane>
              <el-tab-pane label="能力雷达图" name="radar">
                <div ref="radarRef" class="chart"></div>
              </el-tab-pane>
              <el-tab-pane label="能力明细" name="detail">
                <div class="domain-map">
                  <div class="domain-map-title">能力域 → 能力项（与雷达维度一一对应）</div>
                  <div v-for="domain in (currentGraph.domains || [])" :key="domain.id" class="domain-map-row">
                    <span class="domain-map-name">{{ domain.name }}（{{ displayDomainWeight(domain) }}%）</span>
                    <span class="arrow">→</span>
                    <span>{{ competencyNames(domain.id) }}</span>
                  </div>
                </div>
                <el-collapse v-model="openCompetencies">
                  <el-collapse-item
                    v-for="competency in competencies"
                    :key="competency.id"
                    :name="competency.id"
                  >
                    <template #title>
                      <b>{{ competency.name }}</b>
                      <el-tag size="small" class="tag" type="info">{{ domainName(competency.domain_id) }}</el-tag>
                      <el-tag v-if="competency.weight != null" size="small" class="tag">
                        权重 {{ competency.weight }}%
                      </el-tag>
                    </template>
                    <div v-for="unit in (competency.units || [])" :key="unit.id" class="unit">
                      <div class="unit-title">
                        <span>{{ unit.statement }}</span>
                      </div>
                      <div v-if="(unit.skills || []).length" class="unit-line">
                        <span class="line-label">技能要求</span>
                        <el-tag v-for="(skill, index) in unit.skills" :key="index" size="small" class="point-tag">
                          {{ skill.name }}
                        </el-tag>
                      </div>
                      <div v-if="(unit.knowledge || []).length" class="unit-line">
                        <span class="line-label">相关知识</span>
                        <el-tag v-for="(point, index) in unit.knowledge" :key="index" size="small" type="warning" class="point-tag">
                          {{ point.name }}
                        </el-tag>
                      </div>
                    </div>
                  </el-collapse-item>
                </el-collapse>
              </el-tab-pane>
            </el-tabs>
            </template>
          </template>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="chainDialog" title="产业链与岗位群（官方分类）" width="620px" append-to-body>
      <div v-if="chainDetail" class="chain-dialog">
        <div class="chain-dialog-block">
          <div class="chain-dialog-head">产业链 / 行业</div>
          <p class="chain-dialog-name">
            {{ chainDetail.chainName }}
            <span v-if="chainDetail.chainCode" class="chain-dialog-code">{{ chainDetail.chainCode }}</span>
          </p>
          <p class="chain-dialog-desc">{{ chainDetail.chainDesc || '—' }}</p>
        </div>
        <div class="chain-dialog-block">
          <div class="chain-dialog-head">产业链环节</div>
          <p class="chain-dialog-name">{{ chainDetail.stageName || '—' }}</p>
          <p class="chain-dialog-desc">{{ chainDetail.stageDesc || '—' }}</p>
        </div>
        <div class="chain-dialog-block">
          <div class="chain-dialog-head">岗位群（职业分类大典小类）</div>
          <p class="chain-dialog-name">
            {{ chainDetail.clusterName || '—' }}
            <span v-if="chainDetail.clusterCode" class="chain-dialog-code">{{ chainDetail.clusterCode }}</span>
          </p>
          <p class="chain-dialog-desc">{{ chainDetail.clusterDesc || '—' }}</p>
        </div>
        <div class="chain-dialog-meta">
          <p>对应岗位：{{ chainDetail.jobName }}<span v-if="chainDetail.jobCode">（{{ chainDetail.jobCode }}）</span></p>
          <p>数据来源：{{ chainDetail.source || '—' }}</p>
          <p v-if="chainDetail.sourceUrl">
            官方链接：<a :href="chainDetail.sourceUrl" target="_blank" rel="noopener">{{ chainDetail.sourceUrl }}</a>
          </p>
        </div>
      </div>
      <div v-else-if="llmIndustryPath.length" class="chain-dialog">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          title="官方未收录，以下产业链/岗位群由模型补充，仅作参考"
        />
        <div class="chain-dialog-block">
          <div class="chain-dialog-head">产业链路径（模型补充）</div>
          <p class="chain-dialog-name">
            {{ llmIndustryPath.map(item => item.name).join(' → ') }}
          </p>
          <p v-if="classificationPath.length" class="chain-dialog-desc">
            职业分类：{{ classificationPath.map(item => item.name).join(' → ') }}
          </p>
        </div>
        <div class="chain-dialog-meta">
          <p>数据来源：大模型生成，未核对官方分类</p>
        </div>
      </div>
      <el-empty v-else description="该岗位暂无官方产业链/岗位群映射，可在「岗位能力管理 - 产业链管理」中补充" />
    </el-dialog>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'
import {
  getCompetencyGraph,
  generateCompetencyGraph,
  suggestCompetencyJobs
} from '@/api/project/competency'

export default {
  name: 'JobCompetencyGraph',
  data() {
    return {
      jobInput: '计算机程序设计员',
      generating: false,
      exporting: false,
      exportStatus: '',
      detailLoading: false,
      currentGraph: null,
      currentRecord: null,
      officialWeights: null,
      chainDetail: null,
      chainDialog: false,
      activeTab: 'structure',
      structureView: 'mindmap',
      openCompetencies: [],
      charts: {}
    }
  },
  computed: {
    isV3() {
      return !!this.currentGraph && this.currentGraph.schema_version === '3.0.0'
    },
    currentLevel() {
      const job = (this.currentGraph && this.currentGraph.job) || {}
      const level = String(job.level || '')
      return /^[一二三四五]级/.test(level) ? level.slice(0, 2) : ''
    },
    currentJobName() {
      const graph = this.currentGraph
      if (!graph) return ''
      return (graph.job && graph.job.name) || (this.currentRecord && this.currentRecord.jobName) || ''
    },
    currentMeta() {
      if (!this.currentGraph) return ''
      const job = this.currentGraph.job || {}
      const parts = []
      if (job.code) parts.push('职业编码 ' + job.code)
      if (job.level) parts.push(job.level)
      if (this.currentRecord && this.currentRecord.createTime) parts.push(this.currentRecord.createTime)
      return parts.join(' · ')
    },
    competencies() {
      return (this.currentGraph && this.currentGraph.competencies) || []
    },
    llmIndustryPath() {
      const path = (this.currentGraph && this.currentGraph.industry_path) || []
      return path.filter(item => item && item.name
        && item.name.indexOf('待补充') < 0 && item.level !== '职业岗位')
    },
    classificationPath() {
      return (this.currentGraph && this.currentGraph.classification_path) || []
    },
    structureHeight() {
      const nodes = (this.currentGraph && this.currentGraph.views
        && this.currentGraph.views.graph && this.currentGraph.views.graph.nodes) || []
      let leaves = 0
      nodes.forEach(node => {
        if (node.category === 'SKILL_POINT' || node.category === 'KNOWLEDGE_POINT') leaves += 1
      })
      return Math.min(2800, Math.max(760, leaves * 34 + 180))
    },
    sources() {
      return (this.currentGraph && this.currentGraph.sources) || []
    },
    statCards() {
      const graph = this.currentGraph || {}
      const coverage = graph.coverage || {}
      const tasks = graph.tasks || []
      const competencies = graph.competencies || []
      const units = competencies.reduce((sum, item) => sum + ((item.units || []).length), 0)
      const skills = (graph.elements && graph.elements.skills) || []
      const knowledge = (graph.elements && graph.elements.knowledge) || []
      const rate = coverage.knowledge_coverage != null ? coverage.knowledge_coverage + '%' : '—'
      return [
        { label: '工作任务', value: tasks.length },
        { label: '能力项', value: competencies.length },
        { label: '能力单元', value: units },
        { label: '技能点', value: skills.length },
        { label: '知识点', value: knowledge.length },
        { label: '课程库匹配率', value: rate }
      ]
    }
  },
  mounted() {
    const id = this.$route && this.$route.query ? this.$route.query.id : null
    if (id) {
      this.loadRecordById(Number(id))
    }
    window.addEventListener('resize', this.handleResize)
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.handleResize)
    Object.values(this.charts).forEach(chart => chart && chart.dispose())
    this.charts = {}
  },
  methods: {
    queryJobSuggestions(keyword, callback) {
      const query = (keyword || '').trim()
      if (!query) {
        callback([])
        return
      }
      suggestCompetencyJobs(query)
        .then(res => callback(res.data || []))
        .catch(() => callback([]))
    },
    loadRecordById(id) {
      this.detailLoading = true
      getCompetencyGraph(id)
        .then(res => {
          const record = res.data || {}
          let graph = null
          try {
            graph = JSON.parse(record.graphJson || '{}')
          } catch (e) {
            this.$modal.msgError('图谱数据不是合法 JSON')
            return
          }
          this.currentRecord = record
          this.chainDetail = record.chainDetail || null
          this.applyGraph(graph)
          this.officialWeights = record.officialWeights || null
        })
        .catch(() => {
          this.$modal.msgError('图谱加载失败，请稍后重试')
        })
        .finally(() => {
          this.detailLoading = false
        })
    },
    handleGenerate() {
      const job = (this.jobInput || '').trim()
      if (!job) {
        this.$modal.msgWarning('请输入岗位名称')
        return
      }
      this.generating = true
      generateCompetencyGraph(job)
        .then(res => {
          const data = res.data || {}
          this.currentRecord = data
          this.chainDetail = data.chainDetail || null
          this.applyGraph(data.graph || {})
          this.officialWeights = data.officialWeights || null
          this.$modal.msgSuccess(data.reused
            ? '该岗位已有图谱，已直接读取数据库记录'
            : '图谱已生成并入库，状态为待审核')
        })
        .catch(error => {
          const message = (error && error.message) || '生成失败'
          this.$modal.msgError(message)
        })
        .finally(() => {
          this.generating = false
        })
    },
    applyGraph(graph) {
      this.applyChainToGraph(graph)
      this.currentGraph = graph
      this.officialWeights = null
      this.activeTab = 'structure'
      this.structureView = 'mindmap'
      this.openCompetencies = ((graph.competencies || []).slice(0, 1)).map(item => item.id)
      this.$nextTick(() => {
        this.renderStructure()
      })
    },
    applyChainToGraph(graph) {
      const chain = this.chainDetail
      if (!chain || !graph || !graph.views || !graph.views.graph) return
      const nodes = graph.views.graph.nodes || []
      nodes.forEach(node => {
        if (node.category === 'INDUSTRY_CHAIN' && chain.chainName) node.name = chain.chainName
        else if (node.category === 'INDUSTRY_SEGMENT' && chain.stageName) node.name = chain.stageName
        else if (node.category === 'JOB_CLUSTER' && chain.clusterName) node.name = chain.clusterName
      })
    },
    openChainDetail() {
      this.chainDialog = true
    },
    bindChainClick(chart, categories) {
      chart.off('click')
      chart.on('click', params => {
        if (!params || params.dataType !== 'node' || !params.data) return
        const category = categories[params.data.category] || params.data.category
        if (['INDUSTRY_CHAIN', 'INDUSTRY_SEGMENT', 'JOB_CLUSTER'].indexOf(category) >= 0) {
          this.openChainDetail()
        }
      })
    },
    renderActive(name) {
      const tab = name || this.activeTab
      this.$nextTick(() => {
        if (tab === 'structure') this.renderStructure()
        else if (tab === 'radar') this.renderRadar()
      })
    },
    renderStructure() {
      this.$nextTick(() => {
        if (this.structureView === 'mindmap') this.renderMindmap()
        else this.renderRelation()
      })
    },
    competencyNames(domainId) {
      return (this.competencies || [])
        .filter(item => item.domain_id === domainId)
        .map(item => item.name)
        .join('、') || '—'
    },
    handleResize() {
      Object.values(this.charts).forEach(chart => chart && chart.resize())
    },
    ensureChart(key, refName) {
      const element = this.$refs[refName]
      if (!element) return null
      if (!this.charts[key]) {
        this.charts[key] = echarts.init(element)
      }
      return this.charts[key]
    },
    renderMindmap() {
      const graph = this.currentGraph
      const relation = (graph && graph.views && graph.views.graph) || {}
      const nodes = relation.nodes || []
      const edges = relation.edges || []
      const chart = this.ensureChart('mindmap', 'mindmapRef')
      if (!chart) return
      chart.resize()
      if (!nodes.length) {
        this.showChartMessage(chart, '数据不符合 v3 契约：缺少 views.graph')
        return
      }
      const labels = {
        INDUSTRY_CHAIN: '产业链', INDUSTRY_SEGMENT: '产业链环节', JOB_CLUSTER: '岗位群',
        JOB: '岗位', CLASS_MAJOR: '职业大类', CLASS_MIDDLE: '职业中类',
        CLASS_MINOR: '职业小类', CLASS_OCCUPATION: '职业细类',
        WORK_TASK: '工作任务', DOMAIN: '能力域', COMPETENCY: '能力项',
        COMPETENCY_UNIT: '能力单元', SKILL_POINT: '技能点', KNOWLEDGE_POINT: '知识点'
      }
      const relationLabels = {
        IN_CLUSTER: '所属产业链环节',
        BELONGS_TO: '归属于岗位群',
        REQUIRES: '要求具备能力',
        CONTAINS: '包含能力单元',
        SUPPORTED_BY: '由技能/知识支撑'
      }
      const colors = {
        INDUSTRY_CHAIN: '#1f6feb', INDUSTRY_SEGMENT: '#3f8ef2', JOB_CLUSTER: '#0f9b8e',
        JOB: '#e6501e', COMPETENCY: '#6f4ef2', DOMAIN: '#6f4ef2',
        COMPETENCY_UNIT: '#33507a', SKILL_POINT: '#2f9e44', KNOWLEDGE_POINT: '#f0a020'
      }
      const sizes = {
        INDUSTRY_CHAIN: 22, JOB_CLUSTER: 18, JOB: 36, COMPETENCY: 15,
        COMPETENCY_UNIT: 10, SKILL_POINT: 7, KNOWLEDGE_POINT: 7
      }
      const categories = []
      nodes.forEach(node => {
        if (categories.indexOf(node.category) < 0) categories.push(node.category)
      })
      const layout = this.layeredPositions(nodes, edges)
      const width = chart.getWidth() || 900
      const height = chart.getHeight() || 1200
      const padLeft = 50
      const padRight = 320
      const padTop = 36
      const padBottom = 36
      const data = nodes.map(node => {
        const point = layout.positions[node.id] || { depth: 3, y: 0 }
        const isJob = node.category === 'JOB'
        const x = padLeft + (point.depth / (layout.maxDepth || 1)) * (width - padLeft - padRight)
        const y = padTop + (point.y / (layout.maxY || 1)) * (height - padTop - padBottom)
        return {
          id: node.id,
          name: node.name,
          category: categories.indexOf(node.category),
          value: node.value,
          x,
          y,
          symbolSize: isJob ? 36 : (sizes[node.category] || 9),
          itemStyle: isJob
            ? {
              color: '#e6501e',
              borderColor: '#b03a12',
              borderWidth: 3,
              shadowBlur: 18,
              shadowColor: 'rgba(230, 80, 30, 0.45)'
            }
            : { color: colors[node.category] || '#9db6d8' },
          label: {
            show: true,
            position: isJob ? 'bottom' : 'right',
            distance: isJob ? 10 : 5,
            fontSize: isJob ? 15 : (node.category === 'COMPETENCY' ? 12 : 10.5),
            fontWeight: isJob || node.category === 'COMPETENCY' ? 'bold' : 'normal',
            color: isJob ? '#c2400f' : (colors[node.category] || '#33507a'),
            backgroundColor: isJob ? '#fff1e8' : 'transparent',
            borderColor: isJob ? '#f5b183' : 'transparent',
            borderWidth: isJob ? 1 : 0,
            borderRadius: 4,
            padding: isJob ? [4, 8] : 0
          }
        }
      })
      const links = edges.map(edge => ({
        source: edge.source,
        target: edge.target,
        relation: edge.relation,
        lineStyle: {
          color: edge.relation === 'SUPPORTED_BY' ? '#c3d4e6' : '#8fb0d9',
          curveness: edge.relation === 'SUPPORTED_BY' ? 0.1 : 0.05
        }
      }))
      chart.setOption({
        tooltip: {
          formatter: params => {
            if (params.dataType === 'edge') {
              return (relationLabels[params.data.relation] || params.data.relation || '')
                + '<br/>' + params.data.source + ' → ' + params.data.target
            }
            return (labels[params.data.category] || '') + '<br/>' + params.name
          }
        },
        series: [{
          type: 'graph',
          layout: 'none',
          roam: true,
          draggable: true,
          categories: categories.map(name => ({ name: name })),
          data: data,
          links: links,
          labelLayout: { hideOverlap: true },
          lineStyle: { opacity: 0.55, width: 1.1 },
          emphasis: { focus: 'adjacency', lineStyle: { width: 2.4 } }
        }]
      }, true)
      this.bindChainClick(chart, categories)
    },
    officialRadarData() {
      const official = this.officialWeights
      if (!official || !(official.domains || []).length) return null
      const level = this.currentLevel
      const items = official.domains
        .map(item => ({
          name: item.name,
          value: item.weights ? item.weights[level] : null
        }))
        .filter(item => item.value !== null && item.value !== undefined)
      return items.length ? items : null
    },
    officialDomainWeight(name) {
      const official = this.officialWeights
      if (!official || !(official.domains || []).length) return null
      const found = official.domains.find(item => item.name === name)
      if (!found || !found.weights) return null
      const value = found.weights[this.currentLevel]
      return value === undefined ? null : value
    },
    displayDomainWeight(domain) {
      const official = this.officialDomainWeight(domain.name)
      return official !== null ? official : domain.weight
    },
    renderRadar() {
      const graph = this.currentGraph
      const chart = this.ensureChart('radar', 'radarRef')
      if (!chart) return
      const official = this.officialRadarData()
      let indicators
      let series
      if (official) {
        indicators = official.map(item => ({ name: item.name, max: 100 }))
        series = [{
          name: '国家职业标准（' + (this.currentLevel || '当前等级') + '）',
          value: official.map(item => item.value)
        }]
      } else {
        const radar = (graph && graph.views && graph.views.radar) || {}
        indicators = (radar.indicators || []).map(item => ({ name: item.name, max: item.max || 100 }))
        series = (radar.series || []).map(item => ({ name: item.name, value: item.values || [] }))
      }
      if (!indicators.length || !series.length) {
        this.showChartMessage(chart, '数据不符合 v3 契约：缺少 views.radar.series')
        return
      }
      chart.setOption({
        tooltip: { trigger: 'item' },
        legend: { bottom: 0, data: series.map(item => item.name) },
        radar: {
          indicator: indicators,
          radius: '62%',
          splitNumber: 4,
          axisName: { fontSize: 11, color: '#4b5b70' },
          splitLine: { lineStyle: { color: '#e3eaf3' } },
          splitArea: { areaStyle: { color: ['#ffffff', '#f7fafd'] } }
        },
        series: [{
          type: 'radar',
          data: series,
          areaStyle: { opacity: 0.18 },
          lineStyle: { width: 2 }
        }]
      }, true)
    },
    layeredPositions(nodes, edges) {
      const nodeIds = {}
      nodes.forEach(node => { nodeIds[node.id] = true })
      const childMap = {}
      const relationTypes = ['IN_CLUSTER', 'BELONGS_TO', 'REQUIRES', 'CONTAINS', 'SUPPORTED_BY']
      edges.forEach(edge => {
        if (relationTypes.indexOf(edge.relation) < 0) return
        if (!nodeIds[edge.source] || !nodeIds[edge.target]) return
        if (!childMap[edge.source]) childMap[edge.source] = []
        childMap[edge.source].push(edge.target)
      })
      const targets = {}
      edges.forEach(edge => { targets[edge.target] = true })
      let root = nodes.find(node => node.category === 'INDUSTRY_CHAIN')
      if (!root) root = nodes.find(node => !targets[node.id])
      const positions = {}
      let leafIndex = 0
      let maxDepth = 0
      const visit = (id, depth) => {
        if (positions[id]) return positions[id].y
        if (depth > maxDepth) maxDepth = depth
        const kids = (childMap[id] || []).filter(kid => !positions[kid])
        if (!kids.length) {
          positions[id] = { depth, y: leafIndex }
          leafIndex += 1
          return positions[id].y
        }
        let first = null
        let last = null
        kids.forEach(kid => {
          const y = visit(kid, depth + 1)
          if (first === null) first = y
          last = y
        })
        positions[id] = { depth, y: (first + last) / 2 }
        return positions[id].y
      }
      if (root) visit(root.id, 0)
      nodes.forEach(node => {
        if (!positions[node.id]) {
          positions[node.id] = { depth: 3, y: leafIndex }
          leafIndex += 1
        }
      })
      return { positions, maxDepth, maxY: Math.max(leafIndex - 1, 1) }
    },
    renderRelation() {
      const graph = this.currentGraph
      const relation = (graph && graph.views && graph.views.graph) || {}
      const nodes = relation.nodes || []
      const edges = relation.edges || []
      const chart = this.ensureChart('relation', 'relationRef')
      if (!chart) return
      chart.resize()
      if (!nodes.length) {
        this.showChartMessage(chart, '数据不符合 v3 契约：缺少 views.graph')
        return
      }
      const labels = {
        INDUSTRY_CHAIN: '产业链', INDUSTRY_SEGMENT: '产业链环节', JOB_CLUSTER: '岗位群',
        JOB: '岗位', CLASS_MAJOR: '职业大类', CLASS_MIDDLE: '职业中类',
        CLASS_MINOR: '职业小类', CLASS_OCCUPATION: '职业细类',
        WORK_TASK: '工作任务', DOMAIN: '能力域', COMPETENCY: '能力项',
        COMPETENCY_UNIT: '能力单元', SKILL_POINT: '技能点', KNOWLEDGE_POINT: '知识点'
      }
      const relationLabels = {
        IN_CLUSTER: '所属产业链环节',
        BELONGS_TO: '归属于岗位群',
        REQUIRES: '要求具备能力',
        CONTAINS: '包含能力单元',
        SUPPORTED_BY: '由技能/知识支撑'
      }
      const colors = {
        INDUSTRY_CHAIN: '#1f6feb', INDUSTRY_SEGMENT: '#3f8ef2', JOB_CLUSTER: '#0f9b8e',
        JOB: '#e6501e', COMPETENCY: '#6f4ef2', DOMAIN: '#6f4ef2',
        COMPETENCY_UNIT: '#33507a', SKILL_POINT: '#2f9e44', KNOWLEDGE_POINT: '#f0a020'
      }
      const sizes = {
        INDUSTRY_CHAIN: 44, JOB_CLUSTER: 42, JOB: 52, COMPETENCY: 30,
        COMPETENCY_UNIT: 16, SKILL_POINT: 10, KNOWLEDGE_POINT: 10
      }
      const categories = []
      nodes.forEach(node => {
        if (categories.indexOf(node.category) < 0) categories.push(node.category)
      })
      const labelCategories = ['INDUSTRY_CHAIN', 'INDUSTRY_SEGMENT', 'JOB_CLUSTER', 'JOB',
        'CLASS_MAJOR', 'CLASS_MIDDLE', 'CLASS_MINOR', 'CLASS_OCCUPATION',
        'WORK_TASK', 'DOMAIN', 'COMPETENCY']
      const data = nodes.map(node => {
        const isJob = node.category === 'JOB'
        return {
          id: node.id,
          name: node.name,
          category: categories.indexOf(node.category),
          value: node.value,
          symbolSize: isJob ? 52 : (sizes[node.category] || 12),
          itemStyle: isJob
            ? {
              color: '#e6501e',
              borderColor: '#b03a12',
              borderWidth: 3,
              shadowBlur: 18,
              shadowColor: 'rgba(230, 80, 30, 0.45)'
            }
            : { color: colors[node.category] || '#9db6d8' },
          label: isJob
            ? {
              show: true,
              position: 'bottom',
              distance: 10,
              fontSize: 15,
              fontWeight: 'bold',
              color: '#c2400f',
              backgroundColor: '#fff1e8',
              borderColor: '#f5b183',
              borderWidth: 1,
              borderRadius: 4,
              padding: [4, 8]
            }
            : {
              show: labelCategories.indexOf(node.category) >= 0,
              position: 'right',
              fontSize: 11,
              color: '#2c3e56'
            }
        }
      })
      const links = edges.map(edge => ({
        source: edge.source,
        target: edge.target,
        relation: edge.relation,
        lineStyle: {
          color: edge.relation === 'SUPPORTED_BY' ? '#c3d4e6' : '#8fb0d9',
          curveness: edge.relation === 'SUPPORTED_BY' ? 0.1 : 0.05
        }
      }))
      chart.setOption({
        tooltip: {
          formatter: params => {
            if (params.dataType === 'edge') {
              return (relationLabels[params.data.relation] || params.data.relation || '')
                + '<br/>' + params.data.source + ' → ' + params.data.target
            }
            return (labels[params.data.category] || '') + '<br/>' + params.name
          }
        },
        legend: {
          bottom: 0,
          data: categories,
          formatter: name => labels[name] || name
        },
        series: [{
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          categories: categories.map(name => ({
            name: name,
            itemStyle: { color: colors[name] || '#9db6d8' }
          })),
          data: data,
          links: links,
          force: { repulsion: 220, edgeLength: [60, 130], gravity: 0.08 },
          lineStyle: { opacity: 0.55, width: 1.1 },
          emphasis: { focus: 'adjacency', lineStyle: { width: 2 } }
        }]
      }, true)
      this.bindChainClick(chart, categories)
    },
    showChartMessage(chart, text) {
      chart.clear()
      chart.setOption({
        title: {
          text: text,
          left: 'center',
          top: 'middle',
          textStyle: { color: '#f56c6c', fontSize: 14 }
        }
      })
    },
    domainName(domainId) {
      const domains = (this.currentGraph && this.currentGraph.domains) || []
      const found = domains.find(item => item.id === domainId)
      return found ? found.name : domainId || ''
    },
    async exportPdf() {
      if (!this.currentGraph) return
      this.exporting = true
      this.exportStatus = '正在生成 PDF...'
      this.$modal.loading('正在导出 PDF...')
      try {
        const element = this.$refs.reportRef && (this.$refs.reportRef.$el || this.$refs.reportRef)
        if (!element) throw new Error('未找到导出区域')
        const canvas = await html2canvas(element, {
          scale: 2,
          useCORS: true,
          backgroundColor: '#ffffff',
          logging: false
        })
        const pdf = new jsPDF('p', 'mm', 'a4')
        const pageWidth = 210
        const pageHeight = 297
        const margin = 10
        const usableWidth = pageWidth - margin * 2
        const usableHeight = pageHeight - margin * 2
        const imgWidth = usableWidth
        const imgHeight = canvas.height * imgWidth / canvas.width
        const imgData = canvas.toDataURL('image/jpeg', 0.92)
        const totalPages = Math.max(1, Math.ceil(imgHeight / usableHeight))

        let heightLeft = imgHeight
        let position = margin
        pdf.addImage(imgData, 'JPEG', margin, position, imgWidth, imgHeight)
        heightLeft -= usableHeight
        while (heightLeft > 0) {
          position = margin - (imgHeight - heightLeft)
          pdf.addPage()
          pdf.addImage(imgData, 'JPEG', margin, position, imgWidth, imgHeight)
          heightLeft -= usableHeight
        }

        const tabNames = {
          structure: this.structureView === 'mindmap' ? '思维导图' : '关系图',
          radar: '能力雷达图',
          detail: '能力明细'
        }
        const fileName = '岗位能力图谱-' + (this.currentJobName || 'graph')
          + '-' + (tabNames[this.activeTab] || '视图') + '.pdf'
        pdf.save(fileName)
        this.exportStatus = 'PDF 已导出（' + totalPages + ' 页）'
        this.$modal.msgSuccess('PDF 已导出')
      } catch (error) {
        console.error('PDF export failed', error)
        this.exportStatus = '导出失败：' + (error.message || error)
        this.$modal.msgError('PDF 导出失败：' + (error.message || error))
      } finally {
        this.exporting = false
        this.$modal.closeLoading()
      }
    }
  }
}
</script>

<style scoped>
.competency-page .toolbar-card {
  margin-bottom: 16px;
}
.competency-page .action-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
}
.competency-page .hint {
  color: #6b7a90;
  font-size: 12.5px;
  line-height: 1.6;
}
.competency-page .chain-panel {
  background: #f7fafd;
  border: 1px solid #e3eaf3;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.competency-page .chain-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin: 4px 0;
}
.competency-page .chain-label {
  width: 64px;
  color: #4b5b70;
  font-size: 12.5px;
  font-weight: 600;
}
.competency-page .chain-line .arrow {
  color: #a8b6c8;
}
.competency-page .chain-tag {
  cursor: pointer;
}
.competency-page .chain-hint {
  color: #8a98ab;
  font-size: 12px;
}
.chain-dialog {
  font-size: 13px;
  color: #2c3e56;
}
.chain-dialog-block {
  padding: 10px 12px;
  border: 1px solid #e3eaf3;
  border-radius: 8px;
  background: #f7fafd;
  margin-bottom: 10px;
}
.chain-dialog-head {
  font-size: 12.5px;
  font-weight: 600;
  color: #4b5b70;
  margin-bottom: 6px;
}
.chain-dialog-name {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: #1f6feb;
}
.chain-dialog-code {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: #6b7a90;
}
.chain-dialog-desc {
  margin: 0;
  line-height: 1.65;
  color: #4b5b70;
}
.chain-dialog-meta {
  border-top: 1px dashed #e3eaf3;
  padding-top: 8px;
  color: #6b7a90;
  font-size: 12.5px;
  line-height: 1.7;
}
.chain-dialog-meta p {
  margin: 0;
  word-break: break-all;
}
.competency-page .export-status {
  margin-right: 8px;
  color: #6b7a90;
  font-size: 12.5px;
}
.competency-page .view-switch,
.competency-page .detail-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.competency-page .chain-title {
  color: #33507a;
  font-size: 13px;
  font-weight: 600;
}
.competency-page .legend-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 14px;
  padding: 6px 0 2px;
}
.competency-page .legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #4b5b70;
  font-size: 12.5px;
}
.competency-page .legend-item .dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  display: inline-block;
}
.competency-page .dot.dot-job { background: #e6501e; }
.competency-page .dot.dot-industry { background: #1f6feb; }
.competency-page .dot.dot-cluster { background: #0f9b8e; }
.competency-page .dot.dot-competency { background: #6f4ef2; }
.competency-page .dot.dot-unit { background: #33507a; }
.competency-page .dot.dot-skill { background: #2f9e44; }
.competency-page .dot.dot-knowledge { background: #f0a020; }
.competency-page .domain-map {
  background: #f7fafd;
  border: 1px solid #e3eaf3;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.competency-page .domain-map-title {
  font-weight: 600;
  color: #2c3e56;
  margin-bottom: 6px;
}
.competency-page .domain-map-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
  font-size: 12.5px;
  color: #4b5b70;
}
.competency-page .domain-map-name {
  min-width: 160px;
  font-weight: 600;
  color: #1f6feb;
}
.competency-page .suggest-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.competency-page .suggest-name {
  color: #2c3e56;
}
.competency-page .suggest-code {
  color: #8a97a8;
  font-size: 12px;
}
.competency-page .card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.competency-page .title {
  font-size: 16px;
  font-weight: 600;
  color: #16233a;
}
.competency-page .meta {
  margin-left: 8px;
  color: #6b7a90;
  font-size: 12.5px;
}
.competency-page .empty {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px 0;
  text-align: center;
  color: #909399;
}
.competency-page .stats {
  margin-bottom: 8px;
}
.competency-page .stat {
  border: 1px solid #e3eaf3;
  border-radius: 10px;
  padding: 10px 12px;
  text-align: center;
  background: #fbfdff;
}
.competency-page .stat b {
  display: block;
  font-size: 20px;
  color: #1f6feb;
}
.competency-page .stat span {
  color: #6b7a90;
  font-size: 12px;
}
.competency-page .chart {
  width: 100%;
  height: 420px;
}
.competency-page .chart.tall {
  height: 1320px;
}
.competency-page .chart.relation-chart {
  height: 900px;
}
.competency-page .tag {
  margin-left: 8px;
}
.competency-page .unit {
  border-top: 1px dashed #e3eaf3;
  padding: 10px 0;
}
.competency-page .unit:first-child {
  border-top: none;
}
.competency-page .unit-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #2c3e56;
  margin-bottom: 6px;
}
.competency-page .unit-line {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin: 4px 0;
}
.competency-page .line-label {
  width: 70px;
  color: #6b7a90;
  font-size: 12.5px;
}
.competency-page .point-tag {
  max-width: 100%;
  white-space: normal;
  height: auto;
  line-height: 18px;
  padding: 2px 8px;
}
.competency-page .empty-inline {
  color: #c0c4cc;
}
.competency-page .source-list {
  margin: 0;
  padding-left: 18px;
  line-height: 1.9;
}
.competency-page .source-list a {
  color: #1f6feb;
  word-break: break-all;
}
</style>
