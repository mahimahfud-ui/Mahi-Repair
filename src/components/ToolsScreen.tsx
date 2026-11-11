import React, { useState, useMemo } from 'react';
import { RepairTool, ToolCategory } from '../types';
import { getToolMonogram, getParsedToolSteps } from '../utils/helpers';

interface ToolsScreenProps {
  tools: RepairTool[];
  onOpenTool: (toolId: string) => void;
}

const CATEGORIES: { id: ToolCategory; label: string }[] = [
  { id: 'ALL', label: 'All' },
  { id: 'NETWORK', label: 'Network' },
  { id: 'SOCIAL', label: 'Social' },
  { id: 'SYSTEM', label: 'System' },
  { id: 'MEDIA', label: 'Media' },
];

export const ToolsScreen: React.FC<ToolsScreenProps> = ({
  tools,
  onOpenTool,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<ToolCategory>('ALL');

  const filteredTools = useMemo(() => {
    return tools.filter((tool) => {
      const matchesCategory =
        selectedCategory === 'ALL' || tool.category === selectedCategory;
      const query = searchQuery.toLowerCase().trim();
      const matchesSearch =
        !query ||
        tool.name.toLowerCase().includes(query) ||
        tool.description.toLowerCase().includes(query) ||
        tool.problemDescription.toLowerCase().includes(query);
      return matchesCategory && matchesSearch;
    });
  }, [tools, selectedCategory, searchQuery]);

  return (
    <div className="anim-push flex flex-col gap-6 max-w-[1200px] mx-auto pb-12">
      {/* Header */}
      <div>
        <div className="text-[11px] font-semibold tracking-[0.14em] uppercase text-[#5E6B88]">
          Library
        </div>
        <h1 className="text-[28px] font-bold tracking-tight text-[#F3F6FC] mt-1">
          Repair tools
        </h1>
        <p className="text-[14px] text-[#9AA7C2] mt-1.5 leading-relaxed">
          Verified, step-by-step tools. Every action asks for your confirmation.
        </p>
      </div>

      {/* Search & Filter Controls */}
      <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
        {/* Search Input */}
        <div className="relative flex-1 max-w-md">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search tools or symptoms..."
            className="w-full h-11 rounded-[12px] bg-[#121B2F] border border-[rgba(150,180,255,0.16)] px-3.5 text-[14px] text-[#F3F6FC] placeholder-[#5E6B88] focus:border-[#5B8CFF] focus:shadow-[0_0_0_4px_rgba(91,140,255,0.14)] transition-all"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-[#5E6B88] hover:text-[#F3F6FC] text-sm"
            >
              ✕
            </button>
          )}
        </div>

        {/* Category Pills */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 scrollbar-none">
          {CATEGORIES.map((cat) => {
            const isSelected = selectedCategory === cat.id;
            return (
              <button
                key={cat.id}
                onClick={() => setSelectedCategory(cat.id)}
                className={`px-3 py-1.5 rounded-[10px] text-[13px] font-medium whitespace-nowrap transition-all ${
                  isSelected
                    ? 'bg-[#1A2540] text-[#F3F6FC] font-semibold shadow-[0_1px_2px_rgba(0,0,0,0.45)]'
                    : 'text-[#9AA7C2] hover:text-[#F3F6FC] hover:bg-[#121B2F]'
                }`}
              >
                {cat.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Tools Grid */}
      {filteredTools.length > 0 ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
          {filteredTools.map((tool) => {
            const monogram = getToolMonogram(tool);
            const steps = getParsedToolSteps(tool);
            return (
              <button
                key={tool.id}
                onClick={() => onOpenTool(tool.id)}
                className="mahi-card mahi-card-interactive"
              >
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-[12px] bg-[rgba(91,140,255,0.14)] text-[#5B8CFF] font-bold text-[14px] grid place-items-center flex-shrink-0">
                    {monogram}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-[15px] text-[#F3F6FC] truncate">
                      {tool.name}
                    </div>
                    <div className="text-[13px] text-[#9AA7C2] mt-0.5">
                      {steps.length} steps
                    </div>
                  </div>
                  <span className="text-[#9AA7C2] text-lg">›</span>
                </div>
                <p className="text-[14px] text-[#9AA7C2] mt-3.5 line-clamp-2 leading-relaxed">
                  {tool.description}
                </p>
              </button>
            );
          })}
        </div>
      ) : (
        <div className="mahi-card py-16 text-center text-[#9AA7C2]">
          <div className="w-12 h-12 rounded-full bg-[#121B2F] border border-[rgba(150,180,255,0.08)] grid place-items-center text-lg mx-auto mb-3 text-[#5E6B88]">
            ○
          </div>
          <div className="font-semibold text-[16px] text-[#F3F6FC]">
            No repair tools found
          </div>
          <p className="text-[13px] text-[#5E6B88] mt-1">
            Try adjusting your search query or selected category filter.
          </p>
        </div>
      )}
    </div>
  );
};
