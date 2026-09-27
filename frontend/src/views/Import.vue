<template>
  <div class="space-y-6">
    <!-- 页面标题 -->
    <div class="card">
      <h1 class="text-2xl font-bold text-gray-800 mb-2">数据导入</h1>
      <p class="text-gray-500">上传Excel文件，批量导入数据到系统</p>
    </div>

    <!-- 上传区域 -->
    <div class="card">
      <div class="flex items-center justify-between mb-6">
        <h2 class="text-lg font-semibold text-gray-700">上传文件</h2>
        <el-button type="primary" link @click="downloadTemplate">
          <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
          下载导入模板
        </el-button>
      </div>

      <el-upload
        ref="uploadRef"
        class="upload-area"
        drag
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        :before-upload="beforeUpload"
        accept=".xlsx,.xls"
      >
        <div class="upload-content py-8">
          <div class="upload-icon mb-4">
            <svg class="w-16 h-16 mx-auto text-gray-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5"
                d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
            </svg>
          </div>
          <p class="text-gray-600 mb-2">将Excel文件拖到此处，或<em class="text-blue-500 not-italic">点击上传</em></p>
          <p class="text-gray-400 text-sm">支持 .xlsx、.xls 格式，单个文件最大100MB</p>
        </div>
      </el-upload>

      <!-- 已选文件 -->
      <div v-if="selectedFile" class="mt-4 p-4 bg-gray-50 rounded-lg flex items-center justify-between">
        <div class="flex items-center">
          <div class="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center mr-3">
            <svg class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <div>
            <p class="text-gray-700 font-medium">{{ selectedFile.name }}</p>
            <p class="text-gray-400 text-sm">{{ formatFileSize(selectedFile.size) }}</p>
          </div>
        </div>
        <el-button type="danger" link @click="removeFile">
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </el-button>
      </div>

      <!-- 上传按钮 -->
      <div class="mt-6 flex justify-end">
        <el-button
          type="primary"
          size="large"
          :loading="uploading"
          :disabled="!selectedFile"
          @click="handleUpload"
        >
          <svg v-if="!uploading" class="w-5 h-5 mr-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-8l-4-4m0 0L8 8m4-4v12" />
          </svg>
          {{ uploading ? '导入中...' : '开始导入' }}
        </el-button>
      </div>

      <!-- 上传进度 -->
      <div v-if="uploading" class="mt-4">
        <el-progress :percentage="uploadProgress" :status="uploadProgress === 100 ? 'success' : ''" />
        <p class="text-sm text-gray-500 mt-2 text-center">正在导入数据，请稍候...</p>
      </div>
    </div>

    <!-- 导入结果 -->
    <div v-if="importResult" class="card">
      <div class="flex items-center mb-4">
        <div :class="[
          'w-10 h-10 rounded-full flex items-center justify-center mr-3',
          isValidationFailed ? 'bg-red-100' : importResult.failCount === 0 ? 'bg-green-100' : 'bg-yellow-100'
        ]">
          <svg v-if="!isValidationFailed && importResult.failCount === 0" class="w-6 h-6 text-green-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 13l4 4L19 7" />
          </svg>
          <svg v-else-if="isValidationFailed" class="w-6 h-6 text-red-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
          <svg v-else class="w-6 h-6 text-yellow-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>
        <div>
          <h3 class="text-lg font-semibold text-gray-800">{{ isValidationFailed ? '字段校验未通过' : '导入完成' }}</h3>
          <p class="text-gray-500 text-sm">批次号：{{ importResult.batchNo }}</p>
        </div>
      </div>

      <!-- 校验失败提示 -->
      <div v-if="isValidationFailed" class="mb-6 p-4 bg-red-50 border border-red-100 rounded-lg">
        <p class="text-red-700 font-medium mb-1">{{ importResult.message }}</p>
        <p class="text-red-500 text-sm">校验不过的数据不能进入上送步骤。请下载错误行，修正后重新上传文件。</p>
      </div>

      <!-- 统计数据 -->
      <div class="grid grid-cols-3 gap-4 mb-6">
        <div class="bg-blue-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-blue-600">{{ importResult.totalCount }}</p>
          <p class="text-gray-500 text-sm">总记录数</p>
        </div>
        <div class="bg-green-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-green-600">{{ importResult.successCount }}</p>
          <p class="text-gray-500 text-sm">成功导入</p>
        </div>
        <div class="bg-red-50 rounded-lg p-4 text-center">
          <p class="text-2xl font-bold text-red-600">{{ importResult.failCount }}</p>
          <p class="text-gray-500 text-sm">{{ isValidationFailed ? '校验错误' : '导入失败' }}</p>
        </div>
      </div>

      <!-- 错误列表 -->
      <div v-if="importResult.errorList && importResult.errorList.length > 0">
        <div class="flex items-center justify-between mb-3">
          <h4 class="font-medium text-gray-700">
            错误数据详情
            <span v-if="errorTruncated" class="text-sm text-gray-400 font-normal">（仅显示前{{ importResult.errorList.length }}条）</span>
          </h4>
          <el-button v-if="isValidationFailed" type="danger" plain size="small" @click="downloadErrorRows">
            <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2"
                d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
            </svg>
            下载错误行
          </el-button>
        </div>
        <el-table :data="importResult.errorList" stripe max-height="300">
          <el-table-column prop="rowIndex" label="行号" width="80" />
          <el-table-column prop="insuranceNo" label="医保编号" width="130" show-overflow-tooltip />
          <el-table-column prop="name" label="姓名" width="100" />
          <el-table-column prop="itemCode" label="项目编码" width="120" show-overflow-tooltip />
          <el-table-column prop="errorMsg" label="错误原因" min-width="240" show-overflow-tooltip />
        </el-table>
      </div>

      <!-- 操作按钮 -->
      <div class="mt-6 flex justify-end space-x-3">
        <el-button @click="resetImport">{{ isValidationFailed ? '重新上传' : '继续导入' }}</el-button>
        <el-button v-if="isValidationFailed" type="danger" @click="downloadErrorRows">下载错误行</el-button>
        <el-button v-else type="primary" @click="goToDetail">查看详情</el-button>
      </div>
    </div>

    <!-- 使用说明 -->
    <div class="card">
      <h2 class="text-lg font-semibold text-gray-700 mb-4">使用说明</h2>
      <div class="space-y-3 text-gray-600">
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">1</span>
          <p>下载导入模板，按照模板格式填写数据。<span class="text-red-500">医保编号、姓名、项目编码、金额、就诊日期、机构编码</span>为必填项</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">2</span>
          <p>上传Excel文件，系统先进行导入前字段校验：必填字段缺失、金额或就诊日期格式错误的行会被逐行列出</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">3</span>
          <p>校验全部通过后数据才会入库；校验不过时可下载错误行，修正后重新上传，校验不过的数据不能进入上送步骤</p>
        </div>
        <div class="flex items-start">
          <span class="flex-shrink-0 w-6 h-6 bg-blue-100 text-blue-600 rounded-full flex items-center justify-center text-sm font-medium mr-3">4</span>
          <p>支持最多5万条数据导入，系统采用流式解析，内存占用低</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { excelApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const uploadRef = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const importResult = ref(null)

// 是否为字段校验未通过（未入库，不能进入上送步骤）
const isValidationFailed = computed(() => importResult.value?.status === 'validation_failed')

// 错误条数超过保留上限时，明细列表被截断
const errorTruncated = computed(() => {
  if (!importResult.value) return false
  return (importResult.value.failCount || 0) > (importResult.value.errorList?.length || 0)
})

const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const beforeUpload = (file) => {
  const isExcel = file.name.endsWith('.xlsx') || file.name.endsWith('.xls')
  if (!isExcel) {
    ElMessage.error('只能上传Excel文件')
    return false
  }

  const isLt100M = file.size / 1024 / 1024 < 100
  if (!isLt100M) {
    ElMessage.error('文件大小不能超过100MB')
    return false
  }

  return true
}

const removeFile = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
}

const handleUpload = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  uploadProgress.value = 0
  importResult.value = null

  try {
    const res = await excelApi.import(selectedFile.value, (progressEvent) => {
      if (progressEvent.lengthComputable) {
        uploadProgress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total)
      }
    })

    uploadProgress.value = 100
    importResult.value = res.data
    if (res.data.status === 'validation_failed') {
      ElMessage.error(res.message)
    } else {
      ElMessage.success(res.message)
    }
  } catch (error) {
    // 错误已在拦截器中处理
  } finally {
    uploading.value = false
  }
}

const downloadTemplate = () => {
  const token = userStore.token
  const url = excelApi.downloadTemplate()
  window.open(`${url}?token=${token}`, '_blank')
}

const downloadErrorRows = () => {
  if (!importResult.value?.batchNo) {
    ElMessage.warning('暂无错误行可下载')
    return
  }
  const token = userStore.token
  const url = excelApi.exportValidationErrors(importResult.value.batchNo)
  window.open(`${url}?token=${token}`, '_blank')
}

const resetImport = () => {
  selectedFile.value = null
  uploadRef.value?.clearFiles()
  importResult.value = null
  uploadProgress.value = 0
}

const goToDetail = () => {
  if (importResult.value?.batchNo) {
    router.push(`/data/${importResult.value.batchNo}`)
  }
}
</script>

<style scoped>
.upload-area :deep(.el-upload-dragger) {
  @apply border-2 border-dashed border-gray-200 rounded-xl transition-all duration-200;
}

.upload-area :deep(.el-upload-dragger:hover) {
  @apply border-blue-400 bg-blue-50;
}

.upload-area :deep(.el-upload-dragger.is-dragover) {
  @apply border-blue-500 bg-blue-100;
}
</style>
