import { defaultData,getNodeDefinition } from "./nodeRegistry.js";
import { migrateGraphData } from "./migrations.js";
export const GRAPH_FORMAT_VERSION=1;
export const createId=(prefix="id")=>`${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2,8)}`;
export function createGraph(name="Untitled automation"){const now=new Date().toISOString();return{formatVersion:1,id:createId("automation"),name,description:"",createdAt:now,updatedAt:now,nodes:[],edges:[],viewport:{x:0,y:0,zoom:1},generatedCode:""};}
export function createNode(type,position={x:80,y:80}){const definition=getNodeDefinition(type);if(!definition)throw new Error(`Unknown node type: ${type}`);return{id:createId("node"),type,version:definition.version,position:{x:Number(position.x)||0,y:Number(position.y)||0},data:defaultData(type)};}
const touch=graph=>({...graph,updatedAt:new Date().toISOString()});
export const addNode=(graph,type,position)=>touch({...graph,nodes:[...graph.nodes,createNode(type,position)]});
export const updateNode=(graph,id,patch)=>touch({...graph,nodes:graph.nodes.map(n=>n.id===id?{...n,...patch,data:patch.data?{...n.data,...patch.data}:n.data}:n)});
export const removeSelection=(graph,nodeIds=[],edgeIds=[])=>{const nodes=new Set(nodeIds);const edges=new Set(edgeIds);return touch({...graph,nodes:graph.nodes.filter(n=>!nodes.has(n.id)),edges:graph.edges.filter(e=>!edges.has(e.id)&&!nodes.has(e.source)&&!nodes.has(e.target))});};
export const duplicateNodes=(graph,nodeIds)=>{const selected=graph.nodes.filter(n=>nodeIds.includes(n.id));const copies=selected.map(n=>({...structuredClone(n),id:createId("node"),position:{x:n.position.x+32,y:n.position.y+32}}));return touch({...graph,nodes:[...graph.nodes,...copies]});};
export function addEdge(graph,edge){return touch({...graph,edges:[...graph.edges,{id:createId("edge"),...edge}]});}
export const migrateGraph=value=>migrateGraphData(value);
export const serializeGraph=graph=>JSON.stringify(graph,null,2);
export const deserializeGraph=text=>migrateGraph(JSON.parse(text));
