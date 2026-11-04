import request from './request'
export const courseOverview=id=>request.get(`/api/course/${id}/overview`)
export const updateCourseSettings=(id,data)=>request.put(`/api/course/${id}/settings`,data)
export const setCourseRanking=(id,visible)=>request.put(`/api/course/${id}/ranking`,{visible})
export const courseApplications=id=>request.get(`/api/course/${id}/applications`)
export const dismissCourseApplications=()=>request.post('/api/course/applications/dismiss')
export const courseSummary=id=>request.get(`/api/course/${id}/summary`)
export const courseMaterials=id=>request.get(`/api/course/${id}/materials`)
export const uploadCourseMaterial=(id,file)=>{const body=new FormData();body.append('file',file);return request.post(`/api/course/${id}/materials`,body)}
export const deleteCourseMaterial=(id,materialId)=>request.delete(`/api/course/${id}/materials/${materialId}`)
export const downloadCourseMaterial=(id,materialId)=>request.get(`/api/course/${id}/materials/${materialId}/download`,{responseType:'blob'})
export const savedStudyAnswers=(kind,id)=>request.get(`/api/study/${kind}/${id}/answers`)
export const addStudyMistakes=(source,sourceId,questions)=>request.post('/api/error-book/add-batch',{source,sourceId,questions})
