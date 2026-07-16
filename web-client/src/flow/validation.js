import { compatibleTypes, getNodeDefinition, portsFor } from "./nodeRegistry.js";

const issue = (code, message, context = {}, severity = "error", suggestedFix = "") => ({
  id: `${code}:${context.nodeId || context.edgeId || "graph"}`,
  severity, code, message, nodeId: context.nodeId, edgeId: context.edgeId, suggestedFix
});

const finite = value => typeof value === "number" && Number.isFinite(value);
const port = (definition, id) => portsFor(definition).find(candidate => candidate.id === id);
const executionEdges = graph => (graph.edges || []).filter(edge => edge.dataType === "execution");

export function canConnect(graph, connection) {
  const source = (graph.nodes || []).find(node => node.id === connection.source);
  const target = (graph.nodes || []).find(node => node.id === connection.target);
  if (!source || !target) return { ok: false, message: "Missing source or target node." };
  const sourceDefinition = getNodeDefinition(source.type);
  const targetDefinition = getNodeDefinition(target.type);
  if (!sourceDefinition || !targetDefinition) return { ok: false, message: "Unknown node type." };
  const sourcePort = port(sourceDefinition, connection.sourceHandle);
  const targetPort = port(targetDefinition, connection.targetHandle);
  if (!sourcePort || !targetPort || !sourceDefinition.executionOutputs.concat(sourceDefinition.dataOutputs).includes(sourcePort) || !targetDefinition.executionInputs.concat(targetDefinition.dataInputs).includes(targetPort)) return { ok: false, message: "Unknown connection handle." };
  if (connection.source === connection.target && !sourceDefinition.allowSelfLoop) return { ok: false, message: "This node does not allow self-connections." };
  if (!compatibleTypes(sourcePort.dataType, targetPort.dataType)) return { ok: false, message: `${sourcePort.dataType} cannot connect to ${targetPort.dataType}.` };
  if (graph.edges.some(edge => edge.source === connection.source && edge.sourceHandle === connection.sourceHandle && edge.target === connection.target && edge.targetHandle === connection.targetHandle)) return { ok: false, message: "Duplicate connection." };
  if (!targetPort.multiple && graph.edges.some(edge => edge.target === connection.target && edge.targetHandle === connection.targetHandle)) return { ok: false, message: "This input already has a connection." };
  return { ok: true, dataType: sourcePort.dataType };
}

function validateProperty(node, property) {
  const value = node.data?.[property.key];
  if (property.required && (value === undefined || value === null || value === "")) return issue("missing_property", `${property.label} is required.`, { nodeId: node.id }, "error", `Set ${property.label}.`);
  if (value === undefined || value === null || value === "") return null;
  if (["number", "timeout", "retries"].includes(property.type) && (!finite(Number(value)) || (property.min !== undefined && Number(value) < property.min) || (property.max !== undefined && Number(value) > property.max))) return issue(property.type === "timeout" ? "invalid_timeout" : property.type === "retries" ? "invalid_retries" : "invalid_property_type", `${property.label} must be a number between ${property.min ?? "-∞"} and ${property.max ?? "∞"}.`, { nodeId: node.id }, "error", `Use a value in the allowed range.`);
  if (property.type === "coordinates" && (!value || !finite(Number(value.x)) || !finite(Number(value.y)) || !finite(Number(value.z)))) return issue("invalid_coordinates", `${property.label} must contain finite x, y, and z coordinates.`, { nodeId: node.id }, "error", "Provide numeric x, y, and z values.");
  if (property.type === "block" && !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(String(value))) return issue("invalid_block", "Block ID must use namespace:block format.", { nodeId: node.id }, "error", "Use a valid namespaced block ID such as minecraft:stone.");
  if (property.type === "text" && typeof value !== "string") return issue("invalid_property_type", `${property.label} must be text.`, { nodeId: node.id });
  if (property.type === "boolean" && typeof value !== "boolean") return issue("invalid_property_type", `${property.label} must be true or false.`, { nodeId: node.id });
  return null;
}

function findCycles(graph) {
  const adjacency = new Map((graph.nodes || []).map(node => [node.id, []]));
  for (const edge of executionEdges(graph)) adjacency.get(edge.source)?.push(edge.target);
  const active = new Set(), complete = new Set(), cycles = [];
  const visit = id => { if (active.has(id)) { cycles.push(id); return; } if (complete.has(id)) return; active.add(id); for (const next of adjacency.get(id) || []) visit(next); active.delete(id); complete.add(id); };
  for (const node of graph.nodes || []) visit(node.id);
  return cycles;
}

export function validateGraph(graph) {
  const issues = [];
  if (!graph || typeof graph !== "object" || Array.isArray(graph)) return { valid: false, issues: [issue("malformed_graph", "Graph must be an object.", {}, "error", "Load or create a valid graph.")], errorCount: 1, warningCount: 0 };
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  const edges = Array.isArray(graph.edges) ? graph.edges : [];
  const byId = new Map();
  for (const node of nodes) { if (!node?.id) issues.push(issue("malformed_node", "Node is missing an ID.")); else if (byId.has(node.id)) issues.push(issue("duplicate_node", `Duplicate node ID: ${node.id}.`, { nodeId: node.id })); else byId.set(node.id, node); }
  const starts = nodes.filter(node => node.type === "flow.start");
  if (!starts.length) issues.push(issue("missing_start", "Add one Start node.", {}, "error", "Add a Start node as the graph entry point."));
  if (starts.length > 1) starts.slice(1).forEach(node => issues.push(issue("multiple_starts", "Only one Start node is allowed.", { nodeId: node.id }, "error", "Remove the extra Start node.")));
  for (const node of nodes) {
    const definition = getNodeDefinition(node.type);
    if (!definition) { issues.push(issue("unknown_node", `Unknown node type: ${node.type}.`, { nodeId: node.id }, "error", "Replace this node with a supported node.")); continue; }
    if (node.version !== definition.version) issues.push(issue("unsupported_node_version", `${definition.label} version ${node.version} is unsupported.`, { nodeId: node.id }, "error", `Migrate this node to version ${definition.version}.`));
    if (!node.data || typeof node.data !== "object" || Array.isArray(node.data)) issues.push(issue("invalid_node_data", "Node data must be an object.", { nodeId: node.id }));
    if (!node.position || !finite(Number(node.position.x)) || !finite(Number(node.position.y))) issues.push(issue("invalid_coordinates", "Node position must contain finite coordinates.", { nodeId: node.id }));
    for (const property of definition.properties) { const result = validateProperty(node, property); if (result) issues.push(result); }
  }
  const edgeKeys = new Set();
  for (const edge of edges) {
    const source = byId.get(edge.source), target = byId.get(edge.target);
    if (!source || !target) { issues.push(issue("missing_node_reference", "Edge references a missing node.", { edgeId: edge.id }, "error", "Reconnect or delete this edge.")); continue; }
    const sourceDefinition = getNodeDefinition(source.type), targetDefinition = getNodeDefinition(target.type);
    const sourcePort = sourceDefinition && port(sourceDefinition, edge.sourceHandle), targetPort = targetDefinition && port(targetDefinition, edge.targetHandle);
    if (!sourcePort || !targetPort || !sourceDefinition.executionOutputs.concat(sourceDefinition.dataOutputs).includes(sourcePort) || !targetDefinition.executionInputs.concat(targetDefinition.dataInputs).includes(targetPort)) issues.push(issue("unknown_handle", "Edge references an unknown handle.", { edgeId: edge.id }, "error", "Reconnect this edge using compatible handles."));
    else if (!compatibleTypes(sourcePort.dataType, targetPort.dataType) || edge.dataType !== sourcePort.dataType) issues.push(issue("incompatible_connection", `Cannot connect ${sourcePort.dataType} to ${targetPort.dataType}.`, { edgeId: edge.id }, "error", "Connect ports with compatible data types."));
    if (edge.source === edge.target && !sourceDefinition.allowSelfLoop) issues.push(issue("illegal_self_connection", "Self-connections are not allowed for this node.", { edgeId: edge.id }));
    const key = `${edge.source}:${edge.sourceHandle}:${edge.target}:${edge.targetHandle}`;
    if (edgeKeys.has(key)) issues.push(issue("duplicate_edge", "Duplicate connection.", { edgeId: edge.id }, "error", "Remove the duplicate edge.")); edgeKeys.add(key);
  }
  for (const node of nodes) {
    const definition = getNodeDefinition(node.type); if (!definition) continue;
    const incoming = handle => edges.filter(edge => edge.target === node.id && edge.targetHandle === handle);
    const outgoing = handle => edges.filter(edge => edge.source === node.id && edge.sourceHandle === handle);
    for (const input of [...definition.executionInputs, ...definition.dataInputs]) if (input.required && !incoming(input.id).length) issues.push(issue("missing_required_input", `${input.label} input is required.`, { nodeId: node.id }, "error", `Connect a value to ${input.label}.`));
    for (const output of definition.executionOutputs) if (output.required && !outgoing(output.id).length) issues.push(issue("missing_required_output", `${output.label} output requires a connection.`, { nodeId: node.id }, "error", `Connect ${output.label} to the next step.`));
    for (const output of definition.executionOutputs.filter(item => !item.required)) if (!outgoing(output.id).length) issues.push(issue("dead_end_branch", `${output.label} branch is not connected.`, { nodeId: node.id }, "warning", `Connect or intentionally terminate the ${output.label} branch.`));
    if (["movement.walk_to_target", "action.look_at_target", "action.mine_block"].includes(node.type) && !incoming("target-in").length) issues.push(issue("missing_target", "Action requires a usable target input.", { nodeId: node.id }, "error", "Connect a target-producing node."));
  }
  if (starts.length) {
    const reachable = new Set([starts[0].id]); let changed = true; while (changed) { changed = false; for (const edge of executionEdges(graph)) if (reachable.has(edge.source) && !reachable.has(edge.target)) { reachable.add(edge.target); changed = true; } }
    for (const node of nodes) if (!reachable.has(node.id)) issues.push(issue("unreachable_node", `${getNodeDefinition(node.type)?.label || node.type} cannot be reached from Start.`, { nodeId: node.id }, "error", "Connect this node to the execution flow or remove it."));
  }
  for (const cycleNode of findCycles(graph)) { const node = byId.get(cycleNode); if (node?.type !== "flow.repeat") issues.push(issue("unsafe_execution_cycle", "Execution cycle has no safety limit.", { nodeId: cycleNode }, "error", "Use a Repeat node with a finite iteration limit.")); }
  const assigned = new Set();
  for (const node of nodes) { if (node.type === "world.store_target") assigned.add(String(node.data?.name || "")); if (node.data?.variable && !assigned.has(String(node.data.variable))) issues.push(issue("undefined_variable", `Variable ${node.data.variable} is read before assignment.`, { nodeId: node.id })); }
  return { valid: !issues.some(item => item.severity === "error"), issues, errorCount: issues.filter(item => item.severity === "error").length, warningCount: issues.filter(item => item.severity === "warning").length };
}
