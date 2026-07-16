export function createHistory(graph, limit = 100) { return { past: [], present: structuredClone(graph), future: [], limit }; }
export function commit(history, graph) { return { ...history, past: [...history.past, history.present].slice(-history.limit), present: structuredClone(graph), future: [] }; }
export function undo(history) { if (!history.past.length) return history; return { ...history, past: history.past.slice(0, -1), present: history.past.at(-1), future: [history.present, ...history.future] }; }
export function redo(history) { if (!history.future.length) return history; return { ...history, past: [...history.past, history.present].slice(-history.limit), present: history.future[0], future: history.future.slice(1) }; }
