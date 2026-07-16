import{migrateGraph}from"./graph.js";
const key=userId=>`shulkr_flow_draft:${userId||"anonymous"}`;
export const saveDraft=(userId,graph)=>localStorage.setItem(key(userId),JSON.stringify(graph));
export const loadDraft=userId=>{const value=localStorage.getItem(key(userId));return value?migrateGraph(JSON.parse(value)):null;};
export const clearDraft=userId=>localStorage.removeItem(key(userId));
export async function listAutomations(api){const value=await api("/api/automations");if(!Array.isArray(value))throw new Error("Malformed automation list response");return value.map(migrateGraph);}
export async function loadAutomation(api,id){return migrateGraph(await api(`/api/automations/${encodeURIComponent(id)}`));}
export async function createAutomation(api,graph){return migrateGraph(await api("/api/automations",{method:"POST",body:JSON.stringify(graph)}));}
export async function createAutomationFromTemplate(api,templateId,name){return migrateGraph(await api("/api/automations/from-template",{method:"POST",body:JSON.stringify({templateId,name})}));}
export async function publishAutomation(api,graph,metadata={}){return api("/api/library/scripts",{method:"POST",body:JSON.stringify({kind:"automation",graph,...metadata})});}
export async function saveAutomation(api,graph,expectedUpdatedAt){const payload=expectedUpdatedAt?{...graph,expectedUpdatedAt}:graph;return migrateGraph(await api(`/api/automations/${encodeURIComponent(graph.id)}`,{method:"PUT",body:JSON.stringify(payload)}));}
export async function renameAutomation(api,id,name,expectedUpdatedAt){return migrateGraph(await api(`/api/automations/${encodeURIComponent(id)}`,{method:"PATCH",body:JSON.stringify({name,expectedUpdatedAt})}));}
export async function duplicateAutomation(api,id,name){return migrateGraph(await api(`/api/automations/${encodeURIComponent(id)}/duplicate`,{method:"POST",body:JSON.stringify(name?{name}:{})}));}
export async function deleteAutomation(api,id){return api(`/api/automations/${encodeURIComponent(id)}`,{method:"DELETE"});}
export async function executeAutomation(api,id,clientId,confirmPermissions=false){return api(`/api/automations/${encodeURIComponent(id)}/execute`,{method:"POST",body:JSON.stringify({clientId,confirmPermissions})});}
export async function loadAutomationExecution(api,executionId){return api(`/api/automations/executions/${encodeURIComponent(executionId)}`);}
export async function cancelAutomationExecution(api,executionId){return api(`/api/automations/executions/${encodeURIComponent(executionId)}/cancel`,{method:"POST",body:JSON.stringify({})});}
