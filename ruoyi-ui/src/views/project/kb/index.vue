<template>
  <div class="app-container">
    <!-- 概览 -->
    <el-card shadow="never" class="kb-card">
      <template #header>
        <div class="card-header">
          <span>知识库（视频 AI 检索底座）</span>
          <div class="kb-actions">
            <el-button size="small" :icon="Refresh" :loading="kbLoading" @click="loadAll">刷新</el-button>
            <el-button
              size="small"
              type="success"
              plain
              :icon="Document"
              v-hasPermi="['project:point:edit']"
              @click="openImportDialog"
            >导入课本</el-button>
            <el-button
              size="small"
              :icon="Tickets"
              @click="goChunkPage()"
            >浏览切片</el-button>
            <el-button
              size="small"
              type="primary"
              plain
              :icon="Upload"
              :loading="kbRebuilding"
              v-hasPermi="['project:point:edit']"
              @click="openRebuildDialog"
            >重建知识库</el-button>
          </div>
        </div>
      </template>
      <div v-loading="kbLoading" class="kb-body">
        <el-alert
          v-if="!kbEmbedding.configured"
          type="warning"
          show-icon
          :closable="false"
          title="向量化未配置：请在 config/application.yml 配置 kb.embedding（或 langchain.api-key），否则无法重建与检索"
          style="margin-bottom: 12px"
        />
        <div class="kb-grid">
          <div class="kb-item">
            <div class="kb-label">分块总数</div>
            <div class="kb-value">{{ kbStatus.total || 0 }}</div>
          </div>
          <div class="kb-item">
            <div class="kb-label">向量库</div>
            <div class="kb-value kb-value-sm">
              {{ kbStatus.vectorStore?.activeProvider || '-' }}
              <el-tag :type="kbStatus.vectorStore?.available ? 'success' : 'danger'" size="small" effect="plain">
                {{ kbStatus.vectorStore?.available ? '可用' : '不可用' }}
              </el-tag>
            </div>
          </div>
          <div class="kb-item">
            <div class="kb-label">向量化模型</div>
            <div class="kb-value kb-value-sm">
              {{ kbEmbedding.model || '-' }} / {{ kbEmbedding.dimensions || '-' }} 维
            </div>
          </div>
          <div class="kb-item">
            <div class="kb-label">语料构成</div>
            <div class="kb-value kb-value-sm">
              <el-tag
                v-for="item in kbByType"
                :key="item.sourceType"
                size="small"
                effect="plain"
                style="margin-right: 4px"
              >{{ kbTypeLabel(item.sourceType) }} {{ item.total }}</el-tag>
              <span v-if="kbByType.length === 0" class="text-secondary">暂无数据</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 知识库列表 -->
    <el-card shadow="never" class="kb-list-card">
      <template #header>
        <div class="card-header">
          <span>知识库列表</span>
          <span class="card-sub">每个学科对应一个知识库（随学科自动创建）；可在此重建 / 检索 / 清空</span>
        </div>
      </template>

      <el-table v-loading="kbLoading" :data="kbBases" style="width: 100%">
        <el-table-column label="知识库" prop="label" min-width="300" show-overflow-tooltip />
        <el-table-column label="层级" width="100" align="center">
          <template #default="scope">
            <el-tag :type="levelTagType(scope.row.level)" size="small" effect="plain">
              {{ levelLabel(scope.row.level) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="学科ID" prop="subjectId" width="90" align="center">
          <template #default="scope">
            <span>{{ scope.row.subjectId ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="知识库ID" prop="kbId" width="100" align="center">
          <template #default="scope">
            <span>{{ scope.row.kbId ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分块数" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.chunkCount > 0 ? 'success' : 'info'" size="small">
              {{ scope.row.chunkCount }} 段
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'danger' : 'success'" size="small">
              {{ scope.row.status === '1' ? '停用' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="Search" @click="openSearchDialog(scope.row)">检索</el-button>
            <el-button
              v-if="scope.row.subjectId"
              link
              type="success"
              :icon="Upload"
              v-hasPermi="['project:point:edit']"
              @click="handleRebuild(scope.row)"
            >重建</el-button>
            <el-button
              v-if="scope.row.subjectId"
              link
              type="primary"
              :icon="Tickets"
              @click="goChunkPage(scope.row)"
            >切片</el-button>
            <el-button
              v-if="scope.row.level !== 'all'"
              link
              type="danger"
              :icon="Delete"
              v-hasPermi="['project:point:remove']"
              @click="handleClear(scope.row)"
            >清空</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 重建知识库 -->
    <el-dialog v-model="rebuildVisible" title="重建知识库" width="560px" append-to-body>
      <el-form :model="rebuildForm" label-width="100px">
        <el-form-item label="学科" required>
          <el-select v-model="rebuildForm.subjectId" placeholder="请选择学科" filterable style="width: 100%" @change="onRebuildSubjectChange">
            <el-option v-for="item in subjectList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="rebuildForm.chapterId" placeholder="全部章节" clearable style="width: 100%">
            <el-option
              v-for="item in chapterOptions"
              :key="item.id"
              :label="formatChapterLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="题库ID">
          <el-input-number
            v-model="rebuildForm.testId"
            :min="1"
            controls-position="right"
            placeholder="留空则不导入题库"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="视频逐字稿">
          <el-switch v-model="rebuildForm.withTranscript" />
          <span class="form-tip">仅《计算机网络》学科（含内置逐字稿）可附带</span>
        </el-form-item>
      </el-form>
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="重建为覆盖式：会先清空所选范围内的旧分块，再按「章节 + 知识点 + 逐字稿 + 题库」重新构建。"
      />
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="kbRebuilding" @click="submitRebuild">开始重建</el-button>
          <el-button @click="rebuildVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 语义检索 -->
    <el-dialog v-model="searchVisible" title="知识库检索测试" width="860px" append-to-body>
      <div class="search-bar">
        <el-input
          v-model="searchForm.query"
          placeholder="输入问题或关键词，例如：分组交换有什么特点"
          clearable
          @keyup.enter="doSearch"
        />
        <el-input-number v-model="searchForm.topK" :min="1" :max="20" controls-position="right" style="width: 120px" />
        <el-button type="primary" :icon="Search" :loading="searchLoading" @click="doSearch">检索</el-button>
      </div>
      <div class="search-scope">检索范围：{{ searchScopeLabel }}</div>
      <el-table v-loading="searchLoading" :data="searchResults" style="width: 100%" max-height="420">
        <el-table-column label="类型" width="90" align="center">
          <template #default="scope">
            <el-tag :type="sourceTagType(scope.row.sourceType)" size="small" effect="plain">
              {{ kbTypeLabel(scope.row.sourceType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" prop="title" width="180" show-overflow-tooltip />
        <el-table-column label="相似度" width="90" align="center">
          <template #default="scope">
            <span>{{ scope.row.score != null ? Number(scope.row.score).toFixed(3) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="300">
          <template #default="scope">
            <span class="result-content">{{ scope.row.content }}</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!searchLoading && searchResults.length === 0" description="暂无结果，换一个问法试试" :image-size="70" />
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="searchVisible = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 导入课本 -->
    <el-dialog v-model="importVisible" title="导入课本（自动切片入库）" width="600px" append-to-body>
      <el-form :model="importForm" label-width="100px">
        <el-form-item label="学科" required>
          <el-select v-model="importForm.subjectId" placeholder="请选择归属学科" filterable style="width: 100%" @change="onImportSubjectChange">
            <el-option v-for="item in subjectList" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="章节">
          <el-select v-model="importForm.chapterId" placeholder="整本归属学科（不选则不分章节）" clearable style="width: 100%">
            <el-option
              v-for="item in importChapterOptions"
              :key="item.id"
              :label="formatChapterLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="切片模型">
          <el-select v-model="importForm.model" placeholder="使用系统默认模型" clearable style="width: 100%">
            <el-option
              v-for="m in modelOptions"
              :key="m.id"
              :label="m.label"
              :value="m.id"
            />
          </el-select>
          <div class="form-tip">用于「智能语义切片」的大模型；留空则用默认模型</div>
        </el-form-item>
        <el-form-item label="课本文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="onFileChange"
            :on-remove="onFileRemove"
            accept=".md,.markdown,.txt,.docx,.pdf"
          >
            <template #trigger>
              <el-button :icon="Upload">选择文件</el-button>
            </template>
            <template #tip>
              <div class="form-tip">支持 Markdown / 纯文本 / Word(.docx) / PDF（需文本层）；自动解析并按语义切片</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="知识拓扑">
          <el-checkbox v-model="importForm.withTopology">导入后自动生成知识拓扑</el-checkbox>
          <div class="form-tip">
            用大模型从课本正文抽取「知识点 + 关系」，直接写入知识图谱（知识架构管理 → 知识拓扑）
          </div>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="importResult"
        :type="importResult.success ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="importResult.success
          ? `导入成功：共 ${importResult.total} 段切片，已写入向量库（${importResult.provider}）`
          : ('失败：' + (importResult.error || '未知错误'))"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-if="topologyResult"
        :type="topologyResult.ok ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="topologyResult.text"
        style="margin-bottom: 12px"
      >
        <el-button v-if="topologyResult.ok" link type="primary" @click="goTopologyPage">
          查看知识拓扑 →
        </el-button>
      </el-alert>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="importing" @click="submitImport">
            {{ importing && topologyRunning ? '生成知识拓扑中…' : '开始导入并切片' }}
          </el-button>
          <el-button @click="importVisible = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    </div>
</template>

<script setup name="KnowledgeBase">
import { Delete, Document, Refresh, Search, Tickets, Upload } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { clearKb, getKbStatus, importDocument, listKbBases, rebuildKb, searchKb } from '@/api/project/kb'
import { generateTextbookTopology, getCourseModels } from '@/api/project/course'
import { listSubject } from '@/api/project/subject'
import { listChapter } from '@/api/project/chapter'
import { formatChapterLabel, dedupeChapters } from '@/utils/chapter'
import networkTranscript from '../course/network-transcript.json'

/** 内置「计算机网络」逐字稿语料所属的学科ID（仅用于示例数据） */
const SAMPLE_TRANSCRIPT_SUBJECT_ID = 94

const { proxy } = getCurrentInstance()
const router = useRouter()
const route = useRoute()

const kbLoading = ref(false)
const kbRebuilding = ref(false)
const kbStatus = ref({ total: 0, byType: [], vectorStore: {}, embedding: {} })
const kbEmbedding = ref({})
const kbBases = ref([])

const subjectList = ref([])
const chapterOptions = ref([])

const rebuildVisible = ref(false)
const rebuildForm = reactive({
  subjectId: null,
  chapterId: null,
  // 留空表示不灌题库：原先固定 102 会把「计算机网络第1章」的题写进任意学科
  testId: null,
  withTranscript: false
})

const searchVisible = ref(false)
const searchLoading = ref(false)
const searchResults = ref([])
const searchForm = reactive({ query: '', topK: 5, subjectId: null, chapterId: null })
const searchScopeLabel = ref('全部知识库')

// 导入课本
const importVisible = ref(false)
const importing = ref(false)
const importForm = reactive({ subjectId: null, chapterId: null, model: null, file: null, withTopology: true })
const topologyRunning = ref(false)
const topologyResult = ref(null)
const importChapterOptions = ref([])
const modelOptions = ref([])
const importResult = ref(null)

// 切片浏览（独立二级界面 project/kb/chunks）
const uploadRef = ref(null)

const kbByType = computed(() => kbStatus.value.byType || [])

function kbTypeLabel(sourceType) {
  const map = { transcript: '逐字稿', point: '知识点', chapter: '章节', quiz: '练习', document: '课本' }
  return map[sourceType] || sourceType || '其他'
}

function levelLabel(level) {
  const map = { all: '全局', subject: '学科', chapter: '章节' }
  return map[level] || level || '-'
}

function levelTagType(level) {
  const map = { all: 'info', subject: 'success', chapter: 'warning' }
  return map[level] || 'info'
}

function sourceTagType(sourceType) {
  const map = { transcript: 'success', point: 'primary', chapter: 'warning', quiz: 'info' }
  return map[sourceType] || 'info'
}

/** 概览 + 列表一起刷新 */
function loadAll() {
  loadKbStatus()
  loadBases()
}

function loadKbStatus() {
  kbLoading.value = true
  return getKbStatus().then(res => {
    const data = res.data || {}
    kbStatus.value = data
    kbEmbedding.value = data.embedding || {}
  }).catch(() => {}).finally(() => {
    kbLoading.value = false
  })
}

function loadBases() {
  return listKbBases().then(res => {
    const data = res.data || {}
    kbBases.value = (data.bases || []).map(item => ({
      ...item,
      kbId: item.kbId,
      status: item.status || '0'
    }))
  }).catch(() => {
    kbBases.value = []
  })
}

function loadSubjects() {
  return listSubject({ pageSize: 9999, pageNum: 1 }).then(res => {
    subjectList.value = res.rows || res.data || []
  }).catch(() => {
    subjectList.value = []
  })
}

function onRebuildSubjectChange(subjectId) {
  rebuildForm.chapterId = null
  chapterOptions.value = []
  if (!subjectId) return
  listChapter({ subjectId }).then(res => {
    chapterOptions.value = dedupeChapters(res.data || res.rows || [])
  }).catch(() => {
    chapterOptions.value = []
  })
}

function openRebuildDialog() {
  rebuildForm.subjectId = null
  rebuildForm.chapterId = null
  rebuildForm.testId = null
  rebuildForm.withTranscript = false
  chapterOptions.value = []
  rebuildVisible.value = true
}

function buildTranscriptPayload() {
  return (networkTranscript.segments || [])
    .map(segment => ({
      start: Math.floor((segment.start || 0) / 1000),
      text: String(segment.text || '').trim()
    }))
    .filter(item => item.text)
}

function submitRebuild() {
  if (!rebuildForm.subjectId) {
    proxy.$modal.msgWarning('请先选择学科')
    return
  }
  const payload = {
    subjectId: rebuildForm.subjectId
  }
  if (rebuildForm.testId) {
    payload.testId = rebuildForm.testId
  }
  if (rebuildForm.chapterId) {
    payload.chapterId = rebuildForm.chapterId
  }
  if (rebuildForm.withTranscript) {
    payload.transcript = buildTranscriptPayload()
  }
  kbRebuilding.value = true
  rebuildKb(payload).then(res => {
    const data = res.data || {}
    const stats = data.stats || {}
    const detail = Object.keys(stats).map(k => `${kbTypeLabel(k)} ${stats[k]}`).join('，')
    proxy.$modal.msgSuccess(`重建完成：共 ${data.total || 0} 段（${detail || '无'}）`)
    rebuildVisible.value = false
    loadAll()
  }).catch(error => {
    proxy.$modal.msgError('重建失败：' + (error.message || '请稍后重试'))
  }).finally(() => {
    kbRebuilding.value = false
  })
}

/** 行内重建：沿用该行的学科范围（知识库以学科为单位） */
function handleRebuild(row) {
  if (!row.subjectId) {
    proxy.$modal.msgWarning('全局知识库请使用顶部「重建知识库」并选择学科')
    return
  }
  const range = `学科「${row.label}」`
  proxy.$modal.confirm(`确认重建${range}的知识库？将覆盖该范围的旧分块。`).then(() => {
    kbRebuilding.value = true
    // 不再默认带 testId：避免把某个学科的题库灌进其它学科
    const payload = { subjectId: row.subjectId }
    if (row.subjectId === SAMPLE_TRANSCRIPT_SUBJECT_ID) {
      payload.transcript = buildTranscriptPayload()
    }
    return rebuildKb(payload).then(res => {
      const data = res.data || {}
      proxy.$modal.msgSuccess(`重建完成：共 ${data.total || 0} 段`)
      loadAll()
    })
  }).catch((error) => {
    // 用户点取消不提示；真实失败必须提示，否则会误以为重建成功
    if (error !== 'cancel' && error !== 'close') {
      proxy.$modal.msgError('重建失败：' + (error?.message || error || '未知错误'))
    }
  }).finally(() => {
    kbRebuilding.value = false
  })
}

/** 清空指定范围 */
function handleClear(row) {
  // 后端已要求必须带学科；「全部知识库」这类聚合行没有学科，不允许清空
  if (!row.subjectId) {
    proxy.$modal.msgWarning('请按学科清空，不支持清空全部知识库')
    return
  }
  const range = `学科「${row.label}」`
  proxy.$modal.confirm(`确认清空${range}的知识库分块？该操作不可恢复。`).then(() => {
    return clearKb({
      subjectId: row.subjectId,
      chapterId: row.chapterId
    })
  }).then(res => {
    const removed = res.data?.removed ?? res.data ?? 0
    proxy.$modal.msgSuccess(`已清空 ${removed} 段`)
    loadAll()
  }).catch(() => {})
}

function openSearchDialog(row) {
  searchForm.subjectId = row.subjectId
  searchForm.chapterId = row.chapterId
  searchForm.query = ''
  searchForm.topK = 5
  searchResults.value = []
  searchScopeLabel.value = row.level === 'all' ? '全部知识库' : row.label
  searchVisible.value = true
}

function doSearch() {
  const query = (searchForm.query || '').trim()
  if (!query) {
    proxy.$modal.msgWarning('请输入检索内容')
    return
  }
  searchLoading.value = true
  searchKb({
    q: query,
    subjectId: searchForm.subjectId ?? undefined,
    chapterId: searchForm.chapterId ?? undefined,
    topK: searchForm.topK
  }).then(res => {
    searchResults.value = res.data || []
  }).catch(error => {
    proxy.$modal.msgError('检索失败：' + (error.message || '请稍后重试'))
  }).finally(() => {
    searchLoading.value = false
  })
}

// ===== 导入课本（自动解析 + LLM 语义切片入库） =====
function openImportDialog() {
  importForm.subjectId = null
  importForm.chapterId = null
  importForm.model = null
  importForm.file = null
  importForm.withTopology = true
  importChapterOptions.value = []
  importResult.value = null
  topologyResult.value = null
  if (uploadRef.value) {
    uploadRef.value.clearFiles()
  }
  if (modelOptions.value.length === 0) {
    getCourseModels().then(res => {
      modelOptions.value = res.data || res.rows || []
    }).catch(() => {})
  }
  importVisible.value = true
}

function onImportSubjectChange(subjectId) {
  importForm.chapterId = null
  importChapterOptions.value = []
  if (!subjectId) return
  listChapter({ subjectId }).then(res => {
    importChapterOptions.value = res.data || res.rows || []
  }).catch(() => {
    importChapterOptions.value = []
  })
}

function onFileChange(file) {
  importForm.file = file.raw
}

function onFileRemove() {
  importForm.file = null
}

function submitImport() {
  if (!importForm.subjectId) {
    proxy.$modal.msgWarning('请先选择归属学科')
    return
  }
  if (!importForm.file) {
    proxy.$modal.msgWarning('请选择课本文件')
    return
  }
  const formData = new FormData()
  formData.append('file', importForm.file)
  formData.append('subjectId', importForm.subjectId)
  const subjectName = (subjectList.value.find(s => s.id === importForm.subjectId) || {}).name || ''
  formData.append('subjectName', subjectName)
  if (importForm.chapterId) {
    formData.append('chapterId', importForm.chapterId)
  }
  if (importForm.model) {
    formData.append('model', importForm.model)
  }
  importing.value = true
  importResult.value = null
  topologyResult.value = null
  importDocument(formData).then(res => {
    importResult.value = res.data || {}
    if (importResult.value.success) {
      proxy.$modal.msgSuccess(`导入成功：共 ${importResult.value.total} 段切片已入库`)
      loadAll()
      if (importForm.withTopology && importResult.value.documentId) {
        return buildTopology(importResult.value.documentId)
      }
    }
    return null
  }).catch(error => {
    importResult.value = { success: false, error: error.message || '未知错误' }
  }).finally(() => {
    importing.value = false
    topologyRunning.value = false
  })
}

/** 课本 → 知识拓扑：读取刚导入的课本正文，AI 抽取知识点并同步进知识图谱 */
function buildTopology(documentId) {
  topologyRunning.value = true
  topologyResult.value = { ok: false, text: '正在用大模型抽取知识拓扑，请稍候（可能需要 1-2 分钟）…' }
  return generateTextbookTopology({
    subjectId: importForm.subjectId,
    documentId,
    model: importForm.model || undefined,
    autoSync: true
  }).then(res => {
    const data = res.data || {}
    const created = data.createdPoints || 0
    const matched = data.matchedPoints || 0
    const relations = data.createdRelations || 0
    const truncatedTip = data.truncated
      ? `（正文超过单次上限，仅分析前 ${data.chars} 字；整本教材建议按章导入后分别生成）`
      : ''
    topologyResult.value = {
      ok: true,
      text: `知识拓扑已生成：${data.nodeCount || 0} 个知识点、${data.relationCount || 0} 条关系；`
        + `入库新增 ${created} 个、复用 ${matched} 个知识点，写入 ${relations} 条关系`
        + truncatedTip
    }
    return data
  }).catch(error => {
    topologyResult.value = {
      ok: false,
      text: '课本已导入，但知识拓扑生成失败：' + (error.message || '请稍后重试')
    }
    return null
  }).finally(() => {
    topologyRunning.value = false
  })
}

/** 跳到知识拓扑页（自动带上学科） */
function goTopologyPage() {
  importVisible.value = false
  router.push({ path: '/topology', query: { subjectId: String(importForm.subjectId) } })
}

// ===== 切片浏览（跳转二级界面 /picture/kb/chunks） =====
function goChunkPage(row) {
  const query = {}
  if (row && row.subjectId) {
    query.subjectId = String(row.subjectId)
  }
  router.push({ path: `${route.path}/chunks`, query })
}

loadSubjects()
loadAll()
</script>

<style scoped>
.app-container {
  background-color: #f7f8fa;
  padding: 20px;
  min-height: 100vh;
}

.kb-card,
.kb-list-card {
  border-radius: 8px;
  border: 1px solid #ebeef5;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.kb-list-card {
  margin-top: 16px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
  color: #1f2937;
}

.card-sub {
  font-size: 12px;
  font-weight: 400;
  color: #94a3b8;
}

.kb-actions {
  display: flex;
  gap: 8px;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}

.kb-item {
  padding: 12px 14px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid #eef2f7;
}

.kb-label {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 6px;
}

.kb-value {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.kb-value-sm {
  font-size: 13px;
  font-weight: 500;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.text-secondary {
  color: #909399;
  font-size: 13px;
}

.form-tip {
  margin-left: 10px;
  font-size: 12px;
  color: #94a3b8;
}

.search-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.search-bar .el-input {
  flex: 1;
}

.search-scope {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 10px;
}

.result-content {
  font-size: 12px;
  line-height: 1.7;
  color: #475569;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
