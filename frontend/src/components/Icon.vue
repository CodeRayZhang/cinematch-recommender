<script setup lang="ts">
import {
  ArrowUpRight,
  ArrowRight,
  ArrowLeft,
  Search,
  Plus,
  Check,
  Star,
  X,
  Bookmark,
  Film,
  Sparkles,
  LogOut,
  Settings,
  SlidersHorizontal,
  ChevronLeft,
  ChevronRight,
  Clapperboard,
  LoaderCircle,
  Trash2,
  Pencil,
  CheckCircle2,
} from "@lucide/vue";
const icons = {
  ArrowUpRight,
  ArrowRight,
  ArrowLeft,
  Search,
  Plus,
  Check,
  Star,
  X,
  Bookmark,
  Film,
  Sparkles,
  LogOut,
  Settings,
  SlidersHorizontal,
  ChevronLeft,
  ChevronRight,
  Clapperboard,
  LoaderCircle,
  Trash2,
  Pencil,
  CheckCircle2,
};
defineProps<{ name: keyof typeof icons; size?: number }>();
</script>
<template>
  <component
    :is="icons[name]"
    :size="size || 20"
    :stroke-width="1.65"
    aria-hidden="true"
  />
</template>
