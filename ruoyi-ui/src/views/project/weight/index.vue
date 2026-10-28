<template>
  <div class="app-container">
    <el-form ref="queryRef" :model="queryParams" :inline="true" v-show="showSearch" label-width="86px">
      <el-form-item label="职业名称" prop="jobName">
        <el-input
          v-model="queryParams.jobName"
          placeholder="请输入职业名称"
          clearable
          style="width: 190px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="职业编码" prop="jobCode">
        <el-input
          v-model="queryParams.jobCode"
          placeholder="如 4-04-05-01"
          clearable
          style="width: 160px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="技能等级" prop="level">
        <el-select v-model="queryParams.level" placeholder="全部" clearable style="width: 120px">
          <el-option v-for="item in levelOptions" :key="item" :label="item" :value="item" />
        </el-select>
      </el-form-item>
      <el-form-item label="能力域" prop="domainName">
        <el-input
          v-model="queryParams.domainName"
          placeholder="能力域关键字"
          clearable
          style="width: 170px"
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
          v-hasPermi="['project:weight:add']"
        >新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          :disabled="multiple"
          @click="handleDelete"
          v-hasPermi="['project:weight:remove']"
        >删除</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
    </el-row>

    <el-table v-loading="loading" :data="weightList" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="职业名称" prop="jobName" min-width="175" show-overflow-tooltip />
      <el-table-column label="职业编码" prop="jobCode" width="110" />
      <el-table-column label="技能等级" prop="level" width="80" align="center" />
      <el-table-column label="能力域" prop="domainName" min-width="190" show-overflow-tooltip />
      <el-table-column label="权重" width="72" align="center">
        <template #default="scope">
          <span :class="{ 'weight-empty': scope.row.weight === null }">
            {{ scope.row.weight === null ? '—' : scope.row.weight + '%' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="来源文件" prop="sourceFile" min-width="170" show-overflow-tooltip />
      <el-table-column label="页码" prop="sourcePage" width="70" align="center" />
      <el-table-column label="颁布文号" prop="issueNumber" width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="120" align="center" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="handleUpdate(scope.row)"
            v-hasPermi="['project:weight:edit']"
          >修改</el-button>
          <el-button
            link
            type="danger"
            @click="handleDelete(scope.row)"
            v-hasPermi="['project:weight:remove']"
          >删除</el-button>
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

    <el-dialog :title="title" v-model="open" width="580px" append-to-body>
      <el-form ref="weightRef" :model="form" :rules="rules" label-width="92px">
        <el-form-item label="职业名称" prop="jobName">
          <el-input v-model="form.jobName" placeholder="请输入职业名称" />
        </el-form-item>
        <el-form-item label="职业编码" prop="jobCode">
          <el-input v-model="form.jobCode" placeholder="如 4-04-05-01" />
        </el-form-item>
        <el-form-item label="技能等级" prop="level">
          <el-select v-model="form.level" placeholder="请选择等级" style="width: 100%">
            <el-option v-for="item in levelOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="能力域" prop="domainName">
          <el-input v-model="form.domainName" placeholder="职业功能 / 能力域名称" />
        </el-form-item>
        <el-form-item label="权重(%)" prop="weight">
          <el-input-number v-model="form.weight" :min="0" :max="100" :step="5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="来源文件" prop="sourceFile">
          <el-input v-model="form.sourceFile" placeholder="标准 PDF 文件名" />
        </el-form-item>
        <el-form-item label="页码" prop="sourcePage">
          <el-input v-model="form.sourcePage" />
        </el-form-item>
        <el-form-item label="颁布文号" prop="issueNumber">
          <el-input v-model="form.issueNumber" />
        </el-form-item>
        <el-form-item label="数据来源" prop="source">
          <el-input v-model="form.source" placeholder="national_standard / manual" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="cancel">取 消</el-button>
          <el-button type="primary" @click="submitForm">确 定</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { listWeight, getWeight, addWeight, updateWeight, delWeight } from '@/api/project/weight'

export default {
  name: 'JobCompetencyWeight',
  data() {
    return {
      loading: true,
      showSearch: true,
      ids: [],
      single: true,
      multiple: true,
      weightList: [],
      total: 0,
      title: '',
      open: false,
      levelOptions: ['五级', '四级', '三级', '二级', '一级'],
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        jobName: undefined,
        jobCode: undefined,
        level: undefined,
        domainName: undefined
      },
      form: {},
      rules: {
        jobName: [{ required: true, message: '职业名称不能为空', trigger: 'blur' }],
        jobCode: [{ required: true, message: '职业编码不能为空', trigger: 'blur' }],
        level: [{ required: true, message: '技能等级不能为空', trigger: 'change' }],
        domainName: [{ required: true, message: '能力域不能为空', trigger: 'blur' }]
      }
    }
  },
  created() {
    this.getList()
  },
  methods: {
    getList() {
      this.loading = true
      listWeight(this.queryParams)
        .then(res => {
          this.weightList = res.rows || []
          this.total = res.total || 0
        })
        .finally(() => {
          this.loading = false
        })
    },
    handleQuery() {
      this.queryParams.pageNum = 1
      this.getList()
    },
    resetQuery() {
      this.resetForm('queryRef')
      this.handleQuery()
    },
    handleSelectionChange(selection) {
      this.ids = selection.map(item => item.id)
      this.single = selection.length !== 1
      this.multiple = !selection.length
    },
    handleAdd() {
      this.reset()
      this.open = true
      this.title = '新增权重'
    },
    handleUpdate(row) {
      this.reset()
      const id = row.id || this.ids[0]
      getWeight(id).then(res => {
        this.form = res.data || {}
        this.open = true
        this.title = '修改权重'
      })
    },
    submitForm() {
      this.$refs.weightRef.validate(valid => {
        if (!valid) return
        const request = this.form.id ? updateWeight(this.form) : addWeight(this.form)
        request.then(() => {
          this.$modal.msgSuccess(this.form.id ? '修改成功' : '新增成功')
          this.open = false
          this.getList()
        })
      })
    },
    handleDelete(row) {
      const ids = row.id ? [row.id] : this.ids
      this.$modal.confirm('确认删除选中的 ' + ids.length + ' 条权重记录？').then(() => {
        return delWeight(ids.join(','))
      }).then(() => {
        this.$modal.msgSuccess('删除成功')
        this.getList()
      }).catch(() => {})
    },
    cancel() {
      this.open = false
      this.reset()
    },
    reset() {
      this.form = {
        id: undefined,
        jobName: '',
        jobCode: '',
        level: '三级',
        domainName: '',
        weight: 0,
        source: 'manual',
        sourceFile: '',
        sourcePage: '',
        issueNumber: ''
      }
      if (this.$refs.weightRef) {
        this.$refs.weightRef.resetFields()
      }
    }
  }
}
</script>

<style scoped>
.weight-empty {
  color: #909399;
}
</style>
