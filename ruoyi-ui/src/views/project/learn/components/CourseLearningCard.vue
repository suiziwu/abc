<template>
  <article
    class="course-learning-card"
    :class="[`cover-theme-${themeIndex}`, { 'is-compact': compact }]"
    role="button"
    tabindex="0"
    :aria-label="`进入课程：${course.name || '课程学习'}`"
    @click="handleOpen"
    @keydown.enter="handleOpen"
    @keydown.space.prevent="handleOpen"
  >
    <div class="course-cover">
      <span class="cover-orb cover-orb-a"></span>
      <span class="cover-orb cover-orb-b"></span>
      <span class="cover-line cover-line-a"></span>
      <span class="cover-line cover-line-b"></span>

      <div class="cover-top">
        <span class="status-pill" :class="statusClass">
          <span class="status-dot"></span>
          {{ statusLabel }}
        </span>
        <span class="cover-points">{{ pointCount }} 个知识点</span>
      </div>

      <div class="cover-center">
        <span class="course-initial">{{ initial }}</span>
        <span class="cover-icon">
          <el-icon><Reading /></el-icon>
        </span>
      </div>

      <div class="cover-bottom">
        <span class="cover-code">{{ course.code || 'COURSE' }}</span>
        <span class="cover-enter">
          进入课程
          <el-icon><ArrowRight /></el-icon>
        </span>
      </div>
    </div>

    <div class="course-body">
      <h3 class="course-title" :title="course.name">{{ course.name || '未命名课程' }}</h3>
      <p class="course-desc" :title="description">{{ description }}</p>

      <div class="course-progress">
        <div class="progress-label">
          <span>学习进度</span>
          <strong>{{ progress }}%</strong>
        </div>
        <el-progress
          :percentage="progress"
          :stroke-width="6"
          :show-text="false"
          :color="progressColor"
        />
      </div>

      <div class="course-meta">
        <span class="meta-item">
          <el-icon><Collection /></el-icon>
          已学 {{ doneCount }}/{{ pointCount }}
        </span>
        <span v-if="learningCount > 0" class="meta-item meta-learning">
          {{ learningCount }} 个学习中
        </span>
        <span v-else class="meta-item meta-done">
          {{ progress >= 100 ? '已完成' : '待开始' }}
        </span>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowRight, Collection, Reading } from '@element-plus/icons-vue'

const props = defineProps({
  course: {
    type: Object,
    required: true
  },
  compact: {
    type: Boolean,
    default: false
  },
  themeIndex: {
    type: Number,
    default: null
  }
})

const emit = defineEmits(['open'])

const pointCount = computed(() => Number(
  props.course.pointCount
  ?? props.course.totalCount
  ?? props.course._points?.length
  ?? 0
))

const doneCount = computed(() => Number(
  props.course.doneCount
  ?? props.course._doneCount
  ?? 0
))

const learningCount = computed(() => Number(
  props.course.learningCount
  ?? props.course._points?.filter(point => point._status === '1').length
  ?? 0
))

const progress = computed(() => {
  const raw = props.course.progress
    ?? props.course._completionRate
    ?? props.course.completionRate
  if (raw !== undefined && raw !== null && raw !== '') {
    return Math.min(100, Math.max(0, Math.round(Number(raw))))
  }
  return pointCount.value > 0
    ? Math.min(100, Math.max(0, Math.round((doneCount.value / pointCount.value) * 100)))
    : 0
})

const statusLabel = computed(() => {
  if (props.course.statusLabel) return props.course.statusLabel
  if (progress.value >= 100) return '已完成'
  if (learningCount.value > 0 || progress.value > 0) return '学习中'
  return '待学习'
})

const statusClass = computed(() => {
  if (statusLabel.value === '已完成') return 'is-done'
  if (statusLabel.value === '学习中') return 'is-learning'
  return 'is-pending'
})

const description = computed(() => {
  const text = String(props.course.description || '').trim()
  return text || '系统化学习课程知识点，边看视频边掌握核心内容'
})

const initial = computed(() => {
  const name = String(props.course.name || '').trim()
  return name ? name.slice(0, 1).toUpperCase() : '课'
})

const themeIndex = computed(() => {
  if (props.themeIndex !== null && props.themeIndex !== undefined) {
    return Math.abs(Number(props.themeIndex)) % 6
  }
  if (props.course.themeIndex !== undefined && props.course.themeIndex !== null) {
    return Math.abs(Number(props.course.themeIndex)) % 6
  }
  const id = Number(props.course.id)
  if (!Number.isNaN(id)) return Math.abs(id) % 6
  const name = String(props.course.name || '')
  return name.split('').reduce((sum, char) => sum + char.charCodeAt(0), 0) % 6
})

const progressColor = computed(() => {
  if (progress.value >= 80) return '#22a06b'
  if (progress.value >= 50) return '#3b7ef2'
  return '#e8a33d'
})

function handleOpen() {
  emit('open', props.course)
}
</script>

<style scoped>
.course-learning-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e8ebf0;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(31, 35, 41, 0.04);
  cursor: pointer;
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.course-learning-card:hover,
.course-learning-card:focus-visible {
  transform: translateY(-3px);
  border-color: #cfe0ff;
  box-shadow: 0 12px 26px rgba(31, 35, 41, 0.1);
  outline: none;
}

.course-cover {
  position: relative;
  height: 142px;
  overflow: hidden;
  padding: 14px 16px;
  color: #fff;
  background: linear-gradient(135deg, #2f7cf6 0%, #56a8ff 52%, #8fd3ff 100%);
}

.cover-theme-0 .course-cover {
  background: linear-gradient(135deg, #2f7cf6 0%, #56a8ff 52%, #8fd3ff 100%);
}

.cover-theme-1 .course-cover {
  background: linear-gradient(135deg, #6d5dfc 0%, #9b8cff 52%, #cfc6ff 100%);
}

.cover-theme-2 .course-cover {
  background: linear-gradient(135deg, #e8892f 0%, #f5b04d 52%, #ffd899 100%);
}

.cover-theme-3 .course-cover {
  background: linear-gradient(135deg, #159b72 0%, #39bf91 52%, #9ae5c8 100%);
}

.cover-theme-4 .course-cover {
  background: linear-gradient(135deg, #db4f7a 0%, #f47ea0 52%, #ffc2d4 100%);
}

.cover-theme-5 .course-cover {
  background: linear-gradient(135deg, #334c7d 0%, #5473ae 52%, #a4b8df 100%);
}

.cover-orb {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.14);
  pointer-events: none;
}

.cover-orb-a {
  width: 150px;
  height: 150px;
  top: -74px;
  right: -36px;
}

.cover-orb-b {
  width: 90px;
  height: 90px;
  bottom: -50px;
  left: 34%;
  background: rgba(255, 255, 255, 0.1);
}

.cover-line {
  position: absolute;
  height: 1px;
  background: rgba(255, 255, 255, 0.22);
  transform: rotate(-24deg);
  pointer-events: none;
}

.cover-line-a {
  width: 190px;
  top: 58px;
  left: -20px;
}

.cover-line-b {
  width: 150px;
  top: 102px;
  right: -20px;
}

.cover-top,
.cover-bottom {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: #fff;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(4px);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
}

.status-pill.is-done .status-dot {
  background: #d9fbe8;
}

.status-pill.is-learning .status-dot {
  background: #fff4c2;
  box-shadow: 0 0 0 3px rgba(255, 244, 194, 0.22);
}

.cover-points {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.82);
  white-space: nowrap;
}

.cover-center {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 58px;
  margin-top: 6px;
}

.course-initial {
  font-size: 38px;
  font-weight: 700;
  line-height: 1;
  color: rgba(255, 255, 255, 0.95);
  text-shadow: 0 4px 18px rgba(17, 45, 92, 0.18);
}

.cover-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: 1px solid rgba(255, 255, 255, 0.35);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(6px);
}

.cover-icon :deep(.el-icon) {
  font-size: 22px;
}

.cover-bottom {
  font-size: 11px;
}

.cover-code {
  max-width: 48%;
  overflow: hidden;
  color: rgba(255, 255, 255, 0.66);
  letter-spacing: 0.08em;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cover-enter {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-weight: 600;
  color: #fff;
  opacity: 0.92;
}

.cover-enter :deep(.el-icon) {
  font-size: 12px;
}

.course-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  padding: 14px 16px 15px;
}

.course-title {
  margin: 0;
  overflow: hidden;
  color: #1f2329;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-desc {
  display: -webkit-box;
  min-height: 34px;
  margin: 6px 0 12px;
  overflow: hidden;
  color: #8a919c;
  font-size: 12px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.course-progress {
  margin-top: auto;
}

.progress-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 5px;
  color: #8a919c;
  font-size: 11px;
}

.progress-label strong {
  color: #4e5969;
  font-size: 12px;
  font-weight: 600;
}

.course-progress :deep(.el-progress-bar__outer) {
  background: #f0f2f5;
}

.course-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 10px;
  color: #9aa1ab;
  font-size: 11px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  white-space: nowrap;
}

.meta-item :deep(.el-icon) {
  font-size: 12px;
}

.meta-learning {
  color: #2f7cf6;
}

.meta-done {
  color: #22a06b;
}

.is-compact .course-cover {
  height: 112px;
  padding: 11px 14px;
}

.is-compact .cover-center {
  height: 40px;
  margin-top: 2px;
}

.is-compact .course-initial {
  font-size: 30px;
}

.is-compact .cover-icon {
  width: 34px;
  height: 34px;
  border-radius: 10px;
}

.is-compact .cover-icon :deep(.el-icon) {
  font-size: 18px;
}

.is-compact .course-body {
  padding: 12px 14px 13px;
}

.is-compact .course-desc {
  margin: 4px 0 9px;
  -webkit-line-clamp: 1;
}

@media (max-width: 768px) {
  .course-cover {
    height: 128px;
  }
}
</style>
