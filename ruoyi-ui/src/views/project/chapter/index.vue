<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="所属学科" prop="subjectId">
        <el-select
          v-model="queryParams.subjectId"
          placeholder="请选择所属学科"
          clearable
          filterable
          style="width: 200px"
          @change="getList"
        >
          <el-option
            v-for="item in subjectList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="章号" prop="chapterNo">
        <el-input
          v-model="queryParams.chapterNo"
          placeholder="如：第1章"
          clearable
          style="width: 160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="章节名称" prop="title">
        <el-input
          v-model="queryParams.title"
          placeholder="请输入章节名称"
          clearable
          style="width: 180px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Plus"
          @click="handleAdd"
          v-hasPermi="['project:chapter:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['project:chapter:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['project:chapter:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="pagedChapters" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="章号" align="center" prop="chapterNo" width="90" />
      <el-table-column label="章节名称" align="center" prop="title" min-width="160" show-overflow-tooltip />
      <el-table-column label="所属学科" align="center" prop="subjectName" min-width="140" show-overflow-tooltip />
      <el-table-column label="课程名称" align="center" prop="courseTitle" min-width="140" show-overflow-tooltip>
        <template #default="scope">
          <span>{{ scope.row.courseTitle || '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="知识点数" align="center" prop="pointCount" width="90">
        <template #default="scope">
          <el-tag type="info" size="small">{{ scope.row.pointCount || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="课时" align="center" prop="lessonCount" width="90">
        <template #default="scope">
          <el-tag type="success" size="small">{{ scope.row.lessonCount || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="排序" align="center" prop="sortOrder" width="70" />
      <el-table-column label="视频" align="center" prop="video" width="100">
        <template #default="scope">
          <el-link v-if="scope.row.video" type="primary" :underline="false" icon="VideoCamera" @click="previewVideo(scope.row.video)">
            预览
          </el-link>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" align="center" prop="status" width="70">
        <template #default="scope">
          <el-tag :type="scope.row.status === '0' ? 'success' : 'danger'" size="small">
            {{ scope.row.status === '0' ? '正常' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="240" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleViewPoints(scope.row)">知识点</el-button>
          <el-button link type="primary" icon="Film" @click="handleViewLessons(scope.row)">课时</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['project:chapter:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['project:chapter:remove']">删除</el-button>
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

    <!-- 添加或修改章节对话框 -->
    <el-dialog :title="title" v-model="open" width="720px" append-to-body>
      <el-form ref="chapterRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属学科" prop="subjectId">
              <el-select
                v-model="form.subjectId"
                placeholder="请选择所属学科"
                filterable
                style="width: 100%"
                @change="onFormSubjectChange"
              >
                <el-option
                  v-for="item in subjectList"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="章号" prop="chapterNo">
              <el-input v-model="form.chapterNo" placeholder="如：第1章" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="章节名称" prop="title">
              <el-input v-model="form.title" placeholder="如：计算机网络概论" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="课程名称" prop="courseTitle">
              <el-input v-model="form.courseTitle" placeholder="如：计算机网络 · 第一章" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="章节描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="请输入章节描述" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="起始秒" prop="startTime">
              <el-input-number v-model="form.startTime" :min="0" :controls="false" placeholder="视频起始秒" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束秒" prop="endTime">
              <el-input-number v-model="form.endTime" :min="0" :controls="false" placeholder="视频结束秒" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="章节视频" prop="video">
              <div class="chapter-video-cell">
                <file-upload v-model="form.video" :limit="1" :fileSize="1024" :fileType="['mp4', 'webm', 'ogg', 'mov', 'avi', 'flv']" />
                <el-upload
                  :show-file-list="false"
                  :http-request="handleFormUploadVideo"
                  accept=".mp4,.mov,.avi,.mkv,.webm,.flv,.wmv,.ts,.m4v,.mpg,.mpeg,.3gp,video/*"
                  :disabled="formUploading"
                >
                  <el-button type="warning" icon="Upload" :loading="formUploading">上传视频</el-button>
                </el-upload>
                <div class="form-tip">
                  上传后按当前「所属学科 + 章号 + 章节名称」建章并挂到课时上，视频可直接播放；不做语音识别与 AI 解析。
                </div>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" :controls="false" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="章节知识点" prop="pointIds">
          <el-select
            v-model="form.pointIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :disabled="!form.subjectId"
            :placeholder="form.subjectId ? '请选择本章包含的知识点' : '请先选择所属学科'"
            style="width: 100%"
          >
            <el-option
              v-for="item in formPointList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
          <div class="form-tip">共 {{ formPointList.length }} 个候选知识点；保存后将重置本章知识点关联。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 章节知识点查看弹窗 -->
    <el-dialog v-model="pointsDialogVisible" :title="pointsDialogTitle" width="720px" destroy-on-close>
      <el-table v-loading="pointsLoading" :data="chapterPoints" max-height="460" empty-text="本章暂无知识点">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="知识点" align="center" prop="pointName" min-width="140" show-overflow-tooltip />
        <el-table-column label="小节" align="center" prop="section" min-width="180" show-overflow-tooltip>
          <template #default="scope">
            <span>{{ scope.row.section || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="难度" align="center" prop="difficulty" width="80">
          <template #default="scope">
            <el-tag :type="difficultyTag(scope.row.difficulty)" size="small">
              {{ difficultyLabel(scope.row.difficulty) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" align="center" prop="type" width="90">
          <template #default="scope">
            <el-tag v-if="scope.row.type" type="info" size="small">{{ scope.row.type }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="pointsDialogVisible = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- 课时抽屉：上传视频并解析（与课程学习同一套链路） -->
    <el-drawer v-model="lessonsVisible" :title="lessonsTitle" size="820px" append-to-body>
      <div class="upload-file-row" style="margin-bottom: 12px;">
        <el-button
          type="primary"
          icon="Upload"
          :disabled="uploading"
          v-hasPermi="['project:point:edit']"
          @click="openUploadDialog"
        >上传视频</el-button>
      </div>

      <el-table v-loading="lessonsLoading" :data="lessonList" empty-text="本章暂无课时">
        <el-table-column type="index" label="#" width="50" align="center" />
        <el-table-column label="课时编号" align="center" prop="lessonCode" width="110" />
        <el-table-column label="课时名称" align="center" prop="title" min-width="180" show-overflow-tooltip />
        <el-table-column label="时长" align="center" width="90">
          <template #default="scope">
            <span>{{ formatDuration(scope.row.durationSec) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="逐字稿" align="center" prop="transcriptCount" width="80" />
        <el-table-column label="知识点" align="center" prop="pointCount" width="80" />
        <el-table-column label="题目" align="center" prop="questionCount" width="80" />
        <el-table-column label="知识库" align="center" prop="kbCount" width="80" />
        <el-table-column label="视频" align="center" width="90">
          <template #default="scope">
            <el-link v-if="scope.row.videoPath" type="primary" :underline="false" icon="VideoCamera" @click="previewVideo(scope.row.videoPath)">
              预览
            </el-link>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" align="center" width="90">
          <template #default="scope">
            <el-button
              link
              type="danger"
              icon="Delete"
              v-hasPermi="['project:chapter:remove']"
              @click="handleDeleteLesson(scope.row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <!-- 上传视频信息表单：先填信息（章/课时/标题）再上传 -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传视频"
      width="560px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form :model="uploadForm" label-width="90px">
        <el-form-item label="视频文件" required>
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            accept=".mp4,.mov,.avi,.mkv,.webm,.flv,.wmv,.ts,.m4v,.mpg,.mpeg,.3gp,video/*"
            :on-change="onUploadFileChange"
          >
            <el-button icon="FolderOpened">选择视频</el-button>
          </el-upload>
          <span class="upload-file-name" style="margin-left: 10px">
            {{ uploadForm.file ? uploadForm.file.name : '未选择文件' }}
          </span>
        </el-form-item>
        <el-form-item label="章号" required>
          <el-input v-model="uploadForm.chapterNo" placeholder="如：第1章" />
        </el-form-item>
        <el-form-item label="章节名称">
          <el-input v-model="uploadForm.chapterTitle" placeholder="如：网络体系结构" />
        </el-form-item>
        <el-form-item label="课时编号">
          <el-input v-model="uploadForm.lessonCode" placeholder="留空自动编号，如 1.1.12" />
        </el-form-item>
        <el-form-item label="课时标题">
          <el-input v-model="uploadForm.lessonTitle" placeholder="默认取所选文件名" />
        </el-form-item>
        <el-form-item label="解析视频">
          <el-checkbox v-model="uploadForm.parseVideo">
            上传后自动解析视频
          </el-checkbox>
          <div class="form-tip">
            {{ uploadForm.parseVideo
              ? '解析在后台执行（约 2~5 分钟）：语音识别 → 知识点 → 随堂练习 → 知识库，完成后课程学习页可看到逐字稿、时间轴与题目。'
              : '不勾选则只把视频挂到课时，上传后立刻可以播放，不做语音识别与 AI 解析。' }}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="uploadDialogVisible = false">取 消</el-button>
          <el-button
            type="primary"
            :loading="uploading"
            :disabled="!uploadForm.file"
            @click="submitUploadDialog"
          >开始上传</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 视频预览弹窗 -->
    <el-dialog v-model="videoDialogVisible" title="章节视频预览" width="720px" destroy-on-close>
      <video
        v-if="videoUrl"
        :src="videoUrl"
        controls
        autoplay
        style="width: 100%; max-height: 480px; display: block;"
      >
        您的浏览器不支持视频播放
      </video>
    </el-dialog>
    <!-- 解析进度：独立悬浮窗（关闭抽屉后仍可见，便于后台任务跟进） -->
    <import-progress
      :task="analysisTask"
      :stage-label="analysisStageLabel"
      @close="closeAnalysisProgress"
    />
  </div>
</template>

<script setup name="Chapter">
import { listChapter, getChapter, listChapterPoints, addChapter, updateChapter, delChapter } from '@/api/project/chapter'
import { listSubject } from '@/api/project/subject'
import { listPoint } from '@/api/project/point'
import { getVideoImportStatus, listCourseLessons, uploadCourseVideo, attachCourseVideo, delCourseLesson } from '@/api/project/course'
import ImportProgress from './components/ImportProgress.vue'

const { proxy } = getCurrentInstance()

const chapterList = ref([])
const total = ref(0)
/** 当前页展示的章节：接口一次性返回全部（课程学习页需要完整列表），这里做前端分页 */
const pagedChapters = computed(() => {
  const { pageNum, pageSize } = queryParams.value
  const start = Math.max(0, (pageNum - 1) * pageSize)
  return chapterList.value.slice(start, start + pageSize)
})
const subjectList = ref([])
const formPointList = ref([])
const chapterPoints = ref([])
const open = ref(false)
const loading = ref(true)
const pointsLoading = ref(false)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const pointsDialogVisible = ref(false)
const pointsDialogTitle = ref('')
const title = ref('')

const videoDialogVisible = ref(false)
const videoUrl = ref('')

// ====== 课时（章下多视频） ======
const lessonsVisible = ref(false)
const lessonsTitle = ref('')
const lessonsLoading = ref(false)
const lessonList = ref([])
const currentChapter = ref(null)
const uploading = ref(false)
/** 添加/修改章节弹窗内的视频解析上传状态 */
const formUploading = ref(false)
const analysisTask = ref(null)
let analysisTimer = null

const STAGE_LABEL = {
  queued: '排队中',
  media: '解析视频 / 提取音频',
  asr: '语音识别生成逐字稿',
  llm: 'AI 提取章节、知识点与随堂练习',
  persist: '写入章节、知识点、逐字稿与题库',
  kb: '重建知识库',
  done: '解析完成',
  error: '解析失败'
}

const analysisStageLabel = computed(() => {
  const stage = analysisTask.value?.stage
  return STAGE_LABEL[stage] || stage || '处理中'
})

function formatDuration(seconds) {
  const value = Number(seconds) || 0
  if (value <= 0) return '-'
  const m = Math.floor(value / 60)
  const s = value % 60
  return m > 0 ? `${m}分${String(s).padStart(2, '0')}秒` : `${s}秒`
}

/** 打开章节的课时抽屉 */
function handleViewLessons(row) {
  currentChapter.value = row
  lessonsTitle.value = `${row.chapterNo || ''} ${row.title || ''} · 课时`
  lessonsVisible.value = true
  loadLessons(row.id)
}

/** 添加/修改章节弹窗内「上传视频」：保存并挂到课时，不做 AI 解析 */
function handleFormUploadVideo(options) {
  const subjectId = form.value.subjectId
  const chapterNo = (form.value.chapterNo || '').trim()
  const chapterTitle = (form.value.title || form.value.courseTitle || '').trim()
  if (!subjectId) {
    proxy.$modal.msgWarning('请先选择所属学科')
    return
  }
  if (!chapterNo) {
    proxy.$modal.msgWarning('请先填写章号（如 第1章）')
    return
  }
  formUploading.value = true
  const fileName = options.file?.name || ''
  attachCourseVideo(options.file, {
    subjectId,
    chapterNo,
    chapterTitle: chapterTitle || undefined,
    lessonTitle: fileName.replace(/\.[^.]+$/, '') || undefined
  }).then(res => {
    formUploading.value = false
    if (!res?.data?.lessonId) {
      proxy.$modal.msgError('上传失败：未返回课时')
      return
    }
    open.value = false
    proxy.$modal.msgSuccess('视频上传成功，可直接播放')
    // 章节由后端按章号复用/新建，刷新列表后打开该章课时抽屉
    queryParams.value.chapterNo = null
    queryParams.value.title = null
    getList().then(() => {
      const row = chapterList.value.find(item => String(item.chapterNo) === chapterNo)
      if (row) {
        handleViewLessons(row)
      }
    })
  }).catch(() => {
    formUploading.value = false
    proxy.$modal.msgError('上传失败，请重试')
  })
}

/** 课时按编号自然序排序（补录的课时 lesson_no 可能排到末尾，这里以 lessonCode 为准） */
function sortLessons(rows) {
  const key = item => String(item.lessonCode || '')
    .split('.')
    .map(part => String(Number(part) || 0).padStart(4, '0'))
    .join('.')
  return rows.slice().sort((a, b) => key(a).localeCompare(key(b)))
}

function loadLessons(chapterId) {
  lessonsLoading.value = true
  return listCourseLessons(chapterId).then(res => {
    lessonList.value = sortLessons(res.data || res.rows || [])
    lessonsLoading.value = false
  }).catch(() => {
    lessonList.value = []
    lessonsLoading.value = false
  })
}

/** 删除课时：连同逐字稿删除（磁盘视频文件保留） */
function handleDeleteLesson(row) {
  const name = `${row.lessonCode || ''} ${row.title || ''}`.trim()
  proxy.$modal
    .confirm(`确定删除课时「${name}」吗？该课时的逐字稿将一并删除，磁盘上的视频文件保留。`)
    .then(() => delCourseLesson(row.id))
    .then(() => {
      proxy.$modal.msgSuccess('删除成功')
      if (currentChapter.value) {
        loadLessons(currentChapter.value.id)
      }
      getList()
    })
    .catch(() => {})
}

/** 上传视频表单：先填章/课时信息再提交（可选择是否解析） */
const uploadDialogVisible = ref(false)
const uploadForm = reactive({
  file: null,
  chapterNo: '',
  chapterTitle: '',
  lessonCode: '',
  lessonTitle: '',
  parseVideo: false
})

function openUploadDialog() {
  const chapter = currentChapter.value
  if (!chapter) return
  uploadForm.file = null
  uploadForm.chapterNo = chapter.chapterNo || ''
  uploadForm.chapterTitle = chapter.title || ''
  uploadForm.lessonCode = ''
  uploadForm.lessonTitle = ''
  uploadForm.parseVideo = false
  uploadDialogVisible.value = true
}

function onUploadFileChange(file) {
  uploadForm.file = file && file.raw ? file.raw : null
  if (uploadForm.file && !uploadForm.lessonTitle) {
    uploadForm.lessonTitle = (uploadForm.file.name || '').replace(/\.[^.]+$/, '')
  }
}

function submitUploadDialog() {
  const chapter = currentChapter.value
  if (!chapter) return
  if (!uploadForm.file) {
    proxy.$modal.msgWarning('请先选择视频文件')
    return
  }
  if (!String(uploadForm.chapterNo || '').trim()) {
    proxy.$modal.msgWarning('请填写章号（如：第1章）')
    return
  }
  uploading.value = true
  const payload = {
    subjectId: chapter.subjectId,
    chapterNo: String(uploadForm.chapterNo).trim(),
    chapterTitle: (uploadForm.chapterTitle || '').trim(),
    lessonCode: (uploadForm.lessonCode || '').trim(),
    lessonTitle: (uploadForm.lessonTitle || uploadForm.file.name.replace(/\.[^.]+$/, '')).trim()
  }
  // 勾选「解析视频」→ 走完整解析链路；不勾选 → 只挂载视频，立即可播放
  if (uploadForm.parseVideo)
  {
    uploadCourseVideo(uploadForm.file, payload).then(res => {
      uploading.value = false
      const taskId = res?.data?.taskId
      if (!taskId)
      {
        proxy.$modal.msgError('上传失败：未返回任务号')
        return
      }
      uploadDialogVisible.value = false
      proxy.$modal.msgSuccess('上传成功，正在后台解析…')
      currentChapter.value = chapter
      loadLessons(chapter.id)
      getList()
      pollAnalysis(taskId)
    }).catch(() => {
      uploading.value = false
      proxy.$modal.msgError('上传失败，请重试')
    })
    return
  }
  attachCourseVideo(uploadForm.file, payload).then(res => {
    uploading.value = false
    if (!res?.data?.lessonId) {
      proxy.$modal.msgError('上传失败：未返回课时')
      return
    }
    uploadDialogVisible.value = false
    proxy.$modal.msgSuccess('上传成功，视频可直接播放')
    loadLessons(chapter.id)
    getList()
  }).catch(() => {
    uploading.value = false
    proxy.$modal.msgError('上传失败，请重试')
  })
}

/** 轮询解析进度 */
function pollAnalysis(taskId) {
  stopAnalysisPolling()
  const tick = () => {
    getVideoImportStatus(taskId).then(res => {
      const task = res?.data || {}
      analysisTask.value = task
      if (task.status === '2' || task.status === '3') {
        uploading.value = false
        stopAnalysisPolling()
        if (task.status === '2') {
          proxy.$modal.msgSuccess('解析完成')
          if (currentChapter.value) {
            loadLessons(currentChapter.value.id)
          }
          getList()
        } else {
          proxy.$modal.msgError(task.message || '解析失败')
        }
      }
    }).catch(() => {})
  }
  tick()
  analysisTimer = setInterval(tick, 8000)
}

function stopAnalysisPolling() {
  if (analysisTimer) {
    clearInterval(analysisTimer)
    analysisTimer = null
  }
  // 上传按钮恢复可用，避免任务结束或关闭悬浮窗后仍处于 loading 禁用状态
  uploading.value = false
}

/** 关闭独立的解析进度悬浮窗：停止轮询并清除任务展示 */
function closeAnalysisProgress() {
  stopAnalysisPolling()
  analysisTask.value = null
}

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    subjectId: null,
    chapterNo: null,
    title: null
  },
  rules: {
    subjectId: [
      { required: true, message: '所属学科不能为空', trigger: 'change' }
    ],
    chapterNo: [
      { required: true, message: '章号不能为空', trigger: 'blur' }
    ],
    title: [
      { required: true, message: '章节名称不能为空', trigger: 'blur' }
    ]
  }
})

const { queryParams, form, rules } = toRefs(data)

function difficultyLabel(d) {
  return { 1: '简单', 2: '较易', 3: '中等', 4: '较难', 5: '困难' }[d] || '未知'
}
function difficultyTag(d) {
  return { 1: 'success', 2: 'primary', 3: 'warning', 4: 'danger', 5: 'danger' }[d] || 'info'
}

/** 查询章节列表 */
function getList() {
  loading.value = true
  return listChapter(queryParams.value).then(response => {
    // 章节接口返回 AjaxResult（数据在 data 里），不是分页结构（rows）
    const rows = response.data || response.rows || []
    const subjectMap = new Map(subjectList.value.map(s => [s.id, s.name]))
    rows.forEach(row => {
      row.subjectName = subjectMap.get(row.subjectId) || '未知'
    })
    chapterList.value = rows
    total.value = Number(response.total || rows.length)
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 查询学科列表 */
function getSubjectList() {
  return listSubject({ pageSize: 9999, pageNum: 1 }).then(res => {
    subjectList.value = res.rows || res.data || []
  })
}

/** 查询某学科下的知识点（用于章节挂载选择） */
function loadSubjectPoints(subjectId) {
  if (!subjectId) {
    formPointList.value = []
    return Promise.resolve()
  }
  return listPoint({ subjectId, pageSize: 9999, pageNum: 1 }).then(res => {
    formPointList.value = res.rows || res.data || []
  }).catch(() => {
    formPointList.value = []
  })
}

function onFormSubjectChange(subjectId) {
  form.value.pointIds = []
  loadSubjectPoints(subjectId)
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    id: null,
    subjectId: queryParams.value.subjectId || null,
    chapterNo: null,
    title: null,
    courseTitle: null,
    description: null,
    video: null,
    startTime: null,
    endTime: null,
    sortOrder: 0,
    status: '0',
    pointIds: []
  }
  formPointList.value = []
  if (form.value.subjectId) {
    loadSubjectPoints(form.value.subjectId)
  }
  proxy.resetForm('chapterRef')
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm('queryRef')
  queryParams.value.subjectId = null
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = '添加课程章节'
}

function handleUpdate(row) {
  reset()
  const _id = row.id || ids.value[0]
  getChapter(_id).then(response => {
    const data = response.data || {}
    form.value = {
      id: data.id,
      subjectId: data.subjectId,
      chapterNo: data.chapterNo,
      title: data.title,
      courseTitle: data.courseTitle,
      description: data.description,
      video: data.video,
      startTime: data.startTime,
      endTime: data.endTime,
      sortOrder: data.sortOrder ?? 0,
      status: data.status || '0',
      pointIds: []
    }
    open.value = true
    title.value = '修改课程章节'
    return loadSubjectPoints(data.subjectId)
  }).then(() => {
    return listChapterPoints(_id)
  }).then(res => {
    const rows = res.data || res.rows || []
    form.value.pointIds = rows.map(r => r.pointId)
  }).catch(() => {})
}

function submitForm() {
  proxy.$refs.chapterRef.validate(valid => {
    if (!valid) return
    if (form.value.id != null) {
      updateChapter(form.value).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        open.value = false
        getList()
      })
    } else {
      addChapter(form.value).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        open.value = false
        getList()
      })
    }
  })
}

function handleDelete(row) {
  const _ids = row.id ? [row.id] : ids.value
  proxy.$modal.confirm('是否确认删除选中的课程章节？删除后章节与知识点的关联关系将一并移除。').then(() => {
    return delChapter(_ids.join(','))
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

function handleViewPoints(row) {
  pointsDialogTitle.value = `${row.chapterNo || ''} ${row.title || ''} · 知识点`
  pointsDialogVisible.value = true
  pointsLoading.value = true
  chapterPoints.value = []
  listChapterPoints(row.id).then(res => {
    chapterPoints.value = res.data || res.rows || []
    pointsLoading.value = false
  }).catch(() => {
    pointsLoading.value = false
  })
}

function previewVideo(path) {
  videoUrl.value = import.meta.env.VITE_APP_BASE_API + path
  videoDialogVisible.value = true
}

getSubjectList().then(() => {
  getList()
})
</script>

<style scoped>
.text-secondary {
  color: #909399;
  font-size: 13px;
}
.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}

/* 添加/修改章节弹窗：章节视频区域 */
.chapter-video-cell {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 上传视频 */
.upload-file-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.upload-file-name {
  color: #606266;
  font-size: 13px;
  word-break: break-all;
}

.app-container {
  background-color: #f7f8fa;
  padding: 20px;
  min-height: 100vh;
}
.el-form--inline {
  background-color: #ffffff;
  padding: 20px 20px 0 20px;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  margin-bottom: 16px;
}
.el-table {
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}
.el-table th.el-table__cell {
  background-color: #fafbfc !important;
}
.pagination-container {
  background-color: #ffffff;
  padding: 10px 16px;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  margin-top: -1px;
}
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
.mb8 {
  background: transparent;
}
.el-input__wrapper {
  box-shadow: 0 0 0 1px #e7e7e7 inset !important;
}
.el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #d2d3d4 inset !important;
}
</style>