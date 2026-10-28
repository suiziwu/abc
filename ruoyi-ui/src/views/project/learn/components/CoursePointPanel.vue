<template>
  <el-card shadow="never" class="course-panel">
    <template #header>
      <div class="section-header">
        <div class="section-header-left">
          <span><svg-icon icon-class="education" /> 岗位所需课程</span>
          <el-tag type="success" effect="plain" size="small">{{ courses.length }} 门课程</el-tag>
        </div>
        <span class="panel-tip">先选择课程，再点击右侧知识点在本页学习</span>
      </div>
    </template>

    <div v-if="courses.length === 0" class="course-empty">
      <el-empty description="暂未匹配到岗位课程" :image-size="70" />
    </div>

    <div v-else class="course-workbench">
      <!-- 左侧：岗位课程列表 -->
      <aside class="course-sidebar">
        <div class="sidebar-title">
          <span>课程列表</span>
          <el-tag size="small" type="info" effect="plain">{{ courses.length }}</el-tag>
        </div>
        <div class="course-list">
          <button
            v-for="course in courses"
            :key="course.id"
            type="button"
            class="course-item"
            :class="{ active: String(course.id) === String(activeCourseId) }"
            @click="handleSelectCourse(course)"
          >
            <div class="course-item-top">
              <span class="course-item-name" :title="course.name">{{ course.name }}</span>
              <el-icon v-if="String(course.id) === String(activeCourseId)" class="course-item-check">
                <CircleCheck />
              </el-icon>
            </div>
            <div class="course-item-meta">
              <span>{{ course._points.length }} 个知识点</span>
              <span class="course-item-done">已学 {{ course._doneCount }}</span>
            </div>
            <el-progress
              :percentage="course._completionRate"
              :stroke-width="5"
              :show-text="false"
              :color="course._completionRate >= 80 ? '#67c23a' : '#409eff'"
            />
          </button>
        </div>
      </aside>

      <!-- 右侧：所选课程的知识点 -->
      <section class="course-points-pane">
        <template v-if="activeCourse">
          <div class="pane-header">
            <div class="pane-title">
              <span class="pane-course-name">{{ activeCourse.name }}</span>
              <el-tag size="small" type="info" effect="plain">{{ activeCourse._points.length }} 个知识点</el-tag>
              <el-tag v-if="weakPointsInCourse > 0" size="small" type="danger" effect="plain">
                薄弱 {{ weakPointsInCourse }} 个
              </el-tag>
            </div>
            <div class="pane-actions">
              <el-input
                v-model="keyword"
                placeholder="搜索知识点"
                clearable
                size="small"
                class="point-search"
              >
                <template #prefix>
                  <el-icon><Search /></el-icon>
                </template>
              </el-input>
              <el-select
                v-if="chapterOptions.length > 0"
                v-model="chapterFilter"
                placeholder="全部章节"
                clearable
                size="small"
                class="chapter-filter"
              >
                <el-option label="全部章节" value="" />
                <el-option
                  v-for="option in chapterOptions"
                  :key="option.key"
                  :label="`${option.name}（${option.count}）`"
                  :value="option.key"
                />
              </el-select>
              <el-button
                v-if="activeCourse._points.length === 0"
                type="primary"
                plain
                size="small"
                :loading="String(generatingCourseId) === String(activeCourse.id)"
                @click="emit('generate-points', activeCourse)"
              >
                <el-icon><MagicStick /></el-icon>
                根据视频内容生成知识点
              </el-button>
              <el-button
                v-else
                text
                type="primary"
                size="small"
                :loading="String(generatingCourseId) === String(activeCourse.id)"
                @click="emit('generate-points', activeCourse)"
              >
                补充知识点
              </el-button>
            </div>
          </div>

          <div v-loading="loading" class="pane-body">
            <template v-if="activeCourse._points.length > 0">
              <div
                v-for="chapter in filteredChapters"
                :key="chapterKey(chapter)"
                class="chapter-block"
              >
                <button
                  v-if="!chapter._flat"
                  type="button"
                  class="chapter-head"
                  @click="toggleChapter(chapterKey(chapter))"
                >
                  <el-icon
                    class="chapter-arrow"
                    :class="{ collapsed: isChapterCollapsed(chapterKey(chapter)) }"
                  >
                    <ArrowDown />
                  </el-icon>
                  <span class="chapter-name">{{ chapter.name || chapter.title }}</span>
                  <el-tag size="small" type="info" effect="plain">{{ chapter._points.length }}</el-tag>
                  <span v-if="chapterWeakCount(chapter._points) > 0" class="chapter-weak">
                    薄弱 {{ chapterWeakCount(chapter._points) }}
                  </span>
                  <span class="chapter-hint">
                    {{ isChapterCollapsed(chapterKey(chapter)) ? '展开' : '收起' }}
                  </span>
                </button>

                <div
                  v-show="!isChapterCollapsed(chapterKey(chapter))"
                  class="point-rows"
                >
                  <button
                    v-for="point in chapter._points"
                    :key="point.id"
                    type="button"
                    class="point-row"
                    :class="{
                      active: String(point.id) === String(activePointId),
                      weak: isWeakPoint(point)
                    }"
                    @click="emit('select-point', point, activeCourse)"
                  >
                    <span class="point-status">
                      <el-icon v-if="point._status === '2'" color="#67c23a"><CircleCheck /></el-icon>
                      <el-icon v-else-if="point._status === '1'" color="#409eff"><VideoPlay /></el-icon>
                      <span v-else class="status-dot"></span>
                    </span>
                    <span class="point-row-name" :title="point.name">{{ point.name }}</span>
                    <span class="point-row-meta">
                      <el-tag v-if="isWeakPoint(point)" type="danger" size="small" effect="dark">薄弱</el-tag>
                      <el-tag v-if="point.difficulty" size="small" :type="difficultyTag(point.difficulty)" effect="plain">
                        {{ difficultyLabel(point.difficulty) }}
                      </el-tag>
                      <el-tag v-if="point.type" size="small" type="info" effect="plain">{{ point.type }}</el-tag>
                      <el-tag
                        v-if="point._quizScore !== undefined && point._quizScore !== null"
                        size="small"
                        effect="dark"
                        :type="point._quizScore >= 80 ? 'success' : point._quizScore >= 60 ? 'primary' : 'warning'"
                      >
                        {{ point._quizScore }}分
                      </el-tag>
                    </span>
                    <el-icon v-if="point.video" class="point-video" title="含视频">
                      <VideoCamera />
                    </el-icon>
                  </button>
                </div>
              </div>

              <el-empty
                v-if="filteredChapters.length === 0"
                :description="keyword ? '没有匹配的知识点' : '当前章节暂无知识点'"
                :image-size="60"
              />
            </template>

            <el-empty
              v-else
              description="该课程暂无知识点，可根据课程视频内容生成"
              :image-size="60"
            />
          </div>
        </template>

        <el-empty
          v-else
          description="请选择左侧课程查看知识点"
          :image-size="80"
          class="pane-placeholder"
        />
      </section>
    </div>
  </el-card>
</template>

<script setup name="CoursePointPanel">
import { computed, ref, watch } from 'vue'
import {
  Search, MagicStick, CircleCheck, VideoPlay, VideoCamera, ArrowDown
} from '@element-plus/icons-vue'

const props = defineProps({
  courses: {
    type: Array,
    default: () => []
  },
  activeCourseId: {
    type: [String, Number],
    default: null
  },
  activePointId: {
    type: [String, Number],
    default: null
  },
  weakPointIds: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  generatingCourseId: {
    type: [String, Number],
    default: null
  }
})

const emit = defineEmits(['select-course', 'select-point', 'generate-points'])

const keyword = ref('')
const chapterFilter = ref('')
const collapsedChapters = ref({})

const activeCourse = computed(() => {
  if (!props.courses.length || !props.activeCourseId) return null
  return props.courses.find(item => String(item.id) === String(props.activeCourseId)) || null
})

const weakSet = computed(() => new Set((props.weakPointIds || []).map(id => String(id))))

const weakPointsInCourse = computed(() => {
  if (!activeCourse.value) return 0
  return activeCourse.value._points.filter(point => weakSet.value.has(String(point.id))).length
})

const chapterOptions = computed(() => {
  if (!activeCourse.value) return []
  return (activeCourse.value._chapters || [])
    .filter(chapter => !chapter._flat)
    .map(chapter => ({
      key: chapterKey(chapter),
      name: chapter.name || chapter.title,
      count: chapter._points.length
    }))
})

const filteredChapters = computed(() => {
  if (!activeCourse.value) return []
  let chapters = activeCourse.value._chapters || []
  if (chapterFilter.value) {
    chapters = chapters.filter(chapter => chapterKey(chapter) === chapterFilter.value)
  }

  const q = keyword.value.trim().toLowerCase()
  if (!q) return chapters

  return chapters
    .map(chapter => ({
      ...chapter,
      _points: chapter._points.filter(point =>
        String(point.name || '').toLowerCase().includes(q)
        || String(point.content || '').toLowerCase().includes(q)
      )
    }))
    .filter(chapter => chapter._points.length > 0)
})

watch(() => props.activeCourseId, () => {
  keyword.value = ''
  chapterFilter.value = ''
  collapsedChapters.value = {}
})

function chapterKey(chapter) {
  if (chapter._flat) return 'flat'
  return chapter.id !== null && chapter.id !== undefined ? String(chapter.id) : 'unassigned'
}

function isChapterCollapsed(key) {
  // 搜索时自动展开，避免命中结果被折叠隐藏
  if (keyword.value.trim()) return false
  return !!collapsedChapters.value[key]
}

function toggleChapter(key) {
  collapsedChapters.value = {
    ...collapsedChapters.value,
    [key]: !collapsedChapters.value[key]
  }
}

function chapterWeakCount(points) {
  return points.filter(point => weakSet.value.has(String(point.id))).length
}

function handleSelectCourse(course) {
  emit('select-course', course)
}

function isWeakPoint(point) {
  return weakSet.value.has(String(point.id))
}

function difficultyLabel(d) {
  return { 1: '简单', 2: '较易', 3: '中等', 4: '较难', 5: '困难' }[d] || '未知'
}

function difficultyTag(d) {
  return { 1: 'success', 2: 'primary', 3: 'warning', 4: 'danger', 5: 'danger' }[d] || 'info'
}
</script>

<style scoped>
.course-panel {
  margin-bottom: 16px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}

.section-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}

.panel-tip {
  color: #909399;
  font-size: 12px;
}

.course-empty {
  padding: 12px 0;
}

/* 左课程 + 右知识点 */
.course-workbench {
  display: flex;
  min-height: 420px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.course-sidebar {
  flex: 0 0 260px;
  display: flex;
  flex-direction: column;
  max-height: 600px;
  padding: 10px;
  background: #fafbfc;
  border-right: 1px solid #ebeef5;
}

.sidebar-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
  padding: 0 2px;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.course-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 7px;
  padding-right: 2px;
}

.course-list::-webkit-scrollbar,
.pane-body::-webkit-scrollbar {
  width: 5px;
}

.course-list::-webkit-scrollbar-thumb,
.pane-body::-webkit-scrollbar-thumb {
  background: #d0d5dd;
  border-radius: 3px;
}

.course-item {
  width: 100%;
  flex-shrink: 0;
  padding: 8px 10px;
  text-align: left;
  font-family: inherit;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.course-item:hover {
  border-color: #a0cfff;
  background: #f7fbff;
}

.course-item.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.12);
}

.course-item-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.course-item-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.course-item-check {
  flex-shrink: 0;
  color: #409eff;
}

.course-item-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 5px;
  font-size: 12px;
  color: #909399;
}

.course-item-done {
  color: #67c23a;
}

.course-points-pane {
  flex: 1;
  min-width: 0;
  max-height: 600px;
  display: flex;
  flex-direction: column;
}

.pane-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.pane-title {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.pane-course-name {
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.pane-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.point-search {
  width: 200px;
}

.chapter-filter {
  width: 170px;
}

.pane-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 10px 12px 12px;
  background: #fff;
}

.pane-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.chapter-block + .chapter-block {
  margin-top: 10px;
}

.chapter-head {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  font-family: inherit;
  text-align: left;
  border: 0;
  border-radius: 6px;
  background: #f5f7fa;
  cursor: pointer;
  transition: background 0.2s;
}

.chapter-head:hover {
  background: #ecf5ff;
}

.chapter-arrow {
  flex-shrink: 0;
  color: #909399;
  transition: transform 0.2s;
}

.chapter-arrow.collapsed {
  transform: rotate(-90deg);
}

.chapter-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 600;
  color: #606266;
}

.chapter-weak {
  flex-shrink: 0;
  font-size: 12px;
  color: #f56c6c;
}

.chapter-hint {
  flex-shrink: 0;
  margin-left: auto;
  font-size: 12px;
  color: #909399;
}

.point-rows {
  padding: 2px 0 0;
}

.point-row {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 100%;
  padding: 8px 10px;
  font-family: inherit;
  text-align: left;
  border: 0;
  border-bottom: 1px solid #f2f4f7;
  background: #fff;
  cursor: pointer;
  transition: background 0.15s;
}

.point-row:last-child {
  border-bottom: none;
}

.point-row:hover {
  background: #f7fbff;
}

.point-row.active {
  background: #ecf5ff;
  box-shadow: inset 3px 0 0 #409eff;
}

.point-row.weak .point-row-name {
  color: #303133;
}

.point-status {
  flex-shrink: 0;
  width: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  display: inline-block;
}

.point-row-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13.5px;
  font-weight: 500;
  color: #303133;
}

.point-row-meta {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5px;
  flex-wrap: wrap;
  max-width: 58%;
  flex-shrink: 0;
}

.point-row-meta :deep(.el-tag) {
  height: 20px;
  padding: 0 5px;
  font-size: 11px;
  line-height: 18px;
}

.point-video {
  flex-shrink: 0;
  color: #409eff;
}

@media (max-width: 900px) {
  .course-workbench {
    flex-direction: column;
  }

  .course-sidebar {
    flex-basis: auto;
    max-height: none;
    border-right: 0;
    border-bottom: 1px solid #ebeef5;
  }

  .course-list {
    flex-direction: row;
    overflow-x: auto;
    overflow-y: hidden;
    padding-bottom: 2px;
  }

  .course-item {
    flex: 0 0 200px;
  }

  .course-points-pane {
    max-height: 520px;
  }

  .pane-course-name {
    max-width: 180px;
  }

  .point-row-meta {
    max-width: 50%;
  }
}

@media (max-width: 600px) {
  .point-row-meta {
    display: none;
  }

  .point-search {
    width: 100%;
  }

  .pane-actions {
    width: 100%;
  }
}
</style>
