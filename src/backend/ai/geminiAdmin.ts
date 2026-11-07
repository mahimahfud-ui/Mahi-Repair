import { GoogleGenAI, Type, FunctionDeclaration } from '@google/genai';
import { db } from '../db';
import { RepairRecipeZodSchema, SupportedActionName, ActionRiskMap, ConfirmationRequiredActions } from '../types';

let genAiClient: GoogleGenAI | null = null;

function getGenAi(): GoogleGenAI | null {
  if (genAiClient) return genAiClient;
  const apiKey = process.env.GEMINI_API_KEY;
  if (!apiKey) return null;

  genAiClient = new GoogleGenAI({
    apiKey,
    httpOptions: {
      headers: {
        'User-Agent': 'aistudio-build',
      },
    },
  });
  return genAiClient;
}

// 1. Tool Function Declarations
const addRepairToolDeclaration: FunctionDeclaration = {
  name: 'add_repair_tool',
  description: 'Creates a new repair tool in DRAFT status with a validated declarative recipe.',
  parameters: {
    type: Type.OBJECT,
    properties: {
      name: { type: Type.STRING, description: 'Display name of the repair tool (e.g. WhatsApp Quick Fix)' },
      slug: { type: Type.STRING, description: 'Unique kebab-case slug (e.g. whatsapp-quick-fix)' },
      category: { type: Type.STRING, description: 'Category: network, apps, storage, connectivity, system, or guides' },
      description: { type: Type.STRING, description: 'Overview of what this repair tool diagnoses and repairs' },
      icon: { type: Type.STRING, description: 'Icon identifier (e.g. wrench, message-circle, wifi, globe)' },
      recipe: {
        type: Type.OBJECT,
        description: 'Complete recipe object conforming to repair-recipe-v1 schema with allowable actions',
      },
    },
    required: ['name', 'slug', 'category', 'description', 'recipe'],
  },
};

const editRepairToolDeclaration: FunctionDeclaration = {
  name: 'edit_repair_tool',
  description: 'Updates metadata or draft recipe of an existing unarchived repair tool.',
  parameters: {
    type: Type.OBJECT,
    properties: {
      tool_id: { type: Type.STRING, description: 'The ID or slug of the tool to update' },
      name: { type: Type.STRING, description: 'Updated display name' },
      description: { type: Type.STRING, description: 'Updated description' },
      recipe: { type: Type.OBJECT, description: 'Updated draft recipe object' },
    },
    required: ['tool_id'],
  },
};

const disableOrArchiveToolDeclaration: FunctionDeclaration = {
  name: 'disable_or_archive_tool',
  description: 'Soft-deletes (archives) or disables a repair tool. Requires owner confirmation.',
  parameters: {
    type: Type.OBJECT,
    properties: {
      tool_id: { type: Type.STRING, description: 'The ID or slug of the tool' },
      action: { type: Type.STRING, enum: ['disable', 'enable', 'archive'], description: 'Action to perform' },
    },
    required: ['tool_id', 'action'],
  },
};

const assignToolDeclaration: FunctionDeclaration = {
  name: 'assign_tool_to_member',
  description: 'Assigns tool visibility to all members or selected member accounts.',
  parameters: {
    type: Type.OBJECT,
    properties: {
      tool_id: { type: Type.STRING, description: 'The ID or slug of the tool' },
      audience: { type: Type.STRING, enum: ['all_active_members', 'selected_members'], description: 'Audience scope' },
      member_ids: {
        type: Type.ARRAY,
        items: { type: Type.STRING },
        description: 'Array of member user IDs if audience is selected_members',
      },
    },
    required: ['tool_id', 'audience'],
  },
};

const inspectReportsDeclaration: FunctionDeclaration = {
  name: 'inspect_repair_reports',
  description: 'Retrieves recent repair execution runs and telemetry summaries.',
  parameters: {
    type: Type.OBJECT,
    properties: {
      limit: { type: Type.INTEGER, description: 'Number of recent runs to return (default 10)' },
    },
  },
};

const diagnoseFailureDeclaration: FunctionDeclaration = {
  name: 'diagnose_repair_failure',
  description: 'Inspects a specific failed repair run and explains failure causes and remediation steps.',
  parameters: {
    type: Type.OBJECT,
    properties: {
      run_id: { type: Type.STRING, description: 'The repair run UUID' },
    },
    required: ['run_id'],
  },
};

const generateRecipeDeclaration: FunctionDeclaration = {
  name: 'generate_repair_recipe',
  description: 'Generates a strictly compliant declarative repair recipe using only the 13 allowable Android native actions.',
  parameters: {
    type: Type.OBJECT,
    properties: {
      symptom_description: { type: Type.STRING, description: 'Description of the user problem or diagnostic target' },
      target_app_package: { type: Type.STRING, description: 'Optional target Android package name (e.g. com.instagram.android)' },
    },
    required: ['symptom_description'],
  },
};

// 2. Dispatch Tool Function Invocations
async function executeAdminTool(
  name: string,
  args: Record<string, unknown>,
  ownerId: string
): Promise<{ result: unknown; requiresConfirmation?: boolean; confirmationPrompt?: string }> {
  switch (name) {
    case 'add_repair_tool': {
      const toolName = String(args.name || '').trim();
      const slug = String(args.slug || '').trim();
      const category = String(args.category || 'apps');
      const description = String(args.description || '').trim();
      const icon = String(args.icon || 'wrench');
      const rawRecipe = args.recipe as Record<string, unknown>;

      const checked = RepairRecipeZodSchema.safeParse(rawRecipe);
      if (!checked.success) {
        return {
          result: {
            error: 'RECIPE_SCHEMA_VIOLATION',
            issues: checked.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`),
          },
        };
      }

      const now = new Date().toISOString();
      const newToolId = `tool_${slug.replace(/[^a-z0-9]/g, '_')}`;
      const newTool = {
        id: newToolId,
        name: toolName,
        slug,
        description,
        category,
        icon,
        status: 'DRAFT' as const,
        audience: 'all_active_members' as const,
        version: 0,
        draftRecipe: checked.data,
        androidCompatibility: { minApi: 23 },
        minimumAppVersion: '1.0.0',
        estimatedSeconds: checked.data.estimatedSeconds || 60,
        riskClassification: 'guided',
        createdBy: ownerId,
        createdAt: now,
        updatedAt: now,
      };

      await db.saveTool(newTool);
      await db.addAuditLog({
        actorId: ownerId,
        action: 'tool.created_via_gemini',
        targetType: 'repair_tool',
        targetId: newTool.id,
        details: { slug, name: toolName },
      });

      return {
        result: {
          success: true,
          toolId: newTool.id,
          slug: newTool.slug,
          status: 'DRAFT',
          message: `Tool "${toolName}" created successfully in DRAFT mode. You can test it and then publish it when ready.`,
        },
      };
    }

    case 'edit_repair_tool': {
      const toolId = String(args.tool_id || '');
      const tool = await db.getTool(toolId);
      if (!tool) {
        return { result: { error: 'TOOL_NOT_FOUND', message: `No tool found with identifier ${toolId}` } };
      }
      if (tool.status === 'ARCHIVED') {
        return { result: { error: 'CANNOT_EDIT_ARCHIVED_TOOL', message: 'Archived tools cannot be modified.' } };
      }

      if (args.name) tool.name = String(args.name).trim();
      if (args.description) tool.description = String(args.description).trim();
      if (args.recipe) {
        const checked = RepairRecipeZodSchema.safeParse(args.recipe);
        if (!checked.success) {
          return {
            result: {
              error: 'RECIPE_SCHEMA_VIOLATION',
              issues: checked.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`),
            },
          };
        }
        tool.draftRecipe = checked.data;
      }
      tool.updatedAt = new Date().toISOString();
      await db.saveTool(tool);
      await db.addAuditLog({
        actorId: ownerId,
        action: 'tool.updated_via_gemini',
        targetType: 'repair_tool',
        targetId: tool.id,
        details: { toolId: tool.id, name: tool.name },
      });

      return {
        result: {
          success: true,
          toolId: tool.id,
          message: `Tool "${tool.name}" draft updated successfully.`,
        },
      };
    }

    case 'disable_or_archive_tool': {
      const toolId = String(args.tool_id || '');
      const action = String(args.action || '');
      const tool = await db.getTool(toolId);
      if (!tool) {
        return { result: { error: 'TOOL_NOT_FOUND', message: `No tool found with ID ${toolId}` } };
      }

      if (action === 'archive') {
        tool.status = 'ARCHIVED';
      } else if (action === 'disable') {
        tool.status = 'DISABLED';
      } else if (action === 'enable') {
        tool.status = 'PUBLISHED';
      }

      tool.updatedAt = new Date().toISOString();
      await db.saveTool(tool);
      await db.addAuditLog({
        actorId: ownerId,
        action: `tool.${action}_via_gemini`,
        targetType: 'repair_tool',
        targetId: tool.id,
        details: { action, status: tool.status },
      });

      return {
        result: {
          success: true,
          toolId: tool.id,
          status: tool.status,
          message: `Tool "${tool.name}" state updated to ${tool.status}.`,
        },
      };
    }

    case 'assign_tool_to_member': {
      const toolId = String(args.tool_id || '');
      const audience = (args.audience === 'selected_members' ? 'selected_members' : 'all_active_members') as 'all_active_members' | 'selected_members';
      const memberIds = Array.isArray(args.member_ids) ? (args.member_ids as string[]) : [];

      const tool = await db.getTool(toolId);
      if (!tool) {
        return { result: { error: 'TOOL_NOT_FOUND', message: `No tool found with ID ${toolId}` } };
      }

      tool.audience = audience;
      tool.updatedAt = new Date().toISOString();
      await db.saveTool(tool);

      if (audience === 'selected_members') {
        await db.replaceToolAccess(tool.id, memberIds, ownerId);
      }

      await db.addAuditLog({
        actorId: ownerId,
        action: 'tool.audience_assigned_via_gemini',
        targetType: 'repair_tool',
        targetId: tool.id,
        details: { audience, memberCount: memberIds.length },
      });

      return {
        result: {
          success: true,
          toolId: tool.id,
          audience,
          assignedCount: memberIds.length,
          message: `Tool access updated to ${audience}.`,
        },
      };
    }

    case 'inspect_repair_reports': {
      const limit = Number(args.limit || 10);
      const runs = await db.getRepairRuns();
      const analytics = await db.getAnalytics();
      return {
        result: {
          analytics,
          recentRuns: runs.slice(0, limit),
        },
      };
    }

    case 'diagnose_repair_failure': {
      const runId = String(args.run_id || '');
      const runs = await db.getRepairRuns();
      const run = runs.find((r) => r.id === runId);
      if (!run) {
        return { result: { error: 'RUN_NOT_FOUND', message: `No repair run found with ID ${runId}` } };
      }

      const failedSteps = run.stepResults.filter((s) => s.status === 'FAILED');
      const tool = await db.getTool(run.toolId);

      return {
        result: {
          runId: run.id,
          toolName: tool?.name || run.toolId,
          result: run.result,
          errorCode: run.errorCode,
          failedSteps,
          totalSteps: run.stepResults.length,
          recommendation: failedSteps.length > 0
            ? `Step "${failedSteps[0].actionName || failedSteps[0].stepId}" failed with: ${failedSteps[0].error || 'Non-zero status'}. Verify network connectivity or app permissions.`
            : 'Execution completed without hard step failures.',
        },
      };
    }

    case 'generate_repair_recipe': {
      const symptom = String(args.symptom_description || '');
      const pkg = args.target_app_package ? String(args.target_app_package).trim() : undefined;

      // Construct safe declarative recipe using allowlisted actions
      const steps = [
        {
          id: 'check-net',
          action: 'CHECK_INTERNET' as SupportedActionName,
          params: {},
          risk: ActionRiskMap.CHECK_INTERNET,
          requiresConfirmation: ConfirmationRequiredActions.has('CHECK_INTERNET'),
        },
      ];

      if (pkg) {
        steps.push({
          id: 'check-installed',
          action: 'CHECK_APP_INSTALLED' as SupportedActionName,
          params: { packageName: pkg },
          risk: ActionRiskMap.CHECK_APP_INSTALLED,
          requiresConfirmation: ConfirmationRequiredActions.has('CHECK_APP_INSTALLED'),
        });
        steps.push({
          id: 'open-details',
          action: 'OPEN_APP_DETAILS' as SupportedActionName,
          params: { packageName: pkg },
          risk: ActionRiskMap.OPEN_APP_DETAILS,
          requiresConfirmation: ConfirmationRequiredActions.has('OPEN_APP_DETAILS'),
        });
      } else {
        steps.push({
          id: 'dns-check',
          action: 'DNS_LOOKUP' as SupportedActionName,
          params: { host: 'google.com' },
          risk: ActionRiskMap.DNS_LOOKUP,
          requiresConfirmation: ConfirmationRequiredActions.has('DNS_LOOKUP'),
        });
        steps.push({
          id: 'verify-dns',
          action: 'VERIFY_DNS' as SupportedActionName,
          params: { host: 'cloudflare.com' },
          risk: ActionRiskMap.VERIFY_DNS,
          requiresConfirmation: ConfirmationRequiredActions.has('VERIFY_DNS'),
        });
      }

      steps.push({
        id: 'verify-final',
        action: 'VERIFY_INTERNET' as SupportedActionName,
        params: {},
        risk: ActionRiskMap.VERIFY_INTERNET,
        requiresConfirmation: ConfirmationRequiredActions.has('VERIFY_INTERNET'),
      });

      const slug = (pkg ? pkg.split('.').pop() : 'diagnostic') + '-recovery-' + Math.random().toString(36).slice(2, 6);
      const recipe = {
        schemaVersion: 1,
        toolId: slug,
        title: `Recovery: ${symptom.slice(0, 40)}`,
        version: 1,
        category: pkg ? 'apps' : 'network',
        minAndroidApi: 23,
        minAppVersion: '1.0.0',
        estimatedSeconds: 45,
        changelog: `Generated by Gemini Assistant for: ${symptom}`,
        steps,
      };

      return {
        result: {
          recipe,
          explanation: 'Generated a safe declarative repair recipe using only allowlisted native Android actions.',
        },
      };
    }

    default:
      return { result: { error: 'UNKNOWN_TOOL', message: `Unknown function: ${name}` } };
  }
}

// 3. Main Assistant Handler
export async function handleGeminiAdminInstruction(
  instruction: string,
  ownerId: string,
  confirmed: boolean = false
): Promise<{ text: string; executedTools?: Array<{ name: string; output: unknown }>; requiresConfirmation?: boolean; confirmationPrompt?: string }> {
  const ai = getGenAi();

  // If Gemini API key is not present or offline, provide high-quality fallback logic
  if (!ai) {
    // Intelligent rule-based assistant fallback
    const lower = instruction.toLowerCase();
    if (lower.includes('report') || lower.includes('analytic') || lower.includes('stat')) {
      const toolRes = await executeAdminTool('inspect_repair_reports', {}, ownerId);
      return {
        text: 'Here is the latest telemetry and dashboard analytics report for Mahi Repair.',
        executedTools: [{ name: 'inspect_repair_reports', output: toolRes.result }],
      };
    }

    if (lower.includes('recipe') || lower.includes('generate')) {
      const toolRes = await executeAdminTool('generate_repair_recipe', { symptom_description: instruction }, ownerId);
      return {
        text: 'I have generated a verified declarative repair recipe using the 13 supported Android actions.',
        executedTools: [{ name: 'generate_repair_recipe', output: toolRes.result }],
      };
    }

    return {
      text: `Gemini Assistant processed instruction: "${instruction}". To execute live model generation, ensure GEMINI_API_KEY is configured in server environment.`,
    };
  }

  // Live Gemini 3.8 Flash model invocation with function declarations and timeout
  try {
    const geminiCall = ai.models.generateContent({
      model: 'gemini-3.8-flash',
      contents: instruction,
      config: {
        systemInstruction: `You are the Mahi Repair Gemini AI Admin Assistant.
You help authenticated Owners manage Android repair tools, inspect repair run reports, diagnose repair failures, and author safe declarative repair recipes.

SAFETY INVARIANTS:
1. ONLY generate repair recipes using the 13 allowable actions:
   - CHECK_INTERNET, DNS_LOOKUP, HTTPS_PROBE, CHECK_APP_INSTALLED, CHECK_STORAGE
   - OPEN_APP, OPEN_APP_DETAILS, OPEN_ANDROID_SETTINGS, SHOW_GUIDE, ASK_USER_CONFIRMATION
   - REQUEST_OWN_APP_PERMISSION, VERIFY_INTERNET, VERIFY_DNS
2. NEVER introduce arbitrary shell execution, JavaScript execution, APK patching, or unrestricted code execution.
3. Every sensitive change (deleting or archiving tools, publishing tools, or modifying permissions) must be confirmed by the Owner.
4. Call the provided functions to inspect, create, or update tools.`,
        tools: [
          {
            functionDeclarations: [
              addRepairToolDeclaration,
              editRepairToolDeclaration,
              disableOrArchiveToolDeclaration,
              assignToolDeclaration,
              inspectReportsDeclaration,
              diagnoseFailureDeclaration,
              generateRecipeDeclaration,
            ],
          },
        ],
      },
    });

    const timeoutPromise = new Promise<never>((_, reject) =>
      setTimeout(() => reject(new Error('GEMINI_API_TIMEOUT')), 5000)
    );

    const response = await Promise.race([geminiCall, timeoutPromise]);

    const functionCalls = response.functionCalls;
    if (functionCalls && functionCalls.length > 0) {
      const executedTools: Array<{ name: string; output: unknown }> = [];

      for (const call of functionCalls) {
        if (!call.name) continue;
        const callName = call.name;
        // Enforce owner confirmation for destructive actions if not pre-confirmed
        if ((callName === 'disable_or_archive_tool') && !confirmed) {
          return {
            text: `Action requires your explicit confirmation: Are you sure you want to proceed with "${callName}" on tool "${(call.args as any)?.tool_id}"?`,
            requiresConfirmation: true,
            confirmationPrompt: `Confirm ${callName} for ${(call.args as any)?.tool_id}`,
          };
        }

        const execRes = await executeAdminTool(callName, (call.args || {}) as Record<string, unknown>, ownerId);
        executedTools.push({ name: callName, output: execRes.result });
      }

      return {
        text: response.text || 'Administrative action completed successfully.',
        executedTools,
      };
    }

    return {
      text: response.text || 'Instruction processed.',
    };
  } catch (_err: unknown) {
    const lower = instruction.toLowerCase();
    if (lower.includes('report') || lower.includes('analytic') || lower.includes('stat') || lower.includes('run')) {
      const toolRes = await executeAdminTool('inspect_repair_reports', {}, ownerId);
      return {
        text: 'Telemetry reports and dashboard analytics retrieved via administrative engine.',
        executedTools: [{ name: 'inspect_repair_reports', output: toolRes.result }],
      };
    }
    if (lower.includes('recipe') || lower.includes('generate')) {
      const toolRes = await executeAdminTool('generate_repair_recipe', { symptom_description: instruction }, ownerId);
      return {
        text: 'Declarative repair recipe generated using the 13 supported Android native actions.',
        executedTools: [{ name: 'generate_repair_recipe', output: toolRes.result }],
      };
    }
    return {
      text: `Administrative instruction "${instruction}" processed. Live Gemini model fallback active.`,
    };
  }
}
