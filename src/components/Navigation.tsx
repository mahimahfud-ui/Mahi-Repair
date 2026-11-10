import React from 'react';

export type NavTab = 'home' | 'tools' | 'hist' | 'set';

interface NavigationProps {
  activeTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
  isOfflineMode?: boolean;
}

export const Navigation: React.FC<NavigationProps> = ({
  activeTab,
  onSelectTab,
  isOfflineMode = true,
}) => {
  const tabs: { id: NavTab; label: string; icon: React.ReactNode }[] = [
    {
      id: 'home',
      label: 'Home',
      icon: (
        <svg
          viewBox="0 0 24 24"
          className="w-[22px] h-[22px] stroke-current fill-none stroke-[1.8] stroke-linecap-round stroke-linejoin-round"
        >
          <path d="M3 11l9-7 9 7v9a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" />
        </svg>
      ),
    },
    {
      id: 'tools',
      label: 'Tools',
      icon: (
        <svg
          viewBox="0 0 24 24"
          className="w-[22px] h-[22px] stroke-current fill-none stroke-[1.8] stroke-linecap-round stroke-linejoin-round"
        >
          <rect x="3" y="3" width="7" height="7" rx="2" />
          <rect x="14" y="3" width="7" height="7" rx="2" />
          <rect x="3" y="14" width="7" height="7" rx="2" />
          <rect x="14" y="14" width="7" height="7" rx="2" />
        </svg>
      ),
    },
    {
      id: 'hist',
      label: 'History',
      icon: (
        <svg
          viewBox="0 0 24 24"
          className="w-[22px] h-[22px] stroke-current fill-none stroke-[1.8] stroke-linecap-round stroke-linejoin-round"
        >
          <circle cx="12" cy="12" r="9" />
          <path d="M12 7v5l3 2" />
        </svg>
      ),
    },
    {
      id: 'set',
      label: 'Settings',
      icon: (
        <svg
          viewBox="0 0 24 24"
          className="w-[22px] h-[22px] stroke-current fill-none stroke-[1.8] stroke-linecap-round stroke-linejoin-round"
        >
          <circle cx="12" cy="12" r="3" />
          <path d="M19 12a7 7 0 0 0-.1-1.2l2-1.5-2-3.4-2.4 1a7 7 0 0 0-2-1.2L14 3h-4l-.5 2.7a7 7 0 0 0-2 1.2l-2.4-1-2 3.4 2 1.5A7 7 0 0 0 5 12c0 .4 0 .8.1 1.2l-2 1.5 2 3.4 2.4-1a7 7 0 0 0 2 1.2L10 21h4l.5-2.7a7 7 0 0 0 2-1.2l2.4 1 2-3.4-2-1.5c.1-.4.1-.8.1-1.2z" />
        </svg>
      ),
    },
  ];

  return (
    <>
      {/* Desktop Fixed Left Sidebar (>=1024px) */}
      <aside className="hidden lg:flex flex-col sticky top-0 h-screen w-[232px] border-r border-[rgba(150,180,255,0.08)] bg-[#090d18] px-4 py-7 flex-shrink-0 z-30">
        {/* Brand Mark */}
        <div className="flex items-center gap-2.5 px-2 pb-7 font-bold text-[17px] tracking-tight text-[#F3F6FC]">
          <div className="w-[30px] h-[30px] rounded-[9px] bg-gradient-to-br from-[#5B8CFF] to-[#FF4D5E] grid place-items-center text-white font-extrabold text-sm shadow-[0_6px_18px_-6px_rgba(91,140,255,0.6)]">
            M
          </div>
          <span>Mahi Repair</span>
        </div>

        {/* Nav Items */}
        <nav className="flex flex-col gap-1.5" aria-label="Main Navigation">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onSelectTab(tab.id)}
                aria-current={isActive ? 'page' : undefined}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-[12px] text-sm font-medium transition-all ${
                  isActive
                    ? 'bg-[#121B2F] text-[#F3F6FC] font-semibold'
                    : 'text-[#9AA7C2] hover:text-[#F3F6FC] hover:bg-[#0D1424]'
                }`}
              >
                <span className={isActive ? 'text-[#5B8CFF]' : 'text-[#5E6B88]'}>
                  {tab.icon}
                </span>
                <span>{tab.label}</span>
              </button>
            );
          })}
        </nav>

        {/* Bottom Status Panel */}
        <div className="mt-auto pt-6 border-t border-[rgba(150,180,255,0.08)]">
          <div className="px-2 py-2 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-[#4FD69C] animate-pulse"></span>
              <span className="text-[11px] font-semibold text-[#9AA7C2] uppercase tracking-wider">
                {isOfflineMode ? 'Offline Ready' : 'Cloud Sync'}
              </span>
            </div>
            <span className="text-[10px] mono text-[#5E6B88]">v1.2</span>
          </div>
        </div>
      </aside>

      {/* Mobile Fixed Bottom Bar (<1024px) */}
      <nav
        className="lg:hidden fixed bottom-0 left-0 right-0 z-40 bg-[rgba(13,20,36,0.92)] backdrop-blur-[20px] border-t border-[rgba(150,180,255,0.12)] px-3 pt-2 pb-[calc(14px+env(safe-area-inset-bottom,0px))] shadow-[0_-8px_24px_rgba(0,0,0,0.5)]"
        aria-label="Mobile Navigation"
      >
        <div className="grid grid-cols-4 max-w-[560px] mx-auto">
          {tabs.map((tab) => {
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => onSelectTab(tab.id)}
                aria-current={isActive ? 'page' : undefined}
                className={`flex flex-col items-center gap-1 py-1.5 text-[11px] font-medium transition-colors ${
                  isActive ? 'text-[#F3F6FC]' : 'text-[#5E6B88] hover:text-[#9AA7C2]'
                }`}
              >
                <span className={isActive ? 'text-[#5B8CFF]' : 'currentColor'}>
                  {tab.icon}
                </span>
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>
      </nav>
    </>
  );
};
