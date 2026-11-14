import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Activity, AlertCircle, ArrowDownToLine, ArrowUpRight, BarChart3, Boxes, CheckCircle2, ChevronRight, CircleHelp, Clock3, Cloud, Database, Factory, FileCheck2, FileSpreadsheet, Gauge, GitBranch, LayoutDashboard, Menu, Package, Play, Plus, RefreshCw, Search, Settings2, ShieldCheck, SlidersHorizontal, UploadCloud, Workflow, X } from 'lucide-react';
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api, DATASETS } from './lib/api';

const nav = [
  { group: 'WORKSPACE', items: [{ id:'overview', label:'Overview', icon:LayoutDashboard },{ id:'uploads', label:'Data ingestion', icon:UploadCloud },{ id:'pipeline', label:'Pipeline runs', icon:Workflow },{ id:'analytics', label:'Analytics', icon:BarChart3 }] },
  { group: 'OPERATIONS', items: [{ id:'batches', label:'Batch registry', icon:Boxes },{ id:'defects', label:'Defect registry', icon:AlertCircle },{ id:'genealogy', label:'Genealogy', icon:GitBranch }] },
  { group: 'SYSTEM', items: [{ id:'connections', label:'Connections & status', icon:Cloud }] }
];
const fmt = v => v == null || v === '' ? '—' : Number.isFinite(Number(v)) ? Number(v).toLocaleString() : String(v);
const key = s => String(s || '').toLowerCase().replaceAll('_',' ');
const statusTone = s => ['COMPLETED','LOADED','SUCCESS','HEALTHY','CONNECTED'].includes(String(s||'').toUpperCase()) ? 'success' : ['FAILED','LOAD_FAILED','ERROR'].includes(String(s||'').toUpperCase()) ? 'danger' : ['PROCESSING','LOADING','RUNNING','RECEIVED','PENDING'].includes(String(s||'').toUpperCase()) ? 'warning' : 'neutral';
function Status({value}) { return <span className={`status ${statusTone(value)}`}><i/>{value || 'UNKNOWN'}</span>; }
function Metric({label,value,sub,icon:Icon,tone='blue'}) { return <div className="metric"><div className={`metric-icon ${tone}`}><Icon size={18}/></div><div className="metric-copy"><span>{label}</span><strong>{value ?? '—'}</strong><small>{sub}</small></div></div>; }
function Empty({title='No data available',detail='Data will appear here after the first successful pipeline run.'}) { return <div className="empty"><Database size={26}/><b>{title}</b><span>{detail}</span></div>; }
function Table({columns,rows,onSelect}) { if (!rows?.length) return <Empty/>; return <div className="table-wrap"><table><thead><tr>{columns.map(c=><th key={c.key}>{c.label}</th>)}</tr></thead><tbody>{rows.map((r,i)=><tr key={r.UPLOAD_ID||r.BATCH_ID||r.DEFECT_ID||r.id||i} onClick={()=>onSelect?.(r)}>{columns.map(c=><td key={c.key}>{c.status?<Status value={r[c.key]}/>:c.render?c.render(r[c.key],r):fmt(r[c.key])}</td>)}</tr>)}</tbody></table></div>; }

export default function App() {
  const [page,setPage] = useState('overview');
  const [mobileNav,setMobileNav] = useState(false);
  const [uploads,setUploads] = useState([]); const [batches,setBatches] = useState([]); const [defects,setDefects] = useState([]);
  const [health,setHealth] = useState(null); const [s3,setS3] = useState(null);
  const [analytics,setAnalytics] = useState({});
  const [analyticsErrors,setAnalyticsErrors] = useState({});
  const [analyticsLoading,setAnalyticsLoading] = useState({});
  const [overview,setOverview] = useState(null);
  const [loading,setLoading] = useState(false); const [error,setError] = useState('');
  const [activeUpload,setActiveUpload] = useState(null); const [pipeline,setPipeline] = useState(null);
  const [search,setSearch] = useState(''); const [showUpload,setShowUpload] = useState(false); const [toast,setToast] = useState('');
  const loadCore = useCallback(async()=>{ setLoading(true); setError(''); try { const [u,b,d,h] = await Promise.allSettled([api.uploads(),api.batches(),api.defects(),api.health()]); if(u.status==='fulfilled') setUploads(Array.isArray(u.value)?u.value:[]); if(b.status==='fulfilled') setBatches(Array.isArray(b.value)?b.value:[]); if(d.status==='fulfilled') setDefects(Array.isArray(d.value)?d.value:[]); if(h.status==='fulfilled') setHealth(h.value); const failed=[u,b,d,h].filter(x=>x.status==='rejected'); if(failed.length) setError(failed[0].reason.message); } finally { setLoading(false); } },[]);
  useEffect(()=>{ loadCore(); },[loadCore]);
  useEffect(()=>{
    if(!activeUpload) return;
    let alive=true;
    let timer;
    const poll=async()=>{
      try {
        const p=await api.pipelineStatus(activeUpload);
        if(!alive) return;
        setPipeline(p);
        const state=String(p?.PIPELINE_STATUS||p?.pipelineStatus||'').toUpperCase();
        if(['COMPLETED','FAILED'].includes(state)) {
          clearInterval(timer);
          await loadCore();
        }
      } catch(e) {
        if(alive) setError(e.message);
      }
    };
    poll();
    timer=setInterval(poll, Number(import.meta.env.VITE_PIPELINE_POLL_MS||3000));
    return ()=>{alive=false;clearInterval(timer)};
  },[activeUpload,loadCore]);
  const refresh = async()=>{ await loadCore(); if(activeUpload){try{setPipeline(await api.pipelineStatus(activeUpload));}catch{}} };
  const showToast = msg=>{setToast(msg);setTimeout(()=>setToast(''),3500)};
  const startPipeline = async id=>{ try{ const p=await api.startPipeline(id); setActiveUpload(id);setPipeline(p);setPage('pipeline');showToast('Pipeline submitted. Live status tracking started.'); }catch(e){setError(e.message);} };
  const retryPipeline = async id=>{try{const p=await api.retryPipeline(id);setActiveUpload(id);setPipeline(p);showToast('Retry submitted.');}catch(e){setError(e.message);}};
  const pageTitle = ({overview:'Manufacturing overview',uploads:'Data ingestion',pipeline:'Pipeline runs',analytics:'Manufacturing analytics',batches:'Batch registry',defects:'Defect registry',genealogy:'Batch genealogy',connections:'Connections & system'})[page];
  const recent = useMemo(()=>uploads.slice(0,6),[uploads]);
  const totalAccepted = uploads.reduce((n,r)=>n+Number(r.ACCEPTED_RECORDS||0),0), totalRejected=uploads.reduce((n,r)=>n+Number(r.REJECTED_RECORDS||0),0);
  const stats = [
    {label:'Registered batches',value:fmt(overview?.totalBatches ?? batches.length),sub:'Analytics view',icon:Boxes,tone:'blue'},
    {label:'Defect records',value:fmt(overview?.totalDefectRecords ?? defects.length),sub:'Distinct defect records',icon:AlertCircle,tone:'orange'},
    {label:'Accepted records',value:fmt(totalAccepted),sub:'Across ETL uploads',icon:FileCheck2,tone:'green'},
    {label:'Rejected records',value:fmt(totalRejected),sub:'Requires data review',icon:AlertCircle,tone:'red'}
  ];
  const filteredUploads = uploads.filter(r=>[r.FILE_NAME,r.DATASET_NAME,r.UPLOAD_ID,r.UPLOAD_STATUS].join(' ').toLowerCase().includes(search.toLowerCase()));
  const getAnalytics = useCallback(async (name, params = {}) => {
    setAnalyticsLoading(p => ({ ...p, [name]: true }));
    setAnalyticsErrors(p => ({ ...p, [name]: '' }));
    try {
      const result = await api.analytics(name, params);
      setAnalytics(p => ({ ...p, [name]: result }));
      if (name === 'overview') setOverview(result);
      return result;
    } catch (e) {
      setAnalyticsErrors(p => ({ ...p, [name]: e.message }));
      return null;
    } finally {
      setAnalyticsLoading(p => ({ ...p, [name]: false }));
    }
  }, []);

  const loadAllAnalytics = useCallback(() => {
    ['overview','yield-loss','defects','production','genealogy','root-causes','rework','suppliers']
      .forEach(name => getAnalytics(name));
  }, [getAnalytics]);

  useEffect(() => {
    if (page === 'analytics') loadAllAnalytics();
    if (page === 'overview') getAnalytics('overview');
    if (page === 'genealogy') getAnalytics('genealogy');
  }, [page, loadAllAnalytics, getAnalytics]);

  return <div className="app-shell">
    <aside className={`sidebar ${mobileNav?'open':''}`}><div className="brand"><div className="brand-mark"><Factory size={21}/></div><div><b>FACTORY<span>SET</span></b><small>MANUFACTURING INTELLIGENCE</small></div><button className="mobile-close" onClick={()=>setMobileNav(false)}><X size={18}/></button></div>
      <div className="workspace-switch"><div className="workspace-avatar">S</div><div><b>Manufacturing workspace</b><small>Production environment</small></div><ChevronRight size={15}/></div>
      {nav.map(group=><div className="nav-group" key={group.group}><div className="nav-label">{group.group}</div>{group.items.map(item=><button key={item.id} className={`nav-item ${page===item.id?'active':''}`} onClick={()=>{setPage(item.id);setMobileNav(false)}}><item.icon size={17}/><span>{item.label}</span>{item.id==='pipeline'&&uploads.some(u=>['PROCESSING','LOADING'].includes(String(u.UPLOAD_STATUS).toUpperCase()))&&<i className="nav-pulse"/>}</button>)}</div>)}
      <div className="sidebar-bottom"><div className="secure-note"><ShieldCheck size={16}/><span>Connected data platform</span><b className={health?'dot-live':'dot-off'}/></div><div className="profile"><div className="profile-avatar">SJ</div><div><b>CAP-39 Operator</b><small>Workspace access</small></div><Settings2 size={16}/></div></div>
    </aside>
    {mobileNav&&<div className="scrim" onClick={()=>setMobileNav(false)}/>}
    <main className="main-area"><header className="topbar"><div className="top-left"><button className="menu-btn" onClick={()=>setMobileNav(true)}><Menu size={20}/></button><div className="breadcrumb">CAP-39 <ChevronRight size={14}/><b>{pageTitle}</b></div></div><div className="top-actions"><div className="environment"><span className="live-dot"/> LIVE WORKSPACE</div><button className="icon-button" title="Refresh data" onClick={refresh}><RefreshCw size={17}/></button><button className="primary-btn small" onClick={()=>setShowUpload(true)}><Plus size={16}/> New upload</button></div></header>
      <div className="content"><div className="page-heading"><div><div className="eyebrow">CAP-39 / OPERATIONS INTELLIGENCE</div><h1>{pageTitle}</h1><p>{page==='overview'?'Monitor yield, quality, production and data pipeline health in one place.':page==='analytics'?'Explore yield loss, defect patterns, process performance and genealogy insights.':page==='uploads'?'Ingest manufacturing CSV datasets and run governed ETL-to-Snowflake pipelines.':page==='pipeline'?'Track background processing and loading progress for every upload.':'Manage manufacturing records and monitor operational data.'}</p></div><div className="heading-actions"><span className="updated"><span className="live-dot"/> Connected workspace</span><button className="outline-btn" onClick={refresh}><RefreshCw size={15}/> Refresh</button></div></div>
      {error&&<div className="alert"><AlertCircle size={17}/><span>{error}</span><button onClick={()=>setError('')}><X size={16}/></button></div>}
      {page==='overview'&&<>
        <div className="hero-banner"><div className="hero-content"><div className="hero-tag"><Activity size={13}/> MANUFACTURING CONTROL CENTER</div><h2>Production quality.<br/><em>Under control.</em></h2><p>Connect shop-floor events, batch genealogy and quality outcomes to uncover where yield is being lost.</p><button className="hero-button" onClick={()=>setPage('analytics')}>Explore analytics <ArrowUpRight size={16}/></button></div><div className="hero-graphic"><div className="orbit orbit-one"/><div className="orbit orbit-two"/><div className="hero-core"><Factory size={38}/></div><div className="float-chip chip-a"><Gauge size={15}/> Yield intelligence</div><div className="float-chip chip-b"><GitBranch size={15}/> Batch genealogy</div><div className="float-chip chip-c"><ShieldCheck size={15}/> Quality traceability</div></div></div>
        <div className="section-head"><div><h2>Operational snapshot</h2><p>Live counts from your CAP-39 application records</p></div><span className="muted-label">{loading?'Refreshing…':'LATEST AVAILABLE DATA'}</span></div>
        <div className="metrics-grid">{stats.map(s=><Metric key={s.label} {...s}/>)}</div>
        <div className="overview-grid"><section className="panel recent-panel"><div className="panel-head"><div><h3>Recent data uploads</h3><p>Latest ingested manufacturing datasets</p></div><button className="text-btn" onClick={()=>setPage('uploads')}>View all <ChevronRight size={15}/></button></div><Table rows={recent} columns={[{key:'FILE_NAME',label:'File name'},{key:'DATASET_NAME',label:'Dataset'},{key:'UPLOADED_AT',label:'Uploaded'},{key:'UPLOAD_STATUS',label:'Status',status:true}]}/></section><section className="panel system-panel"><div className="panel-head"><div><h3>Platform health</h3><p>Service connectivity</p></div><Activity size={17}/></div><div className="health-row"><div className="health-icon"><Database size={17}/></div><div><b>Snowflake</b><small>Analytics warehouse</small></div><Status value={health ? 'HEALTHY' : 'NOT CHECKED'}/></div><div className="health-row"><div className="health-icon"><Cloud size={17}/></div><div><b>Amazon S3</b><small>Raw and processed storage</small></div><button className="mini-action" onClick={async()=>{try{const r=await api.s3Health();setS3(r);showToast('S3 health check completed');}catch(e){setError(e.message)}}}>Test</button></div><div className="system-foot"><ShieldCheck size={15}/> Credentials are managed by the backend environment.</div></section></div>
        <div className="section-head"><div><h2>Pipeline activity</h2><p>Recent upload lifecycle and processing outcomes</p></div><button className="text-btn" onClick={()=>setPage('pipeline')}>Open pipeline monitor <ChevronRight size={15}/></button></div><div className="panel"><Table rows={uploads.slice(0,5)} columns={[{key:'FILE_NAME',label:'Source file'},{key:'TOTAL_RECORDS',label:'Input rows'},{key:'ACCEPTED_RECORDS',label:'Accepted'},{key:'REJECTED_RECORDS',label:'Rejected'},{key:'UPLOAD_STATUS',label:'Upload status',status:true}]}/></div>
      </>}
      {page==='uploads'&&<><div className="upload-toolbar"><div className="search-box"><Search size={16}/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Search file, dataset, status or upload ID"/></div><button className="primary-btn" onClick={()=>setShowUpload(true)}><UploadCloud size={16}/> Upload CSV</button></div><div className="info-strip"><CircleHelp size={17}/><span>Upload a CSV with one of the 13 supported dataset filenames. The backend validates the exact column headers before storing the original file in S3.</span></div><div className="panel"><div className="panel-head"><div><h3>Upload history</h3><p>{filteredUploads.length} records found</p></div><button className="icon-button" onClick={refresh}><RefreshCw size={16}/></button></div><Table rows={filteredUploads} onSelect={r=>{setActiveUpload(r.UPLOAD_ID);setPage('pipeline')}} columns={[{key:'FILE_NAME',label:'File name'},{key:'DATASET_NAME',label:'Dataset'},{key:'UPLOADED_AT',label:'Uploaded at'},{key:'TOTAL_RECORDS',label:'Rows'},{key:'ACCEPTED_RECORDS',label:'Accepted'},{key:'REJECTED_RECORDS',label:'Rejected'},{key:'UPLOAD_STATUS',label:'Status',status:true},{key:'UPLOAD_ID',label:'Upload ID',render:v=><span className="mono">{String(v||'').slice(0,8)}…</span>}]}/></div><div className="dataset-grid">{DATASETS.map(d=><div className="dataset-pill" key={d}><FileSpreadsheet size={15}/>{d}</div>)}</div></>}
      {page==='pipeline'&&<><div className="pipeline-toolbar"><div className="search-box"><Search size={16}/><input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Filter uploads"/></div><span className="muted-label">AUTO-REFRESH EVERY 3 SECONDS</span></div>{activeUpload&&<div className="panel active-run"><div className="panel-head"><div><h3>Selected pipeline run</h3><p className="mono">Upload ID: {activeUpload}</p></div><Status value={pipeline?.PIPELINE_STATUS||pipeline?.pipelineStatus||'LOADING'}/></div><div className="run-steps"><div className={`run-step ${['PROCESSING','COMPLETED'].includes(pipeline?.PROCESSING_STATUS)?'done':''}`}><span>1</span><b>Python / Pandas ETL</b><small>{pipeline?.PROCESSING_STATUS||'PENDING'}</small></div><div className="step-line"/><div className={`run-step ${pipeline?.LOADING_STATUS==='COMPLETED'?'done':''}`}><span>2</span><b>Snowflake load</b><small>{pipeline?.LOADING_STATUS||'PENDING'}</small></div><div className="step-line"/><div className={`run-step ${pipeline?.PIPELINE_STATUS==='COMPLETED'?'done':''}`}><span>3</span><b>Pipeline complete</b><small>{pipeline?.PIPELINE_STATUS||'RUNNING'}</small></div></div>{pipeline?.ERROR_MESSAGE&&<div className="alert danger"><AlertCircle size={16}/>{pipeline.ERROR_MESSAGE}</div>}<div className="run-meta"><span>Current step <b>{pipeline?.CURRENT_STEP||'—'}</b></span><span>Retry count <b>{pipeline?.RETRY_COUNT??0}</b></span><span>Started <b>{fmt(pipeline?.STARTED_AT)}</b></span></div>{pipeline?.PIPELINE_STATUS==='FAILED'&&<button className="primary-btn" onClick={()=>retryPipeline(activeUpload)}><RefreshCw size={15}/> Retry pipeline</button>}</div>}<div className="panel"><div className="panel-head"><div><h3>All pipeline candidates</h3><p>Select an upload to view its pipeline state</p></div></div><Table rows={uploads.filter(r=>[r.FILE_NAME,r.DATASET_NAME,r.UPLOAD_ID].join(' ').toLowerCase().includes(search.toLowerCase()))} onSelect={r=>{setActiveUpload(r.UPLOAD_ID);setPipeline(null)}} columns={[{key:'FILE_NAME',label:'Source file'},{key:'DATASET_NAME',label:'Dataset'},{key:'UPLOAD_STATUS',label:'Upload status',status:true},{key:'ACCEPTED_RECORDS',label:'Accepted'},{key:'REJECTED_RECORDS',label:'Rejected'},{key:'UPLOAD_ID',label:'Upload ID',render:v=><span className="mono">{String(v||'').slice(0,12)}…</span>},{key:'UPLOAD_STATUS',label:'Action',render:(v,r)=><button className="table-action" onClick={e=>{e.stopPropagation();if(v==='RECEIVED')startPipeline(r.UPLOAD_ID);else{setActiveUpload(r.UPLOAD_ID);setPage('pipeline')}}}>{v==='RECEIVED'?<><Play size={13}/> Start</>:<>View run <ChevronRight size={13}/></>}</button>}]}/></div></>}
      {page==='analytics'&&<AnalyticsPanel analytics={analytics} onRetry={getAnalytics} errors={analyticsErrors} loading={analyticsLoading}/>}
      {page==='batches'&&<><div className="panel"><div className="panel-head"><div><h3>Production batch registry</h3><p>Batch-level production records registered through the application</p></div><span className="count-chip">{batches.length} batches</span></div><Table rows={batches} columns={Object.keys(batches[0]||{BATCH_ID:'',PRODUCT_ID:'',PRODUCTION_LINE:'',SHIFT:'',PRODUCED_QUANTITY:'',STATUS:''}).slice(0,8).map(k=>({key:k,label:key(k),status:k==='STATUS'}))}/></div></>}
      {page==='defects'&&<><div className="panel"><div className="panel-head"><div><h3>Defect registry</h3><p>Inspection outcomes and recorded manufacturing defects</p></div><span className="count-chip">{defects.length} defects</span></div><Table rows={defects} columns={Object.keys(defects[0]||{DEFECT_ID:'',BATCH_ID:'',DEFECT_CATEGORY:'',SEVERITY:'',AFFECTED_QUANTITY:'',DETECTED_AT:''}).slice(0,8).map(k=>({key:k,label:key(k)}))}/></div></>}
      {page==='genealogy'&&<GenealogyPanel analytics={analytics} onLoad={(batchId)=>getAnalytics('genealogy',{batchId})} loading={analyticsLoading.genealogy} error={analyticsErrors.genealogy}/>}
      {page==='connections'&&<div className="connections-grid"><ConnectionCard title="Snowflake" subtitle="Structured data, staging, analytics views" icon={Database} status={health ? 'HEALTHY' : 'NOT CHECKED'} detail={health}/><ConnectionCard title="Amazon S3" subtitle="Original uploads and processed artifacts" icon={Cloud} status={s3?.status||s3?.STATUS|| (s3 ? 'SUCCESS' : 'NOT CHECKED')} detail={s3}/><ConnectionCard title="Python ETL" subtitle="Pandas standardization and validation" icon={Activity} status="CHECKED DURING PROCESSING" detail={{note:'Python executable and Pandas are validated by the backend before each ETL run.'}}/></div>}
      <footer className="footer"><span>CAP-39 Manufacturing Yield Loss & Defect Genealogy Analyzer</span><span>Data pipeline: S3 → Python / Pandas → Snowflake → Power BI</span></footer>
      </div>
    </main>
    {showUpload&&<UploadModal onClose={()=>setShowUpload(false)} onUploaded={async r=>{setShowUpload(false);await loadCore();setActiveUpload(r.uploadId);setPage('pipeline');showToast(`Uploaded ${r.fileName}. Start the pipeline when ready.`)}}/>}
    {toast&&<div className="toast"><CheckCircle2 size={17}/>{toast}</div>}
  </div>;
}

function UploadModal({onClose,onUploaded}) { const [file,setFile]=useState(null),[user,setUser]=useState('CAP39_OPERATOR'),[busy,setBusy]=useState(false),[err,setErr]=useState(''); const submit=async e=>{e.preventDefault();if(!file)return;setBusy(true);setErr('');try{const r=await api.upload(file,user);await onUploaded(r);}catch(e){setErr(e.message);}finally{setBusy(false);}};return <div className="modal-backdrop" onMouseDown={e=>e.target===e.currentTarget&&onClose()}><form className="modal" onSubmit={submit}><div className="modal-head"><div><div className="eyebrow">DATA INGESTION</div><h2>Upload manufacturing data</h2><p>Original CSV is stored in S3 and registered in Snowflake.</p></div><button type="button" className="icon-button" onClick={onClose}><X size={18}/></button></div><label className="field-label">CSV file</label><label className="dropzone"><input type="file" accept=".csv,text/csv" onChange={e=>setFile(e.target.files?.[0]||null)}/><UploadCloud size={28}/><b>{file?file.name:'Choose a CSV file'}</b><span>{file?`${(file.size/1024).toFixed(1)} KB`:'or drag and drop · CSV only · max 50 MB'}</span></label><label className="field-label" htmlFor="uploadedBy">Uploaded by</label><input id="uploadedBy" className="form-input" value={user} onChange={e=>setUser(e.target.value)} required/><div className="supported-note"><b>Supported datasets</b><span>{DATASETS.join(' · ')}</span></div>{err&&<div className="alert danger"><AlertCircle size={16}/>{err}</div>}<div className="modal-actions"><button type="button" className="outline-btn" onClick={onClose}>Cancel</button><button className="primary-btn" disabled={!file||busy}>{busy?'Uploading…':<><UploadCloud size={15}/> Upload CSV</>}</button></div></form></div>}

const rowsOf = value => Array.isArray(value) ? value : (Array.isArray(value?.data) ? value.data : []);
const sumField = (rows, field) => rows.reduce((sum, row) => sum + (Number(row?.[field]) || 0), 0);
const groupBySum = (rows, labelField, valueField, outputLabel = 'label', outputValue = 'value') => {
  const grouped = new Map();
  rows.forEach(row => {
    const label = row?.[labelField] ?? 'Unknown';
    grouped.set(label, (grouped.get(label) || 0) + (Number(row?.[valueField]) || 0));
  });
  return [...grouped.entries()].map(([label, value]) => ({ [outputLabel]: label, [outputValue]: value }));
};
const groupByCount = (rows, labelField) => {
  const grouped = new Map();
  rows.forEach(row => {
    const label = row?.[labelField] ?? 'Unknown';
    grouped.set(label, (grouped.get(label) || 0) + 1);
  });
  return [...grouped.entries()].map(([name, count]) => ({ name, count }));
};
const niceNumber = value => value == null ? '—' : Number(value).toLocaleString(undefined, { maximumFractionDigits: 2 });

function AnalyticsPanel({analytics,onRetry,errors={},loading={}}) {
  const ov = analytics.overview || {};
  const yieldRows = rowsOf(analytics['yield-loss']);
  const defectRows = rowsOf(analytics.defects);
  const production = analytics.production || {};
  const batchRows = rowsOf(production.batchPerformance);
  const machineRows = rowsOf(production.machinePerformance);
  const parameterRows = rowsOf(production.processParameters);
  const rootRows = rowsOf(analytics['root-causes']);
  const reworkRows = rowsOf(analytics.rework);
  const supplierRows = rowsOf(analytics.suppliers);

  const yieldByLine = groupBySum(yieldRows, 'PRODUCTION_LINE', 'PRODUCTION_SHORTFALL', 'line', 'shortfall');
  const defectsByCategory = groupByCount(defectRows, 'DEFECT_CATEGORY');
  const productionByDate = groupBySum(batchRows, 'PRODUCTION_DATE', 'PRODUCED_QUANTITY', 'date', 'produced')
    .sort((a,b) => String(a.date).localeCompare(String(b.date)));

  const attainment = ov.averageProductionAttainmentPercent;
  const metrics = [
    {label:'Total batches',value:niceNumber(ov.totalBatches),icon:Boxes,tone:'blue',sub:'Batch performance view'},
    {label:'Production attainment',value:attainment == null ? '—' : `${niceNumber(attainment)}%`,icon:Gauge,tone:'green',sub:'Average batch attainment'},
    {label:'Defect records',value:niceNumber(ov.totalDefectRecords),icon:AlertCircle,tone:'orange',sub:'Distinct defect IDs'},
    {label:'Production shortfall',value:niceNumber(ov.totalProductionShortfall),icon:Activity,tone:'red',sub:'Planned less produced'}
  ];

  const checks = ['overview','yield-loss','defects','production','genealogy','root-causes','rework','suppliers'];

  return <>
    <div className="info-strip"><BarChart3 size={17}/><span>Live manufacturing analytics from the ten Snowflake views through the CAP-39 backend. Root-cause candidates are investigative signals, not confirmed causes.</span></div>
    <div className="metrics-grid analytics-metrics">
      {metrics.map(m=><Metric key={m.label} {...m}/>)}
    </div>
    <div className="metrics-grid analytics-metrics">
      <Metric label="Produced quantity" value={niceNumber(ov.producedQuantity)} sub="From batch performance" icon={Package} tone="blue"/>
      <Metric label="Affected quantity" value={niceNumber(ov.totalAffectedQuantity)} sub="From defect analysis" icon={AlertCircle} tone="orange"/>
      <Metric label="Rework quantity" value={niceNumber(ov.totalReworkQuantity)} sub="From rework analysis" icon={RefreshCw} tone="green"/>
      <Metric label="Machines monitored" value={niceNumber(ov.machineCount)} sub="Machine performance view" icon={Factory} tone="blue"/>
    </div>

    <div className="charts-grid">
      <ChartCard title="Production shortfall by line" subtitle="Sum of planned-to-produced quantity shortfall">
        {yieldByLine.length ? <ResponsiveContainer width="100%" height={250}><BarChart data={yieldByLine}><CartesianGrid strokeDasharray="3 3" vertical={false}/><XAxis dataKey="line"/><YAxis/><Tooltip/><Bar dataKey="shortfall" name="Production shortfall" fill="#d97745" radius={[5,5,0,0]}/></BarChart></ResponsiveContainer> : <Empty title="No yield-loss rows" detail={errors['yield-loss'] || 'No records are available for this view.'}/>}
      </ChartCard>
      <ChartCard title="Defect distribution" subtitle="Number of defect records by category">
        {defectsByCategory.length ? <ResponsiveContainer width="100%" height={250}><PieChart><Pie data={defectsByCategory} dataKey="count" nameKey="name" innerRadius={58} outerRadius={88} paddingAngle={3}>{defectsByCategory.map((x,i)=><Cell key={x.name} fill={['#4263eb','#18a58b','#d97745','#8b5cf6','#d14f67'][i%5]}/>)}</Pie><Tooltip/></PieChart></ResponsiveContainer> : <Empty title="No defect rows" detail={errors.defects || 'No defect records are available.'}/>}
      </ChartCard>
      <ChartCard title="Production trend" subtitle="Produced quantity grouped by production date">
        {productionByDate.length ? <ResponsiveContainer width="100%" height={250}><AreaChart data={productionByDate}><defs><linearGradient id="prodFill" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#4263eb" stopOpacity={0.25}/><stop offset="100%" stopColor="#4263eb" stopOpacity={0}/></linearGradient></defs><CartesianGrid strokeDasharray="3 3" vertical={false}/><XAxis dataKey="date"/><YAxis/><Tooltip/><Area type="monotone" dataKey="produced" name="Produced quantity" stroke="#4263eb" fill="url(#prodFill)"/></AreaChart></ResponsiveContainer> : <Empty title="No production rows" detail={errors.production || 'No batch performance records are available.'}/>}
      </ChartCard>
      <ChartCard title="Root-cause candidates" subtitle="Signals to investigate, not proven causes">
        <RootCauseTable data={rootRows}/>
      </ChartCard>
    </div>

    <div className="panel">
      <div className="panel-head"><div><h3>Machine performance</h3><p>Equipment activity summarized from production events</p></div><span className="count-chip">{machineRows.length} machines</span></div>
      <Table rows={machineRows} columns={['MACHINE_NAME','MACHINE_TYPE','MACHINE_STATUS','TOTAL_EVENTS','BATCHES_PROCESSED','TOTAL_EVENT_QUANTITY'].map(k=>({key:k,label:key(k),status:k==='MACHINE_STATUS'}))}/>
    </div>
    <div className="panel">
      <div className="panel-head"><div><h3>Process parameter analysis</h3><p>Observed measurement ranges by parameter and machine</p></div><span className="count-chip">{parameterRows.length} parameters</span></div>
      <Table rows={parameterRows} columns={['PARAMETER_NAME','UNIT','MACHINE_NAME','MEASUREMENT_COUNT','AVG_PARAMETER_VALUE','MIN_PARAMETER_VALUE','MAX_PARAMETER_VALUE'].map(k=>({key:k,label:key(k)}))}/>
    </div>
    <div className="overview-grid">
      <section className="panel">
        <div className="panel-head"><div><h3>Rework analysis</h3><p>Rework records and outcomes</p></div><span className="count-chip">{reworkRows.length} records</span></div>
        <Table rows={reworkRows} columns={['BATCH_ID','DEFECT_CATEGORY','REWORK_TYPE','REWORK_QUANTITY','REWORK_OUTCOME'].map(k=>({key:k,label:key(k)}))}/>
      </section>
      <section className="panel">
        <div className="panel-head"><div><h3>Supplier quality</h3><p>Material-lot and certificate coverage</p></div><span className="count-chip">{supplierRows.length} suppliers</span></div>
        <Table rows={supplierRows} columns={['SUPPLIER_NAME','COUNTRY','TOTAL_MATERIAL_LOTS','VALID_CERTIFICATE_LOTS','TOTAL_QUANTITY_RECEIVED'].map(k=>({key:k,label:key(k)}))}/>
      </section>
    </div>

    <div className="panel">
      <div className="panel-head"><div><h3>Analytics API status</h3><p>Live response status for all eight analytics endpoints</p></div><button className="icon-button" onClick={()=>checks.forEach(n=>onRetry(n))}><RefreshCw size={15}/></button></div>
      <div className="api-checks">{checks.map(n=><div key={n}><span>{key(n)}</span><Status value={analytics[n] ? 'CONNECTED' : loading[n] ? 'LOADING' : errors[n] ? 'ERROR' : 'NOT LOADED'}/><button className="mini-action" onClick={()=>onRetry(n)}>Retry</button></div>)}</div>
    </div>
  </>;
}

function ChartCard({title,subtitle,children}) {
  return <section className="panel chart-card"><div className="panel-head"><div><h3>{title}</h3><p>{subtitle}</p></div><SlidersHorizontal size={16}/></div>{children}</section>;
}

function RootCauseTable({data}) {
  if (!data.length) return <Empty title="No candidates returned" detail="No candidate records are currently available."/>;

  return <div className="mini-table">{data.slice(0,8).map((r,i)=><div key={`${r.BATCH_ID||'batch'}-${r.MACHINE_ID||'machine'}-${r.PARAMETER_NAME||i}`}><b>{r.PARAMETER_NAME || r.MACHINE_NAME || `Candidate ${i+1}`}</b><span>{[r.BATCH_ID, r.PRODUCTION_LINE, r.SHIFT, r.ROOT_CAUSE_CANDIDATE_STATUS].filter(Boolean).join(' · ')}</span><small>Affected quantity rate: {r.AFFECTED_QUANTITY_RATE_PERCENT ?? '—'}%</small></div>)}</div>;
}

function GenealogyPanel({analytics,onLoad,loading=false,error=''}) {
  const [batchId,setBatchId] = useState('');
  const result = analytics.genealogy || {};
  const genealogyRows = rowsOf(result.batchGenealogy);
  const materialRows = rowsOf(result.materialTraceability);

  return <div className="genealogy-stack">
    <div className="panel">
      <div className="panel-head"><div><h3>Batch genealogy explorer</h3><p>Trace parent-child production relationships and material lots.</p></div><button className="primary-btn small" onClick={()=>onLoad(batchId)} disabled={loading}><RefreshCw size={14}/>{loading?'Loading…':'Load genealogy'}</button></div>
      <div className="upload-toolbar"><div className="search-box"><Search size={16}/><input value={batchId} onChange={e=>setBatchId(e.target.value)} placeholder="Optional batch ID to trace"/></div><span className="muted-label">Leave blank to view all available relationships</span></div>
      {error&&<div className="alert danger"><AlertCircle size={16}/>{error}</div>}
      <div className="section-head"><div><h3>Parent-child relationships</h3><p>{genealogyRows.length} relationships returned</p></div></div>
      <Table rows={genealogyRows} columns={['PARENT_BATCH_ID','PARENT_PRODUCT_ID','CHILD_BATCH_ID','CHILD_PRODUCT_ID','RELATIONSHIP_TYPE','EVENT_TIMESTAMP'].map(k=>({key:k,label:key(k)}))}/>
    </div>
    <div className="panel">
      <div className="panel-head"><div><h3>Material traceability</h3><p>Material lots and suppliers linked to production events</p></div><span className="count-chip">{materialRows.length} records</span></div>
      <Table rows={materialRows} columns={['BATCH_ID','EVENT_TYPE','MATERIAL_LOT_ID','MATERIAL_TYPE','SUPPLIER_NAME','CERTIFICATE_STATUS','EVENT_TIMESTAMP'].map(k=>({key:k,label:key(k)}))}/>
    </div>
  </div>;
}
function ConnectionCard({title,subtitle,icon:Icon,status,detail}){return <div className="panel connection-card"><div className="connection-icon"><Icon size={22}/></div><h3>{title}</h3><p>{subtitle}</p><Status value={status}/><pre>{JSON.stringify(detail||{},null,2)}</pre></div>}
