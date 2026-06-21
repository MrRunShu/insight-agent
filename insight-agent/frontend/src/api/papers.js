// REST client for the knowledge-base document endpoints.
const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8123/api'

export const paperFileUrl = (id) => `${API_BASE}/papers/${id}/file`

/** List all papers (each: {id, filename, title, category, pageCount}). */
export async function listPapers() {
  const res = await fetch(`${API_BASE}/papers`)
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  return res.json()
}

/** Upload a PDF into the knowledge base under an optional category. */
export async function uploadPaper(file, category) {
  const fd = new FormData()
  fd.append('file', file)
  if (category) fd.append('category', category)
  const res = await fetch(`${API_BASE}/papers/upload`, { method: 'POST', body: fd })
  return res.json()
}

/** Soft-delete a paper by id. */
export async function deletePaper(id) {
  const res = await fetch(`${API_BASE}/papers/${id}`, { method: 'DELETE' })
  return res.json()
}

/** Change a paper's category. */
export async function recategorizePaper(id, category) {
  const res = await fetch(`${API_BASE}/papers/${id}/category`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ category }),
  })
  return res.json()
}

/** List annotations for a paper. */
export async function listAnnotations(id) {
  const res = await fetch(`${API_BASE}/papers/${id}/annotations`)
  if (!res.ok) return []
  return res.json()
}

/** Create an annotation on a paper. */
export async function createAnnotation(id, payload) {
  const res = await fetch(`${API_BASE}/papers/${id}/annotations`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  return res.json()
}

/** Delete an annotation by its own id. */
export async function deleteAnnotation(annId) {
  await fetch(`${API_BASE}/annotations/${annId}`, { method: 'DELETE' })
}

/** Group a flat paper list into {category: [papers]}. */
export function groupByCategory(papers) {
  const groups = {}
  for (const p of papers) {
    const c = p.category || '未分类'
    ;(groups[c] ||= []).push(p)
  }
  return groups
}
