<template>
  <div class="app-container">
    <el-card shadow="never" class="chunk-card">
      <template #header>
        <div class="card-header">
          <div class="card-title">
            <el-icon><Tickets /></el-icon>
            <span>知识库切片</span>
            <span class="card-sub">浏览已入库的切片分块（课本 / 知识点 / 逐字稿 / 章节 / 题库）</span>
          </div>
          <div class="card-actions">
            <el-button size="small" :icon="Refresh" :loading="loading" @click="loadChunks">刷新</el-button>
            <el-button size="small" :icon="ArrowLeft" @click="goBack">返回知识库</el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <el-form :model="query" :inline="true" class="chunk-filter">
        <el-form-item label="学科" prop="subjectId">
          <el-select
            v-model="query.subjectId"
            placeholder="全部学科"
            clearable
            filterable
            style="width: 200px"
            @change="onSubjectChange"
          >
            <el-option v-for="item in subjectList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节" prop="chapterId">
          <el-select
            v-model="query.chapterId"
            placeholder="全部章节"
            clearable
            filterable
            style="width: 230px"
          >
            <el-option
              v-for="item in chapterOptions"
              :key="item.id"
              :label="formatChapterLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型" prop="sourceType">
          <el-select v-model="query.sourceType" placeholder="全部类型" clearable style="width: 150px">
            <el-option label="课本切片" value="document" />
            <el-option label="知识点" value="point" />
            <el-option label="逐字稿" value="transcript" />
            <el-option label="章节" value="chapter" />
            <el-option label="练习" value="quiz" />
          </el-select>
        </el-form-item>
        <el-form-item label="每页">
          <el-select v-model="query.pageSize" style="width: 100px" @change="handleQuery">
            <el-option label="20 条" :value="20" />
            <el-option label="50 条" :value="50" />
            <el-option label="100 条" :value="100" />
            <el-option label="200 条" :value="200" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :loading="loading" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 概览 -->
      <div class="chunk-summary">
        <div class="summary-total">
          共 <b>{{ total }}</b> 段切片
          <span class="summary-scope">{{ scopeLabel }}</span>
        </div>
        <div class="summary-tags">
          <el-tag
            v-for="item in typeSummary"
            :key="item.sourceType"
            size="small"
            effect="plain"
            :type="sourceTagType(item.sourceType)"
          >
            {{ kbTypeLabel(item.sourceType) }} {{ item.total }}
          </el-tag>
          <span v-if="typeSummary.length === 0" class="text-secondary">暂无分块</span>
        </div>
      </div>

      <!-- 切片列表 -->
      <el-table ref="tableRef" v-loading="loading" :data="chunkList" row-key="id" style="width: 100%">
        <el-table-column type="expand">
          <template #default="scope">
            <div class="chunk-detail">
              <div class="chunk-meta">
                <span>ID: {{ scope.row.id }}</span>
                <span v-if="scope.row.docId">来源: {{ scope.row.docId }}</span>
                <span v-if="scope.row.sectionPath">章节路径: {{ scope.row.sectionPath }}</span>
                <span v-if="scope.row.pageStart">页码: {{ scope.row.pageStart }}{{ scope.row.pageEnd && scope.row.pageEnd !== scope.row.pageStart ? '-' + scope.row.pageEnd : '' }}</span>
                <span>切片序号: {{ scope.row.chunkIndex }}</span>
                <span>Token: {{ scope.row.tokenCount }}</span>
              </div>
              <pre class="chunk-content">{{ scope.row.content }}</pre>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100" align="center">
          <template #default="scope">
            <el-tag :type="sourceTagType(scope.row.sourceType)" size="small" effect="plain">
              {{ kbTypeLabel(scope.row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
        <el-table-column label="章节路径" prop="sectionPath" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.sectionPath || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="页码" width="80" align="center">
          <template #default="scope">
            <span>{{ scope.row.pageStart ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Token" width="90" align="center">
          <template #default="scope">
            <span>{{ scope.row.tokenCount ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内容预览" min-width="320">
          <template #default="scope">
            <span class="chunk-preview">{{ scope.row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="View" @click="toggleExpand(scope.row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无切片数据，可先在知识库页重建或导入课本" :image-size="90" />
        </template>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="query.pageNum"
        v-model:limit="query.pageSize"
        @pagination="loadChunks"
      />
    </el-card>
  </div>
</template>

<script setup name="KbChunks">
import { ArrowLeft, Refresh, Search, Tickets, View } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { getKbStatus, listKbChunks } from '@/api/project/kb'
import { listSubject } from '@/api/project/subject'
import { listChapter } from '@/api/project/chapter'
import { formatChapterLabel } from '@/utils/chapter'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const chunkList = ref([])
const total = ref(0)
const subjectList = ref([])
const chapterOptions = ref([])
const typeSummary = ref([])

const query = reactive({
  subjectId: null,
  chapterId: null,
  sourceType: null,
  pageNum: 1,
  pageSize: 50
})

/** 表格展开行：点击「详情」时展开当前分块的完整内容 */
const tableRef = ref(null)

function kbTypeLabel(sourceType) {
  const map = { transcript: '逐字稿', point: '知识点', chapter: '章节', quiz: '练习', document: '课本' }
  return map[sourceType] || sourceType || '其他'
}

function sourceTagType(sourceType) {
  const map = { transcript: 'success', point: 'primary', chapter: 'warning', quiz: 'info', document: 'danger' }
  return map[sourceType] || 'info'
}

const scopeLabel = computed(() => {
  const parts = []
  if (query.subjectId) {
    const subject = subjectList.value.find(item => item.id === query.subjectId)
    parts.push(subject ? subject.name : `学科 ${query.subjectId}`)
  }
  if (query.chapterId) {
    const chapter = chapterOptions.value.find(item => item.id === query.chapterId)
    parts.push(chapter ? formatChapterLabel(chapter) : `章节 ${query.chapterId}`)
  }
  if (query.sourceType) {
    parts.push(kbTypeLabel(query.sourceType))
  }
  return parts.length ? `范围：${parts.join(' / ')}` : '范围：全部切片'
})

function loadSubjects() {
  return listSubject({ pageSize: 9999, pageNum: 1 }).then(res => {
    subjectList.value = res.rows || res.data || []
  }).catch(() => {
    subjectList.value = []
  })
}

/** 章节下拉随所选学科联动 */
function loadChapters(subjectId) {
  if (!subjectId) {
    chapterOptions.value = []
    return Promise.resolve()
  }
  return listChapter({ subjectId }).then(res => {
    chapterOptions.value = res.data || res.rows || []
  }).catch(() => {
    chapterOptions.value = []
  })
}

function onSubjectChange(subjectId) {
  query.chapterId = null
  loadChapters(subjectId)
}

function handleQuery() {
  query.pageNum = 1
  loadChunks()
}

function resetQuery() {
  query.subjectId = null
  query.chapterId = null
  query.sourceType = null
  query.pageNum = 1
  chapterOptions.value = []
  loadChunks()
}

function loadChunks() {
  loading.value = true
  listKbChunks({
    subjectId: query.subjectId || undefined,
    chapterId: query.chapterId || undefined,
    sourceType: query.sourceType || undefined,
    pageNum: query.pageNum,
    pageSize: query.pageSize
  }).then(res => {
    const data = res.data || {}
    chunkList.value = data.rows || []
    total.value = data.total || 0
  }).catch(() => {
    chunkList.value = []
    total.value = 0
  }).finally(() => {
    loading.value = false
  })
  loadTypeSummary()
}

/** 顶部按类型统计（跟随学科筛选） */
function loadTypeSummary() {
  return getKbStatus(query.subjectId || undefined).then(res => {
    typeSummary.value = (res.data?.byType || []).filter(item => item.total > 0)
  }).catch(() => {
    typeSummary.value = []
  })
}

/** 展开当前行显示完整内容 */
function toggleExpand(row) {
  const table = tableRef.value
  if (!table) return
  table.toggleRowExpansion(row)
}

function goBack() {
  router.back()
}

// 支持从知识库页带参进入：?subjectId=&chapterId=&sourceType=
onMounted(() => {
  const subjectId = Number(route.query.subjectId)
  const chapterId = Number(route.query.chapterId)
  if (Number.isFinite(subjectId) && subjectId > 0) query.subjectId = subjectId
  if (Number.isFinite(chapterId) && chapterId > 0) query.chapterId = chapterId
  if (route.query.sourceType) query.sourceType = String(route.query.sourceType)

  loadSubjects().then(() => {
    if (query.subjectId) loadChapters(query.subjectId)
    loadChunks()
  })
})
</script>

<style scoped>
.app-container {
  background-color: #f7f8fa;
  padding: 20px;
  min-height: 100vh;
}

.chunk-card {
  border-radius: 8px;
  border: 1px solid #ebeef5;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #1f2937;
}

.card-sub {
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
}

.card-actions {
  display: flex;
  gap: 8px;
}

.chunk-filter {
  background-color: #fafbfc;
  border-radius: 8px;
  padding: 12px 12px 0;
  margin-bottom: 12px;
}

.chunk-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 10px 12px;
  margin-bottom: 10px;
  border-radius: 8px;
  background-color: #f8fafc;
  border: 1px solid #eef2f7;
}

.summary-total {
  font-size: 13px;
  color: #475569;
}

.summary-total b {
  font-size: 16px;
  color: #0f172a;
  margin: 0 2px;
}

.summary-scope {
  margin-left: 10px;
  font-size: 12px;
  color: #94a3b8;
}

.summary-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.text-secondary {
  color: #909399;
  font-size: 13px;
}

.chunk-preview {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}

.chunk-detail {
  padding: 4px 12px 10px;
}

.chunk-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 8px;
}

.chunk-content {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.8;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 420px;
  overflow: auto;
}
</style>
