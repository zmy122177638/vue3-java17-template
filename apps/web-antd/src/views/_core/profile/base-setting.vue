<script setup lang="ts">
import type { Recordable } from '@vben/types';

import type { VbenFormSchema } from '#/adapter/form';

import { computed, onMounted, ref } from 'vue';

import { ProfileBaseSetting } from '@vben/common-ui';
import { useUserStore } from '@vben/stores';

import { message } from 'ant-design-vue';

import { getUserProfileApi, updateUserProfileApi } from '#/api';
import { $t } from '#/locales';

const profileBaseSettingRef = ref();
const userStore = useUserStore();

/**
 * 基本资料表单。
 * <p>只含可编辑字段（昵称/邮箱/手机号）。用户名只读展示：它是账号标识，
 * 变更会影响审计与登录，属于管理员在「用户管理」里的操作。
 * <p>邮箱格式校验交给后端（{@code ProfileUpdateRequest} 上的 {@code @Email}），
 * 避免与后端规则重复维护；「用户管理」的邮箱字段也是同样的取舍。
 */
const formSchema = computed((): VbenFormSchema[] => {
  return [
    {
      component: 'Input',
      componentProps: { disabled: true },
      fieldName: 'username',
      help: $t('profile.usernameHelp'),
      label: $t('profile.username'),
    },
    {
      component: 'Input',
      fieldName: 'nickname',
      help: $t('profile.nicknameHelp'),
      label: $t('profile.nickname'),
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: $t('profile.email'),
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: $t('profile.phone'),
    },
  ];
});

onMounted(async () => {
  const profile = await getUserProfileApi();
  // 只回填 schema 声明的字段，避免把 roles 之类的只读数据塞进表单状态
  profileBaseSettingRef.value?.getFormApi().setValues({
    email: profile.email,
    nickname: profile.nickname,
    phone: profile.phone,
    username: profile.username,
  });
});

async function handleSubmit(values: Recordable<any>) {
  const updated = await updateUserProfileApi({
    email: values.email,
    nickname: values.nickname,
    phone: values.phone,
  });
  // 昵称会显示在导航栏（取自 userStore.userInfo.realName），就地更新，
  // 否则用户会看到"保存成功但右上角还是旧昵称"，误以为没生效
  if (userStore.userInfo) {
    userStore.setUserInfo({
      ...userStore.userInfo,
      realName: updated.nickname || updated.username,
    });
  }
  message.success($t('ui.actionMessage.operationSuccess'));
}
</script>
<template>
  <div>
    <p class="text-muted-foreground mb-4 text-sm">
      {{ $t('profile.scopeTip') }}
    </p>
    <ProfileBaseSetting
      ref="profileBaseSettingRef"
      :form-schema="formSchema"
      @submit="handleSubmit"
    />
  </div>
</template>
