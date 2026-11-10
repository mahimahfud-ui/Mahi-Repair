import React, { useState, useEffect } from 'react';
import {
  Smartphone,
  Tablet,
  Maximize2,
  RotateCw,
  Wrench,
  Wifi,
  Battery,
  Radio,
  Sliders,
  Check,
  ChevronDown
} from 'lucide-react';

export type DeviceFrameType = 'fluid' | 'pixel8' | 's24' | 'tablet' | 'compact';

interface DeviceFrameProps {
  currentDevice: DeviceFrameType;
  onChangeDevice: (device: DeviceFrameType) => void;
  orientation: 'portrait' | 'landscape';
  onToggleOrientation: () => void;
  onSwitchToAdmin?: () => void;
  children: React.ReactNode;
}

interface FrameSpec {
  id: DeviceFrameType;
  label: string;
  sublabel: string;
  width: number;
  height: number;
  radius: number;
  isTablet?: boolean;
}

const DEVICE_SPECS: Record<DeviceFrameType, FrameSpec> = {
  fluid: {
    id: 'fluid',
    label: 'Fluid Responsive',
    sublabel: 'Full Screen / Viewport',
    width: 0,
    height: 0,
    radius: 0
  },
  pixel8: {
    id: 'pixel8',
    label: 'Pixel 8 Pro',
    sublabel: '393 × 852 dp · 120Hz',
    width: 393,
    height: 852,
    radius: 48
  },
  s24: {
    id: 's24',
    label: 'Galaxy S24',
    sublabel: '360 × 780 dp · One UI',
    width: 360,
    height: 780,
    radius: 40
  },
  tablet: {
    id: 'tablet',
    label: 'Pixel Tablet',
    sublabel: '820 × 1080 dp · Large Screen',
    width: 820,
    height: 1080,
    radius: 30,
    isTablet: true
  },
  compact: {
    id: 'compact',
    label: 'Compact 320dp',
    sublabel: '320 × 640 dp · Baseline',
    width: 320,
    height: 640,
    radius: 28
  }
};

export const DeviceFrame: React.FC<DeviceFrameProps> = ({
  currentDevice,
  onChangeDevice,
  orientation,
  onToggleOrientation,
  onSwitchToAdmin,
  children
}) => {
  const [currentTime, setCurrentTime] = useState('10:14');
  const [deviceDropdownOpen, setDeviceDropdownOpen] = useState(false);

  useEffect(() => {
    const update = () => {
      const d = new Date();
      const hrs = String(d.getHours()).padStart(2, '0');
      const mins = String(d.getMinutes()).padStart(2, '0');
      setCurrentTime(`${hrs}:${mins}`);
    };
    update();
    const interval = setInterval(update, 30000);
    return () => clearInterval(interval);
  }, []);

  const spec = DEVICE_SPECS[currentDevice];

  // Calculate dimensions based on orientation
  const isLandscape = orientation === 'landscape' && currentDevice !== 'fluid';
  const width = isLandscape ? spec.height : spec.width;
  const height = isLandscape ? spec.width : spec.height;

  return (
    <div className="min-h-screen bg-[#05070e] text-[#F3F6FC] flex flex-col font-sans selection:bg-[#5B8CFF]/30">
      {/* Top Device Control Bar */}
      <header className="sticky top-0 z-50 flex items-center justify-between border-b border-[#1b2234] bg-[#090d18]/95 backdrop-blur-md px-3 sm:px-5 py-2.5 shrink-0">
        <div className="flex items-center gap-2 sm:gap-3">
          {/* Logo */}
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded-[8px] bg-gradient-to-br from-[#5B8CFF] to-[#FF4D5E] grid place-items-center text-white font-extrabold text-xs shadow-sm">
              M
            </div>
            <span className="font-bold text-xs sm:text-sm tracking-tight text-[#F3F6FC] hidden xs:inline">
              Mahi Repair
            </span>
          </div>

          <div className="h-4 w-[1px] bg-[#1c2438] hidden sm:block" />

          {/* Device Preset Switcher */}
          <div className="relative">
            <button
              onClick={() => setDeviceDropdownOpen(!deviceDropdownOpen)}
              className="flex items-center gap-2 rounded-xl bg-[#121929] hover:bg-[#18233a] border border-[#212c44] px-2.5 sm:px-3 py-1.5 text-xs font-medium text-gray-200 transition-colors"
            >
              {currentDevice === 'tablet' ? (
                <Tablet size={14} className="text-[#5B8CFF]" />
              ) : currentDevice === 'fluid' ? (
                <Maximize2 size={14} className="text-[#4FD69C]" />
              ) : (
                <Smartphone size={14} className="text-[#5B8CFF]" />
              )}
              <span className="font-semibold">{spec.label}</span>
              <span className="text-[10px] text-gray-400 font-mono hidden md:inline">({spec.sublabel})</span>
              <ChevronDown size={13} className="text-gray-400 ml-0.5" />
            </button>

            {deviceDropdownOpen && (
              <>
                <div
                  className="fixed inset-0 z-40"
                  onClick={() => setDeviceDropdownOpen(false)}
                />
                <div className="absolute left-0 mt-1.5 w-60 rounded-2xl bg-[#0c1220] border border-[#232f4a] p-1.5 shadow-2xl z-50 animate-in fade-in zoom-in-95 duration-150">
                  <div className="px-2.5 py-1 text-[10px] font-semibold text-gray-400 uppercase tracking-wider">
                    Select Target Device
                  </div>
                  {(Object.keys(DEVICE_SPECS) as DeviceFrameType[]).map((key) => {
                    const item = DEVICE_SPECS[key];
                    const isSelected = currentDevice === key;
                    return (
                      <button
                        key={key}
                        onClick={() => {
                          onChangeDevice(key);
                          setDeviceDropdownOpen(false);
                        }}
                        className={`w-full flex items-center justify-between rounded-xl px-2.5 py-2 text-xs text-left transition-colors ${
                          isSelected
                            ? 'bg-[#1a253e] text-white font-semibold'
                            : 'text-gray-300 hover:bg-[#131b2e] hover:text-white'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          {item.isTablet ? (
                            <Tablet size={14} className={isSelected ? 'text-[#5B8CFF]' : 'text-gray-400'} />
                          ) : key === 'fluid' ? (
                            <Maximize2 size={14} className={isSelected ? 'text-[#4FD69C]' : 'text-gray-400'} />
                          ) : (
                            <Smartphone size={14} className={isSelected ? 'text-[#5B8CFF]' : 'text-gray-400'} />
                          )}
                          <div>
                            <div>{item.label}</div>
                            <div className="text-[10px] text-gray-400 font-mono">{item.sublabel}</div>
                          </div>
                        </div>
                        {isSelected && <Check size={14} className="text-[#5B8CFF]" />}
                      </button>
                    );
                  })}
                </div>
              </>
            )}
          </div>

          {/* Orientation Toggle (if non-fluid) */}
          {currentDevice !== 'fluid' && (
            <button
              onClick={onToggleOrientation}
              title="Toggle Portrait / Landscape"
              className="p-1.5 sm:px-2.5 sm:py-1.5 rounded-xl bg-[#121929] hover:bg-[#18233a] border border-[#212c44] text-gray-300 hover:text-white text-xs flex items-center gap-1.5 transition-colors"
            >
              <RotateCw size={13} className={isLandscape ? 'text-[#5B8CFF]' : 'text-gray-400'} />
              <span className="hidden sm:inline text-[11px] font-medium capitalize">
                {orientation}
              </span>
            </button>
          )}
        </div>

        {/* Right side: Device Telemetry & Status Indicator */}
        <div className="flex items-center gap-2">
          <div className="flex items-center gap-2 px-2.5 py-1.5 rounded-xl bg-[#121929] border border-[#212c44] text-[11px] font-mono text-[#9AA7C2]">
            <span className="w-1.5 h-1.5 rounded-full bg-[#4FD69C] animate-pulse"></span>
            <span className="hidden xs:inline">Device:</span>
            <span className="text-gray-200 font-semibold">DEMO-0001</span>
          </div>
        </div>
      </header>

      {/* Frame Container */}
      {currentDevice === 'fluid' ? (
        // Fluid Responsive mode
        <div className="flex-1 w-full flex flex-col">{children}</div>
      ) : (
        // Authentic Device Hardware Chassis
        <div className="flex-1 flex items-center justify-center p-3 sm:p-6 md:p-10 overflow-x-auto bg-[radial-gradient(ellipse_at_center,_rgba(25,35,60,0.35)_0%,_rgba(5,7,14,0.95)_100%)]">
          <div
            className="relative flex flex-col shrink-0 select-none transition-all duration-300 ease-out"
            style={{
              width: `${width}px`,
              maxWidth: '96vw',
              height: `${height}px`,
              maxHeight: 'calc(100vh - 84px)'
            }}
          >
            {/* Outer Metallic Bezel */}
            <div
              className="w-full h-full p-2.5 sm:p-3 bg-gradient-to-b from-[#252c3f] via-[#161a26] to-[#12151f] shadow-[0_25px_60px_-15px_rgba(0,0,0,0.9),0_0_0_1px_rgba(255,255,255,0.08)] flex flex-col relative"
              style={{
                borderRadius: `${spec.radius + 12}px`
              }}
            >
              {/* Physical Hardware Buttons (Simulated) */}
              {!isLandscape && (
                <>
                  {/* Volume Up/Down */}
                  <div className="absolute -left-[5px] top-28 w-[4px] h-14 bg-[#323b52] rounded-l-md shadow-sm" />
                  {/* Power Button */}
                  <div className="absolute -right-[5px] top-32 w-[4px] h-10 bg-[#323b52] rounded-r-md shadow-sm" />
                </>
              )}

              {/* Screen Area with Internal Shadow */}
              <div
                className="w-full h-full bg-[#060A14] flex flex-col overflow-hidden relative shadow-[inset_0_0_8px_rgba(0,0,0,0.8)]"
                style={{
                  borderRadius: `${spec.radius}px`,
                  transform: 'translateZ(0)' // Constrains fixed navigation to device screen
                }}
              >
                {/* Android Status Bar */}
                <div className="h-7 w-full px-5 flex items-center justify-between text-[11px] font-mono text-gray-300 bg-transparent shrink-0 z-30 select-none">
                  {/* Left: Clock */}
                  <span className="font-semibold text-white tracking-wide">{currentTime}</span>

                  {/* Center: Camera Punch Hole */}
                  <div className="w-3.5 h-3.5 rounded-full bg-black border border-[#2b354c] shadow-inner flex items-center justify-center">
                    <div className="w-1.5 h-1.5 rounded-full bg-[#0e1628]" />
                  </div>

                  {/* Right: Network & Battery Indicators */}
                  <div className="flex items-center gap-1.5">
                    <Wifi size={12} className="text-gray-300" />
                    <span className="text-[10px] font-bold text-gray-300">5G</span>
                    <div className="flex items-center gap-0.5">
                      <Battery size={13} className="text-[#4FD69C]" />
                      <span className="text-[10px] font-medium text-gray-300">84%</span>
                    </div>
                  </div>
                </div>

                {/* Inner App Content Screen */}
                <div className="flex-1 w-full overflow-y-auto overflow-x-hidden flex flex-col relative scrollbar-none">
                  {children}
                </div>

                {/* Android Bottom Gesture Navigation Bar (Authentic Android Overlay) */}
                <div className="pointer-events-none absolute bottom-1.5 left-0 right-0 z-50 flex items-center justify-center">
                  <div className="w-28 h-1 rounded-full bg-white/45 shadow-sm" />
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
