<script lang="ts" setup>
import type { PhotoGroup } from "@/types";
import { reset, submitForm } from "@formkit/core";
import { axiosInstance } from "@halo-dev/api-client";
import { VButton, VModal, VSpace } from "@halo-dev/components";
import { useMagicKeys } from "@vueuse/core";
import { cloneDeep } from "lodash-es";
import { computed, nextTick, onMounted, ref, watch } from "vue";

const props = withDefaults(
  defineProps<{
    visible: boolean;
    group: PhotoGroup | null;
  }>(),
  {
    visible: false,
    group: null,
  }
);

const emit = defineEmits<{
  (event: "update:visible", visible: boolean): void;
  (event: "close"): void;
}>();

const initialFormState: PhotoGroup = {
  apiVersion: "core.halo.run/v1alpha1",
  kind: "PhotoGroup",
  metadata: {
    name: "",
    generateName: "photo-group-",
  },
  spec: {
    displayName: "",
    priority: 0,
    hidden: true,
    attachmentGroup: ''
  },
  status: {
    photoCount: 0,
  },
};

const formState = ref<PhotoGroup>(initialFormState);
const saving = ref(false);

const isUpdateMode = computed(() => {
  return !!formState.value.metadata.creationTimestamp;
});
const isMac = /macintosh|mac os x/i.test(navigator.userAgent);
const modalTitle = computed(() => {
  return isUpdateMode.value ? "编辑分组" : "新建分组";
});
const annotationsGroupFormRef = ref();

const handleCreateOrUpdateGroup = async () => {
  annotationsGroupFormRef.value?.handleSubmit();
  await nextTick();
  const { customAnnotations, annotations, customFormInvalid, specFormInvalid } = annotationsGroupFormRef.value || {};
  if (customFormInvalid || specFormInvalid) {
    return;
  }
  formState.value.metadata.annotations = {
    ...annotations,
    ...customAnnotations,
  };
  try {
    saving.value = true;
    if (isUpdateMode.value) {
      await axiosInstance.put(
        `/apis/core.halo.run/v1alpha1/photogroups/${formState.value.metadata.name}`,
        formState.value
      );
    } else {
      await axiosInstance.post("/apis/core.halo.run/v1alpha1/photogroups", formState.value);
    }
    onVisibleChange(false);
  } catch (e) {
    console.error("Failed to create photo group", e);
  } finally {
    saving.value = false;
  }
};

const onVisibleChange = (visible: boolean) => {
  emit("update:visible", visible);
  if (!visible) {
    emit("close");
  }
};

const handleResetForm = () => {
  formState.value = cloneDeep(initialFormState);
  reset("photo-group-form");
};

watch(
  () => props.visible,
  (visible) => {
    if (visible && props.group) {
      formState.value = cloneDeep(props.group);
      return;
    }
    handleResetForm();
  }
);

const { ControlLeft_Enter, Meta_Enter } = useMagicKeys();

watch(ControlLeft_Enter, (v) => {
  if (v && !isMac) {
    submitForm("photo-group-form");
  }
});

watch(Meta_Enter, (v) => {
  if (v && isMac) {
    submitForm("photo-group-form");
  }
});

const attachmentGroup = ref([]);
const fetchAttachmentGroup = async () => {
  try {
    const { data } = await axiosInstance.get("/apis/storage.halo.run/v1alpha1/groups?labelSelector=!halo.run/hidden&sort=metadata.creationTimestamp,asc");
    attachmentGroup.value = data.items.map((item) => ({
      label: item.spec.displayName,
      value: item.metadata.name,
    }));
  } catch (e) {
  }
};

onMounted(() => {
  fetchAttachmentGroup();
})
</script>
<template>
  <VModal :visible="visible" :width="600" :title="modalTitle" @update:visible="onVisibleChange">
    <FormKit
      id="photo-group-form"
      v-model="formState.spec"
      name="photo-group-form"
      :classes="{ form: 'w-full' }"
      type="form"
      :config="{ validationVisibility: 'submit' }"
      @submit="handleCreateOrUpdateGroup"
    >
      <div class="md:grid md:grid-cols-4 md:gap-6">
        <div class="md:col-span-1">
          <div class="sticky top-0">
            <span class="text-base font-medium text-gray-900"> 常规 </span>
          </div>
        </div>
        <div class="mt-5 divide-y divide-gray-100 md:col-span-3 md:mt-0">
          <FormKit
            name="displayName"
            label="分组名称"
            type="text"
            validation="required"
            help="可根据此名称查询图片"
          ></FormKit>
          <FormKit
            name="attachmentGroup"
            label="附件组别"
            type="select"
            help="关联到附件列表"
            placeholder="可关联附件库"
            :options="attachmentGroup"
          ></FormKit>
          <FormKit
            name="hidden"
            label="是否隐藏"
            type="checkbox"
            validation="required"
            help="是否隐藏，不在前台显示"
          ></FormKit>
        </div>
      </div>
    </FormKit>
    <div class="py-5">
      <div class="border-t border-gray-200"></div>
    </div>
    <div class="md:grid md:grid-cols-4 md:gap-6">
      <div class="md:col-span-1">
        <div class="sticky top-0">
          <span class="text-base font-medium text-gray-900"> 元数据 </span>
        </div>
      </div>
      <div class="mt-5 divide-y divide-gray-100 md:col-span-3 md:mt-0">
        <AnnotationsForm
          v-if="visible"
          :key="formState.metadata.name"
          ref="annotationsGroupFormRef"
          :value="formState.metadata.annotations"
          kind="PhotoGroup"
          group="core.halo.run"
        />
      </div>
    </div>
    <template #footer>
      <VSpace>
        <VButton type="secondary" @click="submitForm('photo-group-form')">
          提交 {{ `${isMac ? "⌘" : "Ctrl"} + ↵` }}
        </VButton>
        <VButton @click="onVisibleChange(false)">取消 Esc</VButton>
      </VSpace>
    </template>
  </VModal>
</template>
