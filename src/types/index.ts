/**
 * Allowlisted native repair actions supported by Mahi Repair.
 * Arbitrary scripts or unallowlisted actions are strictly rejected.
 */
export type ActionType =
  | 'CHECK_INTERNET'
  | 'DNS_LOOKUP'
  | 'HTTPS_PROBE'
  | 'CHECK_APP_INSTALLED'
  | 'CHECK_STORAGE'
  | 'OPEN_APP'
  | 'OPEN_APP_DETAILS'
  | 'OPEN_ANDROID_SETTINGS'
  | 'SHOW_GUIDE'
  | 'ASK_USER_CONFIRMATION'
  | 'REQUEST_OWN_APP_PERMISSION'
  | 'VERIFY_INTERNET'
  | 'VERIFY_DNS';

export type StepStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'WAITING_USER'
  | 'CANCELLED'
  | 'SKIPPED';

export type RepairRunResult = 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'CANCELLED';

export type ToolCategory =
  | 'ALL'
  | 'NETWORK'
  | 'SOCIAL'
  | 'SYSTEM'
  | 'MEDIA'
  | 'GENERAL';

export interface RecipeStep {
  id: string;
  action: ActionType;
  name: string;
  description?: string;
  parameters?: Record<string, any>;
  isRequired?: boolean;
  stopOnFailure?: boolean;
}

export interface RepairRecipe {
  recipeId: string;
  toolId: string;
  version: string;
  targetPackage?: string | null;
  requiredAndroidSdk?: number;
  steps: RecipeStep[];
}

export interface RepairTool {
  id: string;
  name: string;
  description: string;
  category: ToolCategory;
  version: string;
  problemDescription: string;
  minAppVersion?: number;
  minAndroidSdk?: number;
  isNew?: boolean;
  enabled?: boolean;
  targetPackage?: string | null;
  lastUpdated?: number;
  rawSignedPayload?: string;
  signatureBase64?: string;
  audience?: 'all_active_members' | 'selected_members';
  status?: 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'DISABLED' | 'ARCHIVED';
}

export interface StepExecutionRecord {
  stepId: string;
  actionName: string;
  title: string;
  status: StepStatus;
  message: string;
  durationMs: number;
  details?: Record<string, string>;
}

export interface RepairRunReport {
  runId: string;
  toolId: string;
  toolName: string;
  toolVersion: string;
  userEmail: string;
  deviceModel: string;
  androidSdk: number;
  startTime: number;
  endTime: number;
  result: RepairRunResult;
  stepRecords: StepExecutionRecord[];
  finalDiagnosis: string;
}

export type UserActionType = 'CONFIRMATION' | 'GUIDE' | 'PERMISSION';

export interface ActionResult {
  status: StepStatus;
  message: string;
  details?: Record<string, string>;
  requiresUserAction?: boolean;
  userActionType?: UserActionType;
  userActionPayload?: Record<string, string>;
}

export type ExecutionEvent =
  | { type: 'Idle' }
  | { type: 'VerifyingSignature'; toolName: string }
  | { type: 'SignatureFailed'; reason: string }
  | { type: 'IncompatibleDevice'; requiredSdk: number; currentSdk: number }
  | {
      type: 'Running';
      tool: RepairTool;
      totalSteps: number;
      currentStepIndex: number;
      currentStep: RecipeStep;
      stepRecords: StepExecutionRecord[];
    }
  | {
      type: 'PromptUserAction';
      actionType: UserActionType;
      title: string;
      message: string;
      steps: string[];
      payload: Record<string, string>;
    }
  | { type: 'Finished'; report: RepairRunReport };

export interface RepairHistoryItem {
  id: string;
  toolId: string;
  toolName: string;
  timestamp: number;
  result: RepairRunResult;
  durationMs: number;
  diagnosis: string;
  stepsCount: number;
  stepRecords?: StepExecutionRecord[];
}

export interface AuthSession {
  isAuthenticated: boolean;
  email: string;
  role: 'member';
  isOfflineMode: boolean;
}

export interface BackendConfig {
  supabaseUrl: string;
  supabaseAnonKey: string;
  publicKeyPem: string;
}
