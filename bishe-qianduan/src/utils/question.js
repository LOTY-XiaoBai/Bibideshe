export const QUESTION_TYPES = {
  CHOICE: 1,
  JUDGE: 2,
  ANSWER: 3,
}

export function questionTypeLabel(type) {
  switch (Number(type)) {
    case QUESTION_TYPES.CHOICE:
      return '选择题'
    case QUESTION_TYPES.JUDGE:
      return '判断题'
    case QUESTION_TYPES.ANSWER:
      return '解答题'
    default:
      return '未知题型'
  }
}

export function auditStatusLabel(status, isPublic) {
  if (!isPublic) return '私有题目'
  if (Number(status) === 1) return '已公开'
  return '待审核'
}
