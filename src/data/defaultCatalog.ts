import { RepairTool } from '../types';
import { signRecipeForTesting } from '../security/verifier';

export const YOUTUBE_RECIPE_JSON = JSON.stringify(
  {
    recipe_id: 'recipe_youtube_01',
    tool_id: 'tool_youtube_repair',
    version: '1.2.0',
    target_package: 'com.google.android.youtube',
    required_android_sdk: 26,
    steps: [
      {
        id: 'step_yt_net',
        action: 'CHECK_INTERNET',
        name: 'Check Internet Connection',
        description: 'Verifies network interface and active internet route.',
        is_required: true,
        stop_on_failure: true,
      },
      {
        id: 'step_yt_dns',
        action: 'DNS_LOOKUP',
        name: 'Resolve Video Streaming Endpoint',
        description: 'Resolves Google Video streaming domain.',
        parameters: {
          hostname: 'googlevideo.com',
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_yt_probe',
        action: 'HTTPS_PROBE',
        name: 'Probe YouTube Server',
        description: 'Tests latency and TLS connectivity to YouTube servers.',
        parameters: {
          url: 'https://www.youtube.com/generate_204',
          timeout_ms: 5000,
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_yt_app',
        action: 'CHECK_APP_INSTALLED',
        name: 'Verify YouTube App',
        description: 'Confirms YouTube is installed on this device.',
        parameters: {
          package_name: 'com.google.android.youtube',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_yt_guide',
        action: 'SHOW_GUIDE',
        name: 'Cache Cleaning Guidance',
        description: 'Instructions to clear YouTube temporary app cache.',
        parameters: {
          guide_title: 'Clear YouTube App Cache',
          guide_description:
            'Clearing corrupted cached video chunks often resolves playback and loading errors.',
          guide_steps: [
            "Tap 'Storage & cache' on the next screen.",
            "Tap 'Clear cache' button.",
            "Do NOT tap 'Clear storage' to keep your offline downloads.",
          ],
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_yt_details',
        action: 'OPEN_APP_DETAILS',
        name: 'Open YouTube Settings',
        description: 'Opens system App Info to allow clearing cache.',
        parameters: {
          package_name: 'com.google.android.youtube',
          instruction:
            'Opened YouTube App Info. Tap Storage & cache -> Clear cache.',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_yt_verify',
        action: 'VERIFY_INTERNET',
        name: 'Verify YouTube Route',
        description: 'Confirms active route to YouTube servers.',
        parameters: {
          host: 'www.youtube.com',
        },
        is_required: true,
        stop_on_failure: true,
      },
    ],
  },
  null,
  2
);

export const DNS_RECIPE_JSON = JSON.stringify(
  {
    recipe_id: 'recipe_dns_01',
    tool_id: 'tool_dns_repair',
    version: '1.1.0',
    required_android_sdk: 26,
    steps: [
      {
        id: 'step_dns_net',
        action: 'CHECK_INTERNET',
        name: 'Check Network Status',
        description: 'Inspects local network connection.',
        is_required: true,
        stop_on_failure: true,
      },
      {
        id: 'step_dns_lookup_google',
        action: 'DNS_LOOKUP',
        name: 'Test Google DNS',
        description: 'Queries Google public DNS resolver.',
        parameters: {
          hostname: 'dns.google',
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_dns_lookup_cf',
        action: 'DNS_LOOKUP',
        name: 'Test Cloudflare DNS',
        description: 'Queries Cloudflare public DNS resolver.',
        parameters: {
          hostname: 'one.one.one.one',
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_dns_confirm',
        action: 'ASK_USER_CONFIRMATION',
        name: 'User Confirmation',
        description: 'Prompts to open Private DNS settings.',
        parameters: {
          title: 'Open Network Settings?',
          message:
            "If DNS is failing, switching Private DNS to 'Automatic' or disabling VPN usually restores resolution.",
          explanation: 'We will open Network & Internet settings for you.',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_dns_settings',
        action: 'OPEN_ANDROID_SETTINGS',
        name: 'Open Network Settings',
        description: 'Navigates to system network settings.',
        parameters: {
          setting_type: 'NETWORK',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_dns_verify',
        action: 'VERIFY_DNS',
        name: 'Verify DNS Health',
        description: 'Confirms DNS queries are resolving properly.',
        parameters: {
          domain: 'dns.google',
        },
        is_required: true,
        stop_on_failure: true,
      },
    ],
  },
  null,
  2
);

export const FACEBOOK_RECIPE_JSON = JSON.stringify(
  {
    recipe_id: 'recipe_fb_01',
    tool_id: 'tool_fb_repair',
    version: '1.0.4',
    target_package: 'com.facebook.katana',
    required_android_sdk: 26,
    steps: [
      {
        id: 'step_fb_net',
        action: 'CHECK_INTERNET',
        name: 'Verify Connection',
        description: 'Checks internet connectivity.',
        is_required: true,
        stop_on_failure: true,
      },
      {
        id: 'step_fb_dns',
        action: 'DNS_LOOKUP',
        name: 'Resolve Facebook Servers',
        description: 'Resolves facebook.com hostnames.',
        parameters: {
          hostname: 'facebook.com',
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_fb_probe',
        action: 'HTTPS_PROBE',
        name: 'Test Facebook HTTPS',
        description: 'Tests connectivity to Facebook endpoint.',
        parameters: {
          url: 'https://m.facebook.com',
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_fb_app',
        action: 'CHECK_APP_INSTALLED',
        name: 'Check Facebook App',
        description: 'Verifies Facebook application status.',
        parameters: {
          package_name: 'com.facebook.katana',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_fb_details',
        action: 'OPEN_APP_DETAILS',
        name: 'Open Facebook Settings',
        description: 'Opens App Info to clear cache or check permissions.',
        parameters: {
          package_name: 'com.facebook.katana',
          instruction:
            'Opened Facebook App Info. Tap Storage & cache -> Clear cache.',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_fb_verify',
        action: 'VERIFY_INTERNET',
        name: 'Verify Route',
        description: 'Confirms Facebook connectivity.',
        parameters: {
          host: 'facebook.com',
        },
        is_required: true,
        stop_on_failure: true,
      },
    ],
  },
  null,
  2
);

export const WIFI_RECIPE_JSON = JSON.stringify(
  {
    recipe_id: 'recipe_wifi_01',
    tool_id: 'tool_wifi_repair',
    version: '1.0.0',
    required_android_sdk: 26,
    steps: [
      {
        id: 'step_wifi_check',
        action: 'CHECK_INTERNET',
        name: 'Check Wi-Fi Gateway',
        description: 'Validates Wi-Fi internet route and captive portals.',
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_wifi_probe',
        action: 'HTTPS_PROBE',
        name: 'Probe Captive Portal',
        description: 'Checks if Wi-Fi requires captive portal sign-in.',
        parameters: {
          url: 'https://clients3.google.com/generate_204',
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_wifi_settings',
        action: 'OPEN_ANDROID_SETTINGS',
        name: 'Open Wi-Fi Settings',
        description:
          'Navigates to Android Wi-Fi settings to reconnect or forget network.',
        parameters: {
          setting_type: 'WIFI',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_wifi_verify',
        action: 'VERIFY_INTERNET',
        name: 'Verify Wi-Fi Internet',
        description: 'Confirms connection is working.',
        parameters: {
          host: 'clients3.google.com',
        },
        is_required: true,
        stop_on_failure: true,
      },
    ],
  },
  null,
  2
);

export const PLAY_STORE_RECIPE_JSON = JSON.stringify(
  {
    recipe_id: 'recipe_play_01',
    tool_id: 'tool_play_store_repair',
    version: '1.3.1',
    target_package: 'com.android.vending',
    required_android_sdk: 26,
    steps: [
      {
        id: 'step_play_net',
        action: 'CHECK_INTERNET',
        name: 'Verify Internet Connectivity',
        description: 'Checks active internet connection.',
        is_required: true,
        stop_on_failure: true,
      },
      {
        id: 'step_play_storage',
        action: 'CHECK_STORAGE',
        name: 'Verify Device Storage',
        description:
          'Checks available internal storage (min 500MB needed for app installs).',
        parameters: {
          min_free_mb: 500,
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_play_app',
        action: 'CHECK_APP_INSTALLED',
        name: 'Verify Play Store',
        description: 'Checks Google Play Store status.',
        parameters: {
          package_name: 'com.android.vending',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_play_guide',
        action: 'SHOW_GUIDE',
        name: 'Clear Play Store Cache',
        description: 'Step-by-step guide to clear download cache.',
        parameters: {
          guide_title: 'Clear Google Play Store Cache',
          guide_description:
            "Stuck app downloads or 'Download Pending' errors are fixed by clearing Play Store cache.",
          guide_steps: [
            "Tap 'Storage & cache' on the next screen.",
            "Tap 'Clear cache'.",
            "If still failing, you can also tap 'Clear storage' safely.",
          ],
        },
        is_required: true,
        stop_on_failure: false,
      },
      {
        id: 'step_play_details',
        action: 'OPEN_APP_DETAILS',
        name: 'Open Play Store Settings',
        description: 'Opens App Info for Google Play Store.',
        parameters: {
          package_name: 'com.android.vending',
          instruction:
            'Opened Google Play Store App Info. Tap Storage & cache -> Clear cache.',
        },
        is_required: false,
        stop_on_failure: false,
      },
      {
        id: 'step_play_verify',
        action: 'VERIFY_INTERNET',
        name: 'Verify Play Services Route',
        description: 'Confirms connection to Google Play servers.',
        parameters: {
          host: 'play.googleapis.com',
        },
        is_required: true,
        stop_on_failure: true,
      },
    ],
  },
  null,
  2
);

export function getInitialTools(): RepairTool[] {
  return [
    {
      id: 'tool_youtube_repair',
      name: 'YouTube Quick Repair',
      description:
        'Fixes video playback errors, infinite buffering, and clears corrupted cache.',
      category: 'MEDIA',
      version: '1.2.0',
      problemDescription:
        'Video buffering, black screen on launch, or playback error 400.',
      minAppVersion: 1,
      minAndroidSdk: 26,
      isNew: true,
      enabled: true,
      targetPackage: 'com.google.android.youtube',
      rawSignedPayload: YOUTUBE_RECIPE_JSON,
      signatureBase64: signRecipeForTesting(YOUTUBE_RECIPE_JSON),
      lastUpdated: Date.now(),
      status: 'PUBLISHED',
      audience: 'all_active_members',
    },
    {
      id: 'tool_fb_repair',
      name: 'Facebook Troubleshooter',
      description:
        'Resolves news feed refresh failures, login timeouts, and cached data issues.',
      category: 'SOCIAL',
      version: '1.0.4',
      problemDescription:
        "Can't connect, feed won't load, or photo upload stuck.",
      minAppVersion: 1,
      minAndroidSdk: 26,
      isNew: false,
      enabled: true,
      targetPackage: 'com.facebook.katana',
      rawSignedPayload: FACEBOOK_RECIPE_JSON,
      signatureBase64: signRecipeForTesting(FACEBOOK_RECIPE_JSON),
      lastUpdated: Date.now() - 86400000,
      status: 'PUBLISHED',
      audience: 'all_active_members',
    },
    {
      id: 'tool_dns_repair',
      name: 'DNS Repair & Diagnostics',
      description:
        'Diagnoses DNS resolution failures and provides Private DNS recovery.',
      category: 'NETWORK',
      version: '1.1.0',
      problemDescription:
        "Connected to Wi-Fi/data but apps report 'No internet connection'.",
      minAppVersion: 1,
      minAndroidSdk: 26,
      isNew: true,
      enabled: true,
      targetPackage: null,
      rawSignedPayload: DNS_RECIPE_JSON,
      signatureBase64: signRecipeForTesting(DNS_RECIPE_JSON),
      lastUpdated: Date.now(),
      status: 'PUBLISHED',
      audience: 'all_active_members',
    },
    {
      id: 'tool_wifi_repair',
      name: 'Wi-Fi Troubleshooter',
      description:
        'Probes captive portals, validates gateway routes, and guides reconnecting.',
      category: 'NETWORK',
      version: '1.0.0',
      problemDescription:
        'Wi-Fi connected without internet, or captive portal login not showing.',
      minAppVersion: 1,
      minAndroidSdk: 26,
      isNew: false,
      enabled: true,
      targetPackage: null,
      rawSignedPayload: WIFI_RECIPE_JSON,
      signatureBase64: signRecipeForTesting(WIFI_RECIPE_JSON),
      lastUpdated: Date.now() - 172800000,
      status: 'PUBLISHED',
      audience: 'all_active_members',
    },
    {
      id: 'tool_play_store_repair',
      name: 'Play Store Repair',
      description:
        "Fixes 'Download Pending' loops, insufficient storage alerts, and app update errors.",
      category: 'SYSTEM',
      version: '1.3.1',
      problemDescription:
        'Play Store stuck downloading or updating apps, or error 910.',
      minAppVersion: 1,
      minAndroidSdk: 26,
      isNew: false,
      enabled: true,
      targetPackage: 'com.android.vending',
      rawSignedPayload: PLAY_STORE_RECIPE_JSON,
      signatureBase64: signRecipeForTesting(PLAY_STORE_RECIPE_JSON),
      lastUpdated: Date.now() - 259200000,
      status: 'PUBLISHED',
      audience: 'all_active_members',
    },
  ];
}
