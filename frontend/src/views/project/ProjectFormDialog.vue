<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { projectApi, type ProjectForm, type ProjectVO } from '@/api/project'
import type { DeptNode, UserOption } from '@/api/system'
import { useAuthStore } from '@/stores/auth'

const props = defineProps<{ modelValue: boolean; project: ProjectVO | null; users: UserOption[]; depts: DeptNode[]; types: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; saved: [id: number] }>()

const auth = useAuthStore()
const formRef = ref<FormInstance>()
const saving = ref(false)
const empty = (): ProjectForm => ({
  code: '', name: '', type: '', client: '', leaderId: null, deptId: null, contractAmount: null,
  startDate: null, planEndDate: null, memberIds: [], remark: ''
})
const form = reactive<ProjectForm>(empty())
const isEdit = computed(() => !!props.project)

const rules: FormRules = {
  name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  leaderId: [{ required: true, message: '请选择项目负责人', trigger: 'change' }],
  planEndDate: [
    { required: true, message: '请选择计划完成日期', trigger: 'change' },
    {
      validator: (_r, v, cb) => (v && form.startDate && v < form.startDate ? cb(new Error('计划完成日期不能早于开始日期')) : cb()),
      trigger: 'change'
    }
  ]
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    const p = props.project
    Object.assign(form, p
      ? {
          code: p.code, name: p.name, type: p.type, client: p.client || '', leaderId: p.leaderId, deptId: p.deptId,
          contractAmount: p.contractAmount, startDate: p.startDate, planEndDate: p.planEndDate,
          memberIds: p.members.map((m) => m.id), remark: p.remark || ''
        }
      : { ...empty(), type: props.types[0] || '', leaderId: auth.user?.id ?? null, deptId: auth.user?.deptId ?? null })
    formRef.value?.clearValidate()
  }
)

/** 选择负责人后，默认带出其所在部门 */
function onLeaderChange(id: number) {
  form.memberIds = form.memberIds.filter((m) => m !== id)
  if (!isEdit.value && !form.deptId) {
    form.deptId = props.users.find((x) => x.id === id)?.deptId ?? null
  }
}

async function submit() {
  await formRef.value?.validate()
  saving.value = true
  try {
    let id: number
    if (props.project) {
      await projectApi.update(props.project.id, form)
      id = props.project.id
      ElMessage.success('已保存')
    } else {
      id = await projectApi.create(form)
      ElMessage.success('项目已创建')
    }
    emit('update:modelValue', false)
    emit('saved', id)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="isEdit ? '编辑项目' : '新建项目'"
    width="680px"
    :close-on-click-modal="false"
    top="6vh"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-row :gutter="12">
        <el-col :span="24">
          <el-form-item label="项目名称" prop="name">
            <el-input v-model="form.name" maxlength="128" placeholder="如：XX县XX河道治理工程初步设计" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="项目编号" prop="code">
            <el-input v-model="form.code" maxlength="32" placeholder="留空自动生成" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="项目类型" prop="type">
            <el-select v-model="form.type" style="width: 100%">
              <el-option v-for="t in types" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="委托单位" prop="client"><el-input v-model="form.client" maxlength="128" /></el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="合同金额" prop="contractAmount">
            <el-input-number v-model="form.contractAmount" :min="0" :precision="2" :controls="false" style="width: 100%" placeholder="万元" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="项目负责人" prop="leaderId">
            <el-select v-model="form.leaderId" filterable style="width: 100%" @change="onLeaderChange">
              <el-option v-for="u in users" :key="u.id" :label="u.realName" :value="u.id">
                <span>{{ u.realName }}</span><span class="opt-sub">{{ u.deptName }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="所属部门" prop="deptId">
            <el-tree-select
              v-model="form.deptId"
              :data="depts"
              :props="{ label: 'name', children: 'children' }"
              node-key="id"
              check-strictly
              default-expand-all
              clearable
              placeholder="默认为负责人所在部门"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="开始日期" prop="startDate">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :sm="12">
          <el-form-item label="计划完成" prop="planEndDate">
            <el-date-picker v-model="form.planEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="参与人员" prop="memberIds">
            <el-select v-model="form.memberIds" multiple filterable style="width: 100%" placeholder="不含项目负责人">
              <el-option v-for="u in users.filter((x) => x.id !== form.leaderId)" :key="u.id" :label="u.realName" :value="u.id">
                <span>{{ u.realName }}</span><span class="opt-sub">{{ u.deptName }}</span>
              </el-option>
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.opt-sub {
  float: right;
  color: #8a94a6;
  font-size: 12px;
  margin-left: 12px;
}
</style>
