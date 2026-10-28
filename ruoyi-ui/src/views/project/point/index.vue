<template>
  <div class="app-container">
    <!-- 学科选择器：与知识点关联保持一致的入口形式 -->
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
          <div class="subject-mode-row">
            <span class="text-secondary" v-if="!subjectId">请先选择学科，然后管理该学科下的知识点</span>
            <span class="text-secondary" v-else>
              当前学科：<strong>{{ selectedSubject?.name }}</strong>
              <template v-if="learnModeType === 'point'"> ｜ 知识点 <strong>{{ total }}</strong> 个</template>
            </span>
            <el-radio-group class="learn-mode-switch" v-model="learnModeType" @change="handleLearnWayChange">
              <el-radio-button label="point">知识点学习</el-radio-button>
              <el-radio-button label="video">视频学习</el-radio-button>
            </el-radio-group>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <!-- 知识点列表：选中学科后展示，形式与知识点关联一致 -->
    <el-card shadow="never" v-if="subjectId" class="point-card">
      <template #header>
        <div class="card-header">
          <div class="card-header-title">
            <span>{{ learnModeType === 'point' ? '知识点列表' : '课程章节' }}</span>
            <el-tag type="info" effect="plain" size="small">
              {{ learnModeType === 'point' ? total + ' 个' : chapterTotal + ' 章' }}
            </el-tag>
          </div>
          <div class="card-header-actions" v-if="learnModeType === 'point'">
            <el-button
              type="primary"
              plain
              icon="Plus"
              @click="handleAdd"
              v-hasPermi="['project:point:add']"
            >新增知识点</el-button>
            <el-dropdown trigger="click" @command="handleActionCommand">
              <el-button plain>
                更多
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="ai" icon="MagicStick">AI 按章生成</el-dropdown-item>
                  <el-dropdown-item command="import" icon="Upload" divided>导入知识点</el-dropdown-item>
                  <el-dropdown-item command="export" icon="Download">导出知识点</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </template>

      <!-- 紧凑筛选栏 -->
      <div class="list-toolbar" v-if="learnModeType === 'point'">
        <el-input
          v-model="queryParams.name"
          placeholder="搜索知识点名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
          @clear="handleQuery"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select
          v-model="queryParams.chapterId"
          placeholder="全部章"
          clearable
          filterable
          style="width: 170px"
          @change="handleQuery"
        >
          <el-option
            v-for="item in chapterList"
            :key="item.id"
            :label="item.name"
            :value="item.id"
          />
        </el-select>
        <el-select
          v-model="queryParams.difficulty"
          placeholder="难度等级"
          clearable
          style="width: 130px"
          @change="handleQuery"
        >
          <el-option label="简单" :value="1" />
          <el-option label="较易" :value="2" />
          <el-option label="中等" :value="3" />
          <el-option label="较难" :value="4" />
          <el-option label="困难" :value="5" />
        </el-select>
        <el-select
          v-model="queryParams.importance"
          placeholder="重要程度"
          clearable
          style="width: 130px"
          @change="handleQuery"
        >
          <el-option label="了解" :value="1" />
          <el-option label="掌握" :value="2" />
          <el-option label="重点" :value="3" />
          <el-option label="核心" :value="4" />
        </el-select>
        <el-select
          v-model="queryParams.type"
          placeholder="知识类型"
          clearable
          style="width: 130px"
          @change="handleQuery"
        >
          <el-option label="概念" value="概念" />
          <el-option label="语法" value="语法" />
          <el-option label="方法" value="方法" />
          <el-option label="工具" value="工具" />
          <el-option label="框架" value="框架" />
          <el-option label="管理" value="管理" />
          <el-option label="原理" value="原理" />
          <el-option label="实践" value="实践" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        <el-button class="refresh-btn" circle icon="Refresh" title="刷新列表" @click="getList" />
      </div>

      <el-table v-if="learnModeType === 'point'" v-loading="loading" :data="pointList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="名称" align="left" prop="name" min-width="160" show-overflow-tooltip />
        <el-table-column label="所属章" align="center" prop="chapterId" min-width="130" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.chapterId">{{ chapterNameMap.get(scope.row.chapterId) || '未分章' }}</span>
            <span v-else class="text-secondary">未分章</span>
          </template>
        </el-table-column>
        <el-table-column label="知识内容" align="left" prop="content" min-width="240" show-overflow-tooltip />
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
        <el-table-column label="附件" align="center" prop="attachment" min-width="110">
          <template #default="scope">
            <el-link v-if="scope.row.attachment" type="primary" :underline="false" icon="Download" @click="downloadFile(scope.row.attachment)">
              下载附件
            </el-link>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column label="视频" align="center" prop="video" min-width="120">
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
        <el-table-column label="操作" align="center" class-name="small-padding fixed-width" min-width="190" fixed="right">
          <template #default="scope">
            <el-button link type="success" icon="VideoPlay" @click="handleLearnPoint(scope.row)">学习</el-button>
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['project:point:edit']">修改</el-button>
            <el-button link type="primary" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['project:point:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="learnModeType === 'point' && total>0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />

      <!-- 视频学习：展示原课程章节管理页，与知识点列表完全独立 -->
      <ChapterManagePanel
        v-if="learnModeType === 'video'"
        ref="chapterPanelRef"
        :subject-id="subjectId"
        @changed="handleChapterChanged"
        @total-changed="chapterTotal = $event"
      />
    </el-card>

    <!-- 添加或修改知识点对话框 -->
    <el-dialog :title="title" v-model="open" width="650px" append-to-body>
      <el-form ref="pointRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="所属学科" prop="subjectId">
              <el-select
                v-model="form.subjectId"
                placeholder="请选择所属学科"
                filterable
                style="width: 100%"
                @change="handleFormSubjectChange"
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
          <el-col :span="8">
            <el-form-item label="所属章" prop="chapterId">
              <el-select
                v-model="form.chapterId"
                placeholder="未分章"
                clearable
                filterable
                style="width: 100%"
              >
                <el-option
                  v-for="item in formChapterList"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
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
                <el-option label="原理" value="原理" />
                <el-option label="实践" value="实践" />
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

    <!-- 新增岗位对话框 -->
    <el-dialog title="新增岗位" v-model="positionOpen" width="460px" append-to-body>
      <el-form ref="positionRef" :model="positionForm" :rules="positionRules" label-width="80px">
        <el-form-item label="岗位名称" prop="name">
          <el-input v-model="positionForm.name" placeholder="请输入岗位名称" />
        </el-form-item>
        <el-form-item label="岗位描述" prop="description">
          <el-input v-model="positionForm.description" type="textarea" :rows="3" placeholder="请输入岗位描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitPosition">确 定</el-button>
          <el-button @click="positionOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- AI 按章生成对话框 -->
    <el-dialog title="AI 按章生成" v-model="aiOpen" width="500px" append-to-body>
      <el-form ref="aiRef" :model="aiForm" :rules="aiRules" label-width="90px">
        <el-form-item label="学科">
          <span class="text-secondary">{{ selectedSubject?.name || '-' }}</span>
        </el-form-item>
        <el-form-item label="岗位" prop="positionId">
          <el-select v-model="aiForm.positionId" placeholder="请选择岗位" filterable style="width: 100%">
            <el-option
              v-for="item in positionList"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="章节数" prop="chapterCount">
          <el-input-number v-model="aiForm.chapterCount" :min="1" :max="10" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-alert
          v-if="!positionLoading && positionList.length === 0"
          type="warning"
          :closable="false"
          class="position-inline-tip"
        >
          <template #title>
            暂无岗位，请先
            <el-button type="primary" link @click="handleAddPosition">新增岗位</el-button>
            后再生成。
          </template>
        </el-alert>
        <div class="ai-tip">AI 将按章节自动生成知识点并直接落库，生成耗时较长，请耐心等待。</div>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="aiLoading" @click="submitAiGenerate">开始生成</el-button>
          <el-button :disabled="aiLoading" @click="aiOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 知识点学习弹窗：当前页面直接观看视频 / 查看内容，不跳转 -->
    <el-dialog
      v-model="learnDialogVisible"
      :title="learningPoint?.name || '知识点学习'"
      width="760px"
      append-to-body
      destroy-on-close
    >
      <div v-if="learningPoint" class="learn-point-dialog">
        <div class="learn-point-meta">
          <el-tag v-if="learningPoint.chapterId" size="small" type="info" effect="plain">
            {{ chapterNameMap.get(learningPoint.chapterId) || '未分章' }}
          </el-tag>
          <el-tag v-else size="small" type="info" effect="plain">未分章</el-tag>
          <el-tag v-if="learningPoint.difficulty" size="small" :type="difficultyTag(learningPoint.difficulty)">
            {{ difficultyLabel(learningPoint.difficulty) }}
          </el-tag>
          <el-tag v-if="learningPoint.importance" size="small" :type="importanceTag(learningPoint.importance)">
            {{ importanceLabel(learningPoint.importance) }}
          </el-tag>
          <el-tag v-if="learningPoint.type" size="small" type="info" effect="plain">{{ learningPoint.type }}</el-tag>
          <el-tag size="small" :type="learningPoint.video ? 'success' : 'warning'" effect="plain">
            {{ learningPoint.video ? '含视频' : '暂无视频' }}
          </el-tag>
        </div>

        <div v-if="learningVideoUrl" class="learn-point-video-wrapper">
          <video :src="learningVideoUrl" controls autoplay class="learn-point-video">
            您的浏览器不支持视频播放
          </video>
        </div>
        <el-alert
          v-else
          type="info"
          :closable="false"
          show-icon
          title="该知识点暂无视频，以下为知识点内容"
          class="learn-point-tip"
        />

        <div class="learn-point-content">{{ learningPoint.content || '暂无知识点内容说明' }}</div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="learnDialogVisible = false">关 闭</el-button>
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
import { listPoint, getPoint, delPoint, addPoint, updatePoint, aiGeneratePoint } from "@/api/project/point"
import { listSubject } from "@/api/project/subject"
import { listPosition, addPosition } from "@/api/project/position"
import { listChapter } from "@/api/project/chapter"
import { getToken } from "@/utils/auth"
import { useRoute } from "vue-router"
import ChapterManagePanel from "./components/ChapterManagePanel.vue"

const { proxy } = getCurrentInstance()
const route = useRoute()

/** 顶部学科选择（页面主线） */
const subjectId = ref(undefined)
const subjectList = ref([])
const pointList = ref([])
// 当前学科下的全部章
const chapterList = ref([])
const positionList = ref([])
const positionLoading = ref(false)
const open = ref(false)
const loading = ref(false)
const ids = ref([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref("")

/** 学习方式：知识点学习展示知识点表格；视频学习在表格下方追加课程章节管理 */
const learnModeType = ref('point')
const learnDialogVisible = ref(false)
const learningPoint = ref(null)
const learningVideoUrl = ref('')
const chapterPanelRef = ref(null)
const chapterTotal = ref(0)

/** 岗位弹窗 */
const positionOpen = ref(false)
const positionForm = ref({})
const positionRules = {
  name: [
    { required: true, message: "岗位名称不能为空", trigger: "blur" }
  ]
}

/** AI 生成弹窗 */
const aiOpen = ref(false)
const aiLoading = ref(false)
const aiForm = ref({})
const aiRules = {
  positionId: [
    { required: true, message: "请选择岗位", trigger: "change" }
  ],
  chapterCount: [
    { required: true, message: "请输入章节数", trigger: ["blur", "change"] }
  ]
}

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
    chapterId: null,
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

/** 当前选中学科对象 */
const selectedSubject = computed(() => subjectList.value.find(item => item.id === subjectId.value))

/** 章 ID -> 章名映射 */
const chapterNameMap = computed(() => {
  const map = new Map()
  chapterList.value.forEach(item => map.set(item.id, item.name || item.title))
  return map
})

/** 知识点表单可选的章（当前学科下的章） */
const formChapterList = computed(() => {
  if (!form.value.subjectId) {
    return []
  }
  return chapterList.value.filter(item => item.subjectId === form.value.subjectId)
})

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

/** 查询知识点列表（未选学科时不请求） */
function getList() {
  if (!subjectId.value) {
    pointList.value = []
    total.value = 0
    ids.value = []
    single.value = true
    multiple.value = true
    return Promise.resolve()
  }
  loading.value = true
  const params = { ...queryParams.value }
  return listPoint(params).then(response => {
    pointList.value = response.rows || []
    total.value = response.total || 0
    loading.value = false
  }).catch(() => {
    loading.value = false
  })
}

/** 查询学科列表 */
function getSubjectList() {
  listSubject({ pageSize: 9999, pageNum: 1 }).then(res => {
    subjectList.value = res.rows || res.data || []
  })
}

/** 查询岗位列表（AI 按章生成使用） */
function getPositionList() {
  positionLoading.value = true
  listPosition({ pageNum: 1, pageSize: 9999 }).then(res => {
    positionList.value = res.rows || res.data || []
  }).catch(() => {
    positionList.value = []
  }).finally(() => {
    positionLoading.value = false
  })
}

/** 查询当前学科下的章 */
function getChapterList() {
  if (!subjectId.value) {
    chapterList.value = []
    chapterTotal.value = 0
    return Promise.resolve()
  }
  return listChapter({ subjectId: subjectId.value }).then(res => {
    const list = res.data || res.rows || []
    chapterList.value = list
      .filter(item => item.subjectId === subjectId.value)
      .map(item => ({ ...item, name: item.name || item.title }))
  }).catch(() => {
    chapterList.value = []
    chapterTotal.value = 0
  })
}

/** 切换学科：清空筛选并重新加载章与知识点 */
function onSubjectChange() {
  queryParams.value.subjectId = subjectId.value || null
  queryParams.value.pageNum = 1
  queryParams.value.chapterId = null
  queryParams.value.name = null
  queryParams.value.difficulty = null
  queryParams.value.importance = null
  queryParams.value.type = null
  pointList.value = []
  total.value = 0
  if (!subjectId.value) {
    chapterList.value = []
    return
  }
  Promise.all([getChapterList(), getList()])
}

/** 学习方式切换：视频学习时滚动到课程章节区域 */
function handleLearnWayChange(value) {
  if (value === 'point') {
    getList()
    return
  }
  chapterTotal.value = chapterList.value.length
  nextTick(() => {
    chapterPanelRef.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  })
}

/** 课程章节面板变更后，同步刷新知识点页的章下拉与列表 */
function handleChapterChanged() {
  getChapterList().then(() => getList())
}

/** 外部（AI 按章生成 / 导入）新增章后，刷新内嵌章节面板 */
function refreshChapterPanel() {
  chapterPanelRef.value?.getList?.()
}

/** 行内学习：弹窗播放视频 / 展示知识点内容，不跳转任何页面 */
function handleLearnPoint(row) {
  learningPoint.value = row
  const video = row.video || ''
  learningVideoUrl.value = !video
    ? ''
    : video.startsWith('http')
      ? video
      : import.meta.env.VITE_APP_BASE_API + video
  learnDialogVisible.value = true
}

/** 更多操作 */
function handleActionCommand(command) {
  if (command === 'ai') {
    handleAiGenerate()
  } else if (command === 'import') {
    handleImport()
  } else if (command === 'export') {
    handleExport()
  }
}

function cancel() {
  open.value = false
  reset()
}

function reset() {
  form.value = {
    id: null,
    subjectId: subjectId.value || null,
    chapterId: null,
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
  queryParams.value.pageNum = 1
  queryParams.value.chapterId = null
  queryParams.value.name = null
  queryParams.value.difficulty = null
  queryParams.value.importance = null
  queryParams.value.type = null
  getList()
}

function handleSelectionChange(selection) {
  ids.value = selection.map(item => item.id)
  single.value = selection.length != 1
  multiple.value = !selection.length
}

/** 切换表单学科时，清空不属于该学科的章 */
function handleFormSubjectChange() {
  if (form.value.chapterId && !formChapterList.value.some(item => item.id === form.value.chapterId)) {
    form.value.chapterId = null
  }
}

function handleAdd() {
  reset()
  // 默认带上当前学科与筛选中的章
  form.value.subjectId = subjectId.value || null
  if (queryParams.value.chapterId) {
    form.value.chapterId = queryParams.value.chapterId
  }
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

/** 新增岗位 */
function handleAddPosition() {
  positionForm.value = { name: null, description: null }
  positionOpen.value = true
  nextTick(() => proxy.$refs["positionRef"]?.clearValidate())
}

/** 提交岗位表单 */
function submitPosition() {
  proxy.$refs["positionRef"].validate(valid => {
    if (!valid) {
      return
    }
    addPosition(positionForm.value).then(() => {
      proxy.$modal.msgSuccess("新增成功")
      positionOpen.value = false
      getPositionList()
    })
  })
}

/** 打开 AI 按章生成弹窗（学科取顶部选择） */
function handleAiGenerate() {
  if (!subjectId.value) {
    proxy.$modal.msgWarning("请先选择学科")
    return
  }
  aiForm.value = {
    positionId: positionList.value[0]?.id || null,
    chapterCount: 3
  }
  aiOpen.value = true
  nextTick(() => proxy.$refs["aiRef"]?.clearValidate())
}

/** 提交 AI 按章生成 */
function submitAiGenerate() {
  proxy.$refs["aiRef"].validate(valid => {
    if (!valid) {
      return
    }
    aiLoading.value = true
    aiGeneratePoint({
      positionId: aiForm.value.positionId,
      subjectId: subjectId.value,
      chapterCount: aiForm.value.chapterCount
    }).then(res => {
      const result = res.data || {}
      proxy.$modal.msgSuccess(`生成完成：${result.chapterCount || 0} 个章、${result.pointCount || 0} 个知识点`)
      aiOpen.value = false
      queryParams.value.pageNum = 1
      getChapterList().then(() => getList())
      refreshChapterPanel()
    }).catch(() => {
      // 失败时由请求拦截器展示后端返回的 msg
    }).finally(() => {
      aiLoading.value = false
    })
  })
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
  proxy.$alert("<div style='overflow: auto;overflow-x: hidden;max-height: 70vh;padding: 10px 20px 0;'>" + (response.msg || "导入完成") + "</div>", "导入结果", { dangerouslyUseHTMLString: true })
  getSubjectList()
  getChapterList().then(() => getList())
  refreshChapterPanel()
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

onMounted(() => {
  getSubjectList()
  getPositionList()
  // 支持从课程页「前往知识点管理」带学科进入
  const querySubjectId = Number(route.query.subjectId) || null
  if (querySubjectId) {
    subjectId.value = querySubjectId
    onSubjectChange()
  }
})
</script>

<style scoped>
.mb-8 {
  margin-bottom: 16px;
}
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
.ai-tip {
  margin-left: 90px;
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
}
.position-inline-tip {
  margin: 0 0 12px 90px;
}
/* 学科选择与学习方式同一行 */
.subject-mode-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}
.subject-mode-row .learn-mode-switch {
  margin-left: auto;
}
/* 卡片头与筛选栏 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.card-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  color: #303133;
}
.card-header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.list-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.refresh-btn {
  margin-left: auto;
}

.learn-point-dialog {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.learn-point-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.learn-point-video-wrapper {
  background: #000;
  border-radius: 6px;
  overflow: hidden;
}
.learn-point-video {
  width: 100%;
  max-height: 420px;
  display: block;
  outline: none;
}
.learn-point-tip {
  margin: 0;
}
.learn-point-content {
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
  white-space: pre-wrap;
  max-height: 260px;
  overflow-y: auto;
  padding: 12px 14px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
/* 页面容器背景 – 极浅灰 */
.app-container {
  background-color: #f7f8fa;
  padding: 20px;
  min-height: 100vh;
}

/* 表格区域 – 白色，极淡阴影 */
.el-table {
  background-color: #ffffff;
  border-radius: 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

/* 表格头部 – 极浅灰 */
.el-table th.el-table__cell {
  background-color: #fafbfc !important;
}

/* 分页 – 白色，与表格无缝 */
.pagination-container {
  background-color: #ffffff;
  padding: 10px 16px;
  border-radius: 0 0 8px 8px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  margin-top: -1px;
}

/* 对话框 – 整体极浅灰背景，内容白色 */
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

/* 输入框边框颜色稍微变淡 */
.el-input__wrapper {
  box-shadow: 0 0 0 1px #e7e7e7 inset !important;
}
.el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #d2d3d4 inset !important;
}
</style>
