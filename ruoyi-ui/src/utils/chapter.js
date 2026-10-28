/**
 * 章节编号 / 标题的统一格式化。
 *
 * 背景：course_chapter 里的章号来源比较杂——手工维护的是「第1章」，视频导入的
 * 可能是「1.1」「3.2」这种小节号，标题里又常常自带一层编号（如「1.1.0 引言」
 * 「第三章 数据链路层」），直接拼成「章号 + 标题」就会出现
 * 「1.1 1.1.0 引言」这类重复编号的脏格式。
 *
 * 这里把「章号 + 标题」归一成固定格式，全站统一展示：
 *   第1章 · 计算机网络 · 概念和功能
 *   第1章 第1节 · 引言
 *   第2章 · 数据通信基础
 */

const CN_DIGITS = { 零: 0, 〇: 0, 一: 1, 二: 2, 两: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9 }

/** 把「一」「十二」「二十一」这类中文数字转成阿拉伯数字 */
function cnToNumber(text) {
  const raw = String(text || '').trim()
  if (!raw) return null
  if (/^\d+$/.test(raw)) return Number(raw)
  if (!/^[零〇一二两三四五六七八九十百]+$/.test(raw)) return null
  if (raw === '十') return 10
  let total = 0
  let section = 0
  let number = 0
  for (const ch of raw) {
    if (ch === '百') {
      section = (section || 1) * 100
      total += section
      section = 0
    } else if (ch === '十') {
      section = (section || 1) * 10
      total += section
      section = 0
    } else {
      number = CN_DIGITS[ch]
      if (number === undefined) return null
      section = number
    }
  }
  return total + section
}

/** 标题里「一层编号」的正则：第X章 / 第X节 / 1.2.3 / （一） / 一、 等 */
const TITLE_PREFIX_PATTERNS = [
  /^第\s*[0-9零〇一二两三四五六七八九十百]+\s*[章节讲部分]\s*[·、,，.．:：\-—_]?\s*/,
  /^\d+(?:\.\d+){1,3}\s*[·、,，.．:：\-—_]?\s*/,
  /^\d+\s*[·、,，.．:：\-—_]\s*(?=\D)/,
  /^[（(]\s*[0-9零〇一二两三四五六七八九十百]+\s*[)）]\s*[·、,，.．:：\-—_]?\s*/,
  /^[零〇一二两三四五六七八九十百]+\s*[、.．]\s*/
]

/**
 * 去掉标题前面自带的编号，可连续剥多层（如「1.1.0 引言」→「引言」）。
 * 剥完为空时返回原值，避免标题被清空。
 */
export function stripChapterTitleNumber(title) {
  const original = String(title || '').trim()
  if (!original) return ''
  let text = original
  for (let round = 0; round < 3; round++) {
    let changed = false
    for (const pattern of TITLE_PREFIX_PATTERNS) {
      const next = text.replace(pattern, '')
      if (next !== text) {
        text = next.trim()
        changed = true
        break
      }
    }
    if (!changed) break
  }
  return text || original
}

/** 解析章号：返回 { chapter, section }，解析不出时 chapter 为 null */
export function parseChapterNo(chapterNo) {
  const raw = String(chapterNo || '').trim()
  if (!raw) return { chapter: null, section: null }

  // 第1章 / 第1章第2节 / 第1章 第2节
  let match = raw.match(/^第\s*([0-9零〇一二两三四五六七八九十百]+)\s*章(?:\s*第?\s*([0-9零〇一二两三四五六七八九十百]+)\s*节)?$/)
  if (match) {
    const chapter = cnToNumber(match[1])
    const section = match[2] ? cnToNumber(match[2]) : null
    if (chapter !== null) return { chapter, section: section || null }
  }

  // 1 / 1.1 / 1.1.0 / 3-2
  match = raw.match(/^(\d+)(?:[.\-](\d+))?(?:[.\-]\d+)?$/)
  if (match) {
    const chapter = Number(match[1])
    const section = match[2] !== undefined ? Number(match[2]) : null
    return { chapter, section: section && section > 0 ? section : null }
  }

  // 纯小节号「1.1.0」但没有章时，用第一段兜底
  match = raw.match(/^(\d+)/)
  if (match) return { chapter: Number(match[1]), section: null }

  return { chapter: null, section: null }
}

/** 整段只是一个编号（如「第一章」「1.1.0」），去掉后不剩内容 */
function isPureNumbering(text) {
  const raw = String(text || '').trim()
  if (!raw) return false
  return /^(?:第\s*[0-9零〇一二两三四五六七八九十百]+\s*[章节讲部分]|\d+(?:[.\-]\d+)*|[（(]\s*[0-9零〇一二两三四五六七八九十百]+\s*[)）]|[零〇一二两三四五六七八九十百]+\s*[、.．])$/.test(raw)
}

/** 把标题按「·」拆段，逐段去编号后再拼回来；纯编号段直接丢掉 */
function normalizeTitleSegments(title) {
  return String(title || '')
    .split(/\s*[·•]\s*/)
    .map(part => part.trim())
    .filter(part => part && !isPureNumbering(part))
    .map(part => stripChapterTitleNumber(part))
    .filter(part => part)
}

/** 章号为空时，从标题开头兜底解析章号 */
function parseChapterNoFromTitle(title) {
  const raw = String(title || '').trim()
  const match = raw.match(/^第\s*([0-9零〇一二两三四五六七八九十百]+)\s*章(?:\s*第?\s*([0-9零〇一二两三四五六七八九十百]+)\s*节)?/)
  if (match) {
    return { chapter: cnToNumber(match[1]), section: match[2] ? cnToNumber(match[2]) : null }
  }
  const decimal = raw.match(/^(\d+)(?:[.\-](\d+))(?:[.\-]\d+)?/)
  if (decimal) {
    return { chapter: Number(decimal[1]), section: Number(decimal[2]) || null }
  }
  return { chapter: null, section: null }
}

/**
 * 统一格式的章节标签。
 * @param {{chapterNo?: string, title?: string}} chapter
 * @returns {string} 如「第1章 · 计算机网络 · 概念和功能」「第1章 第1节 · 引言」
 */
export function formatChapterLabel(chapter) {
  const source = chapter || {}
  const rawNo = String(source.chapterNo || '').trim()
  let { chapter: chapterNumber, section } = parseChapterNo(rawNo)
  // 章号缺失或解析不出时，用标题开头的编号兜底，避免只剩标题
  if (chapterNumber === null) {
    const fromTitle = parseChapterNoFromTitle(source.title)
    chapterNumber = fromTitle.chapter
    section = fromTitle.section
  }
  const titleParts = normalizeTitleSegments(source.title)
  const title = titleParts.join(' · ')

  if (chapterNumber === null) {
    // 章号和标题都没有可用编号：保留原章号，但标题里重复的编号仍然去掉
    return [rawNo, title].filter(Boolean).join(' ').trim()
  }

  const head = section ? `第${chapterNumber}章 第${section}节` : `第${chapterNumber}章`
  // 标题与章号重复（如章号 第1章、标题 第一章）时只保留章号
  if (!title) return head
  return `${head} · ${title}`
}

/** 只保留标题本身（去掉自带的章/节编号），用于面包屑等只需要名称的位置 */
export function cleanChapterTitle(title) {
  return normalizeTitleSegments(title).join(' · ')
}

/** 排序键：先按章号，再按节号，保证下拉框顺序稳定 */
export function chapterSortKey(chapter) {
  const source = chapter || {}
  let { chapter: chapterNumber, section } = parseChapterNo(source.chapterNo)
  if (chapterNumber === null) {
    const fromTitle = parseChapterNoFromTitle(source.title)
    chapterNumber = fromTitle.chapter
    section = fromTitle.section
  }
  return [
    chapterNumber === null ? 9999 : chapterNumber,
    section === null ? 0 : section,
    String(source.title || '')
  ]
}

/** 按章号自然序排序（不改动原数组） */
export function sortChapters(chapters) {
  return (chapters || []).slice().sort((a, b) => {
    const left = chapterSortKey(a)
    const right = chapterSortKey(b)
    if (left[0] !== right[0]) return left[0] - right[0]
    if (left[1] !== right[1]) return left[1] - right[1]
    return String(left[2]).localeCompare(String(right[2]), 'zh-Hans-CN')
  })
}
