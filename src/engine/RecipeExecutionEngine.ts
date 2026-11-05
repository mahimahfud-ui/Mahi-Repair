import { ActionRegistry } from './ActionRegistry';
import { verifySignature } from '../security/verifier';
import {
  ActionType,
  ExecutionEvent,
  RecipeStep,
  RepairRecipe,
  RepairRunReport,
  RepairRunResult,
  RepairTool,
  StepExecutionRecord,
} from '../types';

export class RecipeExecutionEngine {
  private actionRegistry: ActionRegistry;
  private onStateChange: (event: ExecutionEvent) => void;
  private pendingUserResolver: ((approved: boolean) => void) | null = null;
  private isCancelled = false;

  constructor(
    actionRegistry: ActionRegistry,
    onStateChange: (event: ExecutionEvent) => void
  ) {
    this.actionRegistry = actionRegistry;
    this.onStateChange = onStateChange;
  }

  submitUserActionResponse(approved: boolean) {
    if (this.pendingUserResolver) {
      this.pendingUserResolver(approved);
      this.pendingUserResolver = null;
    }
  }

  cancelExecution() {
    this.isCancelled = true;
    if (this.pendingUserResolver) {
      this.pendingUserResolver(false);
      this.pendingUserResolver = null;
    }
  }

  reset() {
    this.pendingUserResolver = null;
    this.isCancelled = false;
    this.onStateChange({ type: 'Idle' });
  }

  /**
   * Parses and strictly validates a JSON recipe string.
   */
  parseRecipeJson(
    jsonString: string
  ): { ok: true; recipe: RepairRecipe } | { ok: false; error: string } {
    try {
      const root = JSON.parse(jsonString);
      if (!root || typeof root !== 'object') {
        return { ok: false, error: 'Recipe payload must be a JSON object' };
      }

      const toolId = root.tool_id;
      if (!toolId || typeof toolId !== 'string') {
        return { ok: false, error: "Missing required 'tool_id' field" };
      }

      const recipeId = root.recipe_id || `recipe_${Date.now()}`;
      const version = root.version || '1.0.0';
      const targetPackage = root.target_package || null;
      const requiredAndroidSdk = Number(root.required_android_sdk) || 26;

      if (!Array.isArray(root.steps)) {
        return { ok: false, error: "Recipe must contain a 'steps' array" };
      }

      const allowlistedActions: ActionType[] = [
        'CHECK_INTERNET',
        'DNS_LOOKUP',
        'HTTPS_PROBE',
        'CHECK_APP_INSTALLED',
        'CHECK_STORAGE',
        'OPEN_APP',
        'OPEN_APP_DETAILS',
        'OPEN_ANDROID_SETTINGS',
        'SHOW_GUIDE',
        'ASK_USER_CONFIRMATION',
        'REQUEST_OWN_APP_PERMISSION',
        'VERIFY_INTERNET',
        'VERIFY_DNS',
      ];

      const steps: RecipeStep[] = [];
      for (let i = 0; i < root.steps.length; i++) {
        const s = root.steps[i];
        if (!s || typeof s !== 'object') {
          return { ok: false, error: `Step at index ${i} is invalid` };
        }

        const action = s.action as ActionType;
        if (!allowlistedActions.includes(action)) {
          return {
            ok: false,
            error: `Unknown or unallowlisted action: '${s.action}'. Recipe rejected.`,
          };
        }

        const step: RecipeStep = {
          id: s.id || `step_${i}`,
          action,
          name: s.name || `Step ${i + 1}`,
          description: s.description || '',
          parameters: s.parameters || {},
          isRequired: s.is_required !== undefined ? !!s.is_required : true,
          stopOnFailure:
            s.stop_on_failure !== undefined ? !!s.stop_on_failure : true,
        };

        const validation = this.actionRegistry.validateStep(step);
        if (!validation.ok) {
          return {
            ok: false,
            error: validation.error || 'Step validation error',
          };
        }

        steps.push(step);
      }

      return {
        ok: true,
        recipe: {
          recipeId,
          toolId,
          version,
          targetPackage,
          requiredAndroidSdk,
          steps,
        },
      };
    } catch (e: any) {
      return {
        ok: false,
        error: `Malformed recipe schema: ${e.message}`,
      };
    }
  }

  /**
   * Executes a signed repair recipe end-to-end.
   */
  async executeRecipe(
    tool: RepairTool,
    rawSignedPayload: string,
    signatureBase64: string,
    userEmail: string,
    publicKeyPem?: string
  ): Promise<RepairRunReport> {
    const startTime = Date.now();
    const runId = `run_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`;
    const stepRecords: StepExecutionRecord[] = [];
    const deviceModel = 'Google Pixel 9 Pro (Simulated)';
    const currentAndroidSdk = 36;
    this.isCancelled = false;

    // 1. Signature Verification
    this.onStateChange({ type: 'VerifyingSignature', toolName: tool.name });
    await new Promise((r) => setTimeout(r, 450)); // Visual confirmation

    const isVerified = await verifySignature(
      rawSignedPayload,
      signatureBase64,
      publicKeyPem
    );

    if (!isVerified) {
      const failRecord: StepExecutionRecord = {
        stepId: 'verify_sig',
        actionName: 'RSA_SIGNATURE_VERIFICATION',
        title: 'Verify Owner Signature',
        status: 'FAILED',
        message: 'Digital signature verification failed. Recipe rejected.',
        durationMs: Date.now() - startTime,
      };

      const report: RepairRunReport = {
        runId,
        toolId: tool.id,
        toolName: tool.name,
        toolVersion: tool.version,
        userEmail,
        deviceModel,
        androidSdk: currentAndroidSdk,
        startTime,
        endTime: Date.now(),
        result: 'FAILED',
        stepRecords: [failRecord],
        finalDiagnosis:
          "Security Error: The recipe signature did not match the Owner's trusted public key. Execution aborted.",
      };

      this.onStateChange({
        type: 'SignatureFailed',
        reason: 'Invalid Owner signature. Untrusted payload rejected.',
      });
      this.onStateChange({ type: 'Finished', report });
      return report;
    }

    // 2. Schema and Parsing
    const parsed = this.parseRecipeJson(rawSignedPayload);
    if (!parsed.ok) {
      const failRecord: StepExecutionRecord = {
        stepId: 'parse_schema',
        actionName: 'VALIDATE_SCHEMA',
        title: 'Validate Schema',
        status: 'FAILED',
        message: parsed.error,
        durationMs: Date.now() - startTime,
      };

      const report: RepairRunReport = {
        runId,
        toolId: tool.id,
        toolName: tool.name,
        toolVersion: tool.version,
        userEmail,
        deviceModel,
        androidSdk: currentAndroidSdk,
        startTime,
        endTime: Date.now(),
        result: 'FAILED',
        stepRecords: [failRecord],
        finalDiagnosis: `Validation Error: ${parsed.error}`,
      };

      this.onStateChange({ type: 'Finished', report });
      return report;
    }

    const recipe = parsed.recipe;

    // 3. Android SDK Compatibility
    if (recipe.requiredAndroidSdk && currentAndroidSdk < recipe.requiredAndroidSdk) {
      const msg = `Device running Android API ${currentAndroidSdk}, but recipe requires API ${recipe.requiredAndroidSdk}+.`;
      const failRecord: StepExecutionRecord = {
        stepId: 'compat_check',
        actionName: 'COMPATIBILITY_CHECK',
        title: 'Android Version Check',
        status: 'FAILED',
        message: msg,
        durationMs: Date.now() - startTime,
      };

      const report: RepairRunReport = {
        runId,
        toolId: tool.id,
        toolName: tool.name,
        toolVersion: tool.version,
        userEmail,
        deviceModel,
        androidSdk: currentAndroidSdk,
        startTime,
        endTime: Date.now(),
        result: 'FAILED',
        stepRecords: [failRecord],
        finalDiagnosis: msg,
      };

      this.onStateChange({
        type: 'IncompatibleDevice',
        requiredSdk: recipe.requiredAndroidSdk,
        currentSdk: currentAndroidSdk,
      });
      this.onStateChange({ type: 'Finished', report });
      return report;
    }

    // 4. Sequential Step Execution
    let overallResult: RepairRunResult = 'SUCCESS';
    let stopPipeline = false;

    for (let index = 0; index < recipe.steps.length; index++) {
      if (this.isCancelled) {
        overallResult = 'CANCELLED';
        break;
      }

      const step = recipe.steps[index];

      if (stopPipeline) {
        stepRecords.push({
          stepId: step.id,
          actionName: step.action,
          title: step.name,
          status: 'SKIPPED',
          message: 'Skipped due to earlier required failure.',
          durationMs: 0,
        });
        continue;
      }

      this.onStateChange({
        type: 'Running',
        tool,
        totalSteps: recipe.steps.length,
        currentStepIndex: index,
        currentStep: step,
        stepRecords: [...stepRecords],
      });

      const stepStart = Date.now();
      const result = await this.actionRegistry.executeAction(step);

      if (result.requiresUserAction && result.userActionType) {
        // Interactive suspension (GUIDE, CONFIRMATION, PERMISSION)
        const title = result.userActionPayload?.title || step.name;
        const message =
          result.userActionPayload?.message ||
          result.userActionPayload?.description ||
          'Please review this step to continue.';
        const rawSteps = result.userActionPayload?.steps || '';
        const stepsList = rawSteps ? rawSteps.split('|||') : [];

        this.onStateChange({
          type: 'PromptUserAction',
          actionType: result.userActionType,
          title,
          message,
          steps: stepsList,
          payload: result.userActionPayload || {},
        });

        // Wait for user to approve or cancel
        const userApproved = await new Promise<boolean>((resolve) => {
          this.pendingUserResolver = resolve;
        });

        if (!userApproved) {
          const duration = Date.now() - stepStart;
          stepRecords.push({
            stepId: step.id,
            actionName: step.action,
            title: step.name,
            status: 'CANCELLED',
            message: 'Cancelled by user.',
            durationMs: duration,
          });

          if (step.isRequired) {
            overallResult = 'CANCELLED';
            stopPipeline = true;
          }
          continue;
        } else {
          // User approved or finished guide
          const duration = Date.now() - stepStart;
          stepRecords.push({
            stepId: step.id,
            actionName: step.action,
            title: step.name,
            status: 'COMPLETED',
            message: 'User completed guidance and confirmed step.',
            durationMs: duration,
            details: result.details,
          });
          continue;
        }
      }

      const stepDuration = Date.now() - stepStart;

      if (result.status === 'COMPLETED') {
        stepRecords.push({
          stepId: step.id,
          actionName: step.action,
          title: step.name,
          status: 'COMPLETED',
          message: result.message,
          durationMs: stepDuration,
          details: result.details,
        });
      } else {
        stepRecords.push({
          stepId: step.id,
          actionName: step.action,
          title: step.name,
          status: 'FAILED',
          message: result.message,
          durationMs: stepDuration,
          details: result.details,
        });

        if (step.isRequired) {
          if (step.stopOnFailure) {
            overallResult = 'FAILED';
            stopPipeline = true;
          } else {
            overallResult = 'PARTIAL';
          }
        }
      }

      // Smooth step pacing for UI visualization
      await new Promise((r) => setTimeout(r, 320));
    }

    // 5. Build Final Diagnosis
    let finalDiagnosis = '';
    switch (overallResult) {
      case 'SUCCESS':
        finalDiagnosis =
          'All repair and diagnostic steps passed successfully. Device issue resolved.';
        break;
      case 'PARTIAL':
        finalDiagnosis =
          'Some non-critical steps encountered errors, but required diagnostics succeeded.';
        break;
      case 'FAILED': {
        const failedStep = stepRecords.find((r) => r.status === 'FAILED');
        finalDiagnosis = failedStep
          ? `Issue diagnosed at step '${failedStep.title}': ${failedStep.message}`
          : 'Repair recipe did not complete successfully.';
        break;
      }
      case 'CANCELLED':
        finalDiagnosis = 'Repair was cancelled by user.';
        break;
    }

    const report: RepairRunReport = {
      runId,
      toolId: tool.id,
      toolName: tool.name,
      toolVersion: tool.version,
      userEmail,
      deviceModel,
      androidSdk: currentAndroidSdk,
      startTime,
      endTime: Date.now(),
      result: overallResult,
      stepRecords,
      finalDiagnosis,
    };

    this.onStateChange({ type: 'Finished', report });
    return report;
  }
}
