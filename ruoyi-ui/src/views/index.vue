<template>
  <div class="dashboard-container">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner">
      <div class="banner-content">
        <h1 class="banner-title">挖掘职业能力 · 生成岗位能力图谱</h1>
        <p class="banner-desc">基于 AI 智能分析，构建个性化学习路径，精准提升岗位胜任力</p>
        <div class="banner-actions">
          <el-button type="primary" size="large" round @click="$router.push('/learn')">
            <svg-icon icon-class="education" style="margin-right:6px" />
            开始学习
          </el-button>
          <el-button size="large" round plain style="color:#fff;border-color:rgba(255,255,255,0.5);background:rgba(255,255,255,0.12)" @click="goToRecords">
            <svg-icon icon-class="list" style="margin-right:6px" />
            查看记录
          </el-button>
        </div>
      </div>
      <div class="banner-decoration">
        <div class="deco-circle deco-1"></div>
        <div class="deco-circle deco-2"></div>
        <div class="deco-circle deco-3"></div>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-grid">
      <div class="stat-card" v-for="stat in stats" :key="stat.label" style="cursor:pointer">
        <div class="stat-icon" :style="{ background: stat.bg }">
          <svg-icon :icon-class="stat.icon" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
        <div class="stat-trend" :style="{ color: stat.trendColor }">
          <span>{{ stat.trend }}</span>
        </div>
      </div>
    </div>

    <!-- 核心功能 -->
    <div class="section">
      <div class="section-header">
        <h2 class="section-title">核心功能</h2>
        <p class="section-subtitle">覆盖能力诊断、学习规划、知识沉淀全流程</p>
      </div>
      <div class="features-grid">
        <div
          v-for="(feature, idx) in features"
          :key="idx"
          class="feature-card"
        >
          <div class="feature-icon-wrapper" :style="{ background: feature.bg }">
            <svg-icon :icon-class="feature.icon" style="font-size:28px" />
          </div>
          <h3 class="feature-title">{{ feature.title }}</h3>
          <p class="feature-desc">{{ feature.desc }}</p>
          <div class="feature-action">
          </div>
        </div>
      </div>
    </div>

    <!-- 技术栈 -->
    <div class="section">
      <div class="section-header">
        <h2 class="section-title">技术架构</h2>
        <p class="section-subtitle">构建高效、可扩展的智能学习平台</p>
      </div>
      <div class="tech-grid">
        <div class="tech-item" v-for="tech in techStack" :key="tech.name">
          <div class="tech-icon" v-html="tech.icon"></div>
          <div class="tech-name">{{ tech.name }}</div>
          <div class="tech-role">{{ tech.role }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'
import { listSubject } from '@/api/project/subject'
import { listPoint } from '@/api/project/point'
import { listAssessment } from '@/api/project/assessment'
import { ElMessage } from 'element-plus'

const router = useRouter()

const stats = ref([
  { label: '学科数量', value: '0', icon: 'tree', bg: 'linear-gradient(135deg,#409eff,#337ecc)', trend: '', trendColor: '#67c23a', path: '/picture/subject' },
  { label: '知识点', value: '0', icon: 'list', bg: 'linear-gradient(135deg,#67c23a,#529b2e)', trend: '', trendColor: '#67c23a', path: '/picture/topology' },
  { label: '诊断记录', value: '0', icon: 'search', bg: 'linear-gradient(135deg,#e6a23c,#cf9236)', trend: '', trendColor: '#e6a23c', path: '/picture/learn' },
  { label: '学习进度', value: '0%', icon: 'chart', bg: 'linear-gradient(135deg,#f56c6c,#d9534f)', trend: '', trendColor: '#f56c6c', path: '/project/learn/profile/index' }
])

const features = [
  {
    title: '薄弱诊断',
    desc: '基于目标岗位与学科知识体系，AI 自动出题检测知识薄弱点，生成针对性学习路径。',
    icon: 'skill',
    bg: 'linear-gradient(135deg,#e8f4ff,#d0eaff)',
    color: '#409eff',
    path: '/picture/learn'
  },
  {
    title: '视频学习',
    desc: '知识点关联视频资源，支持播放进度跟踪与学习记录管理，随时随地学习。',
    icon: 'education',
    bg: 'linear-gradient(135deg,#f0f9eb,#daf0ce)',
    color: '#67c23a',
    path: '/picture/learn'
  },
  {
    title: '人才画像',
    desc: '从多维度构建个人能力画像，直观展示知识掌握程度与能力差距。',
    icon: 'people',
    bg: 'linear-gradient(135deg,#fdf6ec,#fae6c8)',
    color: '#e6a23c',
    path: '/project/learn/profile/index'
  },
  {
    title: '学科管理',
    desc: '灵活配置学科体系与知识点结构，支持多级分类与批量维护。',
    icon: 'tree',
    bg: 'linear-gradient(135deg,#fef0f0,#fad0d0)',
    color: '#f56c6c',
    path: '/picture/subject'
  }
]

const techStack = [
  { name: 'Vue 3', role: '前端框架', icon: '<svg viewBox="0 0 32 32" width="32" height="32"><path fill="#4fc08d" d="M2 4l14 24 14-24H24l-8 14-8-14H2z"/><path fill="#35495e" d="M12 4l4 7 4-7h5l-9 16-9-16h5z"/></svg>' },
  { name: 'Element Plus', role: 'UI 组件库', icon: '<svg viewBox="0 0 32 32" width="32" height="32"><rect fill="#409eff" width="32" height="32" rx="6"/><text x="16" y="22" text-anchor="middle" font-size="16" font-weight="bold" fill="#fff">E</text></svg>' },
  { name: 'Spring Boot', role: '后端框架', icon: '<svg viewBox="0 0 32 32" width="32" height="32"><path fill="#6db33f" d="M19.5 2C18.5 3.5 16 7 15.5 9c-.5 2 .5 4 1 5 .5 1 1.5 2 .5 3s-3-.5-5-2c-2-1.5-3.5-4-3.5-6s2-6 3.5-8C11 1 12 1 12 1s-1.5 1.5-2.5 3c-1 1.5-2 3-2 5s.5 4 2 5.5c1.5 1.5 2.5 2.5 2.5 2.5s.5-1.5-.5-3c-1-1.5-1-4 0-6C13 5 15 3 16 2c1-1 2-2 2-2s1 1 1.5 2z"/><path fill="#6db33f" d="M20 5c0-.5 1-1 1-1s-1 1-.5 2c.5 1 1 2 1 3s0 2-.5 3c-.5 1-2 3-2 3s0-1 .5-2c.5-1 1-2 1-3s0-2-.5-3S20 5.5 20 5z"/></svg>' },
  { name: 'MyBatis', role: '数据持久化', icon: '<svg viewBox="0 0 32 32" width="32" height="32"><rect fill="#34495e" width="32" height="32" rx="6"/><text x="16" y="22" text-anchor="middle" font-size="10" font-weight="bold" fill="#fff">MB</text></svg>' },
  { name: 'MySQL', role: '数据库', icon: '<svg viewBox="0 0 32 32" width="32" height="32"><path fill="#4479a1" d="M16 2L4 8v16l12 6 12-6V8L16 2z"/><path fill="#fff" d="M16 5l-9 4.5v9L16 23l9-4.5v-9L16 5z"/><path fill="#4479a1" d="M16 4.5L8 8.5v7L16 20l8-4.5v-7l-8-4z"/></svg>' },
  { name: 'Vite', role: '构建工具', icon: '<svg viewBox="0 0 32 32" width="32" height="32"><path fill="#ffc107" d="M16 2L2 28h28L16 2z"/><text x="16" y="22" text-anchor="middle" font-size="12" font-weight="bold" fill="#303133">V</text></svg>' }
]

async function loadStats() {
  try {
    const subjectRes = await listSubject({ pageSize: 1, pageNum: 1 })
    if (subjectRes.code === 200) stats.value[0].value = subjectRes.total || '0'

    const pointRes = await listPoint({ pageSize: 1, pageNum: 1 })
    if (pointRes.code === 200) stats.value[1].value = pointRes.total || '0'

    const assessRes = await listAssessment({ pageSize: 1, pageNum: 1 })
    if (assessRes.code === 200) stats.value[2].value = assessRes.total || '0'

    // 统计学习进度：已完成的评估 / 总评估
    if (assessRes.code === 200 && Number(assessRes.total) > 0) {
      const doneRes = await listAssessment({ pageSize: 9999, pageNum: 1, status: '2' })
      const doneCount = (doneRes.rows || []).length
      const total = Number(assessRes.total)
      const pct = Math.round((doneCount / total) * 100)
      stats.value[3].value = pct + '%'
    }
  } catch {}
}

onMounted(loadStats)

// 查看记录
async function goToRecords() {
  try {
    const res = await listAssessment({ pageSize: 1, pageNum: 1 })
    if (res.code === 200 && res.total > 0) {
      router.push('/project/learn/profile/index')
    } else {
      ElMessage.warning('暂无学习记录，请先开始学习')
    }
  } catch {
    ElMessage.warning('暂无学习记录，请先开始学习')
  }
}
</script>

<style scoped>
.dashboard-container {
  padding: 0;
  max-width: 1200px;
  margin: 0 auto;
}

/* ===== 欢迎横幅 ===== */
.welcome-banner {
  position: relative;
  overflow: hidden;
  border-radius: 16px;
  padding: 48px 56px;
  margin-bottom: 24px;
  background: linear-gradient(135deg, #1a3a5c 0%, #2b5f8a 40%, #409eff 100%);
  color: #fff;
}

.banner-content {
  position: relative;
  z-index: 2;
}

.banner-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 12px;
  line-height: 1.3;
  letter-spacing: 1px;
}

.banner-desc {
  font-size: 15px;
  line-height: 1.6;
  opacity: 0.85;
  margin: 0 0 28px;
  max-width: 540px;
}

.banner-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.banner-decoration {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.deco-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(255,255,255,0.04);
}

.deco-1 {
  width: 400px; height: 400px;
  right: -80px; top: -120px;
}

.deco-2 {
  width: 250px; height: 250px;
  right: 160px; bottom: -80px;
  background: rgba(255,255,255,0.06);
}

.deco-3 {
  width: 120px; height: 120px;
  right: 60px; top: 40px;
  background: rgba(255,255,255,0.08);
}

/* ===== 统计卡片 ===== */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 32px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border-radius: 12px;
  padding: 20px 24px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0,0,0,0.08);
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  color: #fff;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
  min-width: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 2px;
}

.stat-trend {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

/* ===== 通用区块 ===== */
.section {
  margin-bottom: 32px;
}

.section-header {
  text-align: center;
  margin-bottom: 24px;
}

.section-title {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}

.section-subtitle {
  font-size: 14px;
  color: #909399;
  margin: 0;
}

/* ===== 功能卡片 ===== */
.features-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.feature-card {
  background: #fff;
  border-radius: 12px;
  padding: 28px 24px;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
  transition: transform 0.2s, box-shadow 0.2s;
  display: flex;
  flex-direction: column;
}

.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}

.feature-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 16px;
  color: #303133;
}

.feature-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 8px;
}

.feature-desc {
  font-size: 13px;
  line-height: 1.6;
  color: #909399;
  margin: 0 0 auto;
  flex: 1;
}

.feature-action {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 16px;
  font-size: 13px;
  color: #409eff;
  font-weight: 500;
  opacity: 0;
  transition: opacity 0.2s;
}

.feature-card:hover .feature-action {
  opacity: 1;
}

.feature-link {
  font-size: 13px;
}

/* ===== 技术栈 ===== */
.tech-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 16px;
}

.tech-item {
  background: #fff;
  border-radius: 12px;
  padding: 24px 16px;
  text-align: center;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04);
  transition: transform 0.2s;
}

.tech-item:hover {
  transform: translateY(-2px);
}

.tech-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10px;
}

.tech-name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.tech-role {
  font-size: 12px;
  color: #c0c4cc;
}

/* ===== 响应式 ===== */
@media (max-width: 992px) {
  .welcome-banner { padding: 32px 24px; }
  .banner-title { font-size: 22px; }
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
  .features-grid { grid-template-columns: repeat(2, 1fr); }
  .tech-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 576px) {
  .stats-grid { grid-template-columns: 1fr; }
  .features-grid { grid-template-columns: 1fr; }
  .tech-grid { grid-template-columns: repeat(2, 1fr); }
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
