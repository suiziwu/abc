/**
 * 从学习记录、学科和知识点数据中整理出「学习中课程」卡片数据。
 * 只返回当前存在 status=1（学习中）记录的课程，按最近学习时间倒序。
 */
export function buildLearningCourses(records = [], subjects = [], points = []) {
  const subjectMap = new Map()
  subjects.forEach(subject => {
    if (subject?.id !== undefined && subject?.id !== null) {
      subjectMap.set(String(subject.id), subject)
    }
  })

  const pointsBySubject = new Map()
  const pointSubjectMap = new Map()
  points.forEach(point => {
    if (!point || point.id === undefined || point.id === null) return
    const subjectId = point.subjectId
    if (subjectId === undefined || subjectId === null) return
    const subjectKey = String(subjectId)
    pointSubjectMap.set(String(point.id), subjectKey)
    if (!pointsBySubject.has(subjectKey)) pointsBySubject.set(subjectKey, [])
    pointsBySubject.get(subjectKey).push(point)
  })

  const recordsBySubject = new Map()
  records.forEach(record => {
    if (!record || record.pointId === undefined || record.pointId === null) return
    const subjectKey = pointSubjectMap.get(String(record.pointId))
    if (!subjectKey) return
    if (!recordsBySubject.has(subjectKey)) recordsBySubject.set(subjectKey, [])
    recordsBySubject.get(subjectKey).push(record)
  })

  const courses = []
  recordsBySubject.forEach((subjectRecords, subjectKey) => {
    const learningRecords = subjectRecords.filter(record => String(record.status) === '1')
    if (learningRecords.length === 0) return

    const subject = subjectMap.get(subjectKey) || {}
    const subjectPoints = pointsBySubject.get(subjectKey) || []
    const doneCount = subjectRecords.filter(record => String(record.status) === '2').length
    const total = subjectPoints.length
    const latestRecord = subjectRecords
      .slice()
      .sort((a, b) => timeValue(b) - timeValue(a))[0]

    courses.push({
      id: subject.id ?? Number(subjectKey),
      name: subject.name || `课程 ${subjectKey}`,
      code: subject.code || '',
      description: subject.description || '',
      pointCount: total,
      doneCount,
      learningCount: learningRecords.length,
      progress: total > 0 ? Math.round((doneCount / total) * 100) : 0,
      statusLabel: '学习中',
      updatedAt: latestRecord?.updateTime || latestRecord?.createTime || ''
    })
  })

  return courses.sort((a, b) => timeValue(b) - timeValue(a))
}

function timeValue(item) {
  const time = item?.updatedAt || item?.updateTime || item?.createTime
  if (!time) return 0
  const value = new Date(String(time).replace(/-/g, '/')).getTime()
  return Number.isNaN(value) ? 0 : value
}
