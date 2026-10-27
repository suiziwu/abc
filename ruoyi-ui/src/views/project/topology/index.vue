<template>
  <div class="app-container">
    <!-- 学科选择器 -->
    <el-card shadow="never" class="mb-8">
      <el-row :gutter="20" align="middle">
        <el-col :xs="24" :sm="8" :md="6">
          <el-select
            v-model="subjectId"
            placeholder="请选择学科"
            size="large"
            clearable
            filterable
            @change="loadTopology"
            style="width: 100%"
          >
            <el-option
              v-for="item in subjectList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-col>
        <el-col :xs="24" :sm="16" :md="18">
          <div v-if="selectedSubject" class="flex items-center gap-2">
            <span class="text-secondary">{{ selectedSubject.description }}</span>
          </div>
          <div v-else class="text-secondary">选择学科后将自动渲染对应的知识点拓扑图</div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 统计概览 -->
    <el-row :gutter="16" class="mb-8" v-if="stats.totalNodes > 0">
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.totalNodes }}</div>
          <div class="stat-label">知识点总数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.totalEdges }}</div>
          <div class="stat-label">关联关系数</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.difficultyLevels }}</div>
          <div class="stat-label">难度等级</div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ stats.avgDifficulty }}</div>
          <div class="stat-label">平均难度</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 拓扑图 + 右侧难度图例 -->
    <el-card shadow="never" class="chart-card">
      <div class="chart-toolbar">
        <div class="chart-switches">
          <el-switch
            v-model="showLabels"
            active-text="显示知识点名称"
            @change="renderChart"
          />
          <el-switch
            v-model="showEdgeLabels"
            active-text="显示连线文字"
            @change="renderChart"
          />
        </div>
        <span class="chart-hint">节点较多时自动隐藏重叠标签，悬停/点击节点查看完整名称</span>
      </div>
      <div v-loading="loading" style="position: relative; min-height: 400px;">
        <div class="chart-layout" :style="{ height: (isLargeGraph ? 860 : 680) + 'px' }">
          <div class="chart-main" ref="chartRef"></div>
          <!-- 右侧难度图例 -->
          <div class="difficulty-legend">
            <div class="legend-title">难度等级</div>
            <div
              v-for="d in difficultyLevels"
              :key="d.value"
              class="legend-item"
            >
              <span class="legend-dot" :style="{ background: d.color }"></span>
              <span class="legend-label">{{ d.label }}</span>
            </div>
          </div>
        </div>
        <el-empty
          v-if="!loading && !subjectId"
          description="请在上方选择一个学科"
          style="position: absolute; top: 0; left: 0; right: 0; bottom: 0;"
        />
        <el-empty
          v-if="!loading && subjectId && points.length === 0"
          description="该学科暂无知识点数据"
          style="position: absolute; top: 0; left: 0; right: 0; bottom: 0;"
        />
      </div>
    </el-card>

    <!-- 知识点详情弹窗 -->
    <el-dialog v-model="dialogVisible" :title="currentPoint?.name" width="650px" destroy-on-close>
      <el-descriptions :column="2" border v-if="currentPoint" style="margin-bottom: 16px;">
        <el-descriptions-item label="所属学科" :span="2">{{ selectedSubject?.name }}</el-descriptions-item>
        <el-descriptions-item label="困难程度">
          <el-tag :type="difficultyTag(currentPoint.difficulty)" size="small">
            {{ difficultyLabel(currentPoint.difficulty) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="知识类型">
          <el-tag type="info" size="small">{{ currentPoint.type || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="重要程度" :span="2">
          <el-rate v-model="importanceValue" disabled :texts="['了解','掌握','重点','核心']" show-text />
        </el-descriptions-item>
        <el-descriptions-item label="详细内容" :span="2">
          {{ currentPoint.content || '暂无内容' }}
        </el-descriptions-item>
        <el-descriptions-item label="附件" :span="2">
          <el-link
            v-if="currentPoint.attachment"
            type="primary"
            :underline="false"
            icon="Download"
            @click="downloadFile(currentPoint.attachment)"
          >
            下载附件（{{ currentPoint.attachment.split('/').pop() }}）
          </el-link>
          <span v-else class="text-secondary">无附件</span>
        </el-descriptions-item>
      </el-descriptions>
      <!-- 视频预览 -->
      <div v-if="currentPoint?.video" class="video-section">
        <div class="video-section-title">相关视频</div>
        <el-divider style="margin: 8px 0 12px;" />
        <video
          :src="videoUrl"
          controls
          preload="metadata"
          style="width: 100%; max-height: 400px; border-radius: 6px; display: block;"
        >
          您的浏览器不支持视频播放
        </video>
      </div>
    </el-dialog>
  </div>
</template>

<script setup name="Topology">
import * as echarts from 'echarts'
import { listSubject } from '@/api/project/subject'
import { listPoint } from '@/api/project/point'
import { listRelation } from '@/api/project/relation'

const { proxy } = getCurrentInstance()

// ---------- 响应式状态 ----------
const subjectList = ref([])
const subjectId = ref(undefined)
const points = ref([])
const relations = ref([])
const loading = ref(false)
const chartRef = ref(null)
let chartInstance = null

const dialogVisible = ref(false)
const currentPoint = ref(null)
const importanceValue = ref(0)
const showLabels = ref(true)
const showEdgeLabels = ref(true)

/** 视频 URL（补全 base API 前缀） */
const videoUrl = computed(() => {
  if (!currentPoint.value?.video) return ''
  const path = currentPoint.value.video
  if (path.startsWith('http')) return path
  return (import.meta.env.VITE_APP_BASE_API || '') + path
})

// ---------- 计算属性 ----------
const selectedSubject = computed(() => {
  return subjectList.value.find(s => s.id === subjectId.value)
})

const stats = computed(() => {
  const nodes = points.value.length
  const edges = relations.value.length
  const levels = new Set(points.value.map(p => p.difficulty)).size

  const totalDiff = points.value.reduce((sum, p) => sum + (p.difficulty || 1), 0)
  const avgDiff = nodes > 0 ? (totalDiff / nodes).toFixed(1) : '-'

  return {
    totalNodes: nodes,
    totalEdges: edges,
    difficultyLevels: levels,
    avgDifficulty: avgDiff
  }
})

/** 大图模式：知识点较多时缩小节点，但保留防重叠标签 */
const isLargeGraph = computed(() => points.value.length > 120)

/** 切换标签显示后重绘当前拓扑 */
function renderChart() {
  if (points.value.length > 0) {
    initChart(points.value, relations.value)
  }
}

// ---------- 生命周期 ----------
onMounted(() => {
  loadSubjects()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})

// ---------- 数据加载 ----------
function loadSubjects() {
  listSubject({ pageSize: 9999, pageNum: 1 }).then(res => {
    subjectList.value = res.rows || res.data || []
  })
}

function loadTopology() {
  if (!subjectId.value) {
    points.value = []
    relations.value = []
    disposeChart()
    return
  }

  loading.value = true
  disposeChart()

  Promise.all([
    listPoint({ subjectId: subjectId.value, pageSize: 9999 }),
    listRelation({ pageSize: 9999 })
  ]).then(([pointRes, relationRes]) => {
    const allPoints = pointRes.rows || pointRes.data || []
    const allRelations = relationRes.rows || relationRes.data || []

    const pointIds = new Set(allPoints.map(p => p.id))
    const filteredRelations = allRelations.filter(
      r => pointIds.has(r.sourceId) && pointIds.has(r.targetId)
    )

    points.value = allPoints
    relations.value = filteredRelations
    // 大图默认隐藏连线文字（避免 600+ 边标签堆叠），小图默认显示；用户可随时用开关切换
    showEdgeLabels.value = allPoints.length <= 120

    nextTick(() => {
      initChart(allPoints, filteredRelations)
    })
  }).catch(err => {
    proxy?.$modal?.msgError?.('加载拓扑数据失败')
    console.error(err)
  }).finally(() => {
    loading.value = false
  })
}

// ---------- ECharts 拓扑图 ----------
function initChart(pointData, relationData) {
  if (!chartRef.value) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(chartRef.value)

  // 大图模式：节点数量多时使用小节点，但仍显示防重叠标签
  const large = pointData.length > 120

  // 构建节点数据
  const typeSet = new Set(pointData.map(p => p.type || '其他'))
  const categories = Array.from(typeSet)

  const nodes = pointData.map(p => {
    // 大图模式：10~30，保证节点可点击；标签放在节点右侧
    // 常规模式：基础60 + 难度系数 * 10，范围 70~110，确保内部能容纳文字
    const size = large ? 10 + (p.difficulty || 1) * 4 : 60 + (p.difficulty || 1) * 10
    return {
      id: String(p.id),
      name: p.name,
      value: p.difficulty || 1,
      category: categories.indexOf(p.type || '其他'),
      difficulty: p.difficulty || 1,
      point: p,
      itemStyle: {
        color: difficultyColor(p.difficulty || 1)
      },
      symbolSize: size
    }
  })

  // 构建边数据
  const edges = relationData.map(r => ({
    source: String(r.sourceId),
    target: String(r.targetId),
    value: r.description || '',
    relationType: r.relationType || 'prerequisite',
    lineStyle: {
      width: large ? Math.min((r.weight || 1), 2) : (r.weight || 1) * 1.5,
      curveness: 0.2,
      type: relationLineType(r.relationType || 'prerequisite')
    }
  }))

  const option = {
    title: {
      text: `${selectedSubject.value?.name || ''} 知识图谱`,
      subtext: `${nodes.length} 个知识点 · ${edges.length} 条关联`,
      left: 'center',
      textStyle: { fontSize: 16 }
    },
    tooltip: {
      trigger: 'item',
      formatter: (params) => {
        if (params.dataType === 'node') {
          const p = params.data.point
          return `<strong>${p.name}</strong><br/>
            类型：${p.type || '-'}<br/>
            难度：${difficultyLabel(p.difficulty)}<br/>
            描述：${p.content || '-'}`
        }
        if (params.dataType === 'edge') {
          return `<strong>关联关系</strong><br/>
            类型：${relationLabel(params.data.relationType)}<br/>
            描述：${params.data.value || '-'}`
        }
        return ''
      }
    },
    legend: {
      type: large ? 'scroll' : 'plain',
      data: categories.map((name, i) => ({
        name,
        icon: legendIcons[i % legendIcons.length]
      })),
      bottom: 0,
      left: 'center',
      textStyle: { fontSize: 13, color: '#606266' },
      icon: 'roundRect',
      itemWidth: 14,
      itemHeight: 14
    },
    animationDuration: 1500,
    animationEasingUpdate: 'quinticInOut',
    series: [{
      type: 'graph',
      layout: 'force',
      force: large
        ? {
            repulsion: 520,
            edgeLength: [60, 150],
            layoutAnimation: false,
            friction: 0.15,
            gravity: 0.08
          }
        : {
            repulsion: 800,           // 增大斥力，避免节点重叠
            edgeLength: [200, 400],   // 增大边长，为内部文字留空间
            layoutAnimation: true,
            friction: 0.1,
            gravity: 0.1
          },
      roam: true,
      draggable: true,
      data: nodes,
      edges: edges,
      legendHoverLink: false,
      categories: categories.map((name, i) => ({
        name,
        itemStyle: { color: categoryColors[i % categoryColors.length] }
      })),
      focusNodeAdjacency: true,
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: [0, 10],
      // ---------- 连线文字：ECharts graph 的边标签读取 edgeLabel（不是 label） ----------
      edgeLabel: {
        show: showEdgeLabels.value,
        position: 'middle',
        formatter: (params) => relationLabel(params.data?.relationType || 'prerequisite'),
        fontSize: large ? 10 : 11,
        color: large ? '#52575e' : '#909399',
        backgroundColor: large ? 'rgba(255, 255, 255, 0.85)' : 'transparent',
        padding: large ? [1, 3] : 0,
        borderRadius: 3,
        textBorderColor: '#FFFFFF',
        textBorderWidth: large ? 1 : 0
      },
      // ---------- 标签：小图放节点内部；大图放节点右侧，并自动隐藏重叠标签 ----------
      label: {
        show: showLabels.value,
        position: large ? 'right' : 'inside',
        distance: large ? 4 : 0,
        fontSize: large ? 11 : 15,
        fontWeight: large ? 'normal' : 'bold',
        color: large ? '#303133' : '#FFFFFF',
        textBorderColor: large ? '#FFFFFF' : 'transparent',
        textBorderWidth: large ? 2 : 0,
        textShadowColor: large ? 'transparent' : 'rgba(0,0,0,0.6)',
        textShadowBlur: large ? 0 : 6,
        textShadowOffsetX: 1,
        textShadowOffsetY: 1,
        formatter: (params) => {
          const name = params.data.name || ''
          const maxLength = large ? 10 : 8
          return name.length > maxLength ? name.slice(0, maxLength) + '...' : name
        }
      },
      // 节点标签：大图时自动隐藏重叠；连线标签不参与 hideOverlap，
      // 否则 300+ 条边的文字会全部被判定为重叠而隐藏（开关打开也看不到）
      labelLayout: large
        ? (params) => (params.dataType === 'edge'
            ? { hideOverlap: false }
            : { hideOverlap: true, moveOverlap: 'shiftY' })
        : undefined,
      lineStyle: {
        color: 'source',
        curveness: 0.2,
        opacity: large ? 0.3 : 0.7
      },
      // ---------- hover 时显示完整名称 ----------
      emphasis: {
        focus: 'adjacency',
        label: {
          show: true,
          position: large ? 'right' : 'inside',
          distance: large ? 4 : 0,
          formatter: (params) => params.data.name || '',
          fontSize: large ? 13 : 17,
          fontWeight: 'bold',
          color: large ? '#303133' : '#FFFFFF',
          textBorderColor: large ? '#FFFFFF' : 'transparent',
          textBorderWidth: large ? 2 : 0,
          textShadowColor: large ? 'transparent' : 'rgba(0,0,0,0.8)',
          textShadowBlur: large ? 0 : 8,
          textShadowOffsetX: 1,
          textShadowOffsetY: 1
        },
        lineStyle: {
          width: 3
        }
      },
      blur: {
        opacity: 0.2
      }
    }]
  }

  chartInstance.setOption(option)
  chartInstance.on('click', handleChartClick)
}

function handleChartClick(params) {
  if (params.dataType === 'node' && params.data.point) {
    currentPoint.value = params.data.point
    importanceValue.value = params.data.point.importance || 1
    dialogVisible.value = true
  }
}

function handleResize() {
  chartInstance?.resize()
}

function disposeChart() {
  if (chartInstance) {
    chartInstance.off('click', handleChartClick)
    chartInstance.dispose()
    chartInstance = null
  }
}

/** 下载附件 */
function downloadFile(path) {
  proxy?.$download?.resource(path)
}

// ---------- 工具函数 ----------
const categoryColors = ['#A0AEC0', '#B0BEC5', '#A8B5C2', '#9EADBE', '#B5BCC9', '#A5B1C2']

/** 难度等级数据（供右侧图例使用） */
const difficultyLevels = [
  { value: 1, label: '简单', color: '#67C23A' },
  { value: 2, label: '较易', color: '#409EFF' },
  { value: 3, label: '中等', color: '#E6A23C' },
  { value: 4, label: '较难', color: '#F56C6C' },
  { value: 5, label: '困难', color: '#b33939' }
]

/** 图例图标映射 */
const legendIcons = ['circle', 'rect', 'roundRect', 'triangle', 'diamond', 'pin']

function difficultyColor(difficulty) {
  const map = {
    1: '#67C23A',
    2: '#409EFF',
    3: '#E6A23C',
    4: '#F56C6C',
    5: '#b33939'
  }
  return map[difficulty] || '#909399'
}

function difficultyLabel(difficulty) {
  const map = { 1: '简单', 2: '较易', 3: '中等', 4: '较难', 5: '困难' }
  return map[difficulty] || '未知'
}

function difficultyTag(difficulty) {
  const map = { 1: 'success', 2: 'primary', 3: 'warning', 4: 'danger', 5: 'danger' }
  return map[difficulty] || 'info'
}

function relationLineType(type) {
  const map = { prerequisite: 'solid', related: 'dashed', extension: 'dotted' }
  return map[type] || 'solid'
}

function relationLabel(type) {
  const map = {
    prerequisite: '前置依赖',
    related: '相关参考',
    extension: '扩展延伸'
  }
  return map[type] || type
}
</script>

<style scoped>
.mb-8 {
  margin-bottom: 16px;
}

.text-secondary {
  color: #909399;
  font-size: 14px;
}

.stat-card {
  text-align: center;
  cursor: default;
  transition: transform 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-card .stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-card .stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}

.chart-card {
  overflow: hidden;
}

.chart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 4px 10px;
}

.chart-switches {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 20px;
}

.chart-hint {
  font-size: 12px;
  color: #909399;
}

.chart-layout {
  display: flex;
  gap: 0;
  height: 680px;
}

.chart-main {
  flex: 1;
  height: 100%;
}

/* 右侧难度图例 —— 样式优化 */
.difficulty-legend {
  width: 100px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 16px;
  padding: 20px 12px;
  border-left: 1px solid #ebeef5;
  background: #fafbfc;
  border-radius: 0 4px 4px 0;
}

.legend-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  text-align: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #e4e7ed;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.legend-dot {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  flex-shrink: 0;
  border: 1px solid rgba(0, 0, 0, 0.06);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.legend-label {
  font-size: 14px;
  color: #606266;
  font-weight: 500;
}

.video-section {
  margin-top: 4px;
}

.video-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
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