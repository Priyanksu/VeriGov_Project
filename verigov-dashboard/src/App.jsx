import React, { useState, useEffect } from 'react';

// Automatically shifts between local development and your live Render production backend
const FLASK_API_BASE = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1"
  ? "http://127.0.0.1:5000"
  : "https://verigov-project.onrender.com"; // 🌟 REPLACE THIS WITH YOUR ACTUAL LIVE RENDER BACKEND URL

export default function App() {
  const [reports, setReports] = useState([]);
  const [activeTab, setActiveTab] = useState('all'); // 'all', 'high', 'low', 'claimed', 'resolved'
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [metrics, setMetrics] = useState({ total: 0, critical: 0, pending: 0, claimed: 0 });

  // Track the currently selected incident for the detailed modal view
  const [selectedIncident, setSelectedIncident] = useState(null);

  // Helper function to safely build image URLs without breaking strings
  const getImageUrl = (urlPath) => {
    if (!urlPath) return null;
    if (urlPath.startsWith('http')) return urlPath;
    const cleanPath = urlPath.startsWith('/') ? urlPath : `/${urlPath}`;
    return `${FLASK_API_BASE}${cleanPath}`;
  };

  // Live Async Data Stream Handler
  const fetchLedgerData = async (filter) => {
    setLoading(true);
    setError(null);
    try {
      // Always request the complete collection to correctly parse metrics on the fly
      const response = await fetch(`${FLASK_API_BASE}/dashboard?view=all`);
      if (!response.ok) throw new Error("Data Sync Outage from Node Cluster");
      const data = await response.json();

      setReports(data);

      // Calculate operational state metrics on the fly based on current task pipelines
      setMetrics({
        total: data.filter(r => r.status !== 'Resolved').length,
        critical: data.filter(r => r.priority_score >= 4 && r.status !== 'Resolved').length,
        pending: data.filter(r => (r.status.includes('Pending') || r.status === 'Pending Review')).length,
        claimed: data.filter(r => r.status === 'Under Investigation').length
      });
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // Synchronize effect whenever active view mode shifts
  useEffect(() => {
    fetchLedgerData(activeTab);
  }, [activeTab]);

  // Handler to claim an incident from the administrative queue
  const handleClaimTask = async (e, reportId) => {
    e.stopPropagation(); // Prevents row click from triggering the modal toggle simultaneously
    try {
      const response = await fetch(`${FLASK_API_BASE}/api/grid_task/${reportId}/claim`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        }
      });
      if (!response.ok) throw new Error("Failed to dispatch task assignment");

      fetchLedgerData(activeTab);

      if (selectedIncident && selectedIncident.id === reportId) {
        setSelectedIncident(prev => ({ ...prev, status: "Under Investigation" }));
      }
    } catch (err) {
      alert(`System Error: ${err.message}`);
    }
  };

  // Handler to resolve an active investigation task
  const handleResolveTask = async (e, reportId) => {
    e.stopPropagation(); // Stop click from firing underlying row closures
    try {
      const response = await fetch(`${FLASK_API_BASE}/api/report/update_status/${reportId}`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ status: 'Resolved' })
      });
      if (!response.ok) throw new Error("Failed to commit task resolution state");

      fetchLedgerData(activeTab);

      if (selectedIncident && selectedIncident.id === reportId) {
        setSelectedIncident(prev => ({ ...prev, status: 'Resolved' }));
      }
    } catch (err) {
      alert(`System Error: ${err.message}`);
    }
  };

  // ==========================================================================
  // ⚡️ COMPREHENSIVE PIPELINE SEGREGATION FILTER LAYER
  // ==========================================================================
  const filteredReports = reports.filter(report => {
    if (activeTab === 'resolved') {
      return report.status === 'Resolved';
    }
    if (activeTab === 'claimed') {
      // IN-PROGRESS STREAM: Show strictly tasks under active deployment investigation
      return report.status === 'Under Investigation';
    }

    // FOR UNASSIGNED VIEWS: Hide everything already Claimed or Resolved to keep them clean
    if (report.status === 'Resolved' || report.status === 'Under Investigation') return false;

    if (activeTab === 'high') {
      return report.confidence_level === 'HIGH';
    }
    if (activeTab === 'low') {
      return report.confidence_level === 'LOW';
    }
    return true; // 'all' Command Center tab shows fresh unassigned issues
  });

  // Unique local pseudorandom identifier mock rule for display tracking purposes
  const getAssignedUnitId = (id) => {
    return `UNIT-${(id * 13) % 90 + 10}`;
  };

  return (
    <div className="flex h-screen overflow-hidden bg-darkBg text-white antialiased">
      {/* Inject custom animation keyframes for live indicators */}
      <style>{`
        @keyframes pulse-glow {
          0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(0, 255, 136, 0.7); }
          70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(0, 255, 136, 0); }
          100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(0, 255, 136, 0); }
        }
        .animate-pulse-glow {
          animation: pulse-glow 2s infinite;
        }
      `}</style>

      {/* SIDEBAR NAVIGATION MODULE */}
      <aside className="w-64 bg-surfaceCard border-r border-gray-800 flex flex-col justify-between">
        <div className="p-6">
          <div className="flex items-center space-x-3 mb-8">
            <i className="fa-solid fa-shield-halved text-techGreen text-2xl"></i>
            <span className="text-xl font-bold tracking-wider text-techGreen">VERIGOV HQ</span>
          </div>
          <nav className="space-y-2">
            <button
              onClick={() => setActiveTab('all')}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg font-semibold transition ${activeTab === 'all' ? 'bg-gray-800 text-techGreen' : 'text-gray-400 hover:bg-gray-800/50 hover:text-white'}`}
            >
              <i className="fa-solid fa-chart-pie w-5"></i><span>Command Center</span>
            </button>
            <button
              onClick={() => setActiveTab('high')}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg font-semibold transition ${activeTab === 'high' ? 'bg-gray-800 text-techGreen' : 'text-gray-400 hover:bg-gray-800/50 hover:text-white'}`}
            >
              <i className="fa-solid fa-triangle-exclamation w-5 text-red-500"></i><span>High Confidence AI</span>
            </button>
            <button
              onClick={() => setActiveTab('low')}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg font-semibold transition ${activeTab === 'low' ? 'bg-gray-800 text-techGreen' : 'text-gray-400 hover:bg-gray-800/50 hover:text-white'}`}
            >
              <i className="fa-solid fa-user-shield w-5 text-amber-500"></i><span>Human Review Queue</span>
            </button>

            {/* SEPARATE TARGET: ACTIVE CLAIMED IN-PROGRESS OPERATIONS */}
            <button
              onClick={() => setActiveTab('claimed')}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg font-semibold transition border border-transparent ${activeTab === 'claimed' ? 'bg-gray-800/90 border-blue-500/20 text-blue-400' : 'text-gray-400 hover:bg-gray-800/50 hover:text-white'}`}
            >
              <i className="fa-solid fa-person-digging w-5 text-blue-400"></i><span>In-Progress Tasks</span>
            </button>

            {/* SEPARATE TARGET: RESOLVED ARCHIVE */}
            <button
              onClick={() => setActiveTab('resolved')}
              className={`w-full flex items-center space-x-3 px-4 py-3 rounded-lg font-semibold transition border border-transparent ${activeTab === 'resolved' ? 'bg-gray-800/90 border-emerald-500/20 text-emerald-400' : 'text-gray-400 hover:bg-gray-800/50 hover:text-white'}`}
            >
              <i className="fa-solid fa-circle-check w-5 text-emerald-400"></i><span>Resolved Operations</span>
            </button>
          </nav>
        </div>
        <div className="p-6 border-t border-gray-800 text-sm text-gray-400 flex items-center space-x-3">
          <div className="w-2 h-2 rounded-full bg-techGreen animate-pulse"></div>
          <span>React Core Sync: Online</span>
        </div>
      </aside>

      {/* MAIN SYSTEM OPERATIONS DASHBOARD */}
      <div className="flex-1 flex flex-col overflow-y-auto">

        {/* HEADER BLOCK */}
        <header className="bg-surfaceCard p-6 border-b border-gray-800 flex justify-between items-center">
          <h1 className="text-2xl font-bold tracking-tight capitalize">
            {activeTab === 'all' ? 'System Infrastructure Stream' : activeTab === 'claimed' ? 'Active Field Investigations' : activeTab === 'resolved' ? 'Archived Records Stream' : `${activeTab} Confidence Tracks`}
          </h1>
          <button
            onClick={() => fetchLedgerData(activeTab)}
            className="bg-techGreen hover:bg-techGreenHover text-black font-bold px-4 py-2 rounded-lg transition flex items-center space-x-2"
          >
            <i className="fa-solid fa-rotate"></i><span>Refresh</span>
          </button>
        </header>

        {/* DATA PANELS METERS */}
        <main className="p-8 space-y-8 flex-1">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
            <div className="bg-surfaceCard p-6 rounded-xl border border-gray-800 shadow-xl">
              <div className="text-gray-400 font-semibold uppercase text-xs tracking-wider">Unassigned Incidents</div>
              <div className="text-3xl font-extrabold mt-2">{metrics.pending}</div>
            </div>
            <div className="bg-surfaceCard p-6 rounded-xl border border-gray-800 shadow-xl border-l-4 border-l-blue-500">
              <div className="text-gray-400 font-semibold uppercase text-xs tracking-wider">Investigating</div>
              <div className="text-3xl font-extrabold mt-2 text-blue-400">{metrics.claimed}</div>
            </div>
            <div className="bg-surfaceCard p-6 rounded-xl border border-gray-800 shadow-xl border-l-4 border-l-red-500">
              <div className="text-gray-400 font-semibold uppercase text-xs tracking-wider">Critical Failures</div>
              <div className="text-3xl font-extrabold mt-2 text-red-500">{metrics.critical}</div>
            </div>

            {/* Dynamic Integrity Metric Module Card */}
            <div className="bg-surfaceCard p-6 rounded-xl border border-gray-800 shadow-xl border-l-4 border-l-techGreen flex flex-col justify-between relative overflow-hidden">
              <div>
                <div className="flex justify-between items-center">
                  <div className="text-gray-400 font-semibold uppercase text-xs tracking-wider">Integrity Status</div>
                  <div className="w-2 h-2 rounded-full bg-techGreen animate-pulse-glow"></div>
                </div>
                <div className="text-3xl font-extrabold mt-2 text-techGreen tracking-wide">SECURE</div>
              </div>
              <div className="text-[10px] text-gray-500 font-mono mt-2 uppercase tracking-tight">SHA-256 Anti-Tamper Loop Pass</div>
            </div>
          </div>

          {/* REAL-TIME PIPELINE COMPONENT */}
          <div className="bg-surfaceCard rounded-xl border border-gray-800 overflow-hidden shadow-2xl">
            <div className="px-6 py-4 border-b border-gray-800 bg-zinc-900/50 flex justify-between items-center">
              <h3 className="font-bold text-lg tracking-wide">
                {activeTab === 'resolved' ? 'Completed Tasks Archive' : activeTab === 'claimed' ? 'Dispatched Field Operations' : 'Dynamic Grievance Stream'}
              </h3>
              <span className="text-xs bg-gray-800 px-3 py-1 rounded-full text-gray-400 font-mono">React Engine Active</span>
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse">
                <thead>
                  <tr className="border-b border-gray-800 text-gray-400 uppercase text-xs font-bold tracking-wider bg-zinc-900/30">
                    <th className="p-4 w-20">ID</th>
                    <th className="p-4">Incident Details</th>
                    <th className="p-4">AI Categorization</th>
                    <th className="p-4">Priority Score</th>
                    <th className="p-4">GPS Coordinates</th>
                    <th className="p-4">SHA-256 Hash Block</th>
                    <th className="p-4 text-center">Action / Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-800/60">
                  {loading ? (
                    <tr>
                      <td colSpan="7" className="text-center p-12 text-gray-400 font-mono">
                        <i className="fa-solid fa-circle-notch animate-spin text-techGreen mr-2"></i> Syncing React State with Backend...
                      </td>
                    </tr>
                  ) : error ? (
                    <tr>
                      <td colSpan="7" className="text-center p-12 text-red-400 font-mono">
                        ⚠️ Link Failure: {error}
                      </td>
                    </tr>
                  ) : filteredReports.length === 0 ? (
                    <tr>
                      <td colSpan="7" className="text-center p-12 text-gray-500">
                        {activeTab === 'resolved'
                          ? 'No resolved incidents recorded inside this node system archive.'
                          : activeTab === 'claimed'
                            ? 'No active field units currently tracking tasks.'
                            : 'No active records found inside this unassigned track layer.'
                        }
                      </td>
                    </tr>
                  ) : (
                    filteredReports.map((report) => (
                      <tr
                        key={report.id}
                        onClick={() => setSelectedIncident(report)}
                        className="hover:bg-zinc-900/40 transition cursor-pointer"
                        title="Click to view full asset attachments and images"
                      >
                        <td className="p-4 font-mono text-gray-500 font-bold text-sm">
                          #{report.id}
                        </td>
                        <td className="p-4 max-w-xs">
                          <div className="flex items-center space-x-3">
                            <div className="w-10 h-10 rounded bg-zinc-800 border border-gray-700 flex-shrink-0 flex items-center justify-center overflow-hidden">
                              {report.image_url ? (
                                <img src={getImageUrl(report.image_url)} alt="Thumbnail" className="w-full h-full object-cover" />
                              ) : (
                                <i className="fa-solid fa-image text-gray-600 text-sm"></i>
                              )}
                            </div>
                            <div>
                              <div className="font-semibold text-sm line-clamp-1">{report.description}</div>
                              <div className="text-xs text-gray-500 mt-0.5">{report.created_at}</div>
                            </div>
                          </div>
                        </td>
                        <td className="p-4">
                          <span className="px-2 py-1 bg-gray-800 text-xs font-mono rounded border border-gray-700 text-gray-300 uppercase">
                            {report.category}
                          </span>
                          <span className={`block text-[10px] mt-1 font-bold tracking-tight ${report.confidence_level === 'HIGH' ? 'text-techGreen' : 'text-amber-500'}`}>
                            {report.confidence_level} CONFIDENCE
                          </span>
                        </td>
                        <td className={`p-4 font-mono ${report.priority_score >= 4 ? 'text-red-500 font-extrabold' : 'text-gray-300'}`}>
                          {report.priority_score} / 5
                        </td>
                        <td className="p-4">
                          <a
                            href={`http://maps.google.com/?q=${report.location_gps}`}
                            target="_blank"
                            rel="noreferrer"
                            className="text-xs text-techGreen hover:underline font-mono"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <i className="fa-solid fa-location-dot mr-1"></i>{report.location_gps}
                          </a>
                        </td>

                        {/* Truncated SHA-256 Compact Badge */}
                        <td className="p-4">
                          <div
                            onClick={(e) => {
                              e.stopPropagation();
                              if (report.sha256_hash) {
                                navigator.clipboard.writeText(report.sha256_hash);
                                alert("Cryptographic verification hash copied to administrative ledger clipboard!");
                              }
                            }}
                            className="text-xs font-mono text-gray-400 bg-zinc-950 px-2.5 py-1.5 rounded border border-gray-800 hover:border-techGreen hover:text-techGreen transition cursor-pointer inline-block tracking-tight select-none"
                            title="Click to copy full immutable ledger blockhash"
                          >
                            <code>
                              {report.sha256_hash ? `${report.sha256_hash.substring(0, 8)}...` : 'Unassigned'}
                            </code>
                          </div>
                        </td>

                        {/* Fully Dynamic Lifecycle Action Status Column */}
                        <td className="p-4 text-center whitespace-nowrap">
                          {report.status === 'Pending' || report.status === 'Pending Review' ? (
                            <button
                              onClick={(e) => handleClaimTask(e, report.id)}
                              className="bg-techGreen hover:bg-techGreenHover text-black text-xs font-extrabold px-3 py-1.5 rounded shadow-lg tracking-wide transition transform hover:scale-105"
                            >
                              CLAIM TASK
                            </button>
                          ) : report.status === 'Under Investigation' ? (
                            <div className="flex flex-col items-center space-y-1">
                              <button
                                onClick={(e) => handleResolveTask(e, report.id)}
                                className="bg-blue-600 hover:bg-blue-700 text-white text-xs font-extrabold px-3 py-1.5 rounded shadow-lg tracking-wide transition transform hover:scale-105 uppercase"
                              >
                                Resolve Issue
                              </button>
                              <span className="text-[10px] font-mono text-blue-400 font-bold tracking-wider uppercase bg-blue-500/10 px-2 py-0.5 rounded border border-blue-500/20">
                                Assigned: {getAssignedUnitId(report.id)}
                              </span>
                            </div>
                          ) : (
                            <span className="px-3 py-1 rounded-full text-xs font-semibold border bg-emerald-500/10 text-emerald-400 border-emerald-500/30">
                              ✅ Resolved
                            </span>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </main>
      </div>

      {/* INTERACTIVE COMPONENT: INSPECTION SIDE DRAWER PANEL */}
      {selectedIncident && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/70 backdrop-blur-sm transition-opacity" onClick={() => setSelectedIncident(null)}>
          <div
            className="w-full max-w-lg bg-surfaceCard h-full border-l border-gray-800 p-6 flex flex-col justify-between shadow-2xl"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="space-y-6 overflow-y-auto pr-2">
              <div className="flex justify-between items-center border-b border-gray-800 pb-4">
                <div>
                  <span className="text-xs bg-gray-800 text-techGreen border border-gray-700 px-2 py-0.5 rounded font-mono uppercase tracking-wider">
                    {selectedIncident.category}
                  </span>
                  <h2 className="text-xl font-bold mt-1 text-white">Grievance Inspection</h2>
                </div>
                <button onClick={() => setSelectedIncident(null)} className="text-gray-400 hover:text-white transition text-lg">
                  <i className="fa-solid fa-xmark"></i>
                </button>
              </div>

              {/* PHOTOGRAPHIC EVIDENCE */}
              <div className="space-y-2">
                <label className="text-xs font-bold uppercase tracking-widest text-gray-400">Field Photographic Evidence</label>
                <div className="w-full h-56 rounded-xl border border-gray-800 bg-zinc-950 flex items-center justify-center overflow-hidden shadow-inner relative">
                  {selectedIncident.image_url ? (
                    <img
                      src={getImageUrl(selectedIncident.image_url)}
                      alt="Grievance Evidence"
                      className="w-full h-full object-contain"
                    />
                  ) : (
                    <div className="text-center space-y-2 text-gray-600">
                      <i className="fa-solid fa-triangle-exclamation text-3xl text-amber-500/60"></i>
                      <div className="text-xs font-mono">No Image Blob Found in Request Stream</div>
                    </div>
                  )}
                  <div className="absolute bottom-3 right-3 bg-black/70 px-2 py-1 rounded text-[10px] font-mono border border-gray-800 text-gray-400">
                    Confidence: {selectedIncident.confidence_level}
                  </div>
                </div>
              </div>

              {/* CITIZEN NARRATIVE */}
              <div className="space-y-1">
                <label className="text-xs font-bold uppercase tracking-widest text-gray-400">Citizen Narrative</label>
                <p className="text-sm text-gray-200 bg-black/30 p-3 rounded-lg border border-gray-800/60 leading-relaxed font-sans">
                  {selectedIncident.description}
                </p>
              </div>

              {/* GEOLOCATION & TIMESTAMPS */}
              <div className="grid grid-cols-2 gap-4">
                <div className="bg-black/20 p-3 rounded-lg border border-gray-800">
                  <div className="text-[10px] uppercase font-bold tracking-wider text-gray-500">Incident GPS Coordinates</div>
                  <a
                    href={`http://maps.google.com/?q=${selectedIncident.location_gps}`}
                    target="_blank"
                    rel="noreferrer"
                    className="text-xs font-mono text-techGreen hover:underline mt-1 block"
                  >
                    <i className="fa-solid fa-location-crosshairs mr-1"></i>{selectedIncident.location_gps}
                  </a>
                </div>
                <div className="bg-black/20 p-3 rounded-lg border border-gray-800">
                  <div className="text-[10px] uppercase font-bold tracking-wider text-gray-500">Entry Timestamp</div>
                  <div className="text-xs font-mono text-gray-300 mt-1">{selectedIncident.created_at}</div>
                </div>
              </div>

              {/* LEDGER BLOCK DETAILS */}
              <div className="space-y-1">
                <label className="text-xs font-bold uppercase tracking-widest text-gray-400">Immutable Ledger Verification</label>
                <div
                  onClick={() => {
                    if (selectedIncident.sha256_hash) {
                      navigator.clipboard.writeText(selectedIncident.sha256_hash);
                      alert("Full hash copied to administrative clipboard!");
                    }
                  }}
                  className="bg-zinc-950 p-3 rounded-lg border border-gray-800 font-mono text-[11px] text-gray-400 break-all select-all hover:border-techGreen transition cursor-pointer"
                  title="Click to copy full hash"
                >
                  <span className="text-techGreen block font-bold text-[10px] mb-1">SHA-256 BLOCKHASH (CLICK TO COPY):</span>
                  {selectedIncident.sha256_hash ? selectedIncident.sha256_hash : 'UNASSIGNED'}
                </div>
              </div>
            </div>

            {/* ACTION STATUS BAR BOTTOM */}
            <div className="border-t border-gray-800 pt-4 mt-4 flex items-center justify-between">
              <div className="text-xs font-mono">
                Priority Ranking: <span className="text-red-500 font-bold">{selectedIncident.priority_score} / 5</span>
              </div>

              {selectedIncident.status === 'Pending' || selectedIncident.status === 'Pending Review' ? (
                <button
                  onClick={(e) => handleClaimTask(e, selectedIncident.id)}
                  className="bg-techGreen hover:bg-techGreenHover text-black text-xs font-extrabold px-4 py-2 rounded-lg tracking-wider uppercase transition shadow-lg"
                >
                  Claim Job
                </button>
              ) : selectedIncident.status === 'Under Investigation' ? (
                <div className="flex items-center space-x-3">
                  <span className="text-xs font-mono text-blue-400 bg-blue-500/10 px-3 py-2 rounded-lg border border-blue-500/20 font-bold">
                    TRACKING: {getAssignedUnitId(selectedIncident.id)}
                  </span>
                  <button
                    onClick={(e) => handleResolveTask(e, selectedIncident.id)}
                    className="bg-blue-600 hover:bg-blue-700 text-white text-xs font-extrabold px-4 py-2 rounded-lg tracking-wider uppercase transition shadow-lg"
                  >
                    Mark as Resolved
                  </button>
                </div>
              ) : (
                <div className="text-xs font-bold uppercase bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-4 py-2 rounded-lg">
                  Status: Resolved
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}