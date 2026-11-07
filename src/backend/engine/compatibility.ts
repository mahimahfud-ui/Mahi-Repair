import {
  DeviceRecord,
  RepairToolRecord,
  CompatibilityEvaluationResult,
  CompatibilityStatus,
} from '../types';

/**
 * Server-Side Device Capability and Compatibility Engine
 * Evaluates real reported device hardware and telemetry profiles against
 * repair tool specifications to compute deterministic eligibility.
 */
export function evaluateDeviceCompatibility(
  device: DeviceRecord,
  tool: RepairToolRecord
): CompatibilityEvaluationResult {
  const reasons: string[] = [];
  const missingPermissions: string[] = [];
  const failedPreconditions: string[] = [];

  const hw = device.hardwareProfile;
  const tel = device.telemetry;
  const meta = tool.compatibilityMetadata;

  // 1. Basic Android SDK verification
  const minSdk = meta?.minimumAndroidSdk ?? tool.androidCompatibility?.minApi ?? 23;
  const maxSdk = meta?.maximumAndroidSdk ?? tool.androidCompatibility?.maxApi;
  const devSdk = hw?.androidSdk ?? device.androidApi ?? 0;

  if (devSdk === 0) {
    return {
      toolId: tool.id,
      toolVersion: tool.version,
      status: 'INSUFFICIENT_DEVICE_INFORMATION',
      isEligible: false,
      reasons: ['Device Android SDK version is unknown or not yet reported.'],
      evaluatedAt: new Date().toISOString(),
    };
  }

  if (devSdk < minSdk) {
    reasons.push(`Android SDK ${devSdk} is below the minimum required SDK ${minSdk}.`);
  }
  if (maxSdk && devSdk > maxSdk) {
    reasons.push(`Android SDK ${devSdk} exceeds the maximum validated SDK ${maxSdk}.`);
  }

  // 2. Manufacturer & Model verification
  if (meta?.supportedManufacturers && meta.supportedManufacturers.length > 0) {
    const devMfg = (hw?.manufacturer || hw?.brand || '').toLowerCase().trim();
    const isMfgMatch = meta.supportedManufacturers.some((m) =>
      devMfg.includes(m.toLowerCase().trim())
    );
    if (!isMfgMatch) {
      reasons.push(
        `Manufacturer "${hw?.manufacturer || 'Unknown'}" is not supported. Supported: ${meta.supportedManufacturers.join(', ')}.`
      );
    }
  }

  if (meta?.supportedModels && meta.supportedModels.length > 0) {
    const devModel = (hw?.model || hw?.commercialModelName || '').toLowerCase().trim();
    const devCodename = (hw?.deviceCodename || '').toLowerCase().trim();
    const isModelMatch = meta.supportedModels.some((m) => {
      const target = m.toLowerCase().trim();
      return devModel.includes(target) || devCodename.includes(target);
    });
    if (!isModelMatch) {
      reasons.push(
        `Device model "${hw?.model || 'Unknown'}" is not in validated compatibility matrix.`
      );
    }
  }

  // 3. CPU ABI verification
  if (meta?.requiredCpuAbis && meta.requiredCpuAbis.length > 0) {
    const devAbis = hw?.cpuAbis || [];
    const hasMatchingAbi = meta.requiredCpuAbis.some((abi) => devAbis.includes(abi));
    if (!hasMatchingAbi) {
      reasons.push(
        `Device CPU architectures (${devAbis.join(', ') || 'unknown'}) do not match required ABIs (${meta.requiredCpuAbis.join(', ')}).`
      );
    }
  }

  // 4. Hardware System Features (Sensors, Camera, etc.)
  if (meta?.requiredSystemFeatures && meta.requiredSystemFeatures.length > 0) {
    const devFeatures = new Set((hw?.supportedFeatures || []).map((f) => f.toLowerCase()));
    for (const reqFeature of meta.requiredSystemFeatures) {
      if (!devFeatures.has(reqFeature.toLowerCase())) {
        reasons.push(`Required system feature is missing: "${reqFeature}".`);
      }
    }
  }

  // Check hard incompatibility first
  if (reasons.length > 0) {
    return {
      toolId: tool.id,
      toolVersion: tool.version,
      status: 'NOT_SUPPORTED',
      isEligible: false,
      reasons,
      evaluatedAt: new Date().toISOString(),
    };
  }

  // 5. Dynamic Telemetry: Thermal Status
  let isTemporarilyUnavailable = false;
  const tempUnavailableReasons: string[] = [];

  if (tel) {
    if (meta?.thermalPrerequisites) {
      const maxThermal = meta.thermalPrerequisites.maximumThermalStatus || 'MODERATE';
      const severeStates = ['SEVERE', 'CRITICAL', 'EMERGENCY', 'SHUTDOWN'];
      if (severeStates.includes(tel.thermalStatus)) {
        isTemporarilyUnavailable = true;
        tempUnavailableReasons.push(
          `Device thermal status is ${tel.thermalStatus}. Cooldown required before executing this repair.`
        );
      } else if (maxThermal === 'LIGHT' && tel.thermalStatus === 'MODERATE') {
        isTemporarilyUnavailable = true;
        tempUnavailableReasons.push(
          `Device is experiencing thermal pressure (${tel.thermalStatus}). Maximum allowed is ${maxThermal}.`
        );
      }

      if (
        meta.thermalPrerequisites.maximumTemperatureCelsius &&
        tel.batteryTemperature &&
        tel.batteryTemperature > meta.thermalPrerequisites.maximumTemperatureCelsius
      ) {
        isTemporarilyUnavailable = true;
        tempUnavailableReasons.push(
          `Battery temperature is ${tel.batteryTemperature.toFixed(1)}°C (threshold: ${meta.thermalPrerequisites.maximumTemperatureCelsius}°C).`
        );
      }
    }

    // 6. Battery prerequisites
    if (meta?.batteryPrerequisites) {
      if (
        meta.batteryPrerequisites.minimumLevel &&
        tel.batteryLevel < meta.batteryPrerequisites.minimumLevel &&
        tel.chargingStatus !== 'CHARGING' &&
        tel.chargingStatus !== 'FULL'
      ) {
        isTemporarilyUnavailable = true;
        tempUnavailableReasons.push(
          `Battery level is ${tel.batteryLevel}% (requires at least ${meta.batteryPrerequisites.minimumLevel}% or connected to charger).`
        );
      }
    }

    // 7. Memory & Storage checks
    if (meta?.minimumAvailableRamBytes && tel.availableRamBytes > 0) {
      if (tel.availableRamBytes < meta.minimumAvailableRamBytes) {
        isTemporarilyUnavailable = true;
        const availMb = Math.round(tel.availableRamBytes / (1024 * 1024));
        const reqMb = Math.round(meta.minimumAvailableRamBytes / (1024 * 1024));
        tempUnavailableReasons.push(
          `Available RAM (${availMb} MB) is below minimum requirement (${reqMb} MB). Close background apps.`
        );
      }
    }

    if (meta?.minimumAvailableStorageBytes && tel.internalStorageAvailableBytes > 0) {
      if (tel.internalStorageAvailableBytes < meta.minimumAvailableStorageBytes) {
        isTemporarilyUnavailable = true;
        const availMb = Math.round(tel.internalStorageAvailableBytes / (1024 * 1024));
        const reqMb = Math.round(meta.minimumAvailableStorageBytes / (1024 * 1024));
        tempUnavailableReasons.push(
          `Available internal storage (${availMb} MB) is below minimum requirement (${reqMb} MB).`
        );
      }
    }
  }

  if (isTemporarilyUnavailable) {
    return {
      toolId: tool.id,
      toolVersion: tool.version,
      status: 'TEMPORARILY_UNAVAILABLE',
      isEligible: false,
      reasons: tempUnavailableReasons,
      evaluatedAt: new Date().toISOString(),
    };
  }

  // 8. Required permissions check
  if (meta?.requiredPermissions && meta.requiredPermissions.length > 0) {
    const granted = new Set(tel?.grantedPermissions || []);
    for (const perm of meta.requiredPermissions) {
      if (!granted.has(perm)) {
        missingPermissions.push(perm);
      }
    }
  }

  if (missingPermissions.length > 0) {
    return {
      toolId: tool.id,
      toolVersion: tool.version,
      status: 'ELIGIBLE_WITH_PERMISSION',
      isEligible: false,
      reasons: missingPermissions.map((p) => `Requires permission: ${p}`),
      missingPermissions,
      evaluatedAt: new Date().toISOString(),
    };
  }

  // 9. Network Preconditions check
  if (meta?.networkPrerequisites?.requiresInternet) {
    if (tel && !tel.internetValidated && tel.networkConnectivity !== 'CONNECTED') {
      failedPreconditions.push('Active internet connection required for remote server probes.');
    }
  }

  if (failedPreconditions.length > 0) {
    return {
      toolId: tool.id,
      toolVersion: tool.version,
      status: 'ELIGIBLE_WITH_PRECONDITION',
      isEligible: false,
      reasons: failedPreconditions,
      failedPreconditions,
      evaluatedAt: new Date().toISOString(),
    };
  }

  // Fully Eligible!
  return {
    toolId: tool.id,
    toolVersion: tool.version,
    status: 'ELIGIBLE',
    isEligible: true,
    reasons: ['Hardware, OS, permissions, and device preconditions verified.'],
    evaluatedAt: new Date().toISOString(),
  };
}

/**
 * Filters and categorizes full tool catalog for a specific device profile.
 */
export function categorizeToolsForDevice(
  device: DeviceRecord,
  tools: RepairToolRecord[]
): {
  compatible: Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>;
  availableAfterPermission: Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>;
  requiresDiagnosticCheck: Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>;
  unsupported: Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>;
  temporarilyUnavailable: Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>;
} {
  const result = {
    compatible: [] as Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>,
    availableAfterPermission: [] as Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>,
    requiresDiagnosticCheck: [] as Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>,
    unsupported: [] as Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>,
    temporarilyUnavailable: [] as Array<{ tool: RepairToolRecord; decision: CompatibilityEvaluationResult }>,
  };

  for (const tool of tools) {
    // Only published and non-archived tools are evaluated for catalogue delivery
    if (tool.status !== 'PUBLISHED') continue;

    const decision = evaluateDeviceCompatibility(device, tool);
    switch (decision.status) {
      case 'ELIGIBLE':
        result.compatible.push({ tool, decision });
        break;
      case 'ELIGIBLE_WITH_PERMISSION':
        result.availableAfterPermission.push({ tool, decision });
        break;
      case 'ELIGIBLE_WITH_PRECONDITION':
        result.requiresDiagnosticCheck.push({ tool, decision });
        break;
      case 'TEMPORARILY_UNAVAILABLE':
        result.temporarilyUnavailable.push({ tool, decision });
        break;
      case 'NOT_SUPPORTED':
      case 'INSUFFICIENT_DEVICE_INFORMATION':
      default:
        result.unsupported.push({ tool, decision });
        break;
    }
  }

  return result;
}
