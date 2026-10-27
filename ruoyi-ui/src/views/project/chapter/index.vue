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
          v-hasPermi="['project:point:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['project:point:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['project:point:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="chapterList" @selection-change="handleSelectionChange">
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
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="160" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="View" @click="handleViewPoints(scope.row)">知识点</el-button>
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['project:point:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['project:point:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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
              <file-upload v-model="form.video" :limit="1" :fileSize="1024" :fileType="['mp4', 'webm', 'ogg', 'mov', 'avi', 'flv']" />
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
  </div>
</template>

<script setup name="Chapter">
import { listChapter, getChapter, listChapterPoints, addChapter, updateChapter, delChapter } from '@/api/project/chapter'
import { listSubject } from '@/api/project/subject'
import { listPoint } from '@/api/project/point'

const { proxy } = getCurrentInstance()

const chapterList = ref([])
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
  listChapter(queryParams.value).then(response => {
    const rows = response.data || response.rows || []
    const subjectMap = new Map(subjectList.value.map(s => [s.id, s.name]))
    rows.forEach(row => {
      row.subjectName = subjectMap.get(row.subjectId) || '未知'
    })
    chapterList.value = rows
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

getSubjectList().then(() => getList())
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