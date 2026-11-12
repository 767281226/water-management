<script setup lang="ts">
import { useRoute } from 'vue-router'

const route = useRoute()
</script>

<template>
  <div class="page">
    <div class="card">
      <el-empty :description="`「${route.meta.title}」模块正在开发中`">
        <p class="muted small">功能设计请参见需求规格说明书与演示原型（prototype/index.html）。</p>
      </el-empty>
    </div>
  </div>
</template>
