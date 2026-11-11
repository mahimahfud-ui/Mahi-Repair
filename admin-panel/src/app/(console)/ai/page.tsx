import { ConsoleShell } from "@/components/console-shell";
import { MahiAiAssistant } from "@/components/mahi-ai-assistant";

export const dynamic = "force-dynamic";

export default function AiAssistantPage() {
  return (
    <ConsoleShell active="/ai" title="AI Recipe Assistant" subtitle="Draft allowlisted repair recipes using your existing Supabase AI service">
      <MahiAiAssistant />
    </ConsoleShell>
  );
}
