<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="80px">
      <el-form-item label="所属学科" prop="subjectId">
        <el-select
          v-model="queryParams.subjectId"
          placeholder="请选择所属学科"
          clearable
          filterable
          style="width: 180px"
        >
          <el-option
            v-for="item in subjectList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入名称"
          clearable
          @keyup.enter="handleQuery"
          style="width: 180px"
        />
      </el-form-item>
      <el-form-item label="难度等级" prop="difficulty">
        <el-select
          v-model="queryParams.difficulty"
          placeholder="请选择难度等级"
          clearable
          style="width: 120px"
        >
          <el-option label="简单" :value="1" />
          <el-option label="较易" :value="2" />
          <el-option label="中等" :value="3" />
          <el-option label="较难" :value="4" />
          <el-option label="困难" :value="5" />
        </el-select>
      </el-form-item>
      <el-form-item label="重要程度" prop="importance">
        <el-select
          v-model="queryParams.importance"
          placeholder="请选择重要程度"
          clearable
          style="width: 120px"
        >
          <el-option label="了解" :value="1" />
          <el-option label="掌握" :value="2" />
          <el-option label="重点" :value="3" />
          <el-option label="核心" :value="4" />
        </el-select>
      </el-form-item>
      <el-form-item label="知识类型" prop="type">
        <el-select
          v-model="queryParams.type"
          placeholder="请选择知识类型"
          clearable
          style="width: 120px"
        >
          <el-option label="概念" value="概念" />
          <el-option label="语法" value="语法" />
          <el-option label="方法" value="方法" />
          <el-option label="工具" value="工具" />
          <el-option label="框架" value="框架" />
          <el-option label="管理" value="管理" />
        </el-select>
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
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Upload"
          @click="handleImport"
          v-hasPermi="['project:point:add']"
        >导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['project:point:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="pointList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="名称" align="center" prop="name" min-width="140" show-overflow-tooltip />
      <el-table-column label="所属学科" align="center" prop="subjectName" min-width="100" />
      <el-table-column label="知识内容" align="center" prop="content" min-width="200" show-overflow-tooltip />
      <el-table-column label="难度等级" align="center" prop="difficulty" width="90">
        <template #default="scope">
          <el-tag :type="difficultyTag(scope.row.difficulty)" size="small">
            {{ difficultyLabel(scope.row.difficulty) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重要程度" align="center" prop="importance" width="90">
        <template #default="scope">
          <el-tag :type="importanceTag(scope.row.importance)" size="small">
            {{ importanceLabel(scope.row.importance) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="知识类型" align="center" prop="type" width="90">
        <template #default="scope">
          <el-tag type="info" size="small" v-if="scope.row.type">{{ scope.row.type }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="附件" align="center" prop="attachment" min-width="120">
        <template #default="scope">
          <el-link v-if="scope.row.attachment" type="primary" :underline="false" icon="Download" @click="downloadFile(scope.row.attachment)">
            下载附件
          </el-link>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>
      <el-table-column label="视频" align="center" prop="video" min-width="130">
        <template #default="scope">
          <el-link v-if="scope.row.video" type="primary" :underline="false" icon="VideoCamera" @click="previewVideo(scope.row.video)">
            预览视频
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
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="120" fixed="right">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['project:point:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['project:point:remove']">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <pagination
      v-show="total>0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />

    <!-- 添加或修改知识点对话框 -->
    <el-dialog :title="title" v-model="open" width="650px" append-to-body>
      <el-form ref="pointRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="所属学科" prop="subjectId">
              <el-select
                v-model="form.subjectId"
                placeholder="请选择所属学科"
                filterable
                style="width: 100%"
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
            <el-form-item label="名称" prop="name">
              <el-input v-model="form.name" placeholder="请输入名称" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="难度等级" prop="difficulty">
              <el-select v-model="form.difficulty" placeholder="请选择" style="width: 100%">
                <el-option label="1-简单" :value="1" />
                <el-option label="2-较易" :value="2" />
                <el-option label="3-中等" :value="3" />
                <el-option label="4-较难" :value="4" />
                <el-option label="5-困难" :value="5" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="重要程度" prop="importance">
              <el-select v-model="form.importance" placeholder="请选择" style="width: 100%">
                <el-option label="1-了解" :value="1" />
                <el-option label="2-掌握" :value="2" />
                <el-option label="3-重点" :value="3" />
                <el-option label="4-核心" :value="4" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="知识类型" prop="type">
              <el-select v-model="form.type" placeholder="请选择" clearable style="width: 100%">
                <el-option label="概念" value="概念" />
                <el-option label="语法" value="语法" />
                <el-option label="方法" value="方法" />
                <el-option label="工具" value="工具" />
                <el-option label="框架" value="框架" />
                <el-option label="管理" value="管理" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="内容描述">
          <el-input v-model="form.content" type="textarea" :rows="4" placeholder="请输入知识点详细内容" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="附件" prop="attachment">
              <file-upload v-model="form.attachment" :limit="1" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="视频" prop="video">
              <file-upload v-model="form.video" :limit="1" :fileSize="1024" :fileType="['mp4', 'webm', 'ogg', 'mov', 'avi', 'flv']" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio>
            <el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 视频预览弹窗 -->
    <el-dialog v-model="videoDialogVisible" title="视频预览" width="720px" destroy-on-close>
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

    <!-- 知识点导入对话框 -->
    <el-dialog :title="upload.title" v-model="upload.open" width="440px" append-to-body>
      <el-upload
        ref="uploadRef"
        :limit="1"
        accept=".xlsx, .xls"
        :headers="upload.headers"
        :action="upload.url + '?updateSupport=' + upload.updateSupport"
        :disabled="upload.isUploading"
        :on-progress="handleFileUploadProgress"
        :on-success="handleFileSuccess"
        :on-error="handleFileError"
        :auto-upload="false"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip text-center">
            <div class="el-upload__tip">
              <el-checkbox v-model="upload.updateSupport" />是否更新已经存在的知识点数据
            </div>
            <span>仅允许导入 xls、xlsx 格式文件。</span>
            <el-link type="primary" :underline="false" style="font-size: 12px; vertical-align: baseline" @click="importTemplate">下载模板</el-link>
            <div class="el-upload__tip upload-rules">
              所属学科填学科名称；难度等级填 简单/较易/中等/较难/困难；重要程度填 了解/掌握/重点/核心；状态填 正常/停用
            </div>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitFileForm">确 定</el-button>
          <el-button @click="upload.open = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Point">
import { listPoint, getPoint, delPoint, addPoint, updatePoint } from "@/api/project/point"
import { listSubject } from "@/api/project/subject"
import { getToken } from "@/utils/auth"

const { proxy } = getCurrentInstance()

const pointList = ref([])
const subjectList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

/** 知识点导入参数 */
const upload = reactive({
  // 是否显示弹出层
  open: false,
  // 弹出层标题
  title: "",
  // 是否禁用上传
  isUploading: false,
  // 是否更新已经存在的知识点数据
  updateSupport: 0,
  // 设置上传的请求头部
  headers: { Authorization: "Bearer " + getToken() },
  // 上传的地址
  url: import.meta.env.VITE_APP_BASE_API + "/project/point/importData"
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    subjectId: null,
    name: null,
    difficulty: null,
    importance: null,
    type: null,
    sortOrder: null,
    status: null
  },
  rules: {
    subjectId: [
      { required: true, message: "所属学科不能为空", trigger: "change" }
    ],
    name: [
      { required: true, message: "名称不能为空", trigger: "blur" }
    ],
    difficulty: [
      { required: true, message: "请选择难度等级", trigger: "change" }
    ],
    importance: [
      { required: true, message: "请选择重要程度", trigger: "change" }
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
function importanceLabel(i) {
  return { 1: '了解', 2: '掌握', 3: '重点', 4: '核心' }[i] || '未知'
}
function importanceTag(i) {
  return { 1: 'info', 2: 'primary', 3: 'warning', 4: 'danger' }[i] || 'info'
}

/** 预览视频 */
const videoDialogVisible = ref(false)
const videoUrl = ref('')

function previewVideo(path) {
  videoUrl.value = import.meta.env.VITE_APP_BASE_API + path
  videoDialogVisible.value = true
}

/** 下载附件 */
function downloadFile(path) {
  proxy?.$download?.resource(path)
}

/** 查询知识点列表 */
function getList() {
  loading.value = true
  listPoint(queryParams.value).then(response => {
    const rows = response.rows || []
    // 将学科名称映射到每行数据
    const subjectMap = new Map(subjectList.value.map(s => [s.id, s.name]))
    rows.forEach(row => {
      row.subjectName = subjectMap.get(row.subjectId) || '未知'
    })
    pointList.value = rows
    total.value = response.total
  }).catch(() => {
    pointList.value = []
    total.value = 0
    proxy?.$modal?.msgError('加载知识点失败')
  }).finally(() => {
    // 无论成功失败都要收起 loading，否则接口异常时页面一直转圈
    loading.value = false
  })
}

/** 查询学科列表 */
function getSubjectList() {
  listSubject({ pageSize: 9999, pageNum: 1 }).then(res => {
    subjectList.value = res.rows || res.data || []
  })
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    id: null,
    subjectId: null,
    name: null,
    content: null,
    difficulty: 3,
    importance: 2,
    type: null,
    sortOrder: null,
    status: '0',
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    delFlag: null,
    attachment: null,
    video: null
  }
  proxy.resetForm("pointRef")
}

function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

function handleAdd() {
  reset()
  open.value = true
  title.value = "添加知识点"
}

function handleUpdate(row) {
  reset()
  const _id = row.id || ids.value
  getPoint(_id).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改知识点"
  })
}

function submitForm() {
  proxy.$refs["pointRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updatePoint(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addPoint(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

function handleDelete(row) {
  const _ids = row.id || ids.value
  proxy.$modal.confirm('是否确认删除知识点"' + (_ids) + '"的数据项？').then(function() {
    return delPoint(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导入按钮操作 */
function handleImport() {
  upload.title = "知识点导入"
  upload.open = true
}

/** 下载模板操作 */
function importTemplate() {
  proxy.download("project/point/importTemplate", {}, `point_template_${new Date().getTime()}.xlsx`)
}

/** 文件上传中处理 */
function handleFileUploadProgress() {
  upload.isUploading = true
}

/** 文件上传成功处理 */
function handleFileSuccess(response, file) {
  upload.open = false
  upload.isUploading = false
  proxy.$refs["uploadRef"].handleRemove(file)
  // response.msg 是后端返回的导入结果，拼进 HTML 前必须转义
  const safeMsg = String(response.msg || "导入完成")
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;")
  proxy.$alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + safeMsg + "</div>", "导入结果", { dangerouslyUseHTMLString: true })
  getSubjectList()
  getList()
}

/** 文件上传失败处理 */
function handleFileError() {
  upload.isUploading = false
  proxy.$modal.msgError("导入失败，请检查文件格式（仅支持 xls、xlsx）")
}

/** 提交上传文件 */
function submitFileForm() {
  proxy.$refs["uploadRef"].submit()
}

function handleExport() {
  proxy.download('project/point/export', {
    ...queryParams.value
  }, `point_${new Date().getTime()}.xlsx`)
}

// 加载学科列表，再加载数据
getSubjectList()
getList()
</script>

<style scoped>
.text-secondary {
  color: #909399;
  font-size: 13px;
}
.upload-rules {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
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
