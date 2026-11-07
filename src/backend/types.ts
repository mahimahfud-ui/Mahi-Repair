import { z } from 'zod';

export type AppRole = 'owner' | 'member';
export type AccountStatus = 'active' | 'suspended' | 'revoked';
export type ToolStatus = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'DISABLED' | 'ARCHIVED';
export type ToolAudience = 'all_active_members' | 'selected_members';
export type RepairResult = 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'CANCELLED';

export interface UserProfile {
  id: string;
  role: AppRole;
  status: AccountStatus;
  email: string;
  displayName: string;
  createdAt: string;
  updatedAt: string;
}

export interface DeviceRecord {
  id: string;
  userId: string;
  deviceLabel?: string;
  appVersion: string;
  androidVersion: string;
  androidApi?: number;
  registeredAt: string;
  lastSeenAt: string;
  revokedAt?: string | null;
}

export interface RepairToolRecord {
  id: string;
  name: string;
  slug: string;
  description: string;
  category: string;
  icon: string;
  status: ToolStatus;
  audience: ToolAudience;
  version: number;
  draftRecipe: unknown;
  androidCompatibility: { minApi: number };
  minimumAppVersion: string;
  estimatedSeconds: number;
  riskClassification: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface RepairToolVersionRecord {
  id: string;
  toolId: string;
  version: number;
  payload: unknown;
  canonicalPayload: string;
  signature: string;
  signatureAlgorithm: 'RSA-SHA256';
  changelog: string;
  publishedBy: string;
  publishedAt: string;
}

export interface RepairToolAccessRecord {
  id: string;
  toolId: string;
  memberId: string;
  assignedBy: string;
  createdAt: string;
}

export interface DeviceToolSyncRecord {
  deviceId: string;
  userId: string;
  toolId: string;
  version: number;
  syncedAt: string;
}

export interface RepairRunRecord {
  id: string;
  userId: string;
  deviceId: string;
  toolId: string;
  toolVersion: number;
  startedAt: string;
  completedAt?: string | null;
  result: RepairResult;
  stepResults: Array<{
    stepId: string;
    actionName?: string;
    status: 'COMPLETED' | 'FAILED' | 'SKIPPED';
    durationMs?: number;
    error?: string;
  }>;
  errorCode?: string | null;
  appVersion: string;
  createdAt: string;
}

export interface AdminAuditLogRecord {
  id: string;
  actorId: string;
  action: string;
  targetType: string;
  targetId?: string;
  details: Record<string, unknown>;
  createdAt: string;
}

export interface RecipeTestHistoryRecord {
  id: string;
  ownerId: string;
  toolId?: string;
  recipeHash: string;
  validationStatus: 'VALID' | 'INVALID';
  issues: unknown[];
  simulatedStepCount: number;
  testedAt: string;
}

export interface AdminPinConfig {
  id: 'pin_config';
  pinHash: string;
  pinSalt: string;
  iterations: number;
  updatedBy: string;
  updatedAt: string;
}

// Recipe Schema Validation
const hostPattern = z.string().min(1).max(253).regex(/^(?=.{1,253}$)([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)(\.([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?))*$/);
const packagePattern = z.string().min(3).max(255).regex(/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/);
const emptyParams = z.object({}).strict();

export const ActionDefinitions = {
  CHECK_INTERNET: emptyParams,
  DNS_LOOKUP: z.object({ host: hostPattern }).strict(),
  HTTPS_PROBE: z.object({
    host: hostPattern,
    path: z.string().max(512).regex(/^\/(?!\/)[^\r\n]*$/).optional(),
    timeoutMs: z.number().int().min(1000).max(10000).optional(),
  }).strict(),
  CHECK_APP_INSTALLED: z.object({ packageName: packagePattern }).strict(),
  CHECK_STORAGE: z.object({ minimumAvailableMb: z.number().int().positive().max(1_000_000).optional() }).strict(),
  OPEN_APP: z.object({ packageName: packagePattern }).strict(),
  OPEN_APP_DETAILS: z.object({ packageName: packagePattern }).strict(),
  OPEN_ANDROID_SETTINGS: z.object({
    screen: z.enum(['wifi', 'internet', 'bluetooth', 'display', 'sound', 'app_notifications', 'date_time', 'storage']),
  }).strict(),
  SHOW_GUIDE: z.object({
    title: z.string().min(1).max(100),
    instructions: z.array(z.string().min(1).max(1000)).min(1).max(30),
  }).strict(),
  ASK_USER_CONFIRMATION: z.object({
    title: z.string().min(1).max(100),
    message: z.string().min(1).max(1000),
    confirmLabel: z.string().max(40).optional(),
  }).strict(),
  REQUEST_OWN_APP_PERMISSION: z.object({
    permission: z.enum(['POST_NOTIFICATIONS', 'CAMERA', 'ACCESS_FINE_LOCATION', 'READ_MEDIA_IMAGES']),
  }).strict(),
  VERIFY_INTERNET: emptyParams,
  VERIFY_DNS: z.object({ host: hostPattern }).strict(),
} as const;

export type SupportedActionName = keyof typeof ActionDefinitions;

export const ActionRiskMap: Record<SupportedActionName, 'read_only' | 'guided' | 'permission'> = {
  CHECK_INTERNET: 'read_only',
  DNS_LOOKUP: 'read_only',
  HTTPS_PROBE: 'read_only',
  CHECK_APP_INSTALLED: 'read_only',
  CHECK_STORAGE: 'read_only',
  OPEN_APP: 'guided',
  OPEN_APP_DETAILS: 'guided',
  OPEN_ANDROID_SETTINGS: 'guided',
  SHOW_GUIDE: 'guided',
  ASK_USER_CONFIRMATION: 'guided',
  REQUEST_OWN_APP_PERMISSION: 'permission',
  VERIFY_INTERNET: 'read_only',
  VERIFY_DNS: 'read_only',
};

export const ConfirmationRequiredActions = new Set<SupportedActionName>([
  'OPEN_APP',
  'OPEN_APP_DETAILS',
  'OPEN_ANDROID_SETTINGS',
  'ASK_USER_CONFIRMATION',
  'REQUEST_OWN_APP_PERMISSION',
]);

export const RepairRecipeZodSchema = z.object({
  schemaVersion: z.literal(1),
  toolId: z.string().min(3).max(80).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
  title: z.string().min(1).max(100),
  version: z.number().int().positive(),
  category: z.enum(['network', 'apps', 'storage', 'connectivity', 'system', 'guides']),
  minAndroidApi: z.number().int().min(23).max(100).default(23),
  minAppVersion: z.string().max(30).regex(/^\d+(?:\.\d+){0,3}$/).default('1.0.0'),
  estimatedSeconds: z.number().int().min(1).max(3600).default(60),
  changelog: z.string().max(2000).optional(),
  steps: z.array(z.object({
    id: z.string().min(1).max(80),
    action: z.string(),
    params: z.record(z.string(), z.unknown()),
    risk: z.enum(['read_only', 'guided', 'permission']),
    requiresConfirmation: z.boolean(),
    condition: z.object({
      previousStep: z.string().min(1).max(80),
      equals: z.enum(['success', 'failure']),
    }).strict().optional(),
  }).strict()).min(1).max(50),
}).strict().superRefine((recipe, ctx) => {
  const seenIds = new Set<string>();

  recipe.steps.forEach((step, index) => {
    if (!Object.prototype.hasOwnProperty.call(ActionDefinitions, step.action)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['steps', index, 'action'],
        message: `Unsupported action: ${step.action}. Must be one of the 13 allowable declarative actions.`,
      });
      return;
    }

    const actionName = step.action as SupportedActionName;

    if (seenIds.has(step.id)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['steps', index, 'id'],
        message: `Duplicate step ID: ${step.id}`,
      });
    }
    seenIds.add(step.id);

    const paramsResult = ActionDefinitions[actionName].safeParse(step.params);
    if (!paramsResult.success) {
      paramsResult.error.issues.forEach((issue) => {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['steps', index, 'params', ...issue.path],
          message: issue.message,
        });
      });
    }

    const expectedRisk = ActionRiskMap[actionName];
    if (step.risk !== expectedRisk) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['steps', index, 'risk'],
        message: `Risk for action ${actionName} must be ${expectedRisk}, got ${step.risk}.`,
      });
    }

    if (ConfirmationRequiredActions.has(actionName) && !step.requiresConfirmation) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['steps', index, 'requiresConfirmation'],
        message: `Action ${actionName} requires confirmation to be true.`,
      });
    }

    if (step.condition?.previousStep) {
      const priorSteps = recipe.steps.slice(0, index);
      if (!priorSteps.some((p) => p.id === step.condition?.previousStep)) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          path: ['steps', index, 'condition'],
          message: `Condition references nonexistent or forward step ID: ${step.condition.previousStep}`,
        });
      }
    }
  });
});

export type RepairRecipe = z.infer<typeof RepairRecipeZodSchema>;
