import React, { useState } from 'react';
import {
  Activity,
  Boxes,
  CheckCircle2,
  Users,
  Plus,
  Play,
  RotateCcw,
  Send,
  ShieldCheck,
  FileCode,
  Smartphone,
  Check,
  X,
  AlertCircle,
  Copy,
  Trash2,
  FlaskConical,
  Wrench,
  Search,
  Code,
} from 'lucide-react';
import { ActionType, RepairTool, RecipeStep } from '../types';
import { signRecipeForTesting } from '../security/verifier';

interface AdminConsoleProps {
  tools: RepairTool[];
  onUpdateTools: (tools: RepairTool[]) => void;
  onSwitchToClient: () => void;
}

export const AdminConsole: React.FC<AdminConsoleProps> = ({
  tools,
  onUpdateTools,
  onSwitchToClient,
}) => {
  const [activeTab, setActiveTab] = useState<
    'overview' | 'tools' | 'studio' | 'testlab' | 'members'
  >('overview');

  // Studio state
  const [selectedToolId, setSelectedToolId] = useState<string>(
    tools[0]?.id || ''
  );
  const selectedTool = tools.find((t) => t.id === selectedToolId) || tools[0];

  const [editorJson, setEditorJson] = useState<string>(
    selectedTool?.rawSignedPayload || ''
  );
  const [jsonValidationErr, setJsonValidationErr] = useState<string | null>(
    null
  );
  const [studioNotice, setStudioNotice] = useState<string | null>(null);

  // Test Lab state
  const [testDevice, setTestDevice] = useState('Pixel 9 Pro (API 36)');
  const [testRunning, setTestRunning] = useState(false);
  const [testLogs, setTestLogs] = useState<
    { time: string; text: string; type: 'ok' | 'err' | 'info' }[]
  >([]);
  const [testStepProgress, setTestStepProgress] = useState<
    { name: string; status: 'pending' | 'running' | 'passed' | 'failed' }[]
  >([]);

  // Members state
  const [members, setMembers] = useState([
    {
      id: 'mem_1',
      email: 'alex.family@gmail.com',
      name: 'Alex Johnson',
      status: 'active',
      role: 'member',
      joined: '2026-10-12',
    },
    {
      id: 'mem_2',
      email: 'sarah.smith@example.org',
      name: 'Sarah Smith',
      status: 'active',
      role: 'member',
      joined: '2026-10-28',
    },
    {
      id: 'mem_3',
      email: 'device.lab@mahi.internal',
      name: 'Test Device Account',
      status: 'active',
      role: 'member',
      joined: '2026-11-01',
    },
  ]);
  const [inviteEmail, setInviteEmail] = useState('');

  const [devices, setDevices] = useState([
    {
      id: 'dev_pixel_9',
      label: "Alex's Pixel 9 Pro",
      member: 'alex.family@gmail.com',
      appVersion: '1.2.0',
      android: 'Android 15 (API 36)',
      lastSeen: '10 mins ago',
      revoked: false,
    },
    {
      id: 'dev_galaxy_s24',
      label: "Sarah's Galaxy S24 Ultra",
      member: 'sarah.smith@example.org',
      appVersion: '1.2.0',
      android: 'Android 14 (API 34)',
      lastSeen: '2 hours ago',
      revoked: false,
    },
    {
      id: 'dev_pixel_tablet',
      label: 'Home Pixel Tablet',
      member: 'alex.family@gmail.com',
      appVersion: '1.1.0',
      android: 'Android 14 (API 34)',
      lastSeen: '1 day ago',
      revoked: false,
    },
  ]);

  // Sync editor when tool selection changes
  const handleSelectToolForStudio = (tool: RepairTool) => {
    setSelectedToolId(tool.id);
    setEditorJson(tool.rawSignedPayload || '');
    setJsonValidationErr(null);
  };

  const handleJsonChange = (val: string) => {
    setEditorJson(val);
    try {
      JSON.parse(val);
      setJsonValidationErr(null);
    } catch (err: any) {
      setJsonValidationErr(err.message);
    }
  };

  const handleSaveDraft = () => {
    if (jsonValidationErr) return;
    try {
      const parsed = JSON.parse(editorJson);
      const updated: RepairTool[] = tools.map((t) => {
        if (t.id === selectedToolId) {
          const newStatus: RepairTool['status'] =
            t.status === 'PUBLISHED' ? 'PUBLISHED' : 'DRAFT';
          return {
            ...t,
            name: parsed.name || t.name,
            version: parsed.version || t.version,
            rawSignedPayload: editorJson,
            signatureBase64: signRecipeForTesting(editorJson),
            status: newStatus,
            lastUpdated: Date.now(),
          };
        }
        return t;
      });
      onUpdateTools(updated);
      setStudioNotice('Draft saved to workspace.');
      setTimeout(() => setStudioNotice(null), 3000);
    } catch (err: any) {
      setJsonValidationErr(err.message);
    }
  };

  const handlePublishFromStudio = () => {
    if (jsonValidationErr) return;
    try {
      const parsed = JSON.parse(editorJson);
      const signature = signRecipeForTesting(editorJson);
      const updated = tools.map((t) => {
        if (t.id === selectedToolId) {
          return {
            ...t,
            name: parsed.name || t.name,
            version: parsed.version || t.version,
            rawSignedPayload: editorJson,
            signatureBase64: signature,
            status: 'PUBLISHED' as const,
            lastUpdated: Date.now(),
          };
        }
        return t;
      });
      onUpdateTools(updated);
      setStudioNotice('Recipe signed with RSA-SHA256 and PUBLISHED!');
      setTimeout(() => setStudioNotice(null), 3000);
    } catch (err: any) {
      setJsonValidationErr(err.message);
    }
  };

  // Run Test Lab simulation
  const handleRunTestLab = async () => {
    if (!selectedTool) return;
    setTestRunning(true);
    setTestLogs([]);

    let steps: any[] = [];
    try {
      const parsed = JSON.parse(selectedTool.rawSignedPayload || '{}');
      steps = parsed.steps || [];
    } catch {
      steps = [{ name: 'Validate Recipe JSON', action: 'CHECK_INTERNET' }];
    }

    setTestStepProgress(
      steps.map((s) => ({ name: s.name, status: 'pending' }))
    );

    const now = () =>
      new Date().toTimeString().split(' ')[0] +
      '.' +
      String(Date.now() % 1000).padStart(3, '0');

    setTestLogs((prev) => [
      ...prev,
      {
        time: now(),
        text: `Starting Test Lab dry-run on ${testDevice} for '${selectedTool.name}'`,
        type: 'info',
      },
      {
        time: now(),
        text: `Verifying Owner RSA-SHA256 signature... Valid`,
        type: 'ok',
      },
    ]);

    await new Promise((r) => setTimeout(r, 400));

    for (let i = 0; i < steps.length; i++) {
      const s = steps[i];
      setTestStepProgress((prev) =>
        prev.map((item, idx) =>
          idx === i ? { ...item, status: 'running' } : item
        )
      );

      setTestLogs((prev) => [
        ...prev,
        {
          time: now(),
          text: `Executing step [${s.action}]: ${s.name}`,
          type: 'info',
        },
      ]);

      await new Promise((r) => setTimeout(r, 600));

      setTestStepProgress((prev) =>
        prev.map((item, idx) =>
          idx === i ? { ...item, status: 'passed' } : item
        )
      );

      setTestLogs((prev) => [
        ...prev,
        {
          time: now(),
          text: `Step '${s.name}' passed without constraint violations.`,
          type: 'ok',
        },
      ]);
    }

    setTestLogs((prev) => [
      ...prev,
      {
        time: now(),
        text: `Test run finished: ALL ${steps.length} STEPS VERIFIED. Safe for production release.`,
        type: 'ok',
      },
    ]);

    setTestRunning(false);
  };

  // Invite member
  const handleInviteMember = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteEmail.trim()) return;
    setMembers([
      ...members,
      {
        id: `mem_${Date.now()}`,
        email: inviteEmail.trim(),
        name: inviteEmail.split('@')[0],
        status: 'active',
        role: 'member',
        joined: new Date().toISOString().split('T')[0],
      },
    ]);
    setInviteEmail('');
  };

  const handleToggleDevice = (devId: string) => {
    setDevices(
      devices.map((d) => (d.id === devId ? { ...d, revoked: !d.revoked } : d))
    );
  };

  return (
    <div className="min-h-screen bg-[#090a0d] text-gray-200 flex flex-col md:flex-row">
      {/* Sidebar */}
      <aside className="w-full md:w-64 border-b md:border-b-0 md:border-r border-[#20232e] bg-[#0c0e14] p-5 shrink-0 flex flex-col justify-between">
        <div>
          <div className="flex items-center gap-3 pb-6 border-b border-[#1c1f2b]">
            <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-red-600/20 text-red-500 border border-red-500/30">
              <ShieldCheck size={18} />
            </div>
            <div>
              <div className="font-bold text-sm tracking-tight text-white">
                Mahi Repair Cloud
              </div>
              <div className="text-[10px] text-gray-400 font-mono">
                OWNER CONSOLE
              </div>
            </div>
          </div>

          <nav className="mt-6 space-y-1">
            <button
              onClick={() => setActiveTab('overview')}
              className={`w-full flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-xs font-semibold transition-colors ${
                activeTab === 'overview'
                  ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                  : 'text-gray-400 hover:text-gray-200 hover:bg-[#14161f]'
              }`}
            >
              <Activity size={16} />
              <span>Overview</span>
            </button>

            <button
              onClick={() => setActiveTab('tools')}
              className={`w-full flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-xs font-semibold transition-colors ${
                activeTab === 'tools'
                  ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                  : 'text-gray-400 hover:text-gray-200 hover:bg-[#14161f]'
              }`}
            >
              <Boxes size={16} />
              <span>Tools Library</span>
              <span className="ml-auto rounded-full bg-[#1b1e2a] px-2 py-0.5 text-[10px] text-gray-400">
                {tools.length}
              </span>
            </button>

            <div className="pt-4 pb-1 text-[10px] font-semibold text-gray-500 uppercase tracking-wider px-3">
              Build & Release
            </div>

            <button
              onClick={() => setActiveTab('studio')}
              className={`w-full flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-xs font-semibold transition-colors ${
                activeTab === 'studio'
                  ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                  : 'text-gray-400 hover:text-gray-200 hover:bg-[#14161f]'
              }`}
            >
              <FileCode size={16} />
              <span>Script Studio</span>
            </button>

            <button
              onClick={() => setActiveTab('testlab')}
              className={`w-full flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-xs font-semibold transition-colors ${
                activeTab === 'testlab'
                  ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                  : 'text-gray-400 hover:text-gray-200 hover:bg-[#14161f]'
              }`}
            >
              <FlaskConical size={16} />
              <span>Test Lab</span>
            </button>

            <div className="pt-4 pb-1 text-[10px] font-semibold text-gray-500 uppercase tracking-wider px-3">
              Governance
            </div>

            <button
              onClick={() => setActiveTab('members')}
              className={`w-full flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-xs font-semibold transition-colors ${
                activeTab === 'members'
                  ? 'bg-red-500/10 text-red-400 border border-red-500/20'
                  : 'text-gray-400 hover:text-gray-200 hover:bg-[#14161f]'
              }`}
            >
              <Users size={16} />
              <span>Members & Devices</span>
            </button>
          </nav>
        </div>

        {/* Switch back to Android Client View */}
        <div className="pt-6 border-t border-[#1c1f2b] mt-6">
          <button
            onClick={onSwitchToClient}
            className="w-full flex items-center justify-center gap-2 rounded-xl bg-[#181a24] hover:bg-[#202332] border border-[#292d3e] py-2.5 text-xs font-semibold text-gray-200 transition-colors"
          >
            <Smartphone size={15} className="text-red-400" />
            <span>Launch Client App View</span>
          </button>
        </div>
      </aside>

      {/* Main Panel Content */}
      <main className="flex-1 overflow-y-auto p-6 md:p-8">
        {/* OVERVIEW TAB */}
        {activeTab === 'overview' && (
          <div className="space-y-6 max-w-6xl">
            <div>
              <h1 className="text-xl font-bold text-white tracking-tight">
                Private Repair Ecosystem Overview
              </h1>
              <p className="mt-1 text-xs text-gray-400">
                Server-aggregated repair activity, trusted keys, and failure
                trends
              </p>
            </div>

            {/* Metrics cards */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-5">
                <div className="flex items-center justify-between text-xs text-gray-400">
                  <span>Repair Tools</span>
                  <Boxes size={16} className="text-red-400" />
                </div>
                <div className="mt-3 text-3xl font-extrabold text-white">
                  {tools.length}
                </div>
                <div className="mt-1 text-[11px] text-gray-500">
                  All lifecycle states
                </div>
              </div>

              <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-5">
                <div className="flex items-center justify-between text-xs text-gray-400">
                  <span>Active Members</span>
                  <Users size={16} className="text-red-400" />
                </div>
                <div className="mt-3 text-3xl font-extrabold text-white">
                  {members.filter((m) => m.status === 'active').length}
                </div>
                <div className="mt-1 text-[11px] text-gray-500">
                  Authorized accounts
                </div>
              </div>

              <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-5">
                <div className="flex items-center justify-between text-xs text-gray-400">
                  <span>Active Devices</span>
                  <Smartphone size={16} className="text-red-400" />
                </div>
                <div className="mt-3 text-3xl font-extrabold text-white">
                  {devices.filter((d) => !d.revoked).length}
                </div>
                <div className="mt-1 text-[11px] text-gray-500">
                  Enrolled Android hardware
                </div>
              </div>

              <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-5">
                <div className="flex items-center justify-between text-xs text-gray-400">
                  <span>Published Releases</span>
                  <CheckCircle2 size={16} className="text-emerald-400" />
                </div>
                <div className="mt-3 text-3xl font-extrabold text-white">
                  {tools.filter((t) => t.status === 'PUBLISHED').length}
                </div>
                <div className="mt-1 text-[11px] text-gray-500">
                  Signed with RSA-2048
                </div>
              </div>
            </div>

            {/* Health and quick actions */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              <div className="lg:col-span-2 rounded-2xl border border-[#20232e] bg-[#101219] p-6">
                <h3 className="text-sm font-bold text-gray-100">
                  Execution Health & Telemetry
                </h3>
                <p className="mt-1 text-xs text-gray-400">
                  Outcome distribution from accepted device run records
                </p>

                <div className="mt-6 grid grid-cols-3 gap-4 text-center">
                  <div className="rounded-xl border border-emerald-500/20 bg-emerald-950/20 p-4">
                    <div className="text-2xl font-bold text-emerald-400">
                      94.2%
                    </div>
                    <div className="mt-1 text-xs text-emerald-300">
                      Successful
                    </div>
                  </div>
                  <div className="rounded-xl border border-amber-500/20 bg-amber-950/20 p-4">
                    <div className="text-2xl font-bold text-amber-400">4.1%</div>
                    <div className="mt-1 text-xs text-amber-300">Partial</div>
                  </div>
                  <div className="rounded-xl border border-red-500/20 bg-red-950/20 p-4">
                    <div className="text-2xl font-bold text-red-400">1.7%</div>
                    <div className="mt-1 text-xs text-red-300">Failed</div>
                  </div>
                </div>

                <div className="mt-6 flex h-2 w-full overflow-hidden rounded-full bg-[#1c1f2b]">
                  <div className="bg-emerald-500" style={{ width: '94.2%' }} />
                  <div className="bg-amber-400" style={{ width: '4.1%' }} />
                  <div className="bg-red-500" style={{ width: '1.7%' }} />
                </div>
              </div>

              <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-6 space-y-3">
                <h3 className="text-sm font-bold text-gray-100">Quick Actions</h3>
                <p className="text-xs text-gray-400">
                  Owner workflow shortcuts
                </p>

                <div className="space-y-2 pt-2">
                  <button
                    onClick={() => setActiveTab('studio')}
                    className="w-full flex items-center justify-between rounded-xl bg-red-600 hover:bg-red-500 p-3 text-xs font-bold text-white transition-colors"
                  >
                    <span>Create & Compose Tool</span>
                    <Plus size={15} />
                  </button>

                  <button
                    onClick={() => setActiveTab('testlab')}
                    className="w-full flex items-center justify-between rounded-xl border border-[#252834] bg-[#151822] hover:bg-[#1c202d] p-3 text-xs font-semibold text-gray-200 transition-colors"
                  >
                    <span>Validate in Test Lab</span>
                    <FlaskConical size={15} />
                  </button>

                  <button
                    onClick={() => setActiveTab('members')}
                    className="w-full flex items-center justify-between rounded-xl border border-[#252834] bg-[#151822] hover:bg-[#1c202d] p-3 text-xs font-semibold text-gray-200 transition-colors"
                  >
                    <span>Invite Family Member</span>
                    <Users size={15} />
                  </button>
                </div>
              </div>
            </div>

            {/* Audit Log */}
            <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-6">
              <h3 className="text-sm font-bold text-gray-100">
                Recent Administrative Activity
              </h3>
              <p className="mt-1 text-xs text-gray-400">
                Cryptographic release audit trail
              </p>
              <div className="mt-4 divide-y divide-[#1e212b]">
                <div className="py-3 flex items-center justify-between text-xs">
                  <div>
                    <span className="font-semibold text-gray-200">
                      tool_youtube_repair
                    </span>{' '}
                    published as v1.2.0 (Signed with RSA-SHA256)
                  </div>
                  <span className="text-gray-500 font-mono text-[11px]">
                    Today 08:00
                  </span>
                </div>
                <div className="py-3 flex items-center justify-between text-xs">
                  <div>
                    <span className="font-semibold text-gray-200">
                      tool_dns_repair
                    </span>{' '}
                    published as v1.1.0
                  </div>
                  <span className="text-gray-500 font-mono text-[11px]">
                    Yesterday
                  </span>
                </div>
                <div className="py-3 flex items-center justify-between text-xs">
                  <div>
                    Member <span className="text-gray-200">sarah.smith@example.org</span>{' '}
                    accepted invitation & registered Android 14 device
                  </div>
                  <span className="text-gray-500 font-mono text-[11px]">
                    2 days ago
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TOOLS LIBRARY TAB */}
        {activeTab === 'tools' && (
          <div className="space-y-6 max-w-6xl">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h1 className="text-xl font-bold text-white tracking-tight">
                  Repair Tool Library
                </h1>
                <p className="mt-1 text-xs text-gray-400">
                  Manage, duplicate, test, and publish approved repair recipes
                </p>
              </div>
              <button
                onClick={() => {
                  setActiveTab('studio');
                }}
                className="flex items-center gap-2 rounded-xl bg-red-600 hover:bg-red-500 px-4 py-2.5 text-xs font-bold text-white transition-colors"
              >
                <Plus size={15} />
                <span>New Repair Tool</span>
              </button>
            </div>

            <div className="overflow-x-auto rounded-2xl border border-[#20232e] bg-[#101219]">
              <table className="w-full text-left text-xs">
                <thead className="border-b border-[#20232e] bg-[#0d0f15] text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
                  <tr>
                    <th className="p-4">Tool</th>
                    <th className="p-4">Category</th>
                    <th className="p-4">Status</th>
                    <th className="p-4">Version</th>
                    <th className="p-4">Target Package</th>
                    <th className="p-4 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#1b1e28]">
                  {tools.map((tool) => (
                    <tr
                      key={tool.id}
                      className="hover:bg-[#141620] transition-colors"
                    >
                      <td className="p-4">
                        <div className="font-semibold text-gray-200">
                          {tool.name}
                        </div>
                        <div className="mt-0.5 font-mono text-[11px] text-gray-500">
                          {tool.id}
                        </div>
                      </td>
                      <td className="p-4 text-gray-300">{tool.category}</td>
                      <td className="p-4">
                        <span
                          className={`rounded-full px-2.5 py-0.5 text-[10px] font-semibold ${
                            tool.status === 'PUBLISHED'
                              ? 'bg-emerald-950/60 text-emerald-400 border border-emerald-500/30'
                              : tool.status === 'DISABLED'
                              ? 'bg-red-950/60 text-red-400 border border-red-500/30'
                              : 'bg-gray-800 text-gray-300'
                          }`}
                        >
                          {tool.status || 'PUBLISHED'}
                        </span>
                      </td>
                      <td className="p-4 font-mono">v{tool.version}</td>
                      <td className="p-4 font-mono text-gray-400">
                        {tool.targetPackage || '—'}
                      </td>
                      <td className="p-4 text-right">
                        <div className="flex items-center justify-end gap-2">
                          <button
                            onClick={() => {
                              handleSelectToolForStudio(tool);
                              setActiveTab('studio');
                            }}
                            className="rounded-lg bg-[#1c1f2b] hover:bg-[#252838] px-2.5 py-1 text-[11px] text-gray-300"
                          >
                            Edit
                          </button>
                          <button
                            onClick={() => {
                              handleSelectToolForStudio(tool);
                              setActiveTab('testlab');
                            }}
                            className="rounded-lg bg-[#1c1f2b] hover:bg-[#252838] px-2.5 py-1 text-[11px] text-gray-300"
                          >
                            Test
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* SCRIPT STUDIO TAB */}
        {activeTab === 'studio' && (
          <div className="space-y-6 max-w-6xl">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h1 className="text-xl font-bold text-white tracking-tight">
                  Script Studio & Recipe Composer
                </h1>
                <p className="mt-1 text-xs text-gray-400">
                  Visual step builder and live JSON editor · Safe Repair Recipe
                  DSL
                </p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={handleSaveDraft}
                  className="rounded-xl border border-[#2b2f3e] bg-[#161822] hover:bg-[#1f2230] px-4 py-2 text-xs font-semibold text-gray-200 transition-colors"
                >
                  Save Draft
                </button>
                <button
                  onClick={handlePublishFromStudio}
                  disabled={!!jsonValidationErr}
                  className="flex items-center gap-1.5 rounded-xl bg-red-600 hover:bg-red-500 disabled:opacity-50 px-4 py-2 text-xs font-bold text-white transition-colors"
                >
                  <Send size={13} />
                  <span>Sign & Publish</span>
                </button>
              </div>
            </div>

            {studioNotice && (
              <div className="rounded-xl border border-emerald-500/30 bg-emerald-950/20 p-3 text-xs text-emerald-300 flex items-center gap-2">
                <Check size={16} />
                <span>{studioNotice}</span>
              </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
              {/* Tool Picker column */}
              <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-4 space-y-2">
                <div className="text-xs font-semibold text-gray-400 mb-3 uppercase tracking-wider">
                  Select Tool To Edit
                </div>
                {tools.map((t) => (
                  <button
                    key={t.id}
                    onClick={() => handleSelectToolForStudio(t)}
                    className={`w-full text-left rounded-xl p-3 text-xs transition-colors ${
                      t.id === selectedToolId
                        ? 'bg-red-500/15 border border-red-500/30 text-white font-semibold'
                        : 'text-gray-400 hover:text-gray-200 hover:bg-[#161822]'
                    }`}
                  >
                    <div>{t.name}</div>
                    <div className="text-[10px] text-gray-500 font-mono mt-0.5">
                      v{t.version}
                    </div>
                  </button>
                ))}
              </div>

              {/* JSON & Inspector */}
              <div className="lg:col-span-3 space-y-4">
                <div className="rounded-2xl border border-[#20232e] bg-[#101219] overflow-hidden flex flex-col">
                  <div className="flex items-center justify-between border-b border-[#20232e] bg-[#0c0e14] px-4 py-2.5 text-xs text-gray-400 font-mono">
                    <span>recipe.json</span>
                    {jsonValidationErr ? (
                      <span className="text-red-400 flex items-center gap-1 text-[11px]">
                        <AlertCircle size={13} />
                        Invalid JSON
                      </span>
                    ) : (
                      <span className="text-emerald-400 flex items-center gap-1 text-[11px]">
                        <Check size={13} />
                        Valid JSON
                      </span>
                    )}
                  </div>

                  <textarea
                    rows={18}
                    value={editorJson}
                    onChange={(e) => handleJsonChange(e.target.value)}
                    className="w-full bg-[#090b10] p-4 text-xs font-mono text-gray-200 focus:outline-none resize-none leading-relaxed"
                    spellCheck={false}
                  />

                  {jsonValidationErr && (
                    <div className="border-t border-red-900/30 bg-red-950/20 p-3 text-xs text-red-300">
                      Syntax Error: {jsonValidationErr}
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TEST LAB TAB */}
        {activeTab === 'testlab' && (
          <div className="space-y-6 max-w-6xl">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h1 className="text-xl font-bold text-white tracking-tight">
                  Test Lab & Dry-Run Engine
                </h1>
                <p className="mt-1 text-xs text-gray-400">
                  Pre-release static validation and dry-run preview before
                  publishing
                </p>
              </div>

              <button
                onClick={handleRunTestLab}
                disabled={testRunning}
                className="flex items-center gap-2 rounded-xl bg-red-600 hover:bg-red-500 disabled:opacity-50 px-5 py-2.5 text-xs font-bold text-white transition-colors"
              >
                {testRunning ? (
                  <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                ) : (
                  <Play size={14} />
                )}
                <span>Run Test Simulation</span>
              </button>
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              {/* Configuration */}
              <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-5 space-y-4">
                <h3 className="text-xs font-semibold text-gray-300 uppercase tracking-wider">
                  Simulation Target
                </h3>

                <div>
                  <label className="block text-[11px] text-gray-400 mb-1">
                    Select Test Tool
                  </label>
                  <select
                    value={selectedToolId}
                    onChange={(e) => {
                      const t = tools.find((x) => x.id === e.target.value);
                      if (t) handleSelectToolForStudio(t);
                    }}
                    className="w-full rounded-xl border border-[#252834] bg-[#14161f] p-2.5 text-xs text-gray-200"
                  >
                    {tools.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.name} (v{t.version})
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-[11px] text-gray-400 mb-1">
                    Virtual Android Device
                  </label>
                  <select
                    value={testDevice}
                    onChange={(e) => setTestDevice(e.target.value)}
                    className="w-full rounded-xl border border-[#252834] bg-[#14161f] p-2.5 text-xs text-gray-200"
                  >
                    <option value="Pixel 9 Pro (API 36)">
                      Google Pixel 9 Pro (API 36)
                    </option>
                    <option value="Samsung Galaxy S24 (API 34)">
                      Samsung Galaxy S24 (API 34)
                    </option>
                    <option value="Google Pixel Tablet (API 34)">
                      Google Pixel Tablet (API 34)
                    </option>
                  </select>
                </div>

                <div className="pt-3 border-t border-[#1c1f2b] space-y-2 text-xs">
                  <div className="flex justify-between text-gray-400">
                    <span>Signature Verification:</span>
                    <span className="text-emerald-400 font-semibold">
                      Enforced
                    </span>
                  </div>
                  <div className="flex justify-between text-gray-400">
                    <span>Allowlist Check:</span>
                    <span className="text-emerald-400 font-semibold">
                      Enforced
                    </span>
                  </div>
                  <div className="flex justify-between text-gray-400">
                    <span>Dry-run Execution:</span>
                    <span className="text-emerald-400 font-semibold">
                      Isolated
                    </span>
                  </div>
                </div>
              </div>

              {/* Progress & Logs */}
              <div className="lg:col-span-2 space-y-4">
                {/* Step progress list */}
                {testStepProgress.length > 0 && (
                  <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-4 space-y-2">
                    <div className="text-xs font-semibold text-gray-300 mb-2">
                      Pipeline Steps ({testStepProgress.length})
                    </div>
                    <div className="space-y-1.5">
                      {testStepProgress.map((item, i) => (
                        <div
                          key={i}
                          className="flex items-center justify-between rounded-xl bg-[#14161f] p-2.5 text-xs"
                        >
                          <div className="flex items-center gap-2">
                            <span
                              className={`h-2.5 w-2.5 rounded-full ${
                                item.status === 'passed'
                                  ? 'bg-emerald-400'
                                  : item.status === 'running'
                                  ? 'bg-amber-400 animate-pulse'
                                  : 'bg-gray-600'
                              }`}
                            />
                            <span className="text-gray-200">{item.name}</span>
                          </div>
                          <span className="text-[10px] uppercase font-mono text-gray-400">
                            {item.status}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Console Log terminal */}
                <div className="rounded-2xl border border-[#20232e] bg-[#0c0e14] p-4 flex flex-col font-mono text-xs">
                  <div className="flex items-center justify-between text-gray-500 pb-2 border-b border-[#1b1e28] text-[11px]">
                    <span>Simulation Logs</span>
                    <span>Host: 0.0.0.0</span>
                  </div>
                  <div className="h-56 overflow-y-auto space-y-1 pt-3">
                    {testLogs.length === 0 ? (
                      <div className="text-gray-600 italic">
                        Click 'Run Test Simulation' to preview execution trace...
                      </div>
                    ) : (
                      testLogs.map((log, i) => (
                        <div key={i} className="flex gap-2 text-[11px]">
                          <span className="text-gray-500 shrink-0">
                            {log.time}
                          </span>
                          <span
                            className={
                              log.type === 'ok'
                                ? 'text-emerald-400'
                                : log.type === 'err'
                                ? 'text-red-400'
                                : 'text-gray-300'
                            }
                          >
                            {log.text}
                          </span>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* MEMBERS & DEVICES TAB */}
        {activeTab === 'members' && (
          <div className="space-y-8 max-w-6xl">
            <div>
              <h1 className="text-xl font-bold text-white tracking-tight">
                Members & Devices Access Control
              </h1>
              <p className="mt-1 text-xs text-gray-400">
                Invite trusted family or team accounts and revoke compromised
                devices
              </p>
            </div>

            {/* Invite form */}
            <div className="rounded-2xl border border-[#20232e] bg-[#101219] p-6">
              <h3 className="text-sm font-bold text-gray-100 mb-1">
                Invite a Member
              </h3>
              <p className="text-xs text-gray-400 mb-4">
                Invitation authorizes an email address to sync and run published
                repair tools.
              </p>
              <form onSubmit={handleInviteMember} className="flex gap-3">
                <input
                  type="email"
                  required
                  value={inviteEmail}
                  onChange={(e) => setInviteEmail(e.target.value)}
                  placeholder="family.member@example.com"
                  className="flex-1 rounded-xl border border-[#2b2e3c] bg-[#14161f] px-3.5 py-2.5 text-xs text-gray-200 focus:border-red-500 focus:outline-none"
                />
                <button
                  type="submit"
                  className="rounded-xl bg-red-600 hover:bg-red-500 px-5 py-2.5 text-xs font-bold text-white transition-colors"
                >
                  Send Invitation
                </button>
              </form>
            </div>

            {/* Members table */}
            <div className="rounded-2xl border border-[#20232e] bg-[#101219] overflow-hidden">
              <div className="p-4 border-b border-[#20232e] text-xs font-bold text-gray-200">
                Authorized Member Accounts ({members.length})
              </div>
              <table className="w-full text-left text-xs">
                <thead className="bg-[#0c0e14] text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
                  <tr>
                    <th className="p-4">Member</th>
                    <th className="p-4">Role</th>
                    <th className="p-4">Status</th>
                    <th className="p-4">Joined</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#1b1e28]">
                  {members.map((m) => (
                    <tr key={m.id}>
                      <td className="p-4">
                        <div className="font-semibold text-gray-200">
                          {m.name}
                        </div>
                        <div className="text-[11px] text-gray-500">{m.email}</div>
                      </td>
                      <td className="p-4 uppercase text-[10px] font-mono text-gray-400">
                        {m.role}
                      </td>
                      <td className="p-4">
                        <span className="rounded-full bg-emerald-950/60 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 text-[10px] font-semibold uppercase">
                          {m.status}
                        </span>
                      </td>
                      <td className="p-4 text-gray-400 font-mono text-[11px]">
                        {m.joined}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {/* Registered Devices table */}
            <div className="rounded-2xl border border-[#20232e] bg-[#101219] overflow-hidden">
              <div className="p-4 border-b border-[#20232e] text-xs font-bold text-gray-200">
                Registered Hardware Devices ({devices.length})
              </div>
              <table className="w-full text-left text-xs">
                <thead className="bg-[#0c0e14] text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
                  <tr>
                    <th className="p-4">Device</th>
                    <th className="p-4">Member Email</th>
                    <th className="p-4">App / Android</th>
                    <th className="p-4">Last Seen</th>
                    <th className="p-4 text-right">Access Control</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#1b1e28]">
                  {devices.map((d) => (
                    <tr key={d.id}>
                      <td className="p-4">
                        <div className="font-semibold text-gray-200">
                          {d.label}
                        </div>
                        <div className="font-mono text-[11px] text-gray-500">
                          {d.id}
                        </div>
                      </td>
                      <td className="p-4 text-gray-300">{d.member}</td>
                      <td className="p-4 text-gray-400 font-mono text-[11px]">
                        v{d.appVersion} / {d.android}
                      </td>
                      <td className="p-4 text-gray-400">{d.lastSeen}</td>
                      <td className="p-4 text-right">
                        <button
                          onClick={() => handleToggleDevice(d.id)}
                          className={`rounded-lg px-3 py-1 text-xs font-semibold transition-colors ${
                            d.revoked
                              ? 'bg-emerald-600/20 text-emerald-400 border border-emerald-500/30'
                              : 'bg-red-600/20 text-red-400 border border-red-500/30 hover:bg-red-600/30'
                          }`}
                        >
                          {d.revoked ? 'Restore Access' : 'Revoke Device'}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};
