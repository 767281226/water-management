<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules, type UploadFile } from 'element-plus'
import { docApi, LEVELS, type CategoryNode, type DocForm, type DocLevel, type DocVO } from '@/api/doc'
import { ACCEPT_EXT, fmtSize } from '@/utils/file'

/** 上传资料 / 编辑资料 / 更新文件 */
const props = defineProps<{ modelValue: boolean; doc: DocVO | null; mode: 'create' | 'edit' | 'replace'; tree: CategoryNode[] }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [] }>()

const formRef = ref<FormInstance>()
const file = ref<File | null>(null)
const progress = ref(0)
const saving = ref(false)
const empty = (): DocForm => ({ categoryId: null, name: '', code: '', version: '', year: new Date().getFullYear(), level: 'INTERNAL', needApproval: true, remark: '' })
const form = reactive<DocForm>(empty())
const levels = Object.entries(LEVELS) as [DocLevel, (typeof LEVELS)[DocLevel]][]
const title = computed(() => ({ create: '上传资料', edit: '编辑资料', replace: '更新文件（新版本）' })[props.mode])

const rules: FormRules = {
  categoryId: [{ required: true, message: '请选择分类', trigger: 'change' }],
  name: [{ required: true, message: '请输入资料名称', trigger: 'blur' }],
  level: [{ required: true, message: '请选择密级', trigger: 'change' }]
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    file.value = null
    progress.value = 0
    const d = props.doc
    Object.assign(form, d
      ? { categoryId: d.categoryId, name: d.name, code: d.code || '', version: d.version || '', year: d.year, level: d.level, needApproval: d.needApproval, remark: d.remark || '' }
      : empty())
    formRef.value?.clearValidate()
  }
)

/** 选择文件后，用文件名自动填写名称和标准号 */
function onFile(f: UploadFile) {
  file.value = f.raw ?? null
  if (props.mode !== 'create' || !f.name) return
  const base = f.name.replace(/\.[^.]+$/, '')
  const m = base.match(/^((?:GB|SL|DL|DB|JTG|CJJ|NB|HJ|JGJ)(?:\s*\d+)?(?:\/T)?\s*[\d.]+\s*-\s*(\d{4}))\s*(.*)$/i)
  if (m) {
    form.code = m[1].replace(/\s+/g, ' ')
    form.year = Number(m[2])
    form.name = m[3] || base
  } else if (!form.name) {
    form.name = base
  }
}

function onLevel(l: DocLevel) {
  if (l === 'SECRET') form.needApproval = true
}

async function submit() {
  if (props.mode !== 'edit' && !file.value) return ElMessage.warning('请选择文件')
  if (props.mode !== 'replace') await formRef.value?.validate()
  saving.value = true
  try {
    if (props.mode === 'create') await docApi.upload(file.value!, form, (p) => (progress.value = p))
    else if (props.mode === 'edit') await docApi.update(props.doc!.id, form)
    else await docApi.replaceFile(props.doc!.id, file.value!, (p) => (progress.value = p))
    ElMessage.success('已保存')
    emit('update:modelValue', false)
    emit('saved')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" :title="title" width="620px" :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <el-upload v-if="mode !== 'edit'" drag :auto-upload="false" :show-file-list="false" :accept="ACCEPT_EXT" :limit="1" :on-change="onFile" class="up">
      <el-icon class="up-icon"><UploadFilled /></el-icon>
      <div v-if="file" class="up-file"><b>{{ file.name }}</b><span>{{ fmtSize(file.size) }}</span></div>
      <div v-else class="up-text">将文件拖到这里，或 <em>点击选择</em></div>
      <template #tip><div class="muted small">支持 PDF、Word、Excel、PPT、CAD 图纸、压缩包、图片等；PDF 和图片可在线预览</div></template>
    </el-upload>
    <el-progress v-if="saving && mode !== 'edit'" :percentage="progress" style="margin: 10px 0" />

    <el-form v-if="mode !== 'replace'" ref="formRef" :model="form" :rules="rules" label-width="86px" style="margin-top: 14px">
      <el-form-item label="分类" prop="categoryId">
        <el-tree-select v-model="form.categoryId" :data="tree" :props="{ label: 'name', children: 'children' }" node-key="id" check-strictly
          default-expand-all placeholder="请选择分类" style="width: 100%" />
      </el-form-item>
      <el-form-item label="资料名称" prop="name"><el-input v-model="form.name" maxlength="200" /></el-form-item>
      <el-row :gutter="12">
        <el-col :span="12"><el-form-item label="编号" prop="code"><el-input v-model="form.code" maxlength="64" placeholder="如 SL 252-2017" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="年份" prop="year"><el-input-number v-model="form.year" :min="1900" :max="2100" :controls="false" style="width: 100%" /></el-form-item></el-col>
        <el-col :span="12"><el-form-item label="版本" prop="version"><el-input v-model="form.version" maxlength="32" placeholder="选填" /></el-form-item></el-col>
        <el-col :span="12">
          <el-form-item label="密级" prop="level">
            <el-select v-model="form.level" style="width: 100%" @change="onLevel">
              <el-option v-for="[k, v] in levels" :key="k" :label="v.label" :value="k" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="下载审批">
        <el-switch v-model="form.needApproval" :disabled="form.level === 'SECRET'" active-text="需要审批" inactive-text="免审直接下载" />
        <div class="muted small" style="width: 100%">涉密资料必须审批，且需公司领导终审</div>
      </el-form-item>
      <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ mode === 'create' ? '上传' : '保存' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.up :deep(.el-upload-dragger) {
  border-radius: 14px;
  padding: 26px 10px;
  background: #f8faff;
}
.up-icon {
  font-size: 44px;
  color: var(--hy-primary);
}
.up-text em {
  color: var(--hy-primary);
  font-style: normal;
}
.up-file b {
  display: block;
}
.up-file span {
  font-size: 12px;
  color: var(--hy-text-3);
}
</style>
