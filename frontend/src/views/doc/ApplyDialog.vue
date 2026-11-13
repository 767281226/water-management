<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { docApi, LEVELS, type DocVO } from '@/api/doc'
import { projectApi, type ProjectOption } from '@/api/project'
import { useAuthStore } from '@/stores/auth'
import { fmtSize } from '@/utils/file'
import FileIcon from '@/components/FileIcon.vue'

const props = defineProps<{ modelValue: boolean; docs: DocVO[] }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; done: [] }>()

const auth = useAuthStore()
const purpose = ref('')
const projectId = ref<number | null>(null)
const projects = ref<ProjectOption[]>([])
const saving = ref(false)

const needApproval = computed(() => props.docs.filter((d) => d.needApproval))
const secret = computed(() => needApproval.value.some((d) => d.level === 'SECRET'))

watch(
  () => props.modelValue,
  async (v) => {
    if (!v) return
    purpose.value = ''
    projectId.value = null
    if (!projects.value.length && auth.hasPerm(['weekly:plan', 'weekly:report'])) {
      projects.value = await projectApi.fillable().catch(() => [])
    }
  }
)

async function submit() {
  if (!purpose.value.trim()) return ElMessage.warning('请填写用途说明')
  saving.value = true
  try {
    await docApi.apply(needApproval.value.map((d) => d.id), purpose.value, projectId.value)
    ElMessage.success('申请已提交，审批结果可在「我的下载申请」中查看')
    emit('update:modelValue', false)
    emit('done')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="申请下载" width="580px" :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)">
    <div class="label">申请资料（{{ needApproval.length }}）</div>
    <div class="docs">
      <div v-for="d in needApproval" :key="d.id" class="doc">
        <FileIcon :ext="d.fileExt" :size="28" />
        <div class="dm"><b>{{ d.name }}</b><span>{{ d.code || d.categoryPath }} · {{ fmtSize(d.fileSize) }}</span></div>
        <el-tag :type="LEVELS[d.level].type" size="small">{{ d.levelLabel }}</el-tag>
      </div>
    </div>
    <el-alert v-if="docs.length > needApproval.length" type="success" :closable="false" style="margin-bottom: 12px"
      :title="`另有 ${docs.length - needApproval.length} 份资料无需审批，可以直接下载`" />
    <el-form label-position="top">
      <el-form-item label="关联项目（选填）">
        <el-select v-model="projectId" clearable filterable placeholder="选择本次用途关联的项目" style="width: 100%">
          <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="用途说明" required>
        <el-input v-model="purpose" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="如：青溪县项目断面设计参考" />
      </el-form-item>
    </el-form>
    <div class="flow">
      <span class="node done">提交申请</span><el-icon><ArrowRight /></el-icon>
      <span class="node">部门负责人审批</span>
      <template v-if="secret"><el-icon><ArrowRight /></el-icon><span class="node red">公司领导终审（含涉密资料）</span></template>
      <el-icon><ArrowRight /></el-icon><span class="node">有效期内下载</span>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!needApproval.length" @click="submit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.label {
  font-size: 13px;
  color: var(--hy-text-2);
  margin-bottom: 8px;
}
.docs {
  max-height: 220px;
  overflow: auto;
  margin-bottom: 12px;
}
.doc {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 10px;
  background: #f6f8fd;
  margin-bottom: 6px;
}
.dm {
  flex: 1;
  min-width: 0;
}
.dm b {
  display: block;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dm span {
  font-size: 12px;
  color: var(--hy-text-3);
}
.flow {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: 12px;
  color: var(--hy-text-3);
}
.node {
  padding: 3px 10px;
  border-radius: 12px;
  background: #f1f5fd;
}
.node.done {
  background: var(--el-color-primary-light-9);
  color: var(--hy-primary);
}
.node.red {
  background: #fdecec;
  color: #d64545;
}
</style>
