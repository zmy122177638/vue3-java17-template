<script setup lang="ts">
import { computed, ref } from 'vue';

import { Profile } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { $t } from '#/locales';

import ProfileBase from './base-setting.vue';
import ProfilePasswordSetting from './password-setting.vue';

const userStore = useUserStore();

const tabsValue = ref<string>('basic');

/**
 * 个人中心页签。
 * <p><b>只保留有真实后端能力的两项</b>：
 * <ul>
 *   <li>基本设置 —— {@code GET/PUT /api/user/profile}（本人资料：昵称/邮箱/手机号）；</li>
 *   <li>修改密码 —— {@code PUT /api/auth/password}（校验旧密码，成功后作废全部会话）。</li>
 * </ul>
 * vben 模板自带的「安全设置」「新消息提醒」两个页签是纯静态展示（写死的"已绑定手机：
 * 138****8293"、"已绑定邮箱：ant***sign.com"，以及一组没有提交动作的开关），
 * 后端没有任何对应能力，留着会让使用者以为这些功能已经存在，因此已移除。
 * 需要时再按真实接口重新实现，不要直接恢复成假数据。
 * <p>标签用 computed 而非 ref：语言切换时需要重新求值（$t 依赖当前语言）。
 */
const tabs = computed(() => [
  { label: $t('profile.tabBasic'), value: 'basic' },
  { label: $t('profile.tabPassword'), value: 'password' },
]);
</script>
<template>
  <Profile
    v-model:model-value="tabsValue"
    :title="$t('profile.pageTitle')"
    :user-info="userStore.userInfo"
    :tabs="tabs"
  >
    <template #content>
      <ProfileBase v-if="tabsValue === 'basic'" />
      <ProfilePasswordSetting v-else-if="tabsValue === 'password'" />
    </template>
  </Profile>
</template>
