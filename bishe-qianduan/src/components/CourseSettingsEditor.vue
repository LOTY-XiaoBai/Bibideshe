<template>
  <el-form class="course-editor" label-position="top" @submit.prevent>
    <el-form-item label="课程封面"><div class="cover-editor"><img v-if="form.coverUrl" :src="form.coverUrl" alt="课程封面" /><div v-else class="cover-placeholder"><i class="ph ph-image" aria-hidden="true" /><span>为课程添加一张封面</span></div><div><el-upload accept="image/*" :auto-upload="false" :show-file-list="false" :on-change="uploadCover"><el-button :loading="uploading">{{ form.coverUrl ? '更换封面' : '上传封面' }}</el-button></el-upload><el-button v-if="form.coverUrl" text type="danger" @click="form.coverUrl = ''">移除封面</el-button><p>支持 JPG、PNG 等图片，最大 10 MB</p></div></div></el-form-item>
    <el-form-item label="课程名称"><el-input v-model="form.courseName" placeholder="例如：高等数学 A" maxlength="100" show-word-limit /></el-form-item>
    <div class="setting-group"><el-checkbox v-model="form.fixedSchedule">固定授课时间</el-checkbox><div v-if="form.fixedSchedule" class="schedule-row"><el-select v-model="form.classWeekday" placeholder="选择星期"><el-option v-for="(day,i) in weekdays" :key="i" :value="i+1" :label="`星期${day}`" /></el-select><el-time-picker v-model="form.classTime" format="HH:mm" value-format="HH:mm" placeholder="选择上课时间" /></div></div>
    <div class="setting-group"><el-checkbox v-model="form.approvalRequired">学生加入课程需要申请</el-checkbox></div>
    <div class="setting-group"><el-checkbox v-model="limitEnabled">限制课堂人数</el-checkbox><div v-if="limitEnabled" class="schedule-row"><el-input-number v-model="form.maxStudents" :min="Math.max(1,memberCount)" :max="10000" :precision="0" /><span>人<span v-if="memberCount">（已加入 {{ memberCount }} 人）</span></span></div></div>
    <div class="setting-group"><el-checkbox v-model="form.showStudentRanking">允许学生查看排名和同学分数</el-checkbox><p>关闭后，学生端按加入课程的先后顺序展示成员，仅显示本人的分数。</p></div>
    <div class="form-footer"><el-button v-if="cancelable" @click="$emit('cancel')">取消</el-button><el-button type="primary" :loading="saving" :disabled="uploading" @click="save">{{ course?.courseId ? '保存课程设置' : '创建课程' }}</el-button></div>
  </el-form>
</template>
<script setup>
import {reactive,ref,watch} from 'vue'
import {ElMessage} from 'element-plus'
import {uploadImage} from '@/api/file'
import {createCourse} from '@/api/course'
import {updateCourseSettings} from '@/api/courseWorkspace'
const props=defineProps({course:Object,memberCount:{type:Number,default:0},cancelable:Boolean})
const emit=defineEmits(['saved','cancel'])
const weekdays=['一','二','三','四','五','六','日'],saving=ref(false),uploading=ref(false),limitEnabled=ref(false)
const form=reactive({courseName:'',coverUrl:'',fixedSchedule:false,classWeekday:1,classTime:'',approvalRequired:false,maxStudents:50,showStudentRanking:true})
watch(()=>props.course?.courseId,()=>{const c=props.course||{};Object.assign(form,{courseName:c.courseName||'',coverUrl:c.coverUrl||'',fixedSchedule:!!c.fixedSchedule,classWeekday:c.classWeekday||1,classTime:c.classTime||'',approvalRequired:!!c.approvalRequired,maxStudents:c.maxStudents||Math.max(50,props.memberCount),showStudentRanking:c.showStudentRanking!==false});limitEnabled.value=c.maxStudents!=null},{immediate:true})
async function uploadCover(item){const file=item.raw;if(!file)return;if(!file.type.startsWith('image/')||file.size>10*1024*1024){ElMessage.warning('请选择10 MB以内的图片');return}uploading.value=true;try{form.coverUrl=(await uploadImage(file)).data.url}catch{}finally{uploading.value=false}}
async function save(){if(!form.courseName.trim()){ElMessage.warning('请输入课程名称');return}if(form.fixedSchedule&&!form.classTime){ElMessage.warning('请选择授课时间');return}saving.value=true;try{const payload={...form,courseName:form.courseName.trim(),maxStudents:limitEnabled.value?form.maxStudents:null};const result=props.course?.courseId?await updateCourseSettings(props.course.courseId,payload):await createCourse(payload);ElMessage.success(props.course?.courseId?'课程设置已保存':'课程创建成功');emit('saved',result.data)}catch{}finally{saving.value=false}}
</script>
<style scoped>
.cover-editor { display:flex;align-items:center;gap:22px;flex-wrap:wrap; }.cover-editor img,.cover-placeholder { width:240px;height:138px;border-radius:14px;object-fit:cover; }.cover-placeholder { background:linear-gradient(120deg,#e6f6ff,#e8fff9);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:#579ab8; }.cover-placeholder i { font-size:30px; }p { font-size:12px;color:#8b96a6;line-height:1.7;margin:6px 0; }.setting-group { padding:15px 0;border-top:1px solid #f0f3f7; }.schedule-row { display:flex;align-items:center;gap:12px;margin-top:10px;flex-wrap:wrap; }.schedule-row .el-select { width:150px; }.form-footer { display:flex;justify-content:flex-end;gap:12px;margin-top:20px; }
</style>
