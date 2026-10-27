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
            @change="onSubjectChange"
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
          <span class="text-secondary" v-if="!subjectId">请先选择学科，然后管理该学科下知识点之间的关联关系</span>
          <span class="text-secondary" v-else>
            当前学科：<strong>{{ selectedSubject?.name }}</strong> ｜
            知识点 {{ pointList.length }} 个 ｜ 关联 {{ relationList.length }} 条
          </span>
        </el-col>
      </el-row>
    </el-card>

    <!-- 关联列表 -->
    <el-card shadow="never" v-if="subjectId">
      <template #header>
        <div class="card-header">
          <span>关联关系列表</span>
          <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['project:relation:add']">
            新增关联
          </el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="relationList" empty-text="暂无关联关系">
        <el-table-column label="源知识点" align="center" prop="sourceName" min-width="150" show-overflow-tooltip />
        <el-table-column label="→" align="center" width="40" />
        <el-table-column label="目标知识点" align="center" prop="targetName" min-width="150" show-overflow-tooltip />
        <el-table-column label="关联类型" align="center" width="100">
          <template #default="scope">
            <el-tag :type="relationTagType(scope.row.relationType)" size="small">
              {{ relationLabel(scope.row.relationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="描述" align="center" prop="description" min-width="180" show-overflow-tooltip />
        <el-table-column label="权重" align="center" prop="weight" width="70" />
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="140" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['project:relation:edit']">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['project:relation:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getRelationList"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog :title="title" v-model="open" width="550px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="源知识点" prop="sourceId">
          <el-select
            v-model="form.sourceId"
            placeholder="请选择源知识点"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in pointList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
              :disabled="item.id === form.targetId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="目标知识点" prop="targetId">
          <el-select
            v-model="form.targetId"
            placeholder="请选择目标知识点"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="item in pointList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
              :disabled="item.id === form.sourceId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联类型" prop="relationType">
          <el-select v-model="form.relationType" placeholder="请选择关联类型" style="width: 100%">
            <el-option label="前置依赖" value="prerequisite" />
            <el-option label="相关参考" value="related" />
            <el-option label="扩展延伸" value="extension" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="描述该关联关系的含义，如：必须先掌握XX才能学习XX" />
        </el-form-item>
        <el-form-item label="关联权重" prop="weight">
          <el-slider
            v-model="form.weight"
            :min="1"
            :max="5"
            :marks="{ 1: '弱', 3: '中', 5: '强' }"
            show-stops
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="Link">
import { listSubject } from '@/api/project/subject'
import { listPoint } from '@/api/project/point'
import { listRelation, getRelation, addRelation, updateRelation, delRelation } from '@/api/project/relation'

const { proxy } = getCurrentInstance()

// ---------- 响应式状态 ----------
const subjectList = ref([])
const subjectId = ref(undefined)
const pointList = ref([])
const pointMap = ref(new Map())
const relationList = ref([])
const loading = ref(false)
const open = ref(false)
const title = ref('')
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  sourceId: null,
  targetId: null,
  relationType: null
})

const form = reactive({
  id: null,
  sourceId: null,
  targetId: null,
  relationType: 'prerequisite',
  description: '',
  weight: 3
})

const rules = {
  sourceId: [
    { required: true, message: '请选择源知识点', trigger: 'change' }
  ],
  targetId: [
    { required: true, message: '请选择目标知识点', trigger: 'change' }
  ],
  relationType: [
    { required: true, message: '请选择关联类型', trigger: 'change' }
  ]
}

// ---------- 计算属性 ----------
const selectedSubject = computed(() => {
  return subjectList.value.find(s => s.id === subjectId.value)
})

// ---------- 生命周期 ----------
onMounted(() => {
  loadSubjects()
})

// ---------- 数据加载 ----------
function loadSubjects() {
  listSubject({ pageSize: 9999, pageNum: 1 }).then(res => {
    subjectList.value = res.rows || res.data || []
  })
}

function onSubjectChange() {
  resetForm()
  if (!subjectId.value) {
    pointList.value = []
    relationList.value = []
    total.value = 0
    return
  }
  loadPoints().then(() => {
    getRelationList()
  })
}

function loadPoints() {
  return listPoint({ subjectId: subjectId.value, pageSize: 9999 }).then(res => {
    const rows = res.rows || res.data || []
    pointList.value = rows
    const map = new Map()
    rows.forEach(p => map.set(p.id, p.name))
    pointMap.value = map
  })
}

function getRelationList() {
  loading.value = true
  listRelation({ pageSize: 9999 }).then(res => {
    const all = res.rows || res.data || []
    // 筛选当前学科下的关联
    const pointIds = new Set(pointList.value.map(p => p.id))
    const filtered = all.filter(r => pointIds.has(r.sourceId) && pointIds.has(r.targetId))

    // 映射知识点名称
    filtered.forEach(r => {
      r.sourceName = pointMap.value.get(r.sourceId) || '未知'
      r.targetName = pointMap.value.get(r.targetId) || '未知'
    })

    relationList.value = filtered
    total.value = filtered.length
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

// ---------- 表单操作 ----------
function resetForm() {
  form.id = null
  form.sourceId = null
  form.targetId = null
  form.relationType = 'prerequisite'
  form.description = ''
  form.weight = 3
  proxy?.resetForm?.('formRef')
}

function handleAdd() {
  resetForm()
  open.value = true
  title.value = '新增关联关系'
}

function handleUpdate(row) {
  resetForm()
  getRelation(row.id).then(res => {
    const data = res.data
    form.id = data.id
    form.sourceId = data.sourceId
    form.targetId = data.targetId
    form.relationType = data.relationType || 'prerequisite'
    form.description = data.description || ''
    form.weight = data.weight ?? 3
    open.value = true
    title.value = '修改关联关系'
  })
}

function submitForm() {
  proxy.$refs.formRef.validate(valid => {
    if (!valid) return
    if (form.id != null) {
      updateRelation(form).then(() => {
        proxy.$modal.msgSuccess('修改成功')
        open.value = false
        getRelationList()
      })
    } else {
      addRelation(form).then(() => {
        proxy.$modal.msgSuccess('新增成功')
        open.value = false
        getRelationList()
      })
    }
  })
}

function cancel() {
  open.value = false
  resetForm()
}

function handleDelete(row) {
  proxy.$modal.confirm(`是否确认删除「${row.sourceName} → ${row.targetName}」的关联关系？`).then(() => {
    return delRelation(row.id)
  }).then(() => {
    getRelationList()
    proxy.$modal.msgSuccess('删除成功')
  }).catch(() => {})
}

// ---------- 工具函数 ----------
function relationLabel(type) {
  const map = { prerequisite: '前置依赖', related: '相关参考', extension: '扩展延伸' }
  return map[type] || type
}

function relationTagType(type) {
  const map = { prerequisite: 'primary', related: 'success', extension: 'warning' }
  return map[type] || 'info'
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
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
