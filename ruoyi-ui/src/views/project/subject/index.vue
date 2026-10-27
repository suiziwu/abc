<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch" label-width="68px">
      <el-form-item label="学科名称" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入学科名称"
          clearable
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="显示排序" prop="sortOrder">
        <el-input
          v-model="queryParams.sortOrder"
          placeholder="请输入显示排序"
          clearable
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
          v-hasPermi="['project:subject:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          :disabled="single"
          @click="handleUpdate"
          v-hasPermi="['project:subject:edit']"
        >修改</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['project:subject:remove']"
        >删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Upload"
          @click="handleImport"
          v-hasPermi="['project:subject:add']"
        >导入</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          @click="handleExport"
          v-hasPermi="['project:subject:export']"
        >导出</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <el-table v-loading="loading" :data="subjectList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="学科名称" align="center" prop="name" />
      <el-table-column label="学科描述" align="center" prop="description" />
      <el-table-column label="图标" align="center" prop="icon" width="100">
        <template #default="scope">
          <image-preview :src="scope.row.icon" :width="50" :height="50"/>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" class-name="small-padding fixed-width">
        <template #default="scope">
          <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['project:subject:edit']">修改</el-button>
          <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['project:subject:remove']">删除</el-button>
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

    <!-- 添加或修改学科对话框 -->
    <el-dialog :title="title" v-model="open" width="500px" append-to-body>
      <el-form ref="subjectRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="学科名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入学科名称" />
        </el-form-item>
        <el-form-item label="学科描述" prop="description">
          <el-input v-model="form.description" type="textarea" placeholder="请输入内容" />
        </el-form-item>
        <el-form-item label="图标" prop="icon">
          <image-upload v-model="form.icon"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 学科导入对话框 -->
    <el-dialog :title="upload.title" v-model="upload.open" width="420px" append-to-body>
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
              <el-checkbox v-model="upload.updateSupport" />是否更新已经存在的学科数据
            </div>
            <span>仅允许导入 xls、xlsx 格式文件。</span>
            <el-link type="primary" :underline="false" style="font-size: 12px; vertical-align: baseline" @click="importTemplate">下载模板</el-link>
            <div class="el-upload__tip">模板列：学科名称（必填）、学科编码、学科描述、图标、显示排序</div>
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

<script setup name="Subject">
import { listSubject, getSubject, delSubject, addSubject, updateSubject } from "@/api/project/subject"
import { getToken } from "@/utils/auth"

const { proxy } = getCurrentInstance()

const subjectList = ref([])
const open = ref(false)
const loading = ref(true)
const showSearch = ref(true)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

/** 学科导入参数 */
const upload = reactive({
  // 是否显示弹出层
  open: false,
  // 弹出层标题
  title: "",
  // 是否禁用上传
  isUploading: false,
  // 是否更新已经存在的学科数据
  updateSupport: 0,
  // 设置上传的请求头部
  headers: { Authorization: "Bearer " + getToken() },
  // 上传的地址
  url: import.meta.env.VITE_APP_BASE_API + "/project/subject/importData"
})

const data = reactive({
  form: {},
  queryParams: {
    pageNum: 1,
    pageSize: 10,
    name: null,
    description: null,
    icon: null,
    sortOrder: null,
  },
  rules: {
    name: [
      { required: true, message: "学科名称不能为空", trigger: "blur" }
    ],
  }
})

const { queryParams, form, rules } = toRefs(data)

/** 查询学科列表 */
function getList() {
  loading.value = true
  listSubject(queryParams.value).then(response => {
    subjectList.value = response.rows
    total.value = response.total
    loading.value = false
  })
}

// 取消按钮
function cancel() {
  open.value = false
  reset()
}

// 表单重置
function reset() {
  form.value = {
    id: null,
    name: null,
    code: null,
    description: null,
    icon: null,
    sortOrder: null,
    status: null,
    createBy: null,
    createTime: null,
    updateBy: null,
    updateTime: null,
    delFlag: null
  }
  proxy.resetForm("subjectRef")
}

/** 搜索按钮操作 */
function handleQuery() {
  queryParams.value.pageNum = 1
  getList()
}

/** 重置按钮操作 */
function resetQuery() {
  proxy.resetForm("queryRef")
  handleQuery()
}

// 多选框选中数据
function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 新增按钮操作 */
function handleAdd() {
  reset()
  open.value = true
  title.value = "添加学科"
}

/** 修改按钮操作 */
function handleUpdate(row) {
  reset()
  const _id = row.id || ids.value
  getSubject(_id).then(response => {
    form.value = response.data
    open.value = true
    title.value = "修改学科"
  })
}

/** 提交按钮 */
function submitForm() {
  proxy.$refs["subjectRef"].validate(valid => {
    if (valid) {
      if (form.value.id != null) {
        updateSubject(form.value).then(response => {
          proxy.$modal.msgSuccess("修改成功")
          open.value = false
          getList()
        })
      } else {
        addSubject(form.value).then(response => {
          proxy.$modal.msgSuccess("新增成功")
          open.value = false
          getList()
        })
      }
    }
  })
}

/** 删除按钮操作 */
function handleDelete(row) {
  const _ids = row.id || ids.value
  proxy.$modal.confirm('是否确认删除学科编号为"' + _ids + '"的数据项？').then(function() {
    return delSubject(_ids)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

/** 导入按钮操作 */
function handleImport() {
  upload.title = "学科导入"
  upload.open = true
}

/** 下载模板操作 */
function importTemplate() {
  proxy.download("project/subject/importTemplate", {}, `subject_template_${new Date().getTime()}.xlsx`)
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
  proxy.$alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + (response.msg || "导入完成") + "</div>", "导入结果", { dangerouslyUseHTMLString: true })
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

/** 导出按钮操作 */
function handleExport() {
  proxy.download('project/subject/export', {
    ...queryParams.value
  }, `subject_${new Date().getTime()}.xlsx`)
}

getList()
</script>
<style scoped>
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