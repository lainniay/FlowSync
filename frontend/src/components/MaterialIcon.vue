<script setup lang="ts">
import { computed } from 'vue'

import accountCircle from '@material-symbols/svg-400/rounded/account_circle.svg?raw'
import add from '@material-symbols/svg-400/rounded/add.svg?raw'
import addTask from '@material-symbols/svg-400/rounded/add_task.svg?raw'
import archive from '@material-symbols/svg-400/rounded/archive.svg?raw'
import arrowBack from '@material-symbols/svg-400/rounded/arrow_back.svg?raw'
import arrowDownward from '@material-symbols/svg-400/rounded/arrow_downward.svg?raw'
import arrowUpward from '@material-symbols/svg-400/rounded/arrow_upward.svg?raw'
import autoAwesome from '@material-symbols/svg-400/rounded/wand_stars.svg?raw'
import check from '@material-symbols/svg-400/rounded/check.svg?raw'
import checklist from '@material-symbols/svg-400/rounded/checklist.svg?raw'
import close from '@material-symbols/svg-400/rounded/close.svg?raw'
import dashboard from '@material-symbols/svg-400/rounded/dashboard.svg?raw'
import darkMode from '@material-symbols/svg-400/rounded/dark_mode.svg?raw'
import deleteIcon from '@material-symbols/svg-400/rounded/delete.svg?raw'
import deleteForever from '@material-symbols/svg-400/rounded/delete_forever.svg?raw'
import edit from '@material-symbols/svg-400/rounded/edit.svg?raw'
import eventBusy from '@material-symbols/svg-400/rounded/event_busy.svg?raw'
import expandMore from '@material-symbols/svg-400/rounded/keyboard_arrow_down.svg?raw'
import filterListOff from '@material-symbols/svg-400/rounded/filter_list_off.svg?raw'
import folder from '@material-symbols/svg-400/rounded/folder.svg?raw'
import group from '@material-symbols/svg-400/rounded/group.svg?raw'
import groupAdd from '@material-symbols/svg-400/rounded/group_add.svg?raw'
import key from '@material-symbols/svg-400/rounded/key.svg?raw'
import lightMode from '@material-symbols/svg-400/rounded/light_mode.svg?raw'
import leftPanelClose from '@material-symbols/svg-400/rounded/left_panel_close.svg?raw'
import leftPanelOpen from '@material-symbols/svg-400/rounded/left_panel_open.svg?raw'
import logout from '@material-symbols/svg-400/rounded/logout.svg?raw'
import mail from '@material-symbols/svg-400/rounded/mail.svg?raw'
import manageAccounts from '@material-symbols/svg-400/rounded/manage_accounts.svg?raw'
import menu from '@material-symbols/svg-400/rounded/menu.svg?raw'
import person from '@material-symbols/svg-400/rounded/person.svg?raw'
import personAdd from '@material-symbols/svg-400/rounded/person_add.svg?raw'
import personRemove from '@material-symbols/svg-400/rounded/person_remove.svg?raw'
import phone from '@material-symbols/svg-400/rounded/phone_enabled.svg?raw'
import publish from '@material-symbols/svg-400/rounded/publish.svg?raw'
import refresh from '@material-symbols/svg-400/rounded/refresh.svg?raw'
import schedule from '@material-symbols/svg-400/rounded/schedule.svg?raw'
import search from '@material-symbols/svg-400/rounded/search.svg?raw'
import summarize from '@material-symbols/svg-400/rounded/summarize.svg?raw'
import switchAccount from '@material-symbols/svg-400/rounded/switch_account.svg?raw'
import syncAlt from '@material-symbols/svg-400/rounded/sync_alt.svg?raw'
import taskAlt from '@material-symbols/svg-400/rounded/task_alt.svg?raw'
import today from '@material-symbols/svg-400/rounded/today.svg?raw'
import unarchive from '@material-symbols/svg-400/rounded/unarchive.svg?raw'
import visibility from '@material-symbols/svg-400/rounded/visibility.svg?raw'
import warning from '@material-symbols/svg-400/rounded/warning.svg?raw'

const icons: Readonly<Record<string, string>> = {
  account_circle: accountCircle,
  add,
  add_task: addTask,
  archive,
  arrow_back: arrowBack,
  arrow_downward: arrowDownward,
  arrow_upward: arrowUpward,
  auto_awesome: autoAwesome,
  check,
  checklist,
  close,
  dashboard,
  dark_mode: darkMode,
  delete: deleteIcon,
  delete_forever: deleteForever,
  edit,
  event_busy: eventBusy,
  expand_more: expandMore,
  filter_list_off: filterListOff,
  folder,
  group,
  group_add: groupAdd,
  key,
  light_mode: lightMode,
  left_panel_close: leftPanelClose,
  left_panel_open: leftPanelOpen,
  logout,
  mail,
  manage_accounts: manageAccounts,
  menu,
  person,
  person_add: personAdd,
  person_remove: personRemove,
  phone,
  publish,
  refresh,
  schedule,
  search,
  summarize,
  switch_account: switchAccount,
  sync_alt: syncAlt,
  task_alt: taskAlt,
  today,
  unarchive,
  visibility,
  warning,
}

const props = withDefaults(defineProps<{
  name: string
  size?: number
}>(), {
  size: 20,
})

const iconSvg = computed(() => icons[props.name] ?? '')
</script>

<template>
  <i
    aria-hidden="true"
    class="material-icon"
    :style="{
      width: `${size}px`,
      height: `${size}px`,
    }"
    v-html="iconSvg"
  />
</template>

<style scoped>
.material-icon {
  display: inline-flex;
  flex: 0 0 auto;
}

.material-icon :deep(svg) {
  display: block;
  width: 100%;
  height: 100%;
  fill: currentColor;
}
</style>
