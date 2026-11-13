<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { docApi, downloadDoc, LEVELS, type DocVO } from '@/api/doc'
import { useAuthStore } from '@/stores/auth'
import { fmtSize } from '@/utils/file'

/** 在线预览：PDF / 图片 / 文本，叠加"姓名 + 工号 + 日期"水印 */
const props = defineProps<{ modelValue: boolean; doc: DocVO | null }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; apply: [DocVO] }>()

const auth = useAuthStore()
const url = ref('')
const loading = ref(false)
const ext = computed(() => (props.doc?.fileExt || '').toLowerCase())
const isImage = computed(() => ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext.value))

/** 平铺水印（SVG 背景） */
const watermark = computed(() => {
  const text = `${auth.user?.realName ?? ''} ${auth.user?.employeeNo ?? ''} ${dayjs().format('YYYY-MM-DD')}`
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="280" height="170"><text x="20" y="100" transform="rotate(-22 140 85)" font-size="15" fill="#1a2233" fill-opacity="0.13" font-family="Microsoft YaHei, sans-serif">${text}</text></svg>`
  return `url("data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}")`
})

watch(
  () => [props.modelValue, props.doc?.id],
  async () => {
    url.value = ''
    if (!props.modelValue || !props.doc) return
    loading.value = true
    try {
      url.value = (await docApi.ticket(props.doc.id, 'preview')).url
    } finally {
      loading.value = false
    }
  }
)
</script>

<template>
  <el-dialog :model-value="modelValue" width="1000px" top="4vh" class="preview-dialog" destroy-on-close
    @update:model-value="emit('update:modelValue', $event)">
    <template #header>
      <div v-if="doc" class="ph">
        <el-tag :type="LEVELS[doc.level].type" size="small">{{ doc.levelLabel }}</el-tag>
        <b>{{ doc.code ? `${doc.code} ` : '' }}{{ doc.name }}</b>
        <span class="muted small">{{ doc.fileName }} · {{ fmtSize(doc.fileSize) }}</span>
      </div>
    </template>
    <div v-loading="loading" class="viewer">
      <template v-if="url">
        <img v-if="isImage" :src="url" class="img" alt="" />
        <iframe v-else :src="url" class="frame" title="预览" />
      </template>
      <div class="wm" :style="{ backgroundImage: watermark }" />
    </div>
    <template #footer>
      <div class="pf">
        <span class="muted small">预览内容带有个人水印，仅供内部查阅</span>
        <div>
          <el-button @click="emit('update:modelValue', false)">关闭</el-button>
          <template v-if="doc">
            <el-button v-if="doc.access.canDownload" type="primary" icon="Download" @click="downloadDoc(doc.id, doc.access.requestId)">下载</el-button>
            <el-button v-else-if="doc.access.pending" disabled>审批中</el-button>
            <el-button v-else v-perm="'doc:request'" type="primary" @click="emit('apply', doc)">申请下载</el-button>
          </template>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.ph {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-right: 30px;
}
.viewer {
  position: relative;
  height: 72vh;
  background: #eef2f8;
  border-radius: 10px;
  overflow: hidden;
}
.frame {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}
.img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.wm {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-repeat: repeat;
}
.pf {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
@media (max-width: 768px) {
  .viewer {
    height: 62vh;
  }
}
</style>
