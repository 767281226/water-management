<template>
  <div style="height: 100%; display: flex; align-items: center; justify-content: center">
    <el-result icon="info" title="页面不存在" sub-title="您访问的页面不存在或已被移除。">
      <template #extra>
        <el-button type="primary" @click="$router.replace('/')">返回首页</el-button>
      </template>
    </el-result>
  </div>
</template>
