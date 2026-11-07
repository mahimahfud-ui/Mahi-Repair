import { RepairTool } from '../types';

export function getToolMonogram(tool: RepairTool): string {
  const id = tool.id.toLowerCase();
  const name = tool.name.toLowerCase();
  if (id.includes('youtube') || name.includes('youtube')) return 'YT';
  if (id.includes('fb') || id.includes('facebook') || name.includes('facebook')) return 'FB';
  if (id.includes('dns') || name.includes('dns')) return 'DNS';
  if (id.includes('wifi') || id.includes('wi-fi') || name.includes('wi-fi') || name.includes('wifi')) return 'Wi';
  if (id.includes('play') || name.includes('play store')) return 'PS';
  return tool.name.slice(0, 2).toUpperCase();
}

export interface ParsedStep {
  id: string;
  name: string;
  description: string;
  action: string;
}

export function getParsedToolSteps(tool: RepairTool): ParsedStep[] {
  if (tool.rawSignedPayload) {
    try {
      const parsed = JSON.parse(tool.rawSignedPayload);
      if (Array.isArray(parsed.steps) && parsed.steps.length > 0) {
        return parsed.steps.map((s: any, idx: number) => ({
          id: s.id || `step_${idx}`,
          name: s.name || s.action || `Step ${idx + 1}`,
          description: s.description || 'Verified native diagnostic action',
          action: s.action || 'CHECK',
        }));
      }
    } catch {
      // Fallback
    }
  }

  // Fallback steps for bundled tools
  if (tool.id.includes('youtube')) {
    return [
      { id: '1', name: 'Verify internet', description: 'Checks network connection & route', action: 'CHECK_INTERNET' },
      { id: '2', name: 'Resolve video streaming endpoint', description: 'Resolves googlevideo.com', action: 'DNS_LOOKUP' },
      { id: '3', name: 'Probe YouTube server', description: 'Tests latency & HTTPS TLS connection', action: 'HTTPS_PROBE' },
      { id: '4', name: 'Check app installed', description: 'Confirms com.google.android.youtube exists', action: 'CHECK_APP_INSTALLED' },
      { id: '5', name: 'Cache cleaning guidance', description: 'Guidance to clear temporary cache', action: 'SHOW_GUIDE' },
      { id: '6', name: 'Ask user confirmation', description: 'Prompts to launch cleaned app', action: 'ASK_USER_CONFIRMATION' },
      { id: '7', name: 'Launch YouTube app', description: 'Starts YouTube activity', action: 'OPEN_APP' },
    ];
  }
  if (tool.id.includes('fb') || tool.id.includes('facebook')) {
    return [
      { id: '1', name: 'Verify internet', description: 'Verifies network interface', action: 'CHECK_INTERNET' },
      { id: '2', name: 'Probe Facebook server', description: 'Tests latency to facebook.com', action: 'HTTPS_PROBE' },
      { id: '3', name: 'Check Facebook app', description: 'Checks package com.facebook.katana', action: 'CHECK_APP_INSTALLED' },
      { id: '4', name: 'Show troubleshooting guide', description: 'Step-by-step reinstall walkthrough', action: 'SHOW_GUIDE' },
    ];
  }
  if (tool.id.includes('dns')) {
    return [
      { id: '1', name: 'DNS resolution lookup', description: 'Resolves google.com & public hosts', action: 'DNS_LOOKUP' },
      { id: '2', name: 'Verify DNS resolver integrity', description: 'Validates primary DNS server route', action: 'VERIFY_DNS' },
      { id: '3', name: 'Verify internet connectivity', description: 'Tests end-to-end route connectivity', action: 'VERIFY_INTERNET' },
    ];
  }
  if (tool.id.includes('wifi')) {
    return [
      { id: '1', name: 'Check internet status', description: 'Checks Wi-Fi transport validation', action: 'CHECK_INTERNET' },
      { id: '2', name: 'Captive portal guidance', description: 'Troubleshoots login gateway', action: 'SHOW_GUIDE' },
      { id: '3', name: 'Open Android Wi-Fi settings', description: 'Opens system wireless settings', action: 'OPEN_ANDROID_SETTINGS' },
      { id: '4', name: 'Verify post-repair connection', description: 'Validates restored internet access', action: 'VERIFY_INTERNET' },
    ];
  }
  if (tool.id.includes('play')) {
    return [
      { id: '1', name: 'Check internal storage', description: 'Ensures at least 500MB free', action: 'CHECK_STORAGE' },
      { id: '2', name: 'Open Play Store settings', description: 'Deep links to app info for cache clear', action: 'OPEN_APP_DETAILS' },
      { id: '3', name: 'Request notification permission', description: 'Ensures download alerts work', action: 'REQUEST_OWN_APP_PERMISSION' },
    ];
  }

  return [
    { id: '1', name: 'Check connectivity', description: 'Tests active route', action: 'CHECK_INTERNET' },
    { id: '2', name: 'Execute repair action', description: 'Runs allowlisted diagnostics', action: 'RUN_ACTION' },
    { id: '3', name: 'Post-repair verification', description: 'Validates resolution', action: 'VERIFY_INTERNET' },
  ];
}

export function formatFriendlyDate(timestamp: number): string {
  const diff = Date.now() - timestamp;
  if (diff < 60000) return 'Just now';
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
  if (diff < 86400000) {
    const d = new Date(timestamp);
    return `Today, ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
  }
  if (diff < 172800000) return 'Yesterday';
  return new Date(timestamp).toLocaleDateString([], {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}
